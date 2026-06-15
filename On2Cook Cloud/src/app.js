import { BleTransport, BLE_UUIDS } from "./ble-transport.js?v=20260612q";
import { importRecipeZipArrayBuffer, importRecipeZipFile, importRecipeZipUrl } from "./zip-reader.js?v=20260612q";
import {
  authService,
  profileService,
  recipeService,
  recipeSignatureFromJson,
  syncService
} from "./ncb-services.js?v=20260615a";
import {
  cloneRecipeForEditing,
  createFinalRecipeFromBase,
  createStore,
  currentPermissions,
  decorateOrderRecord,
  exportState,
  findEffectiveRecipeForOrder,
  findRecipeById,
  getCurrentUser,
  importState,
  loadState,
  syncStateToSupabase
} from "./data-store.js?v=20260612q";

const app = document.getElementById("app");
const ble = new BleTransport();
let seedRecipes = [];
let globalRecipeCatalog = [];
let store = null;
let toastTimer = 0;
let statusTimer = 0;
let orderFeedTimer = 0;
const recipeMissingRetryCounts = new Map();
const RECIPE_ARCHIVE_VERSION = "20260612q";
const cloudRuntime = {
  ready: false,
  instance: "",
  providers: {},
  session: null,
  loading: false,
  lastSummary: "",
  lastError: "",
  lastSyncAt: "",
  lastRestoreAt: ""
};

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function state() {
  return store.getState();
}

function mutate(recipe) {
  store.setState((draft) => recipe(draft) || draft);
}

function nowIso() {
  return new Date().toISOString();
}

function emptyActiveRun() {
  return {
    orderId: "",
    recipeId: "",
    displayName: "",
    firmwareName: "",
    startedAt: "",
    durationSeconds: 0
  };
}

function emptyLastRun() {
  return {
    orderId: "",
    recipeId: "",
    displayName: "",
    firmwareName: "",
    startedAt: "",
    finishedAt: "",
    durationSeconds: 0,
    outcome: "",
    note: "",
    stepNo: 0
  };
}

function emptyUploadState() {
  return {
    inventoryChecking: false,
    active: false,
    totalRecipes: 0,
    currentIndex: 0,
    currentRecipeName: "",
    recipeNames: [],
    completedRecipeNames: [],
    skippedRecipeNames: [],
    summary: ""
  };
}

function guessRecipeDisplayName(recipeJson, fallback = "Recipe") {
  const name = Array.isArray(recipeJson?.name) ? recipeJson.name[0] : recipeJson?.name;
  return String(name || fallback).trim() || fallback;
}

async function loadSeedRecipesFromArchives() {
  const manifestResponse = await fetch(`./data/order-recipes-manifest.json?v=${RECIPE_ARCHIVE_VERSION}`);
  if (!manifestResponse.ok) {
    throw new Error("Unable to load the order recipe archive manifest.");
  }
  const manifest = await manifestResponse.json();
  if (!Array.isArray(manifest) || manifest.length === 0) {
    throw new Error("The order recipe archive manifest is empty.");
  }
  return Promise.all(
    manifest.map(async (entry, index) => {
      const zipUrl = String(entry.zipUrl || "").trim();
      if (!zipUrl) {
        throw new Error(`Recipe archive entry ${index + 1} is missing zipUrl.`);
      }
      const response = await fetch(`${zipUrl}?v=${RECIPE_ARCHIVE_VERSION}`);
      if (!response.ok) {
        throw new Error(`Unable to load recipe ZIP ${zipUrl}`);
      }
      const buffer = await response.arrayBuffer();
      const result = await importRecipeZipArrayBuffer(buffer, entry.zipName || zipUrl.split("/").pop() || "recipe.zip");
      const recipeName = String(entry.recipeName || guessRecipeDisplayName(result.recipeJson, entry.id || `Recipe ${index + 1}`)).trim();
      return {
        id: entry.id || recipeName,
        zipName: entry.zipName || result.sourceName,
        zipUrl,
        recipeTextEntryName: result.recipeTextEntryName || "",
        recipeName,
        imageDataUrl: entry.imageDataUrl || result.imageDataUrl || "",
        recipe: structuredClone(result.recipeJson),
        recipeText: result.recipeText || "",
        entries: result.entries || []
      };
    })
  );
}

async function loadSeedRecipeCatalog() {
  try {
    return await loadSeedRecipesFromArchives();
  } catch (error) {
    console.warn("Falling back to seed-recipes.json because ZIP archive loading failed.", error);
    const response = await fetch(`./data/seed-recipes.json?v=${RECIPE_ARCHIVE_VERSION}`);
    if (!response.ok) {
      throw new Error("Unable to load the fallback seed recipe catalog.");
    }
    return response.json();
  }
}

async function loadGlobalRecipeCatalog() {
  try {
    const response = await fetch(`./data/global-recipes-manifest.json?v=${RECIPE_ARCHIVE_VERSION}`);
    if (!response.ok) {
      throw new Error("Unable to load the global recipe catalog.");
    }
    const manifest = await response.json();
    return Array.isArray(manifest) ? manifest : [];
  } catch (error) {
    console.warn("Global recipe catalog could not be loaded.", error);
    return [];
  }
}

function normalizeCatalogKey(value) {
  return String(value || "").trim().toLowerCase();
}

function buildCatalogEntryFromRecipe(record, options = {}) {
  const signature = record.recipeSignature || recipeSignatureFromJson(record.recipeJson);
  return {
    id: options.catalogEntryId || `imported-${crypto.randomUUID()}`,
    recipeName: record.displayName,
    zipName: options.sourceName || record.zipName || `${record.displayName}.zip`,
    zipUrl: options.zipUrl || "",
    source: options.source || "imported",
    importedAt: nowIso(),
    recipeSignature: signature,
    embeddedRecipe: {
      recipeJson: structuredClone(record.recipeJson),
      recipeText: options.recipeText || record.rawRecipeText || JSON.stringify(record.recipeJson || {}),
      recipeTextEntryName: options.recipeTextEntryName || record.recipeTextEntryName || "",
      imageDataUrl: options.imageDataUrl || record.imageDataUrl || "",
      entries: Array.isArray(options.entries) ? structuredClone(options.entries) : Array.isArray(record.recipeEntries) ? structuredClone(record.recipeEntries) : []
    }
  };
}

function buildImportedCatalogEntry(result, record, options = {}) {
  return buildCatalogEntryFromRecipe(record, {
    ...options,
    sourceName: result.sourceName || `${record.displayName}.zip`,
    recipeText: result.recipeText || record.rawRecipeText || JSON.stringify(record.recipeJson || {}),
    recipeTextEntryName: result.recipeTextEntryName || record.recipeTextEntryName || "",
    imageDataUrl: result.imageDataUrl || record.imageDataUrl || "",
    entries: Array.isArray(result.entries) ? structuredClone(result.entries) : Array.isArray(record.recipeEntries) ? structuredClone(record.recipeEntries) : []
  });
}

function upsertImportedCatalogEntry(draft, entry) {
  if (!entry) return;
  if (!Array.isArray(draft.importedRecipeCatalog)) {
    draft.importedRecipeCatalog = [];
  }
  const signatureKey = normalizeCatalogKey(entry.recipeSignature);
  const zipKey = normalizeCatalogKey(entry.zipName);
  const nameKey = normalizeCatalogKey(entry.recipeName);
  const existingIndex = draft.importedRecipeCatalog.findIndex((item) => {
    return (
      (signatureKey && normalizeCatalogKey(item.recipeSignature) === signatureKey) ||
      (zipKey && normalizeCatalogKey(item.zipName) === zipKey) ||
      (nameKey && normalizeCatalogKey(item.recipeName) === nameKey)
    );
  });
  if (existingIndex >= 0) {
    draft.importedRecipeCatalog[existingIndex] = {
      ...draft.importedRecipeCatalog[existingIndex],
      ...structuredClone(entry)
    };
    return;
  }
  draft.importedRecipeCatalog.unshift(structuredClone(entry));
}

function getRecipeCatalog(snapshot) {
  const imported = Array.isArray(snapshot.importedRecipeCatalog) ? snapshot.importedRecipeCatalog : [];
  const combined = [];
  const seen = new Set();
  [...imported, ...globalRecipeCatalog].forEach((entry, index) => {
    if (!entry) return;
    const key =
      normalizeCatalogKey(entry.recipeSignature) ||
      normalizeCatalogKey(entry.zipName) ||
      `${normalizeCatalogKey(entry.recipeName)}:${index}`;
    if (seen.has(key)) return;
    seen.add(key);
    combined.push(entry);
  });
  return combined;
}

function createImportResultFromCatalogEntry(entry) {
  const embedded = entry?.embeddedRecipe;
  if (!embedded?.recipeJson) {
    throw new Error(`Recipe ZIP payload is not stored locally for ${entry?.recipeName || entry?.zipName || "this recipe"}.`);
  }
  return {
    recipeJson: structuredClone(embedded.recipeJson),
    recipeText: embedded.recipeText || JSON.stringify(embedded.recipeJson || {}),
    recipeTextEntryName: embedded.recipeTextEntryName || "",
    imageDataUrl: embedded.imageDataUrl || "",
    sourceName: entry.zipName || `${entry.recipeName || "recipe"}.zip`,
    entries: Array.isArray(embedded.entries) ? structuredClone(embedded.entries) : []
  };
}

function getRecipeRetryKey(slot, orderId) {
  return `${Number(slot)}:${orderId}`;
}

function clearRecipeRetryTracking(slot, orderId) {
  if (!orderId) return;
  recipeMissingRetryCounts.delete(getRecipeRetryKey(slot, orderId));
}

function formatTimestamp(value) {
  if (!value) return "Never";
  return new Date(value).toLocaleString([], {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });
}

function formatAgo(value) {
  if (!value) return "just now";
  const diff = Math.max(0, Date.now() - new Date(value).getTime());
  const minutes = Math.round(diff / 60000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} hr ago`;
  return `${Math.round(hours / 24)} day ago`;
}

function secondsLabel(seconds) {
  const safe = Math.max(0, Number(seconds) || 0);
  const mins = Math.floor(safe / 60);
  const secs = safe % 60;
  return `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
}

const DEFAULT_STIRRER_LEVEL = "MED";

function toMoney(value) {
  return Number((Number(value) || 0).toFixed(2));
}

function formatCurrency(value) {
  return `₹${toMoney(value).toFixed(2)}`;
}

function sanitizeFirmwareName(value) {
  return String(value || "")
    .replace(/[^A-Za-z0-9 ()_-]/g, "")
    .trim()
    .slice(0, 30) || "APP_RECIPE";
}

function getDevice(slot) {
  return state().devices.find((device) => device.slot === Number(slot)) || null;
}

function getCurrentOrderById(orderId) {
  return state().orders.current.find((order) => order.id === orderId) || null;
}

function getAnyOrderById(snapshot, orderId) {
  return snapshot.orders.current.find((order) => order.id === orderId) || snapshot.orders.previous.find((order) => order.id === orderId) || null;
}

function getCurrentJob(snapshot, device) {
  return (
    snapshot.orders.current.find((order) => order.id === device.currentJobId) ||
    snapshot.orders.current.find(
      (order) =>
        order.assignedSlot === device.slot &&
        ["starting", "cooking", "awaiting_confirmation"].includes(order.status)
    ) ||
    null
  );
}

function getQueueOrders(snapshot, device) {
  if (device.queueOrderIds.length > 0) {
    return device.queueOrderIds
      .map((orderId) => snapshot.orders.current.find((order) => order.id === orderId))
      .filter(Boolean);
  }
  return snapshot.orders.current.filter((order) => order.assignedSlot === device.slot && order.status === "queued");
}

function getSelectedRecipes(snapshot) {
  return snapshot.recipes.filter((recipe) => recipe.selected);
}

function isRecipeAllowedOnDevice(snapshot, device, recipeId) {
  if (!Array.isArray(device.allowedRecipeIds) || device.allowedRecipeIds.length === 0) return false;
  return device.allowedRecipeIds.includes(recipeId);
}

function normalizeRecipeNameKey(value) {
  return String(value || "").trim().toLowerCase();
}

function getRecipePayloadText(recipe) {
  if (typeof recipe?.rawRecipeText === "string" && recipe.rawRecipeText.trim()) {
    return recipe.rawRecipeText;
  }
  return JSON.stringify(recipe?.recipeJson || {});
}

function getRecipeSignature(recipe) {
  const json = getRecipePayloadText(recipe);
  let hash = 2166136261;
  for (let index = 0; index < json.length; index += 1) {
    hash ^= json.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return `${json.length}:${(hash >>> 0).toString(16)}`;
}

function getKnownDeviceRecipeKeys(device) {
  return new Set(
    [...(device.availableRecipeNames || []), ...(device.syncedRecipeNames || [])]
      .map((name) => normalizeRecipeNameKey(name))
      .filter(Boolean)
  );
}

function getLiveRecipeName(device) {
  return String(device.telemetry.currentRecipe || "").trim();
}

function getManualStatus(value) {
  return String(value || "").trim().toUpperCase();
}

function isQuickStartActive(value) {
  return ["RUN", "START", "PAUSE", "RESUME"].includes(getManualStatus(value));
}

function getManualModeTarget(snapshot) {
  const preferredSlot = Math.max(1, Number(snapshot.ui.manualMode?.slot) || 1);
  return snapshot.devices.find((device) => device.slot === preferredSlot) || snapshot.devices[0] || null;
}

function hasLiveRuntime(device) {
  const workStatus = String(device.telemetry.workStatus || "").toLowerCase();
  const manualBusy =
    isQuickStartActive(device.telemetry.inductionStatus) ||
    isQuickStartActive(device.telemetry.magnetronStatus) ||
    Boolean(device.telemetry.pumpOn);
  return (Boolean(getLiveRecipeName(device)) && !["idle", "offline", "complete_wait"].includes(workStatus)) || manualBusy;
}

function inventoryIsFresh(device, maxAgeMs = 45000) {
  if (!device.recipeInventoryUpdatedAt) return false;
  return Date.now() - new Date(device.recipeInventoryUpdatedAt).getTime() <= maxAgeMs;
}

function getEffectiveRecipe(snapshot, order) {
  if (order.activeRecipeId) {
    return findRecipeById(snapshot, order.activeRecipeId) || findEffectiveRecipeForOrder(snapshot, order.recipeLookup || order.itemName);
  }
  return findEffectiveRecipeForOrder(snapshot, order.recipeLookup || order.itemName);
}

function findRecipeByFirmwareName(snapshot, firmwareName) {
  const key = String(firmwareName || "").trim().toLowerCase();
  if (!key) return null;
  return (
    snapshot.recipes.find(
      (recipe) =>
        recipe.firmwareName.toLowerCase() === key ||
        recipe.displayName.toLowerCase() === key ||
        recipe.aliases.some((alias) => alias.toLowerCase() === key)
    ) || null
  );
}

function findRecipeByZipName(snapshot, zipName) {
  const key = String(zipName || "").trim().toLowerCase();
  if (!key) return null;
  return snapshot.recipes.find((recipe) => String(recipe.zipName || "").trim().toLowerCase() === key) || null;
}

function findRecipeForGlobalCatalogEntry(snapshot, entry) {
  return (
    snapshot.recipes.find(
      (recipe) =>
        normalizeCatalogKey(recipe.recipeSignature) &&
        normalizeCatalogKey(recipe.recipeSignature) === normalizeCatalogKey(entry.recipeSignature)
    ) ||
    findRecipeByZipName(snapshot, entry.zipName) ||
    findRecipeByFirmwareName(snapshot, entry.recipeName || entry.id || "") ||
    snapshot.recipes.find((recipe) => recipe.displayName.toLowerCase() === String(entry.recipeName || "").trim().toLowerCase()) ||
    null
  );
}

function getRuntimeRecipe(snapshot, device) {
  const currentOrder = getCurrentJob(snapshot, device);
  if (currentOrder) {
    return getEffectiveRecipe(snapshot, currentOrder);
  }
  return findRecipeByFirmwareName(snapshot, getLiveRecipeName(device));
}

function getTelemetryMode(device) {
  return String(device.telemetry.mode || "").trim().toLowerCase();
}

function getCurrentIngredient(device, recipe) {
  if (!recipe?.recipeJson?.Ingredients?.length) return null;
  const stepIndex = Math.max(0, Number(device.telemetry.ingredientsIndex || device.telemetry.stepNo || 1) - 1);
  return recipe.recipeJson.Ingredients[stepIndex] || null;
}

function getCurrentInstruction(device, recipe) {
  if (!recipe?.recipeJson?.Instruction?.length) return null;
  const stepIndex = Math.max(0, Number(device.telemetry.stepNo || 1) - 1);
  return recipe.recipeJson.Instruction[stepIndex] || null;
}

function getRecipeDuration(recipe) {
  if (!recipe?.recipeJson?.Instruction) return 0;
  return recipe.recipeJson.Instruction.reduce((total, step) => {
    const duration =
      Number(step.durationInSec) ||
      Math.max(Number(step.Induction_on_time) || 0, Number(step.Magnetron_on_time) || 0, Number(step.wait_time) || 0);
    return total + duration;
  }, 0);
}

function getInstructionDuration(step) {
  return (
    Number(step?.durationInSec) ||
    Math.max(Number(step?.Induction_on_time) || 0, Number(step?.Magnetron_on_time) || 0, Number(step?.wait_time) || 0)
  );
}

function getDeviceEta(snapshot, device) {
  let eta = Number(device.telemetry.remainingSeconds) || 0;
  getQueueOrders(snapshot, device).forEach((order) => {
    const recipe = getEffectiveRecipe(snapshot, order);
    eta += getRecipeDuration(recipe);
  });
  return eta;
}

function getConnectedDevices(snapshot) {
  return snapshot.devices.filter((device) => device.connection === "connected" && device.enabled);
}

function getDeviceSyncRecipes(snapshot, device) {
  return getSelectedRecipes(snapshot).filter((recipe) => isRecipeAllowedOnDevice(snapshot, device, recipe.id));
}

function getRecipeForRunRecord(snapshot, runRecord) {
  if (!runRecord) return null;
  if (runRecord.recipeId) {
    const byId = findRecipeById(snapshot, runRecord.recipeId);
    if (byId) return byId;
  }
  return findRecipeByFirmwareName(snapshot, runRecord.firmwareName || runRecord.displayName || "");
}

function getDeviceTimelineRecipe(snapshot, device, runtimeRecipe = null) {
  return runtimeRecipe || getRecipeForRunRecord(snapshot, device.activeRun) || getRecipeForRunRecord(snapshot, device.lastRun);
}

function formatShortTime(value) {
  if (!value) return "--:--";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "--:--";
  return parsed.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit"
  });
}

function clampPercent(value) {
  return Math.max(0, Math.min(100, Number(value) || 0));
}

function getDeviceSummaryMessage(device) {
  return String(device.uploadState?.summary || device.lastMessage || "Waiting for connection");
}

function getDeviceRecipeHeadline(snapshot, device, currentOrder, runtimeRecipe) {
  const liveRecipeName = getLiveRecipeName(device);
  if (currentOrder || hasLiveRuntime(device) || device.activeRun?.displayName || device.activeRun?.firmwareName) {
    return {
      title: device.activeRun?.displayName || currentOrder?.itemName || runtimeRecipe?.displayName || liveRecipeName || "Cooking now",
      status: currentOrder?.status || device.telemetry.workStatus || "cooking",
      note:
        device.telemetry.workStatus === "starting"
          ? "Starting anew on this device."
          : `Started ${device.activeRun?.startedAt ? formatAgo(device.activeRun.startedAt) : "just now"}`
    };
  }
  if (device.lastRun?.displayName || device.lastRun?.firmwareName) {
    const outcome = device.lastRun.outcome || "completed";
    return {
      title: device.lastRun.displayName || device.lastRun.firmwareName,
      status: outcome,
      note:
        outcome === "aborted"
          ? `Aborted ${device.lastRun.finishedAt ? formatAgo(device.lastRun.finishedAt) : "just now"}. Device is ready for the next recipe.`
          : `Completed ${device.lastRun.finishedAt ? formatAgo(device.lastRun.finishedAt) : "just now"}. Device is ready for the next recipe.`
    };
  }
  return null;
}

function getTimelineWindow(device, recipe, active = false) {
  const totalSeconds = Number(active ? device.activeRun?.durationSeconds : device.lastRun?.durationSeconds) || getRecipeDuration(recipe);
  if (!totalSeconds) {
    return {
      totalSeconds: 0,
      startAt: "",
      endAt: ""
    };
  }
  if (active) {
    const remaining = Math.max(0, Number(device.telemetry.remainingSeconds) || 0);
    const elapsed = Math.max(0, totalSeconds - remaining);
    const fallbackStart = new Date(Date.now() - elapsed * 1000).toISOString();
    const startAt = device.activeRun?.startedAt || fallbackStart;
    const endAt = new Date(new Date(startAt).getTime() + totalSeconds * 1000).toISOString();
    return { totalSeconds, startAt, endAt };
  }
  const startAt = device.lastRun?.startedAt || "";
  const endAt =
    device.lastRun?.finishedAt ||
    (startAt ? new Date(new Date(startAt).getTime() + totalSeconds * 1000).toISOString() : "");
  return { totalSeconds, startAt, endAt };
}

function clearOrderFromDeviceAssignments(draft, orderId, nextSlot = null) {
  draft.devices.forEach((device) => {
    if (device.slot !== nextSlot) {
      device.queueOrderIds = device.queueOrderIds.filter((item) => item !== orderId);
      if (device.currentJobId === orderId) {
        device.currentJobId = "";
      }
    }
  });
}

function releaseOrderFromAllDevices(draft, orderId) {
  draft.devices.forEach((device) => {
    device.queueOrderIds = device.queueOrderIds.filter((item) => item !== orderId);
    if (device.currentJobId === orderId) {
      device.currentJobId = "";
      device.completionConfirmationPending = false;
      device.activeRun = emptyActiveRun();
      device.telemetry.workStatus = "idle";
      device.telemetry.remainingSeconds = 0;
      device.telemetry.currentRecipe = "";
    }
  });
}

function resetDeviceRuntimeState(draft, slot, options = {}) {
  const device = draft.devices.find((item) => item.slot === Number(slot));
  if (!device) return null;
  const releaseOrders = options.releaseOrders !== false;
  if (releaseOrders) {
    draft.orders.current.forEach((order) => {
      if (order.assignedSlot !== device.slot) return;
      if (!["queued", "starting", "cooking", "awaiting_confirmation"].includes(order.status)) return;
      order.status = "pending";
      order.assignedSlot = null;
      order.assignedMode = "auto";
      order.currentRunRecipeName = "";
      order.currentRunFirmwareName = "";
      order.targetSlot = null;
    });
  }
  device.currentJobId = "";
  device.queueOrderIds = [];
  device.completionConfirmationPending = false;
  device.activeRun = emptyActiveRun();
  device.uploadState = emptyUploadState();
  device.startupGuardUntil = "";
  device.telemetry.workStatus = options.connection === "disconnected" ? "offline" : "idle";
  device.telemetry.currentRecipe = "";
  device.telemetry.remainingSeconds = 0;
  device.telemetry.magTime = 0;
  device.telemetry.indTime = 0;
  device.telemetry.indPower = 0;
  device.telemetry.magPower = 0;
  device.telemetry.stepNo = 0;
  device.telemetry.mode = "";
  device.telemetry.status = "";
  device.telemetry.inductionStatus = "IDLE";
  device.telemetry.magnetronStatus = "IDLE";
  device.telemetry.ingredientsIndex = 0;
  device.telemetry.stirrer = DEFAULT_STIRRER_LEVEL;
  device.telemetry.pumpOn = false;
  device.telemetry.paused = false;
  return device;
}

function getOrderPayload(order) {
  return order?.kot?.properties || null;
}

function getOrderMeta(order) {
  return getOrderPayload(order)?.Order || {};
}

function getOrderCustomer(order) {
  return getOrderPayload(order)?.Customer || {};
}

function getOrderTaxes(order) {
  return Array.isArray(getOrderPayload(order)?.Tax) ? getOrderPayload(order).Tax : [];
}

function getOrderDiscounts(order) {
  return Array.isArray(getOrderPayload(order)?.Discount) ? getOrderPayload(order).Discount : [];
}

function getOrderItems(order) {
  return Array.isArray(getOrderPayload(order)?.OrderItem) ? getOrderPayload(order).OrderItem : [];
}

function getOrderCustomerName(order) {
  return getOrderCustomer(order).name || order.customerName || "Walk-in";
}

function getOrderItemCount(order) {
  return getOrderItems(order).length || order.itemCount || 0;
}

function getOrderTotal(order) {
  return toMoney(getOrderMeta(order).total ?? order.totalAmount ?? 0);
}

function getOrderType(order) {
  return getOrderMeta(order).order_type || "Kitchen";
}

function getOrderPaymentLabel(order) {
  return getOrderMeta(order).custom_payment_type || getOrderMeta(order).payment_type || order.source || "POS";
}

function getOrderCreatedDisplay(order) {
  const raw = getOrderMeta(order).created_on;
  if (!raw) return formatTimestamp(order.createdAt);
  const parsed = new Date(String(raw).replace(" ", "T"));
  if (Number.isNaN(parsed.getTime())) return raw;
  return parsed.toLocaleString([], {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });
}

function getOrderThumbUrl(order) {
  return order.previewImageDataUrl || "";
}

function getOrderStage(order) {
  const map = {
    pending: { label: "New", tone: "success" },
    queued: { label: "Preparing", tone: "queued" },
    starting: { label: "Preparing", tone: "queued" },
    cooking: { label: "Cooking", tone: "warning" },
    awaiting_confirmation: { label: "Ready", tone: "success" },
    completed: { label: "Completed", tone: "success" },
    aborted: { label: "Aborted", tone: "failed" },
    failed: { label: "Cancelled", tone: "failed" },
    cancelled: { label: "Cancelled", tone: "failed" }
  };
  return map[order.status] || { label: "New", tone: "queued" };
}

function renderOrderStageBadge(order) {
  const stage = getOrderStage(order);
  return `<span class="order-stage-badge ${stage.tone}">${escapeHtml(stage.label)}</span>`;
}

function renderContextOrderAction(order, perms) {
  if (order.status === "awaiting_confirmation") {
    return `<button class="primary-button small" data-action="mark-order-completed" data-order-id="${order.id}">Mark Completed</button>`;
  }
  if (["pending", "queued"].includes(order.status) && perms.canAssignQueues) {
    return `<button class="primary-button small" data-action="auto-assign-order" data-order-id="${order.id}">Assign Recipe</button>`;
  }
  if (order.assignedSlot) {
    return `<button class="secondary-button small" data-action="open-device-sheet" data-slot="${order.assignedSlot}">Device ${order.assignedSlot}</button>`;
  }
  return "";
}

function buildOrderPrintHtml(order) {
  const customer = getOrderCustomer(order);
  const meta = getOrderMeta(order);
  const items = getOrderItems(order);
  const taxes = getOrderTaxes(order);
  const discounts = getOrderDiscounts(order);
  return `
    <!doctype html>
    <html lang="en">
      <head>
        <meta charset="utf-8">
        <title>${escapeHtml(order.orderId)} Invoice</title>
        <style>
          body { font-family: Arial, sans-serif; padding: 24px; color: #222; }
          h1, h2, h3 { margin: 0 0 8px; }
          .block { margin-bottom: 18px; padding-bottom: 12px; border-bottom: 1px solid #ddd; }
          .row { display: flex; justify-content: space-between; gap: 12px; margin: 4px 0; }
          .muted { color: #666; }
        </style>
      </head>
      <body>
        <div class="block">
          <h1>On2Cook Order Invoice</h1>
          <div class="row"><strong>Order ID</strong><span>${escapeHtml(order.orderId)}</span></div>
          <div class="row"><strong>Created</strong><span>${escapeHtml(getOrderCreatedDisplay(order))}</span></div>
          <div class="row"><strong>Payment</strong><span>${escapeHtml(getOrderPaymentLabel(order))}</span></div>
        </div>
        <div class="block">
          <h3>Customer</h3>
          <div class="row"><span>Name</span><span>${escapeHtml(customer.name || "Walk-in")}</span></div>
          <div class="row"><span>Phone</span><span>${escapeHtml(customer.phone || "-")}</span></div>
          <div class="row"><span>Address</span><span>${escapeHtml(customer.address || "-")}</span></div>
        </div>
        <div class="block">
          <h3>Items</h3>
          ${items
            .map(
              (item) => `
                <div class="row"><strong>${escapeHtml(item.name || "Recipe")}</strong><strong>${formatCurrency(item.total || 0)}</strong></div>
                <div class="row muted"><span>Qty ${escapeHtml(item.quantity || 1)}</span><span>Tax ${formatCurrency(item.tax || 0)}</span></div>
              `
            )
            .join("")}
        </div>
        <div class="block">
          <h3>Summary</h3>
          <div class="row"><span>Subtotal</span><strong>${formatCurrency(meta.core_total || 0)}</strong></div>
          ${discounts.map((item) => `<div class="row"><span>${escapeHtml(item.title || "Discount")}</span><strong>- ${formatCurrency(item.amount || 0)}</strong></div>`).join("")}
          ${taxes.map((item) => `<div class="row"><span>${escapeHtml(item.title || "Tax")} (${escapeHtml(item.rate || 0)}%)</span><strong>${formatCurrency(item.amount || 0)}</strong></div>`).join("")}
          <div class="row"><span>Packaging</span><strong>${formatCurrency(meta.packaging_charge || 0)}</strong></div>
          <div class="row"><span>Delivery</span><strong>${formatCurrency(meta.delivery_charges || 0)}</strong></div>
          <div class="row"><span>Service</span><strong>${formatCurrency(meta.service_charge || 0)}</strong></div>
          <div class="row"><strong>Total</strong><strong>${formatCurrency(meta.total || 0)}</strong></div>
        </div>
      </body>
    </html>
  `;
}

function appendActivity(device, text, tone = "info", at = nowIso(), meta = null) {
  const item = {
    id: crypto.randomUUID(),
    text,
    tone,
    at,
    label: meta?.label || "",
    direction: meta?.direction || "",
    channel: meta?.channel || ""
  };
  device.activity = [item, ...(device.activity || [])].slice(0, 8);
  device.lastUpdatedAt = at;
  device.lastMessage = text;
}

function appendTransportActivity(device, direction, channel, message, at = nowIso()) {
  return;
}

function appendFlowActivity(device, text, tone = "info", at = nowIso()) {
  appendActivity(device, text, tone, at, {
    label: "FLOW",
    direction: "flow",
    channel: "stage"
  });
}

function mergeRecipeNames(device, recipeNames, at = nowIso()) {
  const merged = new Map();
  [...(device.availableRecipeNames || []), ...(device.syncedRecipeNames || []), ...recipeNames].forEach((name) => {
    const clean = String(name || "").trim();
    const key = normalizeRecipeNameKey(clean);
    if (clean && key && !merged.has(key)) {
      merged.set(key, clean);
    }
  });
  device.availableRecipeNames = [...merged.values()].sort((left, right) => left.localeCompare(right));
  device.recipeInventoryUpdatedAt = at;
}

function setInventoryCheckState(device, recipeNames = []) {
  device.uploadState = {
    ...emptyUploadState(),
    inventoryChecking: true,
    recipeNames: [...recipeNames],
    totalRecipes: recipeNames.length,
    summary: recipeNames.length > 0 ? `Checking existing recipes before upload (${recipeNames.length})` : "Checking existing recipes before upload"
  };
  device.lastUpdatedAt = nowIso();
}

function setUploadPlan(device, recipes, skippedRecipes = []) {
  const recipeNames = recipes.map((recipe) => recipe.firmwareName);
  const skippedNames = skippedRecipes.map((recipe) => recipe.firmwareName);
  if (recipeNames.length === 0) {
    device.uploadState = {
      ...emptyUploadState(),
      skippedRecipeNames: skippedNames,
      summary:
        skippedNames.length > 0
          ? `All selected recipes already exist on this device (${skippedNames.length})`
          : "No recipes need to be uploaded"
    };
    device.lastUpdatedAt = nowIso();
    return;
  }
  device.uploadState = {
    inventoryChecking: false,
    active: true,
    totalRecipes: recipeNames.length,
    currentIndex: 1,
    currentRecipeName: recipeNames[0],
    recipeNames,
    completedRecipeNames: [],
    skippedRecipeNames: skippedNames,
    summary: `Recipe uploading 1/${recipeNames.length}: ${recipeNames[0]}`
  };
  device.lastUpdatedAt = nowIso();
}

function updateUploadRecipeProgress(device, recipeName) {
  const recipeNames = Array.isArray(device.uploadState?.recipeNames) ? device.uploadState.recipeNames : [];
  const recipeIndex = recipeNames.findIndex((name) => normalizeRecipeNameKey(name) === normalizeRecipeNameKey(recipeName));
  const currentIndex = recipeIndex >= 0 ? recipeIndex + 1 : Math.max(1, Number(device.uploadState?.currentIndex) || 1);
  device.uploadState = {
    ...emptyUploadState(),
    ...device.uploadState,
    inventoryChecking: false,
    active: true,
    currentIndex,
    currentRecipeName: recipeName,
    summary: `Recipe uploading ${currentIndex}/${Math.max(1, Number(device.uploadState?.totalRecipes) || recipeNames.length || 1)}: ${recipeName}`
  };
  device.lastUpdatedAt = nowIso();
}

function completeUploadPlan(device, uploadedRecipeNames = []) {
  const uploadedKeys = new Set(uploadedRecipeNames.map((name) => normalizeRecipeNameKey(name)));
  const allRecipeNames = Array.isArray(device.uploadState?.recipeNames) ? device.uploadState.recipeNames : [];
  const skippedNames = Array.isArray(device.uploadState?.skippedRecipeNames) ? device.uploadState.skippedRecipeNames : [];
  device.uploadState = {
    ...emptyUploadState(),
    recipeNames: allRecipeNames,
    completedRecipeNames: allRecipeNames.filter((name) => uploadedKeys.has(normalizeRecipeNameKey(name))),
    skippedRecipeNames: skippedNames,
    summary:
      uploadedRecipeNames.length > 0
        ? `Recipe upload complete: ${uploadedRecipeNames.length} uploaded, ${skippedNames.length} skipped`
        : skippedNames.length > 0
          ? `All selected recipes already exist on this device (${skippedNames.length})`
          : "Recipe upload complete"
  };
  device.lastUpdatedAt = nowIso();
}

function failUploadPlan(device, message) {
  device.uploadState = {
    ...emptyUploadState(),
    summary: message
  };
  device.lastUpdatedAt = nowIso();
}

function showToast(message, tone = "info") {
  mutate((draft) => {
    draft.ui.toast = message;
    draft.ui.toastTone = tone;
  });
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => {
    mutate((draft) => {
      draft.ui.toast = "";
    });
  }, 3200);
}

function openModal(type, payload = {}) {
  mutate((draft) => {
    draft.ui.activeModal = { type, payload };
  });
}

function closeModal() {
  mutate((draft) => {
    draft.ui.activeModal = null;
  });
}

function setCloudRuntime(patch) {
  Object.assign(cloudRuntime, patch);
  render();
}

async function refreshCloudRuntime() {
  try {
    const status = await authService.getStatus();
    setCloudRuntime({
      ready: Boolean(status.ready),
      instance: status.instance || "",
      providers: status.providers || {},
      session: status.session || null,
      lastError: ""
    });
  } catch (error) {
    setCloudRuntime({
      ready: false,
      lastError: error.message || "Cloud status unavailable."
    });
  }
}

function createRecipeRecordFromCloudRow(row) {
  const recipeJson = (() => {
    try {
      return JSON.parse(row.firmware_recipe_json || "{}");
    } catch {
      return {};
    }
  })();
  const displayName = String(row.title || "Cloud Recipe").trim() || "Cloud Recipe";
  const firmwareName = sanitizeFirmwareName(
    Array.isArray(recipeJson?.name) ? recipeJson.name[0] : recipeJson?.name || displayName
  );
  if (!Array.isArray(recipeJson.name) || recipeJson.name.length === 0) {
    recipeJson.name = [firmwareName];
  } else {
    recipeJson.name[0] = firmwareName;
  }
  return {
    id: crypto.randomUUID(),
    type: row.status === "active" ? "final" : "base",
    baseRecipeId: row.base_recipe_name || null,
    source: "cloud",
    cloudRecordId: row.id,
    cloudUserId: row.user_id || "",
    recipeSignature: row.recipe_signature || recipeSignatureFromJson(recipeJson),
    zipName: row.base_zip_name || "",
    zipUrl: "",
    recipeTextEntryName: "",
    rawRecipeText: row.firmware_recipe_json || "",
    displayName,
    firmwareName,
    aliases: Array.from(new Set([displayName, firmwareName].filter(Boolean))),
    category: row.category || "Cloud",
    imageDataUrl: "",
    recipeEntries: [],
    recipeJson,
    selected: !row.mobile_hidden,
    cloudDeleted: Boolean(row.cloud_deleted),
    createdAt: row.created_at || nowIso(),
    updatedAt: row.updated_at || nowIso()
  };
}

function mergeCloudRecipesIntoStore(rows) {
  if (!Array.isArray(rows) || rows.length === 0) return 0;
  let mergedCount = 0;
  mutate((draft) => {
    rows.forEach((row) => {
      if (row.cloud_deleted) return;
      const signature =
        row.recipe_signature ||
        recipeSignatureFromJson(
          (() => {
            try {
              return JSON.parse(row.firmware_recipe_json || "{}");
            } catch {
              return {};
            }
          })()
        );
      const existing =
        draft.recipes.find((recipe) => String(recipe.cloudRecordId || "") === String(row.id || "")) ||
        draft.recipes.find((recipe) => String(recipe.recipeSignature || "") === String(signature)) ||
        draft.recipes.find((recipe) => String(recipe.displayName || "").trim() === String(row.title || "").trim());
      let libraryRecord = null;
      if (existing) {
        try {
          existing.recipeJson = JSON.parse(row.firmware_recipe_json || "{}");
        } catch {
          existing.recipeJson = existing.recipeJson || {};
        }
        existing.displayName = String(row.title || existing.displayName);
        existing.firmwareName = sanitizeFirmwareName(
          (Array.isArray(existing.recipeJson?.name) ? existing.recipeJson.name[0] : existing.recipeJson?.name) ||
            existing.displayName
        );
        existing.category = row.category || existing.category;
        existing.selected = !row.mobile_hidden;
        existing.cloudDeleted = Boolean(row.cloud_deleted);
        existing.cloudRecordId = row.id;
        existing.cloudUserId = row.user_id || existing.cloudUserId || "";
        existing.recipeSignature = signature;
        existing.updatedAt = row.updated_at || nowIso();
        libraryRecord = existing;
      } else {
        const created = createRecipeRecordFromCloudRow(row);
        draft.recipes.push(created);
        libraryRecord = created;
      }
      if (libraryRecord) {
        upsertImportedCatalogEntry(
          draft,
          buildCatalogEntryFromRecipe(libraryRecord, {
            catalogEntryId: `cloud-${row.id || signature}`,
            source: "cloud",
            sourceName: row.base_zip_name || libraryRecord.zipName || `${libraryRecord.displayName}.zip`,
            recipeText: row.firmware_recipe_json || libraryRecord.rawRecipeText || JSON.stringify(libraryRecord.recipeJson || {}),
            recipeTextEntryName: libraryRecord.recipeTextEntryName || "",
            imageDataUrl: libraryRecord.imageDataUrl || "",
            entries: Array.isArray(libraryRecord.recipeEntries) ? structuredClone(libraryRecord.recipeEntries) : [],
            zipUrl: libraryRecord.zipUrl || ""
          })
        );
      }
      mergedCount += 1;
    });
    syncSelectedRecipesToAllDevices(draft);
  });
  return mergedCount;
}

function seedAllowedRecipeIdsIfNeeded(device, snapshot) {
  if (Array.isArray(device.allowedRecipeIds)) return;
  device.allowedRecipeIds = getSelectedRecipes(snapshot).map((recipe) => recipe.id);
}

function syncSelectedRecipesToAllDevices(draft) {
  const selectedRecipeIds = draft.recipes.filter((recipe) => recipe.selected).map((recipe) => recipe.id);
  draft.devices.forEach((device) => {
    const existingIds = Array.isArray(device.allowedRecipeIds) ? device.allowedRecipeIds : [];
    device.allowedRecipeIds = Array.from(new Set([...existingIds, ...selectedRecipeIds]));
  });
}

function toggleRecipePermission(slot, recipeId) {
  mutate((draft) => {
    const device = draft.devices.find((item) => item.slot === Number(slot));
    if (!device) return draft;
    seedAllowedRecipeIdsIfNeeded(device, draft);
    if (device.allowedRecipeIds.includes(recipeId)) {
      device.allowedRecipeIds = device.allowedRecipeIds.filter((item) => item !== recipeId);
    } else {
      device.allowedRecipeIds.push(recipeId);
    }
  });
}

function moveOrderToHistory(order, status, note) {
  return {
    ...order,
    status,
    historyNote: note,
    createdAt: order.createdAt || nowIso()
  };
}

async function registerServiceWorker() {
  if (!("serviceWorker" in navigator)) return;
  const isLocalPreview = ["localhost", "127.0.0.1"].includes(location.hostname);
  if (isLocalPreview) {
    try {
      const registrations = await navigator.serviceWorker.getRegistrations();
      await Promise.all(registrations.map((registration) => registration.unregister()));
    } catch (error) {
      console.error("Unable to clear service workers for local preview.", error);
    }
    return;
  }
  if (!window.isSecureContext) return;
  try {
    await navigator.serviceWorker.register("./service-worker.js");
  } catch (error) {
    console.error("Unable to register service worker.", error);
  }
}

function ensureStatusPolling() {
  if (statusTimer) clearInterval(statusTimer);
  statusTimer = window.setInterval(() => {
    const snapshot = state();
    getConnectedDevices(snapshot).forEach((device) => {
      const session = ble.getSession(device.slot);
      if (
        session?.transfer ||
        session?.recipeListRequest ||
        (session?.run?.quietUntil && Date.now() < Number(session.run.quietUntil))
      ) {
        return;
      }
      ble.requestStatus(device.slot).catch(() => {
        // Ignore periodic polling errors.
      });
    });
  }, 10000);
}

function ensureIncomingOrderFeed() {
  if (orderFeedTimer) clearInterval(orderFeedTimer);
  orderFeedTimer = window.setInterval(() => {
    const snapshot = state();
    if (!Array.isArray(snapshot.orders?.incoming) || snapshot.orders.incoming.length === 0) return;
    let releasedOrder = null;
    mutate((draft) => {
      const nextOrder = draft.orders.incoming.shift();
      if (!nextOrder) return draft;
      const recipe =
        (nextOrder.activeRecipeId ? findRecipeById(draft, nextOrder.activeRecipeId) : null) ||
        findEffectiveRecipeForOrder(draft, nextOrder.recipeLookup || nextOrder.itemName);
      releasedOrder = decorateOrderRecord(
        {
          ...nextOrder,
          createdAt: nowIso(),
          status: "pending",
          assignedSlot: null,
          assignedMode: "auto",
          currentRunRecipeName: "",
          currentRunFirmwareName: "",
          targetSlot: null
        },
        recipe,
        draft.orders.current.length
      );
      draft.orders.current.unshift(releasedOrder);
    });
    if (!releasedOrder) return;
    if (state().settings.pendingAssignmentMode === "auto_route") {
      queueIdleWork();
    } else {
      showToast(`${releasedOrder.itemName} added to the pending queue`, "info");
    }
  }, 60000);
}

function applyTelemetry(device, parsed, message, at) {
  const readNumeric = (...values) => {
    for (const value of values) {
      if (value === undefined || value === null || value === "") continue;
      const next = Number(value);
      if (Number.isFinite(next)) return next;
    }
    return null;
  };
  const ingredientIndex = Number(parsed.ingredients || parsed.INGREDIENTS || parsed.STEPNO || parsed.stepNo || parsed.stepono) || 0;
  const mode = String(parsed.MODE || parsed.mode || "");
  const status = String(parsed.STATUS || parsed.status || "");
  const explicitWorkStatus = String(parsed.WORKSTATUS || parsed.workstatus || "");
  const instructionRun = String(parsed.INSTR_RUN || parsed.instr_run || "");
  const inductionStatus = String(parsed.INDQUICKSTART || parsed.indquickstart || "");
  const magnetronStatus = String(parsed.MAGQUICKSTART || parsed.magquickstart || "");
  const pumpSignal = String(parsed.PUMP || parsed.pump || "").trim().toUpperCase();
  const manualBusy =
    isQuickStartActive(inductionStatus || device.telemetry.inductionStatus) ||
    isQuickStartActive(magnetronStatus || device.telemetry.magnetronStatus) ||
    ["1", "ON", "RUN", "START"].includes(pumpSignal);
  const guardActive =
    Boolean(device.currentJobId) &&
    Boolean(device.startupGuardUntil) &&
    new Date(device.startupGuardUntil).getTime() > new Date(at || nowIso()).getTime();
  const derivedWorkStatus = mode
    ? mode.toLowerCase().includes("ingredient")
      ? "ingredient"
      : mode.toLowerCase().includes("cooking")
        ? "cooking"
        : mode.toLowerCase().includes("receipe")
          ? "recipe_selected"
          : ""
    : instructionRun.toUpperCase() === "START"
      ? "cooking"
      : manualBusy
        ? "manual"
      : "";
  device.telemetry.lastRaw = message;
  const nextWorkStatus = (explicitWorkStatus || derivedWorkStatus || device.telemetry.workStatus || "").toLowerCase();
  if (guardActive && nextWorkStatus === "idle" && !mode && !instructionRun && !String(parsed.RECIPE || "").trim()) {
    device.telemetry.workStatus = device.telemetry.workStatus === "cooking" ? "cooking" : "starting";
  } else {
    device.telemetry.workStatus = nextWorkStatus;
  }
  const magTime = readNumeric(parsed.magTime, parsed.MAGTIME, parsed.MAG_RUN, parsed.mag_run);
  const indTime = readNumeric(parsed.indTime, parsed.INDTIME, parsed.IND_RUN, parsed.ind_run);
  const stepNo = readNumeric(parsed.STEPNO, parsed.stepNo, parsed.stepono);
  const indPower = readNumeric(parsed.INDPOWER, parsed.indpower);
  const magPower = readNumeric(parsed.MAGPOWER, parsed.magpower);
  if (magTime !== null) device.telemetry.magTime = magTime;
  if (indTime !== null) device.telemetry.indTime = indTime;
  if (stepNo !== null) device.telemetry.stepNo = stepNo;
  if (indPower !== null) device.telemetry.indPower = indPower;
  if (magPower !== null) device.telemetry.magPower = magPower;
  device.telemetry.ingredientsIndex = ingredientIndex || device.telemetry.ingredientsIndex;
  device.telemetry.mode = mode || device.telemetry.mode;
  device.telemetry.status = status || device.telemetry.status;
  device.telemetry.inductionStatus = inductionStatus || device.telemetry.inductionStatus;
  device.telemetry.magnetronStatus = magnetronStatus || device.telemetry.magnetronStatus;
  const stirrerSignal = parsed.STIRRER || parsed.stirrer || "";
  const shouldPreferDefaultStirrer =
    Boolean(mode) ||
    Boolean((parsed.RECIPE && parsed.RECIPE !== "COMPLETE") || device.telemetry.currentRecipe) ||
    instructionRun.toUpperCase() === "START";
  device.telemetry.stirrer = normalizeStirrerTelemetryValue(
    stirrerSignal,
    device.telemetry.stirrer || DEFAULT_STIRRER_LEVEL,
    { preferDefault: shouldPreferDefaultStirrer }
  );
  if (pumpSignal) {
    device.telemetry.pumpOn = ["1", "ON", "RUN", "START"].includes(pumpSignal);
  }
  device.telemetry.currentRecipe =
    parsed.RECIPE && parsed.RECIPE !== "COMPLETE" ? parsed.RECIPE : device.telemetry.currentRecipe;
  if (device.telemetry.currentRecipe || mode || instructionRun.toUpperCase() === "START") {
    device.startupGuardUntil = "";
  }
  device.telemetry.paused = status.toUpperCase() === "PAUSE";
  device.telemetry.remainingSeconds = Math.max(device.telemetry.magTime || 0, device.telemetry.indTime || 0);
  if ((!guardActive && device.telemetry.workStatus === "idle") || String(parsed.RECIPE || "").toUpperCase() === "COMPLETE") {
    device.telemetry.currentRecipe = "";
    device.telemetry.mode = "";
    device.telemetry.status = "";
    device.telemetry.stepNo = 0;
    device.telemetry.ingredientsIndex = 0;
  }
  if (!device.telemetry.currentRecipe && !isQuickStartActive(device.telemetry.inductionStatus) && !isQuickStartActive(device.telemetry.magnetronStatus) && !device.telemetry.pumpOn && !explicitWorkStatus && !mode && !instructionRun) {
    device.telemetry.workStatus = "idle";
  }
  device.lastUpdatedAt = at;
  device.lastMessage = message;
}

function markSelectionAcknowledged(slot, recipeName) {
  mutate((draft) => {
    const device = draft.devices.find((item) => item.slot === Number(slot));
    if (!device) return draft;
    const order =
      draft.orders.current.find((item) => item.id === device.currentJobId) ||
      draft.orders.current.find(
        (item) => item.assignedSlot === device.slot && ["starting", "queued"].includes(item.status)
      );
    if (!order) return draft;
    device.currentJobId = order.id;
    device.startupGuardUntil = "";
    order.status = "cooking";
    order.currentRunFirmwareName = recipeName;
    device.activeRun = {
      ...device.activeRun,
      orderId: order.id,
      recipeId: order.activeRecipeId || device.activeRun.recipeId || "",
      displayName: order.itemName || device.activeRun.displayName || recipeName,
      firmwareName: recipeName,
      startedAt: device.activeRun.startedAt || nowIso(),
      durationSeconds: device.activeRun.durationSeconds || getRecipeDuration(getEffectiveRecipe(draft, order))
    };
    device.telemetry.workStatus = "cooking";
    mergeRecipeNames(device, [recipeName]);
    clearRecipeRetryTracking(device.slot, order.id);
    if (!String(device.lastMessage || "").includes(`recipe=${recipeName}`)) {
      appendActivity(device, `Recipe selection acknowledged: ${recipeName}`, "success");
    }
  });
}

function markRecipeComplete(slot, message, at = nowIso()) {
  let shouldQueue = false;
  mutate((draft) => {
    const device = draft.devices.find((item) => item.slot === Number(slot));
    if (!device) return draft;
    const orderIndex = draft.orders.current.findIndex((item) => item.id === device.currentJobId);
    const fallbackIndex =
      orderIndex >= 0
        ? orderIndex
        : draft.orders.current.findIndex(
            (item) => item.assignedSlot === device.slot && ["starting", "cooking", "awaiting_confirmation"].includes(item.status)
          );
    const order = fallbackIndex >= 0 ? draft.orders.current[fallbackIndex] : null;
    const recipe =
      (order ? getEffectiveRecipe(draft, order) : null) ||
      getRecipeForRunRecord(draft, device.activeRun) ||
      findRecipeByFirmwareName(draft, device.telemetry.currentRecipe || "");
    if (!order && !device.activeRun?.firmwareName && !device.telemetry.currentRecipe) {
      return draft;
    }
    const displayName =
      device.activeRun?.displayName ||
      order?.itemName ||
      recipe?.displayName ||
      device.telemetry.currentRecipe ||
      "Recipe";
    device.lastRun = {
      orderId: device.activeRun?.orderId || order?.id || "",
      recipeId: device.activeRun?.recipeId || order?.activeRecipeId || recipe?.id || "",
      displayName,
      firmwareName: device.activeRun?.firmwareName || order?.currentRunFirmwareName || recipe?.firmwareName || "",
      startedAt: device.activeRun?.startedAt || nowIso(),
      finishedAt: at,
      durationSeconds: device.activeRun?.durationSeconds || getRecipeDuration(recipe),
      outcome: "completed",
      note: "Completed on device",
      stepNo: Array.isArray(recipe?.recipeJson?.Instruction) ? recipe.recipeJson.Instruction.length : Number(device.telemetry.stepNo) || 0
    };
    device.currentJobId = "";
    device.completionConfirmationPending = false;
    device.activeRun = emptyActiveRun();
    device.startupGuardUntil = "";
    device.telemetry.workStatus = "idle";
    device.telemetry.remainingSeconds = 0;
    device.telemetry.paused = false;
    device.telemetry.currentRecipe = "";
    device.telemetry.mode = "";
    device.telemetry.status = "";
    device.telemetry.stepNo = 0;
    device.telemetry.ingredientsIndex = 0;
    appendActivity(device, `${displayName} completed on device`, "success", at);
    if (fallbackIndex >= 0) {
      const [completedOrder] = draft.orders.current.splice(fallbackIndex, 1);
      draft.orders.previous.unshift(moveOrderToHistory(completedOrder, "completed", "Completed on device"));
      device.historyOrderIds.unshift(completedOrder.id);
      clearRecipeRetryTracking(device.slot, completedOrder.id);
    }
    shouldQueue = device.connection === "connected";
  });
  if (shouldQueue) {
    queueIdleWork();
  }
}

function markRecipeAborted(slot, message, at = nowIso()) {
  let shouldQueue = false;
  mutate((draft) => {
    const device = draft.devices.find((item) => item.slot === Number(slot));
    if (!device) return draft;
    const orderIndex = draft.orders.current.findIndex((item) => item.id === device.currentJobId);
    const fallbackIndex =
      orderIndex >= 0
        ? orderIndex
        : draft.orders.current.findIndex(
            (item) => item.assignedSlot === device.slot && ["starting", "cooking", "awaiting_confirmation"].includes(item.status)
          );
    const order = fallbackIndex >= 0 ? draft.orders.current[fallbackIndex] : null;
    const recipe =
      (order ? getEffectiveRecipe(draft, order) : null) ||
      getRecipeForRunRecord(draft, device.activeRun) ||
      findRecipeByFirmwareName(draft, device.telemetry.currentRecipe || "");
    if (!order && !device.activeRun?.firmwareName && !device.telemetry.currentRecipe) {
      return draft;
    }
    const displayName =
      device.activeRun?.displayName ||
      order?.itemName ||
      recipe?.displayName ||
      device.telemetry.currentRecipe ||
      "Recipe";
    device.lastRun = {
      orderId: device.activeRun?.orderId || order?.id || "",
      recipeId: device.activeRun?.recipeId || order?.activeRecipeId || recipe?.id || "",
      displayName,
      firmwareName: device.activeRun?.firmwareName || order?.currentRunFirmwareName || recipe?.firmwareName || "",
      startedAt: device.activeRun?.startedAt || nowIso(),
      finishedAt: at,
      durationSeconds: device.activeRun?.durationSeconds || getRecipeDuration(recipe),
      outcome: "aborted",
      note: `Aborted by device (${message})`,
      stepNo: Number(device.telemetry.stepNo) || 0
    };
    device.currentJobId = "";
    device.completionConfirmationPending = false;
    device.activeRun = emptyActiveRun();
    device.startupGuardUntil = "";
    device.telemetry.workStatus = "idle";
    device.telemetry.remainingSeconds = 0;
    device.telemetry.paused = false;
    device.telemetry.currentRecipe = "";
    device.telemetry.mode = "";
    device.telemetry.status = "";
    device.telemetry.ingredientsIndex = 0;
    appendActivity(device, `${displayName} aborted on device`, "warning", at);
    if (fallbackIndex >= 0) {
      const [abortedOrder] = draft.orders.current.splice(fallbackIndex, 1);
      draft.orders.previous.unshift(moveOrderToHistory(abortedOrder, "aborted", "Aborted on device"));
      device.historyOrderIds.unshift(abortedOrder.id);
      clearRecipeRetryTracking(device.slot, abortedOrder.id);
    }
    shouldQueue = device.connection === "connected";
  });
  if (shouldQueue) {
    queueIdleWork();
  }
}

async function refreshDeviceRecipeInventory(slot, options = {}) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") return [];
  if (!options.force && inventoryIsFresh(device)) {
    return device.availableRecipeNames || [];
  }
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    setInventoryCheckState(draftDevice, options.recipeNames || []);
  });
  const inventoryNames = await ble.readRecipesAvailable(Number(slot), {
    timeoutMs: options.timeoutMs || 4500
  });
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    mergeRecipeNames(draftDevice, inventoryNames);
    draftDevice.uploadState = {
      ...draftDevice.uploadState,
      inventoryChecking: false,
      summary:
        inventoryNames.length > 0
          ? `Checked device recipes: ${inventoryNames.length} found`
          : "Checked device recipes: none reported"
    };
  });
  return inventoryNames;
}

async function ensureRecipesAvailableOnDevice(slot, recipes, options = {}) {
  const device = getDevice(slot);
  if (!device) return [];
  if (!Array.isArray(recipes) || recipes.length === 0) return [];
  let inventoryConfirmed = false;

  try {
    await refreshDeviceRecipeInventory(slot, {
      force: options.forceInventory !== false,
      timeoutMs: options.inventoryTimeoutMs || 3200,
      recipeNames: recipes.map((recipe) => recipe.firmwareName)
    });
    inventoryConfirmed = true;
  } catch (error) {
    if (options.allowBlindUpload === true) {
      if (!options.silent) {
        showToast(`Could not read device recipe list: ${error.message}`, "warning");
      }
    } else {
      throw new Error("Could not confirm recipes on the device, so sync was skipped to avoid overwriting stored recipes.");
    }
  }

  const latestDevice = getDevice(slot);
  const knownRecipeKeys = getKnownDeviceRecipeKeys(latestDevice || device);
  const missingRecipes = recipes.filter((recipe) => {
    const recipeKey = normalizeRecipeNameKey(recipe.firmwareName);
    return !knownRecipeKeys.has(recipeKey);
  });
  const skippedRecipes = recipes.filter((recipe) => {
    const recipeKey = normalizeRecipeNameKey(recipe.firmwareName);
    return knownRecipeKeys.has(recipeKey);
  });

  if (missingRecipes.length === 0) {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      setUploadPlan(draftDevice, [], skippedRecipes);
    });
    if (!options.silent) {
      showToast(`Device ${slot} already has the required recipe set`, "success");
    }
    return [];
  }

  if (!inventoryConfirmed && options.allowBlindUpload !== true) {
    throw new Error("Device recipe list is unavailable, so upload was skipped to protect stored recipes.");
  }

  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    setUploadPlan(draftDevice, missingRecipes, skippedRecipes);
  });

  await ble.syncRecipes(Number(slot), missingRecipes, (progress) => {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      draftDevice.lastMessage = `Syncing ${progress.recipeName} (${progress.current}/${progress.total})`;
      draftDevice.lastUpdatedAt = nowIso();
    });
  }, {
    overwriteExisting: options.overwriteExisting === true
  });

  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    mergeRecipeNames(
      draftDevice,
      missingRecipes.map((recipe) => recipe.firmwareName)
    );
    missingRecipes.forEach((recipe) => {
      draftDevice.syncedRecipeSignatures[normalizeRecipeNameKey(recipe.firmwareName)] = getRecipeSignature(recipe);
    });
    completeUploadPlan(
      draftDevice,
      missingRecipes.map((recipe) => recipe.firmwareName)
    );
  });

  return missingRecipes;
}

async function uploadRecipeForRunRetry(slot, recipe) {
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    setUploadPlan(draftDevice, [recipe], []);
  });

  await ble.syncRecipes(
    Number(slot),
    [recipe],
    (progress) => {
      mutate((draft) => {
        const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
        if (!draftDevice) return draft;
        draftDevice.lastMessage = `Syncing ${progress.recipeName} (${progress.current}/${progress.total})`;
        draftDevice.lastUpdatedAt = nowIso();
      });
    },
    { overwriteExisting: false }
  );

  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    mergeRecipeNames(draftDevice, [recipe.firmwareName]);
    draftDevice.syncedRecipeSignatures[normalizeRecipeNameKey(recipe.firmwareName)] = getRecipeSignature(recipe);
    completeUploadPlan(draftDevice, [recipe.firmwareName]);
  });
}

async function retryOrderRunAfterUpload(slot, orderId, recipe) {
  const idleBeforeRetry = await ble.waitForIdleStatus(Number(slot), {
    timeoutMs: 3200,
    pollEveryMs: 650,
    forceFresh: true,
    description: "idle status before retry start"
  });

  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    appendFlowActivity(draftDevice, "Idle confirmed before retry start", "info", idleBeforeRetry.at);
  });

  await ble.runRecipe(Number(slot), recipe.firmwareName, {
    autoStartAfterIngredient: true,
    statusDelayMs: 650,
    fallbackMs: 1800
  });

  mutate((draft) => {
    const draftOrder = draft.orders.current.find((item) => item.id === orderId);
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftOrder || !draftDevice) return draft;
    draftOrder.status = "starting";
    draftOrder.currentRunRecipeName = recipe.displayName;
    draftOrder.currentRunFirmwareName = recipe.firmwareName;
    draftDevice.currentJobId = orderId;
    draftDevice.startupGuardUntil = new Date(Date.now() + 8000).toISOString();
    draftDevice.telemetry.currentRecipe = recipe.firmwareName;
    draftDevice.telemetry.workStatus = "starting";
    draftDevice.telemetry.mode = "Starting";
    draftDevice.lastMessage = `recipe=${recipe.firmwareName} re-sent after upload`;
    draftDevice.lastUpdatedAt = nowIso();
    appendFlowActivity(draftDevice, `Run retried for ${recipe.firmwareName}`, "success");
  });
}

function handleTransportEvents() {
  ble.addEventListener("device-connected", (event) => {
    const { slot, browserDeviceId, bluetoothName } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      const knownDevice = draft.devices.find(
        (item) => item.slot !== Number(slot) && item.browserDeviceId && item.browserDeviceId === browserDeviceId
      );
      if (knownDevice) {
        mergeRecipeNames(device, [...(knownDevice.availableRecipeNames || []), ...(knownDevice.syncedRecipeNames || [])]);
        device.syncedRecipeSignatures = {
          ...(knownDevice.syncedRecipeSignatures || {}),
          ...(device.syncedRecipeSignatures || {})
        };
      }
      device.browserDeviceId = browserDeviceId;
      device.bluetoothName = bluetoothName;
      device.connection = "connected";
      device.baselineRecipeSyncPending = true;
      device.uploadState = emptyUploadState();
      device.telemetry.workStatus = "idle";
      appendActivity(device, `Connected to ${bluetoothName || browserDeviceId}`, "success");
    });
    ble.sendDateTime(slot).catch(() => {});
    window.setTimeout(() => {
      const session = ble.getSession(slot);
      if (!session || session.transfer || session.run) return;
      ble.requestStatus(slot).catch(() => {});
    }, 250);
    window.setTimeout(() => {
      const session = ble.getSession(slot);
      if (!session || session.transfer || (session.run?.quietUntil && Date.now() < Number(session.run.quietUntil))) return;
      ble.requestFirmwareVersion(slot).catch(() => {});
    }, 1400);
    window.setTimeout(() => {
      const session = ble.getSession(slot);
      if (!session || !session.server?.connected || session.transfer || session.run) return;
      syncBaselineRecipesOnConnect(Number(slot), { silent: true }).catch((error) => {
        console.error("Baseline connect sync failed.", error);
      });
    }, 500);
  });

  ble.addEventListener("device-disconnected", (event) => {
    const { slot } = event.detail;
    mutate((draft) => {
      const device = resetDeviceRuntimeState(draft, slot, { connection: "disconnected" });
      if (!device) return draft;
      device.connection = "disconnected";
      device.baselineRecipeSyncPending = false;
      device.telemetry.workStatus = "offline";
      device.telemetry.disconnectedAt = nowIso();
      appendActivity(device, "Device disconnected. Active work returned to pending.", "warning");
    });
  });

  ble.addEventListener("command-sent", (event) => {
    const { slot, channel, message, at } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      appendTransportActivity(device, "tx", channel, message, at);
    });
  });

  ble.addEventListener("command-acknowledged", (event) => {
    const { slot, type, at } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      if (type === "recipe-select") {
        appendFlowActivity(device, "Recipe command acknowledged by device", "info", at);
      } else if (type === "ingredients-advance") {
        appendFlowActivity(device, "Ingredient advance acknowledged by device", "info", at);
      } else if (type === "instruction-ack") {
        appendFlowActivity(device, "Instruction acknowledgement accepted by device", "info", at);
      }
    });
  });

  ble.addEventListener("device-message", (event) => {
    const { slot, channel, message, at } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      if (String(channel || "").toLowerCase() === "file") {
        return draft;
      }
      device.lastUpdatedAt = at;
      if (!device.uploadState?.active && !device.uploadState?.inventoryChecking) {
        device.lastMessage = message;
      }
      appendTransportActivity(device, "rx", channel, message, at);
    });
  });

  ble.addEventListener("telemetry", (event) => {
    const { slot, parsed, message, at } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      applyTelemetry(device, parsed, message, at);
    });
  });

  ble.addEventListener("firmware-version", (event) => {
    const { slot, firmwareVersion, at } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      device.telemetry.firmwareVersion = firmwareVersion;
      appendActivity(device, `Firmware ${firmwareVersion}`, "info", at);
    });
  });

  ble.addEventListener("recipe-selection-acknowledged", (event) => {
    markSelectionAcknowledged(event.detail.slot, event.detail.recipeName);
  });

  ble.addEventListener("instruction-complete", (event) => {
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(event.detail.slot));
      if (!device) return draft;
      appendActivity(device, `Instruction step ${device.telemetry.stepNo || "?"} completed`, "info", event.detail.at);
    });
  });

  ble.addEventListener("instruction-acknowledged", (event) => {
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(event.detail.slot));
      if (!device) return draft;
      appendActivity(device, `Acknowledgement sent for step ${event.detail.stepNo}`, "success", event.detail.at);
    });
  });

  ble.addEventListener("ingredients-advanced", (event) => {
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(event.detail.slot));
      if (!device) return draft;
      appendFlowActivity(device, `Ingredient stage completed for ${event.detail.recipeName}`, "success", event.detail.at);
    });
  });

  ble.addEventListener("recipe-stop-signal", (event) => {
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(event.detail.slot));
      if (!device) return draft;
      appendFlowActivity(device, `Device emitted ${event.detail.message}`, "warning", event.detail.at);
    });
    markRecipeAborted(event.detail.slot, event.detail.message, event.detail.at);
  });

  ble.addEventListener("recipe-complete", (event) => {
    markRecipeComplete(event.detail.slot, event.detail.message, event.detail.at);
  });

  ble.addEventListener("recipe-missing", async (event) => {
    const slot = Number(event.detail.slot);
    const snapshot = state();
    const device = snapshot.devices.find((item) => item.slot === slot) || null;
    const order =
      (device ? getCurrentJob(snapshot, device) : null) ||
      snapshot.orders.current.find((item) => item.assignedSlot === slot && ["starting", "queued"].includes(item.status)) ||
      null;
    const recipe = order ? getEffectiveRecipe(snapshot, order) : null;
    const retryKey = order ? getRecipeRetryKey(slot, order.id) : "";
    const priorRetries = retryKey ? recipeMissingRetryCounts.get(retryKey) || 0 : 0;

    if (order && recipe && priorRetries < 1) {
      recipeMissingRetryCounts.set(retryKey, priorRetries + 1);
      mutate((draft) => {
        const draftDevice = draft.devices.find((item) => item.slot === slot);
        if (!draftDevice) return draft;
        appendActivity(draftDevice, `${recipe.firmwareName} was missing. Uploading just this recipe and retrying.`, "warning");
      });
      try {
        await uploadRecipeForRunRetry(slot, recipe);
        await retryOrderRunAfterUpload(slot, order.id, recipe);
        showToast(`Uploaded ${recipe.displayName} to Device ${slot} and retried the run`, "success");
        return;
      } catch (error) {
        mutate((draft) => {
          const draftDevice = draft.devices.find((item) => item.slot === slot);
          if (!draftDevice) return draft;
          appendActivity(draftDevice, `Automatic recipe upload retry failed: ${error.message}`, "error");
        });
      }
    }

    if (retryKey) {
      recipeMissingRetryCounts.delete(retryKey);
    }
    mutate((draft) => {
      const deviceAfterReset = resetDeviceRuntimeState(draft, slot);
      if (!deviceAfterReset) return draft;
      appendActivity(deviceAfterReset, "Device reported that the selected recipe is missing", "error");
    });
    showToast(`Device ${slot} does not have that recipe yet`, "error");
  });

  ble.addEventListener("transfer-started", (event) => {
    const { slot, recipeName } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      updateUploadRecipeProgress(device, recipeName);
    });
  });

  ble.addEventListener("transfer-progress", () => {});

  ble.addEventListener("transfer-complete", (event) => {
    const { slot, recipeName } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      if (!device.syncedRecipeNames.includes(recipeName)) {
        device.syncedRecipeNames.push(recipeName);
      }
      const recipe = findRecipeByFirmwareName(draft, recipeName);
      if (recipe) {
        device.syncedRecipeSignatures[normalizeRecipeNameKey(recipeName)] = getRecipeSignature(recipe);
      }
      const completed = new Set([...(device.uploadState?.completedRecipeNames || []), recipeName]);
      device.uploadState = {
        ...device.uploadState,
        completedRecipeNames: [...completed]
      };
      device.lastUpdatedAt = nowIso();
    });
  });

  ble.addEventListener("transfer-retry", (event) => {
    const { slot, recipeName } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      device.uploadState = {
        ...device.uploadState,
        summary: `Retrying recipe upload: ${recipeName}`
      };
      device.lastUpdatedAt = nowIso();
    });
  });
}

async function connectDevice(slot) {
  mutate((draft) => {
    const device = draft.devices.find((item) => item.slot === Number(slot));
    if (!device) return draft;
    device.connection = "connecting";
    device.lastMessage = "Opening Bluetooth chooser";
  });
  try {
    const rememberedId = getDevice(slot)?.browserDeviceId || "";
    await ble.connect(Number(slot), rememberedId);
    showToast(`Device ${slot} connected`, "success");
  } catch (error) {
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      device.connection = "disconnected";
      device.lastMessage = error.message;
    });
    showToast(error.message, "error");
  }
}

async function disconnectDevice(slot) {
  await ble.disconnect(Number(slot));
  mutate((draft) => {
    const device = resetDeviceRuntimeState(draft, slot, { connection: "disconnected" });
    if (!device) return draft;
    device.connection = "disconnected";
    device.baselineRecipeSyncPending = false;
    device.telemetry.workStatus = "offline";
    appendActivity(device, "Device disconnected. Active work returned to pending.", "warning");
  });
  showToast(`Device ${slot} disconnected`, "info");
}

function canRunOnDevice(snapshot, order, device, recipe) {
  if (!device.enabled || device.connection !== "connected") return false;
  if (!recipe) return false;
  return isRecipeAllowedOnDevice(snapshot, device, recipe.id);
}

function pickBestDevice(snapshot, order) {
  const recipe = getEffectiveRecipe(snapshot, order);
  const candidates = getConnectedDevices(snapshot)
    .filter((device) => canRunOnDevice(snapshot, order, device, recipe))
    .sort((left, right) => {
      const etaDiff = getDeviceEta(snapshot, left) - getDeviceEta(snapshot, right);
      return etaDiff !== 0 ? etaDiff : left.slot - right.slot;
    });
  return candidates[0] || null;
}

async function startOrderFlow(orderId, preferredSlot = null) {
  const snapshot = state();
  const order = snapshot.orders.current.find((item) => item.id === orderId);
  if (!order) return;
  const recipe = getEffectiveRecipe(snapshot, order);
  if (!recipe) {
    showToast(`No selected recipe matches ${order.itemName}`, "error");
    return;
  }
  const device = preferredSlot ? snapshot.devices.find((item) => item.slot === Number(preferredSlot)) : pickBestDevice(snapshot, order);
  if (!device) {
    showToast("No connected device is ready for this recipe", "warning");
    return;
  }
  if (!canRunOnDevice(snapshot, order, device, recipe)) {
    showToast(`${recipe.displayName} is not enabled on Device ${device.slot}`, "error");
    return;
  }
  const liveSession = ble.getSession(device.slot);
  if (liveSession?.transfer) {
    showToast(`Device ${device.slot} is still syncing recipes. Try again in a moment.`, "warning");
    return;
  }

  const busy = device.currentJobId || device.queueOrderIds.length > 0 || device.completionConfirmationPending || hasLiveRuntime(device);
  if (busy) {
    mutate((draft) => {
      const draftOrder = draft.orders.current.find((item) => item.id === orderId);
      const draftDevice = draft.devices.find((item) => item.slot === device.slot);
      if (!draftOrder || !draftDevice) return draft;
      clearOrderFromDeviceAssignments(draft, orderId, device.slot);
      if (!draftDevice.queueOrderIds.includes(orderId)) {
        draftDevice.queueOrderIds.push(orderId);
      }
      draftOrder.status = "queued";
      draftOrder.assignedSlot = device.slot;
      draftOrder.assignedMode = preferredSlot ? "device" : "auto";
      draftOrder.activeRecipeId = recipe.id;
      draftOrder.currentRunRecipeName = recipe.displayName;
      draftOrder.currentRunFirmwareName = recipe.firmwareName;
      appendActivity(draftDevice, `${draftOrder.itemName} queued${hasLiveRuntime(device) ? " behind live device work" : ""}`, "info");
    });
    showToast(`${order.itemName} queued on Device ${device.slot}`, "info");
    return;
  }

  mutate((draft) => {
    const draftOrder = draft.orders.current.find((item) => item.id === orderId);
    const draftDevice = draft.devices.find((item) => item.slot === device.slot);
    if (!draftOrder || !draftDevice) return draft;
    clearOrderFromDeviceAssignments(draft, orderId, device.slot);
    draftOrder.status = "starting";
    draftOrder.assignedSlot = device.slot;
    draftOrder.assignedMode = preferredSlot ? "device" : "auto";
    draftOrder.activeRecipeId = recipe.id;
    draftOrder.currentRunRecipeName = recipe.displayName;
    draftOrder.currentRunFirmwareName = recipe.firmwareName;
    draftDevice.currentJobId = orderId;
    draftDevice.activeRun = {
      orderId,
      recipeId: recipe.id,
      displayName: recipe.displayName,
      firmwareName: recipe.firmwareName,
      startedAt: nowIso(),
      durationSeconds: getRecipeDuration(recipe)
    };
    draftDevice.startupGuardUntil = new Date(Date.now() + 8000).toISOString();
    draftDevice.telemetry.currentRecipe = recipe.firmwareName;
    appendActivity(draftDevice, `Preparing ${recipe.firmwareName}`, "info");
  });

  try {
    const idleBeforeSync = await ble.waitForIdleStatus(device.slot, {
      timeoutMs: 3200,
      pollEveryMs: 650,
      forceFresh: true,
      description: "idle status before recipe start"
    });
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === device.slot);
      if (!draftDevice) return draft;
      appendFlowActivity(draftDevice, "Idle confirmed before recipe start", "info", idleBeforeSync.at);
    });
    const latestDevice = getDevice(device.slot) || device;
    const knownRecipeKeys = getKnownDeviceRecipeKeys(latestDevice);
    const recipeKey = normalizeRecipeNameKey(recipe.firmwareName);
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === device.slot);
      if (!draftDevice) return draft;
      if (knownRecipeKeys.has(recipeKey)) {
        appendFlowActivity(draftDevice, `${recipe.firmwareName} already present on device`, "info");
      } else {
        appendFlowActivity(draftDevice, "Recipe inventory not confirmed; attempting direct run without upload", "warning");
      }
    });
    await ble.runRecipe(device.slot, recipe.firmwareName, {
      autoStartAfterIngredient: true,
      statusDelayMs: 650,
      fallbackMs: 1800
    });
    mutate((draft) => {
      const draftOrder = draft.orders.current.find((item) => item.id === orderId);
      const draftDevice = draft.devices.find((item) => item.slot === device.slot);
      if (!draftDevice || !draftOrder) return draft;
      draftOrder.status = "starting";
      draftDevice.telemetry.workStatus = "starting";
      draftDevice.telemetry.mode = draftDevice.telemetry.mode || "Starting";
      draftDevice.lastMessage = `recipe=${recipe.firmwareName} sent, waiting for ingredient stage`;
      draftDevice.lastUpdatedAt = nowIso();
      appendFlowActivity(draftDevice, `Run command sent for ${recipe.firmwareName}`, "success");
    });
  } catch (error) {
    mutate((draft) => {
      const draftOrder = draft.orders.current.find((item) => item.id === orderId);
      const draftDevice = resetDeviceRuntimeState(draft, device.slot, { releaseOrders: false });
      if (draftOrder) {
        draftOrder.status = "pending";
        draftOrder.assignedSlot = null;
        draftOrder.assignedMode = preferredSlot ? "device" : "auto";
        draftOrder.currentRunRecipeName = "";
        draftOrder.currentRunFirmwareName = "";
        draftOrder.targetSlot = null;
      }
      if (draftDevice) {
        appendActivity(draftDevice, `Start failed: ${error.message}`, "error");
      }
    });
    showToast(error.message, "error");
  }
}

async function syncSelectedRecipesToDevice(slot, options = {}) {
  const snapshot = state();
  const device = snapshot.devices.find((item) => item.slot === Number(slot));
  if (!device) return;
  const recipes = getDeviceSyncRecipes(snapshot, device);
  if (recipes.length === 0) {
    if (!options.silent) showToast("No selected recipes are enabled for this device", "warning");
    return;
  }
  if (!options.silent) {
    showToast(`Checking ${recipes.length} allowed recipes for Device ${slot}`, "info");
  }
  try {
    const uploadedRecipes = await ensureRecipesAvailableOnDevice(slot, recipes, {
      silent: options.silent,
      forceInventory: options.forceInventory !== false,
      overwriteExisting: options.overwriteExisting === true
    });
    if (!options.silent) {
      showToast(
        uploadedRecipes.length > 0
          ? `Device ${slot} synced (${uploadedRecipes.length} new recipe${uploadedRecipes.length === 1 ? "" : "s"})`
          : `Device ${slot} already had those recipes`,
        "success"
      );
    }
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      draftDevice.baselineRecipeSyncPending = false;
      draftDevice.startupGuardUntil = "";
    });
  } catch (error) {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      failUploadPlan(draftDevice, `Recipe sync failed: ${error.message}`);
      appendActivity(draftDevice, `Recipe sync failed: ${error.message}`, "error");
    });
    if (!options.silent) showToast(error.message, "error");
    throw error;
  }
}

async function syncBaselineRecipesOnConnect(slot, options = {}) {
  const snapshot = state();
  const device = snapshot.devices.find((item) => item.slot === Number(slot));
  if (!device || device.connection !== "connected") return [];
  const recipes = getSelectedRecipes(snapshot);
  if (recipes.length === 0) {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      draftDevice.baselineRecipeSyncPending = false;
      draftDevice.startupGuardUntil = "";
      appendActivity(draftDevice, "No baseline recipes were selected for connect sync", "warning");
    });
    queueIdleWork();
    return [];
  }

  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.baselineRecipeSyncPending = true;
    draftDevice.startupGuardUntil = new Date(Date.now() + 5 * 60 * 1000).toISOString();
    setInventoryCheckState(
      draftDevice,
      recipes.map((recipe) => recipe.firmwareName)
    );
  });

  try {
    const uploadedRecipes = await ensureRecipesAvailableOnDevice(Number(slot), recipes, {
      silent: true,
      forceInventory: true,
      overwriteExisting: false
    });

    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      draftDevice.baselineRecipeSyncPending = false;
      draftDevice.startupGuardUntil = "";
      if (uploadedRecipes.length === 0) {
        draftDevice.uploadState = {
          ...draftDevice.uploadState,
          summary: `Device already has all ${recipes.length} selected recipes`
        };
      }
    });
    queueIdleWork();
    return uploadedRecipes;
  } catch (error) {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      draftDevice.startupGuardUntil = "";
      draftDevice.baselineRecipeSyncPending = false;
      failUploadPlan(draftDevice, `Connect sync failed: ${error.message}`);
      appendActivity(draftDevice, `Connect sync failed: ${error.message}`, "error");
    });
    if (!options.silent) {
      showToast(`Device ${slot} connect sync failed: ${error.message}`, "error");
    }
    throw error;
  }
}

async function runDeviceRecipe(slot, recipeId) {
  const snapshot = state();
  const recipe = findRecipeById(snapshot, recipeId);
  if (!recipe) return;
  const order = decorateOrderRecord({
    id: crypto.randomUUID(),
    orderId: `#M${Math.floor(Math.random() * 900 + 100)}`,
    itemName: recipe.displayName,
    recipeLookup: recipe.displayName,
    quantity: "1 batch",
    source: "Manual",
    specialInstructions: "",
    accentColor: "#f47b20",
    createdAt: nowIso(),
    status: "pending",
    assignedSlot: null,
    assignedMode: "device",
    activeRecipeId: recipe.id,
    currentRunRecipeName: recipe.displayName,
    currentRunFirmwareName: recipe.firmwareName,
    targetSlot: Number(slot),
    manual: true,
    historyNote: ""
  }, recipe, snapshot.orders.current.length);
  mutate((draft) => {
    draft.orders.current.unshift(order);
  });
  await startOrderFlow(order.id, Number(slot));
}

async function completeIngredientStage(slot) {
  const snapshot = state();
  const device = snapshot.devices.find((item) => item.slot === Number(slot));
  if (!device) return;
  await ble.sendIngredientsValue(Number(slot), 100);
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    appendActivity(draftDevice, "Sent ingredients=100 to complete ingredient stage", "info");
  });
  showToast(`ingredients=100 sent to Device ${slot}`, "success");
}

async function acknowledgeInstructionStep(slot) {
  const snapshot = state();
  const device = snapshot.devices.find((item) => item.slot === Number(slot));
  if (!device) return;
  const stepNo = Math.max(1, Number(device.telemetry.stepNo) || 1);
  await ble.sendAddConfirm(Number(slot), stepNo);
  showToast(`Acknowledgement sent for step ${stepNo} on Device ${slot}`, "success");
}

function refreshStatusSoon(slot, delayMs = 350) {
  window.setTimeout(() => {
    ble.requestStatus(Number(slot)).catch(() => {});
  }, delayMs);
}

function mapStirrerSpeedLabel(level) {
  const normalized = String(level || "").trim().toUpperCase();
  if (normalized === "1" || normalized === "LOW") return "LOW";
  if (normalized === "2" || normalized === "MED") return "MED";
  if (normalized === "3" || normalized === "HIGH") return "HIGH";
  if (normalized === "4" || normalized === "VERY_HIGH" || normalized === "VHIGH") return "VERY_HIGH";
  if (normalized === "0" || normalized === "OFF") return "OFF";
  if (normalized === "ON") return DEFAULT_STIRRER_LEVEL;
  return normalized || DEFAULT_STIRRER_LEVEL;
}

function normalizeStirrerTelemetryValue(value, currentLevel = DEFAULT_STIRRER_LEVEL, options = {}) {
  const normalizedValue = String(value || "").trim().toUpperCase();
  const current = mapStirrerSpeedLabel(currentLevel || DEFAULT_STIRRER_LEVEL);
  if (!normalizedValue) return current || DEFAULT_STIRRER_LEVEL;
  if (normalizedValue === "OFF") return "OFF";
  if (normalizedValue.startsWith("ON,")) {
    return mapStirrerSpeedLabel(normalizedValue.split(",")[1] || DEFAULT_STIRRER_LEVEL);
  }
  if (["LOW", "MED", "HIGH", "VERY_HIGH", "VHIGH", "1", "2", "3", "4"].includes(normalizedValue)) {
    return mapStirrerSpeedLabel(normalizedValue);
  }
  if (normalizedValue === "ON") {
    if (options.preferDefault) return DEFAULT_STIRRER_LEVEL;
    return current === "OFF" ? DEFAULT_STIRRER_LEVEL : current;
  }
  return current || DEFAULT_STIRRER_LEVEL;
}

function formatStirrerDisplay(level) {
  const normalized = mapStirrerSpeedLabel(level);
  if (normalized === "LOW") return "Speed 1";
  if (normalized === "MED") return "Speed 2 (Default)";
  if (normalized === "HIGH") return "Speed 3";
  if (normalized === "VERY_HIGH") return "Speed 4";
  return "Off";
}

async function startManualInduction(slot) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  await ble.startInduction(Number(slot));
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.inductionStatus = "START";
    draftDevice.telemetry.workStatus = "manual";
    appendActivity(draftDevice, "Manual Mode: induction start sent", "success");
  });
  refreshStatusSoon(slot);
  showToast(`Manual induction started on Device ${slot}`, "success");
}

async function stopManualInduction(slot) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  await ble.stopInduction(Number(slot));
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.inductionStatus = "STOP";
    draftDevice.telemetry.indTime = 0;
    appendActivity(draftDevice, "Manual Mode: induction stop sent", "warning");
  });
  refreshStatusSoon(slot);
  showToast(`Manual induction stop sent to Device ${slot}`, "info");
}

async function adjustManualInductionPower(slot, delta) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  if (!isQuickStartActive(device.telemetry.inductionStatus) && Number(device.telemetry.indTime || 0) <= 0) {
    showToast("Start induction first, then adjust power", "warning");
    return;
  }
  await ble.changeInductionPower(Number(slot), delta);
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    appendActivity(draftDevice, `Manual Mode: induction power ${delta > 0 ? "+" : ""}${delta}`, "info");
  });
  refreshStatusSoon(slot);
  showToast(`Induction power ${delta > 0 ? "increased" : "decreased"} on Device ${slot}`, "success");
}

async function startManualMagnetron(slot) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  await ble.startMagnetron(Number(slot));
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.magnetronStatus = "START";
    draftDevice.telemetry.workStatus = "manual";
    appendActivity(draftDevice, "Manual Mode: microwave start sent", "success");
  });
  refreshStatusSoon(slot);
  showToast(`Microwave started on Device ${slot}`, "success");
}

async function stopManualMagnetron(slot) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  await ble.stopMagnetron(Number(slot));
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.magnetronStatus = "STOP";
    draftDevice.telemetry.magTime = 0;
    appendActivity(draftDevice, "Manual Mode: microwave stop sent", "warning");
  });
  refreshStatusSoon(slot);
  showToast(`Microwave stop sent to Device ${slot}`, "info");
}

async function setManualStirrer(slot, speedLabel) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  const normalized = mapStirrerSpeedLabel(speedLabel);
  await ble.setStirrer(Number(slot), normalized);
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.stirrer = normalized;
    if (normalized !== "OFF") {
      draftDevice.telemetry.workStatus = "manual";
    }
    appendActivity(
      draftDevice,
      normalized === "OFF" ? "Manual Mode: stirrer stop sent" : `Manual Mode: stirrer ${normalized} sent`,
      normalized === "OFF" ? "warning" : "success"
    );
  });
  refreshStatusSoon(slot);
  showToast(
    normalized === "OFF" ? `Stirrer stopped on Device ${slot}` : `Stirrer ${formatStirrerDisplay(normalized)} sent to Device ${slot}`,
    "success"
  );
}

async function startManualPump(slot, units) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  const safeUnits = Math.max(1, Math.trunc(Number(units) || 0));
  await ble.startPump(Number(slot), safeUnits);
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.pumpOn = true;
    draftDevice.telemetry.workStatus = "manual";
    appendActivity(draftDevice, `Manual Mode: pump start sent (${safeUnits} x 10 ml)`, "success");
  });
  refreshStatusSoon(slot);
  showToast(`Pump started on Device ${slot}`, "success");
}

async function stopManualPump(slot) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  await ble.stopPump(Number(slot));
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.pumpOn = false;
    appendActivity(draftDevice, "Manual Mode: pump stop sent", "warning");
  });
  refreshStatusSoon(slot);
  showToast(`Pump stop sent to Device ${slot}`, "info");
}

function queueIdleWork() {
  const snapshot = state();
  snapshot.devices.forEach((device) => {
    const liveSession = ble.getSession(device.slot);
    const startupGuardActive =
      Boolean(device.startupGuardUntil) && new Date(device.startupGuardUntil).getTime() > Date.now();
    if (
      device.connection !== "connected" ||
      liveSession?.transfer ||
      device.baselineRecipeSyncPending ||
      startupGuardActive ||
      device.completionConfirmationPending ||
      device.currentJobId ||
      hasLiveRuntime(device)
    ) {
      return;
    }
    const queuedOrderId = device.queueOrderIds[0];
    if (queuedOrderId) {
      mutate((draft) => {
        const draftDevice = draft.devices.find((item) => item.slot === device.slot);
        if (!draftDevice) return draft;
        draftDevice.queueOrderIds = draftDevice.queueOrderIds.filter((item) => item !== queuedOrderId);
      });
      startOrderFlow(queuedOrderId, device.slot);
      return;
    }
    const latest = state();
    if (latest.settings.pendingAssignmentMode !== "auto_route") return;
    const pending = latest.orders.current
      .filter((order) => order.status === "pending")
      .filter((order) => canRunOnDevice(latest, order, device, getEffectiveRecipe(latest, order)))
      .filter((order) => !order.targetSlot || order.targetSlot === device.slot)
      .sort((left, right) => new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime());
    if (pending.length > 0 && latest.settings.queueMode === "global_auto") {
      startOrderFlow(pending[0].id, device.slot);
      return;
    }
    if (pending.length > 0 && latest.settings.queueMode === "per_device") {
      startOrderFlow(pending[0].id, device.slot);
    }
  });
}

function confirmCompletion(slot) {
  mutate((draft) => {
    const device = draft.devices.find((item) => item.slot === Number(slot));
    if (!device) return draft;
    const orderIndex = draft.orders.current.findIndex((item) => item.id === device.currentJobId);
    if (orderIndex >= 0) {
      const [order] = draft.orders.current.splice(orderIndex, 1);
      draft.orders.previous.unshift(moveOrderToHistory(order, "completed", "Operator confirmed completion popup"));
      device.historyOrderIds.unshift(order.id);
      clearRecipeRetryTracking(device.slot, order.id);
    }
    device.currentJobId = "";
    device.completionConfirmationPending = false;
    device.startupGuardUntil = "";
    device.telemetry.workStatus = "idle";
    device.telemetry.remainingSeconds = 0;
    appendActivity(device, "Completion acknowledged. Scheduler unlocked.", "success");
  });
  queueIdleWork();
}

function markOrderCompleted(orderId, note = "Order marked completed from order details") {
  let completedName = "";
  mutate((draft) => {
    const orderIndex = draft.orders.current.findIndex((item) => item.id === orderId);
    if (orderIndex < 0) return draft;
    const [order] = draft.orders.current.splice(orderIndex, 1);
    completedName = order.itemName;
    releaseOrderFromAllDevices(draft, orderId);
    draft.orders.previous.unshift(moveOrderToHistory(order, "completed", note));
  });
  if (completedName) {
    closeModal();
    showToast(`${completedName} moved to previous orders`, "success");
    queueIdleWork();
  }
}

function printOrder(orderId) {
  const order = getAnyOrderById(state(), orderId);
  if (!order) return;
  const popup = window.open("", "_blank", "width=860,height=900");
  if (!popup) {
    showToast("Popup blocked. Allow popups to print the invoice.", "warning");
    return;
  }
  popup.document.open();
  popup.document.write(buildOrderPrintHtml(order));
  popup.document.close();
  popup.focus();
  popup.print();
}

async function abortCurrentRecipe(slot) {
  showToast("This firmware flow keeps stop=100 device-driven. Use the device-side stop/completion flow instead.", "warning");
}

async function restartRecipe(slot) {
  showToast("Restart is disabled in the web app until a device-side restart flow is defined without stop=100.", "warning");
}

function updateNestedSetting(path, value) {
  mutate((draft) => {
    const keys = path.split(".");
    let cursor = draft.settings;
    for (let index = 0; index < keys.length - 1; index += 1) {
      cursor = cursor[keys[index]];
    }
    cursor[keys[keys.length - 1]] = value;
  });
}

function renderStatusPill(status) {
  const map = {
    pending: "pending",
    queued: "queued",
    starting: "starting",
    cooking: "cooking",
    awaiting_confirmation: "queued",
    completed: "complete",
    aborted: "failed",
    failed: "failed",
    cancelled: "failed"
  };
  const tone = map[status] || "pending";
  return `<span class="status-pill ${tone}">${escapeHtml(status.replaceAll("_", " "))}</span>`;
}

function renderControlTabs(snapshot) {
  const tabs = [
    ["orders", "Orders"],
    ["recipes", "Recipes"],
    ["queue", "Queue"],
    ["manual", "Manual Mode"],
    ["global", "Global Recipes"]
  ];
  return `
    <nav class="tab-strip">
      ${tabs
        .map(
          ([id, label]) => `
            <button class="tab-button ${snapshot.ui.activeTab === id ? "active" : ""}" data-action="switch-tab" data-tab="${id}">
              ${label}
            </button>
          `
        )
        .join("")}
    </nav>
  `;
}

function renderGlobalRecipesTab(snapshot) {
  const recipeCatalog = getRecipeCatalog(snapshot);
  const search = String(snapshot.ui.globalRecipeSearch || "").trim().toLowerCase();
  const picked = new Set(snapshot.ui.globalRecipePickedIds || []);
  const filteredCatalog = recipeCatalog.filter((entry) => {
    if (!search) return true;
    return (
      String(entry.recipeName || "").toLowerCase().includes(search) ||
      String(entry.zipName || "").toLowerCase().includes(search)
    );
  });
  return `
    <section class="stack-section">
      <div class="mini-title">Global recipe library</div>
      <div class="settings-card">
        <div class="queue-summary">
          <div class="summary-chip">Library ${recipeCatalog.length}</div>
          <div class="summary-chip">Showing ${filteredCatalog.length}</div>
          <div class="summary-chip">Picked ${picked.size}</div>
        </div>
        <label class="field-label">
          Search the full recipe library
          <input class="field-input" type="search" data-input="global-recipe-search" value="${escapeHtml(snapshot.ui.globalRecipeSearch || "")}" placeholder="Search 500+ recipes">
        </label>
        <div class="action-row">
          <button class="primary-button small" data-action="global-recipes-add-to-list">Add picked to Recipe list</button>
          <button class="secondary-button small" data-action="global-recipes-add-to-orders">Add picked to Orders</button>
          <button class="secondary-button small" data-action="global-recipes-remove-from-list">Remove picked from Recipe list</button>
          <button class="secondary-button small" data-action="global-recipes-clear-picks">Clear picks</button>
        </div>
        <p class="subtle">The bundled ten recipes stay in place. Use this screen to bring additional ZIP recipes into the Recipe list or create pending Orders from them.</p>
      </div>
    </section>
    <section class="stack-section">
      <div class="mini-title">All recipes</div>
      ${
        filteredCatalog.length === 0
          ? `<div class="empty-card">No recipes match that search.</div>`
          : filteredCatalog
              .map((entry) => {
                const existingRecipe = findRecipeForGlobalCatalogEntry(snapshot, entry);
                const statusLabel = existingRecipe
                  ? existingRecipe.source === "seed"
                    ? "Bundled"
                    : existingRecipe.selected
                      ? "In Recipe list"
                      : "Imported"
                  : entry.source === "imported"
                    ? "Local library"
                    : "Library only";
                const statusTone = existingRecipe ? (existingRecipe.source === "seed" || existingRecipe.selected ? "cooking" : "queued") : "pending";
                return `
                  <article class="queue-device-card">
                    <div class="row space">
                      <div>
                        <strong>${escapeHtml(entry.recipeName)}</strong>
                        <div class="subtle">${escapeHtml(entry.zipName)}</div>
                      </div>
                      <div class="chip-row">
                        <span class="status-pill ${statusTone}">${escapeHtml(statusLabel)}</span>
                        <span class="chip-button ${picked.has(entry.id) ? "selected" : ""}" data-action="toggle-global-recipe-pick" data-recipe-catalog-id="${escapeHtml(entry.id)}">
                          ${picked.has(entry.id) ? "Picked" : "Pick"}
                        </span>
                      </div>
                    </div>
                  </article>
                `;
              })
              .join("")
      }
    </section>
  `;
}

function renderCurrentOrders(snapshot, perms) {
  const pendingCount = snapshot.orders.current.filter((order) => order.status === "pending").length;
  const queuedCount = snapshot.orders.current.filter((order) => order.status === "queued").length;
  const incomingCount = Array.isArray(snapshot.orders.incoming) ? snapshot.orders.incoming.length : 0;
  const sections = [
    ["Pending", snapshot.orders.current.filter((order) => order.status === "pending")],
    ["Queued", snapshot.orders.current.filter((order) => order.status === "queued")],
    ["Starting / Cooking", snapshot.orders.current.filter((order) => ["starting", "cooking", "awaiting_confirmation"].includes(order.status))]
  ];
  return `
    <div class="section-head">
      <div class="segment-row">
        <button class="segment ${snapshot.ui.orderMode === "current" ? "active" : ""}" data-action="switch-order-mode" data-mode="current">Current</button>
        <button class="segment ${snapshot.ui.orderMode === "previous" ? "active" : ""}" data-action="switch-order-mode" data-mode="previous">Previous</button>
      </div>
      <button class="primary-button" data-action="open-manual-order">Manual Order</button>
    </div>
    <section class="stack-section">
      <div class="queue-summary">
        <div class="summary-chip">Pending ${pendingCount}</div>
        <div class="summary-chip">Queued ${queuedCount}</div>
        <div class="summary-chip">Next feed ${incomingCount}</div>
      </div>
      <p class="subtle">This demo starts with 5 pending orders. One additional order is released every minute until the remaining demo orders are exhausted.</p>
    </section>
    ${sections
      .map(
        ([title, orders]) => `
          <section class="stack-section">
            <div class="mini-title">${title}</div>
            ${
              orders.length
                ? orders.map((order) => renderOrderCard(snapshot, order, perms)).join("")
                : `<div class="empty-card">No ${title.toLowerCase()} items right now.</div>`
            }
          </section>
        `
      )
      .join("")}
  `;
}

function renderPreviousOrders(snapshot) {
  return `
    <div class="section-head">
      <div class="segment-row">
        <button class="segment ${snapshot.ui.orderMode === "current" ? "active" : ""}" data-action="switch-order-mode" data-mode="current">Current</button>
        <button class="segment ${snapshot.ui.orderMode === "previous" ? "active" : ""}" data-action="switch-order-mode" data-mode="previous">Previous</button>
      </div>
      <button class="secondary-button" data-action="export-state">Export DB</button>
    </div>
    <section class="stack-section">
      <div class="mini-title">Completed and historical runs</div>
      ${
        snapshot.orders.previous.length
          ? snapshot.orders.previous.map((order) => renderHistoryCard(order)).join("")
          : `<div class="empty-card">No previous orders yet.</div>`
      }
    </section>
  `;
}

function renderOrderCard(snapshot, order, perms) {
  const connectedDevices = getConnectedDevices(snapshot);
  const allowManualRouting = perms.canAssignQueues && ["pending", "queued"].includes(order.status);
  const deviceChips = connectedDevices
    .map(
      (device) => `
        <button class="chip-button ${order.assignedSlot === device.slot ? "selected" : ""}" data-action="assign-order-device" data-order-id="${order.id}" data-slot="${device.slot}">
          Device ${device.slot}
        </button>
      `
    )
    .join("");
  const assigned = order.assignedSlot ? `Device ${order.assignedSlot}` : "Auto";
  const orderType = getOrderType(order);
  const thumbUrl = getOrderThumbUrl(order);
  return `
    <article class="order-card order-card-rich">
      <div class="row space order-card-topline">
        <div class="chip-row">
          ${renderOrderStageBadge(order)}
          <span class="order-type-pill">${escapeHtml(orderType)}</span>
        </div>
        <span class="subtle">${escapeHtml(formatAgo(order.createdAt))}</span>
      </div>
      <div class="order-card-main">
        <div class="order-card-copy">
          <div class="order-id">Order ID: ${escapeHtml(order.orderId)}</div>
          <h3>${escapeHtml(order.itemName)}</h3>
          <div class="order-stat-line"><span>Customer</span><strong>${escapeHtml(getOrderCustomerName(order))}</strong></div>
          <div class="order-stat-line"><span>Items</span><strong>${escapeHtml(getOrderItemCount(order))}</strong></div>
          <div class="order-stat-line"><span>Total</span><strong>${escapeHtml(formatCurrency(getOrderTotal(order)))}</strong></div>
        </div>
        <div class="order-card-side">
          ${
            thumbUrl
              ? `<img class="order-thumb" src="${thumbUrl}" alt="${escapeHtml(order.itemName)}">`
              : `<div class="order-thumb placeholder">${escapeHtml(order.itemName.slice(0, 1))}</div>`
          }
          <span class="order-source-pill">${escapeHtml(order.source)}</span>
        </div>
      </div>
      <div class="meta-grid">
        <span>${escapeHtml(order.quantity)}</span>
        <span>${escapeHtml(assigned)}</span>
        <span>${escapeHtml(getOrderPaymentLabel(order))}</span>
      </div>
      <p class="subtle">${escapeHtml(order.specialInstructions || "No special instructions")}</p>
      <div class="action-row">
        <button class="secondary-button small" data-action="open-order-details" data-order-id="${order.id}">Details</button>
        ${renderContextOrderAction(order, perms)}
      </div>
      ${allowManualRouting ? `<div class="chip-row">${deviceChips || `<span class="subtle">No connected devices</span>`}</div>` : ""}
    </article>
  `;
}

function renderHistoryCard(order) {
  return `
    <article class="order-card compact order-card-rich">
      <div class="row space order-card-topline">
        <div class="chip-row">
          ${renderOrderStageBadge(order)}
          <span class="order-type-pill">${escapeHtml(getOrderType(order))}</span>
        </div>
        <span class="subtle">${escapeHtml(formatTimestamp(order.createdAt))}</span>
      </div>
      <div class="order-card-main">
        <div class="order-card-copy">
          <div class="order-id">Order ID: ${escapeHtml(order.orderId)}</div>
          <h3>${escapeHtml(order.itemName)}</h3>
          <div class="order-stat-line"><span>Customer</span><strong>${escapeHtml(getOrderCustomerName(order))}</strong></div>
          <div class="order-stat-line"><span>Items</span><strong>${escapeHtml(getOrderItemCount(order))}</strong></div>
          <div class="order-stat-line"><span>Total</span><strong>${escapeHtml(formatCurrency(getOrderTotal(order)))}</strong></div>
        </div>
        <div class="order-card-side">
          ${
            getOrderThumbUrl(order)
              ? `<img class="order-thumb" src="${getOrderThumbUrl(order)}" alt="${escapeHtml(order.itemName)}">`
              : `<div class="order-thumb placeholder">${escapeHtml(order.itemName.slice(0, 1))}</div>`
          }
          <span class="order-source-pill">${escapeHtml(order.source)}</span>
        </div>
      </div>
      <div class="meta-grid">
        <span>${escapeHtml(order.quantity)}</span>
        <span>${order.assignedSlot ? `Device ${order.assignedSlot}` : "Unassigned"}</span>
        <span>${escapeHtml(getOrderPaymentLabel(order))}</span>
      </div>
      <p class="subtle">${escapeHtml(order.historyNote || "Completed")}</p>
      <div class="action-row">
        <button class="secondary-button small" data-action="open-order-details" data-order-id="${order.id}">Details</button>
      </div>
    </article>
  `;
}

function renderQueueTab(snapshot) {
  const pending = snapshot.orders.current.filter((order) => order.status === "pending");
  const queued = snapshot.orders.current.filter((order) => order.status === "queued");
  const incoming = Array.isArray(snapshot.orders.incoming) ? snapshot.orders.incoming : [];
  return `
    <section class="stack-section">
      <div class="mini-title">Pending and queue strategy</div>
      <div class="settings-card">
        <label class="field-label">
          New pending orders
          <select class="field-input" data-setting-path="pendingAssignmentMode">
            <option value="manual_review" ${snapshot.settings.pendingAssignmentMode === "manual_review" ? "selected" : ""}>Hold in pending for manual review</option>
            <option value="auto_route" ${snapshot.settings.pendingAssignmentMode === "auto_route" ? "selected" : ""}>Auto assign when a device is ready</option>
          </select>
        </label>
        <label class="field-label">
          Auto-routing rule
          <select class="field-input" data-setting-path="queueMode">
            <option value="global_auto" ${snapshot.settings.queueMode === "global_auto" ? "selected" : ""}>Global shortest-time routing</option>
            <option value="per_device" ${snapshot.settings.queueMode === "per_device" ? "selected" : ""}>Per-device only</option>
          </select>
        </label>
        <p class="subtle">Manual review keeps fresh orders visible in Pending until you assign a device or tap Auto Assign. Auto assign starts routing pending items as soon as an eligible device is free.</p>
        <p class="subtle">Global routing sends the next pending item to the connected device with the lowest estimated finish time. Per-device routing only auto-starts items that already target a specific device.</p>
      </div>
    </section>
    <section class="stack-section">
      <div class="mini-title">Pending and queued</div>
      <div class="queue-summary">
        <div class="summary-chip">Pending ${pending.length}</div>
        <div class="summary-chip">Queued ${queued.length}</div>
        <div class="summary-chip">Incoming ${incoming.length}</div>
      </div>
      <p class="subtle">Manual review keeps new orders in Pending until you assign them. Auto assign starts only after you enable it here.</p>
      ${
        snapshot.devices
          .map((device) => {
            const queueOrders = getQueueOrders(snapshot, device);
            return `
              <article class="queue-device-card">
                <div class="row space">
                  <strong>${escapeHtml(device.displayName)}</strong>
                  <span class="subtle">${device.connection}</span>
                </div>
                ${
                  queueOrders.length
                    ? queueOrders
                        .map(
                          (order) => `
                            <div class="queue-item">
                              <span>${escapeHtml(order.itemName)}</span>
                              <span class="subtle">${escapeHtml(order.orderId)}</span>
                            </div>
                          `
                        )
                        .join("")
                    : `<div class="empty-card">No queued items on this device.</div>`
                }
              </article>
            `;
          })
          .join("")
      }
    </section>
  `;
}

function renderManualModeTab(snapshot) {
  const device = getManualModeTarget(snapshot);
  const selectedSlot = Number(snapshot.ui.manualMode?.slot || device?.slot || 1);
  const pumpUnits = Math.max(1, Number(snapshot.ui.manualMode?.pumpUnits) || 10);
  const inductionStatus = getManualStatus(device?.telemetry.inductionStatus || "IDLE");
  const magnetronStatus = getManualStatus(device?.telemetry.magnetronStatus || "IDLE");
  const stirrerLabel = formatStirrerDisplay(device?.telemetry.stirrer || DEFAULT_STIRRER_LEVEL);
  const pumpLabel = device?.telemetry.pumpOn ? "ON" : "OFF";
  return `
    <section class="stack-section">
      <div class="mini-title">Manual Mode</div>
      <div class="settings-card">
        <div class="chip-row">
          ${snapshot.devices
            .map(
              (item) => `
                <button class="chip-button ${selectedSlot === item.slot ? "selected" : ""}" data-action="select-manual-device" data-slot="${item.slot}">
                  Device ${item.slot} ${item.connection === "connected" ? "Connected" : "Offline"}
                </button>
              `
            )
            .join("")}
        </div>
        <p class="subtle">Manual Mode talks directly to the selected device. Queue automation treats manual activity as busy.</p>
      </div>
    </section>
    ${
      !device
        ? `<section class="stack-section"><div class="empty-card">No device slots are available.</div></section>`
        : `
          <section class="stack-section">
            <div class="mini-title">Device status</div>
            <div class="settings-card">
              <div class="detail-info-list">
                <div class="detail-info-row"><span>Device</span><strong>${escapeHtml(device.displayName)}</strong></div>
                <div class="detail-info-row"><span>Connection</span><strong>${escapeHtml(device.connection)}</strong></div>
                <div class="detail-info-row"><span>Induction</span><strong>${escapeHtml(inductionStatus)}</strong></div>
                <div class="detail-info-row"><span>Microwave</span><strong>${escapeHtml(magnetronStatus)}</strong></div>
                <div class="detail-info-row"><span>Remaining</span><strong>${secondsLabel(device.telemetry.indTime || 0)}</strong></div>
                <div class="detail-info-row"><span>Power</span><strong>${escapeHtml((device.telemetry.indPower || 0) + "%")}</strong></div>
                <div class="detail-info-row"><span>Microwave time</span><strong>${secondsLabel(device.telemetry.magTime || 0)}</strong></div>
                <div class="detail-info-row"><span>Microwave power</span><strong>${escapeHtml((device.telemetry.magPower || 0) + "%")}</strong></div>
                <div class="detail-info-row"><span>Stirrer</span><strong>${escapeHtml(stirrerLabel)}</strong></div>
                <div class="detail-info-row"><span>Pump</span><strong>${escapeHtml(pumpLabel)}</strong></div>
                <div class="detail-info-row"><span>Last updated</span><strong>${escapeHtml(device.lastUpdatedAt ? formatAgo(device.lastUpdatedAt) : "Never")}</strong></div>
              </div>
              <p class="subtle">${escapeHtml(device.lastMessage || "Waiting for device status")}</p>
              <div class="action-row top-gap">
                ${
                  device.connection === "connected"
                    ? `<button class="secondary-button small" data-action="manual-request-status" data-slot="${device.slot}">Refresh status</button>`
                    : `<button class="primary-button small" data-action="connect-device" data-slot="${device.slot}">Connect device</button>`
                }
                <button class="secondary-button small" data-action="open-device-sheet" data-slot="${device.slot}">Open details</button>
              </div>
            </div>
          </section>
          <section class="stack-section">
            <div class="mini-title">Induction control</div>
            <div class="settings-card">
              <div class="action-row">
                <button class="primary-button" data-action="manual-induction-start" data-slot="${device.slot}">Start induction</button>
                <button class="danger-button" data-action="manual-induction-stop" data-slot="${device.slot}">Stop induction</button>
              </div>
              <div class="action-row">
                <button class="secondary-button" data-action="manual-induction-power" data-slot="${device.slot}" data-delta="-10">-10 Power</button>
                <button class="secondary-button" data-action="manual-induction-power" data-slot="${device.slot}" data-delta="10">+10 Power</button>
              </div>
              <p class="subtle">Firmware accepts induction power changes in 10% steps while quick-start induction is running.</p>
            </div>
          </section>
          <section class="stack-section">
            <div class="mini-title">Microwave control</div>
            <div class="settings-card">
              <div class="action-row">
                <button class="primary-button" data-action="manual-magnetron-start" data-slot="${device.slot}">Start microwave</button>
                <button class="danger-button" data-action="manual-magnetron-stop" data-slot="${device.slot}">Stop microwave</button>
              </div>
              <p class="subtle">This uses the firmware quick-start microwave commands over BLE.</p>
            </div>
          </section>
          <section class="stack-section">
            <div class="mini-title">Stirrer control</div>
            <div class="settings-card">
              <div class="action-row">
                <button class="secondary-button" data-action="manual-stirrer-speed" data-slot="${device.slot}" data-speed="LOW">Speed 1</button>
                <button class="secondary-button" data-action="manual-stirrer-speed" data-slot="${device.slot}" data-speed="MED">Speed 2</button>
                <button class="secondary-button" data-action="manual-stirrer-speed" data-slot="${device.slot}" data-speed="HIGH">Speed 3</button>
                <button class="secondary-button" data-action="manual-stirrer-speed" data-slot="${device.slot}" data-speed="VERY_HIGH">Speed 4</button>
                <button class="danger-button" data-action="manual-stirrer-stop" data-slot="${device.slot}">Stop stirrer</button>
              </div>
              <p class="subtle">Normal mode keeps the stirrer ON at speed 2 by default. Manual Mode sends temporary overrides only: speed 1-4 maps to LOW, MED, HIGH, and VERY_HIGH, and Stop stirrer sends OFF.</p>
            </div>
          </section>
          <section class="stack-section">
            <div class="mini-title">Pump control</div>
            <div class="settings-card">
              <label class="field-label">
                Pump amount (10 ml units)
                <input class="field-input" type="number" min="1" step="1" value="${pumpUnits}" data-input="manual-pump-units">
              </label>
              <div class="action-row">
                <button class="primary-button" data-action="manual-pump-start" data-slot="${device.slot}">Start pump</button>
                <button class="danger-button" data-action="manual-pump-stop" data-slot="${device.slot}">Stop pump</button>
              </div>
              <p class="subtle"><code>PUMP=ON,n</code> uses the firmware's 10 ml tick units. For example, <code>10</code> sends roughly 100 ml.</p>
            </div>
          </section>
        `
    }
  `;
}

function renderRecipeCard(snapshot, recipe, perms) {
  const selectedClass = recipe.selected ? "selected" : "";
  const devices = snapshot.devices
    .map((device) => {
      const allowed = isRecipeAllowedOnDevice(snapshot, device, recipe.id);
      return `
        <button class="chip-button ${allowed ? "selected" : ""}" data-action="toggle-recipe-device" data-slot="${device.slot}" data-recipe-id="${recipe.id}">
          D${device.slot}
        </button>
      `;
    })
    .join("");
  return `
    <article class="recipe-card ${selectedClass}">
      <div class="recipe-thumb ${recipe.imageDataUrl ? "has-image" : ""}">
        ${recipe.imageDataUrl ? `<img src="${recipe.imageDataUrl}" alt="${escapeHtml(recipe.displayName)}">` : `<span>${escapeHtml(recipe.displayName.slice(0, 1))}</span>`}
      </div>
      <div class="recipe-copy">
        <div class="row space">
          <h3>${escapeHtml(recipe.displayName)}</h3>
          ${renderStatusPill(recipe.type === "final" ? "completed" : recipe.selected ? "cooking" : "pending")}
        </div>
        <div class="subtle">${escapeHtml(recipe.firmwareName)} | ${escapeHtml(recipe.source)}</div>
        <div class="subtle">Aliases: ${escapeHtml(recipe.aliases.join(", "))}</div>
        <div class="action-row">
          <button class="secondary-button small" data-action="toggle-recipe-selected" data-recipe-id="${recipe.id}">
            ${recipe.selected ? "Disable" : "Enable"}
          </button>
          ${
            perms.canCreateFinalRecipes
              ? `<button class="primary-button small" data-action="${recipe.type === "final" ? "edit-final-recipe" : "create-final-recipe"}" data-recipe-id="${recipe.id}">
                  ${recipe.type === "final" ? "Edit Final" : "Create Final"}
                </button>`
              : ""
          }
          ${
            recipe.type === "final" && perms.canCreateFinalRecipes
              ? `<button class="danger-button small" data-action="delete-final-recipe" data-recipe-id="${recipe.id}">Delete</button>`
              : ""
          }
        </div>
        ${perms.canEditDevicePermissions ? `<div class="chip-row">${devices}</div>` : ""}
      </div>
    </article>
  `;
}

function renderRecipesTab(snapshot, perms) {
  const selectedRecipes = snapshot.recipes.filter((recipe) => recipe.selected && recipe.type !== "final");
  const finalRecipes = snapshot.recipes.filter((recipe) => recipe.type === "final");
  const mode = snapshot.ui.recipeMode;
  return `
    <section class="stack-section">
      <div class="section-head">
        <div class="segment-row">
          <button class="segment ${mode === "selected" ? "active" : ""}" data-action="switch-recipe-mode" data-mode="selected">Selected</button>
          <button class="segment ${mode === "final" ? "active" : ""}" data-action="switch-recipe-mode" data-mode="final">Final Modified</button>
          <button class="segment ${mode === "import" ? "active" : ""}" data-action="switch-recipe-mode" data-mode="import">Import</button>
        </div>
      </div>
      ${
        mode === "selected"
          ? selectedRecipes.map((recipe) => renderRecipeCard(snapshot, recipe, perms)).join("") || `<div class="empty-card">No selected recipes.</div>`
          : ""
      }
      ${
        mode === "final"
          ? finalRecipes.map((recipe) => renderRecipeCard(snapshot, recipe, perms)).join("") || `<div class="empty-card">No final modified recipes yet.</div>`
          : ""
      }
      ${
        mode === "import"
          ? `
            <div class="settings-card">
              <div class="mini-title">Recipe finder import</div>
              <a class="link-button" href="${escapeHtml(snapshot.settings.recipeFinder.baseUrl)}" target="_blank" rel="noreferrer">Open recipe finder</a>
              <form class="inline-form" data-form="import-zip-url">
                <input class="field-input" type="url" name="zipUrl" placeholder="Paste a direct recipe ZIP URL" value="${escapeHtml(snapshot.settings.recipeFinder.lastZipUrl)}" required>
                <button class="primary-button small" type="submit">Import URL</button>
              </form>
              <label class="file-field">
                <span>Import a local recipe ZIP</span>
                <input type="file" accept=".zip" data-input="recipe-zip-file">
              </label>
              <p class="subtle">Imported ZIPs are added to the local recipe library, selected for cooking, and made available for device assignment. ZIP importer expects one JSON recipe file and can also pick up one image for the card thumbnail.</p>
            </div>
          `
          : ""
      }
    </section>
  `;
}

function renderMoreTab(snapshot, perms) {
  const currentUser = getCurrentUser(snapshot);
  return `
    <section class="stack-section">
      <div class="mini-title">Workspace</div>
      <div class="settings-card">
        <label class="field-label">
          Active user
          <select class="field-input" data-setting-path="__user__">
            ${snapshot.users
              .map(
                (user) => `
                  <option value="${user.id}" ${user.id === snapshot.currentUserId ? "selected" : ""}>
                    ${escapeHtml(user.displayName)} (${escapeHtml(user.role)})
                  </option>
                `
              )
              .join("")}
          </select>
        </label>
        <div class="subtle">Current role: ${escapeHtml(currentUser.role)}</div>
        <div class="toggle-row">
          <label><input type="checkbox" data-setting-path="orderScreenEnabled" ${snapshot.settings.orderScreenEnabled ? "checked" : ""}> Order screen enabled</label>
          <label><input type="checkbox" data-setting-path="operatorActsAsManager" ${snapshot.settings.operatorActsAsManager ? "checked" : ""}> Operator may act as kitchen manager</label>
        </div>
        <div class="action-row top-gap">
          <button class="secondary-button small" data-action="switch-tab" data-tab="global">Open Global Recipes</button>
        </div>
      </div>
    </section>
    <section class="stack-section">
      <div class="mini-title">People and facilities</div>
      <div class="settings-card">
        <div class="subtle">Facility: ${escapeHtml(snapshot.facilities[0]?.name || "Kitchen")}</div>
        ${snapshot.users
          .map(
            (user) => `
              <div class="user-row">
                <span>${escapeHtml(user.displayName)}</span>
                <span class="subtle">${escapeHtml(user.email)} | ${escapeHtml(user.role)}</span>
              </div>
            `
          )
          .join("")}
        ${perms.canManageUsers ? `<button class="primary-button small" data-action="open-add-user">Add user</button>` : ""}
      </div>
    </section>
    <section class="stack-section">
      <div class="mini-title">Log sync and email digest</div>
      <div class="settings-card">
        <label class="field-label">
          Log cadence
          <select class="field-input" data-setting-path="logSyncCadence">
            ${["nightly", "twice_daily", "every_4_hours", "weekly"]
              .map(
                (value) => `
                  <option value="${value}" ${snapshot.settings.logSyncCadence === value ? "selected" : ""}>${escapeHtml(value.replaceAll("_", " "))}</option>
                `
              )
              .join("")}
          </select>
        </label>
        <label class="field-label">
          Emailit recipient
          <input class="field-input" type="email" data-setting-path="emailit.toEmail" value="${escapeHtml(snapshot.settings.emailit.toEmail)}">
        </label>
        <label class="field-label">
          Emailit API key
          <input class="field-input" type="password" data-setting-path="emailit.apiKey" value="${escapeHtml(snapshot.settings.emailit.apiKey)}">
        </label>
      </div>
    </section>
    <section class="stack-section">
      <div class="mini-title">NoCodeBackend cloud</div>
      <div class="settings-card">
        <div class="meta-grid">
          <span>Instance ${escapeHtml(cloudRuntime.instance || "Not connected")}</span>
          <span>Ready ${cloudRuntime.ready ? "Yes" : "No"}</span>
          <span>Email auth ${cloudRuntime.providers?.email ? "Enabled" : "Disabled"}</span>
          <span>OTP auth ${cloudRuntime.providers?.emailOTP ? "Enabled" : "Disabled"}</span>
        </div>
        <div class="top-gap subtle">
          ${
            cloudRuntime.session?.email
              ? `Signed in as ${escapeHtml(cloudRuntime.session.email)}`
              : "No cloud user is signed in yet."
          }
        </div>
        ${
          cloudRuntime.lastSummary
            ? `<div class="top-gap subtle">${escapeHtml(cloudRuntime.lastSummary)}</div>`
            : ""
        }
        ${
          cloudRuntime.lastError
            ? `<div class="top-gap subtle">${escapeHtml(cloudRuntime.lastError)}</div>`
            : ""
        }
        <div class="action-row top-gap">
          <button class="primary-button small" data-action="open-cloud-login">Sign in</button>
          <button class="secondary-button small" data-action="open-cloud-signup">Sign up</button>
          <button class="secondary-button small" data-action="cloud-refresh-status">Refresh</button>
          <button class="secondary-button small" data-action="cloud-sync">Sync now</button>
          <button class="secondary-button small" data-action="cloud-restore">Restore recipes</button>
          <button class="secondary-button small" data-action="cloud-signout">Sign out</button>
        </div>
      </div>
    </section>
    <section class="stack-section">
      <div class="mini-title">Supabase</div>
      <div class="settings-card">
        <label class="field-label">
          Supabase URL
          <input class="field-input" type="url" data-setting-path="supabase.url" value="${escapeHtml(snapshot.settings.supabase.url)}">
        </label>
        <label class="field-label">
          Anon key
          <input class="field-input" type="password" data-setting-path="supabase.anonKey" value="${escapeHtml(snapshot.settings.supabase.anonKey)}">
        </label>
        <label class="toggle-row"><input type="checkbox" data-setting-path="supabase.enabled" ${snapshot.settings.supabase.enabled ? "checked" : ""}> Enable Supabase sync</label>
        <div class="action-row">
          <button class="primary-button small" data-action="sync-supabase">Sync now</button>
          <button class="secondary-button small" data-action="export-state">Export DB</button>
        </div>
        <label class="file-field">
          <span>Import exported JSON</span>
          <input type="file" accept="application/json" data-input="import-state-file">
        </label>
      </div>
    </section>
    <section class="stack-section">
      <div class="mini-title">Transport</div>
      <div class="settings-card">
        <div class="subtle">Browser BLE support: ${ble.supported ? "available" : "not available"}</div>
        <div class="subtle">Service UUID: ${BLE_UUIDS.SERVICE_UUID}</div>
        <div class="subtle">Command UUID: ${BLE_UUIDS.COMMAND_UUID}</div>
        <div class="subtle">File UUID: ${BLE_UUIDS.FILE_UUID}</div>
      </div>
    </section>
  `;
}

function renderControlPhone(snapshot) {
  const perms = currentPermissions(snapshot);
  const body =
    snapshot.ui.activeTab === "orders"
      ? snapshot.ui.orderMode === "current"
        ? renderCurrentOrders(snapshot, perms)
        : renderPreviousOrders(snapshot)
      : snapshot.ui.activeTab === "recipes"
        ? renderRecipesTab(snapshot, perms)
      : snapshot.ui.activeTab === "queue"
        ? renderQueueTab(snapshot)
      : snapshot.ui.activeTab === "manual"
        ? renderManualModeTab(snapshot)
      : snapshot.ui.activeTab === "global"
        ? renderGlobalRecipesTab(snapshot)
      : renderMoreTab(snapshot, perms);

  return `
    <section class="phone-frame control-phone">
      <div class="phone-shell">
        <header class="phone-head hero-head">
          <img src="./assets/app_banner.png" alt="On2Cook">
          <div class="grow">
            <div class="eyebrow">On2Cook Cloud</div>
            <h2>${escapeHtml(snapshot.facilities[0]?.name || "Kitchen console")}</h2>
            <p>${escapeHtml(getCurrentUser(snapshot).displayName)} | ${escapeHtml(getCurrentUser(snapshot).role)}</p>
          </div>
        </header>
        ${renderControlTabs(snapshot)}
        <div class="phone-body">${body}</div>
      </div>
    </section>
  `;
}

function renderRecipeTimeline(snapshot, device, recipe, active = false) {
  const steps = Array.isArray(recipe?.recipeJson?.Instruction) ? recipe.recipeJson.Instruction : [];
  if (steps.length === 0) {
    return `<div class="empty-card">No recipe timeline is available yet.</div>`;
  }
  const windowInfo = getTimelineWindow(device, recipe, active);
  const activeStepIndex = Math.max(0, Number(device.telemetry.stepNo || 1) - 1);
  const lastStepIndex =
    active
      ? Math.max(-1, activeStepIndex - 1)
      : device.lastRun?.outcome === "completed"
        ? steps.length - 1
        : Math.max(-1, Number(device.lastRun?.stepNo || 0) - 1);
  return `
    <div class="settings-card timeline-card">
      <div class="row space timeline-head">
        <div>
          <div class="mini-title">Cook timeline</div>
          <strong>${escapeHtml(recipe.displayName || recipe.firmwareName || "Recipe")}</strong>
        </div>
        <span class="subtle">${secondsLabel(windowInfo.totalSeconds)}</span>
      </div>
      <div class="timeline-window">
        <span>Start ${escapeHtml(formatShortTime(windowInfo.startAt))}</span>
        <span>End ${escapeHtml(formatShortTime(windowInfo.endAt))}</span>
      </div>
      <div class="timeline-list">
        ${steps
          .map((step, index) => {
            const duration = getInstructionDuration(step);
            const stepState = active
              ? index < activeStepIndex
                ? "done"
                : index === activeStepIndex
                  ? "live"
                  : "upcoming"
              : device.lastRun?.outcome === "aborted" && index === Math.max(0, Number(device.lastRun?.stepNo || 1) - 1)
                ? "aborted"
                : index <= lastStepIndex
                  ? "done"
                  : "upcoming";
            return `
              <div class="timeline-step ${stepState}">
                <div class="timeline-step-top">
                  <span class="timeline-step-index">${index + 1}</span>
                  <div class="timeline-step-copy">
                    <strong>${escapeHtml(step.Text || `Step ${index + 1}`)}</strong>
                    <span class="subtle">${secondsLabel(duration)}${step.Weight ? ` • ${escapeHtml(step.Weight)}` : ""}</span>
                  </div>
                </div>
                <div class="power-track">
                  <span class="power-label">Induction</span>
                  <div class="power-bar-shell induction">
                    <div class="power-bar-fill" style="width:${clampPercent(step.Induction_power)}%"></div>
                  </div>
                  <span class="power-value">${clampPercent(step.Induction_power)}%</span>
                </div>
                <div class="power-track">
                  <span class="power-label">Microwave</span>
                  <div class="power-bar-shell microwave">
                    <div class="power-bar-fill" style="width:${clampPercent(step.Magnetron_power)}%"></div>
                  </div>
                  <span class="power-value">${clampPercent(step.Magnetron_power)}%</span>
                </div>
              </div>
            `;
          })
          .join("")}
      </div>
    </div>
  `;
}

function renderDevicePhone(snapshot, device) {
  const currentOrder = getCurrentJob(snapshot, device);
  const queueOrders = getQueueOrders(snapshot, device);
  const selectableRecipes = getDeviceSyncRecipes(snapshot, device);
  const syncCount = selectableRecipes.length;
  const runtimeRecipe = getRuntimeRecipe(snapshot, device);
  const timelineRecipe = getDeviceTimelineRecipe(snapshot, device, runtimeRecipe);
  const headline = getDeviceRecipeHeadline(snapshot, device, currentOrder, timelineRecipe);
  const activeTimeline = Boolean(currentOrder || hasLiveRuntime(device) || device.activeRun?.firmwareName);
  const connectionLabel =
    device.connection === "connected"
      ? "connected"
      : device.connection === "connecting"
        ? "connecting"
        : "disconnected";
  const connectionTone =
    connectionLabel === "connected" ? "cooking" : connectionLabel === "connecting" ? "starting" : "failed";
  const summaryMessage = getDeviceSummaryMessage(device);
  const uploadState = device.uploadState || emptyUploadState();
  return `
    <section class="phone-frame device-phone ${device.connection}">
      <div class="phone-shell">
        <header class="phone-head device-head">
          <div>
            <div class="eyebrow">Device ${device.slot}</div>
            <h2>${escapeHtml(device.displayName)}</h2>
            <p>${escapeHtml(device.bluetoothName || "Not paired yet")}</p>
          </div>
          <span class="status-pill ${connectionTone}">
            ${escapeHtml(connectionLabel)}
          </span>
        </header>
        <div class="phone-body">
          <section class="stack-section">
            <div class="summary-card">
              <div class="row space">
                <strong>Last updated</strong>
                <span class="subtle">${escapeHtml(device.lastUpdatedAt ? formatAgo(device.lastUpdatedAt) : "Never")}</span>
              </div>
              <div class="subtle">${escapeHtml(summaryMessage)}</div>
              <div class="meta-grid top-gap">
                <span>Status: ${escapeHtml(device.telemetry.workStatus || "offline")}</span>
                <span>ETA: ${secondsLabel(device.telemetry.remainingSeconds)}</span>
                <span>Firmware: ${escapeHtml(device.telemetry.firmwareVersion || "Unknown")}</span>
                <span>Recipes on device: ${escapeHtml((device.availableRecipeNames || []).length)}</span>
                <span>Inventory: ${escapeHtml(device.recipeInventoryUpdatedAt ? formatAgo(device.recipeInventoryUpdatedAt) : "Unknown")}</span>
              </div>
            </div>
            <div class="action-row">
              ${
                device.connection === "connected"
                  ? `<button class="secondary-button small" data-action="disconnect-device" data-slot="${device.slot}">Disconnect</button>`
                  : `<button class="primary-button small" data-action="connect-device" data-slot="${device.slot}">Connect</button>`
              }
              <button class="secondary-button small" data-action="open-device-sheet" data-slot="${device.slot}">Details</button>
              <button class="secondary-button small" data-action="request-status" data-slot="${device.slot}">Status</button>
              <button class="secondary-button small" data-action="request-firmware" data-slot="${device.slot}">Firmware</button>
              <button class="secondary-button small" data-action="list-logs" data-slot="${device.slot}">Logs</button>
            </div>
          </section>
          <section class="stack-section">
            <div class="mini-title">Last cooked recipe</div>
            ${
              headline
                ? `
                  <article class="order-card compact customer-run-card">
                    <div class="row space">
                      <div>
                        <div class="order-id">${escapeHtml(currentOrder?.orderId || device.lastRun?.orderId || `DEVICE ${device.slot}`)}</div>
                        <h3>${escapeHtml(headline.title)}</h3>
                      </div>
                      ${renderStatusPill(headline.status)}
                    </div>
                    <div class="subtle">${escapeHtml(headline.note)}</div>
                    <div class="meta-grid top-gap">
                      <span>Step ${escapeHtml(device.telemetry.stepNo || device.lastRun?.stepNo || 0)}</span>
                      <span>Induction ${escapeHtml(`${clampPercent(device.telemetry.indPower)}%`)}</span>
                      <span>Microwave ${escapeHtml(`${clampPercent(device.telemetry.magPower)}%`)}</span>
                    </div>
                  </article>
                `
                : `<div class="empty-card">No recipe has run on this device yet.</div>`
            }
          </section>
          <section class="stack-section">
            <div class="mini-title">Execution timeline</div>
            ${
              timelineRecipe
                ? renderRecipeTimeline(snapshot, device, timelineRecipe, activeTimeline)
                : `<div class="empty-card">The recipe timeline will appear here once this device starts cooking.</div>`
            }
          </section>
          <section class="stack-section">
            <div class="mini-title">Queue</div>
            ${
              queueOrders.length
                ? queueOrders
                    .map(
                      (order) => `
                        <div class="queue-item">
                          <span>${escapeHtml(order.itemName)}</span>
                          <span class="subtle">${escapeHtml(order.orderId)}</span>
                        </div>
                      `
                    )
                    .join("")
                : `<div class="empty-card">Queue is empty.</div>`
            }
          </section>
          <section class="stack-section">
            <div class="mini-title">Manual run and sync</div>
            <div class="settings-card">
              ${
                uploadState.summary
                  ? `
                    <div class="upload-summary-card">
                      <strong>${escapeHtml(uploadState.summary)}</strong>
                      ${
                        uploadState.recipeNames.length || uploadState.skippedRecipeNames.length
                          ? `<div class="upload-name-list">
                              ${[
                                ...uploadState.recipeNames.map((name) => {
                                  const normalized = normalizeRecipeNameKey(name);
                                  const isDone = (uploadState.completedRecipeNames || []).some((item) => normalizeRecipeNameKey(item) === normalized);
                                  const isCurrent = normalizeRecipeNameKey(uploadState.currentRecipeName) === normalized && uploadState.active;
                                  return `<span class="upload-name-pill ${isDone ? "done" : isCurrent ? "live" : "pending"}">${escapeHtml(name)}</span>`;
                                }),
                                ...uploadState.skippedRecipeNames.map((name) => `<span class="upload-name-pill skipped">${escapeHtml(name)}</span>`)
                              ].join("")}
                            </div>`
                          : ""
                      }
                    </div>
                  `
                  : ""
              }
              <label class="field-label">
                Run selected recipe
                <select class="field-input" data-device-recipe-select="${device.slot}">
                  <option value="">Select a recipe</option>
                  ${selectableRecipes
                    .map(
                      (recipe) => `
                        <option value="${recipe.id}">${escapeHtml(recipe.displayName)}</option>
                      `
                    )
                    .join("")}
                </select>
              </label>
              <div class="action-row">
                <button class="primary-button small" data-action="run-device-selected-recipe" data-slot="${device.slot}">Run now</button>
                <button class="secondary-button small" data-action="sync-selected-recipes" data-slot="${device.slot}">Sync selected ${syncCount}</button>
              </div>
              <label class="file-field">
                <span>Serial number photo</span>
                <input type="file" accept="image/*" data-input="serial-photo" data-slot="${device.slot}">
              </label>
              ${
                device.serialPhotoDataUrl
                  ? `<img class="serial-photo" src="${device.serialPhotoDataUrl}" alt="Serial photo for ${escapeHtml(device.displayName)}">`
                  : ""
              }
            </div>
          </section>
        </div>
      </div>
    </section>
  `;
}

function renderModal(snapshot) {
  const modal = snapshot.ui.activeModal;
  if (!modal) return "";
  if (modal.type === "manual-order") {
    const options = getSelectedRecipes(snapshot)
      .map(
        (recipe) => `
          <option value="${recipe.displayName}">${escapeHtml(recipe.displayName)}</option>
        `
      )
      .join("");
    return `
      <div class="modal-backdrop">
        <div class="modal-card">
          <div class="row space">
            <h3>Manual order</h3>
            <button class="icon-button" data-action="close-modal">x</button>
          </div>
          <form data-form="manual-order" class="modal-form">
            <label class="field-label">Item name<input class="field-input" type="text" name="itemName" required></label>
            <label class="field-label">Recipe lookup<select class="field-input" name="recipeLookup">${options}</select></label>
            <label class="field-label">Quantity<input class="field-input" type="text" name="quantity" value="1 batch" required></label>
            <label class="field-label">Source<select class="field-input" name="source"><option>POS</option><option>Manual</option></select></label>
            <label class="field-label">Special instructions<textarea class="field-input" name="specialInstructions" rows="3"></textarea></label>
            <label class="field-label">Preferred device<select class="field-input" name="preferredSlot"><option value="">Auto</option>${snapshot.devices.map((device) => `<option value="${device.slot}">Device ${device.slot}</option>`).join("")}</select></label>
            <div class="action-row">
              <button class="secondary-button" type="button" data-action="close-modal">Cancel</button>
              <button class="primary-button" type="submit">Create order</button>
            </div>
          </form>
        </div>
      </div>
    `;
  }

  if (modal.type === "recipe-editor") {
    const sourceRecipe = findRecipeById(snapshot, modal.payload.recipeId);
    if (!sourceRecipe) return "";
    const json = cloneRecipeForEditing(sourceRecipe);
    const steps = json.Instruction || [];
    return `
      <div class="modal-backdrop">
        <div class="modal-card wide">
          <div class="row space">
            <h3>${sourceRecipe.type === "final" ? "Edit final recipe" : "Create final recipe"}</h3>
            <button class="icon-button" data-action="close-modal">x</button>
          </div>
          <form data-form="recipe-editor" class="modal-form">
            <input type="hidden" name="recipeId" value="${sourceRecipe.id}">
            <label class="field-label">Display name<input class="field-input" type="text" name="displayName" value="${escapeHtml(sourceRecipe.displayName)}" required></label>
            <label class="field-label">Firmware name<input class="field-input" type="text" name="firmwareName" value="${escapeHtml(sourceRecipe.firmwareName)}" required></label>
            <label class="field-label">Aliases<input class="field-input" type="text" name="aliases" value="${escapeHtml(sourceRecipe.aliases.join(", "))}" required></label>
            ${steps
              .map(
                (step, index) => `
                  <fieldset class="step-fieldset">
                    <legend>Step ${index + 1}</legend>
                    <div class="grid-two">
                      <label class="field-label">Label<input class="field-input" type="text" name="step_${index}_Text" value="${escapeHtml(step.Text || "")}"></label>
                      <label class="field-label">Lid<input class="field-input" type="text" name="step_${index}_lid" value="${escapeHtml(step.lid || "Closed")}"></label>
                      <label class="field-label">Induction seconds<input class="field-input" type="number" name="step_${index}_Induction_on_time" value="${escapeHtml(step.Induction_on_time || 0)}"></label>
                      <label class="field-label">Induction power<input class="field-input" type="number" name="step_${index}_Induction_power" value="${escapeHtml(step.Induction_power || 0)}"></label>
                      <label class="field-label">Microwave seconds<input class="field-input" type="number" name="step_${index}_Magnetron_on_time" value="${escapeHtml(step.Magnetron_on_time || 0)}"></label>
                      <label class="field-label">Microwave power<input class="field-input" type="number" name="step_${index}_Magnetron_power" value="${escapeHtml(step.Magnetron_power || 0)}"></label>
                      <label class="field-label">Stirrer<input class="field-input" type="text" name="step_${index}_stirrer_on" value="${escapeHtml(step.stirrer_on || "Medium")}"></label>
                      <label class="field-label">Pump seconds<input class="field-input" type="number" name="step_${index}_pump_on" value="${escapeHtml(step.pump_on || 0)}"></label>
                      <label class="field-label">Wait seconds<input class="field-input" type="number" name="step_${index}_wait_time" value="${escapeHtml(step.wait_time || 0)}"></label>
                      <label class="field-label">Threshold<input class="field-input" type="number" name="step_${index}_threshold" value="${escapeHtml(step.threshold || 0)}"></label>
                    </div>
                  </fieldset>
                `
              )
              .join("")}
            <div class="action-row">
              <button class="secondary-button" type="button" data-action="close-modal">Cancel</button>
              <button class="primary-button" type="submit">Save final recipe</button>
            </div>
          </form>
        </div>
      </div>
    `;
  }

  if (modal.type === "device-sheet") {
    const device = getDevice(modal.payload.slot);
    if (!device) return "";
    const currentOrder = getCurrentJob(snapshot, device);
    const queueOrders = getQueueOrders(snapshot, device);
    const runtimeRecipe = getRuntimeRecipe(snapshot, device);
    const telemetryMode = getTelemetryMode(device);
    const currentIngredient = getCurrentIngredient(device, runtimeRecipe);
    const currentInstruction = getCurrentInstruction(device, runtimeRecipe);
    const syncRecipes = getDeviceSyncRecipes(snapshot, device);
    const recipeFilter = String(modal.payload.recipeFilter || "").trim().toLowerCase();
    const filteredRecipes = snapshot.recipes
      .filter((recipe) => recipe.selected)
      .filter((recipe) =>
        !recipeFilter ||
        recipe.displayName.toLowerCase().includes(recipeFilter) ||
        recipe.firmwareName.toLowerCase().includes(recipeFilter)
      )
      .slice(0, 40);
    const inventoryPreview = (device.availableRecipeNames || []).slice(0, 12);
    return `
      <div class="modal-backdrop">
        <div class="modal-card wide">
          <div class="row space">
            <div>
              <div class="eyebrow">Device ${device.slot}</div>
              <h3>${escapeHtml(device.displayName)}</h3>
            </div>
            <button class="icon-button" data-action="close-modal">x</button>
          </div>
          <form data-form="device-sheet" class="modal-form">
            <input type="hidden" name="slot" value="${device.slot}">
            <div class="grid-two">
              <label class="field-label">Display name<input class="field-input" type="text" name="displayName" value="${escapeHtml(device.displayName)}" required></label>
              <label class="field-label">Bluetooth name<input class="field-input" type="text" value="${escapeHtml(device.bluetoothName || "")}" disabled></label>
              <label class="field-label">Browser device ID<input class="field-input" type="text" value="${escapeHtml(device.browserDeviceId || "")}" disabled></label>
              <label class="field-label">Connection state<input class="field-input" type="text" value="${escapeHtml(device.connection)}" disabled></label>
            </div>
            <label class="toggle-row"><input type="checkbox" name="enabled" ${device.enabled ? "checked" : ""}> Device enabled for scheduling</label>
            <div class="settings-card">
              <div class="meta-grid">
                <span>Firmware ${escapeHtml(device.telemetry.firmwareVersion || "Unknown")}</span>
                <span>Work status ${escapeHtml(device.telemetry.workStatus || "offline")}</span>
                <span>Remaining ${secondsLabel(device.telemetry.remainingSeconds)}</span>
                <span>Updated ${escapeHtml(formatTimestamp(device.lastUpdatedAt))}</span>
              </div>
              <p class="subtle">${escapeHtml(device.lastMessage || "No live messages yet")}</p>
            </div>
            <div class="settings-card">
              <div class="mini-title">Current recipe and queue</div>
              <div class="subtle">${currentOrder ? `${escapeHtml(currentOrder.itemName)} is on this device` : hasLiveRuntime(device) ? `${escapeHtml(getLiveRecipeName(device))} is running on firmware` : "No live recipe assigned right now."}</div>
              <div class="meta-grid top-gap">
                <span>Mode ${escapeHtml(device.telemetry.mode || "Unknown")}</span>
                <span>Step ${escapeHtml(device.telemetry.stepNo || 0)}</span>
                <span>Status ${escapeHtml(device.telemetry.status || "Unknown")}</span>
                <span>Stirrer ${escapeHtml(formatStirrerDisplay(device.telemetry.stirrer || DEFAULT_STIRRER_LEVEL))}</span>
              </div>
              ${
                currentIngredient
                  ? `<div class="empty-card top-gap"><strong>${escapeHtml(currentIngredient.title || `Ingredient ${device.telemetry.stepNo || 1}`)}</strong><div class="subtle">${escapeHtml(currentIngredient.weight || "")}${currentIngredient.text ? ` | ${escapeHtml(currentIngredient.text)}` : ""}</div></div>`
                  : currentInstruction
                    ? `<div class="empty-card top-gap"><strong>${escapeHtml(currentInstruction.Text || `Step ${device.telemetry.stepNo || 1}`)}</strong><div class="subtle">Lid ${escapeHtml(currentInstruction.lid || "Closed")} | Ind ${escapeHtml(currentInstruction.Induction_on_time || 0)}s | Mag ${escapeHtml(currentInstruction.Magnetron_on_time || 0)}s</div></div>`
                    : ""
              }
              ${
                telemetryMode.includes("ingredient") || telemetryMode.includes("cooking")
                  ? `<div class="action-row top-gap">
                      ${telemetryMode.includes("ingredient") ? `<button class="primary-button" type="button" data-action="complete-ingredients" data-slot="${device.slot}">Complete Ingredients (100)</button>` : ""}
                      ${telemetryMode.includes("cooking") ? `<button class="secondary-button" type="button" data-action="acknowledge-instruction" data-slot="${device.slot}">Acknowledge Step ${escapeHtml(device.telemetry.stepNo || 1)}</button>` : ""}
                    </div>`
                  : ""
              }
              ${
                queueOrders.length
                  ? queueOrders
                      .map(
                        (order) => `
                          <div class="queue-item">
                            <span>${escapeHtml(order.itemName)}</span>
                            <span class="subtle">${escapeHtml(order.orderId)}</span>
                          </div>
                        `
                      )
                      .join("")
                  : `<div class="empty-card">No queued items for this device.</div>`
              }
            </div>
            <div class="settings-card">
              <div class="mini-title">Device recipe inventory</div>
              <div class="meta-grid">
                <span>Known on device ${escapeHtml((device.availableRecipeNames || []).length)}</span>
                <span>Inventory checked ${escapeHtml(formatTimestamp(device.recipeInventoryUpdatedAt))}</span>
              </div>
              ${
                inventoryPreview.length
                  ? `<div class="chip-row top-gap">${inventoryPreview
                      .map((name) => `<span class="chip-button selected static-chip">${escapeHtml(name)}</span>`)
                      .join("")}</div>`
                  : `<div class="empty-card">No device recipe list has been read yet.</div>`
              }
            </div>
            <div class="settings-card">
              <div class="mini-title">Recipe finder and allowed recipes</div>
              <div class="action-row">
                <a class="link-button" href="${escapeHtml(snapshot.settings.recipeFinder.baseUrl)}" target="_blank" rel="noreferrer">Open Recipe Finder</a>
                <button class="secondary-button" type="button" data-action="open-recipes-tab">Manage imported recipes</button>
              </div>
              <label class="field-label">
                Search imported recipes
                <input class="field-input" type="search" data-input="device-recipe-filter" data-slot="${device.slot}" value="${escapeHtml(modal.payload.recipeFilter || "")}" placeholder="Filter imported recipes">
              </label>
              <div class="subtle">Only recipes you explicitly allow here will be candidates for this device.</div>
            </div>
            <div class="settings-card">
              <div class="mini-title">Recipes allowed on this device</div>
              <div class="chip-row">
                ${filteredRecipes
                  .map(
                    (recipe) => `
                      <button class="chip-button ${isRecipeAllowedOnDevice(snapshot, device, recipe.id) ? "selected" : ""}" type="button" data-action="toggle-recipe-device" data-slot="${device.slot}" data-recipe-id="${recipe.id}">
                        ${escapeHtml(recipe.displayName)}
                      </button>
                    `
                  )
                  .join("")}
              </div>
              ${
                recipeFilter && filteredRecipes.length === 0
                  ? `<div class="empty-card">No imported recipe matches that filter.</div>`
                  : ""
              }
              ${
                !recipeFilter && snapshot.recipes.filter((recipe) => recipe.selected).length > filteredRecipes.length
                  ? `<div class="subtle">Showing the first ${filteredRecipes.length} imported recipes. Use search to narrow the list.</div>`
                  : ""
              }
            </div>
            <div class="settings-card">
              <div class="mini-title">Device actions</div>
              <div class="action-row">
                ${
                  device.connection === "connected"
                    ? `<button class="secondary-button" type="button" data-action="disconnect-device" data-slot="${device.slot}">Disconnect</button>`
                    : `<button class="primary-button" type="button" data-action="connect-device" data-slot="${device.slot}">Connect</button>`
                }
                <button class="secondary-button" type="button" data-action="sync-selected-recipes" data-slot="${device.slot}">Sync selected ${syncRecipes.length}</button>
                <button class="secondary-button" type="button" data-action="read-device-recipes" data-slot="${device.slot}">Read recipes</button>
                <button class="secondary-button" type="button" data-action="request-status" data-slot="${device.slot}">Refresh status</button>
                <button class="secondary-button" type="button" data-action="list-logs" data-slot="${device.slot}">Fetch logs</button>
                <button class="danger-button" type="button" data-action="clear-device-binding" data-slot="${device.slot}">Clear pairing</button>
              </div>
            </div>
            <div class="action-row">
              <button class="secondary-button" type="button" data-action="close-modal">Close</button>
              <button class="primary-button" type="submit">Save device details</button>
            </div>
          </form>
        </div>
      </div>
    `;
  }

  if (modal.type === "add-user") {
    return `
      <div class="modal-backdrop">
        <div class="modal-card">
          <div class="row space">
            <h3>Add user</h3>
            <button class="icon-button" data-action="close-modal">x</button>
          </div>
          <form data-form="add-user" class="modal-form">
            <label class="field-label">Display name<input class="field-input" type="text" name="displayName" required></label>
            <label class="field-label">Email<input class="field-input" type="email" name="email" required></label>
            <label class="field-label">Role<select class="field-input" name="role"><option value="admin">Admin</option><option value="kitchen_manager">Kitchen manager</option><option value="operator">Operator</option></select></label>
            <label class="toggle-row"><input type="checkbox" name="managerMode"> Operator acts as manager</label>
            <div class="action-row">
              <button class="secondary-button" type="button" data-action="close-modal">Cancel</button>
              <button class="primary-button" type="submit">Add user</button>
            </div>
          </form>
        </div>
      </div>
    `;
  }

  if (modal.type === "cloud-auth") {
    const mode = modal.payload.mode === "signup" ? "signup" : "login";
    const localUser = getCurrentUser(snapshot);
    return `
      <div class="modal-backdrop">
        <div class="modal-card">
          <div class="row space">
            <h3>${mode === "signup" ? "Create cloud account" : "Sign in to cloud"}</h3>
            <button class="icon-button" data-action="close-modal">x</button>
          </div>
          <form data-form="cloud-auth" class="modal-form">
            <input type="hidden" name="mode" value="${mode}">
            <label class="field-label">Full name<input class="field-input" type="text" name="fullName" value="${escapeHtml(localUser.displayName || "")}" ${mode === "login" ? "" : "required"}></label>
            <label class="field-label">Email<input class="field-input" type="email" name="email" value="${escapeHtml(localUser.email || "")}" required></label>
            <label class="field-label">Password<input class="field-input" type="password" name="password" required></label>
            ${
              mode === "signup"
                ? `
                  <label class="field-label">Mobile phone<input class="field-input" type="tel" name="mobilePhone" placeholder="+91..."></label>
                  <label class="field-label">WhatsApp phone<input class="field-input" type="tel" name="whatsappPhone" placeholder="+91..."></label>
                  <label class="field-label">Role<select class="field-input" name="role"><option value="main_admin">Main admin</option><option value="owner">Owner</option><option value="kitchen_manager">Kitchen manager</option><option value="operator">Operator</option><option value="cook">Cook</option></select></label>
                `
                : ""
            }
            <div class="action-row">
              <button class="secondary-button" type="button" data-action="close-modal">Cancel</button>
              <button class="primary-button" type="submit">${mode === "signup" ? "Create account" : "Sign in"}</button>
            </div>
          </form>
        </div>
      </div>
    `;
  }

  if (modal.type === "order-details") {
    const order = getAnyOrderById(snapshot, modal.payload.orderId);
    if (!order) return "";
    const customer = getOrderCustomer(order);
    const meta = getOrderMeta(order);
    const taxes = getOrderTaxes(order);
    const discounts = getOrderDiscounts(order);
    const items = getOrderItems(order);
    const canMarkCompleted = snapshot.orders.current.some((item) => item.id === order.id) && !["completed", "failed", "cancelled"].includes(order.status);
    return `
      <div class="modal-backdrop">
        <div class="modal-card wide detail-sheet">
          <div class="row space">
            <div>
              <div class="eyebrow">Order details</div>
              <h3>${escapeHtml(order.itemName)}</h3>
            </div>
            <button class="icon-button" data-action="close-modal">x</button>
          </div>
          <div class="detail-hero">
            <div class="detail-hero-copy">
              <div class="chip-row">
                ${renderOrderStageBadge(order)}
                <span class="order-type-pill">${escapeHtml(getOrderType(order))}</span>
              </div>
              <div class="detail-order-id">Order ID: ${escapeHtml(order.orderId)}</div>
              <div class="subtle">${escapeHtml(getOrderCreatedDisplay(order))}</div>
            </div>
            ${
              getOrderThumbUrl(order)
                ? `<img class="detail-hero-thumb" src="${getOrderThumbUrl(order)}" alt="${escapeHtml(order.itemName)}">`
                : ""
            }
          </div>
          <section class="settings-card detail-section">
            <div class="mini-title">Customer details</div>
            <div class="detail-section-head">
              <div class="detail-info-list">
                <div class="detail-info-row"><span>Name</span><strong>${escapeHtml(customer.name || "Walk-in")}</strong></div>
                <div class="detail-info-row"><span>Phone</span><strong>${escapeHtml(customer.phone || "-")}</strong></div>
                <div class="detail-info-row"><span>Address</span><strong>${escapeHtml(customer.address || "-")}</strong></div>
              </div>
              ${
                customer.phone
                  ? `<a class="phone-link" href="tel:${escapeHtml(String(customer.phone).replace(/[^\d+]/g, ""))}">Call</a>`
                  : ""
              }
            </div>
          </section>
          <section class="settings-card detail-section">
            <div class="mini-title">Order summary</div>
            <div class="detail-summary-list">
              <div class="detail-info-row"><span>Items</span><strong>${escapeHtml(getOrderItemCount(order))}</strong></div>
              <div class="detail-info-row"><span>Subtotal</span><strong>${escapeHtml(formatCurrency(meta.core_total || 0))}</strong></div>
              ${discounts
                .map(
                  (item) => `
                    <div class="detail-info-row negative">
                      <span>${escapeHtml(item.title || "Discount")} (${escapeHtml(item.rate || 0)}%)</span>
                      <strong>- ${escapeHtml(formatCurrency(item.amount || 0))}</strong>
                    </div>
                  `
                )
                .join("")}
              ${taxes
                .map(
                  (item) => `
                    <div class="detail-info-row">
                      <span>${escapeHtml(item.title || "Tax")} (${escapeHtml(item.rate || 0)}%)</span>
                      <strong>${escapeHtml(formatCurrency(item.amount || 0))}</strong>
                    </div>
                  `
                )
                .join("")}
              <div class="detail-info-row"><span>Packaging charge</span><strong>${escapeHtml(formatCurrency(meta.packaging_charge || 0))}</strong></div>
              <div class="detail-info-row"><span>Delivery charge</span><strong>${escapeHtml(formatCurrency(meta.delivery_charges || 0))}</strong></div>
              <div class="detail-info-row"><span>Service charge</span><strong>${escapeHtml(formatCurrency(meta.service_charge || 0))}</strong></div>
              <div class="detail-info-row total">
                <span>Total</span>
                <strong>${escapeHtml(formatCurrency(getOrderTotal(order)))}</strong>
              </div>
            </div>
          </section>
          <section class="settings-card detail-section">
            <div class="mini-title">Items</div>
            <div class="detail-item-list">
              ${items
                .map(
                  (item) => `
                    <article class="detail-item-card">
                      ${
                        getOrderThumbUrl(order)
                          ? `<img class="detail-item-thumb" src="${getOrderThumbUrl(order)}" alt="${escapeHtml(item.name || order.itemName)}">`
                          : `<div class="detail-item-thumb placeholder">${escapeHtml(String(item.name || order.itemName).slice(0, 1))}</div>`
                      }
                      <div class="detail-item-copy">
                        <div class="row space">
                          <strong>${escapeHtml(item.name || order.itemName)}</strong>
                          <strong>${escapeHtml(formatCurrency(item.total || 0))}</strong>
                        </div>
                        <div class="subtle">Qty: ${escapeHtml(item.quantity || 1)}</div>
                        <div class="detail-item-meta negative">Discount: - ${escapeHtml(formatCurrency(item.discount || 0))}</div>
                        <div class="detail-item-meta">Tax: ${escapeHtml(formatCurrency(item.tax || 0))}</div>
                        ${item.specialnotes ? `<div class="subtle">${escapeHtml(item.specialnotes)}</div>` : ""}
                      </div>
                    </article>
                  `
                )
                .join("")}
            </div>
          </section>
          <section class="settings-card detail-section">
            <div class="mini-title">Order info</div>
            <div class="detail-info-list">
              <div class="detail-info-row"><span>Source</span><strong>${escapeHtml(meta.order_from || order.source || "POS")}</strong></div>
              <div class="detail-info-row"><span>Payment</span><strong>${escapeHtml(getOrderPaymentLabel(order))}</strong></div>
              <div class="detail-info-row"><span>Status</span><strong>${escapeHtml(meta.status || "Success")}</strong></div>
              <div class="detail-info-row"><span>Biller</span><strong>${escapeHtml(meta.biller || "biller (biller)")}</strong></div>
              <div class="detail-info-row"><span>Created on</span><strong>${escapeHtml(getOrderCreatedDisplay(order))}</strong></div>
            </div>
            ${order.specialInstructions ? `<p class="subtle">${escapeHtml(order.specialInstructions)}</p>` : ""}
          </section>
          <div class="action-row detail-actions">
            <button class="secondary-button" type="button" data-action="print-order" data-order-id="${order.id}">Print Invoice</button>
            ${order.assignedSlot ? `<button class="secondary-button" type="button" data-action="open-device-sheet" data-slot="${order.assignedSlot}">Open Device</button>` : ""}
            ${canMarkCompleted ? `<button class="primary-button" type="button" data-action="mark-order-completed" data-order-id="${order.id}">Mark Completed</button>` : ""}
            <button class="secondary-button" type="button" data-action="close-modal">Close</button>
          </div>
        </div>
      </div>
    `;
  }
  return "";
}

function render() {
  const snapshot = state();
  app.innerHTML = `
    <div class="surface">
      <header class="page-hero">
        <div class="hero-copy">
          <div class="eyebrow">Chrome / Edge / Chrome Android</div>
          <h1>On2Cook Cloud orchestration</h1>
          <p>Order-first mobile layout, direct Web Bluetooth transport, five-device session support, and a recipe pipeline seeded from your local On2Cook recipe archive.</p>
        </div>
        <div class="hero-stats">
          <div class="summary-chip">Orders ${snapshot.orders.current.length}</div>
          <div class="summary-chip">Connected ${getConnectedDevices(snapshot).length}</div>
          <div class="summary-chip">Selected recipes ${getSelectedRecipes(snapshot).length}</div>
          <button class="secondary-button small" data-action="switch-tab" data-tab="settings">Settings</button>
        </div>
      </header>
      ${snapshot.ui.toast ? `<div class="toast ${snapshot.ui.toastTone}">${escapeHtml(snapshot.ui.toast)}</div>` : ""}
      <main class="screen-rail">
        ${renderControlPhone(snapshot)}
        ${snapshot.devices.map((device) => renderDevicePhone(snapshot, device)).join("")}
      </main>
      ${renderModal(snapshot)}
    </div>
  `;
}

async function handleManualOrderSubmit(formData) {
  const snapshot = state();
  const itemName = formData.get("itemName");
  const recipeLookup = formData.get("recipeLookup");
  const quantity = formData.get("quantity");
  const source = formData.get("source");
  const specialInstructions = formData.get("specialInstructions");
  const preferredSlot = formData.get("preferredSlot");
  const recipe = findEffectiveRecipeForOrder(snapshot, recipeLookup || itemName);
  const order = decorateOrderRecord({
    id: crypto.randomUUID(),
    orderId: `#M${Math.floor(Math.random() * 900 + 100)}`,
    itemName: String(itemName),
    recipeLookup: String(recipeLookup),
    quantity: String(quantity),
    source: String(source),
    specialInstructions: String(specialInstructions || ""),
    accentColor: "#f47b20",
    createdAt: nowIso(),
    status: "pending",
    assignedSlot: null,
    assignedMode: preferredSlot ? "device" : "auto",
    activeRecipeId: null,
    currentRunRecipeName: "",
    currentRunFirmwareName: "",
    targetSlot: preferredSlot ? Number(preferredSlot) : null,
    manual: true,
    historyNote: "",
    channelProfileIndex: String(source) === "Manual" ? 2 : 0
  }, recipe, snapshot.orders.current.length);
  mutate((draft) => {
    draft.orders.current.unshift(order);
  });
  closeModal();
  if (preferredSlot) {
    await startOrderFlow(order.id, Number(preferredSlot));
    return;
  }
  if (state().settings.pendingAssignmentMode === "auto_route") {
    queueIdleWork();
  } else {
    showToast(`${order.itemName} added to the pending queue`, "success");
  }
}

function applyRecipeEditor(formData) {
  const snapshot = state();
  const sourceRecipe = findRecipeById(snapshot, formData.get("recipeId"));
  if (!sourceRecipe) return;
  const baseRecipe =
    sourceRecipe.type === "final" && sourceRecipe.baseRecipeId ? findRecipeById(snapshot, sourceRecipe.baseRecipeId) || sourceRecipe : sourceRecipe;
  const recipeJson = cloneRecipeForEditing(sourceRecipe);
  const steps = recipeJson.Instruction || [];
  steps.forEach((step, index) => {
    step.Text = String(formData.get(`step_${index}_Text`) || step.Text || "");
    step.lid = String(formData.get(`step_${index}_lid`) || step.lid || "Closed");
    step.Induction_on_time = String(formData.get(`step_${index}_Induction_on_time`) || step.Induction_on_time || 0);
    step.Induction_power = String(formData.get(`step_${index}_Induction_power`) || step.Induction_power || 0);
    step.Magnetron_on_time = String(formData.get(`step_${index}_Magnetron_on_time`) || step.Magnetron_on_time || 0);
    step.Magnetron_power = String(formData.get(`step_${index}_Magnetron_power`) || step.Magnetron_power || 0);
    step.stirrer_on = String(formData.get(`step_${index}_stirrer_on`) || step.stirrer_on || "Medium");
    step.pump_on = String(formData.get(`step_${index}_pump_on`) || step.pump_on || 0);
    step.wait_time = String(formData.get(`step_${index}_wait_time`) || step.wait_time || 0);
    step.threshold = String(formData.get(`step_${index}_threshold`) || step.threshold || 0);
    step.durationInSec = Math.max(Number(step.Induction_on_time) || 0, Number(step.Magnetron_on_time) || 0, Number(step.wait_time) || 0);
  });
  const finalRecipe = createFinalRecipeFromBase(baseRecipe, recipeJson, {
    displayName: formData.get("displayName"),
    firmwareName: formData.get("firmwareName"),
    aliases: formData.get("aliases"),
    imageDataUrl: sourceRecipe.imageDataUrl
  });
  if (sourceRecipe.type === "final") {
    finalRecipe.id = sourceRecipe.id;
    finalRecipe.createdAt = sourceRecipe.createdAt;
  }
  mutate((draft) => {
    draft.recipes = draft.recipes.filter(
      (recipe) =>
        recipe.id !== sourceRecipe.id &&
        !(recipe.type === "final" && recipe.baseRecipeId === baseRecipe.id && recipe.id !== finalRecipe.id)
    );
    const baseDraft = draft.recipes.find((recipe) => recipe.id === baseRecipe.id);
    if (baseDraft) baseDraft.selected = false;
    draft.recipes.unshift(finalRecipe);
  });
  closeModal();
  showToast(`Saved final recipe ${finalRecipe.displayName}`, "success");
}

async function importRecipeRecord(result, options = {}) {
  const recipeJson = structuredClone(result.recipeJson);
  const displayName = Array.isArray(recipeJson.name) ? recipeJson.name[0] : recipeJson.name;
  const recipeSignature = recipeSignatureFromJson(recipeJson);
  const record = {
    id: crypto.randomUUID(),
    type: "base",
    baseRecipeId: null,
    source: options.source || "imported",
    zipName: result.sourceName,
    zipUrl: options.zipUrl || "",
    recipeTextEntryName: result.recipeTextEntryName || "",
    rawRecipeText: result.recipeText || "",
    displayName: String(displayName || result.sourceName).trim(),
    firmwareName: sanitizeFirmwareName(displayName || result.sourceName),
    aliases: [String(displayName || result.sourceName).trim()],
    category: recipeJson.category || "Orders",
    imageDataUrl: result.imageDataUrl || "",
    recipeEntries: Array.isArray(result.entries) ? structuredClone(result.entries) : [],
    recipeJson,
    recipeSignature,
    selected: options.selected !== false,
    createdAt: nowIso(),
    updatedAt: nowIso()
  };
  if (!Array.isArray(record.recipeJson.name) || record.recipeJson.name.length === 0) {
    record.recipeJson.name = [record.firmwareName];
  } else {
    record.recipeJson.name[0] = record.firmwareName;
  }
  const existingRecipe =
    state().recipes.find((recipe) => normalizeCatalogKey(recipe.recipeSignature) === normalizeCatalogKey(recipeSignature)) ||
    findRecipeByZipName(state(), record.zipName) ||
    findRecipeByFirmwareName(state(), record.firmwareName) ||
    null;
  if (existingRecipe) {
    mutate((draft) => {
      const recipe = draft.recipes.find((item) => item.id === existingRecipe.id);
      if (!recipe) return draft;
      recipe.zipName = record.zipName;
      recipe.zipUrl = record.zipUrl;
      recipe.recipeTextEntryName = record.recipeTextEntryName;
      recipe.rawRecipeText = record.rawRecipeText;
      recipe.displayName = record.displayName;
      recipe.firmwareName = record.firmwareName;
      recipe.aliases = Array.from(new Set([...(recipe.aliases || []), ...record.aliases]));
      recipe.category = record.category;
      recipe.imageDataUrl = record.imageDataUrl || recipe.imageDataUrl || "";
      recipe.recipeEntries = Array.isArray(record.recipeEntries) ? structuredClone(record.recipeEntries) : [];
      recipe.recipeJson = structuredClone(record.recipeJson);
      recipe.recipeSignature = recipeSignature;
      recipe.selected = options.selected !== false ? true : recipe.selected;
      recipe.updatedAt = nowIso();
      if (options.addToCatalog !== false) {
        upsertImportedCatalogEntry(
          draft,
          buildImportedCatalogEntry(result, recipe, {
            zipUrl: options.zipUrl || "",
            source: options.source || "imported",
            catalogEntryId: options.catalogEntryId || ""
          })
        );
      }
      if (recipe.selected) {
        syncSelectedRecipesToAllDevices(draft);
      }
      if (options.activateRecipesTab !== false) {
        draft.ui.recipeMode = "selected";
        draft.ui.activeTab = "recipes";
      }
    });
    if (options.showToast !== false) {
      showToast(`Updated ${record.displayName}`, "success");
    }
    return state().recipes.find((recipe) => recipe.id === existingRecipe.id) || existingRecipe;
  }
  mutate((draft) => {
    draft.recipes.unshift(record);
    if (options.addToCatalog !== false) {
      upsertImportedCatalogEntry(
        draft,
        buildImportedCatalogEntry(result, record, {
          zipUrl: options.zipUrl || "",
          source: options.source || "imported",
          catalogEntryId: options.catalogEntryId || ""
        })
      );
    }
    if (record.selected) {
      syncSelectedRecipesToAllDevices(draft);
    }
    if (options.activateRecipesTab !== false) {
      draft.ui.recipeMode = "selected";
      draft.ui.activeTab = "recipes";
    }
  });
  if (options.showToast !== false) {
    showToast(`Imported ${record.displayName}`, "success");
  }
  return record;
}

function createPendingOrderFromRecipe(recipe, orderIndex = 0, source = "Global Recipes") {
  return decorateOrderRecord(
    {
      id: crypto.randomUUID(),
      orderId: `#G${Math.floor(Math.random() * 900 + 100)}`,
      itemName: recipe.displayName,
      recipeLookup: recipe.displayName,
      quantity: "1 batch",
      source,
      specialInstructions: "",
      accentColor: "#f47b20",
      createdAt: nowIso(),
      status: "pending",
      assignedSlot: null,
      assignedMode: "auto",
      activeRecipeId: recipe.id,
      currentRunRecipeName: recipe.displayName,
      currentRunFirmwareName: recipe.firmwareName,
      targetSlot: null,
      manual: true,
      historyNote: ""
    },
    recipe,
    orderIndex
  );
}

async function ensureGlobalCatalogRecipeImported(entry, options = {}) {
  const snapshot = state();
  const existing = findRecipeForGlobalCatalogEntry(snapshot, entry);
  if (existing) {
    if (options.ensureSelected) {
      mutate((draft) => {
        const recipe = findRecipeForGlobalCatalogEntry(draft, entry);
        if (!recipe) return draft;
        recipe.selected = true;
        syncSelectedRecipesToAllDevices(draft);
      });
    }
    return findRecipeForGlobalCatalogEntry(state(), entry);
  }
  const result = entry.zipUrl
    ? await importRecipeZipUrl(`${entry.zipUrl}?v=${RECIPE_ARCHIVE_VERSION}`)
    : createImportResultFromCatalogEntry(entry);
  return importRecipeRecord(result, {
    zipUrl: entry.zipUrl,
    source: entry.source === "imported" ? "imported" : "library",
    selected: options.ensureSelected !== false,
    activateRecipesTab: false,
    showToast: false,
    addToCatalog: entry.source === "imported",
    catalogEntryId: entry.id || ""
  });
}

async function syncImportedRecipeToCloud(recipe) {
  try {
    if (!cloudRuntime.ready || !cloudRuntime.session?.id) {
      return { synced: false, reason: "not-signed-in" };
    }
    const existingRows = await recipeService.listMine();
    const result = await recipeService.upsertLocalRecipe(recipe, existingRows);
    mutate((draft) => {
      const localRecipe = draft.recipes.find((item) => item.id === recipe.id);
      if (!localRecipe) return draft;
      localRecipe.cloudRecordId = result.cloudId || localRecipe.cloudRecordId || "";
      localRecipe.cloudUserId = cloudRuntime.session?.id || localRecipe.cloudUserId || "";
      localRecipe.recipeSignature = result.signature || localRecipe.recipeSignature || "";
      upsertImportedCatalogEntry(
        draft,
        buildCatalogEntryFromRecipe(localRecipe, {
          catalogEntryId: localRecipe.cloudRecordId ? `cloud-${localRecipe.cloudRecordId}` : "",
          source: "cloud",
          sourceName: localRecipe.zipName || `${localRecipe.displayName}.zip`,
          recipeText: localRecipe.rawRecipeText || JSON.stringify(localRecipe.recipeJson || {}),
          recipeTextEntryName: localRecipe.recipeTextEntryName || "",
          imageDataUrl: localRecipe.imageDataUrl || "",
          entries: Array.isArray(localRecipe.recipeEntries) ? structuredClone(localRecipe.recipeEntries) : [],
          zipUrl: localRecipe.zipUrl || ""
        })
      );
    });
    setCloudRuntime({
      lastSyncAt: nowIso(),
      lastSummary: `Recipe synced to cloud: ${recipe.displayName}`,
      lastError: ""
    });
    return { synced: true, cloudId: result.cloudId || "", signature: result.signature || "" };
  } catch (error) {
    setCloudRuntime({
      lastError: error.message || `Unable to sync ${recipe.displayName} to cloud.`
    });
    showToast(`Imported locally, but cloud sync failed for ${recipe.displayName}`, "warning");
    return { synced: false, reason: "error", error };
  }
}

async function addPickedGlobalRecipesToRecipeList() {
  const pickedIds = [...(state().ui.globalRecipePickedIds || [])];
  if (pickedIds.length === 0) {
    showToast("Pick one or more global recipes first", "warning");
    return;
  }
  const entries = getRecipeCatalog(state()).filter((entry) => pickedIds.includes(entry.id));
  let importedCount = 0;
  let activatedCount = 0;
  for (const entry of entries) {
    const before = findRecipeForGlobalCatalogEntry(state(), entry);
    const recipe = await ensureGlobalCatalogRecipeImported(entry, { ensureSelected: true });
    if (!before && recipe) {
      importedCount += 1;
    } else if (recipe && !before?.selected) {
      activatedCount += 1;
    }
  }
  mutate((draft) => {
    draft.ui.activeTab = "recipes";
    draft.ui.recipeMode = "selected";
    draft.ui.globalRecipePickedIds = [];
  });
  showToast(
    importedCount > 0 || activatedCount > 0
      ? `${importedCount + activatedCount} global recipe${importedCount + activatedCount === 1 ? "" : "s"} added to the Recipe list`
      : "Those recipes are already in the Recipe list",
    "success"
  );
}

async function addPickedGlobalRecipesToOrders() {
  const pickedIds = [...(state().ui.globalRecipePickedIds || [])];
  if (pickedIds.length === 0) {
    showToast("Pick one or more global recipes first", "warning");
    return;
  }
  const entries = getRecipeCatalog(state()).filter((entry) => pickedIds.includes(entry.id));
  const newOrders = [];
  for (const entry of entries) {
    const recipe = await ensureGlobalCatalogRecipeImported(entry, { ensureSelected: true });
    if (!recipe) continue;
    newOrders.unshift(createPendingOrderFromRecipe(recipe, state().orders.current.length + newOrders.length, "Global Recipes"));
  }
  if (newOrders.length === 0) {
    showToast("No orders were created from the selected recipes", "warning");
    return;
  }
  mutate((draft) => {
    draft.orders.current.unshift(...newOrders);
    draft.ui.activeTab = "orders";
    draft.ui.globalRecipePickedIds = [];
  });
  showToast(`${newOrders.length} pending order${newOrders.length === 1 ? "" : "s"} created from Global Recipes`, "success");
}

function removePickedGlobalRecipesFromRecipeList() {
  const pickedIds = new Set(state().ui.globalRecipePickedIds || []);
  if (pickedIds.size === 0) {
    showToast("Pick one or more global recipes first", "warning");
    return;
  }
  let removedCount = 0;
  let skippedBundled = 0;
  let skippedActive = 0;
  mutate((draft) => {
    const entryMap = new Map(getRecipeCatalog(draft).map((entry) => [entry.id, entry]));
    const removableIds = new Set();
    draft.recipes.forEach((recipe) => {
      if (recipe.type === "final") {
        return;
      }
      const entry = [...pickedIds].map((id) => entryMap.get(id)).find((item) => item && findRecipeForGlobalCatalogEntry({ recipes: [recipe] }, item));
      if (!entry) return;
      if (recipe.source === "seed") {
        skippedBundled += 1;
        return;
      }
      const isReferenced = draft.orders.current.some((order) => order.activeRecipeId === recipe.id);
      if (isReferenced) {
        skippedActive += 1;
        return;
      }
      removableIds.add(recipe.id);
    });
    if (removableIds.size > 0) {
      draft.recipes = draft.recipes.filter((recipe) => !removableIds.has(recipe.id));
      removedCount = removableIds.size;
    }
    draft.ui.globalRecipePickedIds = [];
    draft.ui.activeTab = "global";
  });
  if (removedCount > 0) {
    showToast(`Removed ${removedCount} recipe${removedCount === 1 ? "" : "s"} from the Recipe list`, "success");
    return;
  }
  if (skippedBundled > 0) {
    showToast("The bundled ten recipes stay in the Recipe list", "warning");
    return;
  }
  if (skippedActive > 0) {
    showToast("Recipes already tied to active orders were not removed", "warning");
    return;
  }
  showToast("Those picked recipes are not currently in the Recipe list", "warning");
}

async function handleSubmit(event) {
  const form = event.target;
  const formName = form.dataset.form;
  if (!formName) return;
  event.preventDefault();
  const formData = new FormData(form);

  if (formName === "manual-order") {
    await handleManualOrderSubmit(formData);
    return;
  }
  if (formName === "recipe-editor") {
    applyRecipeEditor(formData);
    return;
  }
  if (formName === "add-user") {
    mutate((draft) => {
      draft.users.push({
        id: crypto.randomUUID(),
        facilityId: draft.currentFacilityId,
        email: String(formData.get("email")),
        displayName: String(formData.get("displayName")),
        role: String(formData.get("role")),
        managerMode: Boolean(formData.get("managerMode"))
      });
    });
    closeModal();
    showToast("User added", "success");
    return;
  }
  if (formName === "cloud-auth") {
    const mode = String(formData.get("mode") || "login");
    try {
      if (mode === "signup") {
        await authService.signUpEmail({
          email: String(formData.get("email") || "").trim(),
          password: String(formData.get("password") || ""),
          name: String(formData.get("fullName") || "").trim()
        });
      } else {
        await authService.signInEmail({
          email: String(formData.get("email") || "").trim(),
          password: String(formData.get("password") || "")
        });
      }
      await refreshCloudRuntime();
      if (cloudRuntime.session?.id) {
        await profileService.upsertCurrentProfile(
          cloudRuntime.session,
          getCurrentUser(state()),
          {
            full_name: String(formData.get("fullName") || "").trim(),
            mobile_phone: String(formData.get("mobilePhone") || "").trim(),
            whatsapp_phone: String(formData.get("whatsappPhone") || "").trim(),
            role: String(formData.get("role") || getCurrentUser(state()).role || "operator"),
            status: "active"
          }
        );
      }
      setCloudRuntime({
        lastSummary: mode === "signup" ? "Cloud account created and profile synced." : "Cloud sign-in successful.",
        lastError: ""
      });
      closeModal();
      showToast(mode === "signup" ? "Cloud account created" : "Signed in to cloud", "success");
    } catch (error) {
      setCloudRuntime({ lastError: error.message || "Cloud authentication failed." });
      showToast(error.message, "error");
    }
    return;
  }
  if (formName === "device-sheet") {
    const slot = Number(formData.get("slot"));
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === slot);
      if (!device) return draft;
      device.displayName = String(formData.get("displayName") || device.displayName).trim() || device.displayName;
      device.enabled = formData.get("enabled") === "on";
      appendActivity(device, "Device details updated", "success");
    });
    closeModal();
    showToast(`Device ${slot} details saved`, "success");
    return;
  }
  if (formName === "import-zip-url") {
    const zipUrl = String(formData.get("zipUrl"));
    updateNestedSetting("recipeFinder.lastZipUrl", zipUrl);
    try {
      const result = await importRecipeZipUrl(zipUrl);
      const recipe = await importRecipeRecord(result, { zipUrl, showToast: false });
      const syncResult = await syncImportedRecipeToCloud(recipe);
      if (syncResult.synced) {
        showToast(`Imported and synced ${recipe.displayName}`, "success");
      } else if (syncResult.reason === "not-signed-in") {
        showToast(`Imported ${recipe.displayName} locally. Sign in to sync it to cloud.`, "info");
      } else if (syncResult.reason !== "error") {
        showToast(`Imported ${recipe.displayName}`, "success");
      }
    } catch (error) {
      showToast(error.message, "error");
    }
  }
}

async function handleClick(event) {
  const button = event.target.closest("[data-action]");
  if (!button) return;
  const action = button.dataset.action;

  if (action === "switch-tab") {
    mutate((draft) => {
      draft.ui.activeTab = button.dataset.tab;
    });
    return;
  }
  if (action === "toggle-global-recipe-pick") {
    const recipeCatalogId = button.dataset.recipeCatalogId;
    mutate((draft) => {
      const picked = new Set(draft.ui.globalRecipePickedIds || []);
      if (picked.has(recipeCatalogId)) {
        picked.delete(recipeCatalogId);
      } else {
        picked.add(recipeCatalogId);
      }
      draft.ui.globalRecipePickedIds = [...picked];
    });
    return;
  }
  if (action === "global-recipes-clear-picks") {
    mutate((draft) => {
      draft.ui.globalRecipePickedIds = [];
    });
    return;
  }
  if (action === "global-recipes-add-to-list") {
    await addPickedGlobalRecipesToRecipeList();
    return;
  }
  if (action === "global-recipes-add-to-orders") {
    await addPickedGlobalRecipesToOrders();
    return;
  }
  if (action === "global-recipes-remove-from-list") {
    removePickedGlobalRecipesFromRecipeList();
    return;
  }
  if (action === "switch-order-mode") {
    mutate((draft) => {
      draft.ui.orderMode = button.dataset.mode;
    });
    return;
  }
  if (action === "switch-recipe-mode") {
    mutate((draft) => {
      draft.ui.recipeMode = button.dataset.mode;
    });
    return;
  }
  if (action === "select-manual-device") {
    const slot = Number(button.dataset.slot) || 1;
    mutate((draft) => {
      draft.ui.manualMode.slot = slot;
    });
    ble.requestStatus(slot).catch(() => {});
    return;
  }
  if (action === "open-manual-order") {
    openModal("manual-order");
    return;
  }
  if (action === "close-modal") {
    closeModal();
    return;
  }
  if (action === "connect-device") {
    await connectDevice(button.dataset.slot);
    return;
  }
  if (action === "disconnect-device") {
    await disconnectDevice(button.dataset.slot);
    return;
  }
  if (action === "request-status") {
    await ble.requestStatus(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-request-status") {
    await ble.requestStatus(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "request-firmware") {
    await ble.requestFirmwareVersion(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "list-logs") {
    await ble.listLogs(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "auto-assign-order") {
    await startOrderFlow(button.dataset.orderId);
    return;
  }
  if (action === "assign-order-device") {
    await startOrderFlow(button.dataset.orderId, Number(button.dataset.slot));
    return;
  }
  if (action === "open-order-details") {
    openModal("order-details", { orderId: button.dataset.orderId });
    return;
  }
  if (action === "mark-order-completed") {
    markOrderCompleted(button.dataset.orderId);
    return;
  }
  if (action === "print-order") {
    printOrder(button.dataset.orderId);
    return;
  }
  if (action === "open-device-sheet") {
    openModal("device-sheet", { slot: Number(button.dataset.slot) });
    return;
  }
  if (action === "open-recipes-tab") {
    mutate((draft) => {
      draft.ui.activeTab = "recipes";
      draft.ui.activeModal = null;
      draft.ui.recipeMode = "import";
    });
    return;
  }
  if (action === "toggle-recipe-selected") {
    mutate((draft) => {
      const recipe = draft.recipes.find((item) => item.id === button.dataset.recipeId);
      if (!recipe) return draft;
      recipe.selected = !recipe.selected;
      if (recipe.selected) {
        syncSelectedRecipesToAllDevices(draft);
      }
    });
    return;
  }
  if (action === "toggle-recipe-device") {
    toggleRecipePermission(button.dataset.slot, button.dataset.recipeId);
    return;
  }
  if (action === "create-final-recipe" || action === "edit-final-recipe") {
    openModal("recipe-editor", { recipeId: button.dataset.recipeId });
    return;
  }
  if (action === "delete-final-recipe") {
    mutate((draft) => {
      draft.recipes = draft.recipes.filter((recipe) => recipe.id !== button.dataset.recipeId);
    });
    showToast("Final recipe deleted", "success");
    return;
  }
  if (action === "sync-selected-recipes") {
    await syncSelectedRecipesToDevice(Number(button.dataset.slot));
    return;
  }
  if (action === "read-device-recipes") {
    try {
      const names = await refreshDeviceRecipeInventory(Number(button.dataset.slot), {
        force: true,
        timeoutMs: 4500
      });
      showToast(
        names.length > 0
          ? `Found ${names.length} recipe${names.length === 1 ? "" : "s"} on Device ${button.dataset.slot}`
          : `No recipe names were reported by Device ${button.dataset.slot}`,
        "success"
      );
    } catch (error) {
      showToast(error.message, "error");
    }
    return;
  }
  if (action === "run-device-selected-recipe") {
    const select = app.querySelector(`[data-device-recipe-select="${button.dataset.slot}"]`);
    if (!select?.value) {
      showToast("Select a recipe first", "warning");
      return;
    }
    await runDeviceRecipe(Number(button.dataset.slot), select.value);
    return;
  }
  if (action === "manual-induction-start") {
    await startManualInduction(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-induction-stop") {
    await stopManualInduction(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-induction-power") {
    await adjustManualInductionPower(Number(button.dataset.slot), Number(button.dataset.delta || 0)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-magnetron-start") {
    await startManualMagnetron(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-magnetron-stop") {
    await stopManualMagnetron(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-stirrer-speed") {
    await setManualStirrer(Number(button.dataset.slot), button.dataset.speed || "LOW").catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-stirrer-stop") {
    await setManualStirrer(Number(button.dataset.slot), "OFF").catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-pump-start") {
    const input = app.querySelector('[data-input="manual-pump-units"]');
    const units = Math.max(1, Number(input?.value || state().ui.manualMode?.pumpUnits) || 10);
    mutate((draft) => {
      draft.ui.manualMode.pumpUnits = units;
    });
    await startManualPump(Number(button.dataset.slot), units).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-pump-stop") {
    await stopManualPump(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "confirm-completion") {
    confirmCompletion(Number(button.dataset.slot));
    return;
  }
  if (action === "complete-ingredients") {
    await completeIngredientStage(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "acknowledge-instruction") {
    await acknowledgeInstructionStep(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "abort-device") {
    await abortCurrentRecipe(Number(button.dataset.slot));
    return;
  }
  if (action === "restart-device") {
    await restartRecipe(Number(button.dataset.slot));
    return;
  }
  if (action === "open-add-user") {
    openModal("add-user");
    return;
  }
  if (action === "open-cloud-login") {
    openModal("cloud-auth", { mode: "login" });
    return;
  }
  if (action === "open-cloud-signup") {
    openModal("cloud-auth", { mode: "signup" });
    return;
  }
  if (action === "cloud-refresh-status") {
    await refreshCloudRuntime();
    showToast("Cloud status refreshed", "success");
    return;
  }
  if (action === "cloud-signout") {
    try {
      await authService.signOut();
      await refreshCloudRuntime();
      setCloudRuntime({
        lastSummary: "Signed out from cloud.",
        lastError: ""
      });
      showToast("Signed out from cloud", "success");
    } catch (error) {
      showToast(error.message, "error");
    }
    return;
  }
  if (action === "cloud-sync") {
    try {
      setCloudRuntime({ loading: true, lastError: "" });
      const result = await syncService.syncState(state());
      mutate((draft) => {
        result.recipeMappings.forEach((mapping) => {
          const recipe = draft.recipes.find((item) => item.id === mapping.localId);
          if (!recipe) return;
          recipe.cloudRecordId = mapping.cloudId || recipe.cloudRecordId || "";
          recipe.cloudUserId = result.sessionUser.id;
          recipe.recipeSignature = mapping.signature || recipe.recipeSignature || "";
        });
      });
      setCloudRuntime({
        loading: false,
        lastSyncAt: nowIso(),
        lastSummary: `Cloud sync complete: ${result.recipeCount} recipes, ${result.deviceCount} devices.`,
        lastError: ""
      });
      showToast("Cloud sync complete", "success");
    } catch (error) {
      setCloudRuntime({
        loading: false,
        lastError: error.message || "Cloud sync failed."
      });
      showToast(error.message, "error");
    }
    return;
  }
  if (action === "cloud-restore") {
    try {
      setCloudRuntime({ loading: true, lastError: "" });
      const rows = await syncService.restoreRecipes();
      const merged = mergeCloudRecipesIntoStore(rows);
      setCloudRuntime({
        loading: false,
        lastRestoreAt: nowIso(),
        lastSummary: `Cloud restore complete: ${merged} recipes merged from cloud.`,
        lastError: ""
      });
      showToast(`Restored ${merged} cloud recipe${merged === 1 ? "" : "s"}`, "success");
    } catch (error) {
      setCloudRuntime({
        loading: false,
        lastError: error.message || "Cloud restore failed."
      });
      showToast(error.message, "error");
    }
    return;
  }
  if (action === "clear-device-binding") {
    await ble.disconnect(Number(button.dataset.slot));
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(button.dataset.slot));
      if (!device) return draft;
      device.browserDeviceId = "";
      device.bluetoothName = "";
      device.connection = "disconnected";
      device.availableRecipeNames = [];
      device.recipeInventoryUpdatedAt = "";
      device.syncedRecipeNames = [];
      device.telemetry.currentRecipe = "";
      device.telemetry.firmwareVersion = "";
      device.telemetry.remainingSeconds = 0;
      device.telemetry.indPower = 0;
      device.telemetry.magPower = 0;
      device.telemetry.inductionStatus = "IDLE";
      device.telemetry.magnetronStatus = "IDLE";
      device.telemetry.pumpOn = false;
      device.lastMessage = "Pairing cleared";
      appendActivity(device, "Saved browser pairing was cleared", "warning");
    });
    showToast(`Device ${button.dataset.slot} pairing cleared`, "info");
    return;
  }
  if (action === "export-state") {
    const blob = new Blob([exportState(state())], { type: "application/json" });
    const href = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = href;
    anchor.download = `on2cook-cloud-export-${Date.now()}.json`;
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(href), 1000);
    return;
  }
  if (action === "sync-supabase") {
    try {
      await syncStateToSupabase(state());
      showToast("Supabase sync finished", "success");
    } catch (error) {
      showToast(error.message, "error");
    }
  }
}

async function handleChange(event) {
  const input = event.target;
  const path = input.dataset.settingPath;
  if (path) {
    if (path === "__user__") {
      mutate((draft) => {
        draft.currentUserId = input.value;
      });
      return;
    }
    const value =
      input.type === "checkbox"
        ? input.checked
        : input.type === "number"
          ? Number(input.value)
          : input.value;
    updateNestedSetting(path, value);
    if (path === "pendingAssignmentMode" || path === "queueMode") {
      queueIdleWork();
    }
    return;
  }

  if (input.dataset.input === "serial-photo" && input.files?.[0]) {
    const file = input.files[0];
    const slot = Number(input.dataset.slot);
    const reader = new FileReader();
    reader.onload = () => {
      mutate((draft) => {
        const device = draft.devices.find((item) => item.slot === slot);
        if (!device) return draft;
        device.serialPhotoDataUrl = String(reader.result || "");
      });
    };
    reader.readAsDataURL(file);
    return;
  }

  if (input.dataset.input === "device-recipe-filter") {
    const slot = Number(input.dataset.slot);
    mutate((draft) => {
      if (draft.ui.activeModal?.type !== "device-sheet" || Number(draft.ui.activeModal.payload?.slot) !== slot) return draft;
      draft.ui.activeModal.payload.recipeFilter = input.value;
    });
    return;
  }

  if (input.dataset.input === "manual-pump-units") {
    mutate((draft) => {
      draft.ui.manualMode.pumpUnits = Math.max(1, Math.trunc(Number(input.value) || 10));
    });
    return;
  }

  if (input.dataset.input === "global-recipe-search") {
    mutate((draft) => {
      draft.ui.globalRecipeSearch = input.value;
    });
    return;
  }

  if (input.dataset.input === "recipe-zip-file" && input.files?.[0]) {
    try {
      const result = await importRecipeZipFile(input.files[0]);
      const recipe = await importRecipeRecord(result, { showToast: false });
      const syncResult = await syncImportedRecipeToCloud(recipe);
      if (syncResult.synced) {
        showToast(`Imported and synced ${recipe.displayName}`, "success");
      } else if (syncResult.reason === "not-signed-in") {
        showToast(`Imported ${recipe.displayName} locally. Sign in to sync it to cloud.`, "info");
      } else if (syncResult.reason !== "error") {
        showToast(`Imported ${recipe.displayName}`, "success");
      }
      input.value = "";
    } catch (error) {
      showToast(error.message, "error");
    }
    return;
  }

  if (input.dataset.input === "import-state-file" && input.files?.[0]) {
    const file = input.files[0];
    const text = await file.text();
    try {
      const imported = importState(text, seedRecipes);
      store = createStore(imported);
      mutate((draft) => {
        syncSelectedRecipesToAllDevices(draft);
      });
      bindStore();
      showToast("Database imported", "success");
    } catch (error) {
      showToast(error.message, "error");
    }
  }
}

function bindStore() {
  store.subscribe(() => {
    render();
  });
  render();
}

async function init() {
  seedRecipes = await loadSeedRecipeCatalog();
  globalRecipeCatalog = await loadGlobalRecipeCatalog();
  store = createStore(loadState(seedRecipes));
  mutate((draft) => {
    syncSelectedRecipesToAllDevices(draft);
  });
  bindStore();
  handleTransportEvents();
  ensureStatusPolling();
  ensureIncomingOrderFeed();
  await registerServiceWorker();
  await refreshCloudRuntime();
  app.addEventListener("click", handleClick);
  app.addEventListener("submit", handleSubmit);
  app.addEventListener("change", handleChange);
  queueIdleWork();
}

init().catch((error) => {
  console.error(error);
  app.innerHTML = `
    <div class="fatal-shell">
      <h1>On2Cook Cloud could not start</h1>
      <p>${escapeHtml(error.message)}</p>
    </div>
  `;
});
