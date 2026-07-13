import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { currentPermissions } from "../src/data-store.js";
import {
  canStartQueuedIndex,
  moveQueueIdBeside,
  reorderQueueIds,
  shouldStartQueuedWork
} from "../src/queue-logic.js";

test("arrow controls reorder only the upcoming queue", () => {
  const queue = ["a", "b", "c"];
  assert.deepEqual(reorderQueueIds(queue, "b", "up"), ["b", "a", "c"]);
  assert.deepEqual(reorderQueueIds(queue, "b", "down"), ["a", "c", "b"]);
  assert.deepEqual(reorderQueueIds(queue, "c", "next"), ["c", "a", "b"]);
  assert.deepEqual(queue, ["a", "b", "c"]);
});

test("drag placement supports before and after without losing jobs", () => {
  assert.deepEqual(moveQueueIdBeside(["a", "b", "c"], "c", "a", "before"), ["c", "a", "b"]);
  assert.deepEqual(moveQueueIdBeside(["a", "b", "c"], "a", "b", "after"), ["b", "a", "c"]);
});

test("run-only users may start the first item but cannot skip priority", () => {
  assert.equal(canStartQueuedIndex(0, false), true);
  assert.equal(canStartQueuedIndex(1, false), false);
  assert.equal(canStartQueuedIndex(2, true), true);
});

test("manual queues wait, while auto and explicit stop-and-start handoffs run", () => {
  assert.equal(
    shouldStartQueuedWork({ pendingAssignmentMode: "manual_review", queuedOrderId: "a", explicitOrderId: "", explicitHandoffAgeSeconds: Infinity }),
    false
  );
  assert.equal(
    shouldStartQueuedWork({ pendingAssignmentMode: "auto_route", queuedOrderId: "a", explicitOrderId: "", explicitHandoffAgeSeconds: Infinity }),
    true
  );
  assert.equal(
    shouldStartQueuedWork({ pendingAssignmentMode: "manual_review", queuedOrderId: "a", explicitOrderId: "a", explicitHandoffAgeSeconds: 20 }),
    true
  );
  assert.equal(
    shouldStartQueuedWork({ pendingAssignmentMode: "manual_review", queuedOrderId: "a", explicitOrderId: "a", explicitHandoffAgeSeconds: 91 }),
    false
  );
});

test("queued handoff bypasses the remaining queue busy check", () => {
  const appSource = readFileSync(new URL("../src/app.js", import.meta.url), "utf8");
  assert.match(
    appSource,
    /startOrderFlow\(queuedOrderId, device\.slot, \{ ignoreQueuedWork: true \}\)/,
    "The first queued job must start even when more jobs remain behind it."
  );
});

test("queue priority permission follows administrator configuration", () => {
  const base = { settings: { operatorActsAsManager: false } };
  const permissionsFor = (user) => currentPermissions({ ...base, users: [user], currentUserId: user.id });
  assert.equal(permissionsFor({ id: "admin", role: "main_admin" }).canManageQueues, true);
  assert.equal(permissionsFor({ id: "manager", role: "kitchen_manager", canManageQueues: true }).canManageQueues, true);
  assert.equal(permissionsFor({ id: "operator", role: "operator", canManageQueues: false }).canManageQueues, false);
  assert.equal(permissionsFor({ id: "operator-plus", role: "operator", canManageQueues: true }).canManageQueues, true);
});
