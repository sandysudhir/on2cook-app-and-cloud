const STORAGE_KEY = "on2cook-cloud-state-v5";
const STATE_VERSION = 5;
const MAX_PERSISTED_DEVICE_ACTIVITY = 30;
const MAX_PERSISTED_PREVIOUS_ORDERS = 25;

function uid(prefix) {
  return `${prefix}_${crypto.randomUUID()}`;
}

function isoNow() {
  return new Date().toISOString();
}

function cloneJsonSafe(value) {
  return value === undefined ? undefined : JSON.parse(JSON.stringify(value));
}

function sanitizeFirmwareName(value) {
  return String(value || "")
    .replace(/[^A-Za-z0-9 ()_-]/g, "")
    .trim()
    .slice(0, 30) || "APP_RECIPE";
}

function toRecipeRecord(seedItem, index) {
  const recipe = structuredClone(seedItem.recipe || {});
  const seededName = seedItem.recipeName || seedItem.id;
  const name = Array.isArray(recipe.name) ? recipe.name[0] : recipe.name;
  const displayName = String(seededName || name || `Recipe ${index + 1}`).trim();
  const firmwareName = sanitizeFirmwareName(displayName);
  if (!Array.isArray(recipe.name) || recipe.name.length === 0) {
    recipe.name = [firmwareName];
  } else {
    recipe.name[0] = firmwareName;
  }
  return {
    id: uid("recipe"),
    type: "base",
    baseRecipeId: null,
    source: "seed",
    zipName: seedItem.zipName || "",
    zipUrl: seedItem.zipUrl || "",
    recipeTextEntryName: seedItem.recipeTextEntryName || "",
    rawRecipeText: seedItem.recipeText || "",
    displayName,
    firmwareName,
    aliases: Array.from(
      new Set(
        [displayName, seedItem.recipeName, seedItem.id, name]
          .map((value) => String(value || "").trim())
          .filter(Boolean)
      )
    ),
    category: recipe.category || "Orders",
    imageDataUrl: seedItem.imageDataUrl || "",
    recipeEntries: Array.isArray(seedItem.entries) ? structuredClone(seedItem.entries) : [],
    recipeJson: recipe,
    selected: true,
    createdAt: isoNow(),
    updatedAt: isoNow()
  };
}

function compactRecipeForStorage(recipe, aggressive = false) {
  const base = {
    id: recipe.id,
    type: recipe.type,
    baseRecipeId: recipe.baseRecipeId,
    source: recipe.source,
    cloudRecordId: recipe.cloudRecordId || "",
    cloudUserId: recipe.cloudUserId || "",
    recipeSignature: recipe.recipeSignature || "",
    zipName: recipe.zipName || "",
    zipUrl: recipe.zipUrl || "",
    recipeTextEntryName: recipe.recipeTextEntryName || "",
    displayName: recipe.displayName,
    firmwareName: recipe.firmwareName,
    aliases: Array.isArray(recipe.aliases) ? [...recipe.aliases] : [],
    category: recipe.category || "",
    selected: recipe.selected !== false,
    createdAt: recipe.createdAt || "",
    updatedAt: recipe.updatedAt || ""
  };

  if (recipe.source === "seed") {
    return base;
  }

  return {
    ...base,
    rawRecipeText: recipe.rawRecipeText || "",
    imageDataUrl: aggressive ? "" : recipe.imageDataUrl || "",
    recipeJson: cloneJsonSafe(recipe.recipeJson || {}),
    recipeEntries: aggressive ? [] : Array.isArray(recipe.recipeEntries) ? cloneJsonSafe(recipe.recipeEntries) : []
  };
}

function compactOrderForStorage(order, aggressive = false) {
  return {
    ...order,
    previewImageDataUrl: aggressive ? "" : order.previewImageDataUrl || "",
    payload: aggressive ? {} : order.payload,
    kot: aggressive ? null : order.kot
  };
}

function compactDeviceForStorage(device, aggressive = false) {
  return {
    ...device,
    serialPhotoDataUrl: aggressive ? "" : device.serialPhotoDataUrl || "",
    activity: Array.isArray(device.activity) ? device.activity.slice(-MAX_PERSISTED_DEVICE_ACTIVITY) : []
  };
}

function serializeStateForStorage(state, aggressive = false) {
  return {
    ...state,
    recipes: Array.isArray(state.recipes) ? state.recipes.map((recipe) => compactRecipeForStorage(recipe, aggressive)) : [],
    devices: Array.isArray(state.devices) ? state.devices.map((device) => compactDeviceForStorage(device, aggressive)) : [],
    orders: {
      current: Array.isArray(state.orders?.current)
        ? state.orders.current.map((order) => compactOrderForStorage(order, true))
        : [],
      incoming: Array.isArray(state.orders?.incoming)
        ? state.orders.incoming.map((order) => compactOrderForStorage(order, true))
        : [],
      previous: Array.isArray(state.orders?.previous)
        ? state.orders.previous.slice(-MAX_PERSISTED_PREVIOUS_ORDERS).map((order) => compactOrderForStorage(order, aggressive))
        : []
    }
  };
}

function serializeStateForRecovery(state) {
  const keptRecipes = Array.isArray(state.recipes)
    ? state.recipes
        .filter((recipe) => recipe.selected || recipe.type === "final" || recipe.source === "seed")
        .slice(0, 40)
        .map((recipe) => compactRecipeForStorage(recipe, true))
    : [];
  return {
    version: STATE_VERSION,
    exportedAt: state.exportedAt || "",
    facilities: Array.isArray(state.facilities) ? cloneJsonSafe(state.facilities) : [],
    users: Array.isArray(state.users) ? cloneJsonSafe(state.users) : [],
    currentUserId: state.currentUserId || "",
    currentFacilityId: state.currentFacilityId || "",
    settings: cloneJsonSafe(state.settings || {}),
    ui: cloneJsonSafe(state.ui || {}),
    recipes: keptRecipes,
    orders: {
      current: Array.isArray(state.orders?.current) ? state.orders.current.slice(0, 10).map((order) => compactOrderForStorage(order, true)) : [],
      incoming: Array.isArray(state.orders?.incoming) ? state.orders.incoming.slice(0, 10).map((order) => compactOrderForStorage(order, true)) : [],
      previous: Array.isArray(state.orders?.previous) ? state.orders.previous.slice(0, 10).map((order) => compactOrderForStorage(order, true)) : []
    },
    devices: Array.isArray(state.devices) ? state.devices.map((device) => compactDeviceForStorage(device, true)) : []
  };
}

function writeStateToStorage(state) {
  const compact = serializeStateForStorage(state, false);
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(compact));
    return;
  } catch (error) {
    console.warn("State exceeded localStorage quota. Retrying with a tighter snapshot.", error);
  }

  const aggressive = serializeStateForStorage(state, true);
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(aggressive));
    return;
  } catch (error) {
    console.error("Unable to persist compact state within localStorage quota.", error);
  }

  const recovery = serializeStateForRecovery(state);
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(recovery));
    return;
  } catch (error) {
    console.error("Unable to persist recovery state within localStorage quota.", error);
  }

  try {
    localStorage.removeItem(STORAGE_KEY);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(recovery));
  } catch (error) {
    console.error("Unable to recover localStorage persistence.", error);
  }
}

const CUSTOMER_PROFILES = [
  { name: "Test", address: "Test", phone: "7760978358", gstin: "1234567890" },
  { name: "Rajesh", address: "HSR Layout", phone: "9820012345", gstin: "29ABCDE1234F1Z9" },
  { name: "Priya", address: "Koramangala 5th Block", phone: "9876543210", gstin: "29AAACB2894G1ZA" },
  { name: "Amit", address: "Indiranagar", phone: "9811122233", gstin: "29AAECM4421K1ZQ" },
  { name: "Sneha", address: "Whitefield", phone: "9845033344", gstin: "29AAICR9001D1ZH" },
  { name: "Vikram", address: "Jayanagar", phone: "9900088899", gstin: "29AACCV1023A1ZX" }
];

const CHANNEL_PROFILES = [
  {
    orderType: "Delivery",
    paymentType: "Other",
    customPaymentType: "Zomato Pay",
    orderFrom: "POS",
    subOrderType: "Delivery",
    packagingCharge: 0.95,
    deliveryCharge: 2,
    serviceRate: 0.01
  },
  {
    orderType: "Takeaway",
    paymentType: "UPI",
    customPaymentType: "PhonePe",
    orderFrom: "POS",
    subOrderType: "Takeaway",
    packagingCharge: 1.4,
    deliveryCharge: 0,
    serviceRate: 0.005
  },
  {
    orderType: "Dine In",
    paymentType: "Cash",
    customPaymentType: "Cash",
    orderFrom: "Manual",
    subOrderType: "Table Service",
    packagingCharge: 0,
    deliveryCharge: 0,
    serviceRate: 0.012
  }
];

function toMoney(value) {
  return Number((Number(value) || 0).toFixed(2));
}

function formatOrderApiTimestamp(value = isoNow()) {
  const date = new Date(value);
  const pad = (part) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function numericOrderId(orderId, fallback = 80) {
  const parsed = Number.parseInt(String(orderId || "").replace(/[^\d]/g, ""), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function resolveOrderItemQuantity(quantityLabel) {
  const label = String(quantityLabel || "").trim();
  const match = label.match(/(\d+(?:\.\d+)?)/);
  const count = match ? Math.max(1, Math.round(Number(match[1]))) : 1;
  return /(bowl|tray|plate|piece|pcs|portion|serving)/i.test(label) ? count : 1;
}

function deriveRecipeUnitPrice(recipe, index = 0) {
  const ingredientCount = Array.isArray(recipe?.recipeJson?.Ingredients) ? recipe.recipeJson.Ingredients.length : 4;
  const instructionCount = Array.isArray(recipe?.recipeJson?.Instruction) ? recipe.recipeJson.Instruction.length : 4;
  return toMoney(165 + ingredientCount * 7 + instructionCount * 11 + index * 5);
}

function buildItemCode(recipeName, index = 0) {
  const compact = String(recipeName || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  return `${compact.slice(0, 2).padEnd(2, "X")}${String(30 + index).padStart(2, "0")}`.slice(0, 4);
}

function matchingRecipeForOrder(order, recipes) {
  const keys = [
    order.activeRecipeId,
    order.currentRunRecipeName,
    order.recipeLookup,
    order.itemName
  ]
    .filter(Boolean)
    .map((value) => String(value).trim().toLowerCase());
  if (order.activeRecipeId) {
    const byId = recipes.find((recipe) => recipe.id === order.activeRecipeId);
    if (byId) return byId;
  }
  return (
    recipes.find((recipe) =>
      keys.some(
        (key) =>
          recipe.displayName.toLowerCase() === key ||
          recipe.firmwareName.toLowerCase() === key ||
          recipe.aliases.some((alias) => alias.toLowerCase() === key)
      )
    ) || null
  );
}

export function decorateOrderRecord(order, recipe = null, orderIndex = 0) {
  const enrichedRecipe = recipe || null;
  if (order?.kot?.properties?.Order && Array.isArray(order.kot.properties.OrderItem)) {
    return {
      ...order,
      previewImageDataUrl: order.previewImageDataUrl || enrichedRecipe?.imageDataUrl || "",
      customerName: order.customerName || order.kot.properties.Customer?.name || "Walk-in",
      itemCount: order.itemCount || order.kot.properties.OrderItem.length || 1,
      totalAmount: toMoney(order.totalAmount ?? order.kot.properties.Order?.total ?? 0)
    };
  }

  const customerProfile = CUSTOMER_PROFILES[order.customerProfileIndex ?? orderIndex % CUSTOMER_PROFILES.length] || CUSTOMER_PROFILES[0];
  const channelProfile = CHANNEL_PROFILES[order.channelProfileIndex ?? orderIndex % CHANNEL_PROFILES.length] || CHANNEL_PROFILES[0];
  const customer = {
    name: order.customerName || customerProfile.name,
    address: order.customerAddress || customerProfile.address,
    phone: order.customerPhone || customerProfile.phone,
    gstin: order.customerGstin || customerProfile.gstin
  };
  const itemQuantity = resolveOrderItemQuantity(order.quantity);
  const unitPrice = toMoney(order.unitPrice ?? deriveRecipeUnitPrice(enrichedRecipe, orderIndex));
  const coreTotal = toMoney(order.coreTotal ?? unitPrice * itemQuantity);
  const discountRate = Number(order.discountRate ?? [15, 10, 8, 12, 5, 0][orderIndex % 6]);
  const discountTotal = toMoney(order.discountTotal ?? coreTotal * (discountRate / 100));
  const taxable = toMoney(coreTotal - discountTotal);
  const cgst = toMoney(taxable * 0.025);
  const sgst = toMoney(taxable * 0.025);
  const packagingCharge = toMoney(order.packagingCharge ?? channelProfile.packagingCharge);
  const deliveryCharge = toMoney(order.deliveryCharge ?? channelProfile.deliveryCharge);
  const serviceCharge = toMoney(order.serviceCharge ?? taxable * (order.serviceRate ?? channelProfile.serviceRate));
  const total = toMoney(taxable + cgst + sgst + packagingCharge + deliveryCharge + serviceCharge);
  const numericId = numericOrderId(order.orderId, 80 + orderIndex);
  const itemName = enrichedRecipe?.displayName || order.itemName || "Recipe";
  const itemCode = buildItemCode(itemName, orderIndex);
  const payload = {
    token: "",
    properties: {
      Restaurant: {
        res_name: "Android Live",
        address: "Mexicans",
        contact_information: "7060532398",
        restID: "3n9tgu1d"
      },
      Customer: customer,
      Order: {
        orderID: numericId,
        customer_invoice_id: String(numericId),
        delivery_charges: deliveryCharge,
        order_type: order.orderType || channelProfile.orderType,
        payment_type: order.paymentType || channelProfile.paymentType,
        table_no: order.tableNo || "",
        no_of_persons: Number(order.noOfPersons || 0),
        discount_total: discountTotal,
        tax_total: toMoney(cgst + sgst),
        round_off: "0.00",
        core_total: coreTotal,
        total,
        created_on: order.createdOn || formatOrderApiTimestamp(order.createdAt || isoNow()),
        order_from: order.orderFrom || order.source || channelProfile.orderFrom,
        order_from_id: "",
        sub_order_type: order.subOrderType || channelProfile.subOrderType,
        packaging_charge: packagingCharge,
        status: order.kotOrderStatus || "Success",
        token_no: order.tokenNo || "",
        custom_payment_type: order.customPaymentType || channelProfile.customPaymentType,
        comment: order.specialInstructions || "",
        service_charge: serviceCharge,
        biller: order.biller || "biller (biller)",
        assignee: order.assignee || ""
      },
      Tax: [
        { title: "CGST", type: "P", rate: 2.5, amount: cgst },
        { title: "SGST", type: "P", rate: 2.5, amount: sgst }
      ],
      Discount: discountTotal > 0 ? [{ title: "Customer Discount", type: "P", rate: discountRate, amount: discountTotal }] : [],
      OrderItem: [
        {
          name: /(g|kg|ml|l)\b/i.test(String(order.quantity || "")) ? `${itemName} (${order.quantity})` : itemName,
          itemid: 13470000 + numericId,
          itemcode: itemCode,
          vendoritemcode: "",
          specialnotes: order.specialInstructions || "",
          price: unitPrice,
          quantity: itemQuantity,
          total: coreTotal,
          addon: [],
          category_name: enrichedRecipe?.category || "Orders",
          sap_code: String(2100 + (numericId % 50)),
          discount: discountTotal,
          tax: toMoney(cgst + sgst)
        }
      ]
    },
    event: "orderdetails"
  };

  return {
    ...order,
    customerName: customer.name,
    itemCount: payload.properties.OrderItem.length,
    totalAmount: total,
    previewImageDataUrl: order.previewImageDataUrl || enrichedRecipe?.imageDataUrl || "",
    kot: payload
  };
}

const ORDER_PALETTE = ["#f47b20", "#2d6cdf", "#3f7d58", "#d95f43", "#7a5af8", "#9b6d33"];
const SEED_ORDER_PRESETS = [
  { orderId: "#84", quantity: "800 g", source: "POS", specialInstructions: "Less oil", channelProfileIndex: 0, customerProfileIndex: 0 },
  { orderId: "#83", quantity: "600 g", source: "POS", specialInstructions: "Ready for dispatch", channelProfileIndex: 1, customerProfileIndex: 1 },
  { orderId: "#82", quantity: "1 tray", source: "Manual", specialInstructions: "No garlic", channelProfileIndex: 2, customerProfileIndex: 2 },
  { orderId: "#81", quantity: "900 g", source: "POS", specialInstructions: "Priority pickup", channelProfileIndex: 0, customerProfileIndex: 3 },
  { orderId: "#80", quantity: "450 g", source: "POS", specialInstructions: "Family combo side", channelProfileIndex: 1, customerProfileIndex: 4 },
  { orderId: "#79", quantity: "2 bowls", source: "Manual", specialInstructions: "Extra seasoning", channelProfileIndex: 2, customerProfileIndex: 5 },
  { orderId: "#78", quantity: "700 g", source: "POS", specialInstructions: "Queue after lunch batch", channelProfileIndex: 0, customerProfileIndex: 0 },
  { orderId: "#77", quantity: "500 g", source: "POS", specialInstructions: "Office lunch order", channelProfileIndex: 1, customerProfileIndex: 1 },
  { orderId: "#76", quantity: "300 g", source: "Manual", specialInstructions: "Operator hold", channelProfileIndex: 2, customerProfileIndex: 2 },
  { orderId: "#75", quantity: "550 g", source: "POS", specialInstructions: "Table 12", channelProfileIndex: 0, customerProfileIndex: 3, tableNo: "12", noOfPersons: 2 }
];

function createSeedOrderRecord(recipe, index, preset) {
  return decorateOrderRecord(
    {
      id: uid("order"),
      orderId: preset?.orderId || `#3${index}`,
      itemName: recipe.displayName,
      recipeLookup: recipe.displayName,
      quantity: preset?.quantity || "500 g",
      source: preset?.source || "POS",
      specialInstructions: preset?.specialInstructions || "",
      accentColor: ORDER_PALETTE[index % ORDER_PALETTE.length],
      createdAt: new Date(Date.now() - index * 420000).toISOString(),
      status: "pending",
      assignedSlot: null,
      assignedMode: "auto",
      activeRecipeId: recipe.id,
      currentRunRecipeName: "",
      currentRunFirmwareName: "",
      targetSlot: null,
      manual: (preset?.source || "POS") === "Manual",
      historyNote: "",
      channelProfileIndex: preset?.channelProfileIndex ?? index % CHANNEL_PROFILES.length,
      customerProfileIndex: preset?.customerProfileIndex ?? index % CUSTOMER_PROFILES.length,
      tableNo: preset?.tableNo || "",
      noOfPersons: preset?.noOfPersons || 0
    },
    recipe,
    index
  );
}

function createSeedOrders(recipes) {
  return recipes.slice(0, 5).map((recipe, index) => createSeedOrderRecord(recipe, index, SEED_ORDER_PRESETS[index]));
}

function createIncomingOrders(recipes) {
  return recipes
    .slice(5, 10)
    .map((recipe, index) => createSeedOrderRecord(recipe, index + 5, SEED_ORDER_PRESETS[index + 5]));
}

function createPreviousOrders() {
  return [];
}

function createDefaultUsers() {
  const facilityId = uid("facility");
  const mainAdminId = uid("user");
  const managerId = uid("user");
  const operatorId = uid("user");
  return {
    facilities: [
      {
        id: facilityId,
        name: "On2Cook Demo Kitchen",
        facilityType: "franchise",
        parentId: null,
        createdAt: isoNow()
      }
    ],
    users: [
      {
        id: mainAdminId,
        facilityId,
        email: "admin@on2cook.local",
        displayName: "Main Admin",
        role: "main_admin",
        managerMode: true
      },
      {
        id: managerId,
        facilityId,
        email: "manager@on2cook.local",
        displayName: "Kitchen Manager",
        role: "kitchen_manager",
        managerMode: true
      },
      {
        id: operatorId,
        facilityId,
        email: "operator@on2cook.local",
        displayName: "Operator",
        role: "operator",
        managerMode: false
      }
    ],
    currentUserId: mainAdminId,
    facilityId
  };
}

function createDeviceSlot(slot, allowedRecipeIds = []) {
  return {
    slot,
    displayName: `On2Cook-0${slot}`,
    browserDeviceId: "",
    bluetoothName: "",
    serialPhotoDataUrl: "",
    enabled: true,
    connection: "disconnected",
    lastUpdatedAt: "",
    lastMessage: "Waiting for connection",
    activity: [],
    completionConfirmationPending: false,
    baselineRecipeSyncPending: false,
    startupGuardUntil: "",
    currentJobId: "",
    queueOrderIds: [],
    historyOrderIds: [],
    activeRun: {
      orderId: "",
      recipeId: "",
      displayName: "",
      firmwareName: "",
      startedAt: "",
      durationSeconds: 0
    },
    lastRun: {
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
    },
    uploadState: {
      inventoryChecking: false,
      active: false,
      totalRecipes: 0,
      currentIndex: 0,
      currentRecipeName: "",
      recipeNames: [],
      completedRecipeNames: [],
      skippedRecipeNames: [],
      summary: ""
    },
    allowedRecipeIds: [...allowedRecipeIds],
    availableRecipeNames: [],
    recipeInventoryUpdatedAt: "",
    syncedRecipeNames: [],
    syncedRecipeSignatures: {},
    telemetry: {
      workStatus: "offline",
      currentRecipe: "",
      firmwareVersion: "",
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
      stirrer: "MED",
      pumpOn: false,
      lastRaw: "",
      paused: false,
      disconnectedAt: ""
    }
  };
}

function createDefaultSettings() {
  return {
    orderScreenEnabled: true,
    pendingAssignmentMode: "manual_review",
    queueMode: "global_auto",
    operatorActsAsManager: false,
    maxDeviceCount: 5,
    logSyncCadence: "nightly",
    emailit: {
      apiKey: "",
      toEmail: "admin@on2cook.local",
      enabled: false
    },
    supabase: {
      enabled: false,
      url: "",
      anonKey: ""
    },
    recipeFinder: {
      baseUrl: "https://on2cook-recipe-finder.vercel.app/",
      lastZipUrl: ""
    }
  };
}

function createUiState() {
  return {
    activeTab: "orders",
    orderMode: "current",
    recipeMode: "selected",
    globalRecipeSearch: "",
    globalRecipePickedIds: [],
    manualMode: {
      slot: 1,
      pumpUnits: 10
    },
    activeModal: null,
    toast: "",
    toastTone: "info"
  };
}

export function createInitialState(seedRecipes) {
  const baseRecipes = seedRecipes.map(toRecipeRecord);
  const defaultAllowedRecipeIds = baseRecipes.map((recipe) => recipe.id);
  const auth = createDefaultUsers();
  return {
    version: STATE_VERSION,
    exportedAt: "",
    facilities: auth.facilities,
    users: auth.users,
    currentUserId: auth.currentUserId,
    currentFacilityId: auth.facilityId,
    settings: createDefaultSettings(),
    ui: createUiState(),
    recipes: baseRecipes,
    orders: {
      current: createSeedOrders(baseRecipes),
      incoming: createIncomingOrders(baseRecipes),
      previous: createPreviousOrders(baseRecipes)
    },
    devices: Array.from({ length: 5 }, (_, index) => createDeviceSlot(index + 1, defaultAllowedRecipeIds))
  };
}

function hydrateDevices(devices, recipes) {
  const defaultAllowedRecipeIds = recipes.filter((recipe) => recipe.selected).map((recipe) => recipe.id);
  return Array.from({ length: 5 }, (_, index) => {
    const existing = devices[index] || {};
    const hydrated = {
      ...createDeviceSlot(index + 1, defaultAllowedRecipeIds),
      ...existing,
      slot: index + 1
    };
    hydrated.activeRun = {
      ...createDeviceSlot(index + 1, defaultAllowedRecipeIds).activeRun,
      ...(existing.activeRun || {})
    };
    hydrated.lastRun = {
      ...createDeviceSlot(index + 1, defaultAllowedRecipeIds).lastRun,
      ...(existing.lastRun || {})
    };
    hydrated.uploadState = {
      ...createDeviceSlot(index + 1, defaultAllowedRecipeIds).uploadState,
      ...(existing.uploadState || {})
    };
    hydrated.telemetry = {
      ...createDeviceSlot(index + 1, defaultAllowedRecipeIds).telemetry,
      ...(existing.telemetry || {})
    };
    if (!Array.isArray(hydrated.allowedRecipeIds) || hydrated.allowedRecipeIds.length === 0) {
      hydrated.allowedRecipeIds = [...defaultAllowedRecipeIds];
    } else {
      hydrated.allowedRecipeIds = Array.from(new Set([...hydrated.allowedRecipeIds, ...defaultAllowedRecipeIds]));
    }
    if (!Array.isArray(hydrated.availableRecipeNames)) {
      hydrated.availableRecipeNames = [];
    }
    if (!Array.isArray(hydrated.syncedRecipeNames)) {
      hydrated.syncedRecipeNames = [];
    }
    if (!hydrated.syncedRecipeSignatures || typeof hydrated.syncedRecipeSignatures !== "object") {
      hydrated.syncedRecipeSignatures = {};
    }
    return hydrated;
  });
}

function hydrateOrders(orders, recipes) {
  const current = Array.isArray(orders?.current) ? orders.current : [];
  const incoming = Array.isArray(orders?.incoming) ? orders.incoming : [];
  const previous = Array.isArray(orders?.previous) ? orders.previous : [];
  return {
    current: current.map((order, index) => decorateOrderRecord(order, matchingRecipeForOrder(order, recipes), index)),
    incoming: incoming.map((order, index) => decorateOrderRecord(order, matchingRecipeForOrder(order, recipes), index + 50)),
    previous: previous.map((order, index) => decorateOrderRecord(order, matchingRecipeForOrder(order, recipes), index + 100))
  };
}

function shouldReseedOrders(orders, recipes = []) {
  const current = Array.isArray(orders?.current) ? orders.current : [];
  const incoming = Array.isArray(orders?.incoming) ? orders.incoming : [];
  if (current.length === 0 && !Array.isArray(orders?.incoming)) return true;
  const hasAssignedOrActive = current.some(
    (order) => order.status !== "pending" || order.assignedSlot || order.targetSlot
  );
  if (!Array.isArray(orders?.incoming) && (current.length > 5 || hasAssignedOrActive)) {
    return true;
  }
  const expectedCurrent = Math.min(5, recipes.length);
  const expectedIncoming = Math.min(5, Math.max(0, recipes.length - 5));
  const catalogMismatch = [...current, ...incoming].some((order) => !matchingRecipeForOrder(order, recipes));
  if (catalogMismatch) {
    return true;
  }
  if (!hasAssignedOrActive && (current.length !== expectedCurrent || incoming.length !== expectedIncoming)) {
    return true;
  }
  const legacyIds = current.filter((order) => /^#20\d$/.test(String(order.orderId || "").trim())).length;
  const missingPayload = current.every((order) => !order?.kot?.properties?.Order);
  return legacyIds >= 3 && missingPayload;
}

function mergeSeedRecipes(existingState, seedRecipes) {
  const normalizedSeeds = seedRecipes.map(toRecipeRecord);
  const existingSeedRecipes = existingState.recipes.filter((recipe) => recipe.source === "seed");
  const customRecipes = existingState.recipes.filter((recipe) => recipe.source !== "seed");
  const mergedSeeds = normalizedSeeds.map((seedRecipe, index) => {
    const seedKey = String(seedRecipe.zipName || seedRecipe.displayName).trim().toLowerCase();
    const existing =
      existingSeedRecipes.find((recipe) => String(recipe.zipName || recipe.displayName).trim().toLowerCase() === seedKey) ||
      existingSeedRecipes[index] ||
      null;
    if (!existing) return seedRecipe;
    return {
      ...existing,
      zipName: seedRecipe.zipName,
      zipUrl: seedRecipe.zipUrl || "",
      recipeTextEntryName: seedRecipe.recipeTextEntryName || "",
      rawRecipeText: seedRecipe.rawRecipeText || "",
      displayName: seedRecipe.displayName,
      firmwareName: seedRecipe.firmwareName,
      aliases: Array.from(new Set([...(existing.aliases || []), ...seedRecipe.aliases])),
      category: seedRecipe.category,
      imageDataUrl: seedRecipe.imageDataUrl,
      recipeEntries: Array.isArray(seedRecipe.recipeEntries) ? structuredClone(seedRecipe.recipeEntries) : [],
      recipeJson: seedRecipe.recipeJson,
      selected: existing.selected !== false,
      updatedAt: isoNow()
    };
  });
  existingState.recipes = [...customRecipes, ...mergedSeeds];
  return existingState;
}

function normalizeRuntimeState(existingState) {
  if (!existingState?.orders || !Array.isArray(existingState.orders.current) || !Array.isArray(existingState.devices)) {
    return existingState;
  }

  existingState.devices.forEach((device) => {
    device.connection = "disconnected";
    device.completionConfirmationPending = false;
    device.baselineRecipeSyncPending = false;
    device.currentJobId = "";
    device.queueOrderIds = [];
    device.activeRun = {
      orderId: "",
      recipeId: "",
      displayName: "",
      firmwareName: "",
      startedAt: "",
      durationSeconds: 0
    };
    device.uploadState = {
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
    device.telemetry = {
      ...device.telemetry,
      workStatus: "offline",
      currentRecipe: "",
      remainingSeconds: 0,
      magTime: 0,
      indTime: 0,
      stepNo: 0,
      mode: "",
      status: "",
      ingredientsIndex: 0,
      paused: false
    };
  });

  existingState.orders.current = existingState.orders.current.map((order) => {
    if (!["queued", "starting", "cooking", "awaiting_confirmation"].includes(order.status) && !order.assignedSlot && !order.targetSlot) {
      return order;
    }
    return {
      ...order,
      status: "pending",
      assignedSlot: null,
      assignedMode: "auto",
      currentRunRecipeName: "",
      currentRunFirmwareName: "",
      targetSlot: null
    };
  });

  return existingState;
}

export function loadState(seedRecipes) {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    const initial = createInitialState(seedRecipes);
    writeStateToStorage(initial);
    return initial;
  }
  try {
    const parsed = JSON.parse(raw);
    if (parsed.version !== STATE_VERSION) {
      const initial = createInitialState(seedRecipes);
      writeStateToStorage(initial);
      return initial;
    }
    const merged = mergeSeedRecipes(parsed, seedRecipes);
    merged.ui = {
      ...createUiState(),
      ...(merged.ui || {}),
      manualMode: {
        ...createUiState().manualMode,
        ...(merged.ui?.manualMode || {})
      }
    };
    merged.settings = {
      ...createDefaultSettings(),
      ...(merged.settings || {}),
      emailit: {
        ...createDefaultSettings().emailit,
        ...(merged.settings?.emailit || {})
      },
      supabase: {
        ...createDefaultSettings().supabase,
        ...(merged.settings?.supabase || {})
      },
      recipeFinder: {
        ...createDefaultSettings().recipeFinder,
        ...(merged.settings?.recipeFinder || {})
      }
    };
    merged.orders = shouldReseedOrders(merged.orders, merged.recipes)
      ? {
          current: createSeedOrders(merged.recipes),
          incoming: createIncomingOrders(merged.recipes),
          previous: createPreviousOrders(merged.recipes)
        }
      : hydrateOrders(merged.orders || {}, merged.recipes);
    merged.devices = hydrateDevices(merged.devices, merged.recipes);
    normalizeRuntimeState(merged);
    writeStateToStorage(merged);
    return merged;
  } catch (error) {
    console.error("Unable to parse saved state. Resetting.", error);
    const initial = createInitialState(seedRecipes);
    writeStateToStorage(initial);
    return initial;
  }
}

export function createStore(initialState) {
  let state = structuredClone(initialState);
  const listeners = new Set();

  function emit() {
    writeStateToStorage(state);
    listeners.forEach((listener) => listener(state));
  }

  return {
    getState() {
      return state;
    },
    setState(updater) {
      const nextState = typeof updater === "function" ? updater(structuredClone(state)) : updater;
      state = nextState;
      emit();
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    reset(seedRecipes) {
      state = createInitialState(seedRecipes);
      emit();
    }
  };
}

export function getCurrentUser(state) {
  return state.users.find((user) => user.id === state.currentUserId) || state.users[0];
}

export function currentPermissions(state) {
  const user = getCurrentUser(state);
  const managerLike = user.role === "main_admin" || user.role === "admin" || user.role === "kitchen_manager";
  const operatorManager = user.role === "operator" && (user.managerMode || state.settings.operatorActsAsManager);
  return {
    user,
    canManageUsers: user.role === "main_admin" || user.role === "admin",
    canAssignQueues: managerLike || operatorManager,
    canRunRecipes: true,
    canCreateBaseRecipes: user.role === "main_admin" || user.role === "admin",
    canCreateFinalRecipes: managerLike || operatorManager,
    canEditDevicePermissions: managerLike || operatorManager,
    canAbortOrRestart: true
  };
}

export function findRecipeById(state, recipeId) {
  return state.recipes.find((recipe) => recipe.id === recipeId) || null;
}

export function findEffectiveRecipeForOrder(state, recipeLookup) {
  const key = String(recipeLookup || "").trim().toLowerCase();
  const finalRecipe = state.recipes.find(
    (recipe) => recipe.type === "final" && recipe.selected && recipe.aliases.some((alias) => alias.toLowerCase() === key)
  );
  if (finalRecipe) return finalRecipe;
  return (
    state.recipes.find(
      (recipe) =>
        recipe.selected &&
        (recipe.displayName.toLowerCase() === key ||
          recipe.firmwareName.toLowerCase() === key ||
          recipe.aliases.some((alias) => alias.toLowerCase() === key))
    ) || null
  );
}

export function cloneRecipeForEditing(recipe) {
  return structuredClone(recipe.recipeJson);
}

export function createFinalRecipeFromBase(baseRecipe, recipeJson, formData) {
  const displayName = String(formData.displayName || baseRecipe.displayName).trim() || baseRecipe.displayName;
  const firmwareName = sanitizeFirmwareName(formData.firmwareName || displayName);
  if (!Array.isArray(recipeJson.name) || recipeJson.name.length === 0) {
    recipeJson.name = [firmwareName];
  } else {
    recipeJson.name[0] = firmwareName;
  }
  return {
    id: uid("recipe"),
    type: "final",
    baseRecipeId: baseRecipe.id,
    source: "final",
    zipName: "",
    zipUrl: "",
    recipeTextEntryName: "",
    rawRecipeText: JSON.stringify(recipeJson),
    displayName,
    firmwareName,
    aliases: String(formData.aliases || baseRecipe.displayName)
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean),
    category: recipeJson.category || baseRecipe.category || "Orders",
    imageDataUrl: formData.imageDataUrl || baseRecipe.imageDataUrl || "",
    recipeJson,
    selected: true,
    createdAt: isoNow(),
    updatedAt: isoNow()
  };
}

export function exportState(state) {
  return JSON.stringify(
    {
      ...state,
      exportedAt: isoNow()
    },
    null,
    2
  );
}

export function importState(rawText, seedRecipes) {
  const parsed = JSON.parse(rawText);
  const merged = mergeSeedRecipes(parsed, seedRecipes);
  merged.version = STATE_VERSION;
  merged.ui = {
    ...createUiState(),
    ...(merged.ui || {}),
    manualMode: {
      ...createUiState().manualMode,
      ...(merged.ui?.manualMode || {})
    }
  };
  merged.settings = {
    ...createDefaultSettings(),
    ...(merged.settings || {}),
    emailit: {
      ...createDefaultSettings().emailit,
      ...(merged.settings?.emailit || {})
    },
    supabase: {
      ...createDefaultSettings().supabase,
      ...(merged.settings?.supabase || {})
    },
    recipeFinder: {
      ...createDefaultSettings().recipeFinder,
      ...(merged.settings?.recipeFinder || {})
    }
  };
  merged.orders = shouldReseedOrders(merged.orders, merged.recipes || [])
    ? {
        current: createSeedOrders(merged.recipes || []),
        incoming: createIncomingOrders(merged.recipes || []),
        previous: createPreviousOrders(merged.recipes || [])
      }
    : hydrateOrders(merged.orders || {}, merged.recipes || []);
  merged.devices = hydrateDevices(merged.devices || [], merged.recipes || []);
  normalizeRuntimeState(merged);
  writeStateToStorage(merged);
  return merged;
}

async function supabaseRequest(settings, path, options = {}) {
  const url = `${settings.url.replace(/\/$/, "")}/rest/v1/${path}`;
  const headers = {
    "Content-Type": "application/json",
    apikey: settings.anonKey,
    Authorization: `Bearer ${settings.anonKey}`,
    Prefer: "return=representation,resolution=merge-duplicates",
    ...options.headers
  };
  const response = await fetch(url, { ...options, headers });
  if (!response.ok) {
    throw new Error(`Supabase request failed: ${response.status}`);
  }
  if (response.status === 204) return null;
  return response.json();
}

function buildSettingsRows(state) {
  return [
    { facility_id: state.currentFacilityId, key: "app_settings", value: state.settings },
    { facility_id: state.currentFacilityId, key: "ui_state", value: state.ui }
  ];
}

function buildRecipeRows(state) {
  return state.recipes.map((recipe) => ({
    id: recipe.id,
    facility_id: state.currentFacilityId,
    base_recipe_id: recipe.baseRecipeId,
    name: recipe.displayName,
    firmware_name: recipe.firmwareName,
    category: recipe.category,
    source: recipe.source,
    image_url: recipe.imageDataUrl,
    zip_url: recipe.zipName,
    recipe_json: recipe.recipeJson,
    is_selected: recipe.selected,
    is_final: recipe.type === "final",
    updated_at: recipe.updatedAt
  }));
}

function buildDeviceRows(state) {
  return state.devices.map((device) => ({
    id: `slot-${device.slot}`,
    facility_id: state.currentFacilityId,
    browser_device_id: device.browserDeviceId || `slot-${device.slot}`,
    bluetooth_name: device.bluetoothName,
    display_name: device.displayName,
    serial_photo_url: device.serialPhotoDataUrl,
    enabled: device.enabled,
    updated_at: isoNow()
  }));
}

function buildPermissionRows(state) {
  return state.devices.flatMap((device) =>
    (device.allowedRecipeIds || []).map((recipeId) => ({
      id: `${device.slot}_${recipeId}`,
      device_id: `slot-${device.slot}`,
      recipe_id: recipeId,
      enabled: true
    }))
  );
}

function buildOrderRows(state) {
  return [...state.orders.current, ...state.orders.previous].map((order) => ({
    id: order.id,
    facility_id: state.currentFacilityId,
    external_order_id: order.orderId,
    source: order.source,
    item_name: order.itemName,
    recipe_firmware_name: order.currentRunFirmwareName || sanitizeFirmwareName(order.recipeLookup || order.itemName),
    quantity: order.quantity,
    special_instructions: order.specialInstructions,
    status: order.status,
    assigned_device_id: order.assignedSlot ? `slot-${order.assignedSlot}` : null,
    payload: order
  }));
}

export async function syncStateToSupabase(state) {
  if (!state.settings.supabase.enabled || !state.settings.supabase.url || !state.settings.supabase.anonKey) {
    throw new Error("Supabase is not configured.");
  }
  const supabase = state.settings.supabase;
  await supabaseRequest(supabase, "facilities", {
    method: "POST",
    body: JSON.stringify(state.facilities)
  });
  await supabaseRequest(supabase, "profiles", {
    method: "POST",
    body: JSON.stringify(state.users.map((user) => ({
      id: user.id,
      facility_id: user.facilityId,
      email: user.email,
      display_name: user.displayName,
      role: user.role
    })))
  });
  await supabaseRequest(supabase, "recipes", {
    method: "POST",
    body: JSON.stringify(buildRecipeRows(state))
  });
  await supabaseRequest(supabase, "devices", {
    method: "POST",
    body: JSON.stringify(buildDeviceRows(state))
  });
  await supabaseRequest(supabase, "device_recipe_permissions", {
    method: "POST",
    body: JSON.stringify(buildPermissionRows(state))
  });
  await supabaseRequest(supabase, "orders", {
    method: "POST",
    body: JSON.stringify(buildOrderRows(state))
  });
  await supabaseRequest(supabase, "settings", {
    method: "POST",
    body: JSON.stringify(buildSettingsRows(state))
  });
}
