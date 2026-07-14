import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createStore } from "../src/data-store.js";

const appSource = readFileSync(new URL("../src/app.js", import.meta.url), "utf8");

test("the main renderer reconciles existing DOM instead of replacing the app", () => {
  const renderStart = appSource.indexOf("function render() {");
  const renderEnd = appSource.indexOf("async function handleManualOrderSubmit", renderStart);
  const renderSource = appSource.slice(renderStart, renderEnd);

  assert.match(renderSource, /setStableAppMarkup\(/);
  assert.doesNotMatch(renderSource, /app\.innerHTML\s*=/);
});

test("raw BLE messages and parsed telemetry share the batched update path", () => {
  const transportStart = appSource.indexOf("function handleTransportEvents() {");
  const transportEnd = appSource.indexOf("async function connectDevice", transportStart);
  const transportSource = appSource.slice(transportStart, transportEnd);

  const batchedCalls = transportSource.match(/queueTelemetryMutation\(/g) || [];
  assert.equal(batchedCalls.length, 2);
  assert.match(appSource, /TELEMETRY_BATCH_INTERVAL_MS\s*=\s*75/);
  assert.match(appSource, /TELEMETRY_RENDER_INTERVAL_MS\s*=\s*250/);
});

test("telemetry persistence is deferred and subscription metadata is retained", async () => {
  const writes = [];
  const originalLocalStorage = globalThis.localStorage;
  globalThis.localStorage = {
    setItem(key, value) {
      writes.push([key, value]);
    }
  };

  try {
    const store = createStore({ count: 0 });
    let receivedOptions = null;
    store.subscribe((_snapshot, options) => {
      receivedOptions = options;
    });

    store.setState(
      (draft) => {
        draft.count += 1;
        return draft;
      },
      { render: "telemetry", persist: "defer", persistDelayMs: 10 }
    );

    assert.equal(writes.length, 0);
    assert.equal(receivedOptions.render, "telemetry");
    await new Promise((resolve) => setTimeout(resolve, 25));
    assert.equal(writes.length, 1);
  } finally {
    globalThis.localStorage = originalLocalStorage;
  }
});
