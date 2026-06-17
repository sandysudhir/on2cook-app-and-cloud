import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router';
import { CheckCircle2 } from 'lucide-react';
import { useApp } from '../../AppContext';

// ─── Layout constants (matches landscape sibling screens) ─────────────────────
const DI_OFFSET = 50;

function formatMSS(sec: number): string {
  const s = Math.max(0, Math.round(sec));
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${m.toString().padStart(2, '0')}:${r.toString().padStart(2, '0')}`;
}

// ─── Pulsing ring animation ────────────────────────────────────────────────────
function PulseRing({ color }: { color: string }) {
  const [scale, setScale] = useState(1);
  useEffect(() => {
    let growing = true;
    const id = setInterval(() => {
      setScale(s => {
        if (s >= 1.12) growing = false;
        if (s <= 1.0) growing = true;
        return growing ? s + 0.006 : s - 0.006;
      });
    }, 30);
    return () => clearInterval(id);
  }, []);
  return (
    <div style={{
      position: 'absolute', inset: 0, borderRadius: '50%',
      border: `1.5px solid ${color}`,
      transform: `scale(${scale})`,
      opacity: 0.35,
      transition: 'transform 0.03s linear',
      pointerEvents: 'none',
    }} />
  );
}

// ─── Main Screen ──────────────────────────────────────────────────────────────
export function RecipeCompletedScreen() {
  const navigate = useNavigate();
  const { proRecipe, completionData } = useApp();

  const [postElapsed, setPostElapsed] = useState(0);

  // Guard: if no completion data, go back to live
  useEffect(() => {
    if (!completionData) {
      navigate('/pro-editor/live', { replace: true });
    }
  }, []);

  // Count up post-completion timer
  useEffect(() => {
    const id = setInterval(() => setPostElapsed(p => p + 1), 1000);
    return () => clearInterval(id);
  }, []);

  if (!completionData || !proRecipe) return null;

  const { elapsedSec, totalPlannedSec, wasManual } = completionData;
  const completionPct = Math.round((elapsedSec / totalPlannedSec) * 100);
  const accentColor = wasManual ? '#fbbf24' : '#4ade80';
  const accentColorDim = wasManual ? 'rgba(251,191,36,0.15)' : 'rgba(74,222,128,0.12)';
  const accentBorder = wasManual ? 'rgba(251,191,36,0.35)' : 'rgba(74,222,128,0.3)';

  return (
    <div
      onClick={() => navigate('/export')}
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        paddingLeft: DI_OFFSET,
        cursor: 'pointer',
        position: 'relative',
        overflow: 'hidden',
        userSelect: 'none',
        background: 'linear-gradient(160deg, #f0f4ff 0%, #f7f9ff 60%, #fafbff 100%)',
      }}
    >
      {/* ── Ambient radial glow ─────────────────────────────────────────────── */}
      <div style={{
        position: 'absolute', top: '50%', left: '55%',
        transform: 'translate(-50%, -50%)',
        width: 380, height: 380, borderRadius: '50%',
        background: `radial-gradient(circle, ${wasManual ? 'rgba(217,119,6,0.06)' : 'rgba(22,163,74,0.06)'} 0%, transparent 68%)`,
        pointerEvents: 'none',
      }} />

      {/* ── Status badge — top-right ─────────────────────────────────────────── */}
      <div style={{
        position: 'absolute', top: 14, right: 14,
        background: accentColorDim,
        border: `1px solid ${accentBorder}`,
        borderRadius: 8, padding: '4px 10px',
        display: 'flex', alignItems: 'center', gap: 5,
        zIndex: 2,
      }}>
        <div style={{ width: 6, height: 6, borderRadius: '50%', background: accentColor }} />
        <span style={{ fontSize: 10, color: accentColor, fontWeight: 700, letterSpacing: 0.3 }}>
          {wasManual ? 'Manual End' : 'Auto Complete'}
        </span>
        {wasManual && totalPlannedSec > elapsedSec && (
          <span style={{ fontSize: 9, color: '#94a3b8', marginLeft: 2 }}>
            · {completionPct}% done
          </span>
        )}
      </div>

      {/* ── Recipe name — top-left ───────────────────────────────────────────── */}
      <div style={{
        position: 'absolute', top: 14, left: DI_OFFSET + 14,
        zIndex: 2,
      }}>
        <span style={{ fontSize: 10, color: '#64748b', fontWeight: 500 }}>
          {proRecipe.name}
        </span>
      </div>

      {/* ── Center content ────────────────────────────────────────────────────── */}
      <div style={{
        flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center',
        position: 'relative', zIndex: 1,
      }}>
        <div style={{
          display: 'flex', flexDirection: 'column', alignItems: 'center',
          gap: 22,
        }}>

          {/* ─ Check icon with pulse ring ──────────────────────────────────── */}
          <div style={{ position: 'relative', width: 64, height: 64 }}>
            <div style={{
              width: 64, height: 64, borderRadius: '50%',
              background: accentColorDim,
              border: `1px solid ${accentBorder}`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: `0 4px 20px ${wasManual ? 'rgba(217,119,6,0.15)' : 'rgba(22,163,74,0.15)'}`,
            }}>
              <CheckCircle2 size={30} color={accentColor} />
            </div>
            <PulseRing color={accentColor} />
          </div>

          {/* ─ Headline ────────────────────────────────────────────────────── */}
          <div style={{ textAlign: 'center' }}>
            <div style={{
              fontSize: 10, color: accentColor, fontWeight: 700,
              letterSpacing: 2.5, textTransform: 'uppercase', marginBottom: 7,
            }}>
              {wasManual ? 'Recipe Ended Early' : 'Timeline Complete'}
            </div>
            <div style={{
              fontSize: 30, color: '#0f172a', fontWeight: 800,
              letterSpacing: -0.8, lineHeight: 1.1,
            }}>
              {wasManual ? 'Recipe Aborted' : 'Recipe Completed'}
            </div>
          </div>

          {/* ─ Two time cards side-by-side ─────────────────────────────────── */}
          <div style={{ display: 'flex', gap: 12, alignItems: 'stretch' }}>

            {/* Cook time (frozen at completion) */}
            <div style={{
              background: 'rgba(255,255,255,0.85)',
              border: '1px solid rgba(37,99,235,0.2)',
              borderRadius: 14, padding: '14px 22px',
              textAlign: 'center', minWidth: 120,
              display: 'flex', flexDirection: 'column', gap: 6,
              boxShadow: '0 2px 12px rgba(0,0,0,0.06)',
            }}>
              <div style={{
                fontSize: 9, color: '#94a3b8',
                letterSpacing: 1.5, textTransform: 'uppercase',
              }}>
                Time to Completion
              </div>
              <div style={{
                fontSize: 28, color: '#2563eb', fontWeight: 700,
                letterSpacing: -0.5, fontVariantNumeric: 'tabular-nums',
                lineHeight: 1,
              }}>
                {formatMSS(elapsedSec)}
              </div>
              {wasManual && totalPlannedSec > elapsedSec && (
                <div style={{ fontSize: 9, color: '#94a3b8' }}>
                  of {formatMSS(totalPlannedSec)} planned
                </div>
              )}
            </div>

            {/* Separator */}
            <div style={{
              width: 1, background: 'rgba(148,163,184,0.2)',
              alignSelf: 'stretch', margin: '8px 0',
            }} />

            {/* Post-completion elapsed (live, counts up) */}
            <div style={{
              background: 'rgba(255,255,255,0.7)',
              border: '1px solid rgba(148,163,184,0.18)',
              borderRadius: 14, padding: '14px 22px',
              textAlign: 'center', minWidth: 120,
              display: 'flex', flexDirection: 'column', gap: 6,
              boxShadow: '0 1px 6px rgba(0,0,0,0.04)',
            }}>
              <div style={{
                fontSize: 9, color: '#94a3b8',
                letterSpacing: 1.5, textTransform: 'uppercase',
              }}>
                Since Completion
              </div>
              <div style={{
                fontSize: 28, color: '#64748b', fontWeight: 700,
                letterSpacing: -0.5, fontVariantNumeric: 'tabular-nums',
                lineHeight: 1,
              }}>
                +{formatMSS(postElapsed)}
              </div>
              <div style={{ fontSize: 9, color: '#94a3b8' }}>
                still counting
              </div>
            </div>
          </div>

          {/* ─ Tap hint ────────────────────────────────────────────────────── */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginTop: 4 }}>
            <div style={{ width: 20, height: 20, borderRadius: '50%', border: '1px solid rgba(148,163,184,0.35)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <div style={{ width: 7, height: 7, borderRadius: '50%', background: 'rgba(148,163,184,0.4)' }} />
            </div>
            <span style={{ fontSize: 11, color: '#94a3b8', letterSpacing: 0.3 }}>
              Tap anywhere to view Recipe Sheet
            </span>
          </div>

        </div>
      </div>

      {/* ── Subtle bottom progress strip ─────────────────────────────────────── */}
      <div style={{
        flexShrink: 0, height: 3,
        background: 'rgba(148,163,184,0.12)',
      }}>
        <div style={{
          height: '100%',
          width: `${completionPct}%`,
          background: `linear-gradient(90deg, ${accentColor}60, ${accentColor})`,
          borderRadius: '0 2px 2px 0',
          transition: 'width 0.5s ease',
        }} />
      </div>

    </div>
  );
}