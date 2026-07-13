export function reorderQueueIds(queueIds, orderId, mode) {
  const ids = Array.from(queueIds || []);
  const index = ids.indexOf(orderId);
  if (index < 0) return ids;
  const [picked] = ids.splice(index, 1);
  if (mode === "up") {
    ids.splice(Math.max(0, index - 1), 0, picked);
  } else if (mode === "down") {
    ids.splice(Math.min(ids.length, index + 1), 0, picked);
  } else {
    ids.unshift(picked);
  }
  return ids;
}

export function moveQueueIdBeside(queueIds, orderId, targetOrderId, position = "before") {
  const ids = Array.from(queueIds || []);
  if (orderId === targetOrderId || !ids.includes(orderId) || !ids.includes(targetOrderId)) return ids;
  const ordered = ids.filter((item) => item !== orderId);
  const targetIndex = ordered.indexOf(targetOrderId);
  ordered.splice(targetIndex + (position === "after" ? 1 : 0), 0, orderId);
  return ordered;
}

export function removeQueueId(queueIds, orderId) {
  return Array.from(queueIds || []).filter((item) => item !== orderId);
}

export function canStartQueuedIndex(index, canManageQueues) {
  return Number(index) === 0 || Boolean(canManageQueues);
}

export function shouldStartQueuedWork({
  pendingAssignmentMode,
  queuedOrderId,
  explicitOrderId,
  explicitHandoffAgeSeconds
}) {
  if (!queuedOrderId) return false;
  if (pendingAssignmentMode === "auto_route") return true;
  return explicitOrderId === queuedOrderId && explicitHandoffAgeSeconds >= 0 && explicitHandoffAgeSeconds <= 90;
}
