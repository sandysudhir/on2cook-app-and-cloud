const SERVICE_UUID = "ab0828b1-198e-4351-b779-901fa0e0371e";
const COMMAND_UUID = "4ac8a682-9736-4e5d-932b-e9b31405049c";
const FILE_UUID = "4ac8c682-9736-4e5d-932b-e9b31405049c";
const encoder = new TextEncoder();
const decoder = new TextDecoder();

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function safeText(bytes) {
  try {
    return decoder.decode(bytes);
  } catch (error) {
    console.error("Unable to decode BLE payload.", error);
    return "";
  }
}

function splitTextByUtf8(text, maxBytes) {
  const chunks = [];
  let current = "";
  let currentBytes = 0;
  for (const symbol of text) {
    const symbolBytes = encoder.encode(symbol).length;
    if (current && currentBytes + symbolBytes > maxBytes) {
      chunks.push(current);
      current = symbol;
      currentBytes = symbolBytes;
    } else {
      current += symbol;
      currentBytes += symbolBytes;
    }
  }
  if (current || chunks.length === 0) {
    chunks.push(current);
  }
  return chunks;
}

function parseKeyValues(message) {
  const result = {};
  String(message)
    .split(",")
    .forEach((part) => {
      const [key, ...rest] = part.split("=");
      if (!key || rest.length === 0) return;
      result[key.trim()] = rest.join("=").trim();
    });
  return result;
}

function normalizeRecipeInventoryName(value) {
  return String(value || "")
    .replace(/^.*[\\/]/, "")
    .replace(/\.(txt|json|zip)$/i, "")
    .replace(/^\d+[\])\-.:\s]+/, "")
    .trim()
    .slice(0, 30);
}

function extractRecipeInventoryNames(message) {
  const reserved = new Set([
    "",
    "ack",
    "ack_command",
    "ack_cancel",
    "complete",
    "listend",
    "recipe=none",
    "recipe=complete",
    "instr_run=complete",
    "listrecipes=complete",
    "listrecipes=error",
    "listlogs=complete",
    "listlogs=error"
  ]);
  const names = new Set();
  String(message || "")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .forEach((line) => {
      const lowered = line.toLowerCase();
      if (reserved.has(lowered)) return;
      if (lowered.startsWith("status=") || lowered.startsWith("workstatus=") || lowered.startsWith("firmware=")) return;
      if (lowered.startsWith("readlog=") || lowered.startsWith("logfile=") || lowered.startsWith("listlog")) return;
      const tokens =
        /\.(txt|json|zip)$/i.test(line) || line.includes("/")
          ? [line]
          : line.split(",").map((token) => token.trim()).filter(Boolean);
      tokens.forEach((token) => {
        const normalized = normalizeRecipeInventoryName(token);
        if (!normalized) return;
        if (normalized.includes("=")) return;
        if (!/[A-Za-z0-9]/.test(normalized)) return;
        names.add(normalized);
      });
    });
  return [...names];
}

function isIdleStatusMessage(message, parsed = {}) {
  const upper = String(message || "").trim().toUpperCase();
  if (upper === "WORKSTATUS=IDLE" || upper === "STATUS=IDLE") {
    return true;
  }
  const workStatus = String(parsed.WORKSTATUS || parsed.workstatus || "").trim().toUpperCase();
  const status = String(parsed.STATUS || parsed.status || "").trim().toUpperCase();
  const recipe = String(parsed.RECIPE || parsed.recipe || "").trim();
  const mode = String(parsed.MODE || parsed.mode || "").trim();
  if (workStatus === "IDLE") {
    return true;
  }
  return status === "IDLE" && !recipe && !mode;
}

export class BleTransport extends EventTarget {
  constructor() {
    super();
    this.sessions = new Map();
    this.supported = Boolean(navigator.bluetooth);
  }

  getSession(slot) {
    return this.sessions.get(slot) || null;
  }

  getConnectedSlots() {
    return Array.from(this.sessions.values())
      .filter((session) => session.server?.connected)
      .map((session) => session.slot);
  }

  async getKnownDevices() {
    if (!navigator.bluetooth?.getDevices) return [];
    try {
      return await navigator.bluetooth.getDevices();
    } catch (error) {
      console.error("Unable to fetch known BLE devices.", error);
      return [];
    }
  }

  async connect(slot, rememberedBrowserDeviceId = "") {
    if (!this.supported) {
      throw new Error("Web Bluetooth is not available in this browser.");
    }
    let device = null;
    if (rememberedBrowserDeviceId && navigator.bluetooth.getDevices) {
      const known = await navigator.bluetooth.getDevices();
      device = known.find((item) => item.id === rememberedBrowserDeviceId) || null;
    }
    if (!device) {
      device = await navigator.bluetooth.requestDevice({
        filters: [{ services: [SERVICE_UUID] }],
        optionalServices: [SERVICE_UUID]
      });
    }
    const server = await device.gatt.connect();
    const service = await server.getPrimaryService(SERVICE_UUID);
    const commandCharacteristic = await service.getCharacteristic(COMMAND_UUID);
    const fileCharacteristic = await service.getCharacteristic(FILE_UUID);

    const previous = this.sessions.get(slot);
    if (previous?.device && previous.device !== device) {
      this.cleanupSession(previous);
    }

    const session = {
      slot,
      device,
      server,
      service,
      commandCharacteristic,
      fileCharacteristic,
      transfer: null,
      run: null,
      recipeListRequest: null,
      pendingCommand: null,
      lastDeviceMessage: null,
      lastActivityAt: new Date().toISOString(),
      writeChain: Promise.resolve()
    };

    const handleDisconnect = () => {
      this.cleanupSession(session);
      this.dispatch("device-disconnected", {
        slot,
        browserDeviceId: device.id,
        bluetoothName: device.name || ""
      });
    };

    session.onDisconnected = handleDisconnect;
    device.addEventListener("gattserverdisconnected", handleDisconnect);
    await this.startNotifications(session, commandCharacteristic, "command");
    await this.startNotifications(session, fileCharacteristic, "file");
    this.sessions.set(slot, session);

    this.dispatch("device-connected", {
      slot,
      browserDeviceId: device.id,
      bluetoothName: device.name || "",
      serviceUuid: SERVICE_UUID
    });

    return {
      slot,
      browserDeviceId: device.id,
      bluetoothName: device.name || ""
    };
  }

  async disconnect(slot) {
    const session = this.sessions.get(slot);
    if (!session) return;
    this.cleanupSession(session);
    if (session.device?.gatt?.connected) {
      session.device.gatt.disconnect();
    }
    this.sessions.delete(slot);
  }

  cleanupSession(session) {
    if (session.transfer?.reject) {
      session.transfer.reject(new Error(`Device slot ${session.slot} disconnected during file transfer.`));
    }
    if (session.transfer?.timeoutId) {
      clearTimeout(session.transfer.timeoutId);
    }
    if (session.run?.fallbackId) {
      clearTimeout(session.run.fallbackId);
    }
    if (session.run?.selectionId) {
      clearTimeout(session.run.selectionId);
    }
    if (session.run?.statusId) {
      clearTimeout(session.run.statusId);
    }
    if (session.recipeListRequest?.timeoutId) {
      clearTimeout(session.recipeListRequest.timeoutId);
    }
    if (session.recipeListRequest?.reject) {
      session.recipeListRequest.reject(new Error(`Device slot ${session.slot} disconnected while reading recipe inventory.`));
    }
    if (session.device && session.onDisconnected) {
      session.device.removeEventListener("gattserverdisconnected", session.onDisconnected);
    }
    session.transfer = null;
    session.run = null;
    session.recipeListRequest = null;
    this.sessions.delete(session.slot);
  }

  async startNotifications(session, characteristic, channel) {
    await characteristic.startNotifications();
    characteristic.addEventListener("characteristicvaluechanged", (event) => {
      const bytes = new Uint8Array(event.target.value.buffer);
      this.onNotification(session.slot, channel, bytes);
    });
  }

  dispatch(type, detail) {
    this.dispatchEvent(new CustomEvent(type, { detail }));
  }

  onNotification(slot, channel, bytes) {
    const session = this.sessions.get(slot);
    if (!session) return;
    const message = safeText(bytes).trim();
    const normalized = message.toLowerCase();
    const parsed = parseKeyValues(message);
    session.lastActivityAt = new Date().toISOString();
    const detail = {
      slot,
      channel,
      rawBytes: Array.from(bytes),
      message,
      parsed,
      at: session.lastActivityAt
    };
    session.lastDeviceMessage = detail;

    this.dispatch("device-message", detail);

    if (!message) return;

    if (normalized === "ack_command" && session.pendingCommand) {
      const pendingCommand = session.pendingCommand;
      session.pendingCommand = null;
      if (session.run && pendingCommand.type === "recipe-select") {
        session.run.commandAcknowledged = true;
      }
      this.dispatch("command-acknowledged", {
        slot,
        type: pendingCommand.type,
        command: pendingCommand.message,
        at: session.lastActivityAt,
        message
      });
    }

    if (session.recipeListRequest) {
      const listState = session.recipeListRequest;
      if (normalized === "listend" || normalized === "listrecipes=complete" || normalized === "listrecipe=complete") {
        this.finishRecipeInventory(session);
        return;
      }
      if (normalized === "listrecipes=error" || normalized === "listrecipe=error") {
        this.finishRecipeInventory(session, new Error("Device could not list recipes."));
        return;
      }
      extractRecipeInventoryNames(message).forEach((name) => listState.names.add(name));
    }

    if (normalized === "ack" && session.transfer) {
      this.advanceTransfer(session);
      return;
    }

    if (normalized === "recipe=none") {
      if (session.run?.selectionId) {
        clearTimeout(session.run.selectionId);
      }
      if (session.run?.fallbackId) {
        clearTimeout(session.run.fallbackId);
      }
      if (session.run?.statusId) {
        clearTimeout(session.run.statusId);
      }
      session.run = null;
      this.dispatch("recipe-missing", {
        slot,
        at: session.lastActivityAt,
        message
      });
      return;
    }

    if (session.run) {
      const recipeName = session.run.recipeName.toLowerCase();
      const reportedRecipe = String(parsed.RECIPE || parsed.recipe || "").trim().toLowerCase();
      const reportedMode = String(parsed.MODE || parsed.mode || "").trim().toLowerCase();
      const isSelectionAck =
        reportedRecipe === recipeName ||
        (reportedRecipe === recipeName && (reportedMode.includes("receipe") || reportedMode.includes("recipe") || reportedMode.includes("ingredient") || reportedMode.includes("cooking"))) ||
        normalized.includes("workstatus=cooking");
      if (isSelectionAck && !session.run.selectionAcknowledged) {
        session.run.selectionAcknowledged = true;
        this.dispatch("recipe-selection-acknowledged", {
          slot,
          recipeName: session.run.recipeName,
          at: session.lastActivityAt,
          message
        });
      }
      if (session.run.autoStartAfterIngredient && !session.run.ingredientsSent && reportedRecipe === recipeName && reportedMode.includes("ingredient")) {
        window.setTimeout(() => {
          this.sendIngredientsAdvance(slot).catch((error) => {
            console.error("Unable to send ingredients advance.", error);
          });
        }, 350);
      }
    }

    if (normalized === "instr_run=complete") {
      this.dispatch("instruction-complete", {
        slot,
        at: session.lastActivityAt,
        message
      });
      return;
    }

    if (normalized === "stop=100") {
      this.dispatch("recipe-stop-signal", {
        slot,
        at: session.lastActivityAt,
        message
      });
    }

    if (normalized === "recipe=complete") {
      if (session.run?.fallbackId) {
        clearTimeout(session.run.fallbackId);
      }
      if (session.run?.selectionId) {
        clearTimeout(session.run.selectionId);
      }
      if (session.run?.statusId) {
        clearTimeout(session.run.statusId);
      }
      this.dispatch("recipe-complete", {
        slot,
        at: session.lastActivityAt,
        message
      });
      session.run = null;
      return;
    }

    if (message.startsWith("Firmware=")) {
      this.dispatch("firmware-version", {
        slot,
        firmwareVersion: message.replace("Firmware=", "").trim(),
        at: session.lastActivityAt
      });
    }

    if (
      message.startsWith("LOGFILE=") ||
      message.startsWith("READLOG=") ||
      message === "LISTLOGS=COMPLETE" ||
      message === "LISTLOGS=ERROR"
    ) {
      this.dispatch("log-message", {
        slot,
        message,
        at: session.lastActivityAt
      });
      return;
    }

    if (Object.keys(parsed).length > 0) {
      this.dispatch("telemetry", {
        slot,
        parsed,
        message,
        at: session.lastActivityAt
      });
    }
  }

  async writeCharacteristic(characteristic, value, preferResponse = true) {
    const bytes = value instanceof Uint8Array ? value : encoder.encode(String(value));
    const attempts = preferResponse
      ? [
          characteristic.writeValueWithResponse?.bind(characteristic),
          characteristic.writeValueWithoutResponse?.bind(characteristic),
          characteristic.writeValue?.bind(characteristic)
        ]
      : [
          characteristic.writeValueWithoutResponse?.bind(characteristic),
          characteristic.writeValueWithResponse?.bind(characteristic),
          characteristic.writeValue?.bind(characteristic)
        ];
    let lastError = null;
    for (const attempt of attempts) {
      if (!attempt) continue;
      try {
        await attempt(bytes);
        return;
      } catch (error) {
        lastError = error;
      }
    }
    throw lastError || new Error("Characteristic is not writable.");
  }

  enqueueSessionWrite(session, task) {
    const chain = Promise.resolve(session.writeChain || Promise.resolve());
    const next = chain
      .catch(() => {})
      .then(task);
    session.writeChain = next.catch(() => {});
    return next;
  }

  async sendCommand(slot, message) {
    const session = this.requireSession(slot);
    if (
      typeof message === "string" &&
      (message.startsWith("recipe=") || message.startsWith("ingredients=") || message.startsWith("add_confirm="))
    ) {
      session.pendingCommand = {
        type: message.startsWith("recipe=")
          ? "recipe-select"
          : message.startsWith("ingredients=")
            ? "ingredients-advance"
            : "instruction-ack",
        message,
        at: new Date().toISOString()
      };
    } else if (typeof message === "string" && !message.startsWith("STATUS=?")) {
      session.pendingCommand = null;
    }
    await this.enqueueSessionWrite(session, async () => {
      await this.writeCharacteristic(session.commandCharacteristic, message, true);
    });
    this.dispatch("command-sent", {
      slot,
      channel: "command",
      message,
      at: new Date().toISOString()
    });
  }

  async sendFile(slot, messageOrBytes) {
    const session = this.requireSession(slot);
    await this.enqueueSessionWrite(session, async () => {
      await this.writeCharacteristic(session.fileCharacteristic, messageOrBytes, false);
    });
    this.dispatch("command-sent", {
      slot,
      channel: "file",
      message: typeof messageOrBytes === "string" ? messageOrBytes : `[${messageOrBytes.length} bytes]`,
      at: new Date().toISOString()
    });
  }

  requireSession(slot) {
    const session = this.sessions.get(slot);
    if (!session || !session.server?.connected) {
      throw new Error(`Device slot ${slot} is not connected.`);
    }
    return session;
  }

  async requestStatus(slot) {
    await this.sendCommand(slot, "STATUS=?");
  }

  async waitForDeviceMessage(slot, predicate, options = {}) {
    const session = this.requireSession(slot);
    if (!options.forceFresh && session.lastDeviceMessage && predicate(session.lastDeviceMessage)) {
      return session.lastDeviceMessage;
    }
    return new Promise((resolve, reject) => {
      const timeoutMs = options.timeoutMs || 3200;
      const description = options.description || "a device response";
      let timeoutId = 0;
      const handleMessage = (event) => {
        const detail = event.detail;
        if (Number(detail.slot) !== Number(slot)) return;
        if (!predicate(detail)) return;
        cleanup();
        resolve(detail);
      };
      const handleDisconnect = (event) => {
        if (Number(event.detail.slot) !== Number(slot)) return;
        cleanup();
        reject(new Error(`Device ${slot} disconnected while waiting for ${description}.`));
      };
      const cleanup = () => {
        clearTimeout(timeoutId);
        this.removeEventListener("device-message", handleMessage);
        this.removeEventListener("device-disconnected", handleDisconnect);
      };
      timeoutId = window.setTimeout(() => {
        cleanup();
        reject(new Error(`Timed out waiting for ${description} on Device ${slot}.`));
      }, timeoutMs);
      this.addEventListener("device-message", handleMessage);
      this.addEventListener("device-disconnected", handleDisconnect);
    });
  }

  async waitForIdleStatus(slot, options = {}) {
    const timeoutMs = options.timeoutMs || 3200;
    const pollEveryMs = options.pollEveryMs || 650;
    const waitPromise = this.waitForDeviceMessage(
      slot,
      (detail) => isIdleStatusMessage(detail.message, detail.parsed),
      {
        timeoutMs,
        description: options.description || "idle status",
        forceFresh: options.forceFresh === true
      }
    );
    let pollId = 0;
    try {
      await this.requestStatus(slot);
      pollId = window.setInterval(() => {
        this.requestStatus(slot).catch(() => {});
      }, pollEveryMs);
      return await waitPromise;
    } finally {
      if (pollId) {
        clearInterval(pollId);
      }
    }
  }

  async requestFirmwareVersion(slot) {
    await this.sendCommand(slot, "Firmware=?");
  }

  async sendDateTime(slot, epochSeconds = Math.floor(Date.now() / 1000)) {
    const safeEpoch = Math.max(0, Math.trunc(Number(epochSeconds) || 0));
    await this.sendCommand(slot, `DATETIME=${safeEpoch}`);
  }

  async startInduction(slot) {
    await this.sendCommand(slot, "INDQUICKSTART=START");
  }

  async stopInduction(slot) {
    await this.sendCommand(slot, "INDQUICKSTART=STOP");
  }

  async pauseInduction(slot) {
    await this.sendCommand(slot, "INDQUICKSTART=PAUSE");
  }

  async resumeInduction(slot) {
    await this.sendCommand(slot, "INDQUICKSTART=RESUME");
  }

  async changeInductionPower(slot, delta) {
    const safeDelta = Math.trunc(Number(delta) || 0);
    if (!safeDelta) return;
    await this.sendCommand(slot, `INDPOWER=${safeDelta}`);
  }

  async startMagnetron(slot) {
    await this.sendCommand(slot, "MAGQUICKSTART=START");
  }

  async stopMagnetron(slot) {
    await this.sendCommand(slot, "MAGQUICKSTART=STOP");
  }

  async pauseMagnetron(slot) {
    await this.sendCommand(slot, "MAGQUICKSTART=PAUSE");
  }

  async resumeMagnetron(slot) {
    await this.sendCommand(slot, "MAGQUICKSTART=RESUME");
  }

  async setStirrer(slot, speed) {
    const normalized = String(speed || "").trim().toUpperCase();
    if (!normalized || normalized === "OFF") {
      await this.sendCommand(slot, "STIRRER=OFF");
      return;
    }
    const allowed = new Set(["LOW", "MED", "HIGH", "VERY_HIGH"]);
    if (!allowed.has(normalized)) {
      throw new Error(`Unsupported stirrer speed: ${speed}`);
    }
    await this.sendCommand(slot, `STIRRER=ON,${normalized}`);
  }

  async startPump(slot, units) {
    const safeUnits = Math.max(1, Math.trunc(Number(units) || 0));
    await this.sendCommand(slot, `PUMP=ON,${safeUnits}`);
  }

  async stopPump(slot) {
    await this.sendCommand(slot, "PUMP=OFF");
  }

  async listRecipes(slot) {
    await this.sendCommand(slot, "LISTRECIPES");
  }

  async readRecipesAvailable(slot, options = {}) {
    const session = this.requireSession(slot);
    if (session.recipeListRequest) {
      this.finishRecipeInventory(session, new Error("A recipe inventory request was replaced by a newer request."));
    }
    return new Promise(async (resolve, reject) => {
      const timeoutMs = options.timeoutMs || 4500;
      session.recipeListRequest = {
        names: new Set(),
        resolve,
        reject,
        timeoutId: setTimeout(() => {
          this.finishRecipeInventory(session);
        }, timeoutMs)
      };
      try {
        await this.listRecipes(slot);
      } catch (error) {
        this.finishRecipeInventory(session, error);
      }
    });
  }

  async listLogs(slot) {
    await this.sendCommand(slot, "LISTLOGS");
  }

  async readLog(slot, fileName) {
    await this.sendCommand(slot, `READLOG=${fileName}`);
  }

  async setLiveLog(slot, enabled) {
    await this.sendCommand(slot, enabled ? "livelog=ON" : "livelog=OFF");
  }

  async abortRecipe(slot) {
    throw new Error("This firmware flow expects stop notifications from the device. The web app will not send stop=100.");
  }

  async sendIngredientsValue(slot, value = 100) {
    await this.sendCommand(slot, `ingredients=${value}`);
  }

  async sendIngredientsAdvance(slot) {
    const session = this.requireSession(slot);
    if (!session.run || session.run.ingredientsSent) return;
    const recipeName = session.run.recipeName;
    await this.sendIngredientsValue(slot, 100);
    if (!session.run) return;
    session.run.ingredientsSent = true;
    if (session.run.fallbackId) {
      clearTimeout(session.run.fallbackId);
      session.run.fallbackId = null;
    }
    this.dispatch("ingredients-advanced", {
      slot,
      recipeName,
      at: new Date().toISOString()
    });
  }

  async sendAddConfirm(slot, stepNo, options = {}) {
    const safeStep = Math.max(1, Number(stepNo) || 1);
    const magTime = Number(options.magTime);
    const indTime = Number(options.indTime);
    let payload = `add_confirm=${safeStep}`;
    if (Number.isFinite(magTime) || Number.isFinite(indTime)) {
      payload += `,magTime=${Number.isFinite(magTime) ? Math.trunc(magTime) : 0},indTime=${Number.isFinite(indTime) ? Math.trunc(indTime) : 0}`;
    }
    await this.sendCommand(slot, payload);
    this.dispatch("instruction-acknowledged", {
      slot,
      stepNo: safeStep,
      message: payload,
      at: new Date().toISOString()
    });
  }

  async runRecipe(slot, recipeName, options = {}) {
    const session = this.requireSession(slot);
    const autoStartAfterIngredient = options.autoStartAfterIngredient === true;
    session.run = {
      recipeName,
      autoStartAfterIngredient,
      ingredientsSent: false,
      selectionAcknowledged: false,
      commandAcknowledged: false,
      fallbackId: null,
      selectionId: null,
      statusId: null,
      quietUntil: 0
    };
    await this.sendCommand(slot, `recipe=${recipeName}`);
    let selectionMessage = null;
    try {
      selectionMessage = await this.waitForDeviceMessage(
        slot,
        (detail) => {
          const normalized = String(detail.message || "").trim().toLowerCase();
          if (normalized === "ack_command" || normalized === "recipe=none") {
            return true;
          }
          const parsed = detail.parsed || {};
          const reportedRecipe = String(parsed.RECIPE || parsed.recipe || "").trim().toLowerCase();
          return reportedRecipe === String(recipeName || "").trim().toLowerCase();
        },
        {
          timeoutMs: options.ackTimeoutMs || 1400,
          description: `recipe selection acknowledgement for ${recipeName}`,
          forceFresh: true
        }
      );
    } catch (error) {
      console.warn(`Recipe selection acknowledgement for ${recipeName} was not observed in time.`, error);
    }
    if (!session.run) return;
    if (String(selectionMessage?.message || "").trim().toLowerCase() === "recipe=none") {
      return;
    }
    session.run.quietUntil = Date.now() + (options.quietPeriodMs || 7000);
    session.run.statusId = window.setTimeout(() => {
      this.requestStatus(slot).catch((error) => {
        console.error("Unable to request post-selection status.", error);
      });
    }, options.statusDelayMs || 650);
    if (autoStartAfterIngredient) {
      session.run.fallbackId = setTimeout(() => {
        this.sendIngredientsAdvance(slot).catch((error) => {
          console.error("Unable to send fallback ingredients advance.", error);
        });
      }, options.fallbackMs || 1800);
    }
  }

  async syncRecipes(slot, recipes, progressCallback = null, options = {}) {
    const total = recipes.length;
    for (let index = 0; index < recipes.length; index += 1) {
      const recipe = recipes[index];
      if (progressCallback) {
        progressCallback({
          current: index + 1,
          total,
          recipeName: recipe.firmwareName
        });
      }
      const recipeText =
        typeof recipe.rawRecipeText === "string" && recipe.rawRecipeText.trim()
          ? recipe.rawRecipeText
          : JSON.stringify(recipe.recipeJson || {});
      await this.uploadRecipeJson(slot, recipe.firmwareName, recipeText, options);
    }
  }

  async uploadRecipeJson(slot, recipeName, jsonText, options = {}) {
    const session = this.requireSession(slot);
    if (session.transfer) {
      throw new Error(`A transfer is already in progress for slot ${slot}.`);
    }
    const payloadBytes = encoder.encode(jsonText);
    const transfer = {
      slot,
      recipeName,
      jsonText,
      size: payloadBytes.length,
      chunks: splitTextByUtf8(jsonText, 120),
      ackIndex: 0,
      timeoutId: null,
      lastPayload: "",
      resolve: null,
      reject: null
    };
    session.transfer = transfer;

    return new Promise(async (resolve, reject) => {
      transfer.resolve = resolve;
      transfer.reject = reject;
      try {
        if (options.overwriteExisting === true) {
          await this.sendCommand(slot, `DELETE=${recipeName}`);
          await delay(450);
        } else {
          await delay(80);
        }
        const header = `{"RECIPE":"${recipeName}","SIZE":"${transfer.size} ","SAVE":"1"}`;
        await this.sendFile(slot, header);
        this.armTransferRetry(session, header);
        this.dispatch("transfer-started", {
          slot,
          recipeName,
          totalChunks: transfer.chunks.length,
          size: transfer.size
        });
      } catch (error) {
        session.transfer = null;
        reject(error);
      }
    });
  }

  armTransferRetry(session, payload) {
    if (!session.transfer) return;
    if (session.transfer.timeoutId) {
      clearTimeout(session.transfer.timeoutId);
    }
    session.transfer.lastPayload = payload;
    session.transfer.timeoutId = setTimeout(() => {
      if (!session.transfer) return;
      this.sendFile(session.slot, session.transfer.lastPayload).catch((error) => {
        console.error("Unable to retry file transfer payload.", error);
      });
      this.dispatch("transfer-retry", {
        slot: session.slot,
        recipeName: session.transfer.recipeName,
        ackIndex: session.transfer.ackIndex
      });
      this.armTransferRetry(session, session.transfer.lastPayload);
    }, 3000);
  }

  async advanceTransfer(session) {
    const transfer = session.transfer;
    if (!transfer) return;
    if (transfer.timeoutId) {
      clearTimeout(transfer.timeoutId);
      transfer.timeoutId = null;
    }
    transfer.ackIndex += 1;
    if (transfer.ackIndex <= transfer.chunks.length) {
      const payload = `PNO=${transfer.ackIndex},DATA=${transfer.chunks[transfer.ackIndex - 1]}`;
      await this.sendFile(session.slot, payload);
      this.armTransferRetry(session, payload);
      this.dispatch("transfer-progress", {
        slot: session.slot,
        recipeName: transfer.recipeName,
        currentChunk: transfer.ackIndex,
        totalChunks: transfer.chunks.length
      });
      return;
    }
    if (transfer.ackIndex === transfer.chunks.length + 1) {
      await this.sendFile(session.slot, "COMPLETE");
      this.armTransferRetry(session, "COMPLETE");
      return;
    }
    session.transfer = null;
    transfer.resolve({
      slot: session.slot,
      recipeName: transfer.recipeName,
      size: transfer.size
    });
    this.dispatch("transfer-complete", {
      slot: session.slot,
      recipeName: transfer.recipeName,
      size: transfer.size
    });
  }

  finishRecipeInventory(session, error = null) {
    const request = session.recipeListRequest;
    if (!request) return;
    if (request.timeoutId) {
      clearTimeout(request.timeoutId);
    }
    session.recipeListRequest = null;
    const names = [...request.names];
    if (error && names.length === 0) {
      request.reject(error);
      return;
    }
    request.resolve(names);
  }
}

export const BLE_UUIDS = {
  SERVICE_UUID,
  COMMAND_UUID,
  FILE_UUID
};
