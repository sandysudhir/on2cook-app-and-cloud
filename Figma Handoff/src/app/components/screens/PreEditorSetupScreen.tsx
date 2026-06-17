import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import {
  ChevronLeft, Plus, Trash2, ChevronDown, ChevronUp,
  Play,
} from 'lucide-react';
import { useApp } from '../../AppContext';
import {
  ProIngredient, VeganType, ProRecipeType, ConsistencyType,
} from '../../proTypes';
import { generateTimeline, TimelineFactors, PRESET_RECIPES } from '../../presetRecipes';

// ─── Option sets ──────────────────────────────────────────────────────────────
const DISH_TYPES: { value: VeganType; label: string; emoji: string; color: string }[] = [
  { value: 'veg',     label: 'Veg',     emoji: '🥦', color: '#16a34a' },
  { value: 'non-veg', label: 'Non-Veg', emoji: '🍗', color: '#dc2626' },
  { value: 'vegan',   label: 'Vegan',   emoji: '🌱', color: '#15803d' },
];

const RECIPE_TYPES: { value: ProRecipeType; label: string; color: string }[] = [
  { value: 'gravy',    label: 'Gravy',    color: '#2563eb' },
  { value: 'semi-dry', label: 'Semi-Dry', color: '#7c3aed' },
  { value: 'dry',      label: 'Dry',      color: '#d97706' },
  { value: 'saute',    label: 'Sauté',    color: '#059669' },
  { value: 'boil',     label: 'Boil',     color: '#0891b2' },
  { value: 'fry',      label: 'Fry',      color: '#ea580c' },
  { value: 'steam',    label: 'Steam',    color: '#0e7490' },
];

const CONSISTENCIES: { value: ConsistencyType; label: string; desc: string }[] = [
  { value: 'thin',   label: 'Thin',   desc: 'Flowing, light' },
  { value: 'medium', label: 'Medium', desc: 'Balanced body' },
  { value: 'thick',  label: 'Thick',  desc: 'Dense, rich' },
];

const UNITS: ProIngredient['unit'][] = ['g', 'ml', 'piece', 'tsp', 'tbsp'];

// ─── Ingredient Row component ─────────────────────────────────────────────────
function IngredientRow({
  ing,
  onUpdate,
  onRemove,
}: {
  ing: ProIngredient;
  onUpdate: (updated: ProIngredient) => void;
  onRemove: () => void;
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div style={{
      background: 'rgba(255,255,255,0.85)',
      border: '1px solid rgba(148,163,184,0.2)',
      borderRadius: 10,
      overflow: 'hidden',
      boxShadow: '0 1px 4px rgba(0,0,0,0.05)',
    }}>
      {/* Collapsed row */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '9px 12px' }}>
        <div style={{
          width: 6, height: 6, borderRadius: '50%', flexShrink: 0,
          background: ing.group ? '#2563eb' : 'rgba(148,163,184,0.4)',
        }} />
        <span style={{ flex: 1, fontSize: 12, color: '#0f172a', fontWeight: 500 }}>{ing.name}</span>
        <span style={{ fontSize: 11, color: '#2563eb', fontWeight: 600 }}>
          {ing.quantity} {ing.unit}
        </span>
        <button
          onClick={() => setExpanded(e => !e)}
          style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '2px 4px', color: '#94a3b8' }}
        >
          {expanded ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
        </button>
        <button
          onClick={onRemove}
          style={{ background: 'rgba(220,38,38,0.07)', border: '1px solid rgba(220,38,38,0.18)', borderRadius: 6, cursor: 'pointer', padding: '4px 6px' }}
        >
          <Trash2 size={11} color="#dc2626" />
        </button>
      </div>

      {/* Expanded edit */}
      {expanded && (
        <div style={{ borderTop: '1px solid rgba(148,163,184,0.15)', padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: 8, background: 'rgba(248,250,255,0.8)' }}>
          {/* Name */}
          <div>
            <label style={{ fontSize: 9, color: '#64748b', display: 'block', marginBottom: 4 }}>INGREDIENT NAME</label>
            <input
              value={ing.name}
              onChange={e => onUpdate({ ...ing, name: e.target.value })}
              style={{
                width: '100%', padding: '7px 10px', borderRadius: 7,
                background: 'rgba(255,255,255,0.9)', border: '1px solid rgba(148,163,184,0.25)',
                color: '#0f172a', fontSize: 12, outline: 'none', boxSizing: 'border-box',
              }}
            />
          </div>

          {/* Quantity + Unit */}
          <div style={{ display: 'flex', gap: 8 }}>
            <div style={{ flex: 1 }}>
              <label style={{ fontSize: 9, color: '#64748b', display: 'block', marginBottom: 4 }}>QUANTITY</label>
              <input
                type="number"
                value={ing.quantity}
                onChange={e => onUpdate({ ...ing, quantity: parseFloat(e.target.value) || 0 })}
                style={{
                  width: '100%', padding: '7px 10px', borderRadius: 7,
                  background: 'rgba(255,255,255,0.9)', border: '1px solid rgba(148,163,184,0.25)',
                  color: '#0f172a', fontSize: 12, outline: 'none', boxSizing: 'border-box',
                }}
              />
            </div>
            <div style={{ flex: 1 }}>
              <label style={{ fontSize: 9, color: '#64748b', display: 'block', marginBottom: 4 }}>UNIT</label>
              <div style={{ display: 'flex', gap: 3 }}>
                {UNITS.map(u => (
                  <button
                    key={u}
                    onClick={() => onUpdate({ ...ing, unit: u })}
                    style={{
                      flex: 1, padding: '7px 2px', borderRadius: 6, cursor: 'pointer', fontSize: 10, fontWeight: 600,
                      background: ing.unit === u ? 'rgba(37,99,235,0.12)' : 'rgba(255,255,255,0.7)',
                      border: `1px solid ${ing.unit === u ? 'rgba(37,99,235,0.4)' : 'rgba(148,163,184,0.22)'}`,
                      color: ing.unit === u ? '#2563eb' : '#64748b',
                    }}
                  >
                    {u}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Group tag */}
          <div>
            <label style={{ fontSize: 9, color: '#64748b', display: 'block', marginBottom: 4 }}>GROUP (optional)</label>
            <input
              value={ing.group ?? ''}
              onChange={e => onUpdate({ ...ing, group: e.target.value })}
              placeholder="fats / protein / vegetables / sauce / dairy..."
              style={{
                width: '100%', padding: '7px 10px', borderRadius: 7,
                background: 'rgba(255,255,255,0.9)', border: '1px solid rgba(148,163,184,0.25)',
                color: '#0f172a', fontSize: 11, outline: 'none', boxSizing: 'border-box',
              }}
            />
          </div>
        </div>
      )}
    </div>
  );
}

// ─── Main Screen ──────────────────────────────────────────────────────────────
export function PreEditorSetupScreen() {
  const navigate = useNavigate();
  const { presetSetup, setPresetSetup, startProRecipe } = useApp();

  if (!presetSetup) {
    return (
      <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 12, padding: 24, background: 'linear-gradient(160deg, #f0f4ff 0%, #f7f9ff 100%)' }}>
        <p style={{ fontSize: 13, color: '#94a3b8' }}>No recipe selected.</p>
        <button
          onClick={() => navigate('/preset-library')}
          style={{ fontSize: 13, color: '#2563eb', background: 'rgba(37,99,235,0.08)', border: '1px solid rgba(37,99,235,0.22)', borderRadius: 8, padding: '8px 16px', cursor: 'pointer' }}
        >
          Go to Recipe Library
        </button>
      </div>
    );
  }

  // Local state from presetSetup
  const [name]                     = useState(presetSetup.name);
  const [dishType, setDishType]   = useState<VeganType>(presetSetup.dishType);
  const lockedDishType            = presetSetup.dishType; // locked from preset
  const [recipeType, setRecipeType] = useState<ProRecipeType>(presetSetup.recipeType);
  const [quantity, setQuantity]   = useState(presetSetup.quantity);
  const [quantityUnit, setQuantityUnit] = useState(presetSetup.quantityUnit);
  const [consistency, setConsistency] = useState<ConsistencyType>(presetSetup.consistency);
  const [ingredients, setIngredients] = useState<ProIngredient[]>(presetSetup.ingredients);

  const dtColor = DISH_TYPES.find(d => d.value === dishType)!;
  const presetRecipe = PRESET_RECIPES.find(r => r.id === presetSetup.presetId);;

  function addIngredient() {
    const id = `ing-${Date.now()}`;
    setIngredients(prev => [...prev, { id, name: 'New Ingredient', quantity: 50, unit: 'g', group: '' }]);
  }

  function updateIngredient(idx: number, updated: ProIngredient) {
    setIngredients(prev => prev.map((ing, i) => (i === idx ? updated : ing)));
  }

  function removeIngredient(idx: number) {
    setIngredients(prev => prev.filter((_, i) => i !== idx));
  }

  function handleOpenEditor() {
    const factors: TimelineFactors = {
      dishType, recipeType, quantity, quantityUnit,
      consistency, healthRichRatio: presetSetup.healthRichRatio,
      ingredients,
    };
    const timeline = generateTimeline(factors);
    startProRecipe({
      id: `${presetSetup.presetId}-${Date.now()}`,
      name,
      dishType,
      recipeType,
      quantity,
      quantityUnit,
      consistency,
      healthRichRatio: presetSetup.healthRichRatio,
      tentativeMinutes: timeline.length,
      minutes: timeline,
      notes: '',
    });
    setPresetSetup({ ...presetSetup, name, dishType, recipeType, quantity, quantityUnit, consistency, ingredients });
    navigate('/pro-editor');
  }

  return (
    <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column', overflowY: 'auto', paddingBottom: 100, background: 'linear-gradient(160deg, #f0f4ff 0%, #f7f9ff 60%, #fafbff 100%)' }}>

      {/* ── Sticky header ── */}
      <div style={{
        position: 'sticky', top: 0, zIndex: 10,
        background: 'rgba(245,247,255,0.97)', backdropFilter: 'blur(20px)',
        borderBottom: '1px solid rgba(148,163,184,0.18)',
        padding: '14px 16px 12px',
        flexShrink: 0,
        boxShadow: '0 1px 8px rgba(0,0,0,0.05)',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 2 }}>
          <button
            onClick={() => navigate('/preset-library')}
            style={{ background: 'rgba(255,255,255,0.8)', border: '1px solid rgba(148,163,184,0.25)', borderRadius: 8, padding: '5px 10px', cursor: 'pointer', color: '#64748b', fontSize: 12, display: 'flex', alignItems: 'center', gap: 4, boxShadow: '0 1px 3px rgba(0,0,0,0.06)' }}
          >
            <ChevronLeft size={12} /> Library
          </button>
          <div style={{ flex: 1 }}>
            <p style={{ fontSize: 9, color: '#94a3b8', margin: 0 }}>CONFIGURE RECIPE</p>
          </div>
          <span style={{ fontSize: 9, color: '#16a34a', background: 'rgba(22,163,74,0.1)', border: '1px solid rgba(22,163,74,0.22)', borderRadius: 5, padding: '2px 6px' }}>
            Step 2 of 2
          </span>
        </div>
      </div>

      {/* ── Recipe photo header with editable name ── */}
      {presetRecipe?.imageUrl && (
        <div style={{ position: 'relative', height: 160, flexShrink: 0, overflow: 'hidden' }}>
          <img
            src={presetRecipe.imageUrl}
            alt={name}
            style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
          />
          <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, rgba(0,0,0,0.75) 0%, rgba(0,0,0,0.2) 55%, transparent 100%)' }} />
          {/* Name overlay */}
          <div style={{ position: 'absolute', bottom: 14, left: 14, right: 14 }}>
            <span style={{ fontSize: 18, color: '#fff', fontWeight: 800, textShadow: '0 1px 4px rgba(0,0,0,0.5)', lineHeight: 1.2, display: 'block', marginBottom: 5 }}>{name}</span>
            <div style={{ display: 'flex', gap: 5 }}>
              <span style={{ fontSize: 9, color: '#fff', background: 'rgba(0,0,0,0.35)', borderRadius: 4, padding: '2px 7px', fontWeight: 600 }}>{presetRecipe.category}</span>
              <span style={{ fontSize: 9, color: '#fff', background: 'rgba(0,0,0,0.35)', borderRadius: 4, padding: '2px 7px' }}>{presetRecipe.recipeType}</span>
            </div>
          </div>
        </div>
      )}

      <div style={{ padding: '16px 16px 0', display: 'flex', flexDirection: 'column', gap: 20 }}>


        {/* ── Veg / Non-Veg ── */}
        <section>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
            <span style={{ width: 4, height: 13, borderRadius: 2, background: '#16a34a', display: 'inline-block' }} />
            <span style={{ fontSize: 11, color: '#64748b', fontWeight: 600, letterSpacing: 0.5 }}>DIET TYPE</span>
            <span style={{ fontSize: 9, color: '#94a3b8', background: 'rgba(148,163,184,0.12)', border: '1px solid rgba(148,163,184,0.25)', borderRadius: 4, padding: '1px 6px', marginLeft: 2 }}>
              Pre-loaded from recipe
            </span>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            {DISH_TYPES.map(dt => {
              const active = dishType === dt.value;
              const isLocked = dt.value !== lockedDishType;
              return (
                <button
                  key={dt.value}
                  disabled={isLocked}
                  style={{
                    flex: 1, padding: '10px 6px', borderRadius: 11,
                    cursor: isLocked ? 'not-allowed' : 'default',
                    background: active ? `${dt.color}10` : 'rgba(255,255,255,0.45)',
                    border: `${active ? 2 : 1}px solid ${active ? dt.color : 'rgba(148,163,184,0.15)'}`,
                    display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4,
                    transition: 'all 0.15s',
                    boxShadow: active ? `0 2px 8px ${dt.color}20` : 'none',
                    opacity: isLocked ? 0.38 : 1,
                  }}
                >
                  <span style={{ fontSize: 18 }}>{dt.emoji}</span>
                  <span style={{ fontSize: 11, color: active ? dt.color : '#94a3b8', fontWeight: 600 }}>{dt.label}</span>
                  {active && (
                    <span style={{ fontSize: 8, color: dt.color, fontWeight: 700 }}>✓ Selected</span>
                  )}
                </button>
              );
            })}
          </div>
          <p style={{ fontSize: 10, color: '#94a3b8', marginTop: 6 }}>
            Diet type is pre-determined by the selected recipe and cannot be changed.
          </p>
        </section>

        {/* ── Recipe Type ── */}
        <section>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
            <span style={{ width: 4, height: 13, borderRadius: 2, background: '#7c3aed', display: 'inline-block' }} />
            <span style={{ fontSize: 11, color: '#64748b', fontWeight: 600, letterSpacing: 0.5 }}>RECIPE TYPE</span>
          </div>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {RECIPE_TYPES.map(rt => {
              const active = recipeType === rt.value;
              return (
                <button
                  key={rt.value}
                  onClick={() => setRecipeType(rt.value)}
                  style={{
                    padding: '7px 13px', borderRadius: 8, cursor: 'pointer', fontSize: 12, fontWeight: 600,
                    background: active ? `${rt.color}12` : 'rgba(255,255,255,0.75)',
                    border: `${active ? 2 : 1}px solid ${active ? rt.color : 'rgba(148,163,184,0.22)'}`,
                    color: active ? rt.color : '#64748b',
                    transition: 'all 0.15s',
                  }}
                >
                  {rt.label}
                </button>
              );
            })}
          </div>
        </section>

        {/* ── Quantity ── */}
        <section>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
            <span style={{ width: 4, height: 13, borderRadius: 2, background: '#d97706', display: 'inline-block' }} />
            <span style={{ fontSize: 11, color: '#64748b', fontWeight: 600, letterSpacing: 0.5 }}>QUANTITY</span>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            {/* Stepper */}
            <button
              onClick={() => setQuantity(q => Math.max(100, q - 100))}
              style={{ width: 38, height: 38, borderRadius: 9, background: 'rgba(255,255,255,0.8)', border: '1px solid rgba(148,163,184,0.25)', cursor: 'pointer', color: '#64748b', fontSize: 18, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}
            >
              −
            </button>
            <div style={{ flex: 1 }}>
              <input
                type="number"
                value={quantity}
                onChange={e => setQuantity(Math.max(50, parseInt(e.target.value) || 0))}
                style={{
                  width: '100%', padding: '9px 12px', borderRadius: 9, textAlign: 'center',
                  background: 'rgba(255,255,255,0.9)', border: '1px solid rgba(148,163,184,0.25)',
                  color: '#d97706', fontSize: 18, fontWeight: 700, outline: 'none',
                  boxSizing: 'border-box',
                }}
              />
            </div>
            <button
              onClick={() => setQuantity(q => q + 100)}
              style={{ width: 38, height: 38, borderRadius: 9, background: 'rgba(255,255,255,0.8)', border: '1px solid rgba(148,163,184,0.25)', cursor: 'pointer', color: '#64748b', fontSize: 18, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}
            >
              +
            </button>
            {/* Unit selector */}
            <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
              {['g', 'ml'].map(u => (
                <button
                  key={u}
                  onClick={() => setQuantityUnit(u)}
                  style={{
                    padding: '9px 12px', borderRadius: 9, cursor: 'pointer', fontSize: 12, fontWeight: 700,
                    background: quantityUnit === u ? 'rgba(217,119,6,0.12)' : 'rgba(255,255,255,0.75)',
                    border: `1px solid ${quantityUnit === u ? 'rgba(217,119,6,0.4)' : 'rgba(148,163,184,0.22)'}`,
                    color: quantityUnit === u ? '#d97706' : '#64748b',
                  }}
                >
                  {u}
                </button>
              ))}
            </div>
          </div>
          {/* Visual estimate */}
          <div style={{ marginTop: 8, padding: '6px 10px', background: 'rgba(217,119,6,0.06)', border: '1px solid rgba(217,119,6,0.15)', borderRadius: 7, display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ fontSize: 10, color: '#64748b' }}>Estimated timeline</span>
            <span style={{ fontSize: 10, color: '#d97706', fontWeight: 700 }}>
              ~{Math.min(12, Math.max(3, (
                { gravy: 9, 'semi-dry': 7, dry: 5, saute: 4, boil: 8, fry: 5, steam: 6 }[recipeType] +
                Math.max(0, Math.floor((quantity - 400) / 200))
              )))} minutes
            </span>
          </div>
        </section>

        {/* ── Consistency ── */}
        <section>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
            <span style={{ width: 4, height: 13, borderRadius: 2, background: '#0891b2', display: 'inline-block' }} />
            <span style={{ fontSize: 11, color: '#64748b', fontWeight: 600, letterSpacing: 0.5 }}>CONSISTENCY</span>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            {CONSISTENCIES.map(c => {
              const active = consistency === c.value;
              return (
                <button
                  key={c.value}
                  onClick={() => setConsistency(c.value)}
                  style={{
                    flex: 1, padding: '10px 6px', borderRadius: 11, cursor: 'pointer',
                    background: active ? 'rgba(8,145,178,0.08)' : 'rgba(255,255,255,0.75)',
                    border: `${active ? 2 : 1}px solid ${active ? 'rgba(8,145,178,0.45)' : 'rgba(148,163,184,0.22)'}`,
                    display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 3,
                    transition: 'all 0.15s',
                  }}
                >
                  {/* Viscosity visualiser */}
                  <div style={{ display: 'flex', alignItems: 'flex-end', gap: 1, height: 20 }}>
                    {[1, 2, 3].map(bar => {
                      const filled = bar <= (c.value === 'thin' ? 1 : c.value === 'medium' ? 2 : 3);
                      return (
                        <div key={bar} style={{
                          width: 6, borderRadius: '3px 3px 0 0',
                          height: 6 * bar,
                          background: filled
                            ? (active ? '#0891b2' : 'rgba(8,145,178,0.35)')
                            : 'rgba(148,163,184,0.2)',
                        }} />
                      );
                    })}
                  </div>
                  <span style={{ fontSize: 11, color: active ? '#0891b2' : '#64748b', fontWeight: 600 }}>{c.label}</span>
                  <span style={{ fontSize: 9, color: active ? 'rgba(8,145,178,0.7)' : '#94a3b8' }}>{c.desc}</span>
                </button>
              );
            })}
          </div>
        </section>

        {/* ── Ingredients ── */}
        <section>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 10 }}>
            <span style={{ width: 4, height: 13, borderRadius: 2, background: '#ea580c', display: 'inline-block' }} />
            <span style={{ fontSize: 11, color: '#64748b', fontWeight: 600, letterSpacing: 0.5, flex: 1 }}>INGREDIENTS</span>
            <span style={{ fontSize: 9, color: '#94a3b8' }}>{ingredients.length} items</span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {ingredients.map((ing, i) => (
              <IngredientRow
                key={ing.id}
                ing={ing}
                onUpdate={updated => updateIngredient(i, updated)}
                onRemove={() => removeIngredient(i)}
              />
            ))}
          </div>

          <button
            onClick={addIngredient}
            style={{
              width: '100%', marginTop: 8, padding: '10px 0', borderRadius: 10, cursor: 'pointer',
              background: 'rgba(234,88,12,0.05)', border: '1px dashed rgba(234,88,12,0.3)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
              color: '#ea580c', fontSize: 12, fontWeight: 600,
            }}
          >
            <Plus size={13} /> Add Ingredient
          </button>
        </section>

        {/* ── Summary preview ── */}
        <div style={{
          background: 'linear-gradient(135deg, rgba(219,234,254,0.8), rgba(239,246,255,0.9))',
          border: '1px solid rgba(147,197,253,0.4)',
          borderRadius: 14, padding: '14px 16px',
          boxShadow: '0 2px 10px rgba(37,99,235,0.08)',
        }}>
          <p style={{ fontSize: 9, color: '#2563eb', fontWeight: 600, margin: '0 0 10px', letterSpacing: 0.5 }}>RECIPE PREVIEW</p>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            {[
              { label: 'Dish', value: name, color: '#0f172a' },
              { label: 'Diet', value: `${dtColor.emoji} ${dishType}`, color: dtColor.color },
              { label: 'Type', value: recipeType, color: '#7c3aed' },
              { label: 'Quantity', value: `${quantity}${quantityUnit}`, color: '#d97706' },
              { label: 'Consistency', value: consistency, color: '#0891b2' },
              { label: 'Ingredients', value: `${ingredients.length} items`, color: '#ea580c' },
            ].map(item => (
              <div key={item.label}>
                <div style={{ fontSize: 9, color: '#94a3b8', marginBottom: 2 }}>{item.label}</div>
                <div style={{ fontSize: 12, color: item.color, fontWeight: 700, textTransform: 'capitalize' }}>{item.value}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* ── Sticky CTA ── */}
      <div style={{
        position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 20,
        padding: '12px 16px',
        background: 'linear-gradient(to top, rgba(240,244,255,1) 70%, transparent)',
        display: 'flex', flexDirection: 'column', gap: 8,
      }}>
        <button
          onClick={handleOpenEditor}
          style={{
            width: '100%', height: 52, borderRadius: 14, cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 9,
            background: 'linear-gradient(135deg, #1e40af, #2563eb)',
            border: '1px solid rgba(37,99,235,0.4)',
            boxShadow: '0 4px 20px rgba(37,99,235,0.28)',
          }}
        >
          <Play size={16} color="#fff" />
          <span style={{ fontSize: 14, color: '#fff', fontWeight: 800 }}>Open Pro Timeline Editor</span>
        </button>
      </div>
    </div>
  );
}
