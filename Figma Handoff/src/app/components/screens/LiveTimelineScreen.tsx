import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router';
import {
  Pause, Play, StopCircle, AlertTriangle, ChevronLeft, ChevronRight,
  CheckCircle2, FlagTriangleRight, UtensilsCrossed, X as XIcon,
} from 'lucide-react';
import { useApp } from '../../AppContext';
import { ProMinute, StirrerSpeedType, getProRecipeTotalSeconds } from '../../proTypes';

// ─── Stirrer speed helpers ─────────────────────────────────────────────────────
const LIVE_STIR_BAR_H: Record<StirrerSpeedType, number> = {
  'low': 8, 'medium': 15, 'high': 22, 'very-high': 30,
};
const LIVE_STIR_COLOR: Record<StirrerSpeedType, string> = {
  'low': '#5eead4', 'medium': '#2dd4bf', 'high': '#14b8a6', 'very-high': '#0d9488',
};
const LIVE_STIR_LABEL: Record<StirrerSpeedType, string> = {
  'low': 'Lo', 'medium': 'Md', 'high': 'Hi', 'very-high': 'VH',
};

// ─── Landscape layout constants ───────────────────────────────────────────────
const CELL_W = 76;
const ROW_H = 44;
const LABEL_W = 54;
const HEADER_H = 26;
const TOP_BAR_H = 54;
const BOT_BAR_H = 44;
const DI_OFFSET = 50;
const MAX_HOLD_SEC = 180;
const MIN_HOLD_COL_W = 28;

type RowKey = 'lid' | 'induction' | 'microwave' | 'stirrer' | 'water';
const ROW_META: { key: RowKey; letter: string; label: string; color: string }[] = [
  { key: 'lid',       letter: 'L', label: 'Lid',       color: '#4ade80' },
  { key: 'induction', letter: 'I', label: 'Induction', color: '#f97316' },
  { key: 'microwave', letter: 'M', label: 'Microwave', color: '#ec4899' },
  { key: 'stirrer',   letter: 'S', label: 'Stirrer',   color: '#14b8a6' },
  { key: 'water',     letter: 'W', label: 'Water',     color: '#22d3ee' },
];

type HoldRecord = {
  minuteIdx: number;
  durationSec: number;
  recipeElapsed: number;
  type: 'ingredients' | 'close-lid';
  aborted?: boolean;
};

// ─── Column layout types ──────────────────────────────────────────────────────
type TimelineCol =
  | { kind: 'minute'; idx: number }
  | { kind: 'hold'; key: string; durationSec: number; isActive: boolean; aborted?: boolean };

function holdColW(sec: number): number {
  return Math.max(MIN_HOLD_COL_W, (sec / 60) * CELL_W);
}

function getInsertBeforeMinute(hold: HoldRecord): number {
  return hold.type === 'ingredients' ? hold.minuteIdx : hold.minuteIdx + 1;
}

function buildColumns(
  numMinutes: number,
  completedHolds: HoldRecord[],
  activeHold: { minuteIdx: number; type: 'ingredients' | 'close-lid'; durationSec: number } | null,
): TimelineCol[] {
  const cols: TimelineCol[] = [];
  for (let i = 0; i < numMinutes; i++) {
    for (const h of completedHolds) {
      if (getInsertBeforeMinute(h) === i) {
        cols.push({ kind: 'hold', key: `h-${h.minuteIdx}-${h.type}`, durationSec: h.durationSec, isActive: false, aborted: h.aborted });
      }
    }
    if (activeHold) {
      const insertBefore = activeHold.type === 'ingredients' ? activeHold.minuteIdx : activeHold.minuteIdx + 1;
      if (insertBefore === i) {
        cols.push({ kind: 'hold', key: 'active', durationSec: activeHold.durationSec, isActive: true });
      }
    }
    cols.push({ kind: 'minute', idx: i });
  }
  return cols;
}

function getPlayheadX(
  elapsed: number,
  cols: TimelineCol[],
  isHolding: boolean,
  holdMinuteIdx: number,
  holdType: 'ingredients' | 'close-lid',
): number {
  if (isHolding && holdMinuteIdx >= 0) {
    const insertBefore = holdType === 'ingredients' ? holdMinuteIdx : holdMinuteIdx + 1;
    let x = 0;
    for (const col of cols) {
      if (col.kind === 'hold' && col.isActive) return x;
      if (col.kind === 'minute' && col.idx === insertBefore) return x;
      x += col.kind === 'minute' ? CELL_W : holdColW(col.durationSec);
    }
    return x;
  }
  const minuteIdx = Math.floor(elapsed / 60);
  const secIntoMinute = elapsed % 60;
  let x = 0;
  for (const col of cols) {
    if (col.kind === 'minute' && col.idx === minuteIdx) {
      return x + (secIntoMinute / 60) * CELL_W;
    }
    x += col.kind === 'minute' ? CELL_W : holdColW(col.durationSec);
  }
  return x;
}

// ─── Color helpers ────────────────────────────────────────────────────────────
function indColor(p: number): string {
  if (!p) return 'rgba(148,163,184,0.2)';
  if (p <= 40) return '#d97706'; if (p <= 60) return '#f97316';
  if (p <= 80) return '#ef4444'; return '#dc2626';
}
const MW_ON_COL = '#ec4899';
const MW_ON_GLOW = 'rgba(236,72,153,0.55)';

function formatMSS(sec: number): string {
  const s = Math.max(0, Math.round(sec));
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${m.toString().padStart(2, '0')}:${r.toString().padStart(2, '0')}`;
}

// ─── Sub-bar visualization ────────────────────────────────────────────────────
function SubBars({ values, colorFn, dim }: { values: number[]; colorFn: (v: number) => string; dim?: boolean }) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', width: '100%', height: '100%', gap: 1, padding: '3px 3px' }}>
      {values.map((v, i) => (
        <div key={i} style={{
          flex: 1,
          height: Math.max(2, Math.round((v / 100) * (ROW_H - 10))),
          borderRadius: '2px 2px 0 0',
          background: colorFn(v),
          opacity: dim ? 0.25 : 0.9,
        }} />
      ))}
    </div>
  );
}

// ─── Live Cell (minute columns only) ─────────────────────────────────────────
function LiveCell({ minute, row, isPast, isCurrent, subProgress, currentSubIdx }: {
  minute: ProMinute; row: RowKey; isPast?: boolean; isCurrent?: boolean;
  subProgress?: number; currentSubIdx?: number;
}) {
  const dim = isPast ?? false;
  const forcedMwOff = row === 'microwave' && minute.lidOpen;

  const base: React.CSSProperties = {
    width: CELL_W, height: ROW_H, flexShrink: 0, position: 'relative', overflow: 'hidden',
    opacity: dim ? 0.35 : 1,
    border: `1px solid ${isCurrent ? '#60a5fa' : 'rgba(148,163,184,0.15)'}`,
    boxShadow: isCurrent ? '0 0 0 1px rgba(96,165,250,0.3), inset 0 0 16px rgba(96,165,250,0.04)' : 'none',
    transition: 'opacity 0.4s',
  };

  if (isPast) {
    return (
      <div style={{ ...base, background: 'rgba(148,163,184,0.05)' }}>
        <div style={{ position: 'absolute', inset: 0, background: 'rgba(240,244,255,0.62)', zIndex: 2 }} />
        {row === 'induction' && (
          <SubBars values={minute.subBlocks.map(s => s.inductionPower)} colorFn={indColor} dim />
        )}
        <div style={{ position: 'absolute', top: 2, left: 2, zIndex: 3 }}>
          <CheckCircle2 size={9} color="rgba(22,163,74,0.5)" />
        </div>
      </div>
    );
  }

  return (
    <div style={{ ...base, background: 'rgba(148,163,184,0.04)' }}>
      {isCurrent && typeof currentSubIdx === 'number' && (
        <div style={{
          position: 'absolute', left: currentSubIdx * (CELL_W / 4), top: 0,
          width: CELL_W / 4, height: ROW_H, zIndex: 1,
          background: 'rgba(96,165,250,0.1)', borderRight: '1px solid rgba(96,165,250,0.25)',
        }} />
      )}

      {row === 'lid' && (
        <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 2, background: minute.lidOpen ? 'rgba(74,222,128,0.1)' : 'transparent', zIndex: 2 }}>
          {minute.lidOpen
            ? <><span style={{ fontSize: 8, color: '#16a34a', fontWeight: 700 }}>OPEN</span>{minute.ingredients.length > 0 && <span style={{ fontSize: 7, color: 'rgba(22,163,74,0.65)' }}>+{minute.ingredients.length}</span>}</>
            : <span style={{ fontSize: 8, color: 'rgba(100,116,139,0.35)' }}>—</span>}
        </div>
      )}

      {row === 'induction' && (
        <div style={{ position: 'absolute', inset: 0, zIndex: 2 }}>
          <SubBars values={minute.subBlocks.map(s => s.inductionPower)} colorFn={indColor} />
        </div>
      )}

      {row === 'microwave' && (
        <div style={{ position: 'absolute', inset: 0, zIndex: 2, display: 'flex', alignItems: 'center', padding: '0 4px', gap: 2 }}>
          {forcedMwOff ? (
            <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
              <span style={{ fontSize: 7, color: 'rgba(236,72,153,0.4)', fontWeight: 700 }}>OFF</span>
            </div>
          ) : (
            minute.subBlocks.map((sb, i) => {
              const isBlockPast = isPast || (isCurrent && typeof currentSubIdx === 'number' && i < currentSubIdx);
              const isBlockCurrent = isCurrent && typeof currentSubIdx === 'number' && currentSubIdx === i;
              const on = sb.microwaveActive;
              const power = sb.microwavePower || 800;
              return (
                <div key={i} style={{ flex: 1, position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
                  <div style={{
                    width: '100%',
                    height: on ? ROW_H - 10 : 4,
                    borderRadius: 3,
                    background: isBlockPast ? (on ? 'rgba(236,72,153,0.2)' : 'rgba(148,163,184,0.12)') : on ? MW_ON_COL : 'rgba(148,163,184,0.2)',
                    boxShadow: on && isBlockCurrent ? `0 0 7px ${MW_ON_GLOW}` : 'none',
                    opacity: isBlockPast ? 0.35 : 1,
                    transition: 'height 0.15s',
                  }} />
                  {on && !isBlockPast && (
                    <span style={{ position: 'absolute', top: 2, fontSize: 6, color: '#ec4899', fontWeight: 700, pointerEvents: 'none' }}>{power}W</span>
                  )}
                </div>
              );
            })
          )}
        </div>
      )}

      {row === 'stirrer' && (
        <div style={{ position: 'absolute', inset: 0, zIndex: 2, display: 'flex', alignItems: 'center', padding: '0 4px', gap: 2 }}>
          {minute.subBlocks.map((sb, i) => {
            const isBlockPast = isPast || (isCurrent && typeof currentSubIdx === 'number' && i < currentSubIdx);
            const isBlockCurrent = isCurrent && typeof currentSubIdx === 'number' && currentSubIdx === i;
            const h = sb.stirrerActive ? (LIVE_STIR_BAR_H[sb.stirrerSpeed] ?? 15) : 4;
            const col = sb.stirrerActive ? (LIVE_STIR_COLOR[sb.stirrerSpeed] ?? '#14b8a6') : 'rgba(148,163,184,0.2)';
            return (
              <div key={i} style={{
                flex: 1, height: h, borderRadius: 3,
                background: isBlockPast ? (sb.stirrerActive ? 'rgba(20,184,166,0.22)' : 'rgba(148,163,184,0.12)') : col,
                boxShadow: sb.stirrerActive && isBlockCurrent ? `0 0 7px ${col}90` : 'none',
                opacity: isBlockPast ? 0.35 : 1,
                transition: 'height 0.15s',
              }} />
            );
          })}
        </div>
      )}

      {row === 'water' && (
        <div style={{ position: 'absolute', inset: 0, zIndex: 2, display: 'flex', alignItems: 'center', padding: '0 3px', gap: 2 }}>
          {minute.waterBlocks.map((active, i) => {
            const isBlockPast = isPast || (isCurrent && typeof currentSubIdx === 'number' && i < currentSubIdx);
            const isBlockCurrent = isCurrent && typeof currentSubIdx === 'number' && currentSubIdx === i;
            const opacity = isBlockPast ? 0.3 : 1;
            return (
              <div key={i} style={{
                flex: 1, height: active ? 28 : 6, borderRadius: 3, flexShrink: 0,
                background: active
                  ? (isBlockPast ? 'rgba(34,211,238,0.28)' : isBlockCurrent ? 'linear-gradient(180deg,#67e8f9,#22d3ee)' : 'linear-gradient(180deg,#22d3ee,#0891b2)')
                  : 'rgba(148,163,184,0.18)',
                boxShadow: active && isBlockCurrent ? '0 0 8px rgba(34,211,238,0.6)' : active && !isBlockPast ? '0 0 4px rgba(34,211,238,0.3)' : 'none',
                opacity,
              }} />
            );
          })}
        </div>
      )}

      {isCurrent && typeof subProgress === 'number' && (
        <div style={{
          position: 'absolute', inset: 0, zIndex: 0,
          background: `linear-gradient(90deg, rgba(96,165,250,0.06) ${subProgress * 100}%, transparent ${subProgress * 100}%)`,
        }} />
      )}
    </div>
  );
}

// ─── Hold Column Header Cell ───────────────────────────────────────────────────
function HoldHeaderCell({ col }: { col: TimelineCol & { kind: 'hold' } }) {
  const w = holdColW(col.durationSec);
  const urgency = col.isActive && col.durationSec >= 150;
  const aborted = col.aborted;
  const bg = aborted ? 'rgba(239,68,68,0.12)' : urgency ? 'rgba(239,68,68,0.1)' : 'rgba(217,119,6,0.1)';
  const border = aborted ? 'rgba(239,68,68,0.4)' : urgency ? 'rgba(239,68,68,0.35)' : 'rgba(217,119,6,0.35)';
  const textColor = aborted ? '#dc2626' : urgency ? '#dc2626' : '#d97706';

  return (
    <div style={{
      width: w, height: HEADER_H, flexShrink: 0,
      background: bg, border: `1px solid ${border}`,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      overflow: 'hidden',
      transition: col.isActive ? 'width 0.9s linear' : 'none',
    }}>
      <span style={{ fontSize: 7, color: textColor, fontWeight: 700, whiteSpace: 'nowrap', overflow: 'hidden' }}>
        {aborted ? `✗ ${col.durationSec}s` : col.isActive ? `⏸ ${col.durationSec}s` : `⏸ ${col.durationSec}s`}
      </span>
    </div>
  );
}

// ─── Hold Column Row Cell (fills the amber band for one row) ──────────────────
function HoldRowCell({ col }: { col: TimelineCol & { kind: 'hold' } }) {
  const w = holdColW(col.durationSec);
  const urgency = col.isActive && col.durationSec >= 150;
  const aborted = col.aborted;
  const bg = aborted ? 'rgba(239,68,68,0.1)' : urgency ? 'rgba(239,68,68,0.1)' : 'rgba(217,119,6,0.09)';
  const border = aborted ? 'rgba(239,68,68,0.3)' : urgency ? 'rgba(239,68,68,0.28)' : 'rgba(217,119,6,0.28)';
  return (
    <div style={{
      width: w, height: ROW_H, flexShrink: 0,
      background: bg,
      borderLeft: `1px solid ${border}`,
      borderRight: `1px solid ${border}`,
      transition: col.isActive ? 'width 0.9s linear' : 'none',
    }} />
  );
}

// ─── Status Chip ──────────────────────────────────────────────────────────────
function StatusChip({ label, value, color, active }: { label: string; value: string; color: string; active: boolean }) {
  return (
    <div style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      padding: '4px 9px', borderRadius: 8, flexShrink: 0, minWidth: 40,
      background: active ? `${color}12` : 'rgba(148,163,184,0.08)',
      border: `1px solid ${active ? `${color}45` : 'rgba(148,163,184,0.2)'}`,
    }}>
      <span style={{ fontSize: 12, color: active ? color : '#94a3b8', fontWeight: 700, lineHeight: 1 }}>{value}</span>
      <span style={{ fontSize: 8, color: active ? color : '#94a3b8', marginTop: 2 }}>{label}</span>
    </div>
  );
}

// ─── Pre-Start Modal ──────────────────────────────────────────────────────────
function PreStartModal({ ingredients, onConfirm }: {
  ingredients: { id: string; name: string; quantity: number; unit: string }[];
  onConfirm: () => void;
}) {
  return (
    <div style={{ position: 'absolute', inset: 0, zIndex: 100, background: 'rgba(15,23,42,0.7)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ background: 'rgba(248,250,255,0.98)', borderRadius: 16, padding: '20px 22px', border: '1px solid rgba(148,163,184,0.25)', boxShadow: '0 20px 60px rgba(0,0,0,0.25)', maxWidth: 320, width: '90%' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 14 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: 'rgba(37,99,235,0.1)', border: '1px solid rgba(37,99,235,0.25)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <UtensilsCrossed size={18} color="#2563eb" />
          </div>
          <div>
            <div style={{ fontSize: 14, color: '#0f172a', fontWeight: 700 }}>Ready to Start?</div>
            <div style={{ fontSize: 10, color: '#64748b' }}>Prepare your ingredients first</div>
          </div>
        </div>
        {ingredients.length > 0 && (
          <>
            <div style={{ fontSize: 10, color: '#64748b', marginBottom: 8, fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.5 }}>Add to pot before starting:</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 5, marginBottom: 16 }}>
              {ingredients.map(ing => (
                <div key={ing.id} style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'rgba(37,99,235,0.05)', border: '1px solid rgba(37,99,235,0.15)', borderRadius: 8, padding: '6px 10px' }}>
                  <div style={{ width: 6, height: 6, borderRadius: '50%', background: '#2563eb', flexShrink: 0 }} />
                  <span style={{ fontSize: 12, color: '#0f172a', fontWeight: 500 }}>{ing.quantity}{ing.unit} {ing.name}</span>
                </div>
              ))}
            </div>
          </>
        )}
        {ingredients.length === 0 && (
          <div style={{ fontSize: 12, color: '#64748b', marginBottom: 16, padding: '10px 0' }}>Your device is ready. Tap below to begin cooking.</div>
        )}
        <button onClick={onConfirm} style={{ width: '100%', height: 40, borderRadius: 10, cursor: 'pointer', background: 'linear-gradient(135deg,#1d4ed8,#3b82f6)', border: 'none', color: '#fff', fontSize: 13, fontWeight: 700 }}>
          {ingredients.length > 0 ? '✓ Ingredients Added — Start Cooking' : '▶ Start Cooking'}
        </button>
      </div>
    </div>
  );
}

// ─── Hold / Ingredient Modal ───────────────────────────────────────────────────
function HoldModal({ type, ingredients, holdElapsedSec, holdMinuteIdx, onConfirm }: {
  type: 'ingredients' | 'close-lid';
  ingredients: { id: string; name: string; quantity: number; unit: string }[];
  holdElapsedSec: number;
  holdMinuteIdx: number;
  onConfirm: () => void;
}) {
  const timeLeft = Math.max(0, MAX_HOLD_SEC - holdElapsedSec);
  const urgency = holdElapsedSec >= 150;

  return (
    <div style={{ position: 'absolute', inset: 0, zIndex: 100, background: 'rgba(15,23,42,0.75)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ background: 'rgba(248,250,255,0.98)', borderRadius: 16, padding: '18px 20px', border: `1px solid ${urgency ? 'rgba(239,68,68,0.35)' : 'rgba(217,119,6,0.35)'}`, boxShadow: `0 20px 60px rgba(0,0,0,0.3)`, maxWidth: 320, width: '90%' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: urgency ? 'rgba(239,68,68,0.1)' : 'rgba(217,119,6,0.1)', border: `1px solid ${urgency ? 'rgba(239,68,68,0.3)' : 'rgba(217,119,6,0.3)'}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {type === 'ingredients' ? <UtensilsCrossed size={18} color={urgency ? '#dc2626' : '#d97706'} /> : <AlertTriangle size={18} color={urgency ? '#dc2626' : '#d97706'} />}
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 14, color: '#0f172a', fontWeight: 700 }}>
              {type === 'ingredients' ? `Add Ingredients — Min ${holdMinuteIdx + 1}` : 'Close the Lid'}
            </div>
            <div style={{ fontSize: 10, color: '#64748b' }}>Recipe paused · hold heat at 30%</div>
          </div>
        </div>

        <div style={{ marginBottom: 12 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
            <span style={{ fontSize: 10, color: '#64748b' }}>⏸ Hold duration</span>
            <span style={{ fontSize: 12, fontWeight: 700, color: urgency ? '#dc2626' : '#d97706' }}>{holdElapsedSec}s / {MAX_HOLD_SEC}s</span>
          </div>
          <div style={{ height: 6, borderRadius: 3, background: 'rgba(148,163,184,0.2)', overflow: 'hidden' }}>
            <div style={{ height: '100%', borderRadius: 3, width: `${(holdElapsedSec / MAX_HOLD_SEC) * 100}%`, background: urgency ? 'linear-gradient(90deg,#ef4444,#dc2626)' : 'linear-gradient(90deg,#f59e0b,#d97706)', transition: 'width 0.5s linear, background 0.3s' }} />
          </div>
          {urgency && <div style={{ fontSize: 9, color: '#dc2626', marginTop: 3, fontWeight: 600 }}>⚠ Auto-abort in {timeLeft}s if not confirmed</div>}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 5, background: 'rgba(249,115,22,0.07)', border: '1px solid rgba(249,115,22,0.2)', borderRadius: 8, padding: '5px 9px', marginBottom: 12 }}>
          <span style={{ fontSize: 9, color: '#ea580c', fontWeight: 600 }}>🔥 Induction at 30% — keeping food warm</span>
        </div>

        {type === 'ingredients' && (
          <>
            {ingredients.length > 0 ? (
              <>
                <div style={{ fontSize: 10, color: '#64748b', marginBottom: 6, fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.4 }}>Add now:</div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 14 }}>
                  {ingredients.map(ing => (
                    <div key={ing.id} style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'rgba(217,119,6,0.05)', border: '1px solid rgba(217,119,6,0.18)', borderRadius: 7, padding: '6px 10px' }}>
                      <div style={{ width: 5, height: 5, borderRadius: '50%', background: '#d97706', flexShrink: 0 }} />
                      <span style={{ fontSize: 12, color: '#0f172a', fontWeight: 500 }}>{ing.quantity}{ing.unit} {ing.name}</span>
                    </div>
                  ))}
                </div>
              </>
            ) : (
              <div style={{ fontSize: 11, color: '#64748b', marginBottom: 14, padding: '4px 0' }}>No additional ingredients for this step. Tap confirm when ready.</div>
            )}
          </>
        )}

        {type === 'close-lid' && (
          <div style={{ fontSize: 12, color: '#475569', marginBottom: 14, padding: '4px 0', lineHeight: 1.6 }}>
            The next step requires the <strong>lid to be closed</strong>. Please close the lid on your device and tap confirm.
          </div>
        )}

        <button onClick={onConfirm} style={{ width: '100%', height: 40, borderRadius: 10, cursor: 'pointer', background: urgency ? 'linear-gradient(135deg,#b91c1c,#ef4444)' : 'linear-gradient(135deg,#b45309,#f59e0b)', border: 'none', color: '#fff', fontSize: 13, fontWeight: 700 }}>
          {type === 'ingredients' ? '✓ Ingredients Added — Resume' : '✓ Lid Closed — Resume'}
        </button>
      </div>
    </div>
  );
}

// ─── Abort Overlay ─────────────────────────────────────────────────────────────
function AbortOverlay({ onBack }: { onBack: () => void }) {
  return (
    <div style={{ position: 'absolute', inset: 0, zIndex: 200, background: 'rgba(15,23,42,0.88)', backdropFilter: 'blur(12px)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ background: 'rgba(254,242,242,0.98)', borderRadius: 16, padding: '24px', border: '1px solid rgba(239,68,68,0.4)', boxShadow: '0 20px 60px rgba(239,68,68,0.2)', maxWidth: 300, width: '90%', textAlign: 'center' }}>
        <div style={{ width: 48, height: 48, borderRadius: 14, background: 'rgba(239,68,68,0.12)', border: '1px solid rgba(239,68,68,0.3)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 14px' }}>
          <AlertTriangle size={24} color="#dc2626" />
        </div>
        <div style={{ fontSize: 16, color: '#991b1b', fontWeight: 800, marginBottom: 6 }}>Recipe Aborted</div>
        <div style={{ fontSize: 13, color: '#b91c1c', fontWeight: 600, marginBottom: 6 }}>Hold Time Exceeded</div>
        <div style={{ fontSize: 11, color: '#64748b', marginBottom: 20, lineHeight: 1.6 }}>
          The recipe was automatically stopped because the hold exceeded 3 minutes. Please restart from the editor.
        </div>
        <button onClick={onBack} style={{ width: '100%', height: 38, borderRadius: 10, cursor: 'pointer', background: 'rgba(239,68,68,0.12)', border: '1px solid rgba(239,68,68,0.35)', color: '#dc2626', fontSize: 12, fontWeight: 700 }}>
          ← Back to Editor
        </button>
      </div>
    </div>
  );
}

// ─── Main Screen ──────────────────────────────────────────────────────────────
export function LiveTimelineScreen() {
  const navigate = useNavigate();
  const { proRecipe, setCompletionData } = useApp();

  const recipe = proRecipe;

  const [elapsed, setElapsed] = useState(0);
  const [isPaused, setIsPaused] = useState(false);
  const [confirmEnd, setConfirmEnd] = useState(false);
  const [isAborted, setIsAborted] = useState(false);
  const [showPreStartModal, setShowPreStartModal] = useState(true);

  const [isHolding, setIsHolding] = useState(false);
  const [holdElapsedSec, setHoldElapsedSec] = useState(0);
  const [holdMinuteIdx, setHoldMinuteIdx] = useState(-1);
  const [holdType, setHoldType] = useState<'ingredients' | 'close-lid'>('ingredients');
  const [completedHolds, setCompletedHolds] = useState<HoldRecord[]>([]);

  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const holdIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const confirmTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const hasCompletedRef = useRef(false);
  const processedHoldKeysRef = useRef(new Set<string>());
  const playheadXRef = useRef(0);

  useEffect(() => {
    if (!recipe) navigate('/pro-editor');
  }, [recipe]);

  const isEffectivelyPaused = isPaused || isHolding || showPreStartModal || isAborted;

  useEffect(() => {
    if (!recipe) return;
    if (isEffectivelyPaused) { if (intervalRef.current) clearInterval(intervalRef.current); return; }
    const r = recipe;
    intervalRef.current = setInterval(() => {
      setElapsed(p => {
        const total = getProRecipeTotalSeconds(r);
        if (p >= total) { clearInterval(intervalRef.current!); return p; }
        return p + 1;
      });
    }, 1000);
    return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
  }, [recipe, isEffectivelyPaused]);

  useEffect(() => {
    if (!isHolding) { if (holdIntervalRef.current) clearInterval(holdIntervalRef.current); return; }
    holdIntervalRef.current = setInterval(() => {
      setHoldElapsedSec(s => Math.min(s + 1, MAX_HOLD_SEC));
    }, 1000);
    return () => { if (holdIntervalRef.current) clearInterval(holdIntervalRef.current); };
  }, [isHolding]);

  useEffect(() => {
    if (holdElapsedSec >= MAX_HOLD_SEC && isHolding) {
      if (holdIntervalRef.current) clearInterval(holdIntervalRef.current);
      setCompletedHolds(prev => [...prev, { minuteIdx: holdMinuteIdx, durationSec: MAX_HOLD_SEC, recipeElapsed: elapsed, type: holdType, aborted: true }]);
      setIsHolding(false);
      setIsAborted(true);
    }
  }, [holdElapsedSec]);

  useEffect(() => {
    if (!recipe || hasCompletedRef.current) return;
    const total = getProRecipeTotalSeconds(recipe);
    if (elapsed >= total) {
      hasCompletedRef.current = true;
      if (intervalRef.current) clearInterval(intervalRef.current);
      setCompletionData({ elapsedSec: elapsed, totalPlannedSec: total, wasManual: false, completedAt: Date.now() });
      navigate('/pro-editor/completed');
    }
  }, [elapsed, recipe]);

  const currentMinuteIdx = recipe ? Math.min(recipe.minutes.length - 1, Math.floor(elapsed / 60)) : 0;

  useEffect(() => {
    if (!recipe || showPreStartModal || isAborted || isHolding) return;
    const currentMinute = recipe.minutes[currentMinuteIdx];

    if (currentMinute?.lidOpen && currentMinuteIdx >= 1) {
      const key = `ing-${currentMinuteIdx}`;
      if (!processedHoldKeysRef.current.has(key)) {
        processedHoldKeysRef.current.add(key);
        setIsHolding(true); setHoldElapsedSec(0);
        setHoldMinuteIdx(currentMinuteIdx); setHoldType('ingredients');
        return;
      }
    }
    if (currentMinuteIdx >= 1) {
      const prevMinute = recipe.minutes[currentMinuteIdx - 1];
      if (prevMinute?.lidOpen && !currentMinute?.lidOpen) {
        const key = `close-${currentMinuteIdx}`;
        if (!processedHoldKeysRef.current.has(key)) {
          processedHoldKeysRef.current.add(key);
          setIsHolding(true); setHoldElapsedSec(0);
          setHoldMinuteIdx(currentMinuteIdx - 1); setHoldType('close-lid');
        }
      }
    }
  }, [currentMinuteIdx]);

  const handleEndRecipe = () => {
    if (!recipe || hasCompletedRef.current) return;
    hasCompletedRef.current = true;
    if (intervalRef.current) clearInterval(intervalRef.current);
    if (confirmTimeoutRef.current) clearTimeout(confirmTimeoutRef.current);
    const total = getProRecipeTotalSeconds(recipe);
    setCompletionData({ elapsedSec: elapsed, totalPlannedSec: total, wasManual: true, completedAt: Date.now() });
    navigate('/pro-editor/completed');
  };

  const requestEndRecipe = () => {
    setConfirmEnd(true);
    if (confirmTimeoutRef.current) clearTimeout(confirmTimeoutRef.current);
    confirmTimeoutRef.current = setTimeout(() => setConfirmEnd(false), 4000);
  };

  const cancelEndRecipe = () => {
    setConfirmEnd(false);
    if (confirmTimeoutRef.current) clearTimeout(confirmTimeoutRef.current);
  };

  const confirmHold = () => {
    if (!isHolding) return;
    setCompletedHolds(prev => [...prev, { minuteIdx: holdMinuteIdx, durationSec: holdElapsedSec, recipeElapsed: elapsed, type: holdType }]);
    setIsHolding(false);
    setHoldElapsedSec(0);
    setHoldMinuteIdx(-1);
  };

  useEffect(() => {
    if (!scrollRef.current) return;
    const containerWidth = scrollRef.current.clientWidth;
    const scrollLeft = Math.max(0, playheadXRef.current - containerWidth * 0.35);
    scrollRef.current.scrollTo({ left: scrollLeft, behavior: 'smooth' });
  }, [Math.floor(elapsed / 15), isHolding]);

  if (!recipe) return null;

  const total = getProRecipeTotalSeconds(recipe);
  const isFinished = elapsed >= total;
  const remaining = Math.max(0, total - elapsed);
  const progress = total > 0 ? elapsed / total : 0;

  const secondsIntoMinute = elapsed % 60;
  const currentSubIdx = Math.floor(secondsIntoMinute / 15);
  const subProgress = (secondsIntoMinute % 15) / 15;
  const currentMinute = recipe.minutes[currentMinuteIdx];

  const lidIsOpen = currentMinute?.lidOpen && secondsIntoMinute < currentMinute.lidOpenDuration;
  const waterIsActive = currentMinute?.waterBlocks[currentSubIdx] ?? false;
  const currentInd = isHolding ? 30 : (currentMinute?.subBlocks[currentSubIdx]?.inductionPower ?? 0);
  const currentMwActive = currentMinute?.subBlocks[currentSubIdx]?.microwaveActive ?? false;
  const effectiveMw = lidIsOpen ? false : currentMwActive;
  const currentStir = isHolding ? false : (currentMinute?.subBlocks[currentSubIdx]?.stirrerActive ?? false);
  const currentStirSpeed: StirrerSpeedType = currentMinute?.subBlocks[currentSubIdx]?.stirrerSpeed ?? 'medium';

  // ── Build column layout ──
  const columns = buildColumns(
    recipe.minutes.length,
    completedHolds,
    isHolding && holdMinuteIdx >= 0
      ? { minuteIdx: holdMinuteIdx, type: holdType, durationSec: holdElapsedSec }
      : null,
  );

  const timelineContentW = columns.reduce((sum, col) => sum + (col.kind === 'minute' ? CELL_W : holdColW(col.durationSec)), 0) + 48;

  const playheadX = getPlayheadX(elapsed, columns, isHolding, holdMinuteIdx, holdType);
  playheadXRef.current = playheadX;

  // ── Past/current logic accounting for hold state ──
  function isMinutePast(idx: number): boolean {
    if (isHolding) {
      if (holdType === 'ingredients') return idx < holdMinuteIdx;
      return idx <= holdMinuteIdx;
    }
    return idx < currentMinuteIdx;
  }
  function isMinuteCurrent(idx: number): boolean {
    return !isHolding && idx === currentMinuteIdx;
  }

  const firstMinuteIngredients = recipe.minutes[0]?.ingredients ?? [];
  const holdModalIngredients = (isHolding && holdMinuteIdx >= 0) ? (recipe.minutes[holdMinuteIdx]?.ingredients ?? []) : [];
  const actualHoldTime = completedHolds.reduce((sum, h) => sum + h.durationSec, 0);

  return (
    <div style={{
      display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden',
      paddingLeft: DI_OFFSET,
      background: 'linear-gradient(160deg,#f0f4ff 0%,#f7f9ff 60%,#fafbff 100%)',
      position: 'relative',
    }}>

      {/* ── TOP BAR ─────────────────────────────────────────────────────────── */}
      <div style={{ height: TOP_BAR_H, flexShrink: 0, background: 'rgba(248,250,255,0.97)', backdropFilter: 'blur(20px)', borderBottom: '1px solid rgba(148,163,184,0.2)', display: 'flex', flexDirection: 'column', justifyContent: 'space-between', padding: '6px 10px 6px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <button onClick={() => navigate('/pro-editor')} style={{ display: 'flex', alignItems: 'center', gap: 3, background: 'rgba(255,255,255,0.8)', border: '1px solid rgba(148,163,184,0.25)', borderRadius: 8, padding: '4px 8px', cursor: 'pointer', color: '#64748b', fontSize: 11, flexShrink: 0 }}>
            <ChevronLeft size={12} /> Editor
          </button>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 13, color: '#0f172a', fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{recipe.name}</div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexShrink: 0 }}>
            <div style={{ padding: '3px 9px', borderRadius: 7, background: isAborted ? 'rgba(239,68,68,0.1)' : isFinished ? 'rgba(74,222,128,0.1)' : isHolding ? 'rgba(217,119,6,0.1)' : isPaused ? 'rgba(251,191,36,0.1)' : 'rgba(239,68,68,0.08)', border: `1px solid ${isAborted ? 'rgba(239,68,68,0.4)' : isFinished ? 'rgba(74,222,128,0.38)' : isHolding ? 'rgba(217,119,6,0.4)' : isPaused ? 'rgba(251,191,36,0.32)' : 'rgba(239,68,68,0.3)'}` }}>
              <span style={{ fontSize: 11, fontWeight: 700, color: isAborted ? '#dc2626' : isFinished ? '#16a34a' : isHolding ? '#d97706' : isPaused ? '#d97706' : '#dc2626' }}>
                {isAborted ? '✗ Aborted' : isFinished ? '✓ Done' : isHolding ? '⏸ Hold' : isPaused ? '⏸ Paused' : '● Live'}
              </span>
            </div>
            <div style={{ background: 'rgba(255,255,255,0.85)', border: '1px solid rgba(148,163,184,0.25)', borderRadius: 8, padding: '3px 10px', textAlign: 'center' }}>
              <div style={{ fontSize: 18, color: isFinished ? '#16a34a' : '#0f172a', fontWeight: 700, letterSpacing: -0.5, fontVariantNumeric: 'tabular-nums' }}>{formatMSS(remaining)}</div>
              <div style={{ fontSize: 8, color: '#94a3b8' }}>remaining</div>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 5, alignItems: 'center' }}>
          <StatusChip label="IH%" value={currentInd > 0 ? `${currentInd}${isHolding ? '♨' : ''}` : '—'} color={isHolding ? '#d97706' : '#f97316'} active={currentInd > 0} />
          <StatusChip label="MW" value={effectiveMw ? 'ON' : lidIsOpen && currentMwActive ? '⚠' : 'OFF'} color="#ec4899" active={!!effectiveMw && !isHolding} />
          <StatusChip label="Stir" value={currentStir ? LIVE_STIR_LABEL[currentStirSpeed] : 'OFF'} color="#14b8a6" active={currentStir} />
          <StatusChip label="Lid" value={lidIsOpen ? 'OPEN' : '—'} color="#16a34a" active={!!lidIsOpen} />
          {waterIsActive && !isHolding && <StatusChip label="💧 Water" value="150ml" color="#0891b2" active />}
          {isHolding && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '4px 8px', borderRadius: 7, background: 'rgba(217,119,6,0.08)', border: '1px solid rgba(217,119,6,0.28)' }}>
              <span style={{ fontSize: 9, color: '#d97706', fontWeight: 600 }}>⏸ Hold {holdElapsedSec}s</span>
            </div>
          )}
          {lidIsOpen && currentMwActive && !isHolding && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '4px 8px', borderRadius: 7, background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.28)' }}>
              <AlertTriangle size={10} color="#dc2626" />
              <span style={{ fontSize: 9, color: '#dc2626', fontWeight: 600 }}>MW forced OFF</span>
            </div>
          )}
          <div style={{ flex: 1 }} />
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexShrink: 0 }}>
            <span style={{ fontSize: 9, color: '#94a3b8' }}>M{currentMinuteIdx + 1}/{recipe.minutes.length}</span>
            <div style={{ width: 90, height: 5, borderRadius: 3, background: 'rgba(148,163,184,0.2)', overflow: 'hidden' }}>
              <div style={{ width: `${progress * 100}%`, height: '100%', background: isFinished ? '#4ade80' : 'linear-gradient(90deg,#1d4ed8,#60a5fa)', borderRadius: 3, transition: 'width 0.5s linear' }} />
            </div>
            <span style={{ fontSize: 9, color: '#94a3b8' }}>{formatMSS(elapsed)}</span>
          </div>
        </div>
      </div>

      {/* ── MAIN: LABELS + TIMELINE ─────────────────────────────────────────── */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden', minHeight: 0 }}>

        {/* Labels */}
        <div style={{ width: LABEL_W, flexShrink: 0, background: 'rgba(248,250,255,0.97)', borderRight: '1px solid rgba(148,163,184,0.18)', display: 'flex', flexDirection: 'column' }}>
          <div style={{ height: HEADER_H, borderBottom: '1px solid rgba(148,163,184,0.15)' }} />
          {ROW_META.map(r => (
            <div key={r.key} style={{ height: ROW_H, display: 'flex', alignItems: 'center', justifyContent: 'center', borderBottom: '1px solid rgba(148,163,184,0.12)' }}>
              <span style={{ width: 22, height: 22, borderRadius: 6, background: `${r.color}15`, border: `1px solid ${r.color}40`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, color: r.color, fontWeight: 800 }}>{r.letter}</span>
            </div>
          ))}
        </div>

        {/* Timeline scroll */}
        <div ref={scrollRef} style={{ flex: 1, overflowX: 'auto', overflowY: 'hidden', scrollbarWidth: 'thin', scrollbarColor: 'rgba(148,163,184,0.3) transparent', position: 'relative', background: '#f5f7ff' }}>
          <div style={{ display: 'flex', flexDirection: 'column', height: '100%', minWidth: timelineContentW, position: 'relative' }}>

            {/* ── Column header row ── */}
            <div style={{ display: 'flex', height: HEADER_H, flexShrink: 0, background: 'rgba(248,250,255,0.98)', borderBottom: '1px solid rgba(148,163,184,0.18)', position: 'sticky', top: 0, zIndex: 5 }}>
              {columns.map((col, ci) => {
                if (col.kind === 'minute') {
                  const idx = col.idx;
                  const isCur = isMinuteCurrent(idx);
                  const isPast = isMinutePast(idx);
                  return (
                    <div key={`mh-${idx}`} style={{ width: CELL_W, flexShrink: 0, borderRight: '1px solid rgba(148,163,184,0.12)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 5px', background: isCur ? 'rgba(96,165,250,0.07)' : isPast ? 'rgba(148,163,184,0.04)' : 'transparent' }}>
                      <span style={{ fontSize: 9, color: isPast ? '#cbd5e1' : isCur ? '#2563eb' : '#94a3b8', fontWeight: isCur ? 700 : 400 }}>M{idx + 1}</span>
                      {isCur && !isFinished && <div style={{ width: 5, height: 5, borderRadius: '50%', background: '#60a5fa', opacity: 0.9 }} />}
                      {isPast && <CheckCircle2 size={8} color="rgba(22,163,74,0.5)" />}
                    </div>
                  );
                }
                return <HoldHeaderCell key={`hh-${ci}`} col={col} />;
              })}
              <div style={{ width: 48, flexShrink: 0 }} />
            </div>

            {/* ── Row cells ── */}
            {ROW_META.map(rowMeta => (
              <div key={rowMeta.key} style={{ display: 'flex', height: ROW_H, borderBottom: '1px solid rgba(148,163,184,0.1)' }}>
                {columns.map((col, ci) => {
                  if (col.kind === 'minute') {
                    const idx = col.idx;
                    const isPast = isMinutePast(idx);
                    const isCur = isMinuteCurrent(idx);
                    return (
                      <LiveCell
                        key={`cell-${rowMeta.key}-${idx}`}
                        minute={recipe.minutes[idx]}
                        row={rowMeta.key}
                        isPast={isPast}
                        isCurrent={isCur}
                        subProgress={isCur ? subProgress : undefined}
                        currentSubIdx={isCur ? currentSubIdx : undefined}
                      />
                    );
                  }
                  return <HoldRowCell key={`hr-${rowMeta.key}-${ci}`} col={col} />;
                })}
                <div style={{ width: 48, flexShrink: 0 }} />
              </div>
            ))}

            {/* ── Abort marker ── */}
            {isAborted && (
              <div style={{ position: 'absolute', top: HEADER_H, left: playheadX, bottom: 0, width: 3, background: 'rgba(220,38,38,0.7)', zIndex: 12, pointerEvents: 'none' }}>
                <div style={{ position: 'absolute', top: 4, left: -18, background: '#dc2626', borderRadius: 4, padding: '1px 5px', whiteSpace: 'nowrap' }}>
                  <span style={{ fontSize: 7, color: '#fff', fontWeight: 700 }}>ABORT</span>
                </div>
              </div>
            )}

            {/* ── Playhead ── */}
            {!isAborted && (
              <div style={{ position: 'absolute', top: HEADER_H, bottom: 0, left: playheadX, width: 2, background: isHolding ? 'rgba(217,119,6,0.4)' : 'linear-gradient(180deg,#60a5fa,rgba(96,165,250,0.2))', zIndex: 10, pointerEvents: 'none' }}>
                <div style={{ position: 'absolute', top: -7, left: -6, width: 14, height: 14, background: isHolding ? '#d97706' : '#60a5fa', borderRadius: '50%', boxShadow: `0 0 12px ${isHolding ? 'rgba(217,119,6,0.8)' : 'rgba(96,165,250,0.8)'}`, border: `2px solid ${isHolding ? 'rgba(217,119,6,0.3)' : 'rgba(96,165,250,0.3)'}` }} />
                <div style={{ position: 'absolute', top: 12, left: -18, background: isHolding ? '#d97706' : '#60a5fa', borderRadius: 4, padding: '1px 5px', whiteSpace: 'nowrap' }}>
                  <span style={{ fontSize: 8, color: '#fff', fontWeight: 700 }}>{formatMSS(elapsed)}</span>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* ── BOTTOM CONTROLS ─────────────────────────────────────────────────── */}
      <div style={{ height: BOT_BAR_H, flexShrink: 0, background: 'rgba(248,250,255,0.97)', backdropFilter: 'blur(20px)', borderTop: '1px solid rgba(148,163,184,0.2)', display: 'flex', alignItems: 'center', padding: '0 10px', gap: 8 }}>
        {isFinished ? (
          <>
            <div style={{ display: 'flex', gap: 6, flex: 1 }}>
              {[
                { label: 'Total Cook', value: formatMSS(total), color: '#2563eb' },
                { label: 'Hold Time', value: `${actualHoldTime}s`, color: '#94a3b8' },
                { label: 'Active Cook', value: formatMSS(Math.max(0, total - actualHoldTime)), color: '#16a34a' },
              ].map(s => (
                <div key={s.label} style={{ background: 'rgba(255,255,255,0.85)', border: '1px solid rgba(148,163,184,0.22)', borderRadius: 8, padding: '5px 10px', display: 'flex', gap: 6, alignItems: 'center' }}>
                  <span style={{ fontSize: 9, color: '#94a3b8' }}>{s.label}</span>
                  <span style={{ fontSize: 13, color: s.color, fontWeight: 700, letterSpacing: -0.3 }}>{s.value}</span>
                </div>
              ))}
            </div>
            <button onClick={() => navigate('/export')} style={{ height: 32, padding: '0 14px', borderRadius: 9, cursor: 'pointer', background: 'rgba(37,99,235,0.1)', border: '1px solid rgba(37,99,235,0.3)', color: '#2563eb', fontSize: 12, fontWeight: 700 }}>Recipe Sheet</button>
            <button onClick={() => navigate('/')} style={{ height: 32, padding: '0 16px', borderRadius: 9, cursor: 'pointer', background: 'linear-gradient(135deg,#166534,#4ade80)', border: 'none', color: '#fff', fontSize: 12, fontWeight: 700 }}>Done ✓</button>
          </>
        ) : (
          <>
            <button onClick={() => scrollRef.current?.scrollBy({ left: -CELL_W * 2, behavior: 'smooth' })} style={{ width: 32, height: 32, borderRadius: 8, background: 'rgba(255,255,255,0.8)', border: '1px solid rgba(148,163,184,0.25)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <ChevronLeft size={14} color="#64748b" />
            </button>
            <button onClick={() => scrollRef.current?.scrollBy({ left: CELL_W * 2, behavior: 'smooth' })} style={{ width: 32, height: 32, borderRadius: 8, background: 'rgba(255,255,255,0.8)', border: '1px solid rgba(148,163,184,0.25)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <ChevronRight size={14} color="#64748b" />
            </button>
            <div style={{ flex: 1 }} />
            {isHolding ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '4px 12px', borderRadius: 9, background: 'rgba(217,119,6,0.08)', border: '1px solid rgba(217,119,6,0.3)' }}>
                <span style={{ fontSize: 11, color: '#d97706', fontWeight: 600 }}>⏸ Waiting for confirmation…</span>
              </div>
            ) : (
              <button onClick={() => setIsPaused(p => !p)} style={{ height: 34, padding: '0 16px', borderRadius: 9, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6, background: isPaused ? 'rgba(37,99,235,0.1)' : 'rgba(217,119,6,0.08)', border: `1px solid ${isPaused ? 'rgba(37,99,235,0.35)' : 'rgba(217,119,6,0.3)'}` }}>
                {isPaused ? <><Play size={14} color="#2563eb" /><span style={{ fontSize: 12, color: '#2563eb', fontWeight: 700 }}>Resume</span></> : <><Pause size={14} color="#d97706" /><span style={{ fontSize: 12, color: '#d97706', fontWeight: 700 }}>Pause</span></>}
              </button>
            )}
            {confirmEnd ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '4px 10px', borderRadius: 10, background: 'rgba(248,113,113,0.08)', border: '1px solid rgba(248,113,113,0.3)' }}>
                <span style={{ fontSize: 11, color: '#f87171', fontWeight: 600, whiteSpace: 'nowrap' }}>End early?</span>
                <button onClick={handleEndRecipe} style={{ height: 26, padding: '0 10px', borderRadius: 7, cursor: 'pointer', background: 'rgba(248,113,113,0.18)', border: '1px solid rgba(248,113,113,0.45)', color: '#f87171', fontSize: 11, fontWeight: 700, whiteSpace: 'nowrap' }}>Proceed</button>
                <button onClick={cancelEndRecipe} style={{ width: 26, height: 26, borderRadius: 7, cursor: 'pointer', background: 'rgba(148,163,184,0.1)', border: '1px solid rgba(148,163,184,0.25)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <XIcon size={12} color="#94a3b8" />
                </button>
              </div>
            ) : (
              <button onClick={requestEndRecipe} style={{ height: 34, padding: '0 14px', borderRadius: 9, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6, background: 'rgba(74,222,128,0.08)', border: '1px solid rgba(74,222,128,0.28)' }}>
                <FlagTriangleRight size={14} color="#16a34a" />
                <span style={{ fontSize: 12, color: '#16a34a', fontWeight: 700 }}>End Recipe</span>
              </button>
            )}
            <button onClick={() => { if (intervalRef.current) clearInterval(intervalRef.current); navigate('/pro-editor'); }} style={{ width: 34, height: 34, borderRadius: 9, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(248,113,113,0.08)', border: '1px solid rgba(248,113,113,0.3)' }}>
              <StopCircle size={18} color="#f87171" />
            </button>
          </>
        )}
      </div>

      {/* ── MODALS ──────────────────────────────────────────────────────────── */}
      {showPreStartModal && (
        <PreStartModal ingredients={firstMinuteIngredients} onConfirm={() => setShowPreStartModal(false)} />
      )}
      {isHolding && !isAborted && (
        <HoldModal type={holdType} ingredients={holdModalIngredients} holdElapsedSec={holdElapsedSec} holdMinuteIdx={holdMinuteIdx} onConfirm={confirmHold} />
      )}
      {isAborted && <AbortOverlay onBack={() => navigate('/pro-editor')} />}
    </div>
  );
}
