import { BleTransport, BLE_UUIDS } from "./ble-transport.js?v=20260713a";
import { importRecipeZipArrayBuffer, importRecipeZipFile, importRecipeZipUrl } from "./zip-reader.js?v=20260713a";
import {
  authService,
  profileService,
  cookLogService,
  recipeService,
  recipeSignatureFromJson,
  syncService
} from "./ncb-services.js?v=20260713a";
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
} from "./data-store.js?v=20260713a";

if (window.location.protocol === "https:" && window.location.hostname === "on2cook.net") {
  window.location.replace(`https://www.on2cook.net${window.location.pathname}${window.location.search}${window.location.hash}`);
}

const app = document.getElementById("app");
const SCROLL_STATE_KEY = "on2cook-cloud-scroll-state";
const UI_SESSION_STATE_KEY = "on2cook-cloud-ui-session-v1";
const APP_ASSET_VERSION = "20260713a";
const IS_APK_MODE =
  new URLSearchParams(window.location.search).get("apk") === "1" ||
  navigator.userAgent.includes("On2CookCloudApk");
const ble = new BleTransport();
let seedRecipes = [];
let globalRecipeCatalog = [];
let store = null;
let toastTimer = 0;
let statusTimer = 0;
let orderFeedTimer = 0;
let kotBridgeTimer = 0;
let proLiveTimer = 0;
let uiSessionSaveTimer = 0;
const statusRefreshTimers = new Map();
let lastApkScreenIndex = 0;
let proStudioShellOrientation = "portrait";
let proStudioRoutePath = "";
const recipeMissingRetryCounts = new Map();
const RECIPE_ARCHIVE_VERSION = "20260612q";
const KOT_BRIDGE_URL = "./api/orders/bridge";
const KOT_BRIDGE_POLL_MS = 10000;
const MAX_NOTIFICATIONS = 80;
const FIRMWARE_MANIFEST_URL = `./firmware/latest/manifest.json?v=${APP_ASSET_VERSION}`;
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
const kotBridgeRuntime = {
  active: false,
  runId: "",
  revision: 0,
  orderIds: new Set(),
  lastError: ""
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

function captureScrollState() {
  const positions = {};
  document.querySelectorAll("[data-scroll-key]").forEach((element) => {
    positions[element.dataset.scrollKey] = {
      left: element.scrollLeft || 0,
      top: element.scrollTop || 0
    };
  });
  return {
    windowX: window.scrollX || 0,
    windowY: window.scrollY || 0,
    positions
  };
}

function restoreScrollState(scrollState) {
  if (!scrollState) return;
  window.requestAnimationFrame(() => {
    window.scrollTo(scrollState.windowX || 0, scrollState.windowY || 0);
    Object.entries(scrollState.positions || {}).forEach(([key, position]) => {
      const element = Array.from(document.querySelectorAll("[data-scroll-key]")).find(
        (candidate) => candidate.dataset.scrollKey === key
      );
      if (!element) return;
      element.scrollLeft = position.left || 0;
      element.scrollTop = position.top || 0;
    });
    if (IS_APK_MODE) {
      const { rail, frames } = getApkRailFrames();
      if (rail && frames.length) setApkScreenSwitcherActive(getCurrentApkRailIndex(rail, frames));
    }
  });
}

function saveScrollStateForReload() {
  try {
    sessionStorage.setItem(SCROLL_STATE_KEY, JSON.stringify(captureScrollState()));
  } catch (error) {
    console.warn("[On2Cook] Could not save scroll state before refresh.", error);
  }
}

function sanitizeModalForSession(modal) {
  if (!modal?.type) return null;
  const safeModalTypes = new Set([
    "device-sheet",
    "device-status",
    "device-firmware",
    "stored-logs",
    "device-recipes",
    "order-details",
    "recipe-sheet",
    "manual-order",
    "device-manual",
    "assign-recipe",
    "device-metadata"
  ]);
  if (!safeModalTypes.has(modal.type)) return null;
  return structuredClone(modal);
}

function captureUiSessionState(snapshot = null) {
  const ui = snapshot?.ui || store?.getState?.().ui || {};
  const activeApkButton = document.querySelector(".apk-screen-button.active");
  const activeApkIndex = Number(activeApkButton?.dataset?.apkScreenIndex ?? ui.apkScreenIndex ?? lastApkScreenIndex) || 0;
  const activeTab = ui.activeTab === "manual" ? "orders" : ui.activeTab || "orders";
  return {
    activeTab,
    orderMode: ui.orderMode || "current",
    recipeMode: ui.recipeMode || "selected",
    globalRecipeSearch: ui.globalRecipeSearch || "",
    globalRecipePickedIds: Array.isArray(ui.globalRecipePickedIds) ? ui.globalRecipePickedIds.slice(0, 200) : [],
    manualMode: {
      slot: Math.max(1, Number(ui.manualMode?.slot) || 1),
      recipeId: ui.manualMode?.recipeId || "",
      sprayCount: Math.max(1, Number(ui.manualMode?.sprayCount) || 1),
      slotState: ui.manualMode?.slotState || {}
    },
    apkScreenIndex: Math.max(0, Math.trunc(activeApkIndex)),
    activeModal: sanitizeModalForSession(ui.activeModal),
    scroll: captureScrollState(),
    savedAt: nowIso()
  };
}

function saveUiSessionState(snapshot = null) {
  try {
    sessionStorage.setItem(UI_SESSION_STATE_KEY, JSON.stringify(captureUiSessionState(snapshot)));
    sessionStorage.setItem(SCROLL_STATE_KEY, JSON.stringify(captureScrollState()));
  } catch (error) {
    console.warn("[On2Cook] Could not save current screen state.", error);
  }
}

function scheduleSaveUiSessionState(snapshot = null, delay = 120) {
  if (uiSessionSaveTimer) window.clearTimeout(uiSessionSaveTimer);
  uiSessionSaveTimer = window.setTimeout(() => {
    uiSessionSaveTimer = 0;
    saveUiSessionState(snapshot);
  }, Math.max(0, Number(delay) || 0));
}

function takeSavedUiSessionState() {
  try {
    const raw = sessionStorage.getItem(UI_SESSION_STATE_KEY);
    sessionStorage.removeItem(UI_SESSION_STATE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (error) {
    console.warn("[On2Cook] Could not restore saved screen state.", error);
    return null;
  }
}

function applySavedUiSessionState(initialState, sessionState) {
  if (!initialState?.ui || !sessionState) return initialState;
  const validTabs = new Set(["orders", "recipes", "queue", "global"]);
  const validOrderModes = new Set(["current", "previous"]);
  const validRecipeModes = new Set(["selected", "final", "scale", "import"]);
  if (validTabs.has(sessionState.activeTab)) {
    initialState.ui.activeTab = sessionState.activeTab;
  }
  if (validOrderModes.has(sessionState.orderMode)) {
    initialState.ui.orderMode = sessionState.orderMode;
  }
  if (validRecipeModes.has(sessionState.recipeMode)) {
    initialState.ui.recipeMode = sessionState.recipeMode;
  }
  initialState.ui.globalRecipeSearch = String(sessionState.globalRecipeSearch || "");
  initialState.ui.globalRecipePickedIds = Array.isArray(sessionState.globalRecipePickedIds)
    ? sessionState.globalRecipePickedIds.slice(0, 200)
    : [];
  initialState.ui.manualMode = {
    ...initialState.ui.manualMode,
    ...(sessionState.manualMode || {})
  };
  initialState.ui.apkScreenIndex = Math.max(0, Math.trunc(Number(sessionState.apkScreenIndex) || 0));
  initialState.ui.activeModal = sanitizeModalForSession(sessionState.activeModal);
  return initialState;
}

function takeSavedScrollState() {
  try {
    const raw = sessionStorage.getItem(SCROLL_STATE_KEY);
    sessionStorage.removeItem(SCROLL_STATE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (error) {
    console.warn("[On2Cook] Could not restore saved scroll state.", error);
    return null;
  }
}

function nowIso() {
  return new Date().toISOString();
}

function waitMs(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, Math.max(0, Number(ms) || 0)));
}

function safeRandomId(prefix = "id") {
  const uuid =
    globalThis.crypto && typeof globalThis.crypto.randomUUID === "function"
      ? globalThis.crypto.randomUUID()
      : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  return `${prefix}-${uuid}`;
}

function safeOptionalUrl(value, label = "optional URL") {
  const url = String(value || "").trim();
  if (!url || url === "undefined" || url === "null") return "";
  if (/\/undefined(?:[?#]|$)/i.test(url)) {
    console.warn(`[On2Cook] Skipping ${label} because it resolved to undefined.`, { url });
    return "";
  }
  return url;
}

function normalizeFirmwareVersion(value) {
  return String(value || "")
    .replace(/^Firmware=/i, "")
    .trim()
    .toUpperCase();
}

function firmwareVersionsMatch(current, latest) {
  const currentVersion = normalizeFirmwareVersion(current);
  const latestVersion = normalizeFirmwareVersion(latest);
  return Boolean(currentVersion && latestVersion && currentVersion === latestVersion);
}

async function loadFirmwareManifest() {
  if (latestFirmwareManifest) return latestFirmwareManifest;
  if (firmwareManifestLoadPromise) return firmwareManifestLoadPromise;
  firmwareManifestLoadPromise = fetch(FIRMWARE_MANIFEST_URL, { cache: "no-store" })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`Firmware manifest failed to load: HTTP ${response.status}`);
      }
      const manifest = await response.json();
      const manifestUrl = new URL(FIRMWARE_MANIFEST_URL, window.location.href);
      const fileUrl = new URL(safeOptionalUrl(manifest.file, "firmware file") || "./firmware.bin", manifestUrl).href;
      latestFirmwareManifest = {
        ...manifest,
        manifestUrl: manifestUrl.href,
        absoluteFileUrl: fileUrl
      };
      return latestFirmwareManifest;
    })
    .catch((error) => {
      console.warn("[On2Cook] Firmware manifest unavailable.", error);
      latestFirmwareManifest = null;
      return null;
    })
    .finally(() => {
      firmwareManifestLoadPromise = null;
    });
  return firmwareManifestLoadPromise;
}

function isFirmwareBlockingDevice(device) {
  const status = String(device?.firmwareUpdate?.status || "").toLowerCase();
  return ["checking", "required", "updating", "downloading", "starting"].includes(status);
}

function firmwareBlockMessage(device) {
  const latest = device?.firmwareUpdate?.latestVersion || latestFirmwareManifest?.version || "latest firmware";
  const status = String(device?.firmwareUpdate?.status || "").toLowerCase();
  if (status === "updating" || status === "downloading" || status === "starting") {
    return `Device ${device.slot} firmware is updating to ${latest}. Recipe and manual commands are blocked until it finishes.`;
  }
  return `Device ${device.slot} needs firmware ${latest}. Update firmware before cooking.`;
}

function markFirmwareCurrent(draftDevice, firmwareVersion, manifest = latestFirmwareManifest) {
  draftDevice.firmwareUpdate = {
    ...(draftDevice.firmwareUpdate || {}),
    status: "current",
    currentVersion: firmwareVersion,
    latestVersion: manifest?.version || firmwareVersion,
    manifestUrl: manifest?.manifestUrl || "",
    fileUrl: manifest?.absoluteFileUrl || "",
    message: `Firmware is current (${firmwareVersion}).`,
    error: "",
    progress: 100,
    completedAt: draftDevice.firmwareUpdate?.completedAt || ""
  };
}

function markFirmwareRequired(draftDevice, firmwareVersion, manifest) {
  draftDevice.firmwareUpdate = {
    ...(draftDevice.firmwareUpdate || {}),
    status: "required",
    currentVersion: firmwareVersion,
    latestVersion: manifest?.version || "",
    manifestUrl: manifest?.manifestUrl || "",
    fileUrl: manifest?.absoluteFileUrl || "",
    message: `Firmware update required: ${firmwareVersion || "unknown"} -> ${manifest?.version || "latest"}.`,
    startedAt: "",
    completedAt: "",
    error: "",
    progress: 0
  };
}

async function evaluateFirmwareForDevice(slot, firmwareVersion) {
  const manifest = await loadFirmwareManifest();
  if (!manifest?.version) {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      draftDevice.firmwareUpdate = {
        ...(draftDevice.firmwareUpdate || {}),
        status: "unknown",
        currentVersion: firmwareVersion,
        message: "Firmware version received, but latest manifest is unavailable.",
        progress: 0
      };
    });
    return;
  }
  if (firmwareVersionsMatch(firmwareVersion, manifest.version)) {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      markFirmwareCurrent(draftDevice, firmwareVersion, manifest);
    });
    return;
  }
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    markFirmwareRequired(draftDevice, firmwareVersion, manifest);
    draftDevice.lastMessage = `Firmware update required before cooking: ${firmwareVersion || "unknown"} -> ${manifest.version}`;
    draftDevice.lastUpdatedAt = nowIso();
    pushDraftNotification(draft, {
      type: "device",
      title: "Firmware update required",
      deviceSlot: draftDevice.slot,
      message: `Update ${draftDevice.displayName} from ${firmwareVersion || "unknown"} to ${manifest.version} before cooking.`,
      action: { type: "device-firmware", label: "Open firmware", slot: draftDevice.slot }
    });
  });
  if (ble.usesNativeBridge) {
    startFirmwareUpdateForDevice(Number(slot), { automatic: true }).catch((error) => {
      showToast(error.message, "error");
    });
  } else {
    showToast(`Device ${slot} needs firmware ${manifest.version}. Automatic OTA requires the Android app.`, "warning");
  }
}

async function startFirmwareUpdateForDevice(slot, options = {}) {
  const device = getDevice(slot);
  if (!device) return;
  if (device.connection !== "connected") {
    showToast(`Connect Device ${slot} before firmware update.`, "warning");
    return;
  }
  const manifest = await loadFirmwareManifest();
  if (!manifest?.version || !manifest.absoluteFileUrl) {
    throw new Error("Latest firmware manifest is not available.");
  }
  if (firmwareVersionsMatch(device.telemetry?.firmwareVersion, manifest.version)) {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      markFirmwareCurrent(draftDevice, device.telemetry?.firmwareVersion || manifest.version, manifest);
    });
    if (!options.automatic) showToast(`Device ${slot} already has firmware ${manifest.version}.`, "success");
    return;
  }
  if (!ble.usesNativeBridge) {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      markFirmwareRequired(draftDevice, device.telemetry?.firmwareVersion || "", manifest);
    });
    throw new Error("Automatic firmware update needs the Android APK because the app must switch to ON2COOK_OTA WiFi.");
  }
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.firmwareUpdate = {
      ...(draftDevice.firmwareUpdate || {}),
      status: "starting",
      currentVersion: device.telemetry?.firmwareVersion || "",
      latestVersion: manifest.version,
      manifestUrl: manifest.manifestUrl,
      fileUrl: manifest.absoluteFileUrl,
      startedAt: nowIso(),
      completedAt: "",
      error: "",
      progress: 1,
      message: "Firmware update is starting. Cooking is blocked."
    };
    appendActivity(draftDevice, `Firmware update starting: ${manifest.version}`, "warning");
  });
  await ble.updateFirmware(Number(slot), manifest);
}

function userFacingCloudError(error, fallback = "Cloud sign-in is unavailable right now. Please try again later.") {
  const message = String(typeof error === "string" ? error : error?.message || "").trim();
  if (!message || /unexpected token|doctype|not valid json|html|json/i.test(message)) return fallback;
  return message;
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
      const zipUrl = safeOptionalUrl(entry.zipUrl, `seed recipe ZIP ${index + 1}`);
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
    id: options.catalogEntryId || safeRandomId("imported"),
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
const MANUAL_UI_HOLD_MS = 4500;
const MANUAL_SPRINKLE_UNITS = 1;

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

function normalizeKotRecipeName(value) {
  return String(value || "")
    .replace(/\([^)]*\)/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

function extractKotQuantityText(value, fallback = "1 item") {
  const match = String(value || "").match(/\(([^)]+)\)/);
  return match?.[1]?.trim() || fallback;
}

function parseKotCreatedAt(value, fallback = nowIso()) {
  const text = String(value || "").trim();
  if (!text) return fallback;
  const normalized = text.includes("T") ? text : text.replace(" ", "T");
  const date = new Date(normalized);
  return Number.isNaN(date.getTime()) ? fallback : date.toISOString();
}

function makeKotBridgeOrderId(entry, payload, index) {
  const order = payload?.properties?.Order || {};
  const raw = entry?.id || order.orderID || order.customer_invoice_id || `kot-${index + 1}`;
  return `kot-${String(raw).replace(/^kot-/, "").replace(/[^A-Za-z0-9_-]/g, "-")}`;
}

function createOrderFromKotBridgeEntry(snapshot, entry, index, bridgeRunId = "") {
  const payload = entry?.payload || entry;
  const properties = payload?.properties || {};
  const orderMeta = properties.Order || {};
  const customer = properties.Customer || {};
  const items = Array.isArray(properties.OrderItem) ? properties.OrderItem : [];
  const firstItem = items[0] || {};
  const itemName = normalizeKotRecipeName(firstItem.name || orderMeta.comment || `KOT Order ${index + 1}`);
  const recipe = findEffectiveRecipeForOrder(snapshot, itemName);
  const displayOrderId = String(orderMeta.orderID || orderMeta.customer_invoice_id || index + 1);
  return decorateOrderRecord(
    {
      id: makeKotBridgeOrderId(entry, payload, index),
      serverBridgeId: entry?.id || "",
      serverBridgeRunId: bridgeRunId || entry?.run_id || "",
      orderId: displayOrderId.startsWith("#") ? displayOrderId : `#${displayOrderId}`,
      itemName,
      recipeLookup: itemName,
      quantity: extractKotQuantityText(firstItem.name, `${Number(firstItem.quantity) || 1} item`),
      source: orderMeta.order_from || "POS",
      specialInstructions: firstItem.specialnotes || orderMeta.comment || "",
      accentColor: "#f47b20",
      createdAt: parseKotCreatedAt(orderMeta.created_on, entry?.received_at || nowIso()),
      status: "pending",
      assignedSlot: null,
      assignedMode: "auto",
      activeRecipeId: recipe?.id || "",
      currentRunRecipeName: recipe?.displayName || itemName,
      currentRunFirmwareName: recipe?.firmwareName || "",
      targetSlot: null,
      manual: String(orderMeta.order_from || "").toLowerCase() === "manual",
      historyNote: "",
      customerName: customer.name || "Walk-in",
      customerPhone: customer.phone || "",
      customerAddress: customer.address || "",
      itemCount: items.length || 1,
      totalAmount: toMoney(orderMeta.total || firstItem.total || 0),
      kot: payload
    },
    recipe,
    index
  );
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
  const isQueuedForDevice = (order) =>
    order &&
    order.status === "queued" &&
    order.assignedSlot === device.slot &&
    order.id !== device.currentJobId;
  const queued = snapshot.orders.current.filter(isQueuedForDevice);
  if (!device.queueOrderIds.length) return queued;
  const orderIndex = new Map(device.queueOrderIds.map((orderId, index) => [orderId, index]));
  return queued.sort((left, right) => {
    const leftIndex = orderIndex.has(left.id) ? orderIndex.get(left.id) : Number.MAX_SAFE_INTEGER;
    const rightIndex = orderIndex.has(right.id) ? orderIndex.get(right.id) : Number.MAX_SAFE_INTEGER;
    if (leftIndex !== rightIndex) return leftIndex - rightIndex;
    return snapshot.orders.current.indexOf(left) - snapshot.orders.current.indexOf(right);
  });
}

function getDeviceQueuedOrderIds(draft, device) {
  return getQueueOrders(draft, device).map((order) => order.id);
}

function isDeviceActivelyCooking(device) {
  return Boolean(device.currentJobId || device.completionConfirmationPending || hasLiveRuntime(device) || device.activeRun?.displayName || device.activeRun?.firmwareName);
}

function getDeviceCookedHistoryRows(snapshot, device, limit = 8) {
  const rows = [];
  const seen = new Set();
  const addRunRow = (run, sourceOrder = null, key = "") => {
    if (!run?.finishedAt && !sourceOrder?.createdAt) return;
    const recipe =
      getRecipeForRunRecord(snapshot, run) ||
      (sourceOrder ? getEffectiveRecipe(snapshot, sourceOrder) : null) ||
      findEffectiveRecipeForOrder(snapshot, sourceOrder?.recipeLookup || sourceOrder?.itemName || run?.displayName || run?.firmwareName);
    const sourceId = sourceOrder?.id || run?.orderId || key;
    const dedupeKey = `${sourceId || ""}:${run?.finishedAt || sourceOrder?.createdAt || ""}`;
    if (seen.has(dedupeKey)) return;
    seen.add(dedupeKey);
    rows.push({
      key: key || `history-${rows.length}`,
      orderId: sourceOrder?.id || run?.orderId || "",
      displayOrderId: sourceOrder?.orderId || run?.orderId || "",
      recipeId: run?.recipeId || sourceOrder?.activeRecipeId || recipe?.id || "",
      displayName: run?.displayName || sourceOrder?.itemName || recipe?.displayName || run?.firmwareName || "Recipe",
      firmwareName: run?.firmwareName || sourceOrder?.currentRunFirmwareName || recipe?.firmwareName || "",
      outcome: run?.outcome || sourceOrder?.status || "completed",
      finishedAt: run?.finishedAt || sourceOrder?.createdAt || "",
      durationSeconds: Number(run?.durationSeconds) || getRecipeDuration(recipe),
      actualDurationSeconds: Number(run?.actualDurationSeconds) || 0,
      recook: Boolean(sourceOrder?.recook)
    });
  };

  if (device.lastRun?.finishedAt) {
    addRunRow(device.lastRun, null, `last-${device.slot}`);
  }

  (snapshot.orders.previous || [])
    .filter(
      (order) =>
        order.assignedSlot === device.slot ||
        order.targetSlot === device.slot ||
        device.historyOrderIds.includes(order.id)
    )
    .forEach((order) => {
      addRunRow(
        {
          orderId: order.id,
          recipeId: order.activeRecipeId,
          displayName: order.itemName,
          firmwareName: order.currentRunFirmwareName,
          startedAt: order.startedAt || order.createdAt,
          finishedAt: order.finishedAt || order.createdAt,
          durationSeconds: getRecipeDuration(getEffectiveRecipe(snapshot, order)),
          outcome: order.status === "aborted" ? "aborted" : "completed"
        },
        order,
        `order-${order.id}`
      );
    });

  return rows
    .sort((left, right) => new Date(right.finishedAt || 0).getTime() - new Date(left.finishedAt || 0).getTime())
    .slice(0, limit);
}

function getQueueTimelineModel(snapshot, device) {
  const currentOrder = getCurrentJob(snapshot, device);
  const runtimeRecipe = getRuntimeRecipe(snapshot, device) || (currentOrder ? getEffectiveRecipe(snapshot, currentOrder) : null);
  const upcoming = getQueueOrders(snapshot, device);
  const currentActive = isDeviceActivelyCooking(device);
  const remainingSeconds = currentActive ? getDeviceActiveRemainingSeconds(device, runtimeRecipe) : 0;
  let cursorSeconds = remainingSeconds;
  return {
    cooked: getDeviceCookedHistoryRows(snapshot, device),
    now: currentActive
      ? {
          orderId: currentOrder?.id || device.currentJobId || "",
          displayOrderId: currentOrder?.orderId || "",
          displayName: device.activeRun?.displayName || currentOrder?.itemName || runtimeRecipe?.displayName || getLiveRecipeName(device) || "Cooking now",
          status: device.telemetry.workStatus || currentOrder?.status || "cooking",
          startedAt: device.activeRun?.startedAt || currentOrder?.createdAt || "",
          remainingSeconds,
          recipe: runtimeRecipe
        }
      : null,
    upcoming: upcoming.map((order, index) => {
      const recipe = getEffectiveRecipe(snapshot, order);
      const startsInSeconds = cursorSeconds;
      const durationSeconds = getRecipeDuration(recipe);
      cursorSeconds += durationSeconds;
      return {
        order,
        recipe,
        index,
        startsInSeconds,
        startsAt: new Date(Date.now() + startsInSeconds * 1000).toISOString(),
        durationSeconds
      };
    })
  };
}

function getQuickAssignRecipes(snapshot, device, limit = 3) {
  const candidates = [];
  const addRecipe = (recipe) => {
    if (!recipe || candidates.some((item) => item.id === recipe.id)) return;
    if (recipe.selected === false) return;
    candidates.push(recipe);
  };

  getDeviceCookedHistoryRows(snapshot, device, 8).forEach((row) => {
    addRecipe(
      (row.recipeId ? findRecipeById(snapshot, row.recipeId) : null) ||
        findRecipeByFirmwareName(snapshot, row.firmwareName) ||
        findEffectiveRecipeForOrder(snapshot, row.displayName)
    );
  });
  getQueueOrders(snapshot, device).forEach((order) => addRecipe(getEffectiveRecipe(snapshot, order)));
  snapshot.orders.current
    .filter((order) => order.assignedSlot === device.slot || order.targetSlot === device.slot)
    .forEach((order) => addRecipe(getEffectiveRecipe(snapshot, order)));
  snapshot.recipes.forEach((recipe) => addRecipe(recipe));
  return candidates.slice(0, limit);
}

function getAssignRecipeSearchResults(snapshot, modal) {
  const query = String(modal?.payload?.query || "").trim().toLowerCase();
  const localRows = snapshot.recipes
    .filter((recipe) => recipe.selected !== false)
    .filter(
      (recipe) =>
        !query ||
        recipe.displayName.toLowerCase().includes(query) ||
        recipe.firmwareName.toLowerCase().includes(query) ||
        recipe.aliases.some((alias) => alias.toLowerCase().includes(query))
    )
    .slice(0, 30)
    .map((recipe) => ({
      kind: "local",
      id: recipe.id,
      title: recipe.displayName,
      subtitle: `${recipe.firmwareName} | ${recipe.source || "local"}`,
      recipe
    }));

  const localNames = new Set(localRows.map((row) => normalizeRecipeNameKey(row.title)));
  const catalogRows = getRecipeCatalog(snapshot)
    .filter(
      (entry) =>
        !query ||
        String(entry.name || entry.recipeName || "").toLowerCase().includes(query) ||
        String(entry.id || "").toLowerCase().includes(query)
    )
    .filter((entry) => !localNames.has(normalizeRecipeNameKey(entry.name || entry.recipeName || entry.id)))
    .slice(0, Math.max(0, 30 - localRows.length))
    .map((entry) => ({
      kind: "global",
      id: entry.id,
      title: entry.name || entry.recipeName || entry.id || "Global recipe",
      subtitle: entry.sourceName || entry.zipName || "Global Recipes",
      entry
    }));

  return [...localRows, ...catalogRows].slice(0, 30);
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

function addKnownRecipeName(target, value) {
  const clean = String(value || "").trim();
  const key = normalizeRecipeNameKey(clean);
  if (clean && key && !target.has(key)) {
    target.set(key, clean);
  }
}

function getKnownDeviceRecipeNames(snapshot, device) {
  const names = new Map();
  if (!device) return [];
  [...(device.availableRecipeNames || []), ...(device.syncedRecipeNames || [])].forEach((name) =>
    addKnownRecipeName(names, name)
  );
  [device.activeRun, device.lastRun].forEach((run) => {
    addKnownRecipeName(names, run?.firmwareName);
    addKnownRecipeName(names, run?.displayName);
    const recipe = run?.recipeId ? findRecipeById(snapshot, run.recipeId) : null;
    addKnownRecipeName(names, recipe?.firmwareName);
  });
  (snapshot?.orders?.current || [])
    .filter(
      (order) =>
        order.targetSlot === device.slot ||
        order.id === device.currentJobId ||
        order.id === device.activeRun?.orderId ||
        order.id === device.lastRun?.orderId
    )
    .forEach((order) => {
      addKnownRecipeName(names, order.currentRunFirmwareName);
      const recipe = getEffectiveRecipe(snapshot, order);
      addKnownRecipeName(names, recipe?.firmwareName);
    });
  return [...names.values()].sort((left, right) => left.localeCompare(right));
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

function getManualDeviceRunState(snapshot, device) {
  const liveSession = ble.getSession(device.slot);
  const queuedCount = getQueueOrders(snapshot, device).length;
  const currentOrder = getCurrentJob(snapshot, device);
  const connected = device.enabled && device.connection === "connected";
  const statusText = String(device.telemetry.workStatus || "").toLowerCase();
  if (!connected) {
    return {
      status: "offline",
      label: device.connection === "connecting" ? "Connecting" : "Offline",
      note: "Connect this device before running or queuing a recipe.",
      canRunNow: false,
      canQueue: false,
      actionLabel: "Connect first"
    };
  }
  if (liveSession?.transfer) {
    return {
      status: "syncing",
      label: "Syncing",
      note: "Recipe inventory or upload is in progress.",
      canRunNow: false,
      canQueue: false,
      actionLabel: "Wait for sync"
    };
  }
  if (device.completionConfirmationPending) {
    return {
      status: "busy",
      label: "Awaiting confirmation",
      note: "Confirm the completed recipe before adding the next one.",
      canRunNow: false,
      canQueue: true,
      actionLabel: "Add to queue"
    };
  }
  if (currentOrder || device.currentJobId || hasLiveRuntime(device)) {
    return {
      status: "busy",
      label: statusText === "awaiting_confirmation" ? "Awaiting ingredients" : "Running",
      note: "This device is cooking now. The selected recipe can be added to its queue.",
      canRunNow: false,
      canQueue: true,
      actionLabel: "Add to queue"
    };
  }
  if (queuedCount > 0) {
    return {
      status: "queued",
      label: `Queue ${queuedCount}`,
      note: "This device already has queued work. The selected recipe will be placed after it.",
      canRunNow: false,
      canQueue: true,
      actionLabel: "Add to queue"
    };
  }
  return {
    status: "idle",
    label: "Idle",
    note: "Ready to run the selected recipe immediately.",
    canRunNow: true,
    canQueue: false,
    actionLabel: "Run now"
  };
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

function getRecipeStepElapsed(device, recipe) {
  const totalSeconds = Number(device.activeRun?.durationSeconds) || getRecipeDuration(recipe);
  const remaining = Math.max(0, Number(device.telemetry.remainingSeconds) || 0);
  if (totalSeconds && remaining) return Math.max(0, totalSeconds - remaining);
  if (device.activeRun?.startedAt) return elapsedSecondsBetween(device.activeRun.startedAt, nowIso());
  return 0;
}

function getStepTiming(recipe, stepIndex) {
  const steps = Array.isArray(recipe?.recipeJson?.Instruction) ? recipe.recipeJson.Instruction : [];
  const startsAt = steps.slice(0, Math.max(0, stepIndex)).reduce((total, step) => total + getInstructionDuration(step), 0);
  const duration = getInstructionDuration(steps[stepIndex]);
  return {
    startsAt,
    endsAt: startsAt + duration,
    duration
  };
}

function getStepIngredient(recipe, stepIndex) {
  const ingredients = Array.isArray(recipe?.recipeJson?.Ingredients)
    ? recipe.recipeJson.Ingredients
    : Array.isArray(recipe?.recipeJson?.Ingredient)
      ? recipe.recipeJson.Ingredient
      : [];
  return ingredients[stepIndex] || null;
}

function isWaterStep(step) {
  const pumpValue = String(step?.pump_on || "").trim().toLowerCase();
  const title = String(step?.Text || "").toLowerCase();
  return (
    pumpValue === "on" ||
    (Number(step?.pump_on) || 0) > 0 ||
    title.includes("water")
  );
}

function getLiquidStepValue(value) {
  const text = String(value || "").trim();
  if (!text || text === "0" || text.toLowerCase() === "off" || text.toLowerCase() === "false") {
    return {
      active: false,
      fill: 0,
      label: "Off"
    };
  }
  if (text.toLowerCase() === "on" || text.toLowerCase() === "true") {
    return {
      active: true,
      fill: 100,
      label: "On"
    };
  }
  const numeric = Number(text);
  if (Number.isFinite(numeric) && numeric > 0) {
    const liquidMl = numeric * 10;
    return {
      active: true,
      fill: Math.min(100, Math.max(12, numeric)),
      label: `${liquidMl} ml`
    };
  }
  return {
    active: true,
    fill: 100,
    label: text
  };
}

function buildOperatorPrompt(recipe, step, stepIndex) {
  if (!step) return null;
  if (isWaterStep(step)) {
    return {
      type: "water",
      title: "Check water",
      detail: "Confirm the pump has enough water before this automatic water step.",
      tone: "water"
    };
  }
  const ingredient = getStepIngredient(recipe, stepIndex);
  if (ingredient) {
    const parsed = splitIngredientNameWeight(ingredient, stepIndex);
    const quantity = [parsed.quantity || "", parsed.unit || ""].filter(Boolean).join(" ").trim();
    return {
      type: "ingredient",
      title: `Add ${parsed.name}`,
      detail: quantity || step.Weight || step.Text || "",
      tone: "ingredient"
    };
  }
  if (step.Weight && String(step.Weight).trim() && String(step.Weight).trim() !== "0") {
    return {
      type: "ingredient",
      title: step.Text ? `Add ${step.Text}` : `Add ingredient ${stepIndex + 1}`,
      detail: step.Weight,
      tone: "ingredient"
    };
  }
  return null;
}

function getOperatorStepState(device, recipe) {
  const steps = Array.isArray(recipe?.recipeJson?.Instruction) ? recipe.recipeJson.Instruction : [];
  const activeIndex = Math.max(0, Math.min(steps.length - 1, Number(device.telemetry.stepNo || 1) - 1));
  const currentStep = steps[activeIndex] || null;
  const elapsed = getRecipeStepElapsed(device, recipe);
  const currentTiming = getStepTiming(recipe, activeIndex);
  const currentPrompt = buildOperatorPrompt(recipe, currentStep, activeIndex);
  let nextIndex = -1;
  let nextPrompt = null;
  for (let index = activeIndex + 1; index < steps.length; index += 1) {
    const prompt = buildOperatorPrompt(recipe, steps[index], index);
    if (prompt) {
      nextIndex = index;
      nextPrompt = prompt;
      break;
    }
  }
  const nextTiming = nextIndex >= 0 ? getStepTiming(recipe, nextIndex) : null;
  const secondsToNext = nextTiming ? Math.max(0, Math.ceil(nextTiming.startsAt - elapsed)) : null;
  return {
    activeIndex,
    currentStep,
    currentPrompt,
    currentRemaining: Math.max(0, Math.ceil(currentTiming.endsAt - elapsed)),
    nextIndex,
    nextPrompt,
    secondsToNext,
    totalSteps: steps.length
  };
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

function shouldRenderLiveTimeline(device, currentOrder) {
  return Boolean(currentOrder || hasLiveRuntime(device) || device.activeRun?.displayName || device.activeRun?.firmwareName);
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
  device.telemetry.stirrer = options.connection === "disconnected" ? "OFF" : DEFAULT_STIRRER_LEVEL;
  device.telemetry.pumpOn = false;
  device.telemetry.paused = false;
  device.telemetry.lastRaw = "";
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
  return safeOptionalUrl(order.previewImageDataUrl, "order image");
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

function renderUiIcon(name) {
  const icons = {
    orders: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 6h14M7 12h14M7 18h14"/><path d="M3.5 6h.01M3.5 12h.01M3.5 18h.01"/></svg>`,
    recipes: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 10a6 6 0 0 1 12 0"/><path d="M5 10h14v7a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3z"/><path d="M9 10V8m6 2V8"/></svg>`,
    queue: `<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8"/><path d="M12 7v5l3 2"/></svg>`,
    manual: `<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="8" width="16" height="8" rx="4"/><circle cx="9" cy="12" r="2"/></svg>`,
    global: `<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18"/></svg>`,
    bell: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 17h12l-1.4-2.4V10a4.6 4.6 0 0 0-9.2 0v4.6z"/><path d="M10 20h4"/></svg>`,
    device: `<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="7" y="3" width="10" height="18" rx="2"/><path d="M10 18h4"/></svg>`,
    cooking: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21c3.3 0 6-2.4 6-5.8 0-2.5-1.5-4.4-3.6-6.3-.7 2-1.8 3-3.2 3.7.4-3-1.1-5.2-3-7C7.6 8.5 6 10.6 6 15.2 6 18.6 8.7 21 12 21z"/></svg>`,
    error: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l9 16H3z"/><path d="M12 9v4M12 17h.01"/></svg>`,
    logs: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 3h8l4 4v14H7z"/><path d="M15 3v5h5M9 13h6M9 17h6"/></svg>`,
    order: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 4h11v16H5V7z"/><path d="M8 4v3H5M9 11h6M9 15h6"/></svg>`,
    plus: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>`,
    more: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5h.01M12 12h.01M12 19h.01"/></svg>`,
    chevronLeft: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 6l-6 6 6 6"/></svg>`,
    chevronRight: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 6l6 6-6 6"/></svg>`,
    refresh: `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6v5h-5"/><path d="M4 18v-5h5"/><path d="M18.5 9A7 7 0 0 0 6.4 5.8L4 8"/><path d="M5.5 15a7 7 0 0 0 12.1 3.2L20 16"/></svg>`
  };
  return icons[name] || "";
}

function scrollDeviceQueueTimelineIntoView(slot) {
  const targetSlot = Number(slot) || 1;
  const activeModal = state().ui.activeModal;
  if (activeModal?.type !== "device-sheet" || Number(activeModal.payload?.slot) !== targetSlot) {
    openModal("device-sheet", { slot: targetSlot });
  }
  const scroll = () => {
    const target = app.querySelector(".device-detail-screen .queue-timeline-card");
    if (target) {
      target.scrollIntoView({ block: "start", behavior: "smooth" });
    }
  };
  window.requestAnimationFrame(scroll);
  window.setTimeout(scroll, 80);
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

function renderOrderDeviceAccess(snapshot) {
  const devices = snapshot.devices.slice(0, 5);
  return `
    <section class="stack-section order-device-access dashboard-device-access">
      <div class="dashboard-section-title">
        <div class="mini-title">Device access</div>
        <button class="text-link-button" type="button" data-action="order-jump-device" data-slot="1">View All ${renderUiIcon("chevronRight")}</button>
      </div>
      <div class="dashboard-device-strip">
        <span class="strip-arrow">${renderUiIcon("chevronLeft")}</span>
        ${devices
          .map(
            (device) => {
              const runState = getManualDeviceRunState(snapshot, device);
              const online = device.connection === "connected";
              const active = runState.status === "busy" || runState.status === "syncing";
              return `
              <button
                class="dashboard-device-card ${online ? "online" : "offline"} ${active ? "active" : ""}"
                type="button"
                data-action="open-device-sheet"
                data-slot="${device.slot}"
              >
                <img src="./assets/on2cook-logo.png" alt="" aria-hidden="true">
                <strong>${device.slot}</strong>
                <span><i></i>${escapeHtml(online ? "Online" : "Offline")}</span>
                <small>${escapeHtml(active ? runState.label : online ? "Idle" : "Offline")}</small>
              </button>
            `;
            }
          )
          .join("")}
        <span class="strip-arrow">${renderUiIcon("chevronRight")}</span>
      </div>
    </section>
  `;
}

function renderRefinedScreenTopBar(snapshot, title, subtitle = "") {
  const connectedCount = snapshot.devices.filter((device) => device.connection === "connected").length;
  const busyCount = snapshot.orders.current.filter((order) => ["starting", "cooking", "awaiting_confirmation"].includes(order.status)).length;
  const unreadCount = getUnreadNotificationCount(snapshot);
  return `
    <div class="refined-screen-topbar">
      <div class="refined-brand-lockup">
        <img src="./assets/on2cook-logo.png" alt="On2Cook">
        <div>
          <strong>${escapeHtml(title)}</strong>
          ${subtitle ? `<span>${escapeHtml(subtitle)}</span>` : ""}
        </div>
      </div>
      <div class="refined-stat-stack">
        <span><b>D</b>${escapeHtml(connectedCount)}</span>
        <span><b>B</b>${escapeHtml(busyCount)}</span>
        <button class="refined-notification-button" type="button" data-action="open-notification-drawer" aria-label="Open notifications">
          ${renderUiIcon("bell")}
          ${unreadCount ? `<em>${escapeHtml(Math.min(unreadCount, 99))}</em>` : ""}
        </button>
      </div>
    </div>
  `;
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
    id: safeRandomId("id"),
    text,
    tone,
    at,
    label: meta?.label || "",
    direction: meta?.direction || "",
    channel: meta?.channel || ""
  };
  device.activity = [item, ...(device.activity || [])].slice(0, 100);
  device.lastUpdatedAt = at;
  device.lastMessage = text;
  persistDeviceActivityToCloud(device, item);
}

function persistDeviceActivityToCloud(device, item) {
  if (!device || !item?.text) return;
  const deviceSnapshot = JSON.parse(JSON.stringify(device));
  const itemSnapshot = { ...item };
  Promise.resolve()
    .then(async () => {
      const status = await authService.getStatus().catch(() => null);
      const localUser = getCurrentUser(state());
      const cloudUserId = status?.session?.id || localUser.cloudUserId || "";
      if (!cloudUserId) return;
      await cookLogService.append({
        user_id: cloudUserId,
        device_id: String(
          deviceSnapshot.browserDeviceId ||
            deviceSnapshot.bluetoothName ||
            deviceSnapshot.displayName ||
            `device-${deviceSnapshot.slot}`
        ),
        recipe_id: null,
        recipe_title:
          deviceSnapshot.activeRun?.displayName ||
          deviceSnapshot.lastRun?.displayName ||
          deviceSnapshot.telemetry?.currentRecipe ||
          "",
        order_id: deviceSnapshot.currentJobId || deviceSnapshot.activeRun?.orderId || deviceSnapshot.lastRun?.orderId || "",
        outcome:
          itemSnapshot.tone === "error"
            ? "error"
            : deviceSnapshot.lastRun?.outcome ||
              (String(itemSnapshot.text).toLowerCase().includes("disconnect") ? "disconnected" : itemSnapshot.tone || "info"),
        started_at: deviceSnapshot.activeRun?.startedAt || deviceSnapshot.lastRun?.startedAt || null,
        finished_at: deviceSnapshot.lastRun?.finishedAt || null,
        aborted_at:
          deviceSnapshot.lastRun?.outcome === "aborted" ? deviceSnapshot.lastRun?.finishedAt || itemSnapshot.at : null,
        telemetry_json: JSON.stringify({
          slot: deviceSnapshot.slot,
          displayName: deviceSnapshot.displayName,
          bluetoothName: deviceSnapshot.bluetoothName,
          connection: deviceSnapshot.connection,
          workStatus: deviceSnapshot.telemetry?.workStatus || "",
          stepNo: deviceSnapshot.telemetry?.stepNo || 0,
          remainingSeconds: deviceSnapshot.telemetry?.remainingSeconds || 0,
          label: itemSnapshot.label,
          direction: itemSnapshot.direction,
          channel: itemSnapshot.channel
        }),
        summary: itemSnapshot.text,
        created_at: itemSnapshot.at
      });
    })
    .catch((error) => {
      console.warn("[On2Cook] Cloud cook log append failed.", error);
    });
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

const MAX_LIVE_DEVICE_LOG_CHARS = 120000;
const MAX_LIVE_LOG_ENTRIES = 300;

function emptyLogFetchState() {
  return {
    listing: false,
    reading: false,
    activeFile: "",
    activeDisplayName: "",
    content: "",
    started: false,
    complete: false,
    error: "",
    status: "",
    updatedAt: ""
  };
}

function canUseDeviceForRecipeActions(device) {
  return Boolean(device?.enabled && device.connection === "connected" && !isFirmwareBlockingDevice(device));
}

function ensureDeviceCommandAllowed(device, commandLabel = "command") {
  if (!device || device.connection !== "connected") {
    throw new Error("Device is not connected.");
  }
  if (isFirmwareBlockingDevice(device)) {
    throw new Error(`${commandLabel} blocked. ${firmwareBlockMessage(device)}`);
  }
}

function emptyLiveLogState() {
  return {
    active: false,
    starting: false,
    error: "",
    status: "Live logs are off.",
    openedAt: "",
    updatedAt: "",
    entries: []
  };
}

function formatLogFileDisplay(rawName) {
  return String(rawName || "")
    .replace(/^.*[\\/]/, "")
    .replace(/\.txt$/i, "")
    .trim() || "Device log";
}

function ensureDeviceLogState(device) {
  if (!Array.isArray(device.logFiles)) {
    device.logFiles = [];
  }
  device.logFetch = {
    ...emptyLogFetchState(),
    ...(device.logFetch || {})
  };
  return device.logFetch;
}

function ensureDeviceLiveLogState(device) {
  device.liveLog = {
    ...emptyLiveLogState(),
    ...(device.liveLog || {})
  };
  if (!Array.isArray(device.liveLog.entries)) {
    device.liveLog.entries = [];
  }
  return device.liveLog;
}

function parseLooseKeyValues(message) {
  const parsed = {};
  String(message || "")
    .split(/[,|\n\r]+/)
    .map((part) => part.trim())
    .filter(Boolean)
    .forEach((part) => {
      const separatorIndex = part.search(/[:=]/);
      if (separatorIndex <= 0) return;
      const key = part.slice(0, separatorIndex).trim();
      const value = part.slice(separatorIndex + 1).trim();
      if (!key || !value) return;
      parsed[key] = value;
    });
  return parsed;
}

function parseFirmwareLiveLogCsv(message) {
  const raw = String(message || "").trim();
  if (!/^log=/i.test(raw)) return null;
  const parts = raw.split(",").map((part) => part.trim());
  const timestamp = parts[0]?.replace(/^log=/i, "").trim() || "";
  const macId = parts[1] || "";
  const recipeIndex = parts.findIndex((part, index) => (
    index > 1 &&
    /[A-Za-z]/.test(part) &&
    !part.includes("=") &&
    !/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/i.test(part)
  ));
  const values = parts.slice(2, recipeIndex > 0 ? recipeIndex : undefined);
  const recipeName = recipeIndex > 0 ? parts[recipeIndex] : "";
  const tail = recipeIndex > 0 ? parts.slice(recipeIndex + 1) : [];
  return {
    LOG_TIMESTAMP: timestamp,
    MAC_ID: macId,
    ON_COUNT: values[0] || "",
    IGBT_TEMP: values[1] || "",
    GLASS_TEMP: values[2] || "",
    AMBIENT_TEMP: values[3] || "",
    MAG_CURRENT: values[4] || "",
    IND_CURRENT: values[5] || "",
    IND_VOLTAGE: values[6] || "",
    COIL_TEMP: values[7] || "",
    PAN_TEMP: values[8] || "",
    PCB_TEMP: values[9] || "",
    DEVICE_MODE: values[10] || "",
    MAG_ON: values[11] || "",
    IND_ON: values[12] || "",
    RECIPE: recipeName,
    STEPNO: tail[0] || "",
    TOTAL_STEPS: tail[1] || "",
    TIME_LEFT: tail[2] || "",
    SENSOR_PACKET: "1"
  };
}

function enrichLiveLogParsed(message, parsed = {}) {
  const raw = String(message || "").trim();
  const csv = parseFirmwareLiveLogCsv(raw);
  const enriched = {
    ...(csv || {}),
    ...(parsed || {})
  };
  const stirrerMatch = raw.match(/STIRRER=([^,\s]+),([^,\s]+)/i);
  if (stirrerMatch) {
    enriched.STIRRER = stirrerMatch[1];
    enriched.STIRRER_MODE = stirrerMatch[2];
  }
  const microwaveMatch = raw.match(/MAGQUICKSTART=([^,\s]+)/i);
  if (microwaveMatch) {
    enriched.MAGQUICKSTART = microwaveMatch[1];
  }
  return enriched;
}

function pickParsedValue(parsed, names, fallback = "") {
  const pairs = Object.entries(parsed || {});
  for (const name of names) {
    const direct = parsed?.[name];
    if (direct !== undefined && direct !== null && String(direct).trim() !== "") return String(direct).trim();
    const found = pairs.find(([key]) => key.toLowerCase() === String(name).toLowerCase());
    if (found && String(found[1]).trim() !== "") return String(found[1]).trim();
  }
  return fallback;
}

function formatLiveLogPower(value) {
  const clean = String(value || "").trim();
  if (!clean) return "-";
  if (/^\d+$/.test(clean)) return `${clean}%`;
  return clean;
}

function formatLiveLogSeconds(value) {
  const clean = String(value || "").trim();
  if (!clean || !/^-?\d+(\.\d+)?$/.test(clean)) return clean || "-";
  return secondsLabel(Math.max(0, Math.round(Number(clean))));
}

function formatLiveLogTemperature(value) {
  const clean = String(value || "").trim();
  if (!clean || clean === "-") return "-";
  if (/^-?\d+(\.\d+)?$/.test(clean)) return `${Number(clean).toFixed(Number(clean) % 1 ? 1 : 0)}C`;
  return clean;
}

function buildLiveLogEntry(device, detail = {}) {
  const rawMessage = String(detail.message || "").trim();
  const parsed = enrichLiveLogParsed(rawMessage, {
    ...parseLooseKeyValues(rawMessage),
    ...(detail.parsed || {})
  });
  const isSensorPacket = pickParsedValue(parsed, ["SENSOR_PACKET"], "") === "1";
  const isManualPacket = Boolean(
    pickParsedValue(parsed, ["INDQUICKSTART", "MAGQUICKSTART", "FRYQUICKSTART"], "")
  );
  const isRecipePacket = Boolean(pickParsedValue(parsed, ["RECIPE"], "")) && !isSensorPacket;
  const workStatus = pickParsedValue(parsed, ["WORKSTATUS"], "");
  const modeType =
    isManualPacket ? "manual" :
      isRecipePacket ? "recipe" :
        isSensorPacket ? "sensor" :
          workStatus ? "status" :
            detail.direction === "tx" ? "command" : "message";
  const recipeName =
    pickParsedValue(parsed, ["RECIPE", "recipe", "RECIPENAME", "recipe_name"], "") ||
    device.activeRun?.displayName ||
    device.telemetry?.currentRecipe ||
    "";
  const stepNo =
    pickParsedValue(parsed, ["STEP", "step", "STEPNO", "stepNo", "INSTR", "instruction"], "") ||
    (device.telemetry?.stepNo ? String(device.telemetry.stepNo) : "");
  const totalSteps = pickParsedValue(parsed, ["TOTAL_STEPS", "TOTALSTEP", "totalStep"], "");
  const timeLeft = pickParsedValue(parsed, ["TIME_LEFT", "time_left", "timeLeft"], "");
  const inductionRun = pickParsedValue(parsed, ["IND_RUN", "INDSEC", "FRYSEC"], "");
  const microwaveRun = pickParsedValue(parsed, ["MAG_RUN", "MICROWAVESEC"], "");
  const inductionState = pickParsedValue(parsed, ["INDQUICKSTART", "IND_RUN_STATE", "IND"], "");
  const microwaveState = pickParsedValue(parsed, ["MAGQUICKSTART", "MICROWAVE"], "");
  const induction =
    pickParsedValue(parsed, ["INDPOWER", "indPower", "Induction_power", "induction", "IH", "IND"], "") ||
    (device.telemetry?.inductionPower ? String(device.telemetry.inductionPower) : "");
  const microwave =
    pickParsedValue(parsed, ["MAGPOWER", "magPower", "Magnetron_power", "microwave", "MW", "MAG"], "") ||
    (device.telemetry?.microwavePower ? String(device.telemetry.microwavePower) : "");
  const stirrer =
    [
      pickParsedValue(parsed, ["STIRRER", "stirrer", "stirrer_on", "STR"], ""),
      pickParsedValue(parsed, ["STIRRER_MODE", "stirrerMode"], "")
    ].filter(Boolean).join(" ") ||
    device.telemetry?.stirrer ||
    "";
  const pump =
    pickParsedValue(parsed, ["PUMP", "pump", "pump_on", "WATER", "water", "purge_on"], "") ||
    device.telemetry?.pump ||
    "";
  const temperature = pickParsedValue(parsed, ["TEMP", "TEMPERATURE", "PAN_TEMP", "PAN_TEMP", "temperature", "temp", "NTC"], "") ||
    pickParsedValue(parsed, ["PAN_TEMP", "GLASS_TEMP", "IGBT_TEMP", "AMBIENT_TEMP"], "");
  const currentVoltage = [
    pickParsedValue(parsed, ["CURRENT", "current", "CUR", "I", "IND_CURRENT"], ""),
    pickParsedValue(parsed, ["VOLTAGE", "voltage", "VOLT", "V", "IND_VOLTAGE"], "")
  ].filter(Boolean).join(" / ");
  const error = pickParsedValue(parsed, ["ERROR", "error", "ERR", "FAULT", "fault"], "");
  return {
    id: safeRandomId("live-log"),
    at: detail.at || nowIso(),
    direction: detail.direction || "rx",
    channel: detail.channel || "device",
    modeType,
    status: pickParsedValue(parsed, ["STATUS", "MODE", "WORKSTATUS", "INDQUICKSTART", "MAGQUICKSTART", "FRYQUICKSTART"], ""),
    recipeName,
    stepNo,
    totalSteps,
    timeLeft: formatLiveLogSeconds(timeLeft),
    induction: formatLiveLogPower(induction),
    inductionRun: formatLiveLogSeconds(inductionRun),
    inductionState: inductionState || "",
    microwave: formatLiveLogPower(microwave),
    microwaveRun: formatLiveLogSeconds(microwaveRun),
    microwaveState: microwaveState || "",
    stirrer: stirrer || "-",
    pump: pump || "-",
    temperature: formatLiveLogTemperature(temperature),
    currentVoltage: currentVoltage || "-",
    error: error || "",
    message: rawMessage || detail.message || "",
    sensor: isSensorPacket
      ? {
          macId: pickParsedValue(parsed, ["MAC_ID"], ""),
          onCount: pickParsedValue(parsed, ["ON_COUNT"], ""),
          igbtTemp: formatLiveLogTemperature(pickParsedValue(parsed, ["IGBT_TEMP"], "")),
          glassTemp: formatLiveLogTemperature(pickParsedValue(parsed, ["GLASS_TEMP"], "")),
          ambientTemp: formatLiveLogTemperature(pickParsedValue(parsed, ["AMBIENT_TEMP"], "")),
          coilTemp: formatLiveLogTemperature(pickParsedValue(parsed, ["COIL_TEMP"], "")),
          panTemp: formatLiveLogTemperature(pickParsedValue(parsed, ["PAN_TEMP"], "")),
          pcbTemp: formatLiveLogTemperature(pickParsedValue(parsed, ["PCB_TEMP"], "")),
          magCurrent: pickParsedValue(parsed, ["MAG_CURRENT"], ""),
          indCurrent: pickParsedValue(parsed, ["IND_CURRENT"], ""),
          indVoltage: pickParsedValue(parsed, ["IND_VOLTAGE"], ""),
          magOn: pickParsedValue(parsed, ["MAG_ON"], ""),
          indOn: pickParsedValue(parsed, ["IND_ON"], "")
        }
      : null
  };
}

function normalizeLiveLogEntry(device, entry = {}) {
  const rebuilt = buildLiveLogEntry(device, {
    direction: entry.direction,
    channel: entry.channel,
    message: entry.message,
    parsed: entry.parsed,
    at: entry.at
  });
  return {
    ...entry,
    ...rebuilt,
    id: entry.id || rebuilt.id
  };
}

function appendLiveLogEntry(device, detail = {}) {
  const liveLog = ensureDeviceLiveLogState(device);
  if (!liveLog.active && !liveLog.starting) return;
  const message = String(detail.message || "").trim();
  if (!message) return;
  liveLog.entries.push(buildLiveLogEntry(device, detail));
  if (liveLog.entries.length > MAX_LIVE_LOG_ENTRIES) {
    liveLog.entries = liveLog.entries.slice(-MAX_LIVE_LOG_ENTRIES);
  }
  liveLog.updatedAt = detail.at || nowIso();
}

function startLiveLogState(device, at = nowIso()) {
  const liveLog = ensureDeviceLiveLogState(device);
  liveLog.active = true;
  liveLog.starting = true;
  liveLog.error = "";
  liveLog.status = "Starting live log stream...";
  liveLog.openedAt = at;
  liveLog.updatedAt = at;
  device.lastUpdatedAt = at;
  device.lastMessage = "Live logs requested";
}

function markLiveLogReady(device, at = nowIso()) {
  const liveLog = ensureDeviceLiveLogState(device);
  liveLog.active = true;
  liveLog.starting = false;
  liveLog.error = "";
  liveLog.status = "Live log stream is on.";
  liveLog.updatedAt = at;
  device.lastUpdatedAt = at;
}

function stopLiveLogState(device, at = nowIso()) {
  const liveLog = ensureDeviceLiveLogState(device);
  liveLog.active = false;
  liveLog.starting = false;
  liveLog.error = "";
  liveLog.status = "Live log stream is off.";
  liveLog.updatedAt = at;
  device.lastUpdatedAt = at;
}

function failLiveLogState(device, error, at = nowIso(), options = {}) {
  const liveLog = ensureDeviceLiveLogState(device);
  liveLog.active = false;
  liveLog.starting = false;
  liveLog.error = error;
  liveLog.status = error;
  if (options.clearEntries) {
    liveLog.entries = [];
  }
  liveLog.updatedAt = at;
  device.lastUpdatedAt = at;
}

function beginDeviceLogListing(device, at = nowIso()) {
  const logFetch = ensureDeviceLogState(device);
  device.logFiles = [];
  logFetch.listing = true;
  logFetch.error = "";
  logFetch.status = "Reading firmware log list...";
  logFetch.updatedAt = at;
  device.lastUpdatedAt = at;
  device.lastMessage = "Reading firmware log list...";
}

function beginDeviceLogRead(device, rawName, at = nowIso()) {
  const logFetch = ensureDeviceLogState(device);
  logFetch.reading = true;
  logFetch.started = false;
  logFetch.complete = false;
  logFetch.activeFile = rawName;
  logFetch.activeDisplayName = formatLogFileDisplay(rawName);
  logFetch.content = "";
  logFetch.error = "";
  logFetch.status = `Requesting ${logFetch.activeDisplayName}...`;
  logFetch.updatedAt = at;
  device.lastUpdatedAt = at;
  device.lastMessage = logFetch.status;
}

function handleDeviceLogControlMessage(device, message, at = nowIso()) {
  const logFetch = ensureDeviceLogState(device);
  const text = String(message || "").trim();
  if (text === "LOGSTATUS=BUSY") {
    logFetch.listing = false;
    logFetch.reading = false;
    logFetch.error = "Device is busy. Logs can be downloaded after current cooking ends.";
    logFetch.status = logFetch.error;
  } else if (text === "LOGSTATUS=IDLE") {
    logFetch.error = "";
    logFetch.status = "Device is idle. Stored logs can be downloaded.";
  } else if (text.startsWith("LOGFILE=")) {
    const rawName = text.replace(/^LOGFILE=/, "").trim();
    if (!rawName) return;
    const exists = device.logFiles.some((item) => item.rawName === rawName);
    if (!exists) {
      device.logFiles.push({
        id: safeRandomId("log"),
        rawName,
        displayName: formatLogFileDisplay(rawName)
      });
    }
    logFetch.listing = true;
    logFetch.status = `${device.logFiles.length} log file${device.logFiles.length === 1 ? "" : "s"} found`;
  } else if (text === "LISTLOGS=COMPLETE") {
    logFetch.listing = false;
    logFetch.status = device.logFiles.length ? `${device.logFiles.length} logs ready` : "No firmware logs found on device";
  } else if (text === "LISTLOGS=ERROR") {
    logFetch.listing = false;
    logFetch.error = "Device could not list firmware logs.";
    logFetch.status = logFetch.error;
  } else if (text.startsWith("READLOG=START")) {
    logFetch.reading = true;
    logFetch.started = true;
    logFetch.complete = false;
    logFetch.content = "";
    logFetch.error = "";
    logFetch.status = text;
  } else if (text.startsWith("READLOG=END") || text.startsWith("READLOG=DONE")) {
    logFetch.reading = false;
    logFetch.complete = true;
    logFetch.status = text;
  } else if (text.startsWith("READLOG=CANCELLED") || text.startsWith("READLOG=ABORTED")) {
    logFetch.reading = false;
    logFetch.complete = true;
    logFetch.error = text;
    logFetch.status = text;
  } else if (text.startsWith("READLOG=BUSY") || text.startsWith("READLOG=DEVICE_BUSY") || text.startsWith("READLOG=ERROR")) {
    logFetch.reading = false;
    logFetch.complete = false;
    logFetch.error = text;
    logFetch.status = text;
  }
  logFetch.updatedAt = at;
  device.lastUpdatedAt = at;
  device.lastMessage = logFetch.status || text;
}

function appendDeviceLogChunk(device, message, at = nowIso()) {
  const logFetch = ensureDeviceLogState(device);
  if (!logFetch.reading || !logFetch.started || logFetch.complete) return false;
  const text = String(message || "");
  if (!text || text.startsWith("READLOG=") || text.startsWith("LOGFILE=") || text.startsWith("LISTLOGS=")) return false;
  logFetch.content = `${logFetch.content || ""}${text}`;
  if (logFetch.content.length > MAX_LIVE_DEVICE_LOG_CHARS) {
    logFetch.content = logFetch.content.slice(-MAX_LIVE_DEVICE_LOG_CHARS);
  }
  logFetch.status = `Receiving ${logFetch.activeDisplayName || "device log"}...`;
  logFetch.updatedAt = at;
  device.lastUpdatedAt = at;
  return true;
}

function parseLogFileDate(rawName) {
  const text = String(rawName || "");
  const match =
    text.match(/(20\d{2})[-_]?([01]\d)[-_]?([0-3]\d)/) ||
    text.match(/([0-3]\d)[-_]([01]\d)[-_](20\d{2})/);
  if (!match) return null;
  const year = match[1].length === 4 ? match[1] : match[3];
  const month = match[1].length === 4 ? match[2] : match[2];
  const day = match[1].length === 4 ? match[3] : match[1];
  const date = new Date(`${year}-${month}-${day}T00:00:00`);
  return Number.isNaN(date.getTime()) ? null : date;
}

function groupLogFiles(logFiles = []) {
  const now = new Date();
  const startToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const startWeek = startToday - 6 * 24 * 60 * 60 * 1000;
  const startMonth = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
  const groups = {
    Today: [],
    "This week": [],
    "This month": [],
    Older: []
  };
  logFiles.forEach((file) => {
    const date = parseLogFileDate(file.rawName || file.displayName);
    const time = date?.getTime() || 0;
    if (time >= startToday) groups.Today.push(file);
    else if (time >= startWeek) groups["This week"].push(file);
    else if (time >= startMonth) groups["This month"].push(file);
    else groups.Older.push(file);
  });
  return groups;
}

function parseStoredLogRows(content = "") {
  return String(content || "")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .slice(0, 500)
    .map((line) => {
      const parsed = parseLooseKeyValues(line);
      return {
        time: pickParsedValue(parsed, ["TIME", "time", "timestamp", "at"], ""),
        recipe: pickParsedValue(parsed, ["RECIPE", "recipe", "RECIPENAME"], ""),
        step: pickParsedValue(parsed, ["STEP", "step", "INSTR"], ""),
        induction: formatLiveLogPower(pickParsedValue(parsed, ["INDPOWER", "IH", "IND", "induction"], "")),
        microwave: formatLiveLogPower(pickParsedValue(parsed, ["MAGPOWER", "MW", "MAG", "microwave"], "")),
        stirrer: pickParsedValue(parsed, ["STIRRER", "STR", "stirrer"], "-"),
        pump: pickParsedValue(parsed, ["PUMP", "WATER", "pump", "water"], "-"),
        temperature: pickParsedValue(parsed, ["TEMP", "temperature", "PAN_TEMP"], "-"),
        currentVoltage: [
          pickParsedValue(parsed, ["CURRENT", "I", "current"], ""),
          pickParsedValue(parsed, ["VOLTAGE", "V", "voltage"], "")
        ].filter(Boolean).join(" / ") || "-",
        error: pickParsedValue(parsed, ["ERROR", "ERR", "FAULT", "error"], ""),
        message: line
      };
    });
}

function csvEscape(value) {
  const text = String(value ?? "");
  return /[",\n\r]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function buildStoredLogCsv(content = "") {
  const rows = parseStoredLogRows(content);
  const headers = ["Time", "Recipe", "Step", "Induction", "Microwave", "Stirrer", "Pump/Water", "Temperature", "Current/Voltage", "Error", "Message"];
  return [
    headers.join(","),
    ...rows.map((row) =>
      [
        row.time,
        row.recipe,
        row.step,
        row.induction,
        row.microwave,
        row.stirrer,
        row.pump,
        row.temperature,
        row.currentVoltage,
        row.error,
        row.message
      ].map(csvEscape).join(",")
    )
  ].join("\n");
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
    summary: recipeNames.length > 0 ? `Checking device recipes (${recipeNames.length})` : "Checking device recipes"
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
          ? `Required recipe already exists on this device (${skippedNames.length})`
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
          ? `Required recipe already exists on this device (${skippedNames.length})`
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

function getNotificationTypeLabel(type) {
  const labels = {
    order: "Order",
    device: "Device",
    cooking: "Cooking",
    error: "Error",
    logs: "Logs/Sync"
  };
  return labels[type] || "Notification";
}

function getNotificationIconName(type) {
  const icons = {
    order: "order",
    device: "device",
    cooking: "cooking",
    error: "error",
    logs: "logs"
  };
  return icons[type] || "bell";
}

function pushDraftNotification(draft, payload = {}) {
  if (!draft.ui) return;
  const type = ["order", "device", "cooking", "error", "logs"].includes(payload.type) ? payload.type : "device";
  const timestamp = payload.timestamp || nowIso();
  const notification = {
    id: payload.id || safeRandomId("note"),
    type,
    title: String(payload.title || getNotificationTypeLabel(type)).trim(),
    message: String(payload.message || "").trim(),
    deviceSlot: payload.deviceSlot ? Number(payload.deviceSlot) : null,
    recipeName: String(payload.recipeName || "").trim(),
    orderId: String(payload.orderId || "").trim(),
    timestamp,
    read: Boolean(payload.read),
    action: payload.action || null
  };
  const current = Array.isArray(draft.ui.notifications) ? draft.ui.notifications : [];
  const dedupeKey = [
    notification.type,
    notification.title,
    notification.deviceSlot || "",
    notification.recipeName || "",
    notification.orderId || ""
  ].join("|").toLowerCase();
  const withoutDuplicate = current.filter((item) => {
    const itemKey = [item.type, item.title, item.deviceSlot || "", item.recipeName || "", item.orderId || ""]
      .join("|")
      .toLowerCase();
    const closeInTime = Math.abs(new Date(notification.timestamp).getTime() - new Date(item.timestamp || 0).getTime()) < 60000;
    return !(itemKey === dedupeKey && closeInTime);
  });
  draft.ui.notifications = [notification, ...withoutDuplicate].slice(0, MAX_NOTIFICATIONS);
}

function addNotification(payload = {}) {
  mutate((draft) => {
    pushDraftNotification(draft, payload);
  });
}

function getUnreadNotificationCount(snapshot) {
  return (snapshot.ui.notifications || []).filter((item) => !item.read).length;
}

function renderNotificationMeta(notification) {
  const parts = [];
  if (notification.deviceSlot) parts.push(`D${notification.deviceSlot}`);
  if (notification.orderId) parts.push(notification.orderId);
  if (notification.recipeName) parts.push(notification.recipeName);
  return parts.join(" · ");
}

function renderNotificationAction(notification) {
  const action = notification.action;
  if (!action?.type || !action.label) return "";
  return `
    <button
      class="secondary-button micro notification-action-button"
      type="button"
      data-action="notification-action"
      data-notification-id="${escapeHtml(notification.id)}"
    >
      ${escapeHtml(action.label)}
    </button>
  `;
}

function renderNotificationDrawer(snapshot) {
  if (!snapshot.ui.notificationDrawerOpen) return "";
  const notifications = Array.isArray(snapshot.ui.notifications) ? snapshot.ui.notifications : [];
  return `
    <aside class="notification-drawer-backdrop" role="dialog" aria-label="Notifications">
      <section class="notification-drawer">
        <header class="notification-drawer-head">
          <div>
            <div class="eyebrow">On2Cook alerts</div>
            <h3>Notifications</h3>
            <p class="subtle">${notifications.length} recent event${notifications.length === 1 ? "" : "s"}</p>
          </div>
          <div class="notification-head-actions">
            <button class="secondary-button micro" type="button" data-action="mark-notifications-read">Mark read</button>
            <button class="icon-button" type="button" data-action="close-notification-drawer" aria-label="Close notifications">x</button>
          </div>
        </header>
        <div class="notification-type-legend">
          ${["order", "device", "cooking", "error", "logs"].map((type) => `<span class="${type}">${renderUiIcon(getNotificationIconName(type))}${escapeHtml(getNotificationTypeLabel(type))}</span>`).join("")}
        </div>
        <div class="notification-list">
          ${
            notifications.length
              ? notifications
                  .map((notification) => {
                    const meta = renderNotificationMeta(notification);
                    return `
                      <article class="notification-row ${escapeHtml(notification.type)} ${notification.read ? "read" : "unread"}">
                        <span class="notification-icon">${renderUiIcon(getNotificationIconName(notification.type))}</span>
                        <div class="notification-copy">
                          <div class="row space">
                            <strong>${escapeHtml(notification.title)}</strong>
                            <time>${escapeHtml(formatAgo(notification.timestamp))}</time>
                          </div>
                          ${meta ? `<small>${escapeHtml(meta)}</small>` : ""}
                          ${notification.message ? `<p>${escapeHtml(notification.message)}</p>` : ""}
                          ${renderNotificationAction(notification)}
                        </div>
                      </article>
                    `;
                  })
                  .join("")
              : `<div class="empty-card">No notifications yet. Device, order, cooking, error, and logs/sync events will appear here.</div>`
          }
        </div>
      </section>
    </aside>
  `;
}

async function runNotificationAction(notificationId) {
  const notification = (state().ui.notifications || []).find((item) => item.id === notificationId);
  if (!notification?.action) return;
  mutate((draft) => {
    const item = (draft.ui.notifications || []).find((entry) => entry.id === notificationId);
    if (item) item.read = true;
    draft.ui.notificationDrawerOpen = false;
  });
  const action = notification.action;
  if (action.type === "order" && action.orderId) {
    openModal("order-details", { orderId: action.orderId });
    return;
  }
  if (action.type === "device" && action.slot) {
    openModal("device-sheet", { slot: Number(action.slot) });
    return;
  }
  if (action.type === "device-status" && action.slot) {
    openModal("device-status", { slot: Number(action.slot) });
    return;
  }
  if (action.type === "device-firmware" && action.slot) {
    openModal("device-firmware", { slot: Number(action.slot) });
    return;
  }
  if (action.type === "live-logs" && action.slot) {
    await openLiveLogs(Number(action.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action.type === "stored-logs" && action.slot) {
    await listDeviceLogs(Number(action.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action.type === "device-recipes" && action.slot) {
    openModal("device-recipes", { slot: Number(action.slot), query: "", filter: "all", selectedNames: [] });
    return;
  }
  if (action.type === "queue") {
    mutate((draft) => {
      draft.ui.activeTab = "queue";
      draft.ui.orderMode = "current";
    });
  }
}

function showOrderNotice(order) {
  if (!order) return;
  mutate((draft) => {
    draft.ui.orderNotice = {
      id: order.id,
      orderId: order.orderId || "",
      itemName: order.itemName || "New order",
      createdAt: nowIso()
    };
    pushDraftNotification(draft, {
      type: "order",
      title: "New order received",
      orderId: order.orderId || "",
      recipeName: order.itemName || "",
      message: "New order is waiting in Pending Orders.",
      action: { type: "order", label: "Open order", orderId: order.id }
    });
  });
}

function stopLiveLogForModal(modal = state().ui.activeModal) {
  if (!modal || modal.type !== "live-logs") return;
  const slot = Number(modal.payload?.slot);
  if (!slot) return;
  const deviceSnapshot = state().devices.find((item) => item.slot === slot);
  if (deviceSnapshot?.connection === "connected" && deviceSnapshot?.liveLog?.active) {
    ble.setLiveLog(slot, false).catch((error) => {
      console.warn("[On2Cook] Unable to stop live log stream.", error);
    });
  }
  mutate((draft) => {
    const device = draft.devices.find((item) => item.slot === slot);
    if (!device) return draft;
    stopLiveLogState(device);
  });
}

function openModal(type, payload = {}) {
  const activeModal = state().ui.activeModal;
  if (activeModal?.type === "live-logs" && type !== "live-logs") {
    stopLiveLogForModal(activeModal);
  }
  mutate((draft) => {
    draft.ui.activeModal = { type, payload };
  });
}

function closeModal() {
  stopLiveLogForModal();
  stopProLiveTimer();
  setNativeOrientation("portrait");
  proStudioShellOrientation = "portrait";
  proStudioRoutePath = "";
  mutate((draft) => {
    draft.ui.activeModal = null;
  });
}

function setNativeOrientation(mode) {
  const safeMode = mode === "landscape" ? "landscape" : "portrait";
  try {
    window.On2CookNativeBle?.setOrientation?.(safeMode);
  } catch (error) {
    console.warn("[On2Cook] Native orientation bridge unavailable.", error);
  }
}

function updateProStudioShellOrientation(mode, routePath = "") {
  proStudioShellOrientation = mode === "landscape" ? "landscape" : "portrait";
  if (routePath) proStudioRoutePath = routePath;
  document.querySelectorAll(".figma-pro-modal").forEach((element) => {
    element.classList.toggle("landscape", proStudioShellOrientation === "landscape");
    element.classList.toggle("portrait", proStudioShellOrientation !== "landscape");
  });
  setNativeOrientation(proStudioShellOrientation);
}

function handleProStudioBack() {
  const frame = document.querySelector(".figma-pro-frame");
  const frameWindow = frame?.contentWindow;
  if (!frameWindow) {
    closeModal();
    return;
  }
  const navigateFrame = (targetHash, targetOrientation = "portrait") => {
    proStudioRoutePath = targetHash;
    try {
      frameWindow.location.hash = targetHash;
    } catch (error) {
      const src = frame.getAttribute("src") || "./pro-studio/index.html";
      frame.setAttribute("src", `${src.split("#")[0]}${targetHash}`);
    }
    updateProStudioShellOrientation(targetOrientation, targetHash);
  };
  try {
    const hash = String(frameWindow.location.hash || proStudioRoutePath || "");
    if (hash.includes("/pro-editor/live") || hash.includes("/pro-editor/completed")) {
      navigateFrame("#/pro-editor", "landscape");
      return;
    }
    if (hash.includes("/pro-editor")) {
      navigateFrame("#/preset-setup", "portrait");
      return;
    }
    if (hash.includes("/preset-setup")) {
      navigateFrame("#/preset-library", "portrait");
      return;
    }
    closeModal();
  } catch (error) {
    console.warn("[On2Cook] Unable to navigate Pro Studio iframe.", error);
    closeModal();
  }
}

function returnToQueueContext() {
  stopProLiveTimer();
  mutate((draft) => {
    draft.ui.activeModal = null;
    draft.ui.activeTab = "queue";
  });
  queueIdleWork();
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
      lastError: userFacingCloudError(error, "Cloud status is unavailable right now.")
    });
  }
}

function userRecordFromCloudProfile(profile, sessionUser, fallbackFacilityId = "") {
  const role = profile?.role || "operator";
  const adminLike = role === "main_admin" || role === "admin";
  const managerLike = adminLike || role === "kitchen_manager" || role === "owner";
  return {
    id: `cloud-user-${sessionUser.id}`,
    cloudUserId: sessionUser.id,
    cloudProfileId: profile?.id || "",
    facilityId: profile?.facility_id || fallbackFacilityId,
    email: profile?.email || sessionUser.email || "",
    mobilePhone: profile?.mobile_phone || "",
    whatsappPhone: profile?.whatsapp_phone || "",
    displayName: profile?.full_name || sessionUser.name || sessionUser.email || "Cloud User",
    role,
    status: profile?.status || "active",
    managerMode: Boolean(profile?.manager_mode),
    canAddRecipes: Boolean(profile?.can_add_recipes ?? adminLike),
    canEditRecipes: Boolean(profile?.can_edit_recipes ?? managerLike),
    canManageRecipeAccess: Boolean(profile?.can_manage_recipe_access ?? managerLike)
  };
}

async function syncCloudSessionToLocalUser() {
  const sessionUser = cloudRuntime.session;
  if (!sessionUser?.id) return;
  try {
    const profile = await profileService.getMine(sessionUser);
    if (!profile) return;
    setCloudRuntime({ profile });
    mutate((draft) => {
      const localUser = userRecordFromCloudProfile(profile, sessionUser, draft.currentFacilityId || draft.facilities?.[0]?.id || "");
      const existingIndex = draft.users.findIndex(
        (user) =>
          user.cloudUserId === sessionUser.id ||
          String(user.email || "").toLowerCase() === String(localUser.email || "").toLowerCase()
      );
      if (existingIndex >= 0) {
        draft.users[existingIndex] = {
          ...draft.users[existingIndex],
          ...localUser,
          id: draft.users[existingIndex].id
        };
        draft.currentUserId = draft.users[existingIndex].id;
      } else {
        draft.users.push(localUser);
        draft.currentUserId = localUser.id;
      }
      const canUseGlobal = localUser.canAddRecipes || localUser.role === "main_admin" || localUser.role === "admin";
      if (!canUseGlobal && draft.ui.activeTab === "global") {
        draft.ui.activeTab = "recipes";
      }
      draft.ui.demoAuthBypass = false;
    });
  } catch (error) {
    setCloudRuntime({ lastError: userFacingCloudError(error, "Unable to load the cloud profile right now.") });
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
    id: safeRandomId("id"),
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
    if (device.allowedRecipeIdsConfigured === true) return;
    const existingIds = Array.isArray(device.allowedRecipeIds) ? device.allowedRecipeIds : [];
    device.allowedRecipeIds = Array.from(new Set([...existingIds, ...selectedRecipeIds]));
  });
}

function clearStartupRecipeUploadState(draft) {
  if (draft.ui?.activeModal && !sanitizeModalForSession(draft.ui.activeModal)) {
    draft.ui.activeModal = null;
  }
  draft.devices.forEach((device) => {
    device.baselineRecipeSyncPending = false;
    device.startupGuardUntil = "";
    if (device.uploadState?.active || device.uploadState?.inventoryChecking) {
      device.uploadState = emptyUploadState();
    }
  });
}

function toggleRecipePermission(slot, recipeId) {
  mutate((draft) => {
    const device = draft.devices.find((item) => item.slot === Number(slot));
    if (!device) return draft;
    seedAllowedRecipeIdsIfNeeded(device, draft);
    device.allowedRecipeIdsConfigured = true;
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
    let refreshing = false;
    navigator.serviceWorker.addEventListener("controllerchange", () => {
      if (refreshing) return;
      refreshing = true;
      saveUiSessionState();
      console.info("[On2Cook] App update activated. Staying on the current screen until the user refreshes.");
    });
    const registration = await navigator.serviceWorker.register(`./service-worker.js?v=${APP_ASSET_VERSION}`);
    if (registration.waiting) {
      registration.waiting.postMessage({ type: "SKIP_WAITING" });
    }
    registration.addEventListener("updatefound", () => {
      const worker = registration.installing;
      if (!worker) return;
      worker.addEventListener("statechange", () => {
        if (worker.state === "installed" && navigator.serviceWorker.controller) {
          worker.postMessage({ type: "SKIP_WAITING" });
        }
      });
    });
    registration.update().catch(() => {});
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
    if (kotBridgeRuntime.active) return;
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
    showOrderNotice(releasedOrder);
    if (state().settings.pendingAssignmentMode === "auto_route") {
      queueIdleWork();
    } else {
      showToast(`${releasedOrder.itemName} added to the pending queue`, "info");
    }
  }, 60000);
}

function mergeKotBridgeOrderState(freshOrder, existingOrder) {
  if (!existingOrder) return freshOrder;
  return {
    ...freshOrder,
    status: existingOrder.status || freshOrder.status,
    assignedSlot: existingOrder.assignedSlot ?? freshOrder.assignedSlot,
    assignedMode: existingOrder.assignedMode || freshOrder.assignedMode,
    activeRecipeId: existingOrder.activeRecipeId || freshOrder.activeRecipeId,
    currentRunRecipeName: existingOrder.currentRunRecipeName || freshOrder.currentRunRecipeName,
    currentRunFirmwareName: existingOrder.currentRunFirmwareName || freshOrder.currentRunFirmwareName,
    targetSlot: existingOrder.targetSlot ?? freshOrder.targetSlot,
    historyNote: existingOrder.historyNote || freshOrder.historyNote
  };
}

function applyKotBridgeSnapshot(payload) {
  if (!payload?.active) {
    kotBridgeRuntime.active = false;
    return;
  }
  const entries = Array.isArray(payload.orders) ? payload.orders : [];
  const runId = String(payload.run_id || "");
  const priorIds = new Set(state().orders.current.map((order) => order.id));
  const newOrders = [];

  kotBridgeRuntime.active = true;
  kotBridgeRuntime.runId = runId;
  kotBridgeRuntime.revision = Number(payload.revision || 0);
  kotBridgeRuntime.lastError = "";

  mutate((draft) => {
    const previousSameRunIds = new Set(
      draft.orders.previous
        .filter((order) => !runId || order.serverBridgeRunId === runId)
        .map((order) => order.id)
    );
    const existingById = new Map(draft.orders.current.map((order) => [order.id, order]));
    const nextOrders = [];
    entries.forEach((entry, index) => {
      const freshOrder = createOrderFromKotBridgeEntry(draft, entry, index, runId);
      if (previousSameRunIds.has(freshOrder.id)) return;
      const existingOrder = existingById.get(freshOrder.id);
      const mergedOrder = mergeKotBridgeOrderState(freshOrder, existingOrder);
      nextOrders.push(mergedOrder);
      if (!priorIds.has(mergedOrder.id)) {
        newOrders.push(mergedOrder);
      }
    });
    draft.orders.current = nextOrders;
    draft.orders.incoming = [];
    if (draft.ui.activeTab === "orders" && draft.ui.orderMode !== "previous") {
      draft.ui.orderMode = "current";
    }
  });

  kotBridgeRuntime.orderIds = new Set(entries.map((entry, index) => makeKotBridgeOrderId(entry, entry?.payload || entry, index)));
  if (newOrders.length > 0) {
    showOrderNotice(newOrders[newOrders.length - 1]);
    if (state().settings.pendingAssignmentMode === "auto_route") {
      queueIdleWork();
    }
  }
}

async function fetchKotBridgeOrders() {
  try {
    const response = await fetch(`${KOT_BRIDGE_URL}?t=${Date.now()}`, {
      cache: "no-store",
      credentials: "same-origin"
    });
    if (!response.ok) return;
    const payload = await response.json();
    applyKotBridgeSnapshot(payload);
  } catch (error) {
    kotBridgeRuntime.lastError = error.message || "KOT bridge unavailable";
  }
}

function ensureKotBridgePolling() {
  if (kotBridgeTimer) clearInterval(kotBridgeTimer);
  fetchKotBridgeOrders();
  kotBridgeTimer = window.setInterval(fetchKotBridgeOrders, KOT_BRIDGE_POLL_MS);
}

function getDeviceErrorNotification(message, parsed = {}) {
  const upper = String(message || "").toUpperCase();
  const errorText = String(parsed.ERROR || parsed.ERR || parsed.FAULT || "").toUpperCase();
  const combined = `${upper} ${errorText}`;
  if (/NO[_\s-]?PAN/.test(combined)) return "No pan";
  if (/LID[_\s-]?OPEN/.test(combined)) return "Lid open";
  if (/VOLT(?:AGE)?[_\s-]?HIGH/.test(combined)) return "Voltage high";
  if (/VOLT(?:AGE)?[_\s-]?LOW/.test(combined)) return "Voltage low";
  if (/MAGNETRON|MAG[_\s-]?ERROR/.test(combined)) return "Magnetron error";
  if (/INDUCTION|IND[_\s-]?ERROR/.test(combined)) return "Induction error";
  return "";
}

function applyTelemetry(device, parsed, message, at, draft = null) {
  const previousStepNo = Number(device.telemetry.stepNo) || 0;
  const previousWorkStatus = String(device.telemetry.workStatus || "").toLowerCase();
  const previousPaused = Boolean(device.telemetry.paused);
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
  if (device.currentJobId && device.telemetry.workStatus === "cooking") {
    markLastRunWaitClosed(device, at);
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
  if (!draft) return;
  const activeOrder = draft.orders.current.find((item) => item.id === device.currentJobId) || null;
  const activeRecipeName = device.telemetry.currentRecipe || device.activeRun?.displayName || activeOrder?.itemName || "";
  const currentStepNo = Number(device.telemetry.stepNo) || 0;
  if (currentStepNo > 0 && currentStepNo !== previousStepNo) {
    pushDraftNotification(draft, {
      type: "cooking",
      title: `Step changed to ${currentStepNo}`,
      deviceSlot: device.slot,
      recipeName: activeRecipeName,
      orderId: activeOrder?.orderId || "",
      message: `Device ${device.slot} is now on recipe step ${currentStepNo}.`,
      timestamp: at,
      action: { type: "device", label: "Open device", slot: device.slot }
    });
  }
  if (!previousPaused && device.telemetry.paused) {
    pushDraftNotification(draft, {
      type: "cooking",
      title: "Recipe paused",
      deviceSlot: device.slot,
      recipeName: activeRecipeName,
      orderId: activeOrder?.orderId || "",
      message: "The connected cooker reported a paused state.",
      timestamp: at,
      action: { type: "device", label: "Open device", slot: device.slot }
    });
  }
  if (previousWorkStatus !== device.telemetry.workStatus) {
    if (device.telemetry.workStatus === "idle") {
      pushDraftNotification(draft, {
        type: "device",
        title: "Device idle",
        deviceSlot: device.slot,
        message: `Device ${device.slot} is ready for the next task.`,
        timestamp: at,
        action: { type: "device", label: "Open device", slot: device.slot }
      });
    } else if (["cooking", "starting", "ingredient", "manual"].includes(device.telemetry.workStatus)) {
      pushDraftNotification(draft, {
        type: "device",
        title: "Device busy",
        deviceSlot: device.slot,
        recipeName: activeRecipeName,
        message: `Device ${device.slot} is ${device.telemetry.workStatus}.`,
        timestamp: at,
        action: { type: "device", label: "Open device", slot: device.slot }
      });
    }
  }
  const lowerMode = String(mode || "").toLowerCase();
  const upperMessage = String(message || "").toUpperCase();
  const actionRequired =
    lowerMode.includes("ingredient")
      ? "Add ingredient"
      : upperMessage.includes("OPENLID") || upperMessage.includes("OPEN LID")
        ? "Open lid"
        : upperMessage.includes("CLOSELID") || upperMessage.includes("CLOSE LID")
          ? "Close lid"
          : upperMessage.includes("PLACE PAN") || upperMessage.includes("PAN REQUIRED")
            ? "Place pan"
            : "";
  if (actionRequired) {
    pushDraftNotification(draft, {
      type: "cooking",
      title: `User action required: ${actionRequired}`,
      deviceSlot: device.slot,
      recipeName: activeRecipeName,
      orderId: activeOrder?.orderId || "",
      message: `${actionRequired} is required before the recipe can continue.`,
      timestamp: at,
      action: { type: "device", label: "Open device", slot: device.slot }
    });
  }
  const errorTitle = getDeviceErrorNotification(message, parsed);
  if (errorTitle) {
    pushDraftNotification(draft, {
      type: "error",
      title: errorTitle,
      deviceSlot: device.slot,
      recipeName: activeRecipeName,
      orderId: activeOrder?.orderId || "",
      message: message || errorTitle,
      timestamp: at,
      action: { type: "device", label: "Open device", slot: device.slot }
    });
  }
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
    pushDraftNotification(draft, {
      type: "cooking",
      title: "Recipe started",
      deviceSlot: device.slot,
      recipeName: order.itemName || recipeName,
      orderId: order.orderId || "",
      message: `${order.itemName || recipeName} started on Device ${device.slot}.`,
      action: { type: "device", label: "Open device", slot: device.slot }
    });
  });
}

function markRecipeComplete(slot, message, at = nowIso()) {
  let shouldQueue = false;
  let completedNotification = null;
  let nextQueuedNotification = null;
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
      actualDurationSeconds: elapsedSecondsBetween(device.activeRun?.startedAt || nowIso(), at),
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
    completedNotification = {
      type: "cooking",
      title: "Recipe completed",
      deviceSlot: device.slot,
      recipeName: displayName,
      orderId: order?.orderId || "",
      message: `${displayName} completed on Device ${device.slot}.`,
      timestamp: at,
      action: { type: "device", label: "Open device", slot: device.slot }
    };
    if (fallbackIndex >= 0) {
      const [completedOrder] = draft.orders.current.splice(fallbackIndex, 1);
      draft.orders.previous.unshift(moveOrderToHistory(completedOrder, "completed", "Completed on device"));
      device.historyOrderIds.unshift(completedOrder.id);
      clearRecipeRetryTracking(device.slot, completedOrder.id);
      pushDraftNotification(draft, {
        type: "order",
        title: "Order item completed",
        deviceSlot: device.slot,
        recipeName: completedOrder.itemName,
        orderId: completedOrder.orderId || "",
        message: "Order item moved to previous orders.",
        timestamp: at,
        action: { type: "order", label: "Open order", orderId: completedOrder.id }
      });
    }
    pushDraftNotification(draft, completedNotification);
    const nextOrder = draft.orders.current.find((item) => item.id === device.queueOrderIds[0]) || null;
    if (nextOrder) {
      nextQueuedNotification = {
        type: "cooking",
        title: "Next queued recipe ready",
        deviceSlot: device.slot,
        recipeName: nextOrder.itemName,
        orderId: nextOrder.orderId || "",
        message: `${nextOrder.itemName} is next on Device ${device.slot}.`,
        timestamp: at,
        action: { type: "device", label: "Open device", slot: device.slot }
      };
      pushDraftNotification(draft, nextQueuedNotification);
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
      actualDurationSeconds: elapsedSecondsBetween(device.activeRun?.startedAt || nowIso(), at),
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
    pushDraftNotification(draft, {
      type: "cooking",
      title: "Recipe aborted",
      deviceSlot: device.slot,
      recipeName: displayName,
      orderId: order?.orderId || "",
      message: `${displayName} was aborted on Device ${device.slot}.`,
      timestamp: at,
      action: { type: "device", label: "Open device", slot: device.slot }
    });
    if (fallbackIndex >= 0) {
      const [abortedOrder] = draft.orders.current.splice(fallbackIndex, 1);
      draft.orders.previous.unshift(moveOrderToHistory(abortedOrder, "aborted", "Aborted on device"));
      device.historyOrderIds.unshift(abortedOrder.id);
      clearRecipeRetryTracking(device.slot, abortedOrder.id);
      pushDraftNotification(draft, {
        type: "order",
        title: "Order item failed/aborted",
        deviceSlot: device.slot,
        recipeName: abortedOrder.itemName,
        orderId: abortedOrder.orderId || "",
        message: "Order item moved to previous orders as aborted.",
        timestamp: at,
        action: { type: "order", label: "Open order", orderId: abortedOrder.id }
      });
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
    return getKnownDeviceRecipeNames(state(), device);
  }
  const knownBefore = getKnownDeviceRecipeNames(state(), device);
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    setInventoryCheckState(draftDevice, options.recipeNames || []);
  });
  let inventoryNames = [];
  let inventoryError = null;
  try {
    inventoryNames = await ble.readRecipesAvailable(Number(slot), {
      timeoutMs: options.timeoutMs || 4500
    });
  } catch (error) {
    inventoryError = error;
  }
  let resultNames = [];
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    const namesToMerge = inventoryNames.length > 0 ? inventoryNames : knownBefore;
    mergeRecipeNames(draftDevice, namesToMerge);
    resultNames = getKnownDeviceRecipeNames(draft, draftDevice);
    const sourceLabel =
      inventoryNames.length > 0
        ? "confirmed by device"
        : resultNames.length > 0
          ? "known from prior sync"
          : "firmware returned no names";
    draftDevice.uploadState = {
      ...draftDevice.uploadState,
      inventoryChecking: false,
      recipeNames: resultNames,
      totalRecipes: resultNames.length,
      summary:
        resultNames.length > 0
          ? `Inventory checked: ${resultNames.length} recipe${resultNames.length === 1 ? "" : "s"} on device (${sourceLabel})`
          : inventoryError
            ? `Inventory check returned no names: ${inventoryError.message}`
            : "Inventory checked: no recipe names reported"
    };
  });
  if (inventoryError && resultNames.length === 0) {
    throw inventoryError;
  }
  return resultNames;
}

async function ensureRecipesAvailableOnDevice(slot, recipes, options = {}) {
  const device = getDevice(slot);
  if (!device) return [];
  if (!Array.isArray(recipes) || recipes.length === 0) return [];
  ensureDeviceCommandAllowed(device, "Recipe sync");
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
  const device = getDevice(slot);
  ensureDeviceCommandAllowed(device, "Recipe upload retry");
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
    autoStartAfterIngredient: false,
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
    draftDevice.lastMessage = `recipe=${recipe.firmwareName} re-sent after upload, waiting for ingredient stage`;
    draftDevice.lastUpdatedAt = nowIso();
    appendFlowActivity(draftDevice, `Run retried for ${recipe.firmwareName}`, "success");
  });
}

function handleTransportEvents() {
  ble.addEventListener("device-connected", (event) => {
    const { slot, browserDeviceId, bluetoothName, macAddress } = event.detail;
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
      device.macAddress = macAddress || device.macAddress || "";
      device.connection = "connected";
      device.baselineRecipeSyncPending = false;
      device.uploadState = emptyUploadState();
      device.telemetry.workStatus = "idle";
      device.firmwareUpdate = {
        ...(device.firmwareUpdate || {}),
        status: device.firmwareUpdate?.status === "updating" ? "updating" : "checking",
        message: "Checking firmware version before cooking.",
        error: "",
        progress: device.firmwareUpdate?.progress || 0
      };
      appendActivity(device, `Connected to ${bluetoothName || browserDeviceId}. Recipe upload is disabled until a recipe is run.`, "success");
      pushDraftNotification(draft, {
        type: "device",
        title: "Device connected",
        deviceSlot: device.slot,
        message: `${device.displayName} connected to ${bluetoothName || browserDeviceId || "On2Cook cooker"}.`,
        action: { type: "device", label: "Open device", slot: device.slot }
      });
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
      if (device.firmwareUpdate?.status !== "updating") {
        device.firmwareUpdate = {
          ...(device.firmwareUpdate || {}),
          status: "unknown",
          message: "Connect the cooker to check firmware.",
          progress: 0
        };
      }
      failLiveLogState(device, "Device disconnected. Live log stream stopped.", nowIso(), { clearEntries: true });
      appendActivity(device, "Device disconnected. Active work returned to pending.", "warning");
      pushDraftNotification(draft, {
        type: "device",
        title: "Device disconnected",
        deviceSlot: device.slot,
        message: "Active work was returned to pending if needed.",
        action: { type: "device", label: "Open device", slot: device.slot }
      });
    });
  });

  ble.addEventListener("command-sent", (event) => {
    const { slot, channel, message, at } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      appendTransportActivity(device, "tx", channel, message, at);
      appendLiveLogEntry(device, { direction: "tx", channel, message, at });
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
      const capturedLogChunk = appendDeviceLogChunk(device, message, at);
      device.lastUpdatedAt = at;
      if (capturedLogChunk) {
        return draft;
      }
      appendLiveLogEntry(device, { direction: "rx", channel, message, parsed: event.detail.parsed, at });
      if (!device.uploadState?.active && !device.uploadState?.inventoryChecking) {
        device.lastMessage = message;
      }
      appendTransportActivity(device, "rx", channel, message, at);
    });
  });

  ble.addEventListener("log-message", (event) => {
    const { slot, message, at } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      handleDeviceLogControlMessage(device, message, at);
      appendTransportActivity(device, "rx", "log", message, at);
      if (message === "LISTLOGS=COMPLETE") {
        pushDraftNotification(draft, {
          type: "logs",
          title: "Log file ready",
          deviceSlot: device.slot,
          message: "Stored device log list is ready.",
          timestamp: at,
          action: { type: "stored-logs", label: "View logs", slot: device.slot }
        });
      } else if (message === "LISTLOGS=ERROR" || String(message || "").startsWith("READLOG=ERROR")) {
        pushDraftNotification(draft, {
          type: "logs",
          title: "Log download failed",
          deviceSlot: device.slot,
          message,
          timestamp: at,
          action: { type: "stored-logs", label: "Retry logs", slot: device.slot }
        });
      }
    });
  });

  ble.addEventListener("telemetry", (event) => {
    const { slot, parsed, message, at } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      applyTelemetry(device, parsed, message, at, draft);
    });
  });

  ble.addEventListener("firmware-version", (event) => {
    const { slot, firmwareVersion, at } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      device.telemetry.firmwareVersion = firmwareVersion;
      device.firmwareUpdate = {
        ...(device.firmwareUpdate || {}),
        currentVersion: firmwareVersion,
        message: `Firmware ${firmwareVersion} received.`
      };
      appendActivity(device, `Firmware ${firmwareVersion}`, "info", at);
      pushDraftNotification(draft, {
        type: "device",
        title: "Firmware version received",
        deviceSlot: device.slot,
        message: `Firmware ${firmwareVersion}`,
        timestamp: at,
        action: { type: "device", label: "Open device", slot: device.slot }
      });
    });
    evaluateFirmwareForDevice(slot, firmwareVersion).catch((error) => {
      console.warn("[On2Cook] Firmware evaluation failed.", error);
    });
  });

  ble.addEventListener("firmware-update-started", (event) => {
    const { slot, version, message, progress, at } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      device.firmwareUpdate = {
        ...(device.firmwareUpdate || {}),
        status: "updating",
        latestVersion: version || device.firmwareUpdate?.latestVersion || "",
        startedAt: at,
        completedAt: "",
        error: "",
        message: message || "Firmware update started.",
        progress: Number(progress) || 1
      };
      device.lastMessage = "Firmware is updating. Recipe/manual commands are blocked.";
      device.lastUpdatedAt = at;
      appendActivity(device, device.firmwareUpdate.message, "warning", at);
    });
  });

  ble.addEventListener("firmware-update-progress", (event) => {
    const { slot, version, message, progress, at } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      device.firmwareUpdate = {
        ...(device.firmwareUpdate || {}),
        status: "updating",
        latestVersion: version || device.firmwareUpdate?.latestVersion || "",
        message: message || device.firmwareUpdate?.message || "Firmware update in progress.",
        progress: Math.max(Number(device.firmwareUpdate?.progress) || 0, Number(progress) || 0),
        error: ""
      };
      device.lastMessage = device.firmwareUpdate.message;
      device.lastUpdatedAt = at;
    });
  });

  ble.addEventListener("firmware-update-complete", (event) => {
    const { slot, version, message, at } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      device.firmwareUpdate = {
        ...(device.firmwareUpdate || {}),
        status: "current",
        currentVersion: version || device.firmwareUpdate?.latestVersion || "",
        latestVersion: version || device.firmwareUpdate?.latestVersion || "",
        completedAt: at,
        error: "",
        message: message || `Firmware updated to ${version}.`,
        progress: 100
      };
      device.telemetry.firmwareVersion = version || device.telemetry.firmwareVersion;
      device.lastMessage = device.firmwareUpdate.message;
      device.lastUpdatedAt = at;
      appendActivity(device, device.firmwareUpdate.message, "success", at);
      pushDraftNotification(draft, {
        type: "device",
        title: "Firmware updated",
        deviceSlot: device.slot,
        message: device.firmwareUpdate.message,
        timestamp: at,
        action: { type: "device-firmware", label: "View firmware", slot: device.slot }
      });
    });
    showToast(`Device ${slot} firmware updated to ${version || "latest"}`, "success");
    if (!state().ui.activeModal) {
      openModal("device-firmware", { slot: Number(slot) });
    }
  });

  ble.addEventListener("firmware-update-failed", (event) => {
    const { slot, version, message, at } = event.detail;
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      device.firmwareUpdate = {
        ...(device.firmwareUpdate || {}),
        status: "failed",
        latestVersion: version || device.firmwareUpdate?.latestVersion || "",
        error: message || "Firmware update failed.",
        message: message || "Firmware update failed.",
        progress: 0
      };
      device.lastMessage = device.firmwareUpdate.message;
      device.lastUpdatedAt = at;
      appendActivity(device, device.firmwareUpdate.message, "error", at);
      pushDraftNotification(draft, {
        type: "error",
        title: "Firmware update failed",
        deviceSlot: device.slot,
        message: device.firmwareUpdate.message,
        timestamp: at,
        action: { type: "device-firmware", label: "Open firmware", slot: device.slot }
      });
    });
    showToast(message || `Device ${slot} firmware update failed`, "error");
  });

  ble.addEventListener("recipe-selection-acknowledged", (event) => {
    markSelectionAcknowledged(event.detail.slot, event.detail.recipeName);
  });

  ble.addEventListener("instruction-complete", (event) => {
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(event.detail.slot));
      if (!device) return draft;
      appendActivity(device, `Instruction step ${device.telemetry.stepNo || "?"} completed`, "info", event.detail.at);
      pushDraftNotification(draft, {
        type: "cooking",
        title: "Step changed",
        deviceSlot: device.slot,
        recipeName: device.telemetry.currentRecipe || device.activeRun?.displayName || "",
        message: `Instruction step ${device.telemetry.stepNo || "?"} completed.`,
        timestamp: event.detail.at,
        action: { type: "device", label: "Open device", slot: device.slot }
      });
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
      pushDraftNotification(draft, {
        type: "cooking",
        title: "User action acknowledged",
        deviceSlot: device.slot,
        recipeName: event.detail.recipeName || device.activeRun?.displayName || "",
        message: "Initial ingredients were confirmed.",
        timestamp: event.detail.at,
        action: { type: "device", label: "Open device", slot: device.slot }
      });
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
        pushDraftNotification(draft, {
          type: "error",
          title: "Recipe missing",
          deviceSlot: slot,
          recipeName: recipe.displayName,
          orderId: order.orderId || "",
          message: "The device reported the selected recipe was missing. Upload retry started.",
          action: { type: "device", label: "Open device", slot }
        });
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
      pushDraftNotification(draft, {
        type: "error",
        title: "Recipe missing",
        deviceSlot: slot,
        recipeName: recipe?.displayName || order?.itemName || "",
        orderId: order?.orderId || "",
        message: "Device could not find the selected recipe.",
        action: { type: "device", label: "Open device", slot }
      });
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
      pushDraftNotification(draft, {
        type: "logs",
        title: "Device recipe sync complete",
        deviceSlot: device.slot,
        recipeName,
        message: `${recipeName} uploaded to device memory.`,
        timestamp: event.detail.at,
        action: { type: "device-recipes", label: "View recipes", slot: device.slot }
      });
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
  const rememberedId = getDevice(slot)?.browserDeviceId || "";
  mutate((draft) => {
    const device = draft.devices.find((item) => item.slot === Number(slot));
    if (!device) return draft;
    device.connection = "connecting";
    device.lastMessage = rememberedId
      ? `Reconnecting saved cooker ${device.bluetoothName || `Device ${slot}`}. If Chrome asks, select the same cooker again.`
      : "Opening Bluetooth chooser to assign this window";
    appendActivity(device, device.lastMessage, "info");
  });
  try {
    if (rememberedId) {
      ble.closeStaleWebSession?.(Number(slot));
    }
    await ble.connect(Number(slot), rememberedId, {
      lockToRememberedDevice: Boolean(rememberedId),
      allowRememberedReauthorization: true,
      allowMoveConnectedSession: true
    });
    showToast(`Device ${slot} connected`, "success");
  } catch (error) {
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(slot));
      if (!device) return draft;
      device.connection = "disconnected";
      const message = error.code === "remembered-device-missing"
        ? `Chrome no longer has permission for the saved cooker. Tap Connect again and choose the same physical cooker, or use Clear pairing only if you must assign a different cooker.`
        : error.message;
      device.lastMessage = message;
      appendActivity(device, message, "warning");
    });
    showToast(error.code === "remembered-device-missing" ? "Saved cooker permission is missing. Select the same cooker again to repair this slot." : error.message, "error");
  }
}

function findConnectedSlotMoveCandidate(snapshot, targetSlot) {
  const target = snapshot.devices.find((item) => item.slot === Number(targetSlot));
  const connected = snapshot.devices.filter((item) => item.slot !== Number(targetSlot) && item.connection === "connected");
  if (!target || connected.length === 0) return null;
  const targetId = String(target.browserDeviceId || "").trim();
  const targetName = String(target.bluetoothName || "").trim().toLowerCase();
  if (targetId) {
    const idMatch = connected.find((item) => String(item.browserDeviceId || "") === targetId);
    if (idMatch) return idMatch;
  }
  if (targetName) {
    const nameMatch = connected.find((item) => String(item.bluetoothName || "").trim().toLowerCase() === targetName);
    if (nameMatch) return nameMatch;
  }
  return connected.length === 1 ? connected[0] : null;
}

async function moveConnectedCookerToSlot(targetSlot) {
  const snapshot = state();
  const candidate = findConnectedSlotMoveCandidate(snapshot, Number(targetSlot));
  if (!candidate) {
    showToast(`No connected cooker can be safely moved to Device ${targetSlot}.`, "warning");
    return;
  }
  await ble.moveConnectedSession(Number(candidate.slot), Number(targetSlot));
  mutate((draft) => {
    const target = draft.devices.find((item) => item.slot === Number(targetSlot));
    const source = draft.devices.find((item) => item.slot === Number(candidate.slot));
    if (source) {
      source.browserDeviceId = "";
      source.bluetoothName = "";
      source.macAddress = "";
      source.connection = "disconnected";
      source.lastMessage = `Cooker moved to Device ${targetSlot}.`;
      source.lastUpdatedAt = nowIso();
    }
    if (target) {
      target.browserDeviceId = candidate.browserDeviceId;
      target.bluetoothName = candidate.bluetoothName;
      target.macAddress = candidate.macAddress || target.macAddress || "";
      target.connection = "connected";
      target.lastMessage = `Connected cooker moved from Device ${candidate.slot} to this window.`;
      target.lastUpdatedAt = nowIso();
      appendActivity(target, target.lastMessage, "success");
    }
  });
  showToast(`Connected cooker moved from Device ${candidate.slot} to Device ${targetSlot}`, "success");
}

async function connectAllDevices() {
  const slots = [1, 2, 3, 4, 5];
  const failedSlots = [];
  mutate((draft) => {
    draft.devices.forEach((device) => {
      if (device.connection === "connected") return;
      device.connection = "connecting";
      device.lastMessage = ble.usesNativeBridge ? "Scanning for all On2Cook devices" : "Opening Bluetooth connection flow";
      appendActivity(device, "Kitchen Bluetooth connect-all requested from home screen", "info");
    });
  });
  try {
    if (ble.usesNativeBridge && typeof ble.connectAllNative === "function") {
      await ble.connectAllNative(slots);
    } else {
      for (const slot of slots) {
        const current = getDevice(slot);
        if (current?.connection === "connected") continue;
        if (!current?.browserDeviceId) {
          mutate((draft) => {
            const device = draft.devices.find((item) => item.slot === Number(slot));
            if (!device) return draft;
            device.connection = "disconnected";
            device.lastMessage = "No locked cooker assigned to this window. Use Connect once to assign one.";
          });
          continue;
        }
        mutate((draft) => {
          const device = draft.devices.find((item) => item.slot === Number(slot));
          if (!device) return draft;
          device.connection = "connecting";
          device.lastMessage = `Reconnecting locked cooker ${current?.bluetoothName || `Device ${slot}`}`;
        });
        try {
          await ble.connect(Number(slot), current.browserDeviceId, {
            lockToRememberedDevice: true,
            allowRememberedReauthorization: false
          });
        } catch (error) {
          failedSlots.push({ slot, error });
          mutate((draft) => {
            const device = draft.devices.find((item) => item.slot === Number(slot));
            if (!device) return draft;
            device.connection = "disconnected";
            device.lastMessage = error.message;
            appendActivity(device, error.message, "warning");
          });
        }
      }
      if (failedSlots.length) {
        console.warn("Some On2Cook devices could not be connected.", failedSlots);
      }
    }
    mutate((draft) => {
      draft.devices.forEach((device) => {
        if (device.connection !== "connecting") return;
        const failure = failedSlots.find((item) => Number(item.slot) === Number(device.slot));
        device.connection = "disconnected";
        device.lastMessage = failure?.error?.message || "Connection not started";
      });
    });
    const connectedCount = getConnectedDevices(state()).length;
    const failedCount = failedSlots.length;
    showToast(
      connectedCount > 0
        ? `${connectedCount} device${connectedCount === 1 ? "" : "s"} connected${failedCount ? `, ${failedCount} slot${failedCount === 1 ? "" : "s"} not connected` : ""}.`
        : failedCount
          ? "No device connected. Check power/range, then use Connect again or Clear pairing for the affected slot."
          : "Bluetooth scan started. Devices will appear as they connect.",
      connectedCount > 0 ? (failedCount ? "warning" : "success") : failedCount ? "warning" : "info"
    );
  } catch (error) {
    mutate((draft) => {
      draft.devices.forEach((device) => {
        if (device.connection !== "connecting") return;
        device.connection = "disconnected";
        device.lastMessage = error.message;
      });
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
    failLiveLogState(device, "Device disconnected. Live log stream stopped.", nowIso(), { clearEntries: true });
    appendActivity(device, "Device disconnected. Active work returned to pending.", "warning");
  });
  showToast(`Device ${slot} disconnected`, "info");
}

function canRunOnDevice(snapshot, order, device, recipe) {
  if (!device.enabled || device.connection !== "connected") return false;
  if (isFirmwareBlockingDevice(device)) return false;
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

async function startOrderFlow(orderId, preferredSlot = null, options = {}) {
  const snapshot = state();
  const order = snapshot.orders.current.find((item) => item.id === orderId);
  if (!order) return "missing-order";
  const recipe = getEffectiveRecipe(snapshot, order);
  const device = preferredSlot ? snapshot.devices.find((item) => item.slot === Number(preferredSlot)) : pickBestDevice(snapshot, order);
  const deviceOnlyRecipeName = getDeviceOnlyOrderRecipeName(order);
  if (!recipe && preferredSlot && deviceOnlyRecipeName) {
    return startDeviceOnlyOrderFlow(orderId, Number(preferredSlot), deviceOnlyRecipeName, options);
  }
  if (!recipe) {
    showToast(`No selected recipe matches ${order.itemName}`, "error");
    return "missing-recipe";
  }
  if (!device) {
    showToast("No connected device is ready for this recipe", "warning");
    return "no-device";
  }
  if (isFirmwareBlockingDevice(device)) {
    showToast(firmwareBlockMessage(device), "warning");
    return "firmware-required";
  }
  if (!canRunOnDevice(snapshot, order, device, recipe)) {
    showToast(`${recipe.displayName} is not enabled on Device ${device.slot}`, "error");
    return "not-allowed";
  }
  const liveSession = ble.getSession(device.slot);
  if (liveSession?.transfer) {
    showToast(`Device ${device.slot} is still syncing recipes. Try again in a moment.`, "warning");
    return "transfer-busy";
  }

  const busy =
    device.currentJobId ||
    (!options.ignoreQueuedWork && device.queueOrderIds.length > 0) ||
    device.completionConfirmationPending ||
    hasLiveRuntime(device);
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
      pushDraftNotification(draft, {
        type: "order",
        title: `Order assigned to D${draftDevice.slot}`,
        deviceSlot: draftDevice.slot,
        recipeName: draftOrder.itemName,
        orderId: draftOrder.orderId || "",
        message: "Order was added to this device queue.",
        action: { type: "device", label: "Open device", slot: draftDevice.slot }
      });
    });
    showToast(`${order.itemName} queued on Device ${device.slot}`, "info");
    return "queued";
  }

  mutate((draft) => {
    const draftOrder = draft.orders.current.find((item) => item.id === orderId);
    const draftDevice = draft.devices.find((item) => item.slot === device.slot);
    if (!draftOrder || !draftDevice) return draft;
    const runStartedAt = nowIso();
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
      startedAt: runStartedAt,
      durationSeconds: getRecipeDuration(recipe)
    };
    draftDevice.startupGuardUntil = new Date(Date.now() + 8000).toISOString();
    draftDevice.telemetry.currentRecipe = recipe.firmwareName;
    appendActivity(draftDevice, `Preparing ${recipe.firmwareName}`, "info");
    pushDraftNotification(draft, {
      type: "order",
      title: `Order assigned to D${draftDevice.slot}`,
      deviceSlot: draftDevice.slot,
      recipeName: draftOrder.itemName,
      orderId: draftOrder.orderId || "",
      message: "Order is being prepared for cooking.",
      action: { type: "device", label: "Open device", slot: draftDevice.slot }
    });
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
    const uploadedRecipes = await ensureRecipesAvailableOnDevice(device.slot, [recipe], {
      silent: true,
      forceInventory: true,
      overwriteExisting: false,
      inventoryTimeoutMs: 4500
    });
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === device.slot);
      if (!draftDevice) return draft;
      if (uploadedRecipes.length > 0) {
        appendFlowActivity(draftDevice, `${recipe.firmwareName} uploaded for this run`, "success");
      } else {
        appendFlowActivity(draftDevice, `${recipe.firmwareName} already present on device`, "info");
      }
    });
    await ble.runRecipe(device.slot, recipe.firmwareName, {
      autoStartAfterIngredient: false,
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
    return "started";
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
        pushDraftNotification(draft, {
          type: "error",
          title: "Recipe upload failed",
          deviceSlot: draftDevice.slot,
          recipeName: recipe.displayName,
          orderId: draftOrder?.orderId || "",
          message: error.message,
          action: { type: "device", label: "Open device", slot: draftDevice.slot }
        });
      }
    });
    showToast(error.message, "error");
    return "failed";
  }
}

async function syncSelectedRecipesToDevice(slot, options = {}) {
  const snapshot = state();
  const device = snapshot.devices.find((item) => item.slot === Number(slot));
  if (!device) return;
  if (!options.silent) {
    showToast(`Checking recipes stored on Device ${slot}`, "info");
  }
  try {
    const inventoryNames = await refreshDeviceRecipeInventory(slot, {
      force: true,
      timeoutMs: options.inventoryTimeoutMs || 4500
    });
    if (!options.silent) {
      showToast(`Device ${slot} inventory checked: ${inventoryNames.length} recipe${inventoryNames.length === 1 ? "" : "s"} found`, "success");
    }
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      draftDevice.baselineRecipeSyncPending = false;
      draftDevice.startupGuardUntil = "";
      draftDevice.uploadState = {
        ...emptyUploadState(),
          summary: `Inventory checked: ${inventoryNames.length} recipe${inventoryNames.length === 1 ? "" : "s"} on device`
      };
      if (!options.silent) {
        pushDraftNotification(draft, {
          type: "logs",
          title: "Recipe list refreshed",
          deviceSlot: draftDevice.slot,
          message: `${inventoryNames.length} recipe${inventoryNames.length === 1 ? "" : "s"} found on device.`,
          action: { type: "device-recipes", label: "View recipes", slot: draftDevice.slot }
        });
      }
    });
  } catch (error) {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      failUploadPlan(draftDevice, `Inventory check failed: ${error.message}`);
      appendActivity(draftDevice, `Inventory check failed: ${error.message}`, "error");
      if (!options.silent) {
        pushDraftNotification(draft, {
          type: "error",
          title: "Recipe list refresh failed",
          deviceSlot: draftDevice.slot,
          message: error.message,
          action: { type: "device-recipes", label: "Retry list", slot: draftDevice.slot }
        });
      }
    });
    if (!options.silent) showToast(error.message, "error");
    throw error;
  }
}

async function runDeviceRecipe(slot, recipeId) {
  const snapshot = state();
  const recipe = findRecipeById(snapshot, recipeId);
  if (!recipe) return;
  const order = decorateOrderRecord({
    id: safeRandomId("id"),
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
  return startOrderFlow(order.id, Number(slot));
}

function reorderDeviceQueueItem(slot, orderId, mode) {
  let label = "";
  mutate((draft) => {
    const device = draft.devices.find((item) => item.slot === Number(slot));
    if (!device) return draft;
    const ids = getDeviceQueuedOrderIds(draft, device);
    const index = ids.indexOf(orderId);
    if (index < 0) return draft;
    const [picked] = ids.splice(index, 1);
    if (mode === "up") {
      ids.splice(Math.max(0, index - 1), 0, picked);
      label = "moved up";
    } else if (mode === "down") {
      ids.splice(Math.min(ids.length, index + 1), 0, picked);
      label = "moved down";
    } else {
      ids.unshift(picked);
      label = "moved to next";
    }
    device.queueOrderIds = ids;
    appendActivity(device, `Queue item ${label}`, "info");
  });
  if (label) showToast(`Queue item ${label}`, "success");
}

async function startQueuedOrderNow(slot, orderId) {
  const snapshot = state();
  const device = snapshot.devices.find((item) => item.slot === Number(slot));
  if (!device) return;
  if (device.connection !== "connected") {
    showToast(`Device ${slot} is offline. Connect it before starting queued work.`, "warning");
    return;
  }
  if (isDeviceActivelyCooking(device)) {
    showToast("Device is cooking. Use Stop Current & Start Selected if you want to interrupt it.", "warning");
    return;
  }
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    const draftOrder = draft.orders.current.find((item) => item.id === orderId);
    if (!draftDevice || !draftOrder) return draft;
    draftDevice.queueOrderIds = getDeviceQueuedOrderIds(draft, draftDevice).filter((item) => item !== orderId);
    draftOrder.status = "pending";
    appendActivity(draftDevice, `${draftOrder.itemName} selected as current from queue timeline`, "info");
  });
  await startOrderFlow(orderId, Number(slot), { ignoreQueuedWork: true });
}

async function stopCurrentAndStartQueued(slot, orderId) {
  const snapshot = state();
  const device = snapshot.devices.find((item) => item.slot === Number(slot));
  if (!device) return;
  reorderDeviceQueueItem(slot, orderId, "next");
  if (!isDeviceActivelyCooking(device)) {
    await startQueuedOrderNow(slot, orderId);
    return;
  }
  await abortCurrentRecipe(Number(slot));
  showToast(`Device ${slot} will start the selected queued recipe after the abort is acknowledged.`, "warning");
}

function queueCookAgainFromHistory(slot, historyKey) {
  const snapshot = state();
  const device = snapshot.devices.find((item) => item.slot === Number(slot));
  if (!device) return;
  const historyRow = getDeviceCookedHistoryRows(snapshot, device, 30).find((row) => row.key === historyKey);
  if (!historyRow) {
    showToast("Cooked history item was not found.", "error");
    return;
  }
  const recipe =
    (historyRow.recipeId ? findRecipeById(snapshot, historyRow.recipeId) : null) ||
    findRecipeByFirmwareName(snapshot, historyRow.firmwareName) ||
    findEffectiveRecipeForOrder(snapshot, historyRow.displayName);
  if (!recipe) {
    showToast(`No recipe record matches ${historyRow.displayName}`, "error");
    return;
  }
  const queueOrder = decorateOrderRecord(
    {
      id: safeRandomId("recook"),
      orderId: `#R${Date.now().toString().slice(-5)}`,
      itemName: recipe.displayName || historyRow.displayName,
      recipeLookup: recipe.displayName || historyRow.displayName,
      quantity: "1 batch",
      source: "Re-cook",
      specialInstructions: "Repeated from cooked history",
      accentColor: "#f47b20",
      createdAt: nowIso(),
      status: "queued",
      assignedSlot: Number(slot),
      assignedMode: "device",
      activeRecipeId: recipe.id,
      currentRunRecipeName: recipe.displayName,
      currentRunFirmwareName: recipe.firmwareName,
      targetSlot: Number(slot),
      manual: true,
      recook: true,
      historySourceId: historyRow.orderId || historyRow.key,
      historyNote: "Cook Again from queue timeline"
    },
    recipe,
    snapshot.orders.current.length
  );
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    const ids = getDeviceQueuedOrderIds(draft, draftDevice);
    draft.orders.current.push(queueOrder);
    draftDevice.queueOrderIds = [...ids, queueOrder.id];
    appendActivity(draftDevice, `${queueOrder.itemName} added to queue as Cook Again`, "info");
  });
  showToast(`${queueOrder.itemName} added to Device ${slot} queue as Re-cook`, "success");
}

function allowRecipeOnDevice(draft, slot, recipeId) {
  const device = draft.devices.find((item) => item.slot === Number(slot));
  if (!device || !recipeId) return;
  seedAllowedRecipeIdsIfNeeded(device, draft);
  device.allowedRecipeIdsConfigured = true;
  if (!device.allowedRecipeIds.includes(recipeId)) {
    device.allowedRecipeIds.push(recipeId);
  }
}

function createDeviceQueuedRecipeOrder(snapshot, recipe, slot, source = "Quick Assign") {
  return decorateOrderRecord(
    {
      id: safeRandomId("quick"),
      orderId: `#Q${Date.now().toString().slice(-5)}`,
      itemName: recipe.displayName,
      recipeLookup: recipe.displayName,
      quantity: "1 batch",
      source,
      specialInstructions: "",
      accentColor: "#f47b20",
      createdAt: nowIso(),
      status: "queued",
      assignedSlot: Number(slot),
      assignedMode: "device",
      activeRecipeId: recipe.id,
      currentRunRecipeName: recipe.displayName,
      currentRunFirmwareName: recipe.firmwareName,
      targetSlot: Number(slot),
      manual: true,
      historyNote: ""
    },
    recipe,
    snapshot.orders.current.length
  );
}

async function resolveAssignableRecipe(payload) {
  const snapshot = state();
  if (payload.recipeId) {
    return findRecipeById(snapshot, payload.recipeId);
  }
  if (payload.catalogId) {
    const entry = getRecipeCatalog(snapshot).find((item) => item.id === payload.catalogId);
    if (!entry) return null;
    return ensureGlobalCatalogRecipeImported(entry, { ensureSelected: true });
  }
  return null;
}

async function executeDeviceRecipeAssignment(payload) {
  const slot = Number(payload.slot);
  const action = payload.action === "cook" ? "cook" : payload.action === "upload" ? "upload" : "queue";
  const device = getDevice(slot);
  if (!device) return;
  if (device.connection !== "connected") {
    showToast(`Device ${slot} is offline. Connect it before assigning a recipe.`, "warning");
    return;
  }
  if (action === "cook" && isDeviceActivelyCooking(device)) {
    showToast(`Device ${slot} is cooking now. Add this recipe to the queue instead.`, "warning");
    return;
  }
  const recipe = await resolveAssignableRecipe(payload);
  if (!recipe) {
    showToast("Recipe not found.", "error");
    return;
  }
  mutate((draft) => {
    allowRecipeOnDevice(draft, slot, recipe.id);
  });
  await ensureRecipesAvailableOnDevice(slot, [recipe], {
    silent: false,
    forceInventory: true,
    overwriteExisting: false,
    inventoryTimeoutMs: 4500
  });
  if (action === "upload") {
    showToast(`${recipe.displayName} is available on Device ${slot}`, "success");
    openModal("device-recipes", { slot, query: "", filter: "all", selectedNames: [] });
    return;
  }
  if (action === "cook") {
    const order = createDeviceQueuedRecipeOrder(state(), recipe, slot, payload.source || "Quick Assign");
    order.status = "pending";
    order.assignedSlot = null;
    order.currentRunRecipeName = recipe.displayName;
    order.currentRunFirmwareName = recipe.firmwareName;
    mutate((draft) => {
      draft.orders.current.unshift(order);
    });
    const result = await startOrderFlow(order.id, slot, { ignoreQueuedWork: true });
    if (result === "started") {
      showToast(`${recipe.displayName} starting on Device ${slot}`, "success");
    }
    openModal("device-sheet", { slot });
    return;
  }
  const order = createDeviceQueuedRecipeOrder(state(), recipe, slot, payload.source || "Quick Assign");
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === slot);
    if (!draftDevice) return draft;
    draft.orders.current.push(order);
    draftDevice.queueOrderIds = [...getDeviceQueuedOrderIds(draft, draftDevice), order.id];
    appendActivity(draftDevice, `${recipe.displayName} added from Quick Assign`, "info");
  });
  showToast(`${recipe.displayName} added to Device ${slot} queue`, "success");
  openModal("device-sheet", { slot });
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

function refreshStatusSoon(slot, delayMs = 1200) {
  const normalizedSlot = Number(slot);
  if (!normalizedSlot) return;
  const existing = statusRefreshTimers.get(normalizedSlot);
  if (existing) window.clearTimeout(existing);
  const timer = window.setTimeout(() => {
    statusRefreshTimers.delete(normalizedSlot);
    const snapshot = state();
    const device = snapshot.devices.find((item) => item.slot === normalizedSlot);
    if (!device || device.connection !== "connected") return;
    const session = ble.getSession(normalizedSlot);
    if (session?.transfer || session?.recipeListRequest) return;
    ble.requestStatus(normalizedSlot).catch(() => {});
  }, Math.max(500, Number(delayMs) || 1200));
  statusRefreshTimers.set(normalizedSlot, timer);
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
  if (["1", "2", "3", "4"].includes(normalizedValue)) {
    return current === "OFF" ? DEFAULT_STIRRER_LEVEL : current;
  }
  if (["LOW", "MED", "HIGH", "VERY_HIGH", "VHIGH"].includes(normalizedValue)) {
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

function formatManualTime(seconds) {
  return secondsLabel(seconds || 0).replace(":", " : ");
}

function getManualSlotUi(snapshot, slot) {
  return snapshot?.ui?.manualMode?.slotState?.[String(slot)] || {};
}

function manualSlotUiIsFresh(slotUi) {
  return Number(slotUi?.holdUntil || 0) > Date.now();
}

function getManualDisplayTelemetry(snapshot, device) {
  const telemetry = { ...getDisplayTelemetry(device) };
  const slotUi = getManualSlotUi(snapshot, device.slot);
  if (!manualSlotUiIsFresh(slotUi)) return telemetry;
  if (slotUi.indPower !== undefined) telemetry.indPower = Number(slotUi.indPower) || 0;
  if (slotUi.magPower !== undefined) telemetry.magPower = Number(slotUi.magPower) || 0;
  if (slotUi.indTime !== undefined) telemetry.indTime = Number(slotUi.indTime) || 0;
  if (slotUi.magTime !== undefined) telemetry.magTime = Number(slotUi.magTime) || 0;
  if (slotUi.inductionStatus) telemetry.inductionStatus = slotUi.inductionStatus;
  if (slotUi.magnetronStatus) telemetry.magnetronStatus = slotUi.magnetronStatus;
  if (slotUi.stirrer) telemetry.stirrer = slotUi.stirrer;
  if (slotUi.pumpOn !== undefined) telemetry.pumpOn = Boolean(slotUi.pumpOn);
  if (slotUi.purgeOn !== undefined) telemetry.purgeOn = Boolean(slotUi.purgeOn);
  return telemetry;
}

function setManualSlotUi(draft, slot, patch = {}, holdMs = MANUAL_UI_HOLD_MS) {
  draft.ui.manualMode = {
    ...(draft.ui.manualMode || {}),
    slotState: {
      ...(draft.ui.manualMode?.slotState || {})
    }
  };
  const key = String(slot);
  draft.ui.manualMode.slotState[key] = {
    ...(draft.ui.manualMode.slotState[key] || {}),
    ...patch,
    holdUntil: Date.now() + Math.max(1000, Number(holdMs) || MANUAL_UI_HOLD_MS)
  };
}

function getRecipeByKnownName(snapshot, name) {
  const key = normalizeRecipeNameKey(name);
  if (!key) return null;
  return (
    snapshot.recipes.find((recipe) =>
      [
        recipe.displayName,
        recipe.firmwareName,
        ...(recipe.aliases || [])
      ].some((candidate) => normalizeRecipeNameKey(candidate) === key)
    ) || null
  );
}

function getDeviceRecommendedRecipes(snapshot, device, limit = 8) {
  const picked = [];
  const seen = new Set();
  const add = (recipe) => {
    if (!recipe?.id || seen.has(recipe.id)) return;
    seen.add(recipe.id);
    picked.push(recipe);
  };
  getDeviceCookedHistoryRows(snapshot, device, 20).forEach((row) => {
    add((row.recipeId ? findRecipeById(snapshot, row.recipeId) : null) || getRecipeByKnownName(snapshot, row.firmwareName || row.displayName));
  });
  [...(device.availableRecipeNames || []), ...(device.syncedRecipeNames || [])].forEach((name) => add(getRecipeByKnownName(snapshot, name)));
  (device.allowedRecipeIds || []).forEach((id) => add(findRecipeById(snapshot, id)));
  return picked.slice(0, limit);
}

function manualQuickState(value) {
  const normalized = String(value || "").trim().toUpperCase();
  if (normalized.includes("PAUSE")) return "PAUSE";
  if (normalized.includes("START") || normalized.includes("RUN")) return "START";
  if (normalized.includes("STOP")) return "STOP";
  return "IDLE";
}

function manualModuleLabel(module, status) {
  const state = manualQuickState(status);
  const name = module === "magnetron" ? "Magnetron" : "Induction";
  if (state === "START") return `${name} Started`;
  if (state === "PAUSE") return `${name} paused`;
  if (state === "STOP") return `${name} stopped`;
  return `Start ${name.toLowerCase()}`;
}

function renderManualRoundButton(action, slot, icon, options = {}) {
  const attrs = [
    `class="native-manual-round ${options.active ? "active" : ""}"`,
    `data-action="${action}"`,
    `data-slot="${slot}"`,
    `aria-label="${escapeHtml(options.label || action)}"`
  ];
  if (options.extra) attrs.push(options.extra);
  if (options.disabled) attrs.push("disabled");
  return `<button ${attrs.join(" ")}>${icon}</button>`;
}

function renderManualStepButton(action, slot, label, options = {}) {
  const attrs = [
    `class="native-manual-step"`,
    `data-action="${action}"`,
    `data-slot="${slot}"`
  ];
  if (options.extra) attrs.push(options.extra);
  if (options.disabled) attrs.push("disabled");
  return `<button ${attrs.join(" ")}>${escapeHtml(label)}</button>`;
}

function renderNativeManualRecipeStrip(snapshot, className = "native-manual-recipes-strip", device = null) {
  const recipes = device ? getDeviceRecommendedRecipes(snapshot, device, 8) : getSelectedRecipes(snapshot).slice(0, 4);
  const cards = recipes.length ? recipes : [];
  if (!cards.length) {
    return `
      <div class="${className} empty">
        <div class="native-manual-recipe-empty">Recipes will appear here after they are assigned to this device or sent for cooking.</div>
      </div>
    `;
  }
  return `
    <div class="${className}">
      ${cards
        .slice(0, className.includes("bottom") ? 3 : 2)
        .map((recipe) => {
          const imageUrl = safeOptionalUrl(recipe.imageDataUrl, "manual recommended recipe image");
          const duration = secondsLabel(getRecipeDuration(recipe) || 420).replace("00:", "");
          return `
            <button class="native-manual-recipe-card" type="button" data-action="manual-pick-recipe" data-recipe-id="${recipe.id}" ${device ? `data-slot="${device.slot}"` : ""}>
              <div class="native-manual-recipe-image ${imageUrl ? "has-image" : ""}">
                ${imageUrl ? `<img src="${escapeHtml(imageUrl)}" alt="${escapeHtml(recipe.displayName)}">` : `<span>${escapeHtml(recipe.displayName.slice(0, 1))}</span>`}
                <b>Easy</b>
              </div>
              <strong>${escapeHtml(recipe.displayName)}</strong>
              <small>${escapeHtml(duration)} mins</small>
            </button>
          `;
        })
        .join("")}
    </div>
  `;
}

async function startManualInduction(slot) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  ensureDeviceCommandAllowed(device, "Manual induction");
  await ble.startInduction(Number(slot));
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.inductionStatus = "START";
    draftDevice.telemetry.workStatus = "manual";
    setManualSlotUi(draft, slot, {
      inductionStatus: "START",
      indPower: Number(draftDevice.telemetry.indPower || 0),
      indTime: Number(draftDevice.telemetry.indTime || 0)
    });
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
  ensureDeviceCommandAllowed(device, "Manual induction");
  await ble.stopInduction(Number(slot));
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.inductionStatus = "STOP";
    draftDevice.telemetry.indTime = 0;
    setManualSlotUi(draft, slot, { inductionStatus: "STOP", indTime: 0 });
    appendActivity(draftDevice, "Manual Mode: induction stop sent", "warning");
  });
  refreshStatusSoon(slot);
  showToast(`Manual induction stop sent to Device ${slot}`, "info");
}

async function pauseResumeManualInduction(slot) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  ensureDeviceCommandAllowed(device, "Manual induction pause/resume");
  const paused = manualQuickState(device.telemetry?.inductionStatus) === "PAUSE";
  await (paused ? ble.resumeInduction(Number(slot)) : ble.pauseInduction(Number(slot)));
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.inductionStatus = paused ? "START" : "PAUSE";
    draftDevice.telemetry.workStatus = "manual";
    setManualSlotUi(draft, slot, { inductionStatus: paused ? "START" : "PAUSE" });
    appendActivity(draftDevice, paused ? "Manual Mode: induction resume sent" : "Manual Mode: induction pause sent", "info");
  });
  refreshStatusSoon(slot);
  showToast(paused ? `Induction resumed on Device ${slot}` : `Induction paused on Device ${slot}`, "success");
}

async function adjustManualInductionTime(slot, deltaSeconds) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  ensureDeviceCommandAllowed(device, "Manual induction time");
  if (manualQuickState(device.telemetry?.inductionStatus) !== "START") {
    showToast("Start induction first, then adjust time", "warning");
    return;
  }
  await ble.changeInductionProcessTime(Number(slot), deltaSeconds);
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.indTime = Math.max(0, Number(draftDevice.telemetry.indTime || 0) + Number(deltaSeconds || 0));
    setManualSlotUi(draft, slot, {
      inductionStatus: "START",
      indTime: draftDevice.telemetry.indTime
    });
    appendActivity(draftDevice, `Manual Mode: induction time ${deltaSeconds > 0 ? "+" : ""}${deltaSeconds}s`, "info");
  });
  refreshStatusSoon(slot);
  showToast(`Induction time ${deltaSeconds > 0 ? "increased" : "decreased"} on Device ${slot}`, "success");
}

async function adjustManualInductionPower(slot, delta) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  ensureDeviceCommandAllowed(device, "Manual induction power");
  if (!isQuickStartActive(device.telemetry.inductionStatus) && Number(device.telemetry.indTime || 0) <= 0) {
    showToast("Start induction first, then adjust power", "warning");
    return;
  }
  await ble.changeInductionPower(Number(slot), delta);
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.indPower = Math.max(0, Math.min(100, Number(draftDevice.telemetry.indPower || 0) + Number(delta || 0)));
    setManualSlotUi(draft, slot, {
      inductionStatus: draftDevice.telemetry.inductionStatus || "START",
      indPower: draftDevice.telemetry.indPower
    });
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
  ensureDeviceCommandAllowed(device, "Manual microwave");
  await ble.startMagnetron(Number(slot));
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.magnetronStatus = "START";
    draftDevice.telemetry.workStatus = "manual";
    setManualSlotUi(draft, slot, {
      magnetronStatus: "START",
      magPower: Number(draftDevice.telemetry.magPower || 0),
      magTime: Number(draftDevice.telemetry.magTime || 0)
    });
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
  ensureDeviceCommandAllowed(device, "Manual microwave");
  await ble.stopMagnetron(Number(slot));
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.magnetronStatus = "STOP";
    draftDevice.telemetry.magTime = 0;
    setManualSlotUi(draft, slot, { magnetronStatus: "STOP", magTime: 0 });
    appendActivity(draftDevice, "Manual Mode: microwave stop sent", "warning");
  });
  refreshStatusSoon(slot);
  showToast(`Microwave stop sent to Device ${slot}`, "info");
}

async function pauseResumeManualMagnetron(slot) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  ensureDeviceCommandAllowed(device, "Manual microwave pause/resume");
  const paused = manualQuickState(device.telemetry?.magnetronStatus) === "PAUSE";
  await (paused ? ble.resumeMagnetron(Number(slot)) : ble.pauseMagnetron(Number(slot)));
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.magnetronStatus = paused ? "START" : "PAUSE";
    draftDevice.telemetry.workStatus = "manual";
    setManualSlotUi(draft, slot, { magnetronStatus: paused ? "START" : "PAUSE" });
    appendActivity(draftDevice, paused ? "Manual Mode: microwave resume sent" : "Manual Mode: microwave pause sent", "info");
  });
  refreshStatusSoon(slot);
  showToast(paused ? `Microwave resumed on Device ${slot}` : `Microwave paused on Device ${slot}`, "success");
}

async function adjustManualMagnetronPower(slot, delta) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  ensureDeviceCommandAllowed(device, "Manual microwave power");
  if (manualQuickState(device.telemetry?.magnetronStatus) !== "START") {
    showToast("Start microwave first, then adjust power", "warning");
    return;
  }
  await ble.changeMagnetronPower(Number(slot), delta);
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.magPower = Math.max(0, Math.min(100, Number(draftDevice.telemetry.magPower || 0) + Number(delta || 0)));
    setManualSlotUi(draft, slot, {
      magnetronStatus: draftDevice.telemetry.magnetronStatus || "START",
      magPower: draftDevice.telemetry.magPower
    });
    appendActivity(draftDevice, `Manual Mode: microwave power ${delta > 0 ? "+" : ""}${delta}`, "info");
  });
  refreshStatusSoon(slot);
  showToast(`Microwave power ${delta > 0 ? "increased" : "decreased"} on Device ${slot}`, "success");
}

async function adjustManualMagnetronTime(slot, deltaSeconds) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  ensureDeviceCommandAllowed(device, "Manual microwave time");
  if (manualQuickState(device.telemetry?.magnetronStatus) !== "START") {
    showToast("Start microwave first, then adjust time", "warning");
    return;
  }
  await ble.changeMagnetronProcessTime(Number(slot), deltaSeconds);
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.magTime = Math.max(0, Number(draftDevice.telemetry.magTime || 0) + Number(deltaSeconds || 0));
    setManualSlotUi(draft, slot, {
      magnetronStatus: "START",
      magTime: draftDevice.telemetry.magTime
    });
    appendActivity(draftDevice, `Manual Mode: microwave time ${deltaSeconds > 0 ? "+" : ""}${deltaSeconds}s`, "info");
  });
  refreshStatusSoon(slot);
  showToast(`Microwave time ${deltaSeconds > 0 ? "increased" : "decreased"} on Device ${slot}`, "success");
}

async function setManualStirrer(slot, speedLabel) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  ensureDeviceCommandAllowed(device, "Manual stirrer");
  const normalized = mapStirrerSpeedLabel(speedLabel);
  await ble.setStirrer(Number(slot), normalized);
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.stirrer = normalized;
    if (normalized !== "OFF") {
      draftDevice.telemetry.workStatus = "manual";
    }
    setManualSlotUi(draft, slot, { stirrer: normalized });
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

async function startManualPump(slot, units = MANUAL_SPRINKLE_UNITS) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  ensureDeviceCommandAllowed(device, "Manual pump");
  const safeUnits = MANUAL_SPRINKLE_UNITS;
  await ble.startPump(Number(slot), safeUnits);
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.pumpOn = true;
    draftDevice.telemetry.workStatus = "manual";
    setManualSlotUi(draft, slot, { pumpOn: true }, 2500);
    appendActivity(draftDevice, `Manual Mode: 10 ml sprinkle sent`, "success");
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
  ensureDeviceCommandAllowed(device, "Manual pump");
  await ble.stopPump(Number(slot));
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.pumpOn = false;
    setManualSlotUi(draft, slot, { pumpOn: false });
    appendActivity(draftDevice, "Manual Mode: pump stop sent", "warning");
  });
  refreshStatusSoon(slot);
  showToast(`Pump stop sent to Device ${slot}`, "info");
}

async function startManualPurge(slot, ml) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  ensureDeviceCommandAllowed(device, "Manual spray");
  const safeMl = Math.max(10, Math.trunc(Number(ml) || 0));
  await ble.startPurge(Number(slot), safeMl);
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.purgeOn = true;
    draftDevice.telemetry.workStatus = "manual";
    setManualSlotUi(draft, slot, { purgeOn: true }, 2500);
    appendActivity(draftDevice, `Manual Mode: spray start sent (${safeMl} ml)`, "success");
  });
  refreshStatusSoon(slot);
  showToast(`Spray started on Device ${slot}`, "success");
}

async function stopManualPurge(slot) {
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is not connected`, "warning");
    return;
  }
  ensureDeviceCommandAllowed(device, "Manual spray");
  await ble.stopPurge(Number(slot));
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.purgeOn = false;
    setManualSlotUi(draft, slot, { purgeOn: false });
    appendActivity(draftDevice, "Manual Mode: spray stop sent", "warning");
  });
  refreshStatusSoon(slot);
  showToast(`Spray stop sent to Device ${slot}`, "info");
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
  const device = getDevice(slot);
  if (!device) return;
  if (device.connection !== "connected") {
    showToast(`Device ${slot} is not connected. Abort from screen is available only while connected.`, "warning");
    return;
  }
  const hasActiveWork = Boolean(device.currentJobId || hasLiveRuntime(device) || device.activeRun?.recipeId || device.telemetry.currentRecipe);
  if (!hasActiveWork) {
    showToast(`Device ${slot} has no active recipe to abort.`, "info");
    return;
  }
  await ble.abortRecipe(Number(slot));
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.telemetry.workStatus = "aborting";
    draftDevice.telemetry.status = "Abort requested";
    appendFlowActivity(draftDevice, "Abort requested from screen: stop=100 sent", "warning");
  });
  showToast(`Abort sent to Device ${slot}`, "warning");
}

async function restartRecipe(slot) {
  showToast("Restart is disabled in the web app until a device-side restart flow is defined without stop=100.", "warning");
}

async function openLiveLogs(slot) {
  const device = getDevice(slot);
  if (!device) return;
  openModal("live-logs", { slot: Number(slot) });
  if (device.connection !== "connected") {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      failLiveLogState(draftDevice, "Connect the device to stream live logs.", nowIso(), { clearEntries: true });
    });
    showToast("Connect the device first to view live logs.", "warning");
    return;
  }
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    startLiveLogState(draftDevice);
    appendFlowActivity(draftDevice, "Live Logs opened: livelog=ON sent", "info");
  });
  try {
    await ble.setLiveLog(Number(slot), true);
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      markLiveLogReady(draftDevice);
      pushDraftNotification(draft, {
        type: "logs",
        title: "Live logs started",
        deviceSlot: draftDevice.slot,
        message: "Real-time device diagnostics are streaming.",
        action: { type: "live-logs", label: "View live logs", slot: draftDevice.slot }
      });
    });
  } catch (error) {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      failLiveLogState(draftDevice, error.message || "Unable to start live logs.");
      pushDraftNotification(draft, {
        type: "logs",
        title: "Log download failed",
        deviceSlot: draftDevice.slot,
        message: error.message || "Unable to start live logs.",
        action: { type: "live-logs", label: "Retry live logs", slot: draftDevice.slot }
      });
    });
    throw error;
  }
}

async function stopLiveLogs(slot) {
  const device = getDevice(slot);
  if (!device) return;
  let stopError = null;
  if (device.connection === "connected") {
    try {
      await ble.setLiveLog(Number(slot), false);
    } catch (error) {
      stopError = error;
    }
  }
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    stopLiveLogState(draftDevice);
    appendFlowActivity(
      draftDevice,
      stopError
        ? `Live Logs switched off locally. livelog=OFF failed: ${stopError.message || "unknown error"}`
        : device.connection === "connected"
          ? "Live Logs closed: livelog=OFF sent"
          : "Live Logs switched off locally. Device is not connected.",
      stopError ? "warning" : "info"
    );
    pushDraftNotification(draft, {
      type: "logs",
      title: "Live logs stopped",
      deviceSlot: draftDevice.slot,
      message: stopError ? "The UI stream was stopped, but the device did not acknowledge livelog=OFF." : "Real-time diagnostic stream was stopped.",
      action: { type: "device", label: "Open device", slot: draftDevice.slot }
    });
  });
  if (stopError) throw stopError;
}

async function requestDeviceStatusWindow(slot) {
  const device = getDevice(slot);
  if (!device) return;
  openModal("device-status", { slot: Number(slot) });
  if (device.connection !== "connected") {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      draftDevice.lastMessage = "Please connect the device to refresh live status.";
      draftDevice.lastUpdatedAt = draftDevice.lastUpdatedAt || nowIso();
    });
    showToast("Please connect the device to refresh live status.", "warning");
    return;
  }
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.lastMessage = "STATUS=? requested";
    draftDevice.lastUpdatedAt = nowIso();
    appendFlowActivity(draftDevice, "Status requested: STATUS=? sent", "info");
  });
  try {
    await ble.requestStatus(Number(slot));
    addNotification({
      type: "device",
      title: "Device status refreshed",
      deviceSlot: Number(slot),
      message: "Status request was sent to the cooker.",
      action: { type: "device-status", label: "View status", slot: Number(slot) }
    });
  } catch (error) {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      draftDevice.lastMessage = error.message || "Unable to request device status.";
      draftDevice.lastUpdatedAt = nowIso();
      appendFlowActivity(draftDevice, draftDevice.lastMessage, "error");
    });
    throw error;
  }
}

async function requestDeviceFirmwareWindow(slot) {
  const device = getDevice(slot);
  if (!device) return;
  openModal("device-firmware", { slot: Number(slot) });
  if (device.connection !== "connected") {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      draftDevice.lastMessage = "Please connect the device to read firmware.";
      draftDevice.lastUpdatedAt = draftDevice.lastUpdatedAt || nowIso();
    });
    showToast("Please connect the device to read firmware.", "warning");
    return;
  }
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    draftDevice.lastMessage = "Firmware=? requested";
    draftDevice.lastUpdatedAt = nowIso();
    appendFlowActivity(draftDevice, "Firmware requested: Firmware=? sent", "info");
  });
  try {
    await ble.requestFirmwareVersion(Number(slot));
    addNotification({
      type: "device",
      title: "Firmware request sent",
      deviceSlot: Number(slot),
      message: "Firmware=? was sent to the cooker.",
      action: { type: "device-firmware", label: "View firmware", slot: Number(slot) }
    });
  } catch (error) {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      draftDevice.lastMessage = error.message || "Unable to request firmware.";
      draftDevice.lastUpdatedAt = nowIso();
      appendFlowActivity(draftDevice, draftDevice.lastMessage, "error");
    });
    throw error;
  }
}

async function listDeviceLogs(slot) {
  const device = getDevice(slot);
  if (!device) return;
  openModal("stored-logs", { slot: Number(slot) });
  if (device.connection !== "connected") {
    showToast("Connect the device first to fetch firmware logs. Showing saved log state.", "info");
    return;
  }
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    const logFetch = ensureDeviceLogState(draftDevice);
    logFetch.listing = true;
    logFetch.error = "";
    logFetch.status = "Checking LOGSTATUS=? before stored log download...";
    logFetch.updatedAt = nowIso();
  });
  const logStatus = await ble.checkLogStatus(Number(slot));
  if (logStatus === "BUSY") {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      handleDeviceLogControlMessage(draftDevice, "LOGSTATUS=BUSY");
      appendFlowActivity(draftDevice, "Stored log download blocked because device is busy", "warning");
      pushDraftNotification(draft, {
        type: "device",
        title: "Device busy",
        deviceSlot: draftDevice.slot,
        message: "Logs can be downloaded after current cooking ends.",
        action: { type: "device", label: "Open device", slot: draftDevice.slot }
      });
    });
    showToast("Device is busy. Logs can be downloaded after current cooking ends.", "warning");
    return;
  }
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    beginDeviceLogListing(draftDevice);
    appendFlowActivity(draftDevice, "Stored historical log list requested after LOGSTATUS=IDLE", "info");
    pushDraftNotification(draft, {
      type: "logs",
      title: "Log file ready",
      deviceSlot: draftDevice.slot,
      message: "Stored log list request sent.",
      action: { type: "stored-logs", label: "View logs", slot: draftDevice.slot }
    });
  });
  await ble.listLogs(Number(slot));
}

async function readDeviceLog(slot, rawName) {
  const device = getDevice(slot);
  if (!device) return;
  const cleanName = String(rawName || "").trim();
  if (!cleanName) return;
  openModal("stored-logs", { slot: Number(slot) });
  if (device.connection !== "connected") {
    showToast("Connect the device before reading a firmware log.", "warning");
    return;
  }
  const logStatus = await ble.checkLogStatus(Number(slot));
  if (logStatus === "BUSY") {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      handleDeviceLogControlMessage(draftDevice, "LOGSTATUS=BUSY");
    });
    showToast("Device is busy. Logs can be downloaded after current cooking ends.", "warning");
    return;
  }
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    beginDeviceLogRead(draftDevice, cleanName);
    appendFlowActivity(draftDevice, `Firmware log requested: ${formatLogFileDisplay(cleanName)}`, "info");
  });
  await ble.readLog(Number(slot), cleanName);
}

function exportDeviceLog(slot, format = "txt") {
  const device = getDevice(slot);
  if (!device) return;
  const logFetch = {
    ...emptyLogFetchState(),
    ...(device.logFetch || {})
  };
  if (!logFetch.content) {
    showToast("No stored log content is available to export.", "warning");
    return;
  }
  const safeFormat = format === "csv" ? "csv" : "txt";
  const baseName = (logFetch.activeDisplayName || formatLogFileDisplay(logFetch.activeFile) || `device-${slot}-log`)
    .replace(/[^A-Za-z0-9_-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 70) || `device-${slot}-log`;
  const content = safeFormat === "csv" ? buildStoredLogCsv(logFetch.content) : logFetch.content;
  const blob = new Blob([content], { type: safeFormat === "csv" ? "text/csv" : "text/plain" });
  const href = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = href;
  anchor.download = `${baseName}.${safeFormat}`;
  anchor.click();
  URL.revokeObjectURL(href);
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

function renderFirmwareUpdateNotice(device, options = {}) {
  const update = device.firmwareUpdate || {};
  const status = String(update.status || "unknown").toLowerCase();
  const latest = update.latestVersion || latestFirmwareManifest?.version || "";
  const current = update.currentVersion || device.telemetry?.firmwareVersion || "Unknown";
  const visible = options.always || ["required", "updating", "starting", "downloading", "failed", "checking"].includes(status);
  if (!visible) return "";
  const tone = status === "failed" ? "error" : status === "required" ? "warning" : status === "current" ? "success" : "info";
  const title =
    status === "updating" || status === "starting" || status === "downloading"
      ? "Firmware updating"
      : status === "required"
        ? "Firmware update required"
        : status === "failed"
          ? "Firmware update failed"
          : status === "checking"
            ? "Checking firmware"
            : "Firmware status";
  return `
    <div class="settings-card firmware-update-notice ${tone}">
      <div class="row space">
        <div>
          <div class="mini-title">${escapeHtml(title)}</div>
          <p class="subtle">${escapeHtml(update.message || `Current ${current}${latest ? ` | Latest ${latest}` : ""}`)}</p>
        </div>
        <span class="queue-tag">${escapeHtml(status || "unknown")}</span>
      </div>
      ${
        status === "updating" || status === "starting" || status === "downloading"
          ? `<div class="progress-bar"><span style="width:${Math.max(1, Math.min(100, Number(update.progress) || 1))}%"></span></div>`
          : ""
      }
      <div class="meta-grid">
        <span>Current ${escapeHtml(current)}</span>
        <span>Latest ${escapeHtml(latest || "Unknown")}</span>
      </div>
      ${
        device.connection === "connected" && status !== "updating" && status !== "current"
          ? `<div class="action-row top-gap"><button class="primary-button small" type="button" data-action="start-firmware-update" data-slot="${device.slot}">Update firmware</button></div>`
          : ""
      }
    </div>
  `;
}

function getNewestLiveLogEntry(entries, predicate = () => true) {
  for (let index = entries.length - 1; index >= 0; index -= 1) {
    if (predicate(entries[index])) return entries[index];
  }
  return null;
}

function renderLiveLogMetric(label, value, detail = "") {
  return `
    <span class="live-log-metric">
      <small>${escapeHtml(label)}</small>
      <strong>${escapeHtml(value || "-")}</strong>
      ${detail ? `<em>${escapeHtml(detail)}</em>` : ""}
    </span>
  `;
}

function summarizeLiveLogEntry(entry) {
  if (!entry) return "No live packet received yet.";
  if (entry.modeType === "sensor") {
    const sensor = entry.sensor || {};
    return `Sensors: IGBT ${sensor.igbtTemp || "-"}, glass ${sensor.glassTemp || "-"}, pan ${sensor.panTemp || "-"}, ${sensor.indVoltage || "-"} V`;
  }
  if (entry.modeType === "manual") {
    return `Manual: induction ${entry.inductionState || "-"} ${entry.inductionRun || ""}, microwave ${entry.microwaveState || "-"} ${entry.microwaveRun || ""}, stirrer ${entry.stirrer || "-"}`;
  }
  if (entry.modeType === "recipe") {
    return `Recipe: ${entry.recipeName || "-"}, step ${entry.stepNo || "-"}${entry.totalSteps ? `/${entry.totalSteps}` : ""}, ${entry.status || "-"}`;
  }
  return entry.message || "Device message";
}

function renderLiveLogsModal(snapshot, device) {
  const liveLog = {
    ...emptyLiveLogState(),
    ...(device.liveLog || {})
  };
  const canStream = device.connection === "connected";
  const entries = canStream && liveLog.active && Array.isArray(liveLog.entries)
    ? liveLog.entries.slice(-MAX_LIVE_LOG_ENTRIES).map((entry) => normalizeLiveLogEntry(device, entry))
    : [];
  const newestFirst = [...entries].reverse();
  const latestRecipe = getNewestLiveLogEntry(entries, (entry) => entry.direction !== "tx" && entry.modeType === "recipe");
  const latestManual = getNewestLiveLogEntry(entries, (entry) => entry.direction !== "tx" && entry.modeType === "manual");
  const latestSensor = getNewestLiveLogEntry(entries, (entry) => entry.direction !== "tx" && entry.modeType === "sensor");
  const latestStatus = getNewestLiveLogEntry(entries, (entry) => entry.direction !== "tx" && entry.modeType === "status");
  const currentEntry = latestRecipe || latestManual || latestSensor || latestStatus || getNewestLiveLogEntry(entries, (entry) => entry.direction !== "tx");
  const modeLabel = latestRecipe
    ? "Recipe running"
    : latestManual
      ? "Manual mode"
      : latestSensor
        ? "Sensor stream"
        : latestStatus?.status === "IDLE" || device.telemetry?.workStatus === "idle"
          ? "Device idle"
          : "Waiting for live values";
  const modeNote = latestRecipe
    ? "Showing the latest recipe packet from the firmware."
    : latestManual
      ? "Showing quick-start induction, microwave, stirrer, and pump state."
      : latestSensor
        ? "Showing the latest compact sensor packet from livelog=ON."
        : "Live Logs is on, but no recipe/manual telemetry packet has arrived yet.";
  const sensor = latestSensor?.sensor || {};
  const compactEvents = newestFirst.slice(0, 14);
  const emptyTitle = canStream ? "No live values received yet." : "Please connect the device";
  const emptyText = canStream
    ? "Tap Start Live Logs and keep this open while the device is cooking."
    : "Cannot show live diagnostics at present. Connect this device, then tap Start Live Logs.";
  return `
    <div class="modal-backdrop">
      <div class="modal-card live-logs-modal">
        <div class="row space">
          <div>
            <div class="eyebrow">Live Logs</div>
            <h3>${escapeHtml(device.displayName)}</h3>
            <p class="subtle">Real-time diagnostics only. Opens with livelog=ON and closes with livelog=OFF.</p>
          </div>
          <button class="icon-button" data-action="close-modal" aria-label="Close live logs">x</button>
        </div>
        <div class="settings-card live-log-status-card">
          <div class="meta-grid">
            <span>Connection ${escapeHtml(device.connection)}</span>
            <span>Stream ${escapeHtml(liveLog.active ? "ON" : "OFF")}</span>
            <span>Updated ${escapeHtml(liveLog.updatedAt ? formatTimestamp(liveLog.updatedAt) : "Never")}</span>
          </div>
          <div class="log-status-line ${liveLog.error || !canStream ? "error" : ""}">${escapeHtml(liveLog.error || (!canStream ? "Please connect the device before starting live logs." : liveLog.status || "Live logs are off."))}</div>
          <div class="action-row">
            <button class="primary-button small" type="button" data-action="start-live-logs" data-slot="${device.slot}" ${canStream ? "" : "disabled"}>Start Live Logs</button>
            <button class="secondary-button small" type="button" data-action="stop-live-logs" data-slot="${device.slot}" ${canStream ? "" : "disabled"}>Stop Live Logs</button>
            <button class="secondary-button small" type="button" data-action="clear-live-logs" data-slot="${device.slot}">Clear Feed</button>
          </div>
        </div>
        ${
          entries.length
            ? `<div class="live-log-dashboard">
                <section class="live-log-now-card ${escapeHtml(currentEntry?.modeType || "status")}">
                  <div>
                    <small>Current live view</small>
                    <h4>${escapeHtml(modeLabel)}</h4>
                    <p>${escapeHtml(modeNote)}</p>
                  </div>
                  <strong>${escapeHtml(currentEntry?.status || device.telemetry?.workStatus || "-")}</strong>
                </section>
                <div class="live-log-metric-grid">
                  ${renderLiveLogMetric("Recipe", currentEntry?.recipeName || device.activeRun?.displayName || "-")}
                  ${renderLiveLogMetric("Step", currentEntry?.stepNo ? `${currentEntry.stepNo}${currentEntry.totalSteps ? ` / ${currentEntry.totalSteps}` : ""}` : "-")}
                  ${renderLiveLogMetric("Time left", currentEntry?.timeLeft || device.telemetry?.remainingSeconds ? (currentEntry?.timeLeft || secondsLabel(device.telemetry.remainingSeconds)) : "-")}
                  ${renderLiveLogMetric("Induction", currentEntry?.induction || currentEntry?.inductionState || "-", currentEntry?.inductionRun ? `run ${currentEntry.inductionRun}` : "")}
                  ${renderLiveLogMetric("Microwave", currentEntry?.microwave || currentEntry?.microwaveState || "-", currentEntry?.microwaveRun ? `run ${currentEntry.microwaveRun}` : "")}
                  ${renderLiveLogMetric("Stirrer", currentEntry?.stirrer || "-")}
                  ${renderLiveLogMetric("Water/Pump", currentEntry?.pump || "-")}
                  ${renderLiveLogMetric("Temp", latestSensor ? `Pan ${sensor.panTemp || "-"}` : (currentEntry?.temperature || "-"), latestSensor ? `IGBT ${sensor.igbtTemp || "-"} | Glass ${sensor.glassTemp || "-"}` : "")}
                  ${renderLiveLogMetric("Voltage / Current", latestSensor ? `${sensor.indVoltage || "-"} V / ${sensor.indCurrent || "-"} I` : (currentEntry?.currentVoltage || "-"))}
                </div>
                <div class="live-log-sensor-strip">
                  ${renderLiveLogMetric("MAC", sensor.macId || "-")}
                  ${renderLiveLogMetric("Ambient", sensor.ambientTemp || "-")}
                  ${renderLiveLogMetric("Coil", sensor.coilTemp || "-")}
                  ${renderLiveLogMetric("PCB", sensor.pcbTemp || "-")}
                  ${renderLiveLogMetric("Mag current", sensor.magCurrent || "-")}
                  ${renderLiveLogMetric("Ind/Mag on", latestSensor ? `${sensor.indOn || "0"} / ${sensor.magOn || "0"}` : "-")}
                </div>
              </div>`
            : `<div class="empty-card live-log-empty ${canStream ? "" : "blocked"}">
                <strong>${escapeHtml(emptyTitle)}</strong>
                <span>${escapeHtml(emptyText)}</span>
              </div>`
        }
        <section class="live-log-compact-section">
          <div class="row space">
            <div>
              <div class="mini-title">Recent live events</div>
              <p class="subtle">Compact feed. Full raw BLE packets are kept below for diagnostics.</p>
            </div>
            <span class="queue-tag">${escapeHtml(String(entries.length))} packets</span>
          </div>
          ${
            compactEvents.length
              ? `<div class="live-log-compact-feed">
                  ${compactEvents
                  .map(
                    (entry) => `
                      <div class="live-log-compact-row ${escapeHtml(entry.modeType || "message")} ${escapeHtml(entry.direction || "rx")}">
                        <span>${escapeHtml(formatShortTime(entry.at))}</span>
                        <b>${escapeHtml(entry.modeType === "sensor" ? "Sensor" : entry.modeType === "manual" ? "Manual" : entry.modeType === "recipe" ? "Recipe" : entry.direction === "tx" ? "Sent" : "Device")}</b>
                        <em>${escapeHtml(summarizeLiveLogEntry(entry))}</em>
                      </div>
                    `
                  )
                  .join("")}
                </div>`
              : `<div class="empty-card live-log-empty ${canStream ? "" : "blocked"}">
                  <strong>${escapeHtml(emptyTitle)}</strong>
                  <span>${escapeHtml(emptyText)}</span>
                </div>`
          }
        </section>
        ${
          newestFirst.length
            ? `<details class="live-log-raw-details">
                <summary>Raw BLE traffic (${escapeHtml(String(newestFirst.length))})</summary>
                <div class="live-log-raw-feed">
                  ${newestFirst.slice(0, 40).map((entry) => `
                    <pre class="live-log-message"><b>${escapeHtml(formatShortTime(entry.at))} ${escapeHtml(String(entry.direction || "rx").toUpperCase())}</b> ${escapeHtml(entry.message || "")}</pre>
                  `).join("")}
                </div>
              </details>`
            : ""
        }
      </div>
    </div>
  `;
}

function renderFirmwareLogPanel(device) {
  const logFetch = {
    ...emptyLogFetchState(),
    ...(device.logFetch || {})
  };
  const logFiles = Array.isArray(device.logFiles) ? device.logFiles : [];
  const grouped = groupLogFiles(logFiles);
  const rows = parseStoredLogRows(logFetch.content || "");
  return `
    <div class="settings-card stored-log-panel">
      <div class="row space">
        <div>
          <div class="mini-title">Stored historical logs</div>
          <p class="subtle">Uses LOGSTATUS=? before LISTLOGS. READLOG is available only when the device is idle.</p>
        </div>
        <span class="subtle">${escapeHtml(logFetch.updatedAt ? formatTimestamp(logFetch.updatedAt) : "Not fetched yet")}</span>
      </div>
      <div class="action-row">
        <button class="secondary-button" type="button" data-action="list-logs" data-slot="${device.slot}">
          ${logFetch.listing ? "Checking..." : "Bottom Logs"}
        </button>
      </div>
      ${
        logFetch.status || logFetch.error
          ? `<div class="log-status-line ${logFetch.error ? "error" : ""}">${escapeHtml(logFetch.error || logFetch.status)}</div>`
          : `<div class="subtle">Tap Bottom Logs to check status, list stored log files, and select a file to download.</div>`
      }
      ${
        logFiles.length
          ? `<div class="stored-log-groups">
              ${Object.entries(grouped)
                .filter(([, files]) => files.length)
                .map(
                  ([groupName, files]) => `
                    <div class="stored-log-group">
                      <div class="stored-log-group-title">${escapeHtml(groupName)}</div>
                      <div class="log-file-list">
                        ${files
                          .map(
                            (file) => `
                              <button class="log-file-button ${file.rawName === logFetch.activeFile ? "selected" : ""}" type="button" data-action="read-device-log" data-slot="${device.slot}" data-file-name="${escapeHtml(file.rawName)}">
                                <span>${escapeHtml(file.displayName || formatLogFileDisplay(file.rawName))}</span>
                                <small>${file.rawName === logFetch.activeFile && logFetch.reading ? "reading" : "READLOG"}</small>
                              </button>
                            `
                          )
                          .join("")}
                      </div>
                    </div>
                  `
                )
                .join("")}
            </div>`
          : `<div class="empty-card">No firmware log files are listed yet.</div>`
      }
      ${
        logFetch.activeFile
          ? `<div class="firmware-log-viewer">
              <div class="row space">
                <strong>${escapeHtml(logFetch.activeDisplayName || formatLogFileDisplay(logFetch.activeFile))}</strong>
                <span class="subtle">${escapeHtml(logFetch.reading ? "Receiving..." : logFetch.complete ? "Complete" : "Ready")}</span>
              </div>
              <div class="action-row">
                <button class="secondary-button small" type="button" data-action="export-device-log" data-slot="${device.slot}" data-format="txt" ${logFetch.content ? "" : "disabled"}>Export TXT</button>
                <button class="secondary-button small" type="button" data-action="export-device-log" data-slot="${device.slot}" data-format="csv" ${logFetch.content ? "" : "disabled"}>Export CSV</button>
              </div>
              ${
                rows.length
                  ? `<div class="stored-log-table-wrap">
                      <table class="stored-log-table">
                        <thead>
                          <tr>
                            <th>Time</th>
                            <th>Recipe</th>
                            <th>Step</th>
                            <th>Ind</th>
                            <th>Micro</th>
                            <th>Stirrer</th>
                            <th>Water</th>
                            <th>Temp</th>
                            <th>V/I</th>
                            <th>Error</th>
                          </tr>
                        </thead>
                        <tbody>
                          ${rows
                            .slice(0, 120)
                            .map(
                              (row) => `
                                <tr>
                                  <td>${escapeHtml(row.time || "-")}</td>
                                  <td>${escapeHtml(row.recipe || "-")}</td>
                                  <td>${escapeHtml(row.step || "-")}</td>
                                  <td>${escapeHtml(row.induction || "-")}</td>
                                  <td>${escapeHtml(row.microwave || "-")}</td>
                                  <td>${escapeHtml(row.stirrer || "-")}</td>
                                  <td>${escapeHtml(row.pump || "-")}</td>
                                  <td>${escapeHtml(row.temperature || "-")}</td>
                                  <td>${escapeHtml(row.currentVoltage || "-")}</td>
                                  <td>${escapeHtml(row.error || "-")}</td>
                                </tr>
                              `
                            )
                            .join("")}
                        </tbody>
                      </table>
                    </div>`
                  : `<pre class="log-content">${escapeHtml(logFetch.content || (logFetch.reading ? "Waiting for log data..." : "No log content received yet."))}</pre>`
              }
            </div>`
          : ""
      }
    </div>
  `;
}

function getDeviceMacLabel(device) {
  if (device.macAddress) return device.macAddress;
  if (device.telemetry?.macAddress) return device.telemetry.macAddress;
  if (device.browserDeviceId?.startsWith("native:")) return device.browserDeviceId.replace(/^native:/, "");
  if (device.browserDeviceId) return `Browser ID ${device.browserDeviceId}`;
  return "Unknown";
}

function getDeviceHardwareLabel(device) {
  return device.hardwareVersion || device.telemetry?.hardwareVersion || "Unknown";
}

function getDeviceAssignedUserLabel(snapshot, device) {
  const currentUser = getCurrentUser(snapshot);
  return device.assignedUserName || device.assignedUserEmail || currentUser?.displayName || currentUser?.email || "Unassigned";
}

function getDeviceHealthLabel(device) {
  if (device.connection !== "connected") return "Offline";
  if (device.logFetch?.error || device.liveLog?.error) return "Needs attention";
  if (device.telemetry?.workStatus === "offline") return "Waiting for status";
  if (isDeviceActivelyCooking(device)) return "Cooking";
  return "Healthy";
}

function getDeviceLogsStatusLabel(device) {
  const logFetch = {
    ...emptyLogFetchState(),
    ...(device.logFetch || {})
  };
  if (logFetch.error) return "Error";
  if (logFetch.reading) return "Reading";
  if (logFetch.listing) return "Checking";
  if (logFetch.updatedAt) {
    const count = Array.isArray(device.logFiles) ? device.logFiles.length : 0;
    return `${count} file${count === 1 ? "" : "s"}`;
  }
  return "Not checked";
}

function getDisplayTelemetry(device) {
  const telemetry = device.telemetry || {};
  if (device.connection === "connected") return telemetry;
  return {
    ...telemetry,
    workStatus: "offline",
    currentRecipe: "",
    remainingSeconds: 0,
    magTime: 0,
    indTime: 0,
    indPower: 0,
    magPower: 0,
    stepNo: 0,
    mode: "",
    status: "",
    inductionStatus: "IDLE",
    magnetronStatus: "IDLE",
    ingredientsIndex: 0,
    stirrer: "OFF",
    pumpOn: false,
    paused: false,
    lastRaw: ""
  };
}

function getDeviceLastSyncLabel(device) {
  return device.recipeInventoryUpdatedAt || device.logFetch?.updatedAt || device.lastUpdatedAt
    ? formatTimestamp(device.recipeInventoryUpdatedAt || device.logFetch?.updatedAt || device.lastUpdatedAt)
    : "Never";
}

function getDeviceConnectionHistory(device) {
  return (device.activity || [])
    .filter((item) => /connect|disconnect|pair|locked|cleared|offline/i.test(`${item.text || ""} ${item.label || ""}`))
    .slice(0, 8);
}

function renderInventorySerialDetailsCard(snapshot, device) {
  const recipeCount = Array.isArray(device.availableRecipeNames) ? device.availableRecipeNames.length : 0;
  const firmware = device.telemetry?.firmwareVersion || "Unknown";
  const logsStatus = getDeviceLogsStatusLabel(device);
  return `
    <div class="settings-card inventory-serial-card">
      <div class="row space inventory-serial-heading">
        <div>
          <div class="mini-title">Inventory &amp; Serial Details</div>
          <p class="subtle">Compact device identity, recipe inventory, and service actions.</p>
        </div>
        <span class="status-chip ${logsStatus === "Error" ? "danger" : ""}">${escapeHtml(logsStatus)}</span>
      </div>
      <div class="inventory-serial-grid">
        <button class="inventory-metric-button" type="button" data-action="open-device-recipes" data-slot="${device.slot}">
          <small>Recipes on device</small>
          <strong>${escapeHtml(recipeCount)}</strong>
        </button>
        <span>
          <small>Last sync</small>
          <strong>${escapeHtml(getDeviceLastSyncLabel(device))}</strong>
        </span>
        <span>
          <small>MAC ID</small>
          <strong>${escapeHtml(getDeviceMacLabel(device))}</strong>
        </span>
        <span>
          <small>Logs status</small>
          <strong>${escapeHtml(logsStatus)}</strong>
        </span>
      </div>
      <div class="inventory-serial-firmware">
        <span>Firmware</span>
        <strong>${escapeHtml(firmware)}</strong>
      </div>
      <div class="action-row">
        <button class="secondary-button" type="button" data-action="open-device-metadata" data-slot="${device.slot}">Details</button>
        <button class="secondary-button" type="button" data-action="request-firmware" data-slot="${device.slot}">Firmware</button>
        <button class="secondary-button" type="button" data-action="open-stored-logs" data-slot="${device.slot}">Logs</button>
      </div>
    </div>
  `;
}

function renderDeviceMetadataModal(snapshot, device) {
  const history = getDeviceConnectionHistory(device);
  const metadata = [
    ["Device number", `D${device.slot}`],
    ["MAC ID", getDeviceMacLabel(device)],
    ["Firmware", device.telemetry?.firmwareVersion || "Unknown"],
    ["Hardware version", getDeviceHardwareLabel(device)],
    ["Assigned user", getDeviceAssignedUserLabel(snapshot, device)],
    ["Health status", getDeviceHealthLabel(device)]
  ];
  return `
    <div class="modal-backdrop">
      <div class="modal-card wide refined-mobile-screen device-detail-screen device-metadata-modal">
        ${renderRefinedScreenTopBar(snapshot, "Device Metadata", `${device.displayName} | D${device.slot}`)}
        <div class="refined-title-row">
          <button class="icon-button refined-back-button" data-action="return-device-sheet" data-slot="${device.slot}" aria-label="Back">${renderUiIcon("chevronLeft")}</button>
          <div>
            <div class="eyebrow">Full metadata</div>
            <h3>${escapeHtml(device.displayName)}</h3>
          </div>
          <button class="icon-button" type="button" data-action="request-status" data-slot="${device.slot}" aria-label="Refresh status">${renderUiIcon("refresh")}</button>
        </div>
        <div class="settings-card">
          <div class="metadata-grid">
            ${metadata
              .map(
                ([label, value]) => `
                  <span>
                    <small>${escapeHtml(label)}</small>
                    <strong>${escapeHtml(value)}</strong>
                  </span>
                `
              )
              .join("")}
          </div>
        </div>
        <div class="settings-card">
          <div class="mini-title">Connection history</div>
          <div class="connection-history-list">
            ${
              history.length
                ? history
                    .map(
                      (item) => `
                        <div class="connection-history-row">
                          <span>${escapeHtml(item.text || "Device event")}</span>
                          <small>${escapeHtml(formatTimestamp(item.at))}</small>
                        </div>
                      `
                    )
                    .join("")
                : `<div class="empty-card">No connection history has been retained yet.</div>`
            }
          </div>
        </div>
        <div class="action-row">
          <button class="secondary-button" type="button" data-action="return-device-sheet" data-slot="${device.slot}">Back to Device Details</button>
          <button class="secondary-button" type="button" data-action="open-device-recipes" data-slot="${device.slot}">Recipes</button>
          <button class="secondary-button" type="button" data-action="request-firmware" data-slot="${device.slot}">Check Firmware</button>
        </div>
      </div>
    </div>
  `;
}

function renderDeviceCommandNotice(device, commandName, connectedText, disconnectedText) {
  const connected = device.connection === "connected";
  return `
    <div class="settings-card command-state-card ${connected ? "ready" : "blocked"}">
      <strong>${escapeHtml(connected ? connectedText : "Please connect the device")}</strong>
      <p class="subtle">${escapeHtml(connected ? `${commandName} can be sent now.` : disconnectedText)}</p>
    </div>
  `;
}

function getDeviceLiveLogSnapshot(device) {
  const entries = device.connection === "connected" && device.liveLog?.active && Array.isArray(device.liveLog?.entries)
    ? device.liveLog.entries.map((entry) => normalizeLiveLogEntry(device, entry))
    : [];
  const latestRecipe = getNewestLiveLogEntry(entries, (entry) => entry.direction !== "tx" && entry.modeType === "recipe");
  const latestManual = getNewestLiveLogEntry(entries, (entry) => entry.direction !== "tx" && entry.modeType === "manual");
  const latestSensor = getNewestLiveLogEntry(entries, (entry) => entry.direction !== "tx" && entry.modeType === "sensor");
  return {
    entries,
    latestRecipe,
    latestManual,
    latestSensor,
    latest: latestRecipe || latestManual || latestSensor || getNewestLiveLogEntry(entries, (entry) => entry.direction !== "tx")
  };
}

function renderDeviceStatusModal(snapshot, device) {
  const telemetry = getManualDisplayTelemetry(snapshot, device);
  const liveSnapshot = getDeviceLiveLogSnapshot(device);
  const live = liveSnapshot.latest;
  const liveSensor = liveSnapshot.latestSensor?.sensor || {};
  const liveMode = live?.modeType === "manual" ? "Manual Mode" : live?.modeType === "recipe" ? "Recipe Cooking" : telemetry.mode || "Unknown";
  const statusRows = [
    ["Connection", device.connection || "unknown"],
    ["Work status", telemetry.workStatus || live?.status || "Unknown"],
    ["Mode", liveMode],
    ["Recipe", live?.recipeName || telemetry.currentRecipe || device.activeRun?.displayName || "None"],
    ["Step", live?.stepNo ? `${live.stepNo}${live.totalSteps ? ` / ${live.totalSteps}` : ""}` : telemetry.stepNo ? String(telemetry.stepNo) : "0"],
    ["Remaining", live?.timeLeft && live.timeLeft !== "-" ? live.timeLeft : secondsLabel(telemetry.remainingSeconds || 0)],
    ["Ingredients index", telemetry.ingredientsIndex ? String(telemetry.ingredientsIndex) : "0"],
    ["Induction", live?.induction || live?.inductionState ? `${live.inductionState || ""}${live.induction ? ` | ${live.induction}` : ""}${live.inductionRun ? ` | ${live.inductionRun}` : ""}` : `${telemetry.inductionStatus || "IDLE"}${telemetry.indPower ? ` | ${telemetry.indPower}%` : ""}`],
    ["Microwave", live?.microwave || live?.microwaveState ? `${live.microwaveState || ""}${live.microwave ? ` | ${live.microwave}` : ""}${live.microwaveRun ? ` | ${live.microwaveRun}` : ""}` : `${telemetry.magnetronStatus || "IDLE"}${telemetry.magPower ? ` | ${telemetry.magPower}%` : ""}`],
    ["Stirrer", live?.stirrer || telemetry.stirrer || DEFAULT_STIRRER_LEVEL],
    ["Pump / water", live?.pump || (telemetry.pumpOn ? "ON" : "OFF")],
    ["Pan / glass temp", liveSensor.panTemp || liveSensor.glassTemp ? `Pan ${liveSensor.panTemp || "-"} | Glass ${liveSensor.glassTemp || "-"}` : "No sensor packet"],
    ["Voltage / current", liveSensor.indVoltage || liveSensor.indCurrent ? `${liveSensor.indVoltage || "-"} V | ${liveSensor.indCurrent || "-"} I` : live?.currentVoltage || "-"],
    ["Paused", telemetry.paused ? "Yes" : "No"],
    ["Last raw status", live?.message || telemetry.lastRaw || "No raw status received yet"],
    ["Last message", device.lastMessage || "No status message yet"],
    ["Last updated", device.lastUpdatedAt ? formatTimestamp(device.lastUpdatedAt) : "Never"]
  ];
  return `
    <div class="modal-backdrop">
      <div class="modal-card wide refined-mobile-screen device-detail-screen device-status-modal">
        ${renderRefinedScreenTopBar(snapshot, "Device Status", `${device.displayName} | D${device.slot}`)}
        <div class="refined-title-row">
          <button class="icon-button refined-back-button" data-action="return-device-sheet" data-slot="${device.slot}" aria-label="Back">${renderUiIcon("chevronLeft")}</button>
          <div>
            <div class="eyebrow">STATUS=?</div>
            <h3>${escapeHtml(device.displayName)}</h3>
          </div>
          <button class="icon-button" type="button" data-action="request-status" data-slot="${device.slot}" aria-label="Refresh status">${renderUiIcon("refresh")}</button>
        </div>
        ${renderDeviceCommandNotice(device, "STATUS=?", "Ready to request live device status", live ? "Cannot refresh live status at present. Last captured live values are shown below." : "Cannot show live status at present. Last saved values are shown below.")}
        <div class="settings-card">
          <div class="mini-title">Current status snapshot</div>
          <div class="metadata-grid status-grid">
            ${statusRows
              .map(
                ([label, value]) => `
                  <span>
                    <small>${escapeHtml(label)}</small>
                    <strong>${escapeHtml(value)}</strong>
                  </span>
                `
              )
              .join("")}
          </div>
        </div>
        <div class="settings-card">
          <div class="mini-title">Recent device messages</div>
          <div class="activity-list compact">
            ${
              (device.activity || []).length
                ? (device.activity || [])
                    .slice(0, 10)
                    .map(
                      (item) => `
                        <div class="activity-row ${escapeHtml(item.tone || "info")}">
                          <div class="activity-copy">
                            <span class="activity-badge ${escapeHtml(item.direction || item.tone || "info")}">${escapeHtml(item.label || item.tone || "log")}</span>
                            <span>${escapeHtml(item.text)}</span>
                          </div>
                          <span class="subtle">${escapeHtml(formatTimestamp(item.at))}</span>
                        </div>
                      `
                    )
                    .join("")
                : `<div class="empty-card">No device status messages have been retained yet.</div>`
            }
          </div>
        </div>
        <div class="action-row">
          ${
            device.connection === "connected"
              ? `<button class="primary-button" type="button" data-action="request-status" data-slot="${device.slot}">Refresh Status</button>`
              : `<button class="primary-button" type="button" data-action="connect-device" data-slot="${device.slot}">Connect Device</button>`
          }
          <button class="secondary-button" type="button" data-action="return-device-sheet" data-slot="${device.slot}">Back to Device Details</button>
        </div>
      </div>
    </div>
  `;
}

function renderDeviceFirmwareModal(snapshot, device) {
  const firmwareRows = [
    ["Firmware version", device.telemetry?.firmwareVersion || "Unknown"],
    ["Firmware command", "Firmware=?"],
    ["Connection", device.connection || "unknown"],
    ["Hardware version", getDeviceHardwareLabel(device)],
    ["Device number", `D${device.slot}`],
    ["Display name", device.displayName || "Unnamed device"],
    ["Bluetooth name", device.bluetoothName || "Not paired yet"],
    ["MAC ID", getDeviceMacLabel(device)],
    ["Browser device ID", device.browserDeviceId || "Not assigned"],
    ["Last updated", device.lastUpdatedAt ? formatTimestamp(device.lastUpdatedAt) : "Never"],
    ["Last message", device.lastMessage || "No firmware message yet"]
  ];
  return `
    <div class="modal-backdrop">
      <div class="modal-card wide refined-mobile-screen device-detail-screen device-firmware-modal">
        ${renderRefinedScreenTopBar(snapshot, "Firmware", `${device.displayName} | D${device.slot}`)}
        <div class="refined-title-row">
          <button class="icon-button refined-back-button" data-action="return-device-sheet" data-slot="${device.slot}" aria-label="Back">${renderUiIcon("chevronLeft")}</button>
          <div>
            <div class="eyebrow">Firmware command</div>
            <h3>${escapeHtml(device.displayName)}</h3>
          </div>
          <button class="icon-button" type="button" data-action="request-firmware" data-slot="${device.slot}" aria-label="Check firmware">${renderUiIcon("refresh")}</button>
        </div>
        ${renderDeviceCommandNotice(device, "Firmware=?", "Ready to request firmware version", "Cannot read firmware at present. Connect the device first; saved values are shown below.")}
        ${renderFirmwareUpdateNotice(device, { always: true })}
        <div class="settings-card firmware-summary-card">
          <div>
            <span class="subtle">Current firmware</span>
            <strong>${escapeHtml(device.telemetry?.firmwareVersion || "Unknown")}</strong>
          </div>
          <p class="subtle">When connected, this screen sends Firmware=? and updates as soon as the device replies.</p>
        </div>
        <div class="settings-card">
          <div class="metadata-grid firmware-grid">
            ${firmwareRows
              .map(
                ([label, value]) => `
                  <span>
                    <small>${escapeHtml(label)}</small>
                    <strong>${escapeHtml(value)}</strong>
                  </span>
                `
              )
              .join("")}
          </div>
        </div>
        <div class="action-row">
          ${
            device.connection === "connected"
              ? `<button class="primary-button" type="button" data-action="request-firmware" data-slot="${device.slot}">Check Firmware</button>`
              : `<button class="primary-button" type="button" data-action="connect-device" data-slot="${device.slot}">Connect Device</button>`
          }
          <button class="secondary-button" type="button" data-action="return-device-sheet" data-slot="${device.slot}">Back to Device Details</button>
        </div>
      </div>
    </div>
  `;
}

function renderStoredLogsModal(snapshot, device) {
  return `
    <div class="modal-backdrop">
      <div class="modal-card wide refined-mobile-screen device-detail-screen stored-logs-screen">
        ${renderRefinedScreenTopBar(snapshot, "Historical Logs", `${device.displayName} | D${device.slot}`)}
        <div class="refined-title-row">
          <button class="icon-button refined-back-button" data-action="return-device-sheet" data-slot="${device.slot}" aria-label="Back">${renderUiIcon("chevronLeft")}</button>
          <div>
            <div class="eyebrow">Stored historical logs</div>
            <h3>${escapeHtml(device.displayName)}</h3>
          </div>
          <button class="icon-button" type="button" data-action="list-logs" data-slot="${device.slot}" aria-label="Refresh logs">${renderUiIcon("refresh")}</button>
        </div>
        ${renderFirmwareLogPanel(device)}
      </div>
    </div>
  `;
}

function getRecipeVersionLabel(recipe) {
  return (
    recipe?.version ||
    recipe?.recipeJson?.version ||
    recipe?.recipeJson?.Version ||
    recipe?.recipeJson?.recipe_version ||
    recipe?.recipeJson?.recipeVersion ||
    "Unknown"
  );
}

function getRecipeAliasLabel(recipe, fallbackName = "") {
  if (!recipe) return "Device memory";
  const fallbackKey = normalizeRecipeNameKey(fallbackName || recipe.firmwareName || recipe.displayName);
  const alias =
    (recipe.aliases || []).find((item) => normalizeRecipeNameKey(item) && normalizeRecipeNameKey(item) !== fallbackKey) ||
    recipe.zipName ||
    recipe.firmwareName ||
    "";
  return alias || "No alias";
}

function recipeNameMatches(value, recipeName, recipe = null) {
  const key = normalizeRecipeNameKey(value);
  if (!key) return false;
  const candidates = [
    recipeName,
    recipe?.firmwareName,
    recipe?.displayName,
    recipe?.zipName,
    ...(recipe?.aliases || [])
  ].map((item) => normalizeRecipeNameKey(item));
  return candidates.includes(key);
}

function getDeviceRecipeLastCookedAt(snapshot, device, recipeName, recipe = null) {
  const matchesRun = (run) =>
    run && (
      recipeNameMatches(run.firmwareName, recipeName, recipe) ||
      recipeNameMatches(run.displayName, recipeName, recipe)
    );
  if (matchesRun(device.lastRun) && device.lastRun.finishedAt) return device.lastRun.finishedAt;
  const order = (snapshot.orders.previous || []).find((item) => {
    if (!(item.assignedSlot === device.slot || item.targetSlot === device.slot || device.historyOrderIds.includes(item.id))) {
      return false;
    }
    return (
      recipeNameMatches(item.currentRunFirmwareName, recipeName, recipe) ||
      recipeNameMatches(item.recipeLookup, recipeName, recipe) ||
      recipeNameMatches(item.itemName, recipeName, recipe)
    );
  });
  return order?.finishedAt || order?.createdAt || "";
}

function getDeviceRecipeStorageTags(recipe) {
  if (!recipe) {
    return [
      { label: "On device", tone: "device" },
      { label: "Device only", tone: "warning" }
    ];
  }
  if (recipe.cloudRecordId || recipe.cloudUserId) {
    return [
      { label: "On device", tone: "device" },
      { label: "Synced", tone: "success" }
    ];
  }
  return [
    { label: "On device", tone: "device" },
    { label: "Missing cloud copy", tone: "danger" }
  ];
}

function getDeviceRecipeStorageKind(recipe) {
  if (!recipe) return "device-only";
  if (recipe.cloudRecordId || recipe.cloudUserId) return "cloud-synced";
  return "missing-cloud";
}

function getDeviceRecipeRows(snapshot, device) {
  const names = new Map();
  [...(device.availableRecipeNames || []), ...(device.syncedRecipeNames || [])].forEach((name) => addKnownRecipeName(names, name));
  return [...names.values()]
    .sort((left, right) => left.localeCompare(right))
    .map((recipeName) => {
      const recipe = findRecipeByFirmwareName(snapshot, recipeName);
      const lastCookedAt = getDeviceRecipeLastCookedAt(snapshot, device, recipeName, recipe);
      const storageKind = getDeviceRecipeStorageKind(recipe);
      return {
        id: normalizeRecipeNameKey(recipeName).replace(/[^a-z0-9_-]+/g, "-") || safeRandomId("device-recipe"),
        recipeName,
        recipe,
        alias: getRecipeAliasLabel(recipe, recipeName),
        version: getRecipeVersionLabel(recipe),
        lastCookedAt,
        storageKind,
        tags: getDeviceRecipeStorageTags(recipe)
      };
    });
}

function filterDeviceRecipeRows(rows, modal) {
  const query = String(modal.payload?.query || "").trim().toLowerCase();
  const filter = String(modal.payload?.filter || "all");
  return rows.filter((row) => {
    if (query) {
      const haystack = [row.recipeName, row.recipe?.displayName, row.recipe?.firmwareName, row.alias, row.version]
        .join(" ")
        .toLowerCase();
      if (!haystack.includes(query)) return false;
    }
    if (filter === "recent") return Boolean(row.lastCookedAt);
    if (filter === "missing-cloud") return row.storageKind === "missing-cloud" || row.storageKind === "device-only";
    if (filter === "cloud-synced") return row.storageKind === "cloud-synced";
    if (filter === "device-only") return row.storageKind === "device-only";
    return true;
  });
}

function getDeviceOnlyOrderRecipeName(order) {
  return String(order?.deviceOnlyRecipeName || order?.currentRunFirmwareName || order?.recipeLookup || order?.itemName || "").trim();
}

function createDeviceMemoryOrder(snapshot, recipeName, slot, status = "queued", recipe = null) {
  const displayName = recipe?.displayName || recipeName;
  return decorateOrderRecord(
    {
      id: safeRandomId("device-memory"),
      orderId: `#D${Date.now().toString().slice(-5)}`,
      itemName: displayName,
      recipeLookup: displayName,
      quantity: "1 batch",
      source: "Device Memory",
      specialInstructions: recipe ? "Queued from Recipes on Device" : "Device-only recipe queued from cooker memory",
      accentColor: "#f47b20",
      createdAt: nowIso(),
      status,
      assignedSlot: Number(slot),
      assignedMode: "device",
      activeRecipeId: recipe?.id || "",
      currentRunRecipeName: displayName,
      currentRunFirmwareName: recipe?.firmwareName || recipeName,
      deviceOnlyRecipeName: recipe?.id ? "" : recipeName,
      targetSlot: Number(slot),
      manual: true,
      historyNote: recipe ? "Queued from Recipes on Device" : "Queued as device-only recipe"
    },
    recipe,
    snapshot.orders.current.length
  );
}

function isDeviceRecipeCurrentlyRunning(device, recipeName) {
  return [
    device.telemetry?.currentRecipe,
    device.activeRun?.firmwareName,
    device.activeRun?.displayName
  ].some((item) => recipeNameMatches(item, recipeName));
}

function getQueuedOrdersForDeviceRecipe(snapshot, device, recipeName, recipe = null) {
  return getQueueOrders(snapshot, device).filter(
    (order) =>
      recipeNameMatches(order.currentRunFirmwareName, recipeName, recipe) ||
      recipeNameMatches(order.recipeLookup, recipeName, recipe) ||
      recipeNameMatches(order.itemName, recipeName, recipe) ||
      recipeNameMatches(order.deviceOnlyRecipeName, recipeName, recipe)
  );
}

async function startDeviceOnlyOrderFlow(orderId, preferredSlot, recipeName, options = {}) {
  const snapshot = state();
  const device = snapshot.devices.find((item) => item.slot === Number(preferredSlot));
  const order = snapshot.orders.current.find((item) => item.id === orderId);
  const safeName = String(recipeName || getDeviceOnlyOrderRecipeName(order)).trim();
  if (!device || !order || !safeName) return "missing-device-recipe";
  if (device.connection !== "connected") {
    showToast(`Device ${preferredSlot} is offline. Connect it before starting ${safeName}.`, "warning");
    return "no-device";
  }
  if (isFirmwareBlockingDevice(device)) {
    showToast(firmwareBlockMessage(device), "warning");
    return "firmware-required";
  }
  const liveSession = ble.getSession(device.slot);
  if (liveSession?.transfer) {
    showToast(`Device ${device.slot} is still syncing recipes. Try again in a moment.`, "warning");
    return "transfer-busy";
  }
  const busy =
    device.currentJobId ||
    (!options.ignoreQueuedWork && device.queueOrderIds.length > 0) ||
    device.completionConfirmationPending ||
    hasLiveRuntime(device);
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
      draftOrder.assignedMode = "device";
      draftOrder.targetSlot = device.slot;
      draftOrder.currentRunFirmwareName = safeName;
      draftOrder.deviceOnlyRecipeName = safeName;
      appendActivity(draftDevice, `${safeName} queued from device memory`, "info");
      pushDraftNotification(draft, {
        type: "order",
        title: `Order assigned to D${draftDevice.slot}`,
        deviceSlot: draftDevice.slot,
        recipeName: safeName,
        orderId: draftOrder.orderId || "",
        message: "Device-memory recipe was queued.",
        action: { type: "device", label: "Open device", slot: draftDevice.slot }
      });
    });
    showToast(`${safeName} queued on Device ${device.slot}`, "info");
    return "queued";
  }

  const runStartedAt = nowIso();
  mutate((draft) => {
    const draftOrder = draft.orders.current.find((item) => item.id === orderId);
    const draftDevice = draft.devices.find((item) => item.slot === device.slot);
    if (!draftOrder || !draftDevice) return draft;
    clearOrderFromDeviceAssignments(draft, orderId, device.slot);
    draftOrder.status = "starting";
    draftOrder.assignedSlot = device.slot;
    draftOrder.assignedMode = "device";
    draftOrder.targetSlot = device.slot;
    draftOrder.currentRunRecipeName = safeName;
    draftOrder.currentRunFirmwareName = safeName;
    draftOrder.deviceOnlyRecipeName = safeName;
    draftDevice.currentJobId = orderId;
    draftDevice.activeRun = {
      orderId,
      recipeId: "",
      displayName: safeName,
      firmwareName: safeName,
      startedAt: runStartedAt,
      durationSeconds: 0
    };
    draftDevice.startupGuardUntil = new Date(Date.now() + 8000).toISOString();
    draftDevice.telemetry.currentRecipe = safeName;
    appendActivity(draftDevice, `Preparing device-only recipe ${safeName}`, "info");
    pushDraftNotification(draft, {
      type: "cooking",
      title: "Recipe started",
      deviceSlot: draftDevice.slot,
      recipeName: safeName,
      orderId: draftOrder.orderId || "",
      message: `${safeName} is being started from device memory.`,
      action: { type: "device", label: "Open device", slot: draftDevice.slot }
    });
  });

  try {
    const idleBeforeStart = await ble.waitForIdleStatus(device.slot, {
      timeoutMs: 3200,
      pollEveryMs: 650,
      forceFresh: true,
      description: "idle status before device-only recipe start"
    });
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === device.slot);
      if (!draftDevice) return draft;
      appendFlowActivity(draftDevice, "Idle confirmed before device-only recipe start", "info", idleBeforeStart.at);
    });
    await ble.runRecipe(device.slot, safeName, {
      autoStartAfterIngredient: false,
      statusDelayMs: 650,
      fallbackMs: 1800
    });
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === device.slot);
      const draftOrder = draft.orders.current.find((item) => item.id === orderId);
      if (!draftDevice || !draftOrder) return draft;
      draftOrder.status = "starting";
      draftDevice.telemetry.workStatus = "starting";
      draftDevice.telemetry.currentRecipe = safeName;
      draftDevice.lastMessage = `recipe=${safeName}`;
      draftDevice.lastUpdatedAt = nowIso();
      appendFlowActivity(draftDevice, `Device-only run command sent for ${safeName}`, "success");
    });
    return "started";
  } catch (error) {
    mutate((draft) => {
      const draftOrder = draft.orders.current.find((item) => item.id === orderId);
      const draftDevice = resetDeviceRuntimeState(draft, device.slot, { releaseOrders: false });
      if (draftOrder) {
        draftOrder.status = "pending";
        draftOrder.assignedSlot = null;
        draftOrder.assignedMode = "device";
      }
      if (draftDevice) {
        appendActivity(draftDevice, `Device-only start failed: ${error.message}`, "error");
        pushDraftNotification(draft, {
          type: "error",
          title: "Recipe missing",
          deviceSlot: draftDevice.slot,
          recipeName: safeName,
          orderId: draftOrder?.orderId || "",
          message: error.message,
          action: { type: "device", label: "Open device", slot: draftDevice.slot }
        });
      }
    });
    showToast(error.message, "error");
    return "failed";
  }
}

async function runDeviceStoredRecipe(slot, recipeName) {
  const snapshot = state();
  const device = snapshot.devices.find((item) => item.slot === Number(slot));
  const recipe = findRecipeByFirmwareName(snapshot, recipeName);
  if (!device || device.connection !== "connected") {
    showToast(`Device ${slot} is offline. Connect it before running ${recipeName}.`, "warning");
    return;
  }
  if (isFirmwareBlockingDevice(device)) {
    showToast(firmwareBlockMessage(device), "warning");
    return;
  }
  if (isDeviceActivelyCooking(device)) {
    showToast(`Device ${slot} is cooking. Use Queue for ${recipeName}, or stop the current recipe first.`, "warning");
    return;
  }
  if (recipe) {
    mutate((draft) => {
      allowRecipeOnDevice(draft, slot, recipe.id);
    });
    await runDeviceRecipe(slot, recipe.id);
    return;
  }
  const order = createDeviceMemoryOrder(snapshot, recipeName, slot, "pending", null);
  mutate((draft) => {
    draft.orders.current.unshift(order);
  });
  await startDeviceOnlyOrderFlow(order.id, Number(slot), recipeName, { ignoreQueuedWork: true });
}

function queueDeviceStoredRecipe(slot, recipeName) {
  const snapshot = state();
  const device = snapshot.devices.find((item) => item.slot === Number(slot));
  if (!device) return;
  if (!canUseDeviceForRecipeActions(device)) {
    showToast(`Device ${slot} is offline. Connect it before queuing ${recipeName}.`, "warning");
    return;
  }
  const recipe = findRecipeByFirmwareName(snapshot, recipeName);
  const order = createDeviceMemoryOrder(snapshot, recipeName, slot, "queued", recipe);
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    if (recipe) {
      allowRecipeOnDevice(draft, slot, recipe.id);
    }
    draft.orders.current.push(order);
    draftDevice.queueOrderIds = [...getDeviceQueuedOrderIds(draft, draftDevice), order.id];
    appendActivity(draftDevice, `${order.itemName} queued from Recipes on Device`, "info");
  });
  showToast(`${order.itemName} queued on Device ${slot}`, "success");
  queueIdleWork();
}

async function uploadSingleRecipeToDevice(slot, recipe, options = {}) {
  if (!recipe) throw new Error("Recipe record is required.");
  const device = getDevice(slot);
  if (!device || device.connection !== "connected") {
    throw new Error(`Device ${slot} is not connected.`);
  }
  ensureDeviceCommandAllowed(device, "Recipe upload");
  if (isDeviceRecipeCurrentlyRunning(device, recipe.firmwareName)) {
    throw new Error(`Cannot replace ${recipe.firmwareName} while it is cooking.`);
  }
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    setUploadPlan(draftDevice, [recipe], []);
  });
  await ble.syncRecipes(Number(slot), [recipe], (progress) => {
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      draftDevice.lastMessage = `Uploading ${progress.recipeName} (${progress.current}/${progress.total})`;
      draftDevice.lastUpdatedAt = nowIso();
    });
  }, {
    overwriteExisting: options.overwriteExisting === true
  });
  mutate((draft) => {
    const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
    if (!draftDevice) return draft;
    mergeRecipeNames(draftDevice, [recipe.firmwareName]);
    draftDevice.syncedRecipeNames = Array.from(new Set([...(draftDevice.syncedRecipeNames || []), recipe.firmwareName]));
    draftDevice.syncedRecipeSignatures[normalizeRecipeNameKey(recipe.firmwareName)] = getRecipeSignature(recipe);
    completeUploadPlan(draftDevice, [recipe.firmwareName]);
    appendActivity(draftDevice, `${recipe.firmwareName} uploaded to device`, "success");
  });
  await refreshDeviceRecipeInventory(slot, { force: true, timeoutMs: 4500 }).catch(() => []);
}

async function updateDeviceRecipe(slot, recipeName) {
  const snapshot = state();
  const device = snapshot.devices.find((item) => item.slot === Number(slot));
  const recipe = findRecipeByFirmwareName(snapshot, recipeName);
  if (!recipe) {
    showToast(`${recipeName} has no local/cloud recipe JSON to upload.`, "warning");
    return;
  }
  if (!device || device.connection !== "connected") {
    showToast(`Connect Device ${slot} before updating ${recipe.displayName}.`, "warning");
    return;
  }
  if (isDeviceRecipeCurrentlyRunning(device, recipeName)) {
    showToast(`Cannot update ${recipe.displayName} while it is cooking.`, "warning");
    return;
  }
  const queued = getQueuedOrdersForDeviceRecipe(snapshot, device, recipeName, recipe);
  if (queued.length) {
    showToast(`${recipe.displayName} is queued. Updating will replace the file used by that queued job.`, "warning");
  }
  await uploadSingleRecipeToDevice(slot, recipe, { overwriteExisting: true });
  showToast(`${recipe.displayName} replaced on Device ${slot}`, "success");
  openModal("device-recipes", { slot: Number(slot), query: "", filter: "all", selectedNames: [] });
}

async function deleteDeviceRecipes(slot, recipeNames) {
  const snapshot = state();
  const device = snapshot.devices.find((item) => item.slot === Number(slot));
  const names = Array.from(new Set((recipeNames || []).map((name) => String(name || "").trim()).filter(Boolean)));
  if (!device || device.connection !== "connected") {
    showToast(`Connect Device ${slot} before deleting recipes.`, "warning");
    return;
  }
  const running = names.find((name) => isDeviceRecipeCurrentlyRunning(device, name));
  if (running) {
    showToast(`Cannot delete ${running} while it is cooking.`, "warning");
    openModal("device-recipes", { slot: Number(slot), query: "", filter: "all", selectedNames: [] });
    return;
  }
  for (const name of names) {
    await ble.deleteRecipe(Number(slot), name);
    await waitMs(350);
    mutate((draft) => {
      const draftDevice = draft.devices.find((item) => item.slot === Number(slot));
      if (!draftDevice) return draft;
      const key = normalizeRecipeNameKey(name);
      draftDevice.availableRecipeNames = (draftDevice.availableRecipeNames || []).filter((item) => normalizeRecipeNameKey(item) !== key);
      draftDevice.syncedRecipeNames = (draftDevice.syncedRecipeNames || []).filter((item) => normalizeRecipeNameKey(item) !== key);
      delete draftDevice.syncedRecipeSignatures[key];
      draftDevice.lastUpdatedAt = nowIso();
      draftDevice.lastMessage = `DELETE=${name}`;
      appendActivity(draftDevice, `${name} delete command sent`, "warning");
    });
  }
  await refreshDeviceRecipeInventory(slot, { force: true, timeoutMs: 4500 }).catch(() => []);
  showToast(`${names.length} recipe${names.length === 1 ? "" : "s"} deleted from Device ${slot}`, "success");
  openModal("device-recipes", { slot: Number(slot), query: "", filter: "all", selectedNames: [] });
}

function requestDeviceRecipeDelete(slot, recipeNames) {
  const snapshot = state();
  const device = snapshot.devices.find((item) => item.slot === Number(slot));
  const names = Array.from(new Set((recipeNames || []).map((name) => String(name || "").trim()).filter(Boolean)));
  if (!device || names.length === 0) {
    showToast("Select at least one device recipe to delete.", "warning");
    return;
  }
  const running = names.find((name) => isDeviceRecipeCurrentlyRunning(device, name));
  if (running) {
    showToast(`Cannot delete ${running} while it is cooking.`, "warning");
    return;
  }
  const queuedNames = names.filter((name) => getQueuedOrdersForDeviceRecipe(snapshot, device, name, findRecipeByFirmwareName(snapshot, name)).length);
  openModal("delete-device-recipes-confirm", {
    slot: Number(slot),
    names,
    queuedNames
  });
}

function renderDeviceRecipeRow(row, device, selected) {
  const hasCloudRecipe = Boolean(row.recipe);
  const isRunning = isDeviceRecipeCurrentlyRunning(device, row.recipeName);
  const connected = canUseDeviceForRecipeActions(device);
  const busy = isDeviceActivelyCooking(device);
  return `
    <article class="device-recipe-row ${selected ? "selected" : ""} ${isRunning ? "running" : ""}">
      <button class="device-recipe-select" type="button" data-action="toggle-device-recipe-selected" data-slot="${device.slot}" data-recipe-name="${escapeHtml(row.recipeName)}" aria-label="Select ${escapeHtml(row.recipeName)}">
        ${selected ? "&#10003;" : ""}
      </button>
      <div class="device-recipe-main">
        <div class="row space">
          <div>
            <strong>${escapeHtml(row.recipe?.displayName || row.recipeName)}</strong>
            <small>${escapeHtml(row.recipeName)}</small>
          </div>
          ${isRunning ? `<span class="queue-tag live">Cooking</span>` : ""}
        </div>
        <div class="device-recipe-meta">
          <span>Alias/code <b>${escapeHtml(row.alias)}</b></span>
          <span>Version <b>${escapeHtml(row.version)}</b></span>
          <span>Last cooked <b>${escapeHtml(row.lastCookedAt ? formatTimestamp(row.lastCookedAt) : "Not known")}</b></span>
        </div>
        <div class="device-recipe-tags">
          ${row.tags.map((tag) => `<span class="device-recipe-status ${escapeHtml(tag.tone)}">${escapeHtml(tag.label)}</span>`).join("")}
        </div>
      </div>
      <div class="device-recipe-actions">
        <button class="primary-button micro" type="button" data-action="device-recipe-run" data-slot="${device.slot}" data-recipe-name="${escapeHtml(row.recipeName)}" ${connected && !busy ? "" : "disabled"}>Run</button>
        <button class="secondary-button micro" type="button" data-action="device-recipe-queue" data-slot="${device.slot}" data-recipe-name="${escapeHtml(row.recipeName)}" ${connected && !isRunning ? "" : "disabled"}>Queue</button>
        <button class="secondary-button micro" type="button" data-action="device-recipe-update" data-slot="${device.slot}" data-recipe-name="${escapeHtml(row.recipeName)}" ${connected && hasCloudRecipe && !isRunning ? "" : "disabled"}>Update/Replace</button>
        <button class="danger-button micro" type="button" data-action="device-recipe-delete-request" data-slot="${device.slot}" data-recipe-name="${escapeHtml(row.recipeName)}" ${connected && !isRunning ? "" : "disabled"}>Delete</button>
      </div>
    </article>
  `;
}

function renderDeviceRecipesModal(snapshot, device, modal) {
  const rows = getDeviceRecipeRows(snapshot, device);
  const filteredRows = filterDeviceRecipeRows(rows, modal);
  const selectedNames = Array.isArray(modal.payload?.selectedNames) ? modal.payload.selectedNames : [];
  const selectedSet = new Set(selectedNames.map((name) => normalizeRecipeNameKey(name)));
  const activeFilter = modal.payload?.filter || "all";
  const filters = [
    ["all", "All"],
    ["recent", "Recently used"],
    ["missing-cloud", "Missing from cloud"],
    ["cloud-synced", "Cloud synced"],
    ["device-only", "Device only"]
  ];
  return `
    <div class="modal-backdrop">
      <div class="modal-card wide refined-mobile-screen device-detail-screen device-recipes-screen">
        ${renderRefinedScreenTopBar(snapshot, "Recipes on Device", `${rows.length} recipe${rows.length === 1 ? "" : "s"}`)}
        <div class="refined-title-row">
          <button class="icon-button refined-back-button" data-action="return-device-sheet" data-slot="${device.slot}" aria-label="Back">${renderUiIcon("chevronLeft")}</button>
          <div>
            <div class="eyebrow">Device recipe memory</div>
            <h3>Recipes on D${device.slot} &middot; ${escapeHtml(device.displayName)}</h3>
            <p class="subtle">${escapeHtml(device.bluetoothName || "Not paired yet")} | ${escapeHtml(device.connection)}</p>
          </div>
          <span class="status-chip">${rows.length} stored</span>
        </div>
        <div class="settings-card device-recipes-toolbar">
          <div class="action-row">
            <button class="secondary-button" type="button" data-action="device-recipes-refresh" data-slot="${device.slot}" ${device.connection === "connected" ? "" : "disabled"}>Refresh from device</button>
            <button class="primary-button" type="button" data-action="device-recipes-add" data-slot="${device.slot}" ${device.connection === "connected" ? "" : "disabled"}>Add recipe</button>
            <button class="danger-button" type="button" data-action="delete-selected-device-recipes" data-slot="${device.slot}" ${selectedNames.length ? "" : "disabled"}>Delete selected</button>
          </div>
          <label class="field-label">
            Search recipes on this device
            <input class="field-input" type="search" data-input="device-recipes-search" data-slot="${device.slot}" value="${escapeHtml(modal.payload?.query || "")}" placeholder="Search recipe name, alias, code, or version">
          </label>
          <div class="chip-row device-recipes-filters">
            ${filters
              .map(
                ([id, label]) => `
                  <button class="chip-button ${activeFilter === id ? "selected" : ""}" type="button" data-action="device-recipes-filter" data-slot="${device.slot}" data-filter="${id}">
                    ${escapeHtml(label)}
                  </button>
                `
              )
              .join("")}
          </div>
        </div>
        <div class="device-recipes-summary">
          <span>Showing ${filteredRows.length} of ${rows.length}</span>
          <span>${selectedNames.length} selected</span>
          <span>Last sync ${escapeHtml(getDeviceLastSyncLabel(device))}</span>
        </div>
        <div class="device-recipes-list">
          ${
            filteredRows.length
              ? filteredRows
                  .map((row) => renderDeviceRecipeRow(row, device, selectedSet.has(normalizeRecipeNameKey(row.recipeName))))
                  .join("")
              : `<div class="empty-card">No recipe files match this search or filter. Refresh from device to read memory again.</div>`
          }
        </div>
      </div>
    </div>
  `;
}

function renderDeleteDeviceRecipesConfirmModal(snapshot, modal) {
  const slot = Number(modal.payload?.slot || 0);
  const device = snapshot.devices.find((item) => item.slot === slot);
  const names = Array.isArray(modal.payload?.names) ? modal.payload.names : [];
  const queuedNames = Array.isArray(modal.payload?.queuedNames) ? modal.payload.queuedNames : [];
  return `
    <div class="modal-backdrop">
      <div class="modal-card delete-device-recipes-modal">
        <div class="row space">
          <div>
            <div class="eyebrow">Confirm delete</div>
            <h3>Delete ${names.length} recipe${names.length === 1 ? "" : "s"} from D${slot}</h3>
          </div>
          <button class="icon-button" data-action="return-device-recipes" data-slot="${slot}">x</button>
        </div>
        <p class="subtle">This sends <code>DELETE=&lt;recipeName&gt;</code> to ${escapeHtml(device?.displayName || `Device ${slot}`)} and then refreshes the device recipe list.</p>
        ${
          queuedNames.length
            ? `<div class="log-status-line error">Warning: ${escapeHtml(queuedNames.join(", "))} ${queuedNames.length === 1 ? "is" : "are"} currently in this device queue. Delete only if you are sure the queued job should not use that file.</div>`
            : ""
        }
        <div class="delete-recipe-name-list">
          ${names.map((name) => `<span>${escapeHtml(name)}</span>`).join("")}
        </div>
        <div class="action-row">
          <button class="secondary-button" type="button" data-action="return-device-recipes" data-slot="${slot}">Cancel</button>
          <button class="danger-button" type="button" data-action="confirm-device-recipes-delete" data-slot="${slot}" data-recipe-names="${escapeHtml(JSON.stringify(names))}">Delete from device</button>
        </div>
      </div>
    </div>
  `;
}

const PRO_POWER_STEPS = Array.from({ length: 21 }, (_, index) => index * 5);
const PRO_MICROWAVE_STEPS = [0, 800];
const PRO_STIRRER_SPEEDS = ["low", "medium", "high", "very-high"];
const PRO_MAX_HOLD_SECONDS = 180;
const PRO_DIET_TYPES = [
  { id: "veg", label: "Veg", icon: "Veg" },
  { id: "non-veg", label: "Non-Veg", icon: "Non" },
  { id: "vegan", label: "Vegan", icon: "Vegan" }
];
const PRO_RECIPE_TYPES = ["gravy", "semi-dry", "dry", "saute", "boil", "fry", "steam"];
const PRO_CONSISTENCIES = [
  { id: "thin", label: "Thin", hint: "Flowing, light" },
  { id: "medium", label: "Medium", hint: "Balanced body" },
  { id: "thick", label: "Thick", hint: "Dense, rich" }
];

function toInt(value, fallback = 0) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function normalizePowerStep(value) {
  const numeric = toInt(value, 0);
  return PRO_POWER_STEPS.reduce((nearest, option) => (Math.abs(option - numeric) < Math.abs(nearest - numeric) ? option : nearest), 0);
}

function normalizeMicrowavePower(value) {
  const numeric = toInt(value, 0);
  if (numeric <= 0) return 0;
  return PRO_MICROWAVE_STEPS.reduce((nearest, option) => (Math.abs(option - numeric) < Math.abs(nearest - numeric) ? option : nearest), 800);
}

function stirrerSpeedFromFirmware(value) {
  const text = String(value || "").toLowerCase();
  if (text.includes("very") || text === "4") return "very-high";
  if (text.includes("high") || text === "3") return "high";
  if (text.includes("low") || text === "1") return "low";
  if (text === "0" || text.includes("off") || text === "false") return "off";
  return "medium";
}

function firmwareStirrerFromSpeed(speed) {
  if (speed === "off") return "0";
  if (speed === "low") return "1";
  if (speed === "high") return "3";
  if (speed === "very-high") return "4";
  return "2";
}

function makeProSubBlock(overrides = {}) {
  const stirrerSpeed = overrides.stirrerSpeed || "medium";
  const stirrerActive = overrides.stirrerActive !== false && stirrerSpeed !== "off";
  return {
    inductionPower: normalizePowerStep(overrides.inductionPower ?? 0),
    microwaveActive: Boolean(overrides.microwaveActive),
    microwavePower: normalizeMicrowavePower(overrides.microwavePower || 800),
    stirrerActive,
    stirrerSpeed: stirrerActive ? stirrerSpeed : "medium",
    stirrerMode: stirrerActive ? "continuous" : "off"
  };
}

function makeProMinute(index, overrides = {}) {
  return {
    id: `min-${index}`,
    minuteIndex: index,
    sourceStepIndex: Number.isFinite(Number(overrides.sourceStepIndex)) ? Number(overrides.sourceStepIndex) : index,
    title: overrides.title || `Minute ${index + 1}`,
    weight: overrides.weight || "",
    lidOpen: Boolean(overrides.lidOpen),
    lidOpenDuration: Math.max(0, Math.min(60, toInt(overrides.lidOpenDuration, overrides.lidOpen ? 60 : 0))),
    subBlocks: overrides.subBlocks || [makeProSubBlock(), makeProSubBlock(), makeProSubBlock(), makeProSubBlock()],
    waterBlocks: overrides.waterBlocks || [0, 0, 0, 0],
    ingredients: overrides.ingredients || []
  };
}

function normalizeRecipeType(value) {
  const key = String(value || "").toLowerCase().replace(/\s+/g, "-");
  if (key.includes("semi")) return "semi-dry";
  if (key.includes("dry")) return "dry";
  if (key.includes("saute") || key.includes("sauté")) return "saute";
  if (key.includes("boil")) return "boil";
  if (key.includes("fry")) return "fry";
  if (key.includes("steam")) return "steam";
  return "gravy";
}

function inferDietType(recipe) {
  const text = [
    recipe?.displayName,
    recipe?.category,
    recipe?.recipeJson?.category,
    recipe?.recipeJson?.description,
    Array.isArray(recipe?.aliases) ? recipe.aliases.join(" ") : ""
  ]
    .join(" ")
    .toLowerCase();
  if (/(chicken|mutton|fish|egg|prawn|meat|keema|non.?veg)/i.test(text)) return "non-veg";
  if (/(vegan|plant.?based)/i.test(text)) return "vegan";
  return "veg";
}

function parseQuantityText(value, fallbackQuantity = 500, fallbackUnit = "g") {
  const text = String(value || "");
  const match = text.match(/(\d+(?:\.\d+)?)\s*(kg|g|gm|gram|grams|ml|l|litre|liter|piece|pcs|number)?/i);
  if (!match) return { quantity: fallbackQuantity, unit: fallbackUnit };
  const rawUnit = String(match[2] || fallbackUnit).toLowerCase();
  const unit = rawUnit === "gm" || rawUnit.startsWith("gram") ? "g" : rawUnit === "litre" || rawUnit === "liter" ? "l" : rawUnit;
  return { quantity: Number(match[1]) || fallbackQuantity, unit };
}

function parseExplicitQuantityText(value, fallbackQuantity = 500, fallbackUnit = "g") {
  const text = String(value || "");
  const match = text.match(/(\d+(?:\.\d+)?)\s*(kg|g|gm|gram|grams|ml|l|litre|liter|piece|pcs|number|nos?)\b/i);
  if (!match) return { quantity: fallbackQuantity, unit: fallbackUnit };
  return parseQuantityText(`${match[1]} ${match[2]}`, fallbackQuantity, fallbackUnit);
}

function findFinalOutputQuantity(recipeJson) {
  const text = [
    recipeJson?.quantity,
    recipeJson?.servings,
    recipeJson?.weight,
    recipeJson?.finalQuantity,
    recipeJson?.description
  ]
    .filter(Boolean)
    .join(" ");
  const outputMatch = text.match(
    /(?:final\s+(?:output|outcome|quantity|weight)|output|batch\s+size)\s*[:=\-]?\s*(\d+(?:\.\d+)?)\s*(kg|g|gm|gram|grams|ml|l|litre|liter|piece|pcs|number|nos?)\b/i
  );
  if (outputMatch) return parseQuantityText(`${outputMatch[1]} ${outputMatch[2]}`, 500, "g");
  const direct = [recipeJson?.quantity, recipeJson?.finalQuantity, recipeJson?.weight, recipeJson?.servings].find(Boolean);
  return parseExplicitQuantityText(direct, 500, "g");
}

function inferRecipeQuantity(recipeJson) {
  return findFinalOutputQuantity(recipeJson);
}

function splitIngredientNameWeight(item, index) {
  const title = item?.title || item?.name || item?.Text || item?.text || item?.Ingredient || item?.ingredient || "";
  const weight = item?.weight || item?.Weight || item?.quantity || item?.Quantity || item?.qty || item?.audioQ || "";
  const parsed = parseQuantityText(weight, 0, item?.unit || item?.Unit || "g");
  return {
    id: item?.id ? String(item.id) : `ing-${index}-${safeRandomId("item")}`,
    name: String(title || `Ingredient ${index + 1}`).trim(),
    quantity: parsed.quantity || 0,
    unit: String(item?.unit || item?.Unit || parsed.unit || "g").trim() || "g",
    source: structuredClone(item || {})
  };
}

function recipeJsonToConfigIngredients(recipeJson) {
  const ingredientSource = Array.isArray(recipeJson?.Ingredients)
    ? recipeJson.Ingredients
    : Array.isArray(recipeJson?.Ingredient)
      ? recipeJson.Ingredient
      : [];
  if (ingredientSource.length) {
    return ingredientSource.slice(0, 40).map(splitIngredientNameWeight);
  }
  const steps = Array.isArray(recipeJson?.Instruction) ? recipeJson.Instruction : [];
  return steps
    .filter((step) => step?.Weight || step?.Text)
    .slice(0, 12)
    .map((step, index) => splitIngredientNameWeight({ title: step.Text, weight: step.Weight }, index));
}

function configIngredientsToFirmware(ingredients) {
  return ingredients
    .filter((ingredient) => ingredient.name)
    .map((ingredient, index) => ({
      ...(ingredient.source || {}),
      id: ingredient.source?.id || index + 1,
      title: ingredient.name,
      name: ingredient.name,
      weight: `${ingredient.quantity || 0} ${ingredient.unit || "g"}`.trim(),
      Weight: `${ingredient.quantity || 0} ${ingredient.unit || "g"}`.trim()
    }));
}

const INGREDIENT_CATEGORY_RULES = [
  { id: "salt", label: "Salt", exponent: 0, pattern: /\b(salt|black salt|sendha)\b/i },
  { id: "sweetness", label: "Sweetness", exponent: 0.95, pattern: /\b(sugar|jaggery|honey|milk maid|condensed milk)\b/i },
  { id: "cooking-fat", label: "Cooking fat", exponent: 0.85, pattern: /\b(oil|ghee|butter|fat)\b/i },
  { id: "cooking-liquid", label: "Cooking liquids", exponent: 1, pattern: /\b(water|stock|milk|dal water|coconut milk|cream)\b/i },
  { id: "acid", label: "Acids / sourness", exponent: 0.75, pattern: /\b(lemon|lime|vinegar|tamarind|amchur|kokum|sour)\b/i },
  { id: "ground-spice", label: "Ground spices", exponent: 0.9, pattern: /\b(powder|masala|turmeric|chilli|chili|coriander powder|cumin powder|pepper)\b/i },
  { id: "whole-spice", label: "Whole spices", exponent: 0.78, pattern: /\b(cumin|mustard|clove|cardamom|cinnamon|bay leaf|peppercorn|seed|hing|dalchini)\b/i },
  { id: "fresh-aromatic", label: "Fresh aromatics", exponent: 0.88, pattern: /\b(coriander|curry leaves|ginger|garlic|chilli|chili|mint|spring onion|herb)\b/i },
  { id: "body-masala", label: "Body / base masala", exponent: 0.95, pattern: /\b(onion|tomato|curd|yogurt|paste|gravy|sauce|vegetable)\b/i },
  { id: "finisher", label: "Finishers", exponent: 0.72, pattern: /\b(kasuri|saffron|garam masala|aroma|finish|garnish)\b/i },
  { id: "main", label: "Main ingredient / base", exponent: 1, pattern: /.*/i }
];

const SALT_INTENSITY_PERCENT = {
  mild: 0.007,
  medium: 0.009,
  rich: 0.011
};
let latestFirmwareManifest = null;
let firmwareManifestLoadPromise = null;

function classifyIngredientCategory(name) {
  const rule = INGREDIENT_CATEGORY_RULES.find((item) => item.pattern.test(String(name || "")));
  return rule || INGREDIENT_CATEGORY_RULES[INGREDIENT_CATEGORY_RULES.length - 1];
}

function normalizeIngredientUnit(unit) {
  const value = String(unit || "g").trim().toLowerCase();
  if (value === "gm" || value.startsWith("gram")) return "g";
  if (value === "litre" || value === "liter") return "l";
  if (value === "nos" || value === "no" || value === "pcs") return "piece";
  return value || "g";
}

function formatScaledNumber(value, unit = "g") {
  const safe = Math.max(0, Number(value) || 0);
  const normalizedUnit = normalizeIngredientUnit(unit);
  if (["piece", "number"].includes(normalizedUnit)) return String(Math.max(1, Math.round(safe)));
  if (safe >= 100) return String(Math.round(safe));
  if (safe >= 10) return String(Math.round(safe * 2) / 2).replace(/\.0$/, "");
  return String(Math.round(safe * 10) / 10).replace(/\.0$/, "");
}

function formatScaledWeight(quantity, unit) {
  return `${formatScaledNumber(quantity, unit)} ${normalizeIngredientUnit(unit)}`.trim();
}

function getRecipeIngredientFactor(baseQuantity, targetQuantity) {
  const base = Math.max(1, Number(baseQuantity) || 1);
  return Math.max(0.05, Number(targetQuantity) || base) / base;
}

function getRecipeTimeFactor(baseQuantity, targetQuantity) {
  return Math.sqrt(getRecipeIngredientFactor(baseQuantity, targetQuantity));
}

function scaleIngredientAmount(name, quantity, unit, ingredientFactor, targetFinalQuantity, intensity = "medium") {
  const category = classifyIngredientCategory(name);
  const normalizedUnit = normalizeIngredientUnit(unit);
  if (category.id === "salt" && ["g", "gm"].includes(normalizedUnit)) {
    return {
      quantity: Math.max(0.1, (Number(targetFinalQuantity) || 0) * (SALT_INTENSITY_PERCENT[intensity] || SALT_INTENSITY_PERCENT.medium)),
      unit: "g",
      category
    };
  }
  const categoryFactor = Math.pow(ingredientFactor, category.exponent);
  return {
    quantity: (Number(quantity) || 0) * categoryFactor,
    unit: normalizedUnit,
    category
  };
}

const INGREDIENT_TOKEN_PATTERN =
  /([^,;|]+?)\s*(\d+(?:\.\d+)?)\s*(kg|g|gm|gram|grams|ml|l|litre|liter|piece|pcs|number|nos?|pinch|tbsp|tsp)\b/gi;

function parseIngredientDetailsFromText(text, groupTitle = "") {
  const source = String(text || "");
  const details = [];
  let match;
  INGREDIENT_TOKEN_PATTERN.lastIndex = 0;
  while ((match = INGREDIENT_TOKEN_PATTERN.exec(source))) {
    const name = String(match[1] || groupTitle || "Ingredient")
      .replace(/^[,\s:&+-]+|[,\s:&+-]+$/g, "")
      .replace(/\s+/g, " ")
      .trim();
    if (!name) continue;
    const unit = normalizeIngredientUnit(match[3]);
    details.push({
      name,
      quantity: Number(match[2]) || 0,
      unit,
      category: classifyIngredientCategory(name)
    });
  }
  return details;
}

function extractRecipeIngredientDetails(recipeJson) {
  const ingredients = Array.isArray(recipeJson?.Ingredients)
    ? recipeJson.Ingredients
    : Array.isArray(recipeJson?.Ingredient)
      ? recipeJson.Ingredient
      : [];
  const details = [];
  ingredients.forEach((item, index) => {
    const title = item?.title || item?.name || item?.Text || item?.text || `Ingredient ${index + 1}`;
    const textDetails = parseIngredientDetailsFromText(item?.text || item?.ingredients || "", title);
    const titleDetails = textDetails.length ? [] : parseIngredientDetailsFromText(title, title);
    const parsedDetails = textDetails.length ? textDetails : titleDetails;
    if (parsedDetails.length) {
      details.push(...parsedDetails);
      return;
    }
    const parsed = splitIngredientNameWeight(item, index);
    details.push({
      name: parsed.name,
      quantity: parsed.quantity,
      unit: parsed.unit,
      category: classifyIngredientCategory(parsed.name)
    });
  });
  return details.slice(0, 80);
}

function extractRecipeCalories(recipeJson, details) {
  const direct = [
    recipeJson?.calories,
    recipeJson?.Calories,
    recipeJson?.kcal,
    recipeJson?.Kcal,
    recipeJson?.nutrition?.calories,
    recipeJson?.nutrition?.kcal
  ].find((value) => Number(value) > 0);
  if (direct) return { value: Math.round(Number(direct)), source: "listed" };
  const kcalMap = [
    [/oil|ghee/i, 884],
    [/butter/i, 717],
    [/sugar|jaggery|honey/i, 380],
    [/semolina|rava|sooji/i, 360],
    [/rice|noodle|pasta/i, 350],
    [/dal|lentil|chana|urad/i, 340],
    [/peanut|cashew|almond|raisin/i, 560],
    [/milk/i, 65],
    [/paneer|cheese/i, 265],
    [/chicken|egg|mutton|fish|prawn/i, 170],
    [/potato/i, 77],
    [/carrot|beans|peas|cabbage|onion|tomato|spinach|vegetable/i, 35]
  ];
  const estimated = (details || []).reduce((total, item) => {
    const unit = normalizeIngredientUnit(item.unit);
    if (!["g", "ml"].includes(unit)) return total;
    const rule = kcalMap.find(([pattern]) => pattern.test(item.name));
    if (!rule) return total;
    const grams = Number(item.quantity) || 0;
    return total + (grams * rule[1]) / 100;
  }, 0);
  return estimated > 20 ? { value: Math.round(estimated), source: "estimated" } : { value: 0, source: "missing" };
}

function summarizeRecipeForCard(recipe) {
  const quantity = inferRecipeQuantity(recipe.recipeJson || {});
  const details = extractRecipeIngredientDetails(recipe.recipeJson || {});
  const categories = new Map();
  details.forEach((item) => {
    const key = item.category?.label || "Ingredients";
    categories.set(key, (categories.get(key) || 0) + 1);
  });
  const calories = extractRecipeCalories(recipe.recipeJson || {}, details);
  return {
    quantity,
    details,
    categories: [...categories.entries()].slice(0, 5),
    calories
  };
}

function renderRecipeNutritionLine(summary) {
  const quantity = `${formatScaledNumber(summary.quantity.quantity, summary.quantity.unit)} ${summary.quantity.unit}`;
  const calorieText =
    summary.calories.value > 0
      ? `${summary.calories.source === "estimated" ? "~" : ""}${summary.calories.value} kcal`
      : "Calories not listed";
  return `
    <div class="recipe-facts">
      <span><b>Final quantity</b>${escapeHtml(quantity)}</span>
      <span><b>Calories</b>${escapeHtml(calorieText)}</span>
      <span><b>Ingredients</b>${summary.details.length}</span>
    </div>
  `;
}

function renderIngredientSummary(summary) {
  const detailRows = summary.details
    .slice(0, 6)
    .map(
      (item) => `
        <span class="ingredient-mini">
          <b>${escapeHtml(item.name)}</b>
          ${escapeHtml(formatScaledWeight(item.quantity, item.unit))}
        </span>
      `
    )
    .join("");
  const categoryRows = summary.categories
    .map(([label, count]) => `<span class="category-chip">${escapeHtml(label)} ${count}</span>`)
    .join("");
  return `
    <div class="recipe-ingredient-summary">
      <div class="category-chip-row">${categoryRows || `<span class="category-chip">Ingredients ${summary.details.length}</span>`}</div>
      <div class="ingredient-mini-grid">${detailRows || `<span class="subtle">Ingredient details are not listed in this recipe file.</span>`}</div>
    </div>
  `;
}

function replaceFinalOutputDescription(description, targetQuantity, targetUnit) {
  const replacement = `FINAL OUTPUT ${formatScaledNumber(targetQuantity, targetUnit)} ${String(targetUnit || "g").toUpperCase()}`;
  const text = String(description || "");
  if (/(final\s+(?:output|outcome)|output)\s*[:=\-]?\s*\d+(?:\.\d+)?\s*(kg|g|gm|ml|l|litre|liter|piece|pcs|number|nos?)/i.test(text)) {
    return text.replace(
      /(final\s+(?:output|outcome)|output)\s*[:=\-]?\s*\d+(?:\.\d+)?\s*(kg|g|gm|ml|l|litre|liter|piece|pcs|number|nos?)/i,
      replacement
    );
  }
  return `${text}${text ? "\n" : ""}${replacement}`;
}

function scaleIngredientPhraseText(text, ingredientFactor, targetQuantity, intensity) {
  return String(text || "").replace(INGREDIENT_TOKEN_PATTERN, (full, rawName, rawQuantity, rawUnit) => {
    const name = String(rawName || "").replace(/\s+/g, " ").trim();
    const scaled = scaleIngredientAmount(name, Number(rawQuantity), rawUnit, ingredientFactor, targetQuantity, intensity);
    return `${name} ${formatScaledWeight(scaled.quantity, scaled.unit)}`;
  });
}

function roundScaledSeconds(value, timeFactor) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) return value;
  return String(Math.max(5, Math.round((numeric * timeFactor) / 5) * 5));
}

function formatAudioDuration(seconds) {
  const safe = Math.max(0, Number(seconds) || 0);
  const minutes = Math.floor(safe / 60);
  const secs = safe % 60;
  if (minutes && secs) return `${minutes}Minute${secs}Second`;
  if (minutes) return `${minutes}Minute`;
  return `${secs}Second`;
}

function createScaledRecipePayload(sourceRecipe, targetQuantity, intensity = "medium") {
  const recipeJson = cloneRecipeForEditing(sourceRecipe);
  const baseQuantity = inferRecipeQuantity(sourceRecipe.recipeJson || {});
  const targetUnit = normalizeIngredientUnit(baseQuantity.unit || "g");
  const cleanTarget = Math.max(1, Number(targetQuantity) || baseQuantity.quantity || 500);
  const ingredientFactor = getRecipeIngredientFactor(baseQuantity.quantity, cleanTarget);
  const timeFactor = getRecipeTimeFactor(baseQuantity.quantity, cleanTarget);
  const originalIngredients = Array.isArray(recipeJson.Ingredients) ? recipeJson.Ingredients : [];
  recipeJson.Ingredients = originalIngredients.map((item) => {
    const next = structuredClone(item || {});
    const title = next.title || next.name || "";
    if (next.text) next.text = scaleIngredientPhraseText(next.text, ingredientFactor, cleanTarget, intensity);
    if (title && /\d/.test(title)) {
      next.title = scaleIngredientPhraseText(title, ingredientFactor, cleanTarget, intensity);
      if (next.name) next.name = next.title;
    }
    const groupName = next.title || next.name || next.audioI || "Ingredient";
    const parsedWeight = parseExplicitQuantityText(next.weight || next.Weight || "", 0, "g");
    if (parsedWeight.quantity > 0) {
      const scaled = scaleIngredientAmount(groupName, parsedWeight.quantity, parsedWeight.unit, ingredientFactor, cleanTarget, intensity);
      next.weight = formatScaledWeight(scaled.quantity, scaled.unit);
      next.Weight = next.weight;
      next.audioQ = formatScaledNumber(scaled.quantity, scaled.unit);
      next.audioU = normalizeIngredientUnit(scaled.unit);
    }
    return next;
  });
  if (Array.isArray(recipeJson.Instruction)) {
    recipeJson.Instruction = recipeJson.Instruction.map((step) => {
      const next = structuredClone(step || {});
      const stepName = next.Text || next.audioI || "Step";
      const parsedWeight = parseExplicitQuantityText(next.Weight || next.weight || "", 0, "g");
      if (parsedWeight.quantity > 0) {
        const scaled = scaleIngredientAmount(stepName, parsedWeight.quantity, parsedWeight.unit, ingredientFactor, cleanTarget, intensity);
        next.Weight = formatScaledWeight(scaled.quantity, scaled.unit);
        next.weight = next.Weight;
        next.audioQ = formatScaledNumber(scaled.quantity, scaled.unit);
      }
      ["durationInSec", "Induction_on_time", "Magnetron_on_time", "wait_time", "warm_time"].forEach((key) => {
        next[key] = roundScaledSeconds(next[key], timeFactor);
      });
      if (Number(next.pump_on) > 0) {
        next.pump_on = String(Math.max(1, Math.round(Number(next.pump_on) * ingredientFactor)));
      }
      next.audioU = formatAudioDuration(Number(next.durationInSec) || getInstructionDuration(next) || 0);
      return next;
    });
  }
  const scaledName = `${sourceRecipe.displayName}_${Math.round(cleanTarget)}${targetUnit}`;
  const firmwareName = sanitizeFirmwareName(scaledName);
  recipeJson.quantity = `${formatScaledNumber(cleanTarget, targetUnit)} ${targetUnit}`;
  recipeJson.finalQuantity = recipeJson.quantity;
  recipeJson.scaling = {
    baseRecipe: sourceRecipe.firmwareName,
    baseQuantity: `${formatScaledNumber(baseQuantity.quantity, baseQuantity.unit)} ${baseQuantity.unit}`,
    targetQuantity: recipeJson.quantity,
    ingredientFactor: Number(ingredientFactor.toFixed(3)),
    timeFactor: Number(timeFactor.toFixed(3)),
    intensity
  };
  recipeJson.description = replaceFinalOutputDescription(recipeJson.description || "", cleanTarget, targetUnit);
  return {
    recipeJson,
    displayName: scaledName,
    firmwareName,
    aliases: `${sourceRecipe.displayName}, ${scaledName}, ${firmwareName}`,
    baseQuantity,
    targetQuantity: cleanTarget,
    targetUnit,
    ingredientFactor,
    timeFactor
  };
}

async function saveScaledRecipe(recipeId, targetQuantity, intensity = "medium") {
  const snapshot = state();
  const sourceRecipe = findRecipeById(snapshot, recipeId);
  if (!sourceRecipe) {
    showToast("Recipe not found", "error");
    return null;
  }
  const baseRecipe =
    sourceRecipe.type === "final" && sourceRecipe.baseRecipeId ? findRecipeById(snapshot, sourceRecipe.baseRecipeId) || sourceRecipe : sourceRecipe;
  const payload = createScaledRecipePayload(sourceRecipe, targetQuantity, intensity);
  const finalRecipe = createFinalRecipeFromBase(baseRecipe, payload.recipeJson, {
    displayName: payload.displayName,
    firmwareName: payload.firmwareName,
    aliases: payload.aliases,
    imageDataUrl: sourceRecipe.imageDataUrl
  });
  finalRecipe.source = "scaled-final";
  finalRecipe.zipName = `${payload.firmwareName}.zip`;
  const existing = snapshot.recipes.find(
    (recipe) => recipe.id !== sourceRecipe.id && normalizeRecipeNameKey(recipe.firmwareName) === normalizeRecipeNameKey(finalRecipe.firmwareName)
  );
  if (existing) {
    finalRecipe.id = existing.id;
    finalRecipe.createdAt = existing.createdAt;
  }
  mutate((draft) => {
    draft.recipes = draft.recipes.filter((recipe) => recipe.id !== finalRecipe.id);
    const baseDraft = draft.recipes.find((recipe) => recipe.id === baseRecipe.id);
    if (baseDraft) baseDraft.selected = true;
    draft.recipes.unshift(finalRecipe);
    draft.ui.recipeMode = "scale";
    syncSelectedRecipesToAllDevices(draft);
  });
  const saved = state().recipes.find((recipe) => recipe.id === finalRecipe.id) || finalRecipe;
  syncImportedRecipeToCloud(saved).catch((error) => console.warn("[On2Cook] Scaled recipe cloud sync failed.", error));
  showToast(`${saved.displayName} is ready as ${saved.zipName}`, "success");
  return saved;
}

function recipeJsonToProMinutes(recipeJson) {
  const steps = Array.isArray(recipeJson?.Instruction) ? recipeJson.Instruction : [];
  if (steps.length === 0) return [makeProMinute(0)];
  const minutes = [];
  steps.forEach((step, stepIndex) => {
    const duration = Math.max(15, getInstructionDuration(step) || 60);
    const minuteCount = Math.max(1, Math.ceil(duration / 60));
    const inductionSeconds = Math.max(0, toInt(step.Induction_on_time, 0));
    const microwaveSeconds = Math.max(0, toInt(step.Magnetron_on_time, 0));
    const stirrerSpeed = stirrerSpeedFromFirmware(step.stirrer_on);
    const stirrerActive = stirrerSpeed !== "off";
    Array.from({ length: minuteCount }).forEach((_, partIndex) => {
      const minuteOffset = partIndex * 60;
      const subBlocks = [0, 1, 2, 3].map((block) => {
        const blockStart = minuteOffset + block * 15;
        return makeProSubBlock({
          inductionPower: blockStart < inductionSeconds && blockStart < duration ? step.Induction_power : 0,
          microwaveActive: blockStart < microwaveSeconds && blockStart < duration,
          microwavePower: step.Magnetron_power || 800,
          stirrerActive: stirrerActive && blockStart < duration,
          stirrerSpeed: stirrerActive ? stirrerSpeed : "medium"
        });
      });
      const waterOn = (toInt(step.pump_on, 0) > 0 || String(step.pump_on || "").toLowerCase() === "on") && partIndex === 0;
      minutes.push(
        makeProMinute(minutes.length, {
          title: minuteCount > 1 ? `${step.Text || `Step ${stepIndex + 1}`} (${partIndex + 1}/${minuteCount})` : step.Text || `Step ${stepIndex + 1}`,
          sourceStepIndex: stepIndex,
          weight: step.Weight || "",
          lidOpen: String(step.lid || "").toLowerCase().includes("open"),
          lidOpenDuration: String(step.lid || "").toLowerCase().includes("open") ? Math.min(60, Math.max(0, duration - minuteOffset)) : 0,
          subBlocks,
          waterBlocks: [waterOn ? 150 : 0, 0, 0, 0],
          ingredients: step.Weight || step.Text ? [{ id: `step-${stepIndex}-${partIndex}-ingredient`, name: step.Text || `Step ${stepIndex + 1}`, quantity: 0, unit: step.Weight || "" }] : []
        })
      );
    });
  });
  return minutes;
}

function getProDraft(snapshot) {
  return snapshot.ui.activeModal?.payload?.draft || null;
}

function recipeToProDraft(recipe) {
  const quantity = inferRecipeQuantity(recipe.recipeJson);
  const ingredients = recipeJsonToConfigIngredients(recipe.recipeJson);
  return {
    recipeId: recipe.id,
    step: "configure",
    displayName: recipe.displayName,
    firmwareName: recipe.firmwareName,
    aliases: Array.isArray(recipe.aliases) ? recipe.aliases.join(", ") : recipe.displayName,
    dietType: inferDietType(recipe),
    recipeType: normalizeRecipeType(recipe.recipeJson?.type || recipe.recipeJson?.recipeType || recipe.category),
    consistency: recipe.recipeJson?.consistency || "medium",
    quantity: quantity.quantity,
    quantityUnit: quantity.unit,
    ingredients,
    selectedMinute: 0,
    selectedBlock: 0,
    minutes: recipeJsonToProMinutes(recipe.recipeJson)
  };
}

function normalizeProStudioUnit(unit) {
  const value = String(unit || "g").toLowerCase();
  if (["g", "ml", "piece", "tsp", "tbsp"].includes(value)) return value;
  if (value === "gm" || value.startsWith("gram")) return "g";
  if (value === "pcs" || value === "number") return "piece";
  return "g";
}

function proDraftToStudioPayload(recipe, draft) {
  const ingredients = (draft.ingredients || []).map((ingredient, index) => ({
    id: String(ingredient.id || `ing-${index}`),
    name: String(ingredient.name || `Ingredient ${index + 1}`),
    quantity: Number(ingredient.quantity || 0),
    unit: normalizeProStudioUnit(ingredient.unit),
    group: ingredient.group || ingredient.source?.group || ""
  }));
  const minutes = (draft.minutes || []).map((minute, index) => ({
    id: String(minute.id || `min-${index}`),
    minuteIndex: Number(minute.minuteIndex ?? index),
    lidOpen: Boolean(minute.lidOpen),
    lidOpenDuration: Number(minute.lidOpenDuration || 0),
    subBlocks: (minute.subBlocks || [makeProSubBlock(), makeProSubBlock(), makeProSubBlock(), makeProSubBlock()]).slice(0, 4).map((block) => ({
      inductionPower: Number(block.inductionPower || 0),
      microwaveActive: Boolean(block.microwaveActive),
      microwavePower: Number(block.microwavePower || 800),
      stirrerActive: block.stirrerActive !== false,
      stirrerSpeed: block.stirrerSpeed || "medium",
      stirrerMode: block.stirrerMode || (block.stirrerActive === false ? "off" : "continuous")
    })),
    waterBlocks: (minute.waterBlocks || [0, 0, 0, 0]).slice(0, 4).map((value) => Boolean(Number(value))) ,
    ingredients: (minute.ingredients || []).map((ingredient, ingredientIndex) => ({
      id: String(ingredient.id || `min-${index}-ing-${ingredientIndex}`),
      name: String(ingredient.name || minute.title || `Ingredient ${ingredientIndex + 1}`),
      quantity: Number(ingredient.quantity || 0),
      unit: normalizeProStudioUnit(ingredient.unit),
      group: ingredient.group || ""
    }))
  }));
  while (minutes.length && minutes[minutes.length - 1].subBlocks.length < 4) {
    minutes[minutes.length - 1].subBlocks.push(makeProSubBlock());
  }
  const payload = {
    presetId: recipe?.id || draft.recipeId || safeRandomId("recipe"),
    name: draft.displayName || recipe?.displayName || "On2Cook Recipe",
    dishType: draft.dietType || "veg",
    recipeType: draft.recipeType || "gravy",
    quantity: Number(draft.quantity || 500),
    quantityUnit: normalizeProStudioUnit(draft.quantityUnit),
    consistency: draft.consistency || "medium",
    healthRichRatio: 35,
    ingredients,
    proRecipe: {
      id: recipe?.id || draft.recipeId || safeRandomId("pro"),
      name: draft.displayName || recipe?.displayName || "On2Cook Recipe",
      dishType: draft.dietType || "veg",
      recipeType: draft.recipeType || "gravy",
      quantity: Number(draft.quantity || 500),
      quantityUnit: normalizeProStudioUnit(draft.quantityUnit),
      consistency: draft.consistency || "medium",
      healthRichRatio: 35,
      tentativeMinutes: minutes.length,
      minutes,
      notes: "Opened from On2Cook Cloud"
    }
  };
  return payload;
}

function proDraftToFirmwareRecipe(sourceRecipe, draft) {
  const recipeJson = cloneRecipeForEditing(sourceRecipe);
  recipeJson.consistency = draft.consistency || "medium";
  recipeJson.recipeType = draft.recipeType || "gravy";
  recipeJson.dietType = draft.dietType || "veg";
  recipeJson.quantity = `${draft.quantity || 0}${draft.quantityUnit || "g"}`;
  recipeJson.Ingredients = configIngredientsToFirmware(draft.ingredients || []);
  const originalSteps = Array.isArray(recipeJson.Instruction) ? recipeJson.Instruction : [];
  const fallbackStep = originalSteps[0] || {};
  recipeJson.Instruction = draft.minutes.flatMap((minute) =>
    minute.subBlocks.map((subBlock, blockIndex) => {
      const original = originalSteps[minute.sourceStepIndex] || fallbackStep;
      const waterMl = Number(minute.waterBlocks?.[blockIndex] || 0);
      return {
        ...structuredClone(original),
        id: draft.minutes.indexOf(minute) * 4 + blockIndex + 1,
        Text: minute.title || original.Text || `Minute ${minute.minuteIndex + 1}`,
        Weight: minute.weight || original.Weight || "",
        lid: minute.lidOpen ? "open" : "close",
        durationInSec: 15,
        Induction_on_time: subBlock.inductionPower > 0 ? "15" : "0",
        Induction_power: String(subBlock.inductionPower || 0),
        Magnetron_on_time: subBlock.microwaveActive ? "15" : "0",
        Magnetron_power: String(subBlock.microwaveActive ? subBlock.microwavePower || 800 : 0),
        stirrer_on: subBlock.stirrerActive ? firmwareStirrerFromSpeed(subBlock.stirrerSpeed) : "0",
        pump_on: waterMl > 0 ? String(waterMl) : "",
        wait_time: original.wait_time || "0",
        warm_time: original.warm_time || "0",
        threshold: original.threshold || "0"
      };
    })
  );
  return recipeJson;
}

function openProfessionalEditor(recipeId) {
  const recipe = findRecipeById(state(), recipeId);
  if (!recipe) {
    showToast("Recipe not found", "error");
    return;
  }
  const draft = recipeToProDraft(recipe);
  openModal("professional-editor", {
    recipeId,
    draft
  });
}

function updateProDraft(mutator) {
  mutate((draftState) => {
    const modal = draftState.ui.activeModal;
    if (!modal || modal.type !== "professional-editor" || !modal.payload?.draft) return draftState;
    mutator(modal.payload.draft);
  });
}

function getSelectedProMinute(draft) {
  return draft.minutes[Math.max(0, Math.min(draft.minutes.length - 1, Number(draft.selectedMinute) || 0))] || null;
}

function getSelectedProBlock(draft) {
  const minute = getSelectedProMinute(draft);
  if (!minute) return null;
  return minute.subBlocks[Math.max(0, Math.min(3, Number(draft.selectedBlock) || 0))] || null;
}

function getMinuteIngredients(minute) {
  return Array.isArray(minute?.ingredients) ? minute.ingredients.filter((item) => item?.name) : [];
}

function formatProClock(seconds) {
  const safe = Math.max(0, Math.floor(Number(seconds) || 0));
  return `${String(Math.floor(safe / 60)).padStart(2, "0")}:${String(safe % 60).padStart(2, "0")}`;
}

function elapsedSecondsBetween(startAt, endAt = nowIso()) {
  const start = new Date(startAt || "").getTime();
  const end = new Date(endAt || "").getTime();
  if (!Number.isFinite(start) || !Number.isFinite(end)) return 0;
  return Math.max(0, Math.round((end - start) / 1000));
}

function getRunActualSeconds(run) {
  return Number(run?.actualDurationSeconds) || elapsedSecondsBetween(run?.startedAt, run?.finishedAt);
}

function getSinceRunFinishedSeconds(run) {
  return run?.finishedAt ? elapsedSecondsBetween(run.finishedAt, run.nextStartedAt || nowIso()) : 0;
}

function markLastRunWaitClosed(device, at = nowIso()) {
  if (!device?.lastRun?.finishedAt || device.lastRun.nextStartedAt) return;
  device.lastRun.nextStartedAt = at;
  device.lastRun.waitAfterCompletionSeconds = elapsedSecondsBetween(device.lastRun.finishedAt, at);
}

function getProLive(draft) {
  if (!draft.live) {
    draft.live = {
      phase: "ready",
      elapsed: 0,
      holdElapsed: 0,
      holds: [],
      paused: false,
      outcome: ""
    };
  }
  return draft.live;
}

function shouldHoldAtMinute(draft, minuteIndex) {
  const minute = draft.minutes[minuteIndex];
  if (!minute || !minute.lidOpen || getMinuteIngredients(minute).length === 0) return false;
  const live = draft.live || {};
  return !(live.holds || []).some((hold) => Number(hold.minuteIndex) === minuteIndex && hold.type === "ingredients");
}

function advanceProLiveSecond() {
  updateProDraft((draft) => {
    const live = getProLive(draft);
    if (live.outcome) {
      live.resultTick = Number(live.resultTick || 0) + 1;
      return;
    }
    if (!["running", "hold"].includes(live.phase) || live.paused || live.outcome) return;
    if (live.phase === "hold") {
      live.holdElapsed = Math.min(PRO_MAX_HOLD_SECONDS, Number(live.holdElapsed || 0) + 1);
      if (live.holdElapsed >= PRO_MAX_HOLD_SECONDS) {
        live.phase = "aborted";
        live.outcome = "aborted";
        live.finishedAt = nowIso();
        live.actualDurationSeconds = Number(live.elapsed || 0);
      }
      return;
    }
    const minuteIndex = Math.floor((Number(live.elapsed) || 0) / 60);
    if (shouldHoldAtMinute(draft, minuteIndex)) {
      live.phase = "hold";
      live.holdMinuteIndex = minuteIndex;
      live.holdElapsed = 0;
      return;
    }
    live.elapsed = Math.min(draft.minutes.length * 60, Number(live.elapsed || 0) + 1);
    if (live.elapsed >= draft.minutes.length * 60) {
      live.phase = "completed";
      live.outcome = "completed";
      live.finishedAt = nowIso();
      live.actualDurationSeconds = Number(live.elapsed || 0);
    }
  });
}

function ensureProLiveTimer() {
  if (proLiveTimer) return;
  proLiveTimer = window.setInterval(() => {
    const snapshot = state();
    if (snapshot.ui.activeModal?.type !== "professional-editor" || !snapshot.ui.activeModal.payload?.draft?.live) {
      window.clearInterval(proLiveTimer);
      proLiveTimer = 0;
      return;
    }
    advanceProLiveSecond();
  }, 1000);
}

function stopProLiveTimer() {
  if (!proLiveTimer) return;
  window.clearInterval(proLiveTimer);
  proLiveTimer = 0;
}

function renderControlTabs(snapshot) {
  const perms = currentPermissions(snapshot);
  const activeTab = snapshot.ui.activeTab === "manual" ? "orders" : snapshot.ui.activeTab;
  const tabs = [
    ["orders", "Orders", "orders"],
    ["recipes", "Recipes", "recipes"],
    ["queue", "Queue", "queue"],
    perms.canSelectGlobalRecipes ? ["global", "Global R", "global"] : null
  ].filter(Boolean);
  return `
    <nav class="tab-strip dashboard-tabs">
      <span class="tab-edge-icon">${renderUiIcon("chevronLeft")}</span>
      ${tabs
        .map(
          ([id, label, icon]) => `
            <button class="tab-button ${activeTab === id ? "active" : ""}" data-action="switch-tab" data-tab="${id}">
              <span class="tab-icon">${renderUiIcon(icon)}</span>
              <span>${label}</span>
            </button>
          `
        )
        .join("")}
      <span class="tab-edge-icon">${renderUiIcon("chevronRight")}</span>
    </nav>
  `;
}

function renderGlobalRecipesTab(snapshot, perms = currentPermissions(snapshot)) {
  if (!perms.canSelectGlobalRecipes) {
    return `
      <section class="stack-section">
        <div class="mini-title">Global recipe library</div>
        <div class="empty-card">Your login can run selected recipes only. Ask the master admin or kitchen manager for access to more recipes.</div>
      </section>
    `;
  }
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
      <div class="mini-title">Select Recipe</div>
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
        <p class="subtle">Select recipes here, then add them to the kitchen Recipe list, create pending Orders, or open the Edit Recipe workflow.</p>
      </div>
    </section>
    <section class="stack-section">
      <div class="mini-title">Recipe selection</div>
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
                    <div class="action-row top-gap">
                      ${(() => {
                        if (existingRecipe && perms.canCreateFinalRecipes) {
                          return `<button class="primary-button small" data-action="open-professional-editor" data-recipe-id="${existingRecipe.id}">Edit Recipe</button>`;
                        }
                        if (existingRecipe) {
                          return `<span class="subtle">Already available in this kitchen</span>`;
                        }
                        return `<button class="secondary-button small" data-action="global-recipe-import-one" data-recipe-catalog-id="${escapeHtml(entry.id)}">Add to Recipe list</button>`;
                      })()}
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
  const bridgeActive = kotBridgeRuntime.active;
  const sections = [
    ["Pending", snapshot.orders.current.filter((order) => order.status === "pending")],
    ["Queued", snapshot.orders.current.filter((order) => order.status === "queued")],
    ["Starting / Cooking", snapshot.orders.current.filter((order) => ["starting", "cooking", "awaiting_confirmation"].includes(order.status))]
  ];
  return `
    <div class="section-head dashboard-order-head">
      <div class="segment-row dashboard-segments">
        <button class="segment ${snapshot.ui.orderMode === "current" ? "active" : ""}" data-action="switch-order-mode" data-mode="current">Current</button>
        <button class="segment ${snapshot.ui.orderMode === "previous" ? "active" : ""}" data-action="switch-order-mode" data-mode="previous">Previous</button>
      </div>
      <button class="primary-button dashboard-manual-order" data-action="open-manual-order">
        <span>${renderUiIcon("plus")}</span>
        Manual Order
      </button>
    </div>
    ${renderOrderDeviceAccess(snapshot)}
    <section class="stack-section">
      <div class="queue-summary dashboard-status-row">
        <div class="summary-chip pending-tone">Pending ${pendingCount}</div>
        <div class="summary-chip queued-tone">Queued ${queuedCount}</div>
        <div class="summary-chip feed-tone">${bridgeActive ? `Server Feed ${snapshot.orders.current.length}` : `Server Feed ${incomingCount}`}</div>
      </div>
      <p class="subtle dashboard-feed-note">
        ${
          bridgeActive
            ? `KOT bridge is active. Current orders are coming from the server endpoint ${escapeHtml(KOT_BRIDGE_URL)}.`
            : "This demo starts with 5 pending orders. One additional order is released every minute until the remaining demo orders are exhausted."
        }
      </p>
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
  const recipe = getEffectiveRecipe(snapshot, order);
  const availableDevices = connectedDevices.filter((device) => !recipe || isRecipeAllowedOnDevice(snapshot, device, recipe.id));
  const blockedConnectedDevices = connectedDevices.filter((device) => recipe && !isRecipeAllowedOnDevice(snapshot, device, recipe.id));
  const deviceButtons = availableDevices
    .map(
      (device) => {
        const runState = getManualDeviceRunState(snapshot, device);
        return `
        <button class="order-device-assign-button ${order.assignedSlot === device.slot ? "selected" : ""} ${escapeHtml(runState.status)}" data-action="assign-order-device" data-order-id="${order.id}" data-slot="${device.slot}">
          <strong>D${device.slot}</strong>
          <span>${escapeHtml(runState.canRunNow ? "Idle" : runState.label)}</span>
        </button>
      `;
      }
    )
    .join("");
  const assignmentPanel =
    allowManualRouting
      ? `
        <div class="available-device-panel dashboard-inline-device-panel">
          <span class="available-device-title">Assign to device</span>
          <div class="available-device-row">
            ${deviceButtons || `<span class="subtle">${connectedDevices.length ? "No connected device is enabled for this recipe" : "No connected devices"}</span>`}
          </div>
          ${blockedConnectedDevices.length ? `<div class="subtle">Blocked here: ${blockedConnectedDevices.map((device) => `D${device.slot}`).join(", ")} not enabled for this recipe.</div>` : ""}
        </div>
      `
      : "";
  const assigned = order.assignedSlot ? `Device ${order.assignedSlot}` : "Auto";
  const orderType = getOrderType(order);
  const thumbUrl = getOrderThumbUrl(order);
  const canCook = ["pending", "queued"].includes(order.status) && perms.canAssignQueues && availableDevices.length > 0;
  const blockedByNoDevice = ["pending", "queued"].includes(order.status) && perms.canAssignQueues && availableDevices.length === 0;
  return `
    <article class="order-card order-card-rich dashboard-order-card">
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
          ${assignmentPanel}
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
      <div class="meta-grid dashboard-order-meta">
        <span>${escapeHtml(order.quantity)}</span>
        <span>${escapeHtml(assigned)}</span>
        <span>${escapeHtml(getOrderPaymentLabel(order))}</span>
      </div>
      ${order.specialInstructions ? `<p class="subtle">${escapeHtml(order.specialInstructions)}</p>` : ""}
      <div class="action-row dashboard-order-actions">
        <button class="secondary-button small" data-action="open-order-details" data-order-id="${order.id}">Details</button>
        ${
          canCook
            ? `<button class="secondary-button small assign-recipe-button" data-action="auto-assign-order" data-order-id="${order.id}">Assign Recipe</button>`
            : blockedByNoDevice
              ? `<button class="secondary-button small assign-recipe-button" type="button" disabled>Connect device first</button>`
              : renderContextOrderAction(order, perms)
        }
        ${canCook ? `<button class="primary-button small cook-now-button" data-action="auto-assign-order" data-order-id="${order.id}">Cook Now</button>` : ""}
        <button class="icon-button more-order-button" data-action="open-order-details" data-order-id="${order.id}" aria-label="More order details">${renderUiIcon("more")}</button>
      </div>
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

function renderManualModeTab(snapshot, fixedDevice = null) {
  const device = fixedDevice || getManualModeTarget(snapshot);
  if (!device) {
    return `<div class="native-manual-empty">No device slot is available.</div>`;
  }
  const selectedRecipeId = String(snapshot.ui.manualMode?.recipeId || "");
  const selectedRecipe = selectedRecipeId ? findRecipeById(snapshot, selectedRecipeId) : null;
  const manualRecipes = getSelectedRecipes(snapshot);
  const selectedRunState = getManualDeviceRunState(snapshot, device);
  const recipeAllowedOnSelectedDevice = Boolean(selectedRecipe && isRecipeAllowedOnDevice(snapshot, device, selectedRecipe.id));
  const canSubmitManualRecipe =
    Boolean(selectedRecipe && recipeAllowedOnSelectedDevice && (selectedRunState.canRunNow || selectedRunState.canQueue));
  const telemetry = getDisplayTelemetry(device);
  const isConnected = device.connection === "connected";
  const disabled = !isConnected || isFirmwareBlockingDevice(device);
  const disabledAttr = disabled ? "disabled" : "";
  const indState = manualQuickState(telemetry.inductionStatus);
  const magState = manualQuickState(telemetry.magnetronStatus);
  const stirrerSpeed = mapStirrerSpeedLabel(telemetry.stirrer || DEFAULT_STIRRER_LEVEL);
  const sprayCount = Math.max(1, Number(snapshot.ui.manualMode?.sprayCount) || 1);
  const manualRunMessage = !selectedRecipe
    ? "Choose a recipe first."
    : !recipeAllowedOnSelectedDevice
      ? `${selectedRecipe.displayName} is not enabled on D${device.slot}.`
      : selectedRunState?.note || "";
  return `
    <section class="native-manual-body">
      ${renderNativeManualRecipeStrip(snapshot, "native-manual-recipes-strip", device)}
      <div class="native-manual-section-title"><span>Manual</span><i></i></div>
      <div class="native-manual-status-note ${isConnected ? "connected" : "offline"}">
        <b>${escapeHtml(isConnected ? "Connected" : "Reconnect required")}</b>
        <span>${escapeHtml(device.lastMessage || (isConnected ? "Live manual controls are ready." : "Connect this cooker before using Manual Mode."))}</span>
      </div>

      <div class="native-manual-module">
        <div class="native-manual-module-head">
          <span class="${indState === "IDLE" || indState === "STOP" ? "" : "active"}">${escapeHtml(manualModuleLabel("induction", telemetry.inductionStatus))}</span>
          <div class="native-manual-adjust">
            ${renderManualStepButton("manual-induction-power", device.slot, "+", { extra: 'data-delta="10"', disabled })}
            <b>${escapeHtml(`${Number(telemetry.indPower || 0)} %`)}</b>
            ${renderManualStepButton("manual-induction-power", device.slot, "-", { extra: 'data-delta="-10"', disabled })}
          </div>
        </div>
        <div class="native-manual-row">
          <div class="native-manual-adjust time">
            ${renderManualStepButton("manual-induction-time", device.slot, "+", { extra: 'data-delta="10"', disabled })}
            <b>10</b>
            ${renderManualStepButton("manual-induction-time", device.slot, "-", { extra: 'data-delta="-10"', disabled })}
            <strong>${escapeHtml(formatManualTime(telemetry.indTime || telemetry.remainingSeconds || 0))}</strong>
          </div>
          <div class="native-manual-power-group">
            ${renderManualRoundButton(indState === "START" || indState === "PAUSE" ? "manual-induction-stop" : "manual-induction-start", device.slot, indState === "START" || indState === "PAUSE" ? "■" : "▶", {
              active: indState === "START" || indState === "PAUSE",
              disabled,
              label: indState === "START" || indState === "PAUSE" ? "Stop induction" : "Start induction"
            })}
            ${renderManualRoundButton("manual-induction-pause-toggle", device.slot, indState === "PAUSE" ? "▶" : "Ⅱ", {
              disabled: disabled || !(indState === "START" || indState === "PAUSE"),
              label: indState === "PAUSE" ? "Resume induction" : "Pause induction"
            })}
          </div>
        </div>
      </div>

      <div class="native-manual-module">
        <div class="native-manual-module-head">
          <span class="${magState === "IDLE" || magState === "STOP" ? "" : "active"}">${escapeHtml(manualModuleLabel("magnetron", telemetry.magnetronStatus))}</span>
          <div class="native-manual-adjust">
            ${renderManualStepButton("manual-magnetron-power", device.slot, "+", { extra: 'data-delta="10"', disabled })}
            <b>${escapeHtml(`${Number(telemetry.magPower || 0)} %`)}</b>
            ${renderManualStepButton("manual-magnetron-power", device.slot, "-", { extra: 'data-delta="-10"', disabled })}
          </div>
        </div>
        <div class="native-manual-row">
          <div class="native-manual-adjust time">
            ${renderManualStepButton("manual-magnetron-time", device.slot, "+", { extra: 'data-delta="10"', disabled })}
            <b>10</b>
            ${renderManualStepButton("manual-magnetron-time", device.slot, "-", { extra: 'data-delta="-10"', disabled })}
            <strong>${escapeHtml(formatManualTime(telemetry.magTime || 0))}</strong>
          </div>
          <div class="native-manual-power-group">
            ${renderManualRoundButton(magState === "START" || magState === "PAUSE" ? "manual-magnetron-stop" : "manual-magnetron-start", device.slot, magState === "START" || magState === "PAUSE" ? "■" : "▶", {
              active: magState === "START" || magState === "PAUSE",
              disabled,
              label: magState === "START" || magState === "PAUSE" ? "Stop microwave" : "Start microwave"
            })}
            ${renderManualRoundButton("manual-magnetron-pause-toggle", device.slot, magState === "PAUSE" ? "▶" : "Ⅱ", {
              disabled: disabled || !(magState === "START" || magState === "PAUSE"),
              label: magState === "PAUSE" ? "Resume microwave" : "Pause microwave"
            })}
          </div>
        </div>
      </div>

      <div class="native-manual-module stirrer">
        <div class="native-manual-module-head">
          <span class="active">Stirrer</span>
          ${renderManualRoundButton(telemetry.stirrer === "OFF" ? "manual-stirrer-speed" : "manual-stirrer-stop", device.slot, "⏻", {
            active: telemetry.stirrer !== "OFF",
            disabled,
            extra: telemetry.stirrer === "OFF" ? `data-speed="${DEFAULT_STIRRER_LEVEL}"` : "",
            label: telemetry.stirrer === "OFF" ? "Start stirrer" : "Stop stirrer"
          })}
        </div>
        <div class="native-stirrer-segment">
          ${[
            ["LOW", "Low"],
            ["MED", "Med"],
            ["HIGH", "High"],
            ["VERY_HIGH", "V High"]
          ]
            .map(
              ([speed, label]) => `
                <button class="${stirrerSpeed === speed ? "selected" : ""}" data-action="manual-stirrer-speed" data-slot="${device.slot}" data-speed="${speed}" ${disabledAttr}>${label}</button>
              `
            )
            .join("")}
        </div>
      </div>

      <div class="native-liquid-grid">
        <label>Sprinkle</label>
        <div class="native-fixed-dose">10 ml</div>
        ${renderManualRoundButton(telemetry.pumpOn ? "manual-pump-stop" : "manual-pump-start", device.slot, "⏻", {
          active: telemetry.pumpOn,
          disabled,
          label: telemetry.pumpOn ? "Stop sprinkle" : "Start 10 ml sprinkle"
        })}
        <label>Spray</label>
        <input type="number" min="1" step="1" value="${sprayCount}" placeholder="count" data-input="manual-spray-count" ${disabledAttr}>
        ${renderManualRoundButton(telemetry.purgeOn ? "manual-spray-stop" : "manual-spray-start", device.slot, "⏻", {
          active: telemetry.purgeOn,
          disabled,
          label: telemetry.purgeOn ? "Stop spray" : "Start spray"
        })}
      </div>

      <div class="native-manual-run">
        <select data-input="manual-recipe-id" ${disabledAttr}>
          <option value="">Select recipe to run</option>
          ${manualRecipes.map((recipe) => `<option value="${recipe.id}" ${selectedRecipeId === recipe.id ? "selected" : ""}>${escapeHtml(recipe.displayName)}</option>`).join("")}
        </select>
        <button class="native-manual-run-button" data-action="manual-run-selected-recipe" data-slot="${device.slot}" ${canSubmitManualRecipe && !disabled ? "" : "disabled"}>
          ${escapeHtml(selectedRunState?.actionLabel || "Run now")}
        </button>
        <small>${escapeHtml(manualRunMessage)}</small>
      </div>

      <div class="native-manual-section-title recommended"><span>Recommended</span><i></i></div>
      ${renderNativeManualRecipeStrip(snapshot, "native-manual-recipes-strip bottom", device)}
      <div class="native-manual-live-status">
        <span>Induction ${escapeHtml(getManualStatus(telemetry.inductionStatus || "IDLE"))}</span>
        <span>Microwave ${escapeHtml(getManualStatus(telemetry.magnetronStatus || "IDLE"))}</span>
        <span>Stirrer ${escapeHtml(formatStirrerDisplay(stirrerSpeed))}</span>
        <span>Pump ${escapeHtml(telemetry.pumpOn ? "ON" : "OFF")}</span>
        <span>Updated ${escapeHtml(device.lastUpdatedAt ? formatAgo(device.lastUpdatedAt) : "Never")}</span>
        <button data-action="manual-request-status" data-slot="${device.slot}" ${disabledAttr}>STATUS=?</button>
      </div>
    </section>
  `;
}

function renderDeviceManualModeModal(snapshot, device) {
  const deviceTitle = device.bluetoothName || device.lockedBluetoothName || device.displayName || `ON2COOK00${device.slot}`;
  return `
    <div class="native-manual-backdrop">
      <div class="native-manual-phone device-manual-screen">
        <div class="native-manual-statusbar">
          <strong>${escapeHtml(new Date().toLocaleTimeString([], { hour: "numeric", minute: "2-digit" }))}</strong>
          <span>!</span>
          <span>BLE</span>
          <span>&bull;</span>
        </div>
        <header class="native-manual-toolbar">
          <button class="native-manual-nav-button" data-action="close-modal" aria-label="Back">&lsaquo;</button>
          <span class="native-ble-mark" aria-hidden="true">B</span>
          <strong>${escapeHtml(deviceTitle)}</strong>
          <button class="native-manual-add-button" data-action="open-assign-recipe" data-slot="${device.slot}" aria-label="Add recipe">+</button>
          <button class="native-manual-menu-button" data-action="manual-request-status" data-slot="${device.slot}" aria-label="Refresh status">&#9776;</button>
        </header>
        ${renderManualModeTab(snapshot, device)}
      </div>
    </div>
  `;
}

function renderRecipeCard(snapshot, recipe, perms) {
  const selectedClass = recipe.selected ? "selected" : "";
  const recipeImageUrl = safeOptionalUrl(recipe.imageDataUrl, "recipe image");
  const summary = summarizeRecipeForCard(recipe);
  const connectedDevices = snapshot.devices.filter((device) => device.connection === "connected");
  const availableDevices = connectedDevices.filter((device) => isRecipeAllowedOnDevice(snapshot, device, recipe.id));
  const blockedConnectedDevices = connectedDevices.filter((device) => !isRecipeAllowedOnDevice(snapshot, device, recipe.id));
  const devices = availableDevices
    .map((device) => {
      const runState = getDeviceRunState(snapshot, device);
      return `
        <button class="order-device-assign-button ${escapeHtml(runState.status)}" data-action="run-recipe-on-device" data-slot="${device.slot}" data-recipe-id="${recipe.id}">
          <strong>D${device.slot}</strong>
          <span>${escapeHtml(runState.label)}</span>
        </button>
      `;
    })
    .join("");
  return `
    <article class="recipe-card ${selectedClass}">
      <div class="recipe-thumb ${recipeImageUrl ? "has-image" : ""}">
        ${recipeImageUrl ? `<img src="${escapeHtml(recipeImageUrl)}" alt="${escapeHtml(recipe.displayName)}">` : `<span>${escapeHtml(recipe.displayName.slice(0, 1))}</span>`}
      </div>
      <div class="recipe-copy">
        <div class="row space">
          <h3>${escapeHtml(recipe.displayName)}</h3>
          ${renderStatusPill(recipe.type === "final" ? "completed" : recipe.selected ? "cooking" : "pending")}
        </div>
        <div class="subtle">${escapeHtml(recipe.firmwareName)} | ${escapeHtml(recipe.source)}</div>
        <div class="subtle">Aliases: ${escapeHtml(recipe.aliases.join(", "))}</div>
        ${renderRecipeNutritionLine(summary)}
        ${renderIngredientSummary(summary)}
        <div class="action-row">
          ${
            perms.canCreateBaseRecipes
              ? `<button class="secondary-button small" data-action="toggle-recipe-selected" data-recipe-id="${recipe.id}">
                  ${recipe.selected ? "Disable" : "Enable"}
                </button>`
              : ""
          }
          ${
            perms.canCreateFinalRecipes
              ? `<button class="primary-button small" data-action="open-professional-editor" data-recipe-id="${recipe.id}">
                  ${recipe.type === "final" ? "Edit Final" : "Edit Recipe"}
                </button>`
              : ""
          }
          ${
            perms.canCreateFinalRecipes
              ? `<button class="secondary-button small" data-action="switch-recipe-mode" data-mode="scale" data-recipe-id="${recipe.id}">
                  Scale
                </button>`
              : ""
          }
          ${
            recipe.type === "final" && perms.canCreateFinalRecipes
              ? `<button class="danger-button small" data-action="delete-final-recipe" data-recipe-id="${recipe.id}">Delete</button>`
              : ""
          }
        </div>
        ${
          recipe.selected
            ? `<div class="available-device-panel recipe-device-panel">
                <div class="available-device-title">Available devices</div>
                <div class="available-device-row">
                  ${devices || `<span class="subtle">${connectedDevices.length ? "No connected device is enabled for this recipe" : "No connected devices"}</span>`}
                </div>
                ${blockedConnectedDevices.length ? `<div class="subtle">Blocked here: ${blockedConnectedDevices.map((device) => `D${device.slot}`).join(", ")} not enabled for this recipe.</div>` : ""}
              </div>`
            : ""
        }
      </div>
    </article>
  `;
}

function renderRecipeScaleCard(snapshot, recipe, perms) {
  const summary = summarizeRecipeForCard(recipe);
  const quantity = summary.quantity;
  const intensity = recipe.recipeJson?.scaling?.intensity || "medium";
  const scaledRecipes = snapshot.recipes
    .filter((item) => item.type === "final" && item.baseRecipeId === (recipe.baseRecipeId || recipe.id) && item.source === "scaled-final")
    .slice(0, 3);
  const previewQuantities = [0.5, 0.75, 1, 1.5, 2].map((factor) => Math.max(1, Math.round(quantity.quantity * factor)));
  return `
    <article class="recipe-card recipe-scale-card">
      <div class="recipe-thumb ${recipe.imageDataUrl ? "has-image" : ""}">
        ${recipe.imageDataUrl ? `<img src="${escapeHtml(recipe.imageDataUrl)}" alt="${escapeHtml(recipe.displayName)}">` : `<span>${escapeHtml(recipe.displayName.slice(0, 1))}</span>`}
      </div>
      <div class="recipe-copy">
        <div class="row space">
          <div>
            <h3>${escapeHtml(recipe.displayName)}</h3>
            <div class="subtle">Base: ${escapeHtml(formatScaledWeight(quantity.quantity, quantity.unit))} | Time uses square-root scaling</div>
          </div>
          ${renderStatusPill(recipe.type === "final" ? "completed" : "pending")}
        </div>
        ${renderRecipeNutritionLine(summary)}
        ${renderIngredientSummary(summary)}
        <div class="scale-control-grid">
          <label class="field-label">
            Target final quantity
            <input class="field-input" type="number" min="1" step="50" value="${escapeHtml(quantity.quantity)}" data-input="recipe-scale-quantity" data-recipe-id="${recipe.id}" data-intensity="${escapeHtml(intensity)}">
          </label>
          <label class="field-label">
            Salt and intensity profile
            <select class="field-input" data-input="recipe-scale-intensity" data-recipe-id="${recipe.id}" data-target-quantity="${escapeHtml(quantity.quantity)}">
              <option value="mild" ${intensity === "mild" ? "selected" : ""}>Mild - 0.7% salt</option>
              <option value="medium" ${intensity === "medium" ? "selected" : ""}>Medium - 0.9% salt</option>
              <option value="rich" ${intensity === "rich" ? "selected" : ""}>Rich - 1.1% salt</option>
            </select>
          </label>
        </div>
        <div class="chip-row">
          ${previewQuantities
            .map(
              (target) => `
                <button class="chip-button" data-action="scale-recipe-now" data-recipe-id="${recipe.id}" data-target-quantity="${target}" data-intensity="${escapeHtml(intensity)}">
                  ${escapeHtml(formatScaledWeight(target, quantity.unit))}
                </button>
              `
            )
            .join("")}
        </div>
        <div class="scaling-rule-card">
          <span><b>Ingredients</b> new / base quantity</span>
          <span><b>Time</b> square root of new / base quantity</span>
          <span><b>Salt</b> mild 0.7%, medium 0.9%, rich 1.1% of final weight</span>
        </div>
        ${
          scaledRecipes.length
            ? `<div class="subtle">Ready final recipes: ${scaledRecipes.map((item) => escapeHtml(item.displayName)).join(", ")}</div>`
            : `<div class="subtle">Changing the quantity creates a ZIP-ready final recipe without altering the base recipe.</div>`
        }
      </div>
    </article>
  `;
}

function renderRecipeScaleTab(snapshot, perms) {
  if (!perms.canCreateFinalRecipes) {
    return `<div class="empty-card">Your login can run recipes only. Scaling is available to the master admin or kitchen manager.</div>`;
  }
  const recipes = snapshot.recipes.filter((recipe) => recipe.selected || recipe.type === "final");
  return `
    <div class="settings-card recipe-scale-intro">
      <div class="mini-title">Scale recipe output</div>
      <p class="subtle">Choose the new final quantity. The app keeps the firmware JSON structure, scales raw ingredients by category, scales time by the square-root rule, and saves the result as a new final recipe named with the target size.</p>
    </div>
    ${recipes.map((recipe) => renderRecipeScaleCard(snapshot, recipe, perms)).join("") || `<div class="empty-card">No recipes are enabled for scaling.</div>`}
  `;
}

function renderRecipesTab(snapshot, perms) {
  const selectedRecipes = snapshot.recipes.filter((recipe) => recipe.selected && recipe.type !== "final");
  const finalRecipes = snapshot.recipes.filter((recipe) => recipe.type === "final");
  const mode = snapshot.ui.recipeMode;
  const recipeFinderUrl = safeOptionalUrl(snapshot.settings.recipeFinder.baseUrl, "recipe finder URL");
  return `
    <section class="stack-section">
      <div class="section-head">
        <div class="segment-row">
          <button class="segment ${mode === "selected" ? "active" : ""}" data-action="switch-recipe-mode" data-mode="selected">Selected</button>
          <button class="segment ${mode === "final" ? "active" : ""}" data-action="switch-recipe-mode" data-mode="final">Final Modified</button>
          ${perms.canCreateFinalRecipes ? `<button class="segment ${mode === "scale" ? "active" : ""}" data-action="switch-recipe-mode" data-mode="scale">Scale</button>` : ""}
          ${perms.canCreateBaseRecipes ? `<button class="segment ${mode === "import" ? "active" : ""}" data-action="switch-recipe-mode" data-mode="import">Import</button>` : ""}
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
      ${mode === "scale" ? renderRecipeScaleTab(snapshot, perms) : ""}
      ${
        mode === "import" && perms.canCreateBaseRecipes
          ? `
            <div class="settings-card">
              <div class="mini-title">Recipe finder import</div>
              ${recipeFinderUrl ? `<a class="link-button" href="${escapeHtml(recipeFinderUrl)}" target="_blank" rel="noreferrer">Open recipe finder</a>` : `<span class="subtle">Recipe finder URL is not configured.</span>`}
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
          : mode === "import"
            ? `<div class="empty-card">Your login can run selected recipes only. Recipe import is controlled by the master admin.</div>`
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
        <div class="permission-grid top-gap">
          <span class="${perms.canManageUsers ? "yes" : "no"}">Manage people</span>
          <span class="${perms.canSelectGlobalRecipes ? "yes" : "no"}">Add/select recipes</span>
          <span class="${perms.canCreateFinalRecipes ? "yes" : "no"}">Edit recipes</span>
          <span class="${perms.canRunRecipes ? "yes" : "no"}">Run recipes</span>
        </div>
        <div class="toggle-row">
          <label><input type="checkbox" data-setting-path="orderScreenEnabled" ${snapshot.settings.orderScreenEnabled ? "checked" : ""}> Order screen enabled</label>
          <label><input type="checkbox" data-setting-path="operatorActsAsManager" ${snapshot.settings.operatorActsAsManager ? "checked" : ""}> Operator may act as kitchen manager</label>
        </div>
        <div class="action-row top-gap">
          ${
            perms.canSelectGlobalRecipes
              ? `<button class="secondary-button small" data-action="switch-tab" data-tab="global">Open Global Recipes</button>`
              : `<span class="subtle">Global recipe selection is controlled by the master admin.</span>`
          }
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
                <span>
                  <strong>${escapeHtml(user.displayName)}</strong>
                  <span class="subtle">${escapeHtml(user.mobilePhone || user.email || "No contact")}</span>
                </span>
                <span class="subtle">${escapeHtml(user.role)} | ${user.canAddRecipes ? "can add recipes" : "selected only"} | ${user.canEditRecipes ? "can edit" : "run only"}</span>
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
      <div class="mini-title">Cloud sync</div>
      <div class="settings-card">
        <div class="meta-grid">
          <span>Status ${cloudRuntime.ready ? "Ready" : "Unavailable"}</span>
          <span>${cloudRuntime.session?.email ? `Signed in` : "Not signed in"}</span>
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
            ? `<div class="top-gap subtle">${escapeHtml(userFacingCloudError(cloudRuntime.lastError, "Cloud sync is unavailable right now."))}</div>`
            : ""
        }
        <div class="action-row top-gap">
          <button class="primary-button small" data-action="open-cloud-login">Sign in</button>
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
  const connectedCount = getConnectedDevices(snapshot).length;
  const connectingCount = snapshot.devices.filter((device) => device.connection === "connecting").length;
  const busyOrderCount = snapshot.orders.current.filter((order) =>
    ["queued", "starting", "cooking", "awaiting_confirmation"].includes(order.status)
  ).length;
  const unreadCount = getUnreadNotificationCount(snapshot);
  const activeTab = snapshot.ui.activeTab === "manual" ? "orders" : snapshot.ui.activeTab;
  const body =
    activeTab === "orders"
      ? snapshot.ui.orderMode === "current"
        ? renderCurrentOrders(snapshot, perms)
        : renderPreviousOrders(snapshot)
      : activeTab === "recipes"
        ? renderRecipesTab(snapshot, perms)
      : activeTab === "queue"
        ? renderQueueTab(snapshot)
      : activeTab === "global"
        ? renderGlobalRecipesTab(snapshot, perms)
      : renderMoreTab(snapshot, perms);

  return `
    <section class="phone-frame control-phone" data-scroll-key="frame-control">
      <div class="phone-shell">
        <header class="phone-head hero-head dashboard-hero-head">
          <img class="brand-logo dashboard-brand-logo" src="./assets/on2cook-logo.png" alt="On2Cook">
          <div class="dashboard-title-block">
            <h2>On2Cook Cloud</h2>
            <p>${escapeHtml(snapshot.facilities[0]?.name || "Kitchen console")} <span class="dropdown-mark">⌄</span></p>
          </div>
          <div class="dashboard-header-spacer"></div>
          <div class="dashboard-counter-card">
            <strong>D</strong>
            <span>${escapeHtml(connectedCount)}</span>
            <small>Devices</small>
          </div>
          <div class="dashboard-counter-card">
            <strong>B</strong>
            <span>${escapeHtml(busyOrderCount)}</span>
            <small>Busy Orders</small>
          </div>
          <button
            class="icon-button dashboard-bell-button ${unreadCount > 0 ? "has-notifications" : ""}"
            type="button"
            data-action="open-notification-drawer"
            title="Open notifications"
            aria-label="Open notifications"
          >
            <span class="bell-glyph">${renderUiIcon("bell")}</span>
            <span class="bluetooth-count">${connectingCount > 0 ? "..." : unreadCount ? Math.min(unreadCount, 99) : ""}</span>
          </button>
          <button
            class="icon-button dashboard-more-button"
            type="button"
            data-action="switch-tab"
            data-tab="more"
            title="More"
            aria-label="More"
          >
            ${renderUiIcon("more")}
          </button>
        </header>
        ${renderControlTabs(snapshot)}
        <div class="phone-body" data-scroll-key="body-control">${body}</div>
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
            const water = getLiquidStepValue(step.pump_on);
            const slurry = getLiquidStepValue(step.purge_on);
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
                  <span class="power-label">Water</span>
                  <div class="power-bar-shell water">
                    <div class="power-bar-fill" style="width:${water.fill}%"></div>
                  </div>
                  <span class="power-value">${escapeHtml(water.label)}</span>
                </div>
                <div class="power-track">
                  <span class="power-label">Slurry</span>
                  <div class="power-bar-shell slurry">
                    <div class="power-bar-fill" style="width:${slurry.fill}%"></div>
                  </div>
                  <span class="power-value">${escapeHtml(slurry.label)}</span>
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

function renderCurrentRecipeCard(snapshot, device, currentOrder, recipe) {
  const active = isDeviceActivelyCooking(device) && Boolean(recipe || currentOrder || getLiveRecipeName(device));
  const displayTelemetry = getDisplayTelemetry(device);
  const stepState = recipe ? getOperatorStepState(device, recipe) : { activeIndex: 0, totalSteps: 0, currentStep: {} };
  const step = stepState.currentStep || {};
  const recipeName =
    device.activeRun?.displayName ||
    currentOrder?.itemName ||
    recipe?.displayName ||
    getLiveRecipeName(device) ||
    "No active recipe";
  const status = active ? device.telemetry.workStatus || currentOrder?.status || "cooking" : "idle";
  const remainingSeconds = active ? getDeviceActiveRemainingSeconds(device, recipe) : 0;
  const totalSeconds = Math.max(1, Number(device.activeRun?.durationSeconds) || getRecipeDuration(recipe));
  const elapsedSeconds = active ? elapsedSecondsBetween(device.activeRun?.startedAt || currentOrder?.createdAt || nowIso(), nowIso()) : 0;
  const progress = active ? Math.min(100, Math.max(0, (elapsedSeconds / totalSeconds) * 100)) : 0;
  const water = active ? getLiquidStepValue(step.pump_on) : getLiquidStepValue(0);
  const stirrerValue = active
    ? displayTelemetry.stirrer || step.stirrer_on || DEFAULT_STIRRER_LEVEL
    : device.connection === "connected"
      ? displayTelemetry.stirrer || DEFAULT_STIRRER_LEVEL
      : "OFF";
  const inductionPower = active ? clampPercent(displayTelemetry.indPower || step.Induction_power) : 0;
  const microwavePower = active ? clampPercent(displayTelemetry.magPower || step.Magnetron_power) : 0;
  const tiles = [
    {
      label: "Induction",
      value: `${inductionPower}%`,
      hint: active && step.Induction_on_time ? `${secondsLabel(step.Induction_on_time)}` : "Standby"
    },
    {
      label: "Microwave",
      value: `${microwavePower}%`,
      hint: active && step.Magnetron_on_time ? `${secondsLabel(step.Magnetron_on_time)}` : "Standby"
    },
    {
      label: "Stirrer",
      value: formatStirrerDisplay(stirrerValue),
      hint: stirrerValue === "OFF" || String(stirrerValue) === "0" ? "Off" : "Active"
    },
    {
      label: "Water",
      value: water.label,
      hint: water.fill > 0 ? "Scheduled" : "No water now"
    }
  ];
  return `
    <article class="current-recipe-card">
      <div class="row space current-recipe-head">
        <div>
          <div class="mini-title">Current Recipe</div>
          <h3>${escapeHtml(recipeName)}</h3>
          <p class="subtle">
            ${
              active
                ? `Step ${escapeHtml(stepState.activeIndex + 1)}${stepState.totalSteps ? ` of ${escapeHtml(stepState.totalSteps)}` : ""}`
                : "Device is idle"
            }
          </p>
        </div>
        <div class="current-recipe-status">
          ${renderStatusPill(status)}
          <strong>${active ? `${formatProClock(remainingSeconds)} left` : "00:00"}</strong>
        </div>
      </div>
      <div class="current-recipe-progress" aria-hidden="true">
        <span style="width:${progress}%"></span>
      </div>
      <div class="current-recipe-tiles">
        ${tiles
          .map(
            (tile) => `
              <div class="current-recipe-tile">
                <span>${escapeHtml(tile.label)}</span>
                <strong>${escapeHtml(tile.value)}</strong>
                <small>${escapeHtml(tile.hint)}</small>
              </div>
            `
          )
          .join("")}
      </div>
      <div class="action-row current-recipe-actions">
        <button class="secondary-button small" type="button" data-action="view-device-queue" data-slot="${device.slot}">View Queue</button>
      </div>
    </article>
  `;
}

function renderCompactDeviceInfo(device, summaryMessage) {
  return `
    <button class="device-info-tab" data-action="open-device-sheet" data-slot="${device.slot}">
      <span>
        <strong>${escapeHtml(device.telemetry.workStatus || device.connection || "offline")}</strong>
        <small>${escapeHtml(summaryMessage || "Tap for live details")}</small>
      </span>
      <span class="subtle">${escapeHtml(device.lastUpdatedAt ? formatAgo(device.lastUpdatedAt) : "Never")}</span>
    </button>
  `;
}

function renderOperatorPromptBlock(prompt, label, seconds = null, options = {}) {
  if (!prompt) return "";
  const urgent = Number.isFinite(seconds) && seconds <= 5;
  const prefix = urgent ? `${label} in -${Math.max(0, seconds)}s` : seconds === null ? label : `${label} in ${formatProClock(seconds)}`;
  return `
    <div class="operator-prompt ${prompt.tone || ""} ${urgent ? "urgent" : ""}">
      <div>
        <span>${escapeHtml(prefix)}</span>
        <strong>${escapeHtml(prompt.title)}</strong>
        ${prompt.detail ? `<small>${escapeHtml(prompt.detail)}</small>` : ""}
      </div>
      ${options.actionHtml || ""}
    </div>
  `;
}

function renderOperatorCookPanel(snapshot, device, currentOrder, recipe) {
  const state = getOperatorStepState(device, recipe);
  const step = state.currentStep || {};
  const water = getLiquidStepValue(step.pump_on);
  const slurry = getLiquidStepValue(step.purge_on);
  const recipeName =
    device.activeRun?.displayName ||
    currentOrder?.itemName ||
    recipe?.displayName ||
    getLiveRecipeName(device) ||
    "Recipe running";
  const nextPrompt = state.nextPrompt;
  const showNextPopup = nextPrompt && Number.isFinite(state.secondsToNext) && state.secondsToNext <= 5;
  const currentPrompt = state.currentPrompt;
  const elapsedSeconds = elapsedSecondsBetween(device.activeRun?.startedAt || nowIso(), nowIso());
  const remainingSeconds = getDeviceActiveRemainingSeconds(device, recipe);
  const plannedSeconds = Number(device.activeRun?.durationSeconds) || getRecipeDuration(recipe);
  const currentActionHtml = currentPrompt?.type === "ingredient"
    ? `<button class="primary-button small" data-action="complete-ingredients" data-slot="${device.slot}">Ingredient added</button>`
    : "";
  return `
    <article class="operator-cook-card">
      <div class="row space">
        <div>
          <div class="order-id">${escapeHtml(currentOrder?.orderId || `DEVICE ${device.slot}`)}</div>
          <h3>${escapeHtml(recipeName)}</h3>
        </div>
        ${renderStatusPill(device.telemetry.workStatus || currentOrder?.status || "cooking")}
      </div>
      <div class="operator-live-summary">
        <span><b>${formatProClock(elapsedSeconds)}</b><small>elapsed</small></span>
        <span><b>${formatProClock(remainingSeconds)}</b><small>remaining</small></span>
        <span><b>${state.activeIndex + 1}${state.totalSteps ? `/${state.totalSteps}` : ""}</b><small>step</small></span>
      </div>
      ${
        showNextPopup
          ? `<div class="operator-next-popup">
              ${renderOperatorPromptBlock(nextPrompt, "Next step", state.secondsToNext)}
            </div>`
          : ""
      }
      <div class="operator-current-step">
        <span>Current step</span>
        <strong>${escapeHtml(step.Text || `Step ${state.activeIndex + 1}`)}</strong>
        <small>${secondsLabel(state.currentRemaining)} left</small>
      </div>
      ${currentPrompt ? renderOperatorPromptBlock(currentPrompt, "Add now", null, { actionHtml: currentActionHtml }) : ""}
      ${
        nextPrompt && !showNextPopup
          ? renderOperatorPromptBlock(nextPrompt, "Next", state.secondsToNext)
          : `<div class="operator-prompt quiet"><div><span>Next</span><strong>No manual ingredient due yet</strong><small>Automatic cooking continues.</small></div></div>`
      }
      <div class="action-row top-gap">
        <button class="danger-button small" data-action="abort-device" data-slot="${device.slot}">Abort recipe</button>
        <button class="secondary-button small" data-action="open-device-sheet" data-slot="${device.slot}">Full details</button>
      </div>
      <details class="operator-detail-tab">
        <summary>Cook details</summary>
        <div class="operator-now-grid">
          <div class="operator-step-card">
            <span>Current step</span>
            <strong>${escapeHtml(step.Text || `Step ${state.activeIndex + 1}`)}</strong>
            <small>Step ${state.activeIndex + 1}${state.totalSteps ? ` of ${state.totalSteps}` : ""} | planned ${formatProClock(plannedSeconds)}</small>
          </div>
          <div class="operator-step-card muted">
            <span>Step controls</span>
            <strong>IH ${clampPercent(device.telemetry.indPower || step.Induction_power)}%</strong>
            <small>MW ${clampPercent(device.telemetry.magPower || step.Magnetron_power)}% | Water ${escapeHtml(water.label)} | Slurry ${escapeHtml(slurry.label)}</small>
          </div>
        </div>
      </details>
    </article>
  `;
}

function renderCompactTimelinePreview(device, recipe) {
  const steps = Array.isArray(recipe?.recipeJson?.Instruction) ? recipe.recipeJson.Instruction : [];
  if (!steps.length) return "";
  const activeIndex = Math.max(0, Number(device.telemetry.stepNo || 1) - 1);
  return `
    <div class="compact-timeline-preview">
      <div class="row space">
        <span>Timeline preview</span>
        <strong>${escapeHtml(`Step ${activeIndex + 1}/${steps.length}`)}</strong>
      </div>
      <div class="compact-timeline-bars">
        ${steps
          .map((step, index) => {
            const width = Math.max(8, Math.min(100, (getInstructionDuration(step) / Math.max(1, getRecipeDuration(recipe))) * 100));
            const state = index < activeIndex ? "done" : index === activeIndex ? "live" : "upcoming";
            return `<span class="${state}" style="flex-basis:${width}%"></span>`;
          })
          .join("")}
      </div>
    </div>
  `;
}

function getDeviceActiveRemainingSeconds(device, recipe) {
  const telemetryRemaining = Number(device.telemetry.remainingSeconds) || 0;
  if (telemetryRemaining > 0) return telemetryRemaining;
  const totalSeconds = Number(device.activeRun?.durationSeconds) || getRecipeDuration(recipe);
  if (!totalSeconds || !device.activeRun?.startedAt) return 0;
  return Math.max(0, totalSeconds - elapsedSecondsBetween(device.activeRun.startedAt, nowIso()));
}

function getRecipePrepItems(recipe, limit = 4) {
  return recipeSheetIngredientsFromRecipe(recipe)
    .filter((item) => item?.name)
    .slice(0, limit)
    .map((item) => {
      const quantity = [item.quantity || "", item.unit || ""].filter(Boolean).join(" ").trim();
      return {
        name: item.name,
        quantity
      };
    });
}

function renderQueuePrepItems(recipe) {
  const prepItems = getRecipePrepItems(recipe, 4);
  const allCount = recipeSheetIngredientsFromRecipe(recipe).filter((item) => item?.name).length;
  if (!prepItems.length) return `<div class="subtle">Prep ingredients are not available for this recipe yet.</div>`;
  return `
    <div class="queue-prep-list">
      ${prepItems
        .map((item) => `<span>${escapeHtml(item.name)}${item.quantity ? ` <strong>${escapeHtml(item.quantity)}</strong>` : ""}</span>`)
        .join("")}
      ${allCount > prepItems.length ? `<span class="muted">+${allCount - prepItems.length} more</span>` : ""}
    </div>
  `;
}

function renderNextQueuedPrepPrompt(snapshot, queueOrders) {
  if (!queueOrders.length) return "";
  const nextOrder = queueOrders[0];
  const recipe = getEffectiveRecipe(snapshot, nextOrder);
  return `
    <article class="next-prep-prompt">
      <div>
        <span>Prepare next</span>
        <strong>${escapeHtml(nextOrder.itemName)}</strong>
        <small>${escapeHtml(nextOrder.orderId)} | starts when this device is free</small>
      </div>
      ${renderQueuePrepItems(recipe)}
    </article>
  `;
}

function renderQueuedRecipeCard(snapshot, order, index, startInSeconds) {
  const recipe = getEffectiveRecipe(snapshot, order);
  const duration = getRecipeDuration(recipe);
  const startsAtIso = new Date(Date.now() + startInSeconds * 1000).toISOString();
  const imageUrl = safeOptionalUrl(recipe?.imageDataUrl || "", "queued recipe image");
  return `
    <article class="next-recipe-card ${index === 0 ? "primary-next" : ""}">
      <div class="next-recipe-main">
        ${imageUrl ? `<img src="${escapeHtml(imageUrl)}" alt="${escapeHtml(order.itemName)}">` : ""}
        <div>
          <span>${index === 0 ? "Next recipe" : `Queue ${index + 1}`}</span>
          <strong>${escapeHtml(order.itemName)}</strong>
          <small>${escapeHtml(order.orderId)} | cook time ${secondsLabel(duration)}</small>
        </div>
      </div>
      <div class="next-recipe-timing">
        <div>
          <span>Starts in</span>
          <strong>${startInSeconds <= 0 ? "Now" : formatProClock(startInSeconds)}</strong>
        </div>
        <div>
          <span>Start at</span>
          <strong>${escapeHtml(formatShortTime(startsAtIso))}</strong>
        </div>
      </div>
      <div class="mini-title">Prep before start</div>
      ${renderQueuePrepItems(recipe)}
    </article>
  `;
}

function renderDeviceQueuePlan(snapshot, device, queueOrders, activeRecipe) {
  if (!queueOrders.length) return `<div class="empty-card compact-empty">Queue is empty.</div>`;
  let cursorSeconds = getDeviceActiveRemainingSeconds(device, activeRecipe);
  return `
    <div class="device-queue-plan">
      ${queueOrders
        .map((order, index) => {
          const card = renderQueuedRecipeCard(snapshot, order, index, cursorSeconds);
          cursorSeconds += getRecipeDuration(getEffectiveRecipe(snapshot, order));
          return card;
        })
        .join("")}
    </div>
  `;
}

function renderQueueTimelineHistoryRow(row, device) {
  const completed = row.outcome !== "aborted";
  return `
    <article class="queue-timeline-row history ${completed ? "completed" : "aborted"}">
      <span class="queue-timeline-marker ${completed ? "complete" : "failed"}">${completed ? "&#10003;" : "!"}</span>
      <div class="queue-timeline-copy">
        <strong>${escapeHtml(row.displayName)}</strong>
        <small>${escapeHtml(completed ? "completed" : "aborted")} | ${escapeHtml(formatShortTime(row.finishedAt))}</small>
      </div>
      ${row.recook ? `<span class="queue-tag recook">Re-cook</span>` : ""}
      <button class="secondary-button micro" type="button" data-action="queue-cook-again" data-slot="${device.slot}" data-history-key="${escapeHtml(row.key)}">Cook Again</button>
    </article>
  `;
}

function renderQueueTimelineNow(model) {
  if (!model.now) {
    return `
      <article class="queue-timeline-row now idle">
        <span class="queue-timeline-marker current"></span>
        <div class="queue-timeline-copy">
          <strong>No active recipe</strong>
          <small>Device is idle. Start any upcoming item when ready.</small>
        </div>
      </article>
    `;
  }
  return `
    <article class="queue-timeline-row now">
      <span class="queue-timeline-marker current"></span>
      <div class="queue-timeline-copy">
        <strong>${escapeHtml(model.now.displayName)}</strong>
        <small>${escapeHtml(model.now.status || "Cooking")} | ${escapeHtml(formatShortTime(model.now.startedAt || nowIso()))} | ${secondsLabel(model.now.remainingSeconds)} left</small>
      </div>
      <span class="queue-tag live">NOW</span>
    </article>
  `;
}

function renderQueueTimelineUpcomingRow(item, device, isBusy) {
  const order = item.order;
  const canStartNow = device.connection === "connected" && !isBusy;
  const canForceStart = device.connection === "connected" && isBusy;
  return `
    <article class="queue-timeline-row upcoming ${order.recook ? "recook" : ""}">
      <span class="queue-timeline-marker pending"></span>
      <div class="queue-timeline-copy">
        <strong>${escapeHtml(order.itemName)}</strong>
        <small>${escapeHtml(order.orderId)} | Upcoming | ${escapeHtml(formatShortTime(item.startsAt))} | ${secondsLabel(item.durationSeconds)}</small>
        ${order.recook ? `<span class="queue-tag recook">Re-cook</span>` : ""}
      </div>
      <div class="queue-timeline-controls" aria-label="Queue controls for ${escapeHtml(order.itemName)}">
        <button class="icon-button tiny" type="button" data-action="queue-move-up" data-slot="${device.slot}" data-order-id="${order.id}" ${item.index === 0 ? "disabled" : ""} aria-label="Move up">&uarr;</button>
        <button class="icon-button tiny" type="button" data-action="queue-move-down" data-slot="${device.slot}" data-order-id="${order.id}" ${item.index === item.total - 1 ? "disabled" : ""} aria-label="Move down">&darr;</button>
        <button class="secondary-button micro" type="button" data-action="queue-move-next" data-slot="${device.slot}" data-order-id="${order.id}" ${item.index === 0 ? "disabled" : ""}>Next</button>
        ${
          canStartNow
            ? `<button class="primary-button micro" type="button" data-action="queue-start-now" data-slot="${device.slot}" data-order-id="${order.id}">Start now</button>`
            : canForceStart
              ? `<button class="danger-button micro" type="button" data-action="queue-force-start" data-slot="${device.slot}" data-order-id="${order.id}">Stop Current & Start Selected</button>`
              : `<button class="secondary-button micro" type="button" disabled>Connect first</button>`
        }
      </div>
    </article>
  `;
}

function renderQueueTimelineCard(snapshot, device) {
  const model = getQueueTimelineModel(snapshot, device);
  const isBusy = isDeviceActivelyCooking(device);
  return `
    <div class="queue-timeline-card">
      <div class="queue-timeline-header">
        <div>
          <div class="mini-title">Queue timeline</div>
          <p>Cooked history, current recipe, and upcoming queue for Device ${device.slot}.</p>
        </div>
        <span class="queue-tag">${model.upcoming.length} upcoming</span>
      </div>
      <section class="queue-timeline-section cooked">
        <div class="queue-section-title">Cooked history</div>
        ${
          model.cooked.length
            ? model.cooked.map((row) => renderQueueTimelineHistoryRow(row, device)).join("")
            : `<div class="empty-card compact-empty">No completed recipes recorded for this device yet.</div>`
        }
      </section>
      <section class="queue-timeline-section now">
        <div class="queue-section-title">NOW</div>
        ${renderQueueTimelineNow(model)}
      </section>
      <section class="queue-timeline-section upcoming">
        <div class="queue-section-title">Upcoming queue</div>
        ${
          model.upcoming.length
            ? model.upcoming
                .map((item) => renderQueueTimelineUpcomingRow({ ...item, total: model.upcoming.length }, device, isBusy))
                .join("")
            : `<div class="empty-card compact-empty">Queue is empty. Cook Again or assign an order to add work here.</div>`
        }
      </section>
    </div>
  `;
}

function renderQuickAssignCard(snapshot, device) {
  const recipes = getQuickAssignRecipes(snapshot, device, 3);
  const busy = isDeviceActivelyCooking(device);
  const connected = canUseDeviceForRecipeActions(device);
  return `
    <div class="quick-assign-card">
      <div class="queue-timeline-header">
        <div>
          <div class="mini-title">Quick assign recipe</div>
          <p>Add a frequent recipe to Device ${device.slot} without leaving Device Details.</p>
        </div>
        <button class="primary-button micro" type="button" data-action="open-assign-recipe" data-slot="${device.slot}" ${connected ? "" : "disabled"}>Add Recipe</button>
      </div>
      <div class="quick-recipe-chip-row">
        ${
          recipes.length
            ? recipes
                .map(
                  (recipe) => `
                    <button class="quick-recipe-chip" type="button" data-action="quick-assign-chip" data-slot="${device.slot}" data-recipe-id="${recipe.id}" ${connected ? "" : "disabled"}>
                      <span>${escapeHtml(recipe.displayName)}</span>
                      <small>${escapeHtml(connected ? (busy ? "Add to Queue" : "Cook / Queue") : "Connect first")}</small>
                    </button>
                  `
                )
                .join("")
            : `<div class="empty-card compact-empty">No recent or allowed recipes are available for this device yet.</div>`
        }
      </div>
    </div>
  `;
}

function renderQuickAssignConfirmModal(snapshot, modal) {
  const slot = Number(modal.payload?.slot || 0);
  const device = snapshot.devices.find((item) => item.slot === slot);
  const recipe =
    (modal.payload?.recipeId ? findRecipeById(snapshot, modal.payload.recipeId) : null) ||
    (modal.payload?.catalogId ? getRecipeCatalog(snapshot).find((entry) => entry.id === modal.payload.catalogId) : null);
  const title = recipe?.displayName || recipe?.name || recipe?.recipeName || "Selected recipe";
  const busy = device ? isDeviceActivelyCooking(device) : false;
  const forcedAction = modal.payload?.action === "cook" || modal.payload?.action === "queue" ? modal.payload.action : "";
  const connected = canUseDeviceForRecipeActions(device);
  const canCookNow = connected && !busy;
  const baseAttrs = `data-slot="${slot}" data-recipe-id="${escapeHtml(modal.payload?.recipeId || "")}" data-catalog-id="${escapeHtml(modal.payload?.catalogId || "")}"`;
  return `
    <div class="modal-backdrop">
      <div class="modal-card quick-assign-confirm-modal">
        <div class="row space">
          <div>
            <div class="eyebrow">Confirm recipe assignment</div>
            <h3>${escapeHtml(title)}</h3>
          </div>
          <button class="icon-button" data-action="return-device-sheet" data-slot="${slot}">x</button>
        </div>
        <div class="settings-card compact-note">
          <strong>Device ${slot}: ${escapeHtml(device?.displayName || "Unknown device")}</strong>
          <p class="subtle">${escapeHtml(connected ? "The app will check the device recipe list and upload this recipe only if it is missing." : "Connect this device before assigning a recipe.")}</p>
        </div>
        ${
          forcedAction
            ? `<p class="subtle">${escapeHtml(forcedAction === "cook" ? "Ready to check/upload the recipe and start cooking now." : "Ready to check/upload the recipe and add it to this device queue.")}</p>
               <div class="action-row">
                 <button class="secondary-button" type="button" data-action="return-device-sheet" data-slot="${slot}">Cancel</button>
                 <button class="${forcedAction === "cook" ? "primary-button" : "secondary-button"}" type="button" data-action="confirm-quick-assignment" data-assign-action="${forcedAction}" ${baseAttrs} ${connected ? "" : "disabled"}>
                   ${forcedAction === "cook" ? "Confirm Cook Now" : "Confirm Add to Queue"}
                 </button>
               </div>`
            : `<p class="subtle">Choose what to do after the device recipe check is complete.</p>
               <div class="action-row">
                 <button class="primary-button" type="button" data-action="confirm-quick-assignment" data-assign-action="cook" ${baseAttrs} ${canCookNow ? "" : "disabled"}>Cook Now</button>
                 <button class="secondary-button" type="button" data-action="confirm-quick-assignment" data-assign-action="queue" ${baseAttrs} ${connected ? "" : "disabled"}>Add to Queue</button>
                 <button class="secondary-button" type="button" data-action="return-device-sheet" data-slot="${slot}">Cancel</button>
               </div>`
        }
      </div>
    </div>
  `;
}

function renderAssignRecipeModal(snapshot, modal) {
  const slot = Number(modal.payload?.slot || 0);
  const device = snapshot.devices.find((item) => item.slot === slot);
  const busy = device ? isDeviceActivelyCooking(device) : false;
  const connected = canUseDeviceForRecipeActions(device);
  const uploadOnly = modal.payload?.mode === "upload";
  const results = getAssignRecipeSearchResults(snapshot, modal);
  return `
    <div class="modal-backdrop">
      <div class="modal-card wide assign-recipe-modal">
        <div class="row space">
          <div>
            <div class="eyebrow">Assign recipe</div>
            <h3>Device ${slot} ${device ? `- ${escapeHtml(device.displayName)}` : ""}</h3>
          </div>
          <button class="icon-button" data-action="${uploadOnly ? "return-device-recipes" : "return-device-sheet"}" data-slot="${slot}">x</button>
        </div>
        <label class="field-label">
          Search local and global recipes
          <input class="field-input" type="search" data-input="assign-recipe-search" data-slot="${slot}" value="${escapeHtml(modal.payload?.query || "")}" placeholder="Search recipe name, firmware name, or alias">
        </label>
        <div class="assign-recipe-list">
          ${
            results.length
              ? results
                  .map((row) => {
                    const idAttrs =
                      row.kind === "local"
                        ? `data-recipe-id="${escapeHtml(row.id)}"`
                        : `data-catalog-id="${escapeHtml(row.id)}"`;
                    return `
                      <article class="assign-recipe-row">
                        <div>
                          <span class="queue-tag ${row.kind === "global" ? "recook" : ""}">${row.kind === "global" ? "Global" : "Local"}</span>
                          <strong>${escapeHtml(row.title)}</strong>
                          <small>${escapeHtml(row.subtitle)}</small>
                        </div>
                        <div class="action-row">
                          ${
                            !connected
                              ? `<button class="secondary-button micro" type="button" disabled>Connect D${slot} first</button>`
                              : uploadOnly
                                ? `<button class="primary-button micro" type="button" data-action="assign-recipe-action" data-assign-action="upload" data-slot="${slot}" ${idAttrs}>Upload/Add</button>`
                                : !busy
                                  ? `<button class="primary-button micro" type="button" data-action="assign-recipe-action" data-assign-action="cook" data-slot="${slot}" ${idAttrs}>Cook Now</button>`
                                  : ""
                          }
                          ${uploadOnly || !connected ? "" : `<button class="secondary-button micro" type="button" data-action="assign-recipe-action" data-assign-action="queue" data-slot="${slot}" ${idAttrs}>Add to Queue</button>`}
                        </div>
                      </article>
                    `;
                  })
                  .join("")
              : `<div class="empty-card">No recipe matches that search.</div>`
          }
        </div>
      </div>
    </div>
  `;
}

function renderLastRunMetrics(run) {
  if (!run?.finishedAt) return "";
  const actualSeconds = getRunActualSeconds(run);
  const plannedSeconds = Number(run.durationSeconds) || actualSeconds;
  const sinceSeconds = getSinceRunFinishedSeconds(run);
  const sinceLabel = run.nextStartedAt ? "Idle before next" : `Since ${run.outcome === "aborted" ? "abort" : "completion"}`;
  const sinceSmall = run.nextStartedAt
    ? "Next recipe has started"
    : "No next recipe started yet";
  return `
    <div class="last-run-metrics">
      <div>
        <span>${run.outcome === "aborted" ? "Ran before abort" : "Cook time"}</span>
        <strong>${formatProClock(actualSeconds)}</strong>
        <small>of ${formatProClock(plannedSeconds)} planned</small>
      </div>
      <div>
        <span>${escapeHtml(sinceLabel)}</span>
        <strong>${run.nextStartedAt ? "" : "+"}${formatProClock(sinceSeconds)}</strong>
        <small>${escapeHtml(sinceSmall)}</small>
      </div>
    </div>
  `;
}

function renderLastRunTab(device, label = "Last recipe sheet") {
  if (!device?.lastRun?.finishedAt) return "";
  const actualSeconds = getRunActualSeconds(device.lastRun);
  const sinceSeconds = getSinceRunFinishedSeconds(device.lastRun);
  const sinceCopy = device.lastRun.nextStartedAt
    ? `idle ${formatProClock(sinceSeconds)} before next`
    : `${formatProClock(sinceSeconds)} since ${device.lastRun.outcome === "aborted" ? "abort" : "completion"}`;
  return `
    <button class="last-recipe-tab" data-action="open-device-recipe-sheet" data-slot="${device.slot}">
      <span class="status-dot ${device.lastRun.outcome === "aborted" ? "failed" : "complete"}"></span>
      <span>
        <strong>${escapeHtml(device.lastRun.displayName || device.lastRun.firmwareName || "Last recipe")}</strong>
        <small>${escapeHtml(device.lastRun.outcome === "aborted" ? "Aborted" : "Completed")} | ran ${formatProClock(actualSeconds)} | ${escapeHtml(sinceCopy)}</small>
      </span>
      <span class="chevron">›</span>
    </button>
  `;
}

function renderLastCookedCard(device) {
  if (!device?.lastRun?.finishedAt) {
    return `<div class="empty-card compact-empty">No recipe has run on this device yet.</div>`;
  }
  const outcome = device.lastRun.outcome === "aborted" ? "aborted" : "completed";
  return `
    <article class="last-cooked-card ${outcome}">
      <button class="last-cooked-main" data-action="open-device-recipe-sheet" data-slot="${device.slot}">
        <span class="status-dot ${outcome === "aborted" ? "failed" : "complete"}"></span>
        <span>
          <small>Last recipe ${escapeHtml(outcome)}</small>
          <strong>${escapeHtml(device.lastRun.displayName || device.lastRun.firmwareName || "Last recipe")}</strong>
        </span>
        <span class="chevron">›</span>
      </button>
      ${renderLastRunMetrics(device.lastRun)}
    </article>
  `;
}

function renderTimelineIdleState(device) {
  if (device?.lastRun?.finishedAt) {
    const outcome = device.lastRun.outcome === "aborted" ? "aborted" : "completed";
    return `
      <div class="empty-card timeline-idle-card">
        Last recipe ${escapeHtml(outcome)}. Open the recipe sheet above the queue to review what happened.
      </div>
    `;
  }
  return `<div class="empty-card">The recipe timeline will appear here once this device starts cooking.</div>`;
}

function renderActiveRunTab(device) {
  if (!device?.activeRun?.firmwareName && !device?.activeRun?.displayName) return "";
  return `
    <button class="last-recipe-tab active-run-tab" data-action="open-device-sheet" data-slot="${device.slot}">
      <span class="status-dot live"></span>
      <span>
        <strong>${escapeHtml(device.activeRun.displayName || device.activeRun.firmwareName || "Recipe running")}</strong>
        <small>Running now | ${formatProClock(elapsedSecondsBetween(device.activeRun.startedAt || nowIso(), nowIso()))} elapsed | tap for details</small>
      </span>
      <span class="chevron">›</span>
    </button>
  `;
}

function recipeSheetIngredientsFromRecipe(recipe) {
  const items = Array.isArray(recipe?.recipeJson?.Ingredients)
    ? recipe.recipeJson.Ingredients
    : Array.isArray(recipe?.recipeJson?.Ingredient)
      ? recipe.recipeJson.Ingredient
      : [];
  if (items.length) {
    return items.slice(0, 40).map((item, index) => splitIngredientNameWeight(item, index));
  }
  return recipeJsonToConfigIngredients(recipe?.recipeJson || {});
}

function renderRecipeSheetContent({ title, recipe, run, draft, sourceLabel = "On2Cook Pro" }) {
  const displayName = title || run?.displayName || recipe?.displayName || draft?.displayName || "Recipe";
  const imageUrl = safeOptionalUrl(recipe?.imageDataUrl || "", "recipe sheet image");
  const plannedSeconds = Number(run?.durationSeconds) || (draft?.minutes?.length ? draft.minutes.length * 60 : getRecipeDuration(recipe));
  const actualSeconds = getRunActualSeconds(run) || Number(run?.actualDurationSeconds) || Number(run?.elapsed || 0);
  const sinceSeconds = getSinceRunFinishedSeconds(run);
  const outcome = run?.outcome || "completed";
  const sinceSheetLabel = run?.nextStartedAt ? "Idle before next" : `Since ${outcome === "aborted" ? "abort" : "completion"}`;
  const sinceSheetPrefix = run?.nextStartedAt ? "" : "+";
  const ingredients = draft?.ingredients || recipeSheetIngredientsFromRecipe(recipe);
  const steps = draft?.minutes
    ? draft.minutes.map((minute, index) => ({
        label: minute.title || `Minute ${index + 1}`,
        lid: minute.lidOpen ? "Lid open" : "Lid closed",
        ind: Math.max(...minute.subBlocks.map((block) => Number(block.inductionPower) || 0)),
        mag: minute.subBlocks.some((block) => block.microwaveActive) ? "On" : "Off",
        water: (minute.waterBlocks || []).some((amount) => Number(amount) > 0) ? `${(minute.waterBlocks || []).reduce((total, amount) => total + (Number(amount) || 0), 0)} ml` : "Off",
        slurry: "Off"
      }))
    : Array.isArray(recipe?.recipeJson?.Instruction)
      ? recipe.recipeJson.Instruction.map((step, index) => ({
          label: step.Text || `Step ${index + 1}`,
          lid: step.lid || "",
          ind: step.Induction_power || 0,
          mag: step.Magnetron_on_time ? "On" : "Off",
          water: getLiquidStepValue(step.pump_on).label,
          slurry: getLiquidStepValue(step.purge_on).label
        }))
      : [];
  return `
    <div class="recipe-sheet-phone">
      <div class="recipe-sheet-nav">
        <button class="secondary-button small" data-action="${draft ? "close-live-sheet-to-queue" : "close-modal"}">Back</button>
        <strong>Recipe Sheet</strong>
        <span class="subtle">${escapeHtml(sourceLabel)}</span>
      </div>
      <div class="recipe-sheet-hero ${imageUrl ? "has-image" : ""}">
        ${imageUrl ? `<img src="${escapeHtml(imageUrl)}" alt="${escapeHtml(displayName)}">` : ""}
        <div>
          <div class="chip-row">
            <span class="chip-button selected static-chip">${escapeHtml(recipe?.recipeJson?.dietType || draft?.dietType || "veg")}</span>
            <span class="chip-button selected static-chip">${escapeHtml(recipe?.recipeJson?.recipeType || draft?.recipeType || "boil")}</span>
          </div>
          <h2>${escapeHtml(displayName)}</h2>
        </div>
      </div>
      <section class="recipe-sheet-status">
        <div class="run-icon">${outcome === "aborted" ? "!" : "›"}</div>
        <div>
          <strong>${outcome === "aborted" ? "Manually Ended Early" : "Completed"}</strong>
          <p>${Math.max(0, Math.round(actualSeconds / 60))} of ${Math.max(1, Math.round(plannedSeconds / 60))} planned minutes cooked</p>
        </div>
        <strong>${formatProClock(actualSeconds)}</strong>
      </section>
      <div class="recipe-sheet-stat-grid">
        <div><span>On2Cook</span><strong>${formatProClock(actualSeconds)}</strong></div>
        <div><span>${escapeHtml(sinceSheetLabel)}</span><strong>${sinceSheetPrefix}${formatProClock(sinceSeconds)}</strong></div>
        <div><span>Normal cooking</span><strong>${formatProClock(plannedSeconds)}</strong></div>
      </div>
      <div class="recipe-sheet-profile">
        <span>Quantity<strong>${escapeHtml(recipe?.recipeJson?.quantity || (draft ? `${draft.quantity}${draft.quantityUnit}` : ""))}</strong></span>
        <span>Consistency<strong>${escapeHtml(recipe?.recipeJson?.consistency || draft?.consistency || "medium")}</strong></span>
        <span>Profile<strong>Healthy</strong></span>
        <span>Minutes<strong>${Math.ceil(actualSeconds / 60)}/${Math.ceil(plannedSeconds / 60)}</strong></span>
      </div>
      <section class="stack-section">
        <div class="mini-title">Ingredients</div>
        <div class="recipe-sheet-list">
          ${ingredients.map((item) => `<div><span>${escapeHtml(item.name)}</span><strong>${escapeHtml([item.quantity || "", item.unit || ""].filter(Boolean).join(" "))}</strong></div>`).join("") || `<div class="empty-card">No ingredient details available.</div>`}
        </div>
      </section>
      <section class="stack-section">
        <div class="mini-title">Cooking steps</div>
        <div class="recipe-sheet-list">
          ${steps.slice(0, 12).map((step, index) => `<div><span>${index + 1}. ${escapeHtml(step.label)} | ${escapeHtml(step.lid)}</span><strong>W ${escapeHtml(step.water || "Off")} | SL ${escapeHtml(step.slurry || "Off")} | IH ${escapeHtml(step.ind)} | MW ${escapeHtml(step.mag)}</strong></div>`).join("") || `<div class="empty-card">No cooking steps available.</div>`}
        </div>
      </section>
      ${
        draft
          ? `
            <section class="recipe-sheet-finish">
              <div class="mini-title">Dish photo</div>
              <div class="photo-drop">
                ${draft.dishPhotoDataUrl ? `<img class="dish-photo-preview" src="${escapeHtml(draft.dishPhotoDataUrl)}" alt="Finished dish photo">` : `<div class="photo-icon">camera</div>`}
                <strong>Capture your dish</strong>
                <p>Photograph the finished result to attach to this recipe sheet.</p>
                <div class="action-row">
                  <label class="secondary-button small file-button">Take Photo<input type="file" accept="image/*" capture="environment" data-input="live-dish-photo"></label>
                  <label class="secondary-button small file-button">Upload<input type="file" accept="image/*" data-input="live-dish-photo"></label>
                </div>
                <button class="secondary-button small" data-action="share-recipe-image" ${draft.dishPhotoDataUrl ? "" : "disabled"}>${draft.dishPhotoDataUrl ? "Share Image" : "Share Image - add a photo first"}</button>
              </div>
              <div class="mini-title">Finish session</div>
              <label class="field-label">Save as new recipe name<input class="field-input" data-input="library-recipe-name" value="${escapeHtml(`${displayName} Final`)}" placeholder="Enter a new recipe name"></label>
              <div class="action-row">
                <button class="primary-button" data-action="save-live-recipe-library">Save to Library</button>
                <button class="secondary-button" data-action="reopen-live-editor">Back to Editor</button>
                <button class="secondary-button" data-action="close-live-sheet-to-queue">Return to Queue</button>
              </div>
            </section>
          `
          : ""
      }
    </div>
  `;
}

function renderDevicePhone(snapshot, device) {
  const currentOrder = getCurrentJob(snapshot, device);
  const queueOrders = getQueueOrders(snapshot, device);
  const runtimeRecipe = getRuntimeRecipe(snapshot, device);
  const activeTimeline = shouldRenderLiveTimeline(device, currentOrder);
  const timelineRecipe = activeTimeline ? getDeviceTimelineRecipe(snapshot, device, runtimeRecipe) : null;
  const connectionLabel =
    device.connection === "connected"
      ? "connected"
      : device.connection === "connecting"
        ? "connecting"
        : "disconnected";
  const connectionTone =
    connectionLabel === "connected" ? "cooking" : connectionLabel === "connecting" ? "starting" : "failed";
  const summaryMessage = getDeviceSummaryMessage(device);
  const hasActiveCook = Boolean(timelineRecipe && (currentOrder || hasLiveRuntime(device) || device.currentJobId || device.activeRun?.displayName));
  const linkedDeviceLabel = device.bluetoothName
    ? `Locked to ${device.bluetoothName}`
    : device.browserDeviceId
      ? "Locked to saved cooker"
      : "Not assigned";
  const moveCandidate = findConnectedSlotMoveCandidate(snapshot, device.slot);
  return `
    <section class="phone-frame device-phone ${device.connection}" data-scroll-key="frame-device-${device.slot}">
      <div class="phone-shell">
        <header class="phone-head device-head">
          <img class="device-head-logo" src="./assets/on2cook-logo.png" alt="On2Cook">
          <div class="device-head-copy">
            <div class="eyebrow">Device ${device.slot}</div>
            <h2>${escapeHtml(device.displayName)}</h2>
            <p>${escapeHtml(device.bluetoothName || "Not paired yet")}</p>
          </div>
          <span class="status-pill ${connectionTone}">
            ${escapeHtml(connectionLabel)}
          </span>
          <span class="linked-device-label ${device.browserDeviceId ? "locked" : ""}" title="${escapeHtml(linkedDeviceLabel)}">${escapeHtml(linkedDeviceLabel)}</span>
        </header>
        <div class="phone-body" data-scroll-key="body-device-${device.slot}">
          <section class="stack-section">
            <div class="action-row">
              ${
                device.connection === "connected"
                  ? `<button class="secondary-button small" data-action="disconnect-device" data-slot="${device.slot}">Disconnect</button>`
                  : `<button class="primary-button small" data-action="connect-device" data-slot="${device.slot}">Connect</button>`
              }
              ${
                device.connection !== "connected" && moveCandidate
                  ? `<button class="secondary-button small" data-action="move-connected-cooker" data-slot="${device.slot}">Use D${moveCandidate.slot} cooker here</button>`
                  : ""
              }
              <button class="secondary-button small" data-action="open-device-sheet" data-slot="${device.slot}">Details</button>
              <button class="secondary-button small" data-action="request-status" data-slot="${device.slot}">Status</button>
              <button class="secondary-button small" data-action="request-firmware" data-slot="${device.slot}">Firmware</button>
              <button class="secondary-button small" data-action="open-device-manual" data-slot="${device.slot}">Manual Mode</button>
              <button class="secondary-button small" data-action="open-live-logs" data-slot="${device.slot}">Live Logs</button>
            </div>
            ${renderCompactDeviceInfo(device, summaryMessage)}
            ${renderFirmwareUpdateNotice(device)}
          </section>
          <section class="stack-section">
            <div class="mini-title">Last cooked recipe</div>
            ${renderLastCookedCard(device)}
          </section>
          ${
            hasActiveCook
              ? `
                <section class="stack-section">
                  <div class="mini-title">Cooking now</div>
                  ${renderOperatorCookPanel(snapshot, device, currentOrder, timelineRecipe)}
                </section>
              `
              : queueOrders.length
                ? `
                  <section class="stack-section">
                    <div class="mini-title">Ready for next</div>
                    ${renderNextQueuedPrepPrompt(snapshot, queueOrders)}
                  </section>
                `
              : ""
          }
          <section class="stack-section queue-stack">
            <div class="mini-title">Next recipe and prep</div>
            ${renderDeviceQueuePlan(snapshot, device, queueOrders, timelineRecipe)}
          </section>
        </div>
      </div>
    </section>
  `;
}

function renderProMinuteCell(minute, selectedMinute, selectedBlock) {
  const isSelectedMinute = minute.minuteIndex === selectedMinute;
  return `
    <div class="pro-minute ${isSelectedMinute ? "selected" : ""}">
      <button class="pro-minute-head" data-action="pro-select-minute" data-minute="${minute.minuteIndex}">
        ${minute.minuteIndex + 1}m
      </button>
      <div class="pro-row lid-row">
        <span>${minute.lidOpen ? "Open" : "Closed"}</span>
      </div>
      <div class="pro-row chunk-row">
        ${minute.subBlocks
          .map(
            (block, blockIndex) => `
              <button class="pro-chunk induction ${isSelectedMinute && selectedBlock === blockIndex ? "selected" : ""} ${block.inductionPower > 0 ? "on" : ""}" data-action="pro-select-block" data-minute="${minute.minuteIndex}" data-block="${blockIndex}">
                ${block.inductionPower}
              </button>
            `
          )
          .join("")}
      </div>
      <div class="pro-row chunk-row">
        ${minute.subBlocks
          .map(
            (block, blockIndex) => `
              <button class="pro-chunk microwave ${block.microwaveActive ? "on" : ""}" data-action="pro-select-block" data-minute="${minute.minuteIndex}" data-block="${blockIndex}">
                ${block.microwaveActive ? block.microwavePower : "0"}
              </button>
            `
          )
          .join("")}
      </div>
      <div class="pro-row chunk-row">
        ${minute.subBlocks
          .map(
            (block, blockIndex) => `
              <button class="pro-chunk stirrer ${block.stirrerActive ? "on" : ""}" data-action="pro-select-block" data-minute="${minute.minuteIndex}" data-block="${blockIndex}">
                ${block.stirrerActive ? block.stirrerSpeed.replace("very-", "v") : "off"}
              </button>
            `
          )
          .join("")}
      </div>
      <div class="pro-row chunk-row">
        ${minute.waterBlocks
          .map(
            (amount, blockIndex) => `
              <button class="pro-chunk water ${Number(amount) > 0 ? "on" : ""}" data-action="pro-select-block" data-minute="${minute.minuteIndex}" data-block="${blockIndex}">
                ${Number(amount) > 0 ? `${amount}` : "0"}
              </button>
            `
          )
          .join("")}
      </div>
    </div>
  `;
}

function renderProConfigureModal(snapshot, draft, sourceRecipe) {
  const recipeImageUrl = safeOptionalUrl(sourceRecipe.imageDataUrl, "recipe image");
  const timelineMinutes = Math.max(1, Math.ceil((draft.minutes.length * 60) / 60));
  return `
    <div class="modal-backdrop">
      <div class="modal-card pro-config-modal">
        <div class="pro-config-shell">
          <div class="pro-config-nav">
            <button class="secondary-button small" data-action="close-modal">Back</button>
            <div>
              <div class="eyebrow">Configure Recipe</div>
              <strong>${escapeHtml(draft.displayName)}</strong>
            </div>
            <span class="status-pill cooking">Step 1 of 2</span>
          </div>
          <div class="pro-config-hero ${recipeImageUrl ? "has-image" : ""}">
            ${recipeImageUrl ? `<img src="${escapeHtml(recipeImageUrl)}" alt="${escapeHtml(draft.displayName)}">` : ""}
            <div class="pro-config-hero-copy">
              <h3>${escapeHtml(draft.displayName)}</h3>
              <div class="chip-row">
                <span class="chip-button selected static-chip">${escapeHtml(draft.recipeType)}</span>
                <span class="chip-button selected static-chip">${escapeHtml(draft.dietType)}</span>
              </div>
            </div>
          </div>
          <div class="pro-config-body">
            <section class="pro-config-section diet">
              <div class="mini-title">Diet type</div>
              <div class="pro-choice-grid three">
                ${PRO_DIET_TYPES.map(
                  (item) => `
                    <button class="pro-choice ${draft.dietType === item.id ? "selected" : ""}" data-action="pro-set-diet" data-value="${item.id}">
                      <span>${escapeHtml(item.icon)}</span>
                      <strong>${escapeHtml(item.label)}</strong>
                      ${draft.dietType === item.id ? `<small>Selected</small>` : ""}
                    </button>
                  `
                ).join("")}
              </div>
            </section>
            <section class="pro-config-section type">
              <div class="mini-title">Recipe type</div>
              <div class="pro-pill-grid">
                ${PRO_RECIPE_TYPES.map(
                  (type) => `<button class="chip-button ${draft.recipeType === type ? "selected" : ""}" data-action="pro-set-recipe-type" data-value="${type}">${escapeHtml(type.replace("-", " "))}</button>`
                ).join("")}
              </div>
            </section>
            <section class="pro-config-section quantity">
              <div class="mini-title">Quantity</div>
              <div class="pro-quantity-row">
                <button class="secondary-button square" data-action="pro-adjust-quantity" data-delta="-50">-</button>
                <input class="field-input pro-quantity-input" type="number" min="1" data-input="pro-quantity" value="${escapeHtml(draft.quantity || 500)}">
                <button class="secondary-button square" data-action="pro-adjust-quantity" data-delta="50">+</button>
                <button class="chip-button ${draft.quantityUnit === "g" ? "selected" : ""}" data-action="pro-set-quantity-unit" data-value="g">g</button>
                <button class="chip-button ${draft.quantityUnit === "ml" ? "selected" : ""}" data-action="pro-set-quantity-unit" data-value="ml">ml</button>
              </div>
              <div class="pro-estimate-row"><span>Estimated timeline</span><strong>~${timelineMinutes} minutes</strong></div>
            </section>
            <section class="pro-config-section consistency">
              <div class="mini-title">Consistency</div>
              <div class="pro-choice-grid three">
                ${PRO_CONSISTENCIES.map(
                  (item) => `
                    <button class="pro-choice consistency ${draft.consistency === item.id ? "selected" : ""}" data-action="pro-set-consistency" data-value="${item.id}">
                      <span class="bars">${item.id === "thin" ? "|" : item.id === "medium" ? "||" : "|||"}</span>
                      <strong>${escapeHtml(item.label)}</strong>
                      <small>${escapeHtml(item.hint)}</small>
                    </button>
                  `
                ).join("")}
              </div>
            </section>
            <section class="pro-config-section ingredients">
              <div class="row space">
                <div class="mini-title">Ingredients</div>
                <span class="subtle">${draft.ingredients.length} items</span>
              </div>
              <div class="pro-ingredient-list">
                ${draft.ingredients.map((ingredient, index) => renderProIngredientRow(ingredient, index)).join("") || `<div class="empty-card">No ingredients found in this recipe yet.</div>`}
              </div>
              <button class="secondary-button full-width dashed" data-action="pro-add-ingredient">Add Ingredient</button>
            </section>
            <section class="pro-config-preview">
              <div class="mini-title">Recipe preview</div>
              <div class="pro-preview-grid">
                <span>Dish<strong>${escapeHtml(draft.displayName)}</strong></span>
                <span>Diet<strong>${escapeHtml(draft.dietType)}</strong></span>
                <span>Type<strong>${escapeHtml(draft.recipeType)}</strong></span>
                <span>Quantity<strong>${escapeHtml(`${draft.quantity}${draft.quantityUnit}`)}</strong></span>
                <span>Consistency<strong>${escapeHtml(draft.consistency)}</strong></span>
                <span>Ingredients<strong>${draft.ingredients.length} items</strong></span>
              </div>
            </section>
          </div>
          <button class="primary-button pro-open-timeline" data-action="pro-open-timeline-editor">Open Pro Timeline Editor</button>
        </div>
      </div>
    </div>
  `;
}

function renderProIngredientRow(ingredient, index) {
  return `
    <div class="pro-ingredient-row">
      <input class="field-input ingredient-name" data-input="pro-ingredient-name" data-index="${index}" value="${escapeHtml(ingredient.name)}" aria-label="Ingredient name">
      <input class="field-input ingredient-qty" type="number" min="0" step="0.5" data-input="pro-ingredient-quantity" data-index="${index}" value="${escapeHtml(ingredient.quantity)}" aria-label="Ingredient quantity">
      <select class="field-input ingredient-unit" data-input="pro-ingredient-unit" data-index="${index}" aria-label="Ingredient unit">
        ${["g", "ml", "piece", "tsp", "tbsp"].map((unit) => `<option value="${unit}" ${ingredient.unit === unit ? "selected" : ""}>${unit}</option>`).join("")}
      </select>
      <button class="danger-button icon-only" data-action="pro-remove-ingredient" data-index="${index}" aria-label="Remove ingredient">x</button>
    </div>
  `;
}

function renderProReadyOverlay(draft, live) {
  const firstIngredients = (Array.isArray(draft.ingredients) ? draft.ingredients : getMinuteIngredients(draft.minutes[0])).slice(0, 8);
  return `
    <div class="pro-live-overlay">
      <div class="pro-live-dialog">
        <div class="mini-title">${live.phase === "ready" ? "Ready to Start?" : `Add Ingredients - Min ${Number(live.holdMinuteIndex || 0) + 1}`}</div>
        <p class="subtle">${live.phase === "ready" ? "Prepare your ingredients first" : "Recipe paused - hold heat at 30%"}</p>
        ${
          live.phase === "hold"
            ? `<div class="pro-hold-meter"><span style="width:${Math.min(100, ((live.holdElapsed || 0) / PRO_MAX_HOLD_SECONDS) * 100)}%"></span></div>
               <div class="row space"><span class="subtle">Hold duration</span><strong>${live.holdElapsed || 0}s / ${PRO_MAX_HOLD_SECONDS}s</strong></div>
               <div class="pro-hold-note">Induction at 30% - keeping food warm</div>`
            : ""
        }
        <div class="mini-title">${live.phase === "ready" ? "Add to pot before starting" : "Add now"}</div>
        <div class="pro-live-ingredients">
          ${(live.phase === "hold" ? getMinuteIngredients(draft.minutes[live.holdMinuteIndex]) : firstIngredients)
            .map((item) => `<div class="pro-live-ingredient"><span></span>${escapeHtml([item.quantity || "", item.unit || "", item.name || ""].filter(Boolean).join(" "))}</div>`)
            .join("") || `<div class="empty-card">No ingredient prompt is defined for this stage.</div>`}
        </div>
        <button class="primary-button full-width" data-action="${live.phase === "ready" ? "pro-live-start" : "pro-live-resume"}">
          ${live.phase === "ready" ? "Ingredients Added - Start Cooking" : "Ingredients Added - Resume"}
        </button>
      </div>
    </div>
  `;
}

function renderProLiveResult(draft, live) {
  const plannedSeconds = draft.minutes.length * 60;
  const actualSeconds = Number(live.actualDurationSeconds) || Number(live.elapsed || 0);
  const sinceSeconds = live.finishedAt ? elapsedSecondsBetween(live.finishedAt, nowIso()) : 0;
  const aborted = live.outcome === "aborted";
  return `
    <div class="modal-backdrop pro-live-backdrop dismissable-result" data-action="close-live-result-to-queue" title="Tap to return to queue">
      <div class="modal-card pro-live-modal pro-result-modal">
        <div class="pro-result-top">
          <strong>${escapeHtml(draft.displayName)}</strong>
          <span class="status-pill ${aborted ? "failed" : "complete"}">${aborted ? "Manual End" : "Completed"} - ${Math.min(100, Math.round((actualSeconds / Math.max(1, plannedSeconds)) * 100))}% done</span>
        </div>
        <div class="pro-result-body">
          <div class="pro-result-icon">${aborted ? "!" : "✓"}</div>
          <div class="eyebrow">${aborted ? "Recipe ended early" : "Recipe completed"}</div>
          <h2>${aborted ? "Recipe Aborted" : "Recipe Completed"}</h2>
          <div class="pro-result-metrics">
            <div>
              <span>${aborted ? "Time before abort" : "Time to completion"}</span>
              <strong>${formatProClock(actualSeconds)}</strong>
              <small>of ${formatProClock(plannedSeconds)} planned</small>
            </div>
            <div>
              <span>Since ${aborted ? "abort" : "completion"}</span>
              <strong>+${formatProClock(sinceSeconds)}</strong>
              <small>still counting</small>
            </div>
          </div>
          <button class="secondary-button" data-action="pro-live-view-sheet">View Recipe Sheet</button>
          <p class="subtle">Tap anywhere else to return to the device queue. Saving this sheet is optional and will not block the next queued recipe.</p>
        </div>
      </div>
    </div>
  `;
}

function renderProLiveCookModal(snapshot, modal) {
  const draft = getProDraft(snapshot);
  if (!draft) return "";
  const live = getProLive(draft);
  if (live.outcome) {
    return renderProLiveResult(draft, live);
  }
  const totalSeconds = draft.minutes.length * 60;
  const minuteIndex = Math.min(draft.minutes.length - 1, Math.floor((live.elapsed || 0) / 60));
  const subIndex = Math.min(3, Math.floor(((live.elapsed || 0) % 60) / 15));
  const remaining = Math.max(0, totalSeconds - (live.elapsed || 0));
  const playheadPct = totalSeconds > 0 ? Math.min(100, ((live.elapsed || 0) / totalSeconds) * 100) : 0;
  const showOverlay = live.phase === "ready" || live.phase === "hold";
  return `
    <div class="modal-backdrop pro-live-backdrop">
      <div class="modal-card pro-live-modal">
        <div class="pro-live-topbar">
          <button class="secondary-button small" data-action="pro-live-back-editor">Editor</button>
          <strong>${escapeHtml(draft.displayName)}</strong>
          <span class="status-pill ${live.outcome === "aborted" ? "failed" : live.outcome === "completed" ? "complete" : "failed"}">${live.outcome || "Live"}</span>
          <div class="pro-live-clock"><strong>${formatProClock(remaining)}</strong><small>remaining</small></div>
        </div>
        <div class="pro-live-grid-wrap">
          <div class="pro-live-playhead" style="left:calc(58px + ${playheadPct}%);"><span>${formatProClock(live.elapsed || 0)}</span></div>
          <div class="pro-grid-shell pro-live-grid">
            <div class="pro-labels">
              <div class="pro-label head">${live.phase === "hold" ? "HOLD" : "Live"}</div>
              <div class="pro-label">L</div>
              <div class="pro-label">I</div>
              <div class="pro-label">M</div>
              <div class="pro-label">S</div>
              <div class="pro-label">W</div>
            </div>
            <div class="pro-minutes">
              ${draft.minutes
                .map((minute, index) => {
                  const isCurrent = live.phase !== "ready" && index === minuteIndex;
                  const adjustedMinute =
                    live.phase === "hold" && index === live.holdMinuteIndex
                      ? { ...minute, subBlocks: minute.subBlocks.map((block) => ({ ...block, inductionPower: 30, microwaveActive: false })) }
                      : minute;
                  return renderProMinuteCell(adjustedMinute, isCurrent ? index : -1, isCurrent ? subIndex : -1);
                })
                .join("")}
            </div>
          </div>
        </div>
        <div class="pro-live-actions">
          <button class="secondary-button" data-action="pro-live-prev">‹</button>
          <button class="secondary-button" data-action="pro-live-next">›</button>
          <span class="grow"></span>
          <button class="secondary-button" data-action="pro-live-pause">${live.paused ? "Resume" : "Pause"}</button>
          ${
            live.confirmEnd
              ? `<div class="pro-end-confirm"><span>End early?</span><button class="danger-button small" data-action="pro-live-confirm-end">Proceed</button><button class="secondary-button small" data-action="pro-live-cancel-end">x</button></div>`
              : `<button class="primary-button" data-action="pro-live-end">End Recipe</button>`
          }
          <button class="danger-button" data-action="pro-live-abort">Stop</button>
        </div>
        ${showOverlay ? renderProReadyOverlay(draft, live) : ""}
      </div>
    </div>
  `;
}

function renderProfessionalEditorModal(snapshot, modal) {
  const sourceRecipe = findRecipeById(snapshot, modal.payload.recipeId);
  const draft = getProDraft(snapshot);
  if (!sourceRecipe || !draft) return "";
  if (draft.step === "live") {
    return renderProLiveCookModal(snapshot, modal);
  }
  if (draft.step !== "timeline") {
    return renderProConfigureModal(snapshot, draft, sourceRecipe);
  }
  const minute = getSelectedProMinute(draft);
  const block = getSelectedProBlock(draft);
  const selectedMinute = Number(draft.selectedMinute) || 0;
  const selectedBlock = Number(draft.selectedBlock) || 0;
  const totalSeconds = draft.minutes.length * 60;
  return `
    <div class="modal-backdrop">
      <div class="modal-card pro-editor-modal">
        <div class="row space pro-editor-topbar">
          <div>
            <div class="eyebrow">Pro Timeline Editor</div>
            <h3>${escapeHtml(draft.displayName)}</h3>
            <p class="subtle">${escapeHtml(draft.recipeType)} | ${escapeHtml(draft.consistency)} | ${escapeHtml(`${draft.quantity}${draft.quantityUnit}`)} | ${draft.minutes.length} minutes | 4 x 15-second chunks per minute</p>
          </div>
          <div class="action-row">
            <button class="secondary-button small" data-action="pro-back-to-config">Edit</button>
            <button class="icon-button" data-action="close-modal">x</button>
          </div>
        </div>
        <div class="pro-editor-layout">
          <section class="pro-timeline-panel">
            <div class="pro-grid-shell">
              <div class="pro-labels">
                <div class="pro-label head">Time</div>
                <div class="pro-label">Lid</div>
                <div class="pro-label">Ind</div>
                <div class="pro-label">Micro</div>
                <div class="pro-label">Stir</div>
                <div class="pro-label">Water</div>
              </div>
              <div class="pro-minutes">
                ${draft.minutes.map((item) => renderProMinuteCell(item, selectedMinute, selectedBlock)).join("")}
              </div>
            </div>
            <div class="pro-editor-actions">
              <button class="secondary-button small" data-action="pro-add-minute">Add minute</button>
              <button class="secondary-button small" data-action="pro-remove-minute">Remove last minute</button>
              <button class="secondary-button small" data-action="pro-copy-block-to-minute">Copy selected block across minute</button>
            </div>
          </section>
          <aside class="pro-inspector">
            <div class="mini-title">Selected block</div>
            <div class="settings-card compact-card">
              <label class="field-label">Recipe name<input class="field-input" data-input="pro-display-name" value="${escapeHtml(draft.displayName)}"></label>
              <label class="field-label">Firmware name<input class="field-input" data-input="pro-firmware-name" value="${escapeHtml(draft.firmwareName)}"></label>
              <label class="field-label">Aliases<input class="field-input" data-input="pro-aliases" value="${escapeHtml(draft.aliases)}"></label>
            </div>
            <div class="settings-card compact-card">
              <div class="meta-grid">
                <span>Minute ${selectedMinute + 1}</span>
                <span>${selectedBlock * 15}-${(selectedBlock + 1) * 15}s</span>
              </div>
              <label class="field-label">Step label<input class="field-input" data-input="pro-minute-title" value="${escapeHtml(minute?.title || "")}"></label>
              <label class="field-label">Weight / note<input class="field-input" data-input="pro-minute-weight" value="${escapeHtml(minute?.weight || "")}"></label>
              <label class="toggle-row"><input type="checkbox" data-input="pro-lid-open" ${minute?.lidOpen ? "checked" : ""}> Lid open during this minute</label>
              <label class="field-label">
                Induction power
                <select class="field-input" data-input="pro-induction-power">
                  ${PRO_POWER_STEPS.map((value) => `<option value="${value}" ${block?.inductionPower === value ? "selected" : ""}>${value}%</option>`).join("")}
                </select>
              </label>
              <label class="field-label">
                Microwave
                <select class="field-input" data-input="pro-microwave-power">
                  ${PRO_MICROWAVE_STEPS.map((value) => `<option value="${value}" ${Number(block?.microwaveActive ? 800 : 0) === value ? "selected" : ""}>${value === 0 ? "Off" : "On"}</option>`).join("")}
                </select>
              </label>
              <label class="field-label">
                Stirrer
                <select class="field-input" data-input="pro-stirrer-speed">
                  <option value="off" ${block?.stirrerActive === false ? "selected" : ""}>Off</option>
                  ${PRO_STIRRER_SPEEDS.map((value, index) => `<option value="${value}" ${block?.stirrerActive !== false && block?.stirrerSpeed === value ? "selected" : ""}>Speed ${index + 1}${value === "medium" ? " default" : ""}</option>`).join("")}
                </select>
              </label>
              <label class="field-label">Water in this 15s block (ml)<input class="field-input" type="number" min="0" step="10" data-input="pro-water-block" value="${escapeHtml(minute?.waterBlocks?.[selectedBlock] || 0)}"></label>
            </div>
            <div class="settings-card compact-card">
              <div class="mini-title">Run Recipe</div>
              <p class="subtle">Save creates a final modified recipe using the same firmware JSON shape. Run now sends this selected recipe only when a device is chosen.</p>
              <label class="field-label">
                Device
                <select class="field-input" data-input="pro-run-slot">
                  <option value="">Choose connected device</option>
                  ${getConnectedDevices(snapshot).map((device) => `<option value="${device.slot}">Device ${device.slot} - ${escapeHtml(device.displayName)}</option>`).join("")}
                </select>
              </label>
              <div class="action-row">
                <button class="primary-button small" data-action="pro-save-final">Save final</button>
                <button class="secondary-button small" data-action="pro-save-and-run">Save and run</button>
                <button class="primary-button small" data-action="pro-live-cook">Live Cook</button>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </div>
  `;
}

function renderModal(snapshot) {
  const modal = snapshot.ui.activeModal;
  if (!modal) return "";
  if (modal.type === "figma-pro-studio") {
    const src = safeOptionalUrl(modal.payload?.src, "Figma Pro Studio") || "./pro-studio/index.html#/preset-setup";
    const orientationClass = proStudioShellOrientation === "landscape" ? "landscape" : "portrait";
    return `
      <div class="modal-backdrop figma-pro-backdrop">
        <div class="figma-pro-modal ${orientationClass}">
          <div class="figma-pro-toolbar">
            <button class="secondary-button small" data-action="pro-studio-back">Back</button>
            <strong>${escapeHtml(modal.payload?.title || "On2Cook Pro Studio")}</strong>
            <button class="secondary-button small" data-action="close-modal">Close</button>
          </div>
          <iframe class="figma-pro-frame" src="${escapeHtml(src)}" title="On2Cook Pro Studio"></iframe>
        </div>
      </div>
    `;
  }
  if (modal.type === "professional-editor") {
    return renderProfessionalEditorModal(snapshot, modal);
  }
  if (modal.type === "recipe-sheet") {
    const slot = Number(modal.payload.slot || 0);
    const device = snapshot.devices.find((item) => item.slot === slot);
    if (!device?.lastRun?.finishedAt) return "";
    const recipe = getRecipeForRunRecord(snapshot, device.lastRun);
    return `
      <div class="modal-backdrop">
        <div class="modal-card recipe-sheet-modal">
          ${renderRecipeSheetContent({ title: device.lastRun.displayName, recipe, run: device.lastRun, sourceLabel: device.displayName })}
        </div>
      </div>
    `;
  }
  if (modal.type === "live-recipe-sheet") {
    const draft = modal.payload.draft || {};
    const live = draft.live || {};
    const run = {
      displayName: draft.displayName,
      startedAt: live.startedAt || "",
      finishedAt: live.finishedAt || nowIso(),
      durationSeconds: Array.isArray(draft.minutes) ? draft.minutes.length * 60 : 0,
      actualDurationSeconds: live.actualDurationSeconds || live.elapsed || 0,
      outcome: live.outcome || "completed"
    };
    return `
      <div class="modal-backdrop">
        <div class="modal-card recipe-sheet-modal">
          ${renderRecipeSheetContent({ title: draft.displayName, recipe: null, run, draft, sourceLabel: "On2Cook Pro" })}
        </div>
      </div>
    `;
  }
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

  if (modal.type === "quick-assign-confirm") {
    return renderQuickAssignConfirmModal(snapshot, modal);
  }

  if (modal.type === "assign-recipe") {
    return renderAssignRecipeModal(snapshot, modal);
  }

  if (modal.type === "live-logs") {
    const device = getDevice(modal.payload.slot);
    if (!device) return "";
    return renderLiveLogsModal(snapshot, device);
  }

  if (modal.type === "device-manual") {
    const device = getDevice(modal.payload.slot);
    if (!device) return "";
    return renderDeviceManualModeModal(snapshot, device);
  }

  if (modal.type === "device-metadata") {
    const device = getDevice(modal.payload.slot);
    if (!device) return "";
    return renderDeviceMetadataModal(snapshot, device);
  }

  if (modal.type === "device-status") {
    const device = getDevice(modal.payload.slot);
    if (!device) return "";
    return renderDeviceStatusModal(snapshot, device);
  }

  if (modal.type === "device-firmware") {
    const device = getDevice(modal.payload.slot);
    if (!device) return "";
    return renderDeviceFirmwareModal(snapshot, device);
  }

  if (modal.type === "stored-logs") {
    const device = getDevice(modal.payload.slot);
    if (!device) return "";
    return renderStoredLogsModal(snapshot, device);
  }

  if (modal.type === "device-recipes") {
    const device = getDevice(modal.payload.slot);
    if (!device) return "";
    return renderDeviceRecipesModal(snapshot, device, modal);
  }

  if (modal.type === "delete-device-recipes-confirm") {
    return renderDeleteDeviceRecipesConfirmModal(snapshot, modal);
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
    const deviceCommandDisabled = device.connection === "connected" ? "" : "disabled";
    const moveCandidate = findConnectedSlotMoveCandidate(snapshot, device.slot);
    const recipeFilter = String(modal.payload.recipeFilter || "").trim().toLowerCase();
    const filteredRecipes = snapshot.recipes
      .filter((recipe) => recipe.selected)
      .filter((recipe) =>
        !recipeFilter ||
        recipe.displayName.toLowerCase().includes(recipeFilter) ||
        recipe.firmwareName.toLowerCase().includes(recipeFilter)
      )
      .slice(0, 40);
    return `
      <div class="modal-backdrop">
        <div class="modal-card wide refined-mobile-screen device-detail-screen">
          ${renderRefinedScreenTopBar(snapshot, "Device Details", `${device.displayName} | D${device.slot}`)}
          <div class="refined-title-row">
            <button class="icon-button refined-back-button" data-action="close-modal" aria-label="Back">${renderUiIcon("chevronLeft")}</button>
            <div>
              <div class="eyebrow">Device ${device.slot}</div>
              <h3>${escapeHtml(device.displayName)}</h3>
            </div>
            <button class="icon-button" type="button" data-action="request-status" data-slot="${device.slot}" aria-label="Refresh status">${renderUiIcon("refresh")}</button>
          </div>
          <form data-form="device-sheet" class="modal-form">
            <input type="hidden" name="slot" value="${device.slot}">
            <div class="refined-device-summary-card">
              <div class="refined-device-image">
                <img src="./assets/on2cook-logo.png" alt="" aria-hidden="true">
              </div>
              <div class="refined-device-summary-copy">
                <div class="row space">
                  <div>
                    <h3>${escapeHtml(device.displayName)}</h3>
                    <p>${escapeHtml(device.bluetoothName || "Not paired yet")}</p>
                  </div>
                  ${renderStatusPill(device.connection === "connected" ? "cooking" : "failed")}
                </div>
                <div class="refined-info-grid">
                  <span><small>Status</small><b>${escapeHtml(device.telemetry.workStatus || device.connection)}</b></span>
                  <span><small>Mode</small><b>${escapeHtml(device.telemetry.mode || "Auto")}</b></span>
                  <span><small>Firmware</small><b>${escapeHtml(device.telemetry.firmwareVersion || "Unknown")}</b></span>
                  <button class="info-grid-button" type="button" data-action="open-device-recipes" data-slot="${device.slot}"><small>Recipes</small><b>${escapeHtml((device.availableRecipeNames || []).length)}</b></button>
                </div>
              </div>
            </div>
            <details class="settings-card refined-edit-details">
              <summary>Pairing and display settings</summary>
              <div class="grid-two top-gap">
                <label class="field-label">Display name<input class="field-input" type="text" name="displayName" value="${escapeHtml(device.displayName)}" required></label>
                <label class="field-label">Bluetooth name<input class="field-input" type="text" value="${escapeHtml(device.bluetoothName || "")}" disabled></label>
                <label class="field-label">Browser device ID<input class="field-input" type="text" value="${escapeHtml(device.browserDeviceId || "")}" disabled></label>
                <label class="field-label">Connection state<input class="field-input" type="text" value="${escapeHtml(device.connection)}" disabled></label>
              </div>
            </details>
            <label class="toggle-row refined-toggle"><input type="checkbox" name="enabled" ${device.enabled ? "checked" : ""}> Device enabled for scheduling</label>
            <div class="settings-card compact-note refined-lock-note">
              <strong>${escapeHtml(device.browserDeviceId ? `Locked to ${device.bluetoothName || "a saved cooker"}` : "Not assigned to a cooker yet")}</strong>
              <p class="subtle">${escapeHtml(device.browserDeviceId ? "Reconnect will use this saved physical cooker. Use Clear pairing before assigning a different cooker to this window." : "Press Connect once and select the intended cooker. After that this window will reconnect only to that cooker.")}</p>
            </div>
            ${renderFirmwareUpdateNotice(device)}
            <div class="settings-card refined-live-card">
              <div class="meta-grid">
                <span>Firmware ${escapeHtml(device.telemetry.firmwareVersion || "Unknown")}</span>
                <span>Work status ${escapeHtml(device.telemetry.workStatus || "offline")}</span>
                <span>Remaining ${secondsLabel(device.telemetry.remainingSeconds)}</span>
                <span>Updated ${escapeHtml(formatTimestamp(device.lastUpdatedAt))}</span>
              </div>
              <p class="subtle">${escapeHtml(device.lastMessage || "No live messages yet")}</p>
            </div>
            <div class="settings-card refined-current-card">
              ${renderCurrentRecipeCard(snapshot, device, currentOrder, runtimeRecipe)}
              ${renderQueueTimelineCard(snapshot, device)}
              ${
                telemetryMode.includes("ingredient") || telemetryMode.includes("cooking") || currentOrder || hasLiveRuntime(device)
                  ? `<div class="action-row top-gap">
                      ${telemetryMode.includes("ingredient") ? `<button class="primary-button" type="button" data-action="complete-ingredients" data-slot="${device.slot}">Complete Ingredients (100)</button>` : ""}
                      ${telemetryMode.includes("cooking") ? `<button class="secondary-button" type="button" data-action="acknowledge-instruction" data-slot="${device.slot}">Acknowledge Step ${escapeHtml(device.telemetry.stepNo || 1)}</button>` : ""}
                      <button class="danger-button" type="button" data-action="abort-device" data-slot="${device.slot}">Abort recipe</button>
                    </div>`
                  : ""
              }
            </div>
            <div class="settings-card refined-quick-assign-card">
              ${renderQuickAssignCard(snapshot, device)}
            </div>
            ${renderInventorySerialDetailsCard(snapshot, device)}
            <div class="settings-card refined-recipe-access-card">
              <div class="mini-title">Recipe finder and allowed recipes</div>
              <div class="action-row">
                ${safeOptionalUrl(snapshot.settings.recipeFinder.baseUrl, "recipe finder URL") ? `<a class="link-button" href="${escapeHtml(safeOptionalUrl(snapshot.settings.recipeFinder.baseUrl, "recipe finder URL"))}" target="_blank" rel="noreferrer">Open Recipe Finder</a>` : `<span class="subtle">Recipe finder URL is not configured.</span>`}
                <button class="secondary-button" type="button" data-action="open-recipes-tab">Manage imported recipes</button>
              </div>
              <label class="field-label">
                Search imported recipes
                <input class="field-input" type="search" data-input="device-recipe-filter" data-slot="${device.slot}" value="${escapeHtml(modal.payload.recipeFilter || "")}" placeholder="Filter imported recipes">
              </label>
              <div class="subtle">Only recipes you explicitly allow here will be candidates for this device.</div>
            </div>
            <div class="settings-card refined-allowed-card">
              <div class="mini-title">Recipes allowed on this device</div>
              <div class="subtle">Orange recipes are enabled for this device. Grey recipes are imported but blocked from running here.</div>
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
            <div class="settings-card refined-actions-card">
              <div class="mini-title">Device actions</div>
              <div class="action-row">
                ${
                  device.connection === "connected"
                    ? `<button class="secondary-button" type="button" data-action="disconnect-device" data-slot="${device.slot}">Disconnect</button>`
                    : `<button class="primary-button" type="button" data-action="connect-device" data-slot="${device.slot}">Connect</button>`
                }
                ${
                  device.connection !== "connected" && moveCandidate
                    ? `<button class="secondary-button" type="button" data-action="move-connected-cooker" data-slot="${device.slot}">Use Device ${moveCandidate.slot} cooker here</button>`
                    : ""
                }
                <button class="secondary-button" type="button" data-action="sync-selected-recipes" data-slot="${device.slot}" ${deviceCommandDisabled}>Check device recipes</button>
                <button class="secondary-button" type="button" data-action="read-device-recipes" data-slot="${device.slot}" ${deviceCommandDisabled}>Read recipes</button>
                <button class="secondary-button" type="button" data-action="request-status" data-slot="${device.slot}">Refresh status</button>
                <button class="secondary-button" type="button" data-action="open-device-manual" data-slot="${device.slot}">Manual Mode</button>
                <button class="secondary-button" type="button" data-action="open-live-logs" data-slot="${device.slot}">Live Logs</button>
                <button class="danger-button" type="button" data-action="clear-device-binding" data-slot="${device.slot}">Clear pairing</button>
              </div>
            </div>
            <div class="settings-card">
              <div class="row space">
                <div class="mini-title">Saved device log</div>
                <span class="subtle">${escapeHtml((device.activity || []).length)} retained locally</span>
              </div>
              <div class="activity-list">
                ${
                  (device.activity || []).length
                    ? (device.activity || [])
                        .slice(0, 40)
                        .map(
                          (item) => `
                            <div class="activity-row ${escapeHtml(item.tone || "info")}">
                              <div class="activity-copy">
                                <span class="activity-badge ${escapeHtml(item.direction || item.tone || "info")}">${escapeHtml(item.label || item.tone || "log")}</span>
                                <span>${escapeHtml(item.text)}</span>
                              </div>
                              <span class="subtle">${escapeHtml(formatTimestamp(item.at))}</span>
                            </div>
                          `
                        )
                        .join("")
                    : `<div class="empty-card">No saved activity for this device yet.</div>`
                }
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
            <label class="field-label">Full name<input class="field-input" type="text" name="displayName" required></label>
            <label class="field-label">Email ID<input class="field-input" type="email" name="email" placeholder="name@example.com"></label>
            <label class="field-label">Mobile number<input class="field-input" type="tel" name="mobilePhone" placeholder="+91..."></label>
            <label class="field-label">WhatsApp number<input class="field-input" type="tel" name="whatsappPhone" placeholder="+91..."></label>
            <label class="field-label">Role<select class="field-input" name="role"><option value="admin">Franchise admin</option><option value="kitchen_manager">Kitchen manager</option><option value="operator">Cook / Operator</option></select></label>
            <label class="field-label">Status<select class="field-input" name="status"><option value="invited">Invited</option><option value="active">Active</option><option value="suspended">Suspended</option></select></label>
            <label class="toggle-row"><input type="checkbox" name="canAddRecipes"> Can add/select recipes from Global Recipes</label>
            <label class="toggle-row"><input type="checkbox" name="canEditRecipes" checked> Can optimize/edit selected recipes</label>
            <label class="toggle-row"><input type="checkbox" name="canManageRecipeAccess" checked> Can assign recipes to devices/operators</label>
            <label class="toggle-row"><input type="checkbox" name="managerMode"> Operator acts as kitchen manager in small kitchen</label>
            <p class="subtle">Operators normally run only the recipes selected by the master admin or kitchen manager. Leave edit/add unchecked for a run-only cook/operator.</p>
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
            <h3>${mode === "signup" ? "Create cloud account" : "Sign in with Email"}</h3>
            <button class="icon-button" data-action="close-modal">x</button>
          </div>
          <form data-form="cloud-auth" class="modal-form">
            <input type="hidden" name="mode" value="${mode}">
            ${
              mode === "signup"
                ? `<label class="field-label">Full name<input class="field-input" type="text" name="fullName" value="${escapeHtml(localUser.displayName || "")}" required></label>`
                : ""
            }
            <label class="field-label">Email<input class="field-input" type="email" name="email" value="${escapeHtml(localUser.email || "")}" required></label>
            <label class="field-label">Password<input class="field-input" type="password" name="password" required></label>
            ${
              mode === "signup"
                ? `
                  <label class="field-label">Mobile phone<input class="field-input" type="tel" name="mobilePhone" placeholder="+91..."></label>
                  <label class="field-label">WhatsApp phone<input class="field-input" type="tel" name="whatsappPhone" placeholder="+91..."></label>
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

function renderLoginGate(snapshot) {
  return `
    <div class="surface login-surface">
      ${snapshot.ui.toast ? `<div class="toast ${snapshot.ui.toastTone}">${escapeHtml(snapshot.ui.toast)}</div>` : ""}
      <section class="login-shell">
        <div class="login-brand-card">
          <img class="brand-logo" src="./assets/on2cook-logo.png" alt="On2Cook">
          <div>
            <div class="eyebrow">On2Cook Cloud</div>
            <h1>Kitchen Login</h1>
            <p>Sign in to load your kitchen role, allowed recipes, devices, and cloud recipe library.</p>
          </div>
        </div>
        <div class="login-card-grid">
          <article class="login-card">
            <div class="mini-title">Sign in with Email</div>
            <p class="subtle">Use the account provided for this kitchen.</p>
            <button class="primary-button" data-action="open-cloud-login">Sign in with Email</button>
          </article>
          <article class="login-card">
            <div class="mini-title">Continue as Guest User</div>
            <p class="subtle">Continue locally for device pairing and demo testing.</p>
            <button class="secondary-button" data-action="demo-auth-bypass">Continue as Guest User</button>
          </article>
        </div>
      </section>
      ${renderModal(snapshot)}
    </div>
  `;
}

function renderOrderNotice(snapshot) {
  const notice = snapshot.ui.orderNotice;
  if (!notice?.id) return "";
  return `
    <div class="order-notice" role="status" aria-live="polite">
      <button class="order-notice-main" data-action="open-order-notice" data-order-id="${escapeHtml(notice.id)}">
        <span class="order-notice-kicker">New order</span>
        <strong>${escapeHtml(notice.orderId || "Order")} - ${escapeHtml(notice.itemName || "Recipe")}</strong>
        <span>${escapeHtml(formatAgo(notice.createdAt))}</span>
      </button>
      <button class="order-notice-close" data-action="dismiss-order-notice" aria-label="Dismiss new order notice">x</button>
    </div>
  `;
}

function getApkRailFrames() {
  const rail = document.querySelector(".apk-rail");
  return {
    rail,
    frames: rail ? Array.from(rail.querySelectorAll(".phone-frame")) : []
  };
}

function getCurrentApkRailIndex(rail, frames) {
  if (!rail || !frames.length) return 0;
  const railCenter = rail.scrollLeft + rail.clientWidth / 2;
  return frames.reduce(
    (best, frame, index) => {
      const center = frame.offsetLeft + frame.offsetWidth / 2;
      const distance = Math.abs(center - railCenter);
      return distance < best.distance ? { index, distance } : best;
    },
    { index: 0, distance: Number.POSITIVE_INFINITY }
  ).index;
}

function setApkScreenSwitcherActive(index) {
  lastApkScreenIndex = Math.max(0, Number(index) || 0);
  document.querySelectorAll(".apk-screen-switcher [data-apk-screen-index]").forEach((button) => {
    button.classList.toggle("active", Number(button.dataset.apkScreenIndex) === lastApkScreenIndex);
  });
}

function scrollApkRailToIndex(index, behavior = "smooth") {
  const { rail, frames } = getApkRailFrames();
  if (!rail || !frames.length) return;
  const safeIndex = Math.max(0, Math.min(frames.length - 1, Number(index) || 0));
  const frame = frames[safeIndex];
  const left = frame.offsetLeft - Math.max(0, (rail.clientWidth - frame.offsetWidth) / 2);
  rail.scrollTo({ left, behavior });
  setApkScreenSwitcherActive(safeIndex);
  scheduleSaveUiSessionState(null, behavior === "smooth" ? 420 : 80);
}

function restoreApkRailFromUiState(snapshot) {
  if (!IS_APK_MODE) return;
  const index = Math.max(0, Number(snapshot.ui.apkScreenIndex ?? lastApkScreenIndex) || 0);
  if (index <= 0) {
    setApkScreenSwitcherActive(0);
    return;
  }
  window.requestAnimationFrame(() => scrollApkRailToIndex(index, "auto"));
}

function renderApkScreenSwitcher(snapshot) {
  if (!IS_APK_MODE) return "";
  const activeIndex = Math.max(0, Number(snapshot.ui.apkScreenIndex ?? lastApkScreenIndex) || 0);
  const items = [
    ["Home", 0],
    ...snapshot.devices.map((device, index) => [`D${device.slot}`, index + 1])
  ];
  return `
    <nav class="apk-screen-switcher" aria-label="Device screens">
      ${items
        .map(([label, index]) => `
          <button
            class="apk-screen-button ${index === activeIndex ? "active" : ""}"
            type="button"
            data-action="jump-apk-screen"
            data-apk-screen-index="${index}"
          >${escapeHtml(label)}</button>
        `)
        .join("")}
    </nav>
  `;
}

function render() {
  const snapshot = state();
  const scrollState = captureScrollState();
  const signedIn = Boolean(cloudRuntime.session?.id || snapshot.ui.demoAuthBypass);
  if (!signedIn) {
    app.innerHTML = renderLoginGate(snapshot);
    restoreScrollState(scrollState);
    return;
  }
  app.innerHTML = `
    <div class="surface ${IS_APK_MODE ? "apk-surface" : ""}">
      ${snapshot.ui.toast ? `<div class="toast ${snapshot.ui.toastTone}">${escapeHtml(snapshot.ui.toast)}</div>` : ""}
      ${renderOrderNotice(snapshot)}
      ${renderNotificationDrawer(snapshot)}
      ${renderApkScreenSwitcher(snapshot)}
      <main class="screen-rail ${IS_APK_MODE ? "apk-rail" : ""}" data-scroll-key="screen-rail">
        ${renderControlPhone(snapshot)}
        ${snapshot.devices.map((device) => renderDevicePhone(snapshot, device)).join("")}
      </main>
      ${renderModal(snapshot)}
    </div>
  `;
  restoreScrollState(scrollState);
  restoreApkRailFromUiState(snapshot);
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
    id: safeRandomId("id"),
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

function saveProfessionalRecipe() {
  const snapshot = state();
  const modal = snapshot.ui.activeModal;
  const draft = modal?.payload?.draft;
  if (!modal || modal.type !== "professional-editor" || !draft) return null;
  const sourceRecipe = findRecipeById(snapshot, modal.payload.recipeId);
  if (!sourceRecipe) {
    showToast("Recipe not found", "error");
    return null;
  }
  const baseRecipe =
    sourceRecipe.type === "final" && sourceRecipe.baseRecipeId ? findRecipeById(snapshot, sourceRecipe.baseRecipeId) || sourceRecipe : sourceRecipe;
  const recipeJson = proDraftToFirmwareRecipe(sourceRecipe, draft);
  const finalRecipe = createFinalRecipeFromBase(baseRecipe, recipeJson, {
    displayName: draft.displayName,
    firmwareName: draft.firmwareName,
    aliases: draft.aliases,
    imageDataUrl: sourceRecipe.imageDataUrl
  });
  if (sourceRecipe.type === "final") {
    finalRecipe.id = sourceRecipe.id;
    finalRecipe.createdAt = sourceRecipe.createdAt;
  }
  mutate((draftState) => {
    draftState.recipes = draftState.recipes.filter(
      (recipe) =>
        recipe.id !== sourceRecipe.id &&
        !(recipe.type === "final" && recipe.baseRecipeId === baseRecipe.id && recipe.id !== finalRecipe.id)
    );
    const baseDraft = draftState.recipes.find((recipe) => recipe.id === baseRecipe.id);
    if (baseDraft) baseDraft.selected = false;
    draftState.recipes.unshift(finalRecipe);
    draftState.ui.recipeMode = "final";
    syncSelectedRecipesToAllDevices(draftState);
  });
  return state().recipes.find((recipe) => recipe.id === finalRecipe.id) || finalRecipe;
}

async function saveLiveDraftToLibrary(newName) {
  const snapshot = state();
  const modal = snapshot.ui.activeModal;
  const draft = modal?.payload?.draft;
  if (!modal || modal.type !== "live-recipe-sheet" || !draft) {
    showToast("Recipe sheet is not available for saving", "error");
    return null;
  }
  const cleanName = String(newName || "").trim();
  if (!cleanName) {
    showToast("Enter a new recipe name before saving", "warning");
    return null;
  }
  const originalNames = new Set(
    [
      draft.displayName,
      draft.firmwareName,
      ...(Array.isArray(draft.aliases) ? draft.aliases : String(draft.aliases || "").split(","))
    ]
      .map((item) => normalizeRecipeNameKey(item))
      .filter(Boolean)
  );
  if (originalNames.has(normalizeRecipeNameKey(cleanName))) {
    showToast("Use a new recipe name. The saved recipe cannot use the old name.", "warning");
    return null;
  }
  if (snapshot.recipes.some((recipe) => normalizeRecipeNameKey(recipe.displayName) === normalizeRecipeNameKey(cleanName) || normalizeRecipeNameKey(recipe.firmwareName) === normalizeRecipeNameKey(cleanName))) {
    showToast("That recipe name already exists in the library", "warning");
    return null;
  }
  const sourceRecipe = findRecipeById(snapshot, draft.recipeId) || snapshot.recipes.find((recipe) => recipe.selected) || snapshot.recipes[0];
  if (!sourceRecipe) {
    showToast("No base recipe is available for saving", "error");
    return null;
  }
  const recipeJson = proDraftToFirmwareRecipe(sourceRecipe, {
    ...draft,
    displayName: cleanName,
    firmwareName: cleanName,
    aliases: cleanName
  });
  const finalRecipe = createFinalRecipeFromBase(sourceRecipe, recipeJson, {
    displayName: cleanName,
    firmwareName: cleanName,
    aliases: cleanName,
    imageDataUrl: draft.dishPhotoDataUrl || sourceRecipe.imageDataUrl || ""
  });
  finalRecipe.source = "live-final";
  finalRecipe.selected = true;
  mutate((draftState) => {
    draftState.recipes.unshift(finalRecipe);
    draftState.ui.activeTab = "queue";
    syncSelectedRecipesToAllDevices(draftState);
  });
  const saved = state().recipes.find((recipe) => recipe.id === finalRecipe.id) || finalRecipe;
  const syncResult = await syncImportedRecipeToCloud(saved);
  if (syncResult.synced) {
    showToast(`${saved.displayName} saved to library and synced to cloud`, "success");
  } else if (syncResult.reason === "not-signed-in") {
    showToast(`${saved.displayName} saved locally. Sign in to sync it to cloud.`, "success");
  } else {
    showToast(`${saved.displayName} saved locally. Cloud sync can retry later.`, "warning");
  }
  returnToQueueContext();
  return saved;
}

async function importRecipeRecord(result, options = {}) {
  const recipeJson = structuredClone(result.recipeJson);
  const displayName = Array.isArray(recipeJson.name) ? recipeJson.name[0] : recipeJson.name;
  const recipeSignature = recipeSignatureFromJson(recipeJson);
  const record = {
    id: safeRandomId("id"),
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
      id: safeRandomId("id"),
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
  const entryZipUrl = safeOptionalUrl(entry.zipUrl, "global recipe ZIP");
  const result = entryZipUrl
    ? await importRecipeZipUrl(`${entryZipUrl}?v=${RECIPE_ARCHIVE_VERSION}`)
    : createImportResultFromCatalogEntry(entry);
  return importRecipeRecord(result, {
    zipUrl: entryZipUrl,
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
    const role = String(formData.get("role") || "operator");
    const adminLike = role === "admin" || role === "main_admin";
    const managerLike = adminLike || role === "kitchen_manager";
    const localUser = {
      id: safeRandomId("id"),
      facilityId: state().currentFacilityId,
      email: String(formData.get("email") || "").trim(),
      mobilePhone: String(formData.get("mobilePhone") || "").trim(),
      whatsappPhone: String(formData.get("whatsappPhone") || "").trim(),
      displayName: String(formData.get("displayName") || "").trim(),
      role,
      status: String(formData.get("status") || "invited"),
      managerMode: Boolean(formData.get("managerMode")),
      canAddRecipes: adminLike || Boolean(formData.get("canAddRecipes")),
      canEditRecipes: managerLike || Boolean(formData.get("canEditRecipes")),
      canManageRecipeAccess: managerLike || Boolean(formData.get("canManageRecipeAccess")),
      createdAt: nowIso()
    };
    if (!localUser.email && !localUser.mobilePhone) {
      showToast("Add either an email ID or mobile number for this user.", "warning");
      return;
    }
    mutate((draft) => {
      draft.users.push(localUser);
    });
    if (cloudRuntime.session?.id) {
      try {
        await profileService.createManagedProfile(localUser, cloudRuntime.profile || null);
        setCloudRuntime({ lastSummary: `${localUser.displayName} profile saved to cloud.`, lastError: "" });
      } catch (error) {
        setCloudRuntime({ lastError: userFacingCloudError(error, "Local user saved, but cloud profile sync is unavailable right now.") });
      }
    }
    closeModal();
    showToast("User added with permissions", "success");
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
        const existingProfile = await profileService.getMine(cloudRuntime.session).catch(() => null);
        if (existingProfile) {
          setCloudRuntime({ profile: existingProfile });
        } else {
          const selectedRole = "operator";
          await profileService.upsertCurrentProfile(
            cloudRuntime.session,
            getCurrentUser(state()),
            {
              full_name: String(formData.get("fullName") || "").trim(),
              mobile_phone: String(formData.get("mobilePhone") || "").trim(),
              whatsapp_phone: String(formData.get("whatsappPhone") || "").trim(),
              role: selectedRole,
              can_add_recipes: selectedRole === "main_admin" || selectedRole === "admin",
              can_edit_recipes: selectedRole !== "operator" && selectedRole !== "cook",
              can_manage_recipe_access: selectedRole !== "operator" && selectedRole !== "cook",
              status: "active"
            }
          );
          await refreshCloudRuntime();
        }
        await syncCloudSessionToLocalUser();
      }
      setCloudRuntime({
        lastSummary: mode === "signup" ? "Cloud account created and profile synced." : "Cloud sign-in successful.",
        lastError: ""
      });
      closeModal();
      showToast(mode === "signup" ? "Cloud account created" : "Signed in to cloud", "success");
    } catch (error) {
      const message = userFacingCloudError(error, "Cloud sign-in failed. Please check the account details and try again.");
      setCloudRuntime({ lastError: message });
      showToast(message, "error");
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
    const zipUrl = safeOptionalUrl(formData.get("zipUrl"), "recipe import ZIP");
    updateNestedSetting("recipeFinder.lastZipUrl", zipUrl);
    if (!zipUrl) {
      showToast("Paste a valid recipe ZIP URL first", "warning");
      return;
    }
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
      showToast(error.message || "Recipe import failed.", "error");
    }
  }
}

async function handleClick(event) {
  const button = event.target.closest("[data-action]");
  if (!button) return;
  const action = button.dataset.action;
  const activeDraft = state().ui.activeModal?.type === "professional-editor" ? state().ui.activeModal.payload?.draft : null;
  if (activeDraft?.live?.confirmEnd && !["pro-live-confirm-end", "pro-live-cancel-end", "pro-live-end"].includes(action)) {
    updateProDraft((draft) => {
      if (draft.live) draft.live.confirmEnd = false;
    });
  }

  if (action === "switch-tab") {
    const perms = currentPermissions(state());
    if (button.dataset.tab === "global" && !perms.canSelectGlobalRecipes) {
      showToast("Your login can run selected recipes only. Global Recipes is controlled by the master admin.", "warning");
      return;
    }
    if (button.dataset.tab === "manual") {
      showToast("Manual Mode is available inside each device screen.", "info");
      return;
    }
    mutate((draft) => {
      draft.ui.activeTab = button.dataset.tab;
    });
    return;
  }
  if (action === "open-order-notice") {
    mutate((draft) => {
      draft.ui.activeTab = "orders";
      draft.ui.orderMode = "current";
      draft.ui.orderNotice = null;
    });
    return;
  }
  if (action === "dismiss-order-notice") {
    mutate((draft) => {
      draft.ui.orderNotice = null;
    });
    return;
  }
  if (action === "jump-apk-screen") {
    const apkScreenIndex = Math.max(0, Number(button.dataset.apkScreenIndex) || 0);
    lastApkScreenIndex = apkScreenIndex;
    mutate((draft) => {
      draft.ui.apkScreenIndex = apkScreenIndex;
    });
    window.requestAnimationFrame(() => scrollApkRailToIndex(apkScreenIndex));
    return;
  }
  if (action === "order-jump-device") {
    const slot = Number(button.dataset.slot) || 1;
    openModal("device-sheet", { slot });
    return;
  }
  if (action === "view-device-queue") {
    scrollDeviceQueueTimelineIntoView(Number(button.dataset.slot) || 1);
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
    if (!currentPermissions(state()).canSelectGlobalRecipes) {
      showToast("Only permitted admins can add recipes from the global library.", "warning");
      return;
    }
    await addPickedGlobalRecipesToRecipeList();
    return;
  }
  if (action === "global-recipe-import-one") {
    if (!currentPermissions(state()).canSelectGlobalRecipes) {
      showToast("Only permitted admins can add recipes from the global library.", "warning");
      return;
    }
    const entry = getRecipeCatalog(state()).find((item) => item.id === button.dataset.recipeCatalogId);
    if (!entry) {
      showToast("Recipe not found in the global library", "error");
      return;
    }
    const recipe = await ensureGlobalCatalogRecipeImported(entry, { ensureSelected: true });
    showToast(recipe ? `${recipe.displayName} added to the Recipe list` : "Unable to add recipe", recipe ? "success" : "error");
    return;
  }
  if (action === "global-recipes-add-to-orders") {
    if (!currentPermissions(state()).canSelectGlobalRecipes) {
      showToast("Only permitted admins can create orders directly from the global library.", "warning");
      return;
    }
    await addPickedGlobalRecipesToOrders();
    return;
  }
  if (action === "global-recipes-remove-from-list") {
    if (!currentPermissions(state()).canSelectGlobalRecipes) {
      showToast("Only permitted admins can remove recipes from the kitchen list.", "warning");
      return;
    }
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
  if (action === "scale-recipe-now") {
    await saveScaledRecipe(button.dataset.recipeId, Number(button.dataset.targetQuantity), button.dataset.intensity || "medium");
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
  if (action === "open-notification-drawer") {
    mutate((draft) => {
      draft.ui.notificationDrawerOpen = true;
    });
    return;
  }
  if (action === "close-notification-drawer") {
    mutate((draft) => {
      draft.ui.notificationDrawerOpen = false;
    });
    return;
  }
  if (action === "mark-notifications-read") {
    mutate((draft) => {
      draft.ui.notifications = (draft.ui.notifications || []).map((item) => ({ ...item, read: true }));
    });
    return;
  }
  if (action === "notification-action") {
    await runNotificationAction(button.dataset.notificationId || "");
    return;
  }
  if (action === "return-device-sheet") {
    openModal("device-sheet", { slot: Number(button.dataset.slot) });
    return;
  }
  if (action === "return-device-recipes") {
    openModal("device-recipes", { slot: Number(button.dataset.slot), query: "", filter: "all", selectedNames: [] });
    return;
  }
  if (action === "pro-studio-back") {
    handleProStudioBack();
    return;
  }
  if (action === "close-live-result-to-queue" || action === "close-live-sheet-to-queue") {
    returnToQueueContext();
    return;
  }
  if (action === "connect-device") {
    await connectDevice(button.dataset.slot);
    return;
  }
  if (action === "move-connected-cooker") {
    await moveConnectedCookerToSlot(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "connect-all-devices") {
    await connectAllDevices();
    return;
  }
  if (action === "disconnect-device") {
    await disconnectDevice(button.dataset.slot);
    return;
  }
  if (action === "request-status") {
    await requestDeviceStatusWindow(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-request-status") {
    const slot = Number(button.dataset.slot);
    await ble.requestStatus(slot).then(() => {
      addNotification({
        type: "device",
        title: "Device status refreshed",
        deviceSlot: slot,
        message: "Manual Mode status request was sent to the cooker.",
        action: { type: "device", label: "Open device", slot }
      });
    }).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "open-device-metadata") {
    openModal("device-metadata", { slot: Number(button.dataset.slot) });
    return;
  }
  if (action === "open-device-recipes") {
    openModal("device-recipes", { slot: Number(button.dataset.slot), query: "", filter: "all", selectedNames: [] });
    return;
  }
  if (action === "device-recipes-refresh") {
    const slot = Number(button.dataset.slot);
    try {
      const names = await refreshDeviceRecipeInventory(slot, { force: true, timeoutMs: 4500 });
      showToast(`Device ${slot} recipe list refreshed: ${names.length} found`, "success");
      addNotification({
        type: "logs",
        title: "Recipe list refreshed",
        deviceSlot: slot,
        message: `${names.length} recipe${names.length === 1 ? "" : "s"} found on device.`,
        action: { type: "device-recipes", label: "View recipes", slot }
      });
    } catch (error) {
      showToast(error.message, "error");
      addNotification({
        type: "error",
        title: "Recipe upload failed",
        deviceSlot: slot,
        message: error.message,
        action: { type: "device-recipes", label: "Retry list", slot }
      });
    }
    openModal("device-recipes", { slot, query: "", filter: "all", selectedNames: [] });
    return;
  }
  if (action === "device-recipes-add") {
    openModal("assign-recipe", { slot: Number(button.dataset.slot), query: "", mode: "upload" });
    return;
  }
  if (action === "device-recipes-filter") {
    const slot = Number(button.dataset.slot);
    mutate((draft) => {
      if (draft.ui.activeModal?.type !== "device-recipes" || Number(draft.ui.activeModal.payload?.slot) !== slot) return draft;
      draft.ui.activeModal.payload.filter = button.dataset.filter || "all";
    });
    return;
  }
  if (action === "toggle-device-recipe-selected") {
    const slot = Number(button.dataset.slot);
    const recipeName = button.dataset.recipeName || "";
    mutate((draft) => {
      if (draft.ui.activeModal?.type !== "device-recipes" || Number(draft.ui.activeModal.payload?.slot) !== slot) return draft;
      const selected = new Map((draft.ui.activeModal.payload.selectedNames || []).map((name) => [normalizeRecipeNameKey(name), name]));
      const key = normalizeRecipeNameKey(recipeName);
      if (selected.has(key)) selected.delete(key);
      else selected.set(key, recipeName);
      draft.ui.activeModal.payload.selectedNames = [...selected.values()];
    });
    return;
  }
  if (action === "delete-selected-device-recipes") {
    const slot = Number(button.dataset.slot);
    const modal = state().ui.activeModal;
    const selectedNames = modal?.type === "device-recipes" && Number(modal.payload?.slot) === slot ? modal.payload.selectedNames || [] : [];
    requestDeviceRecipeDelete(slot, selectedNames);
    return;
  }
  if (action === "device-recipe-delete-request") {
    requestDeviceRecipeDelete(Number(button.dataset.slot), [button.dataset.recipeName || ""]);
    return;
  }
  if (action === "confirm-device-recipes-delete") {
    let names = [];
    try {
      names = JSON.parse(button.dataset.recipeNames || "[]");
    } catch {
      names = [];
    }
    await deleteDeviceRecipes(Number(button.dataset.slot), names).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "device-recipe-run") {
    await runDeviceStoredRecipe(Number(button.dataset.slot), button.dataset.recipeName || "").catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "device-recipe-queue") {
    queueDeviceStoredRecipe(Number(button.dataset.slot), button.dataset.recipeName || "");
    return;
  }
  if (action === "device-recipe-update") {
    await updateDeviceRecipe(Number(button.dataset.slot), button.dataset.recipeName || "").catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "request-firmware") {
    await requestDeviceFirmwareWindow(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "start-firmware-update") {
    await startFirmwareUpdateForDevice(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "open-stored-logs") {
    await listDeviceLogs(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "open-live-logs") {
    await openLiveLogs(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "start-live-logs") {
    await openLiveLogs(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "stop-live-logs") {
    await stopLiveLogs(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "clear-live-logs") {
    mutate((draft) => {
      const device = draft.devices.find((item) => item.slot === Number(button.dataset.slot));
      if (!device) return draft;
      const liveLog = ensureDeviceLiveLogState(device);
      liveLog.entries = [];
      liveLog.updatedAt = nowIso();
    });
    return;
  }
  if (action === "list-logs") {
    await listDeviceLogs(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "read-device-log") {
    await readDeviceLog(Number(button.dataset.slot), button.dataset.fileName || "").catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "export-device-log") {
    exportDeviceLog(Number(button.dataset.slot), button.dataset.format || "txt");
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
  if (action === "queue-move-up") {
    reorderDeviceQueueItem(Number(button.dataset.slot), button.dataset.orderId || "", "up");
    return;
  }
  if (action === "queue-move-down") {
    reorderDeviceQueueItem(Number(button.dataset.slot), button.dataset.orderId || "", "down");
    return;
  }
  if (action === "queue-move-next") {
    reorderDeviceQueueItem(Number(button.dataset.slot), button.dataset.orderId || "", "next");
    return;
  }
  if (action === "queue-start-now") {
    await startQueuedOrderNow(Number(button.dataset.slot), button.dataset.orderId || "");
    return;
  }
  if (action === "queue-force-start") {
    await stopCurrentAndStartQueued(Number(button.dataset.slot), button.dataset.orderId || "");
    return;
  }
  if (action === "queue-cook-again") {
    queueCookAgainFromHistory(Number(button.dataset.slot), button.dataset.historyKey || "");
    return;
  }
  if (action === "quick-assign-chip") {
    const slot = Number(button.dataset.slot);
    const device = state().devices.find((item) => item.slot === slot);
    openModal("quick-assign-confirm", {
      slot,
      recipeId: button.dataset.recipeId || "",
      action: device && isDeviceActivelyCooking(device) ? "queue" : ""
    });
    return;
  }
  if (action === "open-assign-recipe") {
    openModal("assign-recipe", { slot: Number(button.dataset.slot), query: "" });
    return;
  }
  if (action === "assign-recipe-action") {
    if (button.dataset.assignAction === "upload") {
      await executeDeviceRecipeAssignment({
        slot: Number(button.dataset.slot),
        recipeId: button.dataset.recipeId || "",
        catalogId: button.dataset.catalogId || "",
        action: "upload",
        source: button.dataset.catalogId ? "Global Recipes" : "Recipes on Device"
      }).catch((error) => showToast(error.message || "Unable to upload recipe.", "error"));
      return;
    }
    openModal("quick-assign-confirm", {
      slot: Number(button.dataset.slot),
      recipeId: button.dataset.recipeId || "",
      catalogId: button.dataset.catalogId || "",
      action: button.dataset.assignAction || "queue",
      source: button.dataset.catalogId ? "Global Recipes" : "Quick Assign"
    });
    return;
  }
  if (action === "confirm-quick-assignment") {
    await executeDeviceRecipeAssignment({
      slot: Number(button.dataset.slot),
      recipeId: button.dataset.recipeId || "",
      catalogId: button.dataset.catalogId || "",
      action: button.dataset.assignAction || "queue",
      source: button.dataset.catalogId ? "Global Recipes" : "Quick Assign"
    }).catch((error) => showToast(error.message || "Unable to assign recipe.", "error"));
    return;
  }
  if (action === "run-recipe-on-device") {
    const slot = Number(button.dataset.slot);
    const recipeId = button.dataset.recipeId || "";
    const snapshot = state();
    const device = snapshot.devices.find((item) => item.slot === slot);
    const recipe = findRecipeById(snapshot, recipeId);
    if (!device || device.connection !== "connected") {
      showToast(`Device ${slot || ""} is offline. Connect it before assigning this recipe.`, "warning");
      return;
    }
    if (!recipe) {
      showToast("Recipe not found.", "error");
      return;
    }
    if (!isRecipeAllowedOnDevice(snapshot, device, recipe.id)) {
      showToast(`${recipe.displayName} is not enabled on Device ${device.slot}`, "warning");
      return;
    }
    const runState = getDeviceRunState(snapshot, device);
    const result = await runDeviceRecipe(device.slot, recipe.id);
    if (result === "started") {
      showToast(`${recipe.displayName} starting on Device ${device.slot}`, "success");
    } else if (runState.status !== "idle") {
      showToast(`${recipe.displayName} added to Device ${device.slot} queue`, "success");
    }
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
  if (action === "open-device-manual") {
    const slot = Number(button.dataset.slot) || 1;
    mutate((draft) => {
      draft.ui.manualMode.slot = slot;
    });
    openModal("device-manual", { slot });
    return;
  }
  if (action === "open-device-recipe-sheet") {
    openModal("recipe-sheet", { slot: Number(button.dataset.slot) });
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
    if (!currentPermissions(state()).canCreateBaseRecipes) {
      showToast("Only permitted admins can enable or disable kitchen recipes.", "warning");
      return;
    }
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
    if (!currentPermissions(state()).canEditDevicePermissions) {
      showToast("Only kitchen managers or admins can assign recipes to devices.", "warning");
      return;
    }
    toggleRecipePermission(button.dataset.slot, button.dataset.recipeId);
    return;
  }
  if (action === "create-final-recipe" || action === "edit-final-recipe" || action === "open-professional-editor") {
    if (!currentPermissions(state()).canCreateFinalRecipes) {
      showToast("Your login can run recipes only. Recipe editing is disabled.", "warning");
      return;
    }
    openProfessionalEditor(button.dataset.recipeId);
    return;
  }
  if (action === "pro-select-minute") {
    updateProDraft((draft) => {
      draft.selectedMinute = Number(button.dataset.minute) || 0;
      draft.selectedBlock = 0;
    });
    return;
  }
  if (action === "pro-select-block") {
    updateProDraft((draft) => {
      draft.selectedMinute = Number(button.dataset.minute) || 0;
      draft.selectedBlock = Number(button.dataset.block) || 0;
    });
    return;
  }
  if (action === "pro-toggle-water") {
    updateProDraft((draft) => {
      const minute = draft.minutes[Number(button.dataset.minute) || 0];
      const blockIndex = Number(button.dataset.block) || 0;
      if (!minute?.waterBlocks) return;
      minute.waterBlocks[blockIndex] = !minute.waterBlocks[blockIndex];
      draft.selectedMinute = Number(button.dataset.minute) || 0;
      draft.selectedBlock = blockIndex;
    });
    return;
  }
  if (action === "pro-add-minute") {
    updateProDraft((draft) => {
      draft.minutes.push(makeProMinute(draft.minutes.length));
      draft.selectedMinute = draft.minutes.length - 1;
      draft.selectedBlock = 0;
    });
    return;
  }
  if (action === "pro-remove-minute") {
    updateProDraft((draft) => {
      if (draft.minutes.length <= 1) return;
      draft.minutes.pop();
      draft.minutes.forEach((minute, index) => {
        minute.minuteIndex = index;
        minute.id = `min-${index}`;
      });
      draft.selectedMinute = Math.min(Number(draft.selectedMinute) || 0, draft.minutes.length - 1);
    });
    return;
  }
  if (action === "pro-copy-block-to-minute") {
    updateProDraft((draft) => {
      const minute = getSelectedProMinute(draft);
      const block = getSelectedProBlock(draft);
      if (!minute || !block) return;
      minute.subBlocks = minute.subBlocks.map(() => structuredClone(block));
    });
    return;
  }
  if (action === "pro-set-diet") {
    updateProDraft((draft) => {
      draft.dietType = button.dataset.value || "veg";
    });
    return;
  }
  if (action === "pro-set-recipe-type") {
    updateProDraft((draft) => {
      draft.recipeType = button.dataset.value || "gravy";
    });
    return;
  }
  if (action === "pro-set-consistency") {
    updateProDraft((draft) => {
      draft.consistency = button.dataset.value || "medium";
    });
    return;
  }
  if (action === "pro-set-quantity-unit") {
    updateProDraft((draft) => {
      draft.quantityUnit = button.dataset.value || "g";
    });
    return;
  }
  if (action === "pro-adjust-quantity") {
    updateProDraft((draft) => {
      const delta = Number(button.dataset.delta) || 0;
      draft.quantity = Math.max(1, Math.round((Number(draft.quantity) || 500) + delta));
    });
    return;
  }
  if (action === "pro-add-ingredient") {
    updateProDraft((draft) => {
      draft.ingredients.push({
        id: safeRandomId("ingredient"),
        name: "New Ingredient",
        quantity: 0,
        unit: draft.quantityUnit === "ml" ? "ml" : "g",
        source: {}
      });
    });
    return;
  }
  if (action === "pro-remove-ingredient") {
    updateProDraft((draft) => {
      const index = Number(button.dataset.index);
      if (!Number.isFinite(index)) return;
      draft.ingredients.splice(index, 1);
    });
    return;
  }
  if (action === "pro-open-timeline-editor") {
    updateProDraft((draft) => {
      draft.step = "timeline";
    });
    return;
  }
  if (action === "pro-back-to-config") {
    updateProDraft((draft) => {
      draft.step = "configure";
    });
    return;
  }
  if (action === "pro-live-cook") {
    updateProDraft((draft) => {
      draft.step = "live";
      draft.live = {
        phase: "ready",
        elapsed: 0,
        holdElapsed: 0,
        holds: [],
        paused: false,
        outcome: "",
        startedAt: ""
      };
    });
    ensureProLiveTimer();
    return;
  }
  if (action === "pro-live-start") {
    updateProDraft((draft) => {
      const live = getProLive(draft);
      live.phase = "running";
      live.paused = false;
      live.startedAt = live.startedAt || nowIso();
      live.holds = [{ minuteIndex: 0, type: "ingredients", durationSec: 0 }];
    });
    ensureProLiveTimer();
    return;
  }
  if (action === "pro-live-resume") {
    updateProDraft((draft) => {
      const live = getProLive(draft);
      live.holds = [
        ...(live.holds || []),
        {
          minuteIndex: Number(live.holdMinuteIndex || 0),
          type: "ingredients",
          durationSec: Number(live.holdElapsed || 0)
        }
      ];
      live.phase = "running";
      live.holdElapsed = 0;
    });
    ensureProLiveTimer();
    return;
  }
  if (action === "pro-live-pause") {
    updateProDraft((draft) => {
      const live = getProLive(draft);
      live.paused = !live.paused;
    });
    return;
  }
  if (action === "pro-live-end") {
    updateProDraft((draft) => {
      const live = getProLive(draft);
      live.confirmEnd = true;
    });
    return;
  }
  if (action === "pro-live-cancel-end") {
    updateProDraft((draft) => {
      const live = getProLive(draft);
      live.confirmEnd = false;
    });
    return;
  }
  if (action === "pro-live-confirm-end") {
    updateProDraft((draft) => {
      const live = getProLive(draft);
      live.phase = "aborted";
      live.outcome = "aborted";
      live.confirmEnd = false;
      live.paused = false;
      live.finishedAt = nowIso();
      live.actualDurationSeconds = Number(live.elapsed || 0);
    });
    ensureProLiveTimer();
    return;
  }
  if (action === "pro-live-abort") {
    updateProDraft((draft) => {
      const live = getProLive(draft);
      live.phase = "aborted";
      live.outcome = "aborted";
      live.confirmEnd = false;
      live.paused = false;
      live.finishedAt = nowIso();
      live.actualDurationSeconds = Number(live.elapsed || 0);
    });
    ensureProLiveTimer();
    return;
  }
  if (action === "pro-live-back-editor") {
    stopProLiveTimer();
    updateProDraft((draft) => {
      draft.step = "timeline";
    });
    return;
  }
  if (action === "pro-live-view-sheet") {
    const modal = state().ui.activeModal;
    const draft = modal?.payload?.draft ? structuredClone(modal.payload.draft) : null;
    if (!draft) return;
    stopProLiveTimer();
    openModal("live-recipe-sheet", { draft });
    queueIdleWork();
    return;
  }
  if (action === "reopen-live-editor") {
    const modal = state().ui.activeModal;
    const draft = modal?.payload?.draft ? structuredClone(modal.payload.draft) : null;
    if (!draft) return;
    draft.step = "timeline";
    openModal("professional-editor", { recipeId: draft.recipeId, draft });
    return;
  }
  if (action === "save-live-recipe-library") {
    const input = app.querySelector('[data-input="library-recipe-name"]');
    await saveLiveDraftToLibrary(input?.value || "");
    return;
  }
  if (action === "share-recipe-image") {
    showToast("Image sharing will be enabled after the photo/share provider is connected.", "info");
    return;
  }
  if (action === "pro-live-prev" || action === "pro-live-next") {
    updateProDraft((draft) => {
      const live = getProLive(draft);
      const delta = action === "pro-live-prev" ? -15 : 15;
      live.elapsed = Math.max(0, Math.min(draft.minutes.length * 60, Number(live.elapsed || 0) + delta));
    });
    return;
  }
  if (action === "pro-save-final" || action === "pro-save-and-run") {
    const savedRecipe = saveProfessionalRecipe();
    if (!savedRecipe) return;
    if (action === "pro-save-and-run") {
      const slotSelect = app.querySelector('[data-input="pro-run-slot"]');
      const slot = Number(slotSelect?.value || 0);
      if (!slot) {
        showToast("Saved final recipe. Choose a connected device before running.", "warning");
        return;
      }
      closeModal();
      await runDeviceRecipe(slot, savedRecipe.id);
      return;
    }
    closeModal();
    showToast(`Saved final recipe ${savedRecipe.displayName}`, "success");
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
    const slot = Number(button.dataset.slot);
    try {
      const names = await refreshDeviceRecipeInventory(slot, {
        force: true,
        timeoutMs: 4500
      });
      showToast(
        names.length > 0
          ? `Found ${names.length} recipe${names.length === 1 ? "" : "s"} on Device ${slot}`
          : `Device ${slot} did not return recipe names over BLE`,
        names.length > 0 ? "success" : "warning"
      );
      openModal("device-recipes", { slot, query: "", filter: "all", selectedNames: [] });
    } catch (error) {
      showToast(error.message || "Unable to read device recipes.", "error");
    }
    return;
  }
  if (action === "manual-run-selected-recipe") {
    const snapshot = state();
    const slot = Number(button.dataset.slot || snapshot.ui.manualMode?.slot || 0);
    const recipeId = String(snapshot.ui.manualMode?.recipeId || "");
    const device = snapshot.devices.find((item) => item.slot === slot) || null;
    const recipe = recipeId ? findRecipeById(snapshot, recipeId) : null;
    if (!recipe) {
      showToast("Select a recipe in Manual Mode first", "warning");
      return;
    }
    if (!device) {
      showToast("Select a device in Manual Mode first", "warning");
      return;
    }
    if (device.connection !== "connected") {
      showToast(`Device ${device.slot} is offline. Connect it before running or queuing a manual recipe.`, "warning");
      return;
    }
    if (!isRecipeAllowedOnDevice(snapshot, device, recipe.id)) {
      showToast(`${recipe.displayName} is not enabled on Device ${device.slot}`, "warning");
      return;
    }
    const runState = getManualDeviceRunState(snapshot, device);
    const result = await runDeviceRecipe(device.slot, recipe.id);
    if (result === "started" && runState.canRunNow) {
      showToast(`${recipe.displayName} starting on Device ${device.slot}`, "success");
    }
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
  if (action === "manual-pick-recipe") {
    const recipeId = String(button.dataset.recipeId || "");
    const slot = Number(button.dataset.slot || state().ui.manualMode?.slot || 1);
    mutate((draft) => {
      draft.ui.manualMode.slot = slot;
      draft.ui.manualMode.recipeId = recipeId;
    });
    return;
  }
  if (action === "manual-induction-time") {
    await adjustManualInductionTime(Number(button.dataset.slot), Number(button.dataset.delta || 0)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-induction-pause-toggle") {
    await pauseResumeManualInduction(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
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
  if (action === "manual-magnetron-power") {
    await adjustManualMagnetronPower(Number(button.dataset.slot), Number(button.dataset.delta || 0)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-magnetron-time") {
    await adjustManualMagnetronTime(Number(button.dataset.slot), Number(button.dataset.delta || 0)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-magnetron-pause-toggle") {
    await pauseResumeManualMagnetron(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
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
    await startManualPump(Number(button.dataset.slot), MANUAL_SPRINKLE_UNITS).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-pump-stop") {
    await stopManualPump(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-spray-start") {
    const input = app.querySelector('[data-input="manual-spray-count"]');
    const count = Math.max(1, Math.trunc(Number(input?.value || state().ui.manualMode?.sprayCount) || 1));
    const ml = count * 10;
    mutate((draft) => {
      draft.ui.manualMode.sprayCount = count;
    });
    await startManualPurge(Number(button.dataset.slot), ml).catch((error) => showToast(error.message, "error"));
    return;
  }
  if (action === "manual-spray-stop") {
    await stopManualPurge(Number(button.dataset.slot)).catch((error) => showToast(error.message, "error"));
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
  if (action === "demo-auth-bypass") {
    mutate((draft) => {
      draft.ui.demoAuthBypass = true;
      const admin = draft.users.find((user) => user.role === "main_admin") || draft.users[0];
      if (admin) draft.currentUserId = admin.id;
    });
    showToast("Demo mode enabled as Main Admin", "success");
    return;
  }
  if (action === "cloud-refresh-status") {
    await refreshCloudRuntime();
    await syncCloudSessionToLocalUser();
    showToast("Cloud status refreshed", "success");
    return;
  }
  if (action === "cloud-signout") {
    try {
      await authService.signOut();
      await refreshCloudRuntime();
      mutate((draft) => {
        draft.ui.demoAuthBypass = false;
      });
      setCloudRuntime({
        lastSummary: "Signed out from cloud.",
        lastError: ""
      });
      showToast("Signed out from cloud", "success");
    } catch (error) {
      showToast(userFacingCloudError(error, "Cloud sign-out is unavailable right now."), "error");
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
      const message = userFacingCloudError(error, "Cloud sync failed. Please try again later.");
      setCloudRuntime({
        loading: false,
        lastError: message
      });
      showToast(message, "error");
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
      const message = userFacingCloudError(error, "Cloud restore failed. Please try again later.");
      setCloudRuntime({
        loading: false,
        lastError: message
      });
      showToast(message, "error");
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
      device.macAddress = "";
      device.hardwareVersion = "";
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
        const user = draft.users.find((item) => item.id === input.value);
        const canUseGlobal = user?.canAddRecipes || user?.role === "main_admin" || user?.role === "admin";
        if (!canUseGlobal && draft.ui.activeTab === "global") {
          draft.ui.activeTab = "recipes";
        }
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

  if (input.dataset.input === "live-dish-photo" && input.files?.[0]) {
    const file = input.files[0];
    const reader = new FileReader();
    reader.onload = () => {
      mutate((draftState) => {
        if (draftState.ui.activeModal?.type !== "live-recipe-sheet") return draftState;
        draftState.ui.activeModal.payload.draft.dishPhotoDataUrl = String(reader.result || "");
      });
      showToast("Dish photo attached to recipe sheet", "success");
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

  if (input.dataset.input === "assign-recipe-search") {
    const slot = Number(input.dataset.slot);
    mutate((draft) => {
      if (draft.ui.activeModal?.type !== "assign-recipe" || Number(draft.ui.activeModal.payload?.slot) !== slot) return draft;
      draft.ui.activeModal.payload.query = input.value;
    });
    return;
  }

  if (input.dataset.input === "device-recipes-search") {
    const slot = Number(input.dataset.slot);
    mutate((draft) => {
      if (draft.ui.activeModal?.type !== "device-recipes" || Number(draft.ui.activeModal.payload?.slot) !== slot) return draft;
      draft.ui.activeModal.payload.query = input.value;
    });
    return;
  }

  if (input.dataset.input === "manual-spray-count") {
    mutate((draft) => {
      draft.ui.manualMode.sprayCount = Math.max(1, Math.trunc(Number(input.value) || 1));
    });
    return;
  }

  if (input.dataset.input === "manual-recipe-id") {
    mutate((draft) => {
      draft.ui.manualMode.recipeId = input.value;
    });
    return;
  }

  if (input.dataset.input === "global-recipe-search") {
    mutate((draft) => {
      draft.ui.globalRecipeSearch = input.value;
    });
    return;
  }

  if (input.dataset.input === "recipe-scale-quantity") {
    await saveScaledRecipe(input.dataset.recipeId, Number(input.value), input.dataset.intensity || "medium");
    return;
  }

  if (input.dataset.input === "recipe-scale-intensity") {
    await saveScaledRecipe(input.dataset.recipeId, Number(input.dataset.targetQuantity), input.value || "medium");
    return;
  }

  if (input.dataset.input?.startsWith("pro-") && input.dataset.input !== "pro-run-slot") {
    const key = input.dataset.input;
    updateProDraft((draft) => {
      const minute = getSelectedProMinute(draft);
      const block = getSelectedProBlock(draft);
      if (key === "pro-display-name") draft.displayName = input.value;
      if (key === "pro-firmware-name") draft.firmwareName = input.value;
      if (key === "pro-aliases") draft.aliases = input.value;
      if (key === "pro-quantity") draft.quantity = Math.max(1, Number(input.value) || 1);
      if (key === "pro-minute-title" && minute) minute.title = input.value;
      if (key === "pro-minute-weight" && minute) minute.weight = input.value;
      if (key === "pro-lid-open" && minute) {
        minute.lidOpen = input.checked;
        minute.lidOpenDuration = input.checked ? 60 : 0;
      }
      if (key === "pro-induction-power" && block) {
        block.inductionPower = normalizePowerStep(input.value);
      }
      if (key === "pro-microwave-power" && block) {
        const power = normalizeMicrowavePower(input.value);
        block.microwaveActive = power > 0;
        block.microwavePower = 800;
      }
      if (key === "pro-stirrer-speed" && block) {
        block.stirrerActive = input.value !== "off";
        block.stirrerSpeed = input.value === "off" ? "medium" : input.value;
        block.stirrerMode = input.value === "off" ? "off" : "continuous";
      }
      if (key === "pro-water-block" && minute) {
        minute.waterBlocks[Number(draft.selectedBlock) || 0] = Math.max(0, Number(input.value) || 0);
      }
      if (key === "pro-ingredient-name") {
        const ingredient = draft.ingredients[Number(input.dataset.index)];
        if (ingredient) ingredient.name = input.value;
      }
      if (key === "pro-ingredient-quantity") {
        const ingredient = draft.ingredients[Number(input.dataset.index)];
        if (ingredient) ingredient.quantity = Math.max(0, Number(input.value) || 0);
      }
      if (key === "pro-ingredient-unit") {
        const ingredient = draft.ingredients[Number(input.dataset.index)];
        if (ingredient) ingredient.unit = input.value || "g";
      }
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

function bindStore(initialScrollState = null) {
  store.subscribe((snapshot) => {
    render();
    scheduleSaveUiSessionState(snapshot);
  });
  render();
  restoreScrollState(initialScrollState || takeSavedScrollState());
}

function bindApkRailGestures() {
  if (!IS_APK_MODE) return;
  const swipe = {
    active: false,
    startX: 0,
    startY: 0,
    startIndex: 0
  };
  const isInteractiveTarget = (target) =>
    Boolean(target?.closest?.("button,a,input,select,textarea,label,.modal-card,.pro-editor-modal,.pro-live-modal"));
  const beginSwipe = (target, x, y) => {
    const rail = target?.closest?.(".apk-rail");
    if (!rail || isInteractiveTarget(target)) {
      swipe.active = false;
      return;
    }
    const { frames } = getApkRailFrames();
    swipe.active = true;
    swipe.startX = x;
    swipe.startY = y;
    swipe.startIndex = getCurrentApkRailIndex(rail, frames);
  };
  const finishSwipe = (x, y, event) => {
    if (!swipe.active) return;
    swipe.active = false;
    const deltaX = x - swipe.startX;
    const deltaY = y - swipe.startY;
    if (Math.abs(deltaX) < 52 || Math.abs(deltaX) < Math.abs(deltaY) * 1.25) return;
    event.preventDefault();
    const { frames } = getApkRailFrames();
    const targetIndex = Math.max(0, Math.min(frames.length - 1, swipe.startIndex + (deltaX < 0 ? 1 : -1)));
    lastApkScreenIndex = targetIndex;
    mutate((draft) => {
      draft.ui.apkScreenIndex = targetIndex;
    });
    window.requestAnimationFrame(() => scrollApkRailToIndex(targetIndex));
  };

  app.addEventListener(
    "touchstart",
    (event) => {
      if (event.touches.length !== 1) {
        swipe.active = false;
        return;
      }
      beginSwipe(event.target, event.touches[0].clientX, event.touches[0].clientY);
    },
    { passive: true }
  );

  app.addEventListener(
    "touchend",
    (event) => {
      if (!event.changedTouches.length) return;
      finishSwipe(event.changedTouches[0].clientX, event.changedTouches[0].clientY, event);
    },
    { passive: false }
  );

  app.addEventListener(
    "pointerdown",
    (event) => {
      if (!event.isPrimary) return;
      beginSwipe(event.target, event.clientX, event.clientY);
    },
    { passive: true }
  );

  app.addEventListener(
    "pointerup",
    (event) => {
      if (!event.isPrimary) return;
      finishSwipe(event.clientX, event.clientY, event);
    },
    { passive: false }
  );

  app.addEventListener(
    "pointercancel",
    () => {
      swipe.active = false;
    },
    { passive: true }
  );

  app.addEventListener(
    "scroll",
    (event) => {
      if (!event.target.classList?.contains("apk-rail")) return;
      const { rail, frames } = getApkRailFrames();
      const activeIndex = getCurrentApkRailIndex(rail, frames);
      setApkScreenSwitcherActive(activeIndex);
      if (Number(state().ui.apkScreenIndex || 0) !== activeIndex) {
        mutate((draft) => {
          draft.ui.apkScreenIndex = activeIndex;
        });
      }
    },
    true
  );
}

window.addEventListener("message", (event) => {
  if (event.origin !== window.location.origin) return;
  if (event.data?.type !== "on2cook-pro-studio-route") return;
  updateProStudioShellOrientation(event.data.orientation, event.data.path || event.data.hash || "");
});

async function init() {
  seedRecipes = await loadSeedRecipeCatalog();
  globalRecipeCatalog = await loadGlobalRecipeCatalog();
  await loadFirmwareManifest();
  const savedUiSession = takeSavedUiSessionState();
  const initialState = applySavedUiSessionState(loadState(seedRecipes), savedUiSession);
  store = createStore(initialState);
  mutate((draft) => {
    clearStartupRecipeUploadState(draft);
    syncSelectedRecipesToAllDevices(draft);
  });
  bindStore(savedUiSession?.scroll || takeSavedScrollState());
  handleTransportEvents();
  ensureStatusPolling();
  ensureIncomingOrderFeed();
  ensureKotBridgePolling();
  await registerServiceWorker();
  await refreshCloudRuntime();
  await syncCloudSessionToLocalUser();
  app.addEventListener("click", handleClick);
  app.addEventListener("submit", handleSubmit);
  app.addEventListener("change", handleChange);
  app.addEventListener("scroll", () => scheduleSaveUiSessionState(null, 250), true);
  window.addEventListener("scroll", () => scheduleSaveUiSessionState(null, 250), { passive: true });
  window.addEventListener("beforeunload", () => saveUiSessionState());
  window.addEventListener("pagehide", () => {
    saveUiSessionState();
    ble.disconnectAllLocal?.();
  });
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") saveUiSessionState();
  });
  bindApkRailGestures();
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

