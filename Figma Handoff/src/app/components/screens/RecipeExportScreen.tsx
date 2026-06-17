import React, { useState, useRef } from 'react';
import { useNavigate } from 'react-router';
import { ArrowLeft, Clock, Zap, FlagTriangleRight, Camera, ImagePlus, X, Home, BookmarkCheck, ChevronLeft } from 'lucide-react';
import { useApp } from '../../AppContext';
import { ProRecipe } from '../../proTypes';
import { StirrerSpeedType, getMinuteWaterMl, getActiveWaterBlocks, getTotalHoldTime, getProRecipeTotalSeconds } from '../../proTypes';

// ─── Stirrer export helpers ───────────────────────────────────────────────────
const EXP_STIR_COLOR: Record<StirrerSpeedType, string> = {
  'low': '#5eead4', 'medium': '#2dd4bf', 'high': '#14b8a6', 'very-high': '#0d9488',
};
const EXP_STIR_BAR_H: Record<StirrerSpeedType, number> = {
  'low': 8, 'medium': 15, 'high': 22, 'very-high': 30,
};
const EXP_STIR_LABEL: Record<StirrerSpeedType, string> = {
  'low': 'Lo', 'medium': 'Md', 'high': 'Hi', 'very-high': 'VH',
};
// Dominant speed: the most-used active speed in a minute (or null if all OFF)
function dominantStirSpeed(subBlocks: { stirrerActive: boolean; stirrerSpeed: StirrerSpeedType }[]): StirrerSpeedType | null {
  const active = subBlocks.filter(s => s.stirrerActive);
  if (!active.length) return null;
  const counts: Partial<Record<StirrerSpeedType, number>> = {};
  active.forEach(s => { counts[s.stirrerSpeed] = (counts[s.stirrerSpeed] ?? 0) + 1; });
  return (Object.entries(counts).sort((a, b) => (b[1] as number) - (a[1] as number))[0]?.[0] as StirrerSpeedType) ?? 'medium';
}
import foodImage from 'figma:asset/c6f412b1c7acfce4671e2b403b7879b9d8c7d144.png';

function formatMMSS(sec: number): string {
  const s = Math.max(0, sec);
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${m.toString().padStart(2, '0')}:${r.toString().padStart(2, '0')}`;
}

function indColor(p: number): string {
  if (!p) return '#475569';
  if (p <= 40) return '#d97706'; if (p <= 60) return '#f97316'; if (p <= 80) return '#ef4444'; return '#dc2626';
}
// MW is now pure ON/OFF
const EXP_MW_ON_COLOR = '#ec4899';

export function RecipeExportScreen() {
  const navigate = useNavigate();
  const { proRecipe, completionData, saveProRecipeToLibrary } = useApp();

  // ── Dish photo state ───────────────────────────────────────────────────────
  const [dishPhoto, setDishPhoto] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [showSaveDialog, setShowSaveDialog] = useState(false);
  const [saveDialogName, setSaveDialogName] = useState('');
  const cameraInputRef = useRef<HTMLInputElement>(null);
  const galleryInputRef = useRef<HTMLInputElement>(null);

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = ev => setDishPhoto(ev.target?.result as string);
    reader.readAsDataURL(file);
    e.target.value = '';
  }

  function openSaveDialog() {
    setSaveDialogName(proRecipe?.name ?? '');
    setShowSaveDialog(true);
  }

  function confirmSave() {
    if (!saveDialogName.trim() || !proRecipe) return;
    saveProRecipeToLibrary({ ...proRecipe, id: `pro-saved-${Date.now()}`, name: saveDialogName.trim() } as ProRecipe);
    setSaved(true);
    setShowSaveDialog(false);
  }

  const recipe = proRecipe;

  if (!recipe) {
    return (
      <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16, padding: 24 }}>
        <p style={{ color: '#64748b', fontSize: 14 }}>No recipe to display.</p>
        <button onClick={() => navigate('/pro-editor')} style={{ padding: '10px 20px', borderRadius: 10, cursor: 'pointer', background: 'rgba(96,165,250,0.15)', border: '1px solid rgba(96,165,250,0.3)', color: '#60a5fa', fontSize: 14, fontWeight: 600 }}>Open Editor</button>
      </div>
    );
  }

  const totalPlannedSec = getProRecipeTotalSeconds(recipe);
  // Actual elapsed: use completionData if available, else fall back to planned total
  const actualElapsedSec = completionData ? completionData.elapsedSec : totalPlannedSec;
  const wasManual = completionData?.wasManual ?? false;
  const totalHold = getTotalHoldTime(recipe.minutes);
  const actualCookSec = Math.max(0, actualElapsedSec - Math.min(totalHold, actualElapsedSec));

  // How many whole minutes were executed
  const executedMinuteCount = Math.min(recipe.minutes.length, Math.max(1, Math.ceil(actualElapsedSec / 60)));

  // Collect all ingredients across executed minutes only
  const allIngredients = recipe.minutes.slice(0, executedMinuteCount).flatMap(m => m.ingredients);

  // Collect minutes that have any water blocks active (within executed range)
  const waterMinutes = recipe.minutes
    .slice(0, executedMinuteCount)
    .map((m, i) => ({ minuteIdx: i, waterBlocks: m.waterBlocks, activeCount: getActiveWaterBlocks(m.waterBlocks), ml: getMinuteWaterMl(m.waterBlocks) }))
    .filter(w => w.activeCount > 0);
  const totalWaterMl = waterMinutes.reduce((acc, w) => acc + w.ml, 0);

  // Build step list from lid-open events within executed minutes
  const steps = recipe.minutes
    .slice(0, executedMinuteCount)
    .filter(m => m.lidOpen && m.ingredients.length > 0)
    .map((m, stepNum) => ({
      stepNum: stepNum + 1,
      minuteIdx: m.minuteIndex,
      ingredients: m.ingredients,
      lidDuration: m.lidOpenDuration,
      avgInd: Math.round(m.subBlocks.reduce((a, b) => a + b.inductionPower, 0) / 4),
      mwOnCount: m.lidOpen ? 0 : m.subBlocks.filter(s => s.microwaveActive).length,
    }));

  const dishTypeColor = recipe.dishType === 'vegan' ? '#4ade80' : recipe.dishType === 'veg' ? '#86efac' : '#f87171';
  const dishTypeEmoji = recipe.dishType === 'vegan' ? '🌱' : recipe.dishType === 'veg' ? '🥦' : '🍗';

  return (
    <div style={{ minHeight: '100dvh', overflowY: 'auto', padding: '0 0 40px', background: 'linear-gradient(160deg, #f0f4ff 0%, #f7f9ff 60%, #fafbff 100%)' }}>
      {/* Back nav */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 16px', borderBottom: '1px solid rgba(148,163,184,0.18)', background: 'rgba(245,247,255,0.97)', backdropFilter: 'blur(20px)', position: 'sticky', top: 0, zIndex: 10, boxShadow: '0 1px 8px rgba(0,0,0,0.05)' }}>
        <button onClick={() => navigate(-1)} style={{ display: 'flex', alignItems: 'center', gap: 5, background: 'rgba(255,255,255,0.8)', border: '1px solid rgba(148,163,184,0.25)', borderRadius: 8, padding: '5px 10px', cursor: 'pointer', color: '#64748b', fontSize: 12 }}>
          <ArrowLeft size={13} /> Back
        </button>
        <span style={{ fontSize: 14, color: '#0f172a', fontWeight: 700, flex: 1 }}>Recipe Sheet</span>
        <span style={{ fontSize: 10, color: '#94a3b8' }}>On2Cook Pro</span>
      </div>

      <div style={{ maxWidth: 480, margin: '0 auto', padding: '0 16px' }}>

        {/* Hero Image */}
        <div style={{ margin: '20px 0 0', borderRadius: 20, overflow: 'hidden', height: 200, position: 'relative' }}>
          <img src={foodImage} alt={recipe.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, rgba(0,0,0,0.7) 0%, transparent 60%)' }} />
          <div style={{ position: 'absolute', bottom: 14, left: 16, right: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
              <span style={{ fontSize: 10, color: dishTypeColor, background: 'rgba(255,255,255,0.9)', border: `1px solid ${dishTypeColor}40`, borderRadius: 6, padding: '2px 7px', fontWeight: 600 }}>
                {dishTypeEmoji} {recipe.dishType}
              </span>
              <span style={{ fontSize: 10, color: '#2563eb', background: 'rgba(255,255,255,0.9)', border: '1px solid rgba(37,99,235,0.3)', borderRadius: 6, padding: '2px 7px', fontWeight: 600 }}>
                {recipe.recipeType}
              </span>
            </div>
            <h1 style={{ fontSize: 26, color: '#fff', fontWeight: 800, lineHeight: 1.1, margin: 0 }}>{recipe.name}</h1>
          </div>
        </div>

        {/* ── Completion status banner ─────────────────────────────────────────── */}
        <div style={{
          margin: '14px 0 0',
          background: wasManual ? 'rgba(217,119,6,0.06)' : 'rgba(22,163,74,0.06)',
          border: `1px solid ${wasManual ? 'rgba(217,119,6,0.22)' : 'rgba(22,163,74,0.2)'}`,
          borderRadius: 12, padding: '10px 14px',
          display: 'flex', alignItems: 'center', gap: 10,
        }}>
          <div style={{
            width: 32, height: 32, borderRadius: '50%',
            background: wasManual ? 'rgba(217,119,6,0.1)' : 'rgba(22,163,74,0.1)',
            border: `1px solid ${wasManual ? 'rgba(217,119,6,0.3)' : 'rgba(22,163,74,0.25)'}`,
            display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
          }}>
            {wasManual
              ? <FlagTriangleRight size={15} color="#d97706" />
              : <span style={{ fontSize: 14, color: '#16a34a' }}>✓</span>}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 11, color: wasManual ? '#d97706' : '#16a34a', fontWeight: 700 }}>
              {wasManual ? 'Manually Ended Early' : 'Recipe Auto-Completed'}
            </div>
            <div style={{ fontSize: 10, color: '#94a3b8', marginTop: 2 }}>
              {wasManual
                ? `${executedMinuteCount} of ${recipe.minutes.length} planned minute${recipe.minutes.length !== 1 ? 's' : ''} cooked · ${Math.round((actualElapsedSec / totalPlannedSec) * 100)}% of timeline`
                : `Full ${recipe.minutes.length}-minute timeline completed`}
            </div>
          </div>
          <div style={{ textAlign: 'right', flexShrink: 0 }}>
            <div style={{ fontSize: 14, color: '#2563eb', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{formatMMSS(actualElapsedSec)}</div>
            <div style={{ fontSize: 9, color: '#94a3b8' }}>actual cook</div>
          </div>
        </div>

        {/* Time Summary */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8, margin: '14px 0' }}>
          {[
            { label: 'On2Cook', value: formatMMSS(actualCookSec), color: '#2563eb', icon: <Zap size={12} /> },
            { label: 'Hold Time', value: `${Math.min(totalHold, actualElapsedSec)}s`, color: '#64748b', icon: <Clock size={12} /> },
            { label: 'Normal Cooking', value: formatMMSS(actualElapsedSec * 4), color: '#94a3b8', icon: <Clock size={12} /> },
          ].map(item => (
            <div key={item.label} style={{ background: 'rgba(255,255,255,0.8)', border: '1px solid rgba(148,163,184,0.18)', borderRadius: 12, padding: '10px 12px', boxShadow: '0 1px 4px rgba(0,0,0,0.05)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginBottom: 4 }}>
                <span style={{ color: item.color }}>{item.icon}</span>
                <span style={{ fontSize: 9, color: '#64748b', fontWeight: 500 }}>{item.label}</span>
              </div>
              <div style={{ fontSize: 14, color: item.color, fontWeight: 700 }}>{item.value}</div>
            </div>
          ))}
        </div>

        {/* Recipe parameters */}
        <div style={{ background: 'rgba(255,255,255,0.8)', border: '1px solid rgba(148,163,184,0.18)', borderRadius: 14, padding: '12px 16px', marginBottom: 16, boxShadow: '0 1px 6px rgba(0,0,0,0.05)' }}>
          <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
            <div><span style={{ fontSize: 10, color: '#94a3b8' }}>Quantity</span><div style={{ fontSize: 13, color: '#0f172a', fontWeight: 700 }}>{recipe.quantity}{recipe.quantityUnit}</div></div>
            <div><span style={{ fontSize: 10, color: '#94a3b8' }}>Consistency</span><div style={{ fontSize: 13, color: '#7c3aed', fontWeight: 700 }}>{recipe.consistency}</div></div>
            <div><span style={{ fontSize: 10, color: '#94a3b8' }}>Profile</span><div style={{ fontSize: 13, color: recipe.healthRichRatio < 40 ? '#16a34a' : recipe.healthRichRatio > 70 ? '#ea580c' : '#0f172a', fontWeight: 700 }}>{recipe.healthRichRatio < 40 ? 'Healthy' : recipe.healthRichRatio > 70 ? 'Rich' : 'Balanced'}</div></div>
            <div>
              <span style={{ fontSize: 10, color: '#94a3b8' }}>Minutes</span>
              <div style={{ fontSize: 13, color: '#0f172a', fontWeight: 700 }}>
                {executedMinuteCount}
                {wasManual && executedMinuteCount < recipe.minutes.length && (
                  <span style={{ color: '#cbd5e1', fontSize: 10, fontWeight: 500 }}>/{recipe.minutes.length}</span>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Divider */}
        <div style={{ height: 1, background: 'rgba(148,163,184,0.15)', margin: '0 0 16px' }} />

        {/* Ingredients */}
        {allIngredients.length > 0 && (
          <section style={{ marginBottom: 20 }}>
            <h2 style={{ fontSize: 14, color: '#0f172a', fontWeight: 700, marginBottom: 12, display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ width: 4, height: 14, borderRadius: 2, background: '#2563eb', display: 'inline-block' }} />
              Ingredients
            </h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
              {allIngredients.map((ing, i) => (
                <div key={ing.id + i} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '7px 12px', background: 'rgba(255,255,255,0.75)', borderRadius: 8, border: '1px solid rgba(148,163,184,0.15)' }}>
                  <span style={{ fontSize: 12, color: '#0f172a' }}>{ing.name}</span>
                  <span style={{ fontSize: 12, color: '#2563eb', fontWeight: 600 }}>{ing.quantity} {ing.unit}</span>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Steps */}
        {steps.length > 0 && (
          <section style={{ marginBottom: 20 }}>
            <h2 style={{ fontSize: 14, color: '#0f172a', fontWeight: 700, marginBottom: 12, display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ width: 4, height: 14, borderRadius: 2, background: '#16a34a', display: 'inline-block' }} />
              Cooking Steps
            </h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {steps.map((step) => (
                <div key={step.minuteIdx} style={{ background: 'rgba(255,255,255,0.8)', border: '1px solid rgba(148,163,184,0.15)', borderRadius: 12, padding: '12px 14px', boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                      <span style={{ width: 22, height: 22, borderRadius: '50%', background: 'rgba(37,99,235,0.1)', border: '1px solid rgba(37,99,235,0.25)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, color: '#2563eb', fontWeight: 700, flexShrink: 0 }}>{step.stepNum}</span>
                      <span style={{ fontSize: 12, color: '#64748b' }}>Min {step.minuteIdx + 1} · Lid open {step.lidDuration}s</span>
                    </div>
                    <div style={{ display: 'flex', gap: 5 }}>
                      {step.avgInd > 0 && (
                        <span style={{ fontSize: 10, color: indColor(step.avgInd), background: `${indColor(step.avgInd)}15`, border: `1px solid ${indColor(step.avgInd)}40`, borderRadius: 6, padding: '2px 6px', fontWeight: 600 }}>IH {step.avgInd}</span>
                      )}
                      {step.mwOnCount > 0 && (
                        <span style={{ fontSize: 10, color: EXP_MW_ON_COLOR, background: 'rgba(236,72,153,0.12)', border: '1px solid rgba(236,72,153,0.35)', borderRadius: 6, padding: '2px 6px', fontWeight: 600 }}>MW ON</span>
                      )}
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap' }}>
                    {step.ingredients.map(ing => (
                      <span key={ing.id} style={{ fontSize: 11, color: '#1e293b', background: 'rgba(248,250,255,0.9)', border: '1px solid rgba(148,163,184,0.2)', borderRadius: 6, padding: '3px 8px' }}>
                        {ing.quantity}{ing.unit} {ing.name}
                      </span>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Power timeline summary */}
        <section style={{ marginBottom: 20 }}>
          <h2 style={{ fontSize: 14, color: '#0f172a', fontWeight: 700, marginBottom: 12, display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 4, height: 14, borderRadius: 2, background: '#ea580c', display: 'inline-block' }} />
            Cooking Timeline
            {wasManual && (
              <span style={{ marginLeft: 'auto', fontSize: 9, color: '#94a3b8', fontWeight: 500 }}>
                executed {executedMinuteCount}/{recipe.minutes.length} min
              </span>
            )}
          </h2>
          <div style={{ overflowX: 'auto', paddingBottom: 8 }}>
            <div style={{ display: 'inline-flex', flexDirection: 'column', gap: 4, minWidth: recipe.minutes.length * 40 }}>

              {/* IH row */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <span style={{ width: 28, fontSize: 9, color: '#f97316', fontWeight: 700, flexShrink: 0 }}>IH</span>
                <div style={{ display: 'flex', gap: 2 }}>
                  {recipe.minutes.map((m, i) => {
                    const avg = Math.round(m.subBlocks.reduce((a, b) => a + b.inductionPower, 0) / 4);
                    const executed = i < executedMinuteCount;
                    return (
                      <div key={i} style={{ width: 36, height: 24, borderRadius: 4, background: avg > 0 ? indColor(avg) : 'rgba(148,163,184,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: executed ? (avg > 0 ? 0.9 : 1) : 0.25 }}>
                        <span style={{ fontSize: 8, color: avg > 0 ? '#fff' : '#94a3b8', fontWeight: 700 }}>{avg || '—'}</span>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* MW row — ON/OFF only per minute */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <span style={{ width: 28, fontSize: 9, color: '#ec4899', fontWeight: 700, flexShrink: 0 }}>MW</span>
                <div style={{ display: 'flex', gap: 2 }}>
                  {recipe.minutes.map((m, i) => {
                    const onCount = m.lidOpen ? 0 : m.subBlocks.filter(s => s.microwaveActive).length;
                    const hasOn = onCount > 0;
                    const executed = i < executedMinuteCount;
                    return (
                      <div key={i} style={{ width: 36, height: 24, borderRadius: 4, background: hasOn ? 'rgba(236,72,153,0.15)' : 'rgba(148,163,184,0.1)', border: hasOn ? '1px solid rgba(236,72,153,0.4)' : 'none', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 1, opacity: executed ? 1 : 0.25 }}>
                        {hasOn ? (
                          <>
                            <div style={{ display: 'flex', gap: 1 }}>
                              {m.subBlocks.map((sb, si) => (
                                <div key={si} style={{ width: 5, height: 8, borderRadius: 1, background: sb.microwaveActive ? EXP_MW_ON_COLOR : 'rgba(148,163,184,0.2)' }} />
                              ))}
                            </div>
                            <span style={{ fontSize: 7, color: EXP_MW_ON_COLOR, fontWeight: 700, lineHeight: 1 }}>ON</span>
                          </>
                        ) : (
                          <span style={{ fontSize: 8, color: '#94a3b8', fontWeight: 700 }}>{m.lidOpen ? 'LID' : '—'}</span>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Stirrer row */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <span style={{ width: 28, fontSize: 9, color: '#14b8a6', fontWeight: 700, flexShrink: 0 }}>STR</span>
                <div style={{ display: 'flex', gap: 2 }}>
                  {recipe.minutes.map((m, i) => {
                    const dom = dominantStirSpeed(m.subBlocks);
                    const col = dom ? EXP_STIR_COLOR[dom] : '';
                    const barH = dom ? Math.round(EXP_STIR_BAR_H[dom] * 0.55) : 0;
                    const label = dom ? EXP_STIR_LABEL[dom] : '';
                    const executed = i < executedMinuteCount;
                    return (
                      <div key={i} style={{ width: 36, height: 24, borderRadius: 4, background: dom ? `${col}15` : 'rgba(148,163,184,0.1)', border: dom ? `1px solid ${col}40` : 'none', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'flex-end', padding: '2px 0 2px', opacity: executed ? 1 : 0.25 }}>
                        {dom && (
                          <>
                            <div style={{ width: '45%', height: barH, borderRadius: '2px 2px 0 0', background: col, marginBottom: 1 }} />
                            <span style={{ fontSize: 7, color: col, fontWeight: 700, lineHeight: 1 }}>{label}</span>
                          </>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Lid row */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <span style={{ width: 28, fontSize: 9, color: '#4ade80', fontWeight: 700, flexShrink: 0 }}>LID</span>
                <div style={{ display: 'flex', gap: 2 }}>
                  {recipe.minutes.map((m, i) => {
                    const executed = i < executedMinuteCount;
                    return (
                      <div key={i} style={{ width: 36, height: 24, borderRadius: 4, background: m.lidOpen ? 'rgba(22,163,74,0.18)' : 'rgba(148,163,184,0.1)', border: m.lidOpen ? '1px solid rgba(22,163,74,0.35)' : 'none', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: executed ? 1 : 0.25 }}>
                        {m.lidOpen && <span style={{ fontSize: 7, color: '#16a34a', fontWeight: 700 }}>○</span>}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Water row */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <span style={{ width: 28, fontSize: 9, color: '#22d3ee', fontWeight: 700, flexShrink: 0 }}>W</span>
                <div style={{ display: 'flex', gap: 2 }}>
                  {recipe.minutes.map((m, i) => {
                    const active = getActiveWaterBlocks(m.waterBlocks);
                    const executed = i < executedMinuteCount;
                    return (
                      <div key={i} style={{ width: 36, height: 24, borderRadius: 4, background: active > 0 ? 'rgba(8,145,178,0.15)' : 'rgba(148,163,184,0.1)', border: active > 0 ? '1px solid rgba(8,145,178,0.3)' : 'none', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: executed ? 1 : 0.25 }}>
                        {active > 0 && <span style={{ fontSize: 7, color: '#0891b2', fontWeight: 700 }}>{active * 150}ml</span>}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Minute labels — amber line marks manual end boundary */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginTop: 2 }}>
                <span style={{ width: 28, flexShrink: 0 }} />
                <div style={{ display: 'flex', gap: 2 }}>
                  {recipe.minutes.map((_, i) => (
                    <div key={i} style={{ width: 36, textAlign: 'center', position: 'relative' }}>
                      <span style={{ fontSize: 8, color: i < executedMinuteCount ? '#94a3b8' : '#cbd5e1' }}>M{i + 1}</span>
                      {/* Amber boundary marker at the last executed minute */}
                      {wasManual && i === executedMinuteCount - 1 && (
                        <div style={{
                          position: 'absolute', top: -30, right: -1,
                          width: 2, height: 36,
                          background: 'rgba(251,191,36,0.55)',
                          borderRadius: 1,
                        }} />
                      )}
                    </div>
                  ))}
                </div>
              </div>

            </div>
          </div>
        </section>

        {/* Water additions */}
        {waterMinutes.length > 0 && (
          <section style={{ marginBottom: 20 }}>
            <h2 style={{ fontSize: 14, color: '#e2e8f0', fontWeight: 700, marginBottom: 12, display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ width: 4, height: 14, borderRadius: 2, background: '#22d3ee', display: 'inline-block' }} />
              Water Additions
              <span style={{ marginLeft: 'auto', fontSize: 12, color: '#22d3ee', fontWeight: 700 }}>{totalWaterMl} ml total</span>
            </h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {waterMinutes.map(w => (
                <div key={w.minuteIdx} style={{ background: 'rgba(6,182,212,0.06)', border: '1px solid rgba(6,182,212,0.2)', borderRadius: 12, padding: '10px 14px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                    <span style={{ fontSize: 12, color: '#94a3b8' }}>Min {w.minuteIdx + 1}</span>
                    <span style={{ fontSize: 14, color: '#22d3ee', fontWeight: 700 }}>{w.ml} ml</span>
                  </div>
                  {/* 4-block visual */}
                  <div style={{ display: 'flex', gap: 4 }}>
                    {w.waterBlocks.map((active, i) => (
                      <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 3 }}>
                        <div style={{ width: '100%', height: 14, borderRadius: 4, background: active ? 'linear-gradient(90deg,#22d3ee,#0891b2)' : 'rgba(255,255,255,0.07)', boxShadow: active ? '0 0 5px rgba(34,211,238,0.4)' : 'none' }} />
                        <span style={{ fontSize: 8, color: active ? '#22d3ee' : '#334155' }}>{active ? '150ml' : '—'}</span>
                        <span style={{ fontSize: 7, color: '#475569' }}>{i * 15}s</span>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Hold summary */}
        {totalHold > 0 && (
          <section style={{ marginBottom: 20 }}>
            <h2 style={{ fontSize: 14, color: '#e2e8f0', fontWeight: 700, marginBottom: 12, display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ width: 4, height: 14, borderRadius: 2, background: '#64748b', display: 'inline-block' }} />
              Miscellaneous Activity
            </h2>
            <div style={{ background: 'rgba(100,116,139,0.08)', border: '1px solid rgba(100,116,139,0.2)', borderRadius: 12, padding: '12px 14px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                <span style={{ fontSize: 12, color: '#94a3b8', fontWeight: 600 }}>Total Hold / Misc Activity</span>
                <span style={{ fontSize: 14, color: '#94a3b8', fontWeight: 700 }}>{totalHold}s</span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                {recipe.minutes.filter(m => m.lidOpen).map((m, i) => (
                  <div key={i} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: '#64748b' }}>
                    <span>Min {m.minuteIndex + 1} — lid open</span>
                    <span>{m.lidOpenDuration}s</span>
                  </div>
                ))}
              </div>
            </div>
          </section>
        )}

        {/* Final summary box */}
        <div style={{ background: 'linear-gradient(135deg, rgba(30,64,175,0.3), rgba(59,130,246,0.15))', border: '1px solid rgba(96,165,250,0.3)', borderRadius: 16, padding: '16px', marginBottom: 20 }}>
          <p style={{ fontSize: 10, color: '#93c5fd', fontWeight: 600, marginBottom: 12, letterSpacing: 0.5 }}>RECIPE SUMMARY</p>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            {[
              { label: 'Actual Cook Time', value: formatMMSS(actualCookSec), color: '#60a5fa' },
              { label: 'Traditional Cook', value: formatMMSS(actualElapsedSec * 4), color: '#64748b' },
              { label: 'Hold / Activity', value: `${Math.min(totalHold, actualElapsedSec)}s`, color: '#94a3b8' },
              { label: wasManual ? 'Executed Duration' : 'Total Recipe', value: formatMMSS(actualElapsedSec), color: '#e2e8f0' },
            ].map(item => (
              <div key={item.label}>
                <div style={{ fontSize: 9, color: '#64748b', marginBottom: 2 }}>{item.label}</div>
                <div style={{ fontSize: 15, color: item.color, fontWeight: 700 }}>{item.value}</div>
              </div>
            ))}
          </div>
          {wasManual && totalPlannedSec > actualElapsedSec && (
            <div style={{ marginTop: 12, paddingTop: 10, borderTop: '1px solid rgba(255,255,255,0.07)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: 9, color: '#334155' }}>Planned total</span>
              <span style={{ fontSize: 12, color: '#1e293b', fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>{formatMMSS(totalPlannedSec)}</span>
            </div>
          )}
        </div>

        {/* ── DISH PHOTO ───────────────────────────────────────────────────────── */}
        <section style={{ marginBottom: 24 }}>
          <h2 style={{ fontSize: 14, color: '#e2e8f0', fontWeight: 700, marginBottom: 14, display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 4, height: 14, borderRadius: 2, background: '#c084fc', display: 'inline-block' }} />
            Dish Photo
            {dishPhoto && (
              <span style={{ marginLeft: 'auto', fontSize: 10, color: '#64748b', fontWeight: 500 }}>Tap to replace</span>
            )}
          </h2>

          {/* Hidden file inputs */}
          <input
            ref={cameraInputRef}
            type="file"
            accept="image/*"
            capture="environment"
            onChange={handleFileChange}
            style={{ display: 'none' }}
          />
          <input
            ref={galleryInputRef}
            type="file"
            accept="image/*"
            onChange={handleFileChange}
            style={{ display: 'none' }}
          />

          {dishPhoto ? (
            /* ── Photo preview ── */
            <div style={{ position: 'relative', borderRadius: 16, overflow: 'hidden' }}>
              <img
                src={dishPhoto}
                alt="Dish photo"
                style={{ width: '100%', height: 220, objectFit: 'cover', display: 'block' }}
              />
              {/* Gradient overlay at bottom */}
              <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, height: 70, background: 'linear-gradient(to top, rgba(3,11,26,0.85), transparent)' }} />
              {/* Action chips over image */}
              <div style={{ position: 'absolute', bottom: 12, left: 12, right: 12, display: 'flex', gap: 8 }}>
                <button
                  onClick={() => cameraInputRef.current?.click()}
                  style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5, height: 34, borderRadius: 9, cursor: 'pointer', background: 'rgba(192,132,252,0.15)', border: '1px solid rgba(192,132,252,0.35)', color: '#c084fc', fontSize: 11, fontWeight: 600 }}
                >
                  <Camera size={13} /> Retake
                </button>
                <button
                  onClick={() => galleryInputRef.current?.click()}
                  style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5, height: 34, borderRadius: 9, cursor: 'pointer', background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.12)', color: '#94a3b8', fontSize: 11, fontWeight: 600 }}
                >
                  <ImagePlus size={13} /> Replace
                </button>
                <button
                  onClick={() => setDishPhoto(null)}
                  style={{ width: 34, height: 34, borderRadius: 9, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(248,113,113,0.1)', border: '1px solid rgba(248,113,113,0.3)', flexShrink: 0 }}
                >
                  <X size={14} color="#f87171" />
                </button>
              </div>
            </div>
          ) : (
            /* ── Upload zone ── */
            <div style={{
              border: '1.5px dashed rgba(192,132,252,0.25)',
              borderRadius: 16,
              background: 'rgba(192,132,252,0.04)',
              padding: '28px 20px',
              display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14,
            }}>
              {/* Icon ring */}
              <div style={{
                width: 56, height: 56, borderRadius: '50%',
                background: 'rgba(192,132,252,0.08)', border: '1px solid rgba(192,132,252,0.2)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Camera size={24} color="rgba(192,132,252,0.6)" />
              </div>
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 13, color: '#cbd5e1', fontWeight: 600, marginBottom: 4 }}>
                  Capture your dish
                </div>
                <div style={{ fontSize: 11, color: '#475569' }}>
                  Photograph the finished result to attach to this recipe sheet
                </div>
              </div>
              {/* Buttons */}
              <div style={{ display: 'flex', gap: 10, width: '100%' }}>
                <button
                  onClick={() => cameraInputRef.current?.click()}
                  style={{
                    flex: 1, height: 42, borderRadius: 11, cursor: 'pointer',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7,
                    background: 'rgba(192,132,252,0.12)', border: '1px solid rgba(192,132,252,0.3)',
                    color: '#c084fc', fontSize: 13, fontWeight: 600,
                  }}
                >
                  <Camera size={15} /> Take Photo
                </button>
                <button
                  onClick={() => galleryInputRef.current?.click()}
                  style={{
                    flex: 1, height: 42, borderRadius: 11, cursor: 'pointer',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7,
                    background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.1)',
                    color: '#94a3b8', fontSize: 13, fontWeight: 600,
                  }}
                >
                  <ImagePlus size={15} /> Upload
                </button>
              </div>
              {/* Share Image — enabled once a photo is attached */}
              <button
                disabled={!dishPhoto}
                onClick={async () => {
                  if (!dishPhoto) return;
                  try {
                    const res = await fetch(dishPhoto);
                    const blob = await res.blob();
                    const file = new File([blob], 'dish-photo.jpg', { type: blob.type });
                    if (navigator.share && navigator.canShare?.({ files: [file] })) {
                      await navigator.share({ title: 'My Recipe Dish', text: 'Check out this dish I made!', files: [file] });
                    } else if (navigator.share) {
                      await navigator.share({ title: 'My Recipe Dish', text: 'Check out this dish I made!' });
                    } else {
                      const a = document.createElement('a');
                      a.href = dishPhoto; a.download = 'dish-photo.jpg'; a.click();
                    }
                  } catch (_) { /* cancelled */ }
                }}
                style={{
                  width: '100%', height: 40, borderRadius: 11,
                  display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7,
                  background: dishPhoto ? 'rgba(96,165,250,0.12)' : 'rgba(255,255,255,0.03)',
                  border: `1px solid ${dishPhoto ? 'rgba(96,165,250,0.35)' : 'rgba(255,255,255,0.08)'}`,
                  color: dishPhoto ? '#60a5fa' : '#334155',
                  fontSize: 12, fontWeight: 600,
                  cursor: dishPhoto ? 'pointer' : 'not-allowed',
                  opacity: dishPhoto ? 1 : 0.45,
                  transition: 'all 0.2s',
                }}
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/>
                  <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
                </svg>
                {dishPhoto ? 'Share Image' : 'Share Image — add a photo first'}
              </button>
            </div>
          )}
        </section>

        {/* ── ACTION BUTTONS ───────────────────────────────────────────────────── */}
        <section style={{ marginBottom: 20 }}>
          {/* Divider with label */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
            <div style={{ flex: 1, height: 1, background: 'rgba(255,255,255,0.07)' }} />
            <span style={{ fontSize: 9, color: '#334155', letterSpacing: 1, textTransform: 'uppercase' }}>Finish Session</span>
            <div style={{ flex: 1, height: 1, background: 'rgba(255,255,255,0.07)' }} />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>

            {/* Save to Library — primary CTA */}
            <button
              onClick={saved ? undefined : openSaveDialog}
              disabled={saved}
              style={{
                width: '100%', height: 50, borderRadius: 13, cursor: saved ? 'default' : 'pointer',
                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 9,
                background: saved
                  ? 'rgba(74,222,128,0.1)'
                  : 'linear-gradient(135deg, rgba(30,64,175,0.9), rgba(37,99,235,0.85))',
                border: saved ? '1px solid rgba(74,222,128,0.3)' : '1px solid rgba(96,165,250,0.4)',
                boxShadow: saved ? 'none' : '0 4px 24px rgba(37,99,235,0.25)',
                transition: 'all 0.25s ease',
              }}
            >
              <BookmarkCheck size={16} color={saved ? '#4ade80' : '#fff'} />
              <span style={{ fontSize: 14, color: saved ? '#4ade80' : '#fff', fontWeight: 700 }}>
                {saved ? 'Saved to Library ✓' : 'Save to Library'}
              </span>
            </button>

            {/* Secondary row: Back to Editor + Return Home */}
            <div style={{ display: 'flex', gap: 10 }}>
              <button
                onClick={() => navigate('/pro-editor')}
                style={{
                  flex: 1, height: 44, borderRadius: 11, cursor: 'pointer',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7,
                  background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.09)',
                }}
              >
                <ChevronLeft size={14} color="#64748b" />
                <span style={{ fontSize: 13, color: '#64748b', fontWeight: 600 }}>Back to Editor</span>
              </button>
              <button
                onClick={() => navigate('/')}
                style={{
                  flex: 1, height: 44, borderRadius: 11, cursor: 'pointer',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7,
                  background: 'rgba(74,222,128,0.07)', border: '1px solid rgba(74,222,128,0.2)',
                }}
              >
                <Home size={14} color="#4ade80" />
                <span style={{ fontSize: 13, color: '#4ade80', fontWeight: 600 }}>Return Home</span>
              </button>
            </div>

          </div>
        </section>

        {/* Save to Library dialog */}
        {showSaveDialog && (
          <div
            style={{ position: 'fixed', inset: 0, zIndex: 50, background: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
            onClick={() => setShowSaveDialog(false)}
          >
            <div
              onClick={e => e.stopPropagation()}
              style={{ background: 'linear-gradient(160deg,#f8faff,#f0f4ff)', border: '1px solid rgba(148,163,184,0.22)', borderRadius: 16, padding: '24px 22px', width: 320, boxShadow: '0 12px 40px rgba(0,0,0,0.2)' }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
                <span style={{ fontSize: 15, color: '#0f172a', fontWeight: 700 }}>Name your recipe</span>
                <button onClick={() => setShowSaveDialog(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94a3b8', padding: 4 }}>
                  <X size={16} />
                </button>
              </div>
              <input
                autoFocus
                value={saveDialogName}
                onChange={e => setSaveDialogName(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') confirmSave(); }}
                placeholder="Enter recipe name…"
                style={{ width: '100%', padding: '11px 13px', borderRadius: 10, background: 'rgba(255,255,255,0.9)', border: '1px solid rgba(148,163,184,0.3)', color: '#0f172a', fontSize: 14, fontWeight: 600, outline: 'none', boxSizing: 'border-box', marginBottom: 14 }}
              />
              <div style={{ display: 'flex', gap: 8 }}>
                <button onClick={() => setShowSaveDialog(false)} style={{ flex: 1, padding: '10px 0', borderRadius: 9, cursor: 'pointer', background: 'rgba(148,163,184,0.1)', border: '1px solid rgba(148,163,184,0.25)', color: '#64748b', fontSize: 13, fontWeight: 600 }}>
                  Cancel
                </button>
                <button
                  onClick={confirmSave}
                  disabled={!saveDialogName.trim()}
                  style={{ flex: 1, padding: '10px 0', borderRadius: 9, cursor: saveDialogName.trim() ? 'pointer' : 'not-allowed', background: saveDialogName.trim() ? 'linear-gradient(135deg,#1e40af,#3b82f6)' : 'rgba(148,163,184,0.2)', border: 'none', color: saveDialogName.trim() ? '#fff' : '#94a3b8', fontSize: 13, fontWeight: 700 }}
                >
                  Save
                </button>
              </div>
            </div>
          </div>
        )}

        {/* QR-like footer */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 0', borderTop: '1px solid rgba(255,255,255,0.07)' }}>
          <div>
            <p style={{ fontSize: 10, color: '#475569' }}>Created with</p>
            <p style={{ fontSize: 12, color: '#60a5fa', fontWeight: 700 }}>On2Cook Pro</p>
          </div>
          <div style={{ width: 40, height: 40, background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ width: 24, height: 24, display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 2 }}>
              {Array(9).fill(0).map((_, i) => <div key={i} style={{ borderRadius: 1, background: [0,1,3,4,5,7,8].includes(i) ? 'rgba(255,255,255,0.6)' : 'transparent' }} />)}
            </div>
          </div>
        </div>

      </div>
    </div>
  );
}