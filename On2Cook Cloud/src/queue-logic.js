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

function secondsBetween(startAt, endAt) {
  const start = new Date(startAt || "").getTime();
  const end = new Date(endAt || "").getTime();
  if (!Number.isFinite(start) || !Number.isFinite(end)) return 0;
  return Math.max(0, Math.round((end - start) / 1000));
}

export function getRunTimingMetrics(run, nowAt = new Date().toISOString()) {
  const outcomeText = `${run?.outcome || ""} ${run?.note || ""}`.toLowerCase();
  const outcome = outcomeText.includes("abort") ? "aborted" : "completed";
  const elapsedFromTimestamps = secondsBetween(run?.startedAt, run?.finishedAt);
  const recordedActual = Number(run?.actualDurationSeconds);
  const actualSeconds = Math.max(
    0,
    Math.round(Number.isFinite(recordedActual) && recordedActual > 0 ? recordedActual : elapsedFromTimestamps)
  );
  const recordedPlanned = Number(run?.durationSeconds);
  const plannedSeconds = Math.max(
    0,
    Math.round(Number.isFinite(recordedPlanned) && recordedPlanned > 0 ? recordedPlanned : actualSeconds)
  );
  const remainingSeconds = Math.max(0, plannedSeconds - actualSeconds);
  const waitEndAt = run?.nextStartedAt || nowAt;
  const sinceFinishedSeconds = run?.finishedAt ? secondsBetween(run.finishedAt, waitEndAt) : 0;
  const progressPercent = plannedSeconds > 0
    ? Math.min(100, Math.max(0, Math.round((actualSeconds / plannedSeconds) * 100)))
    : outcome === "completed"
      ? 100
      : 0;

  return {
    outcome,
    actualSeconds,
    plannedSeconds,
    remainingSeconds,
    sinceFinishedSeconds,
    progressPercent,
    waitClosed: Boolean(run?.nextStartedAt)
  };
}
