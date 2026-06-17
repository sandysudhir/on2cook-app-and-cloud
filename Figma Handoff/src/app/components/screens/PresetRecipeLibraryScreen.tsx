import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router';
import {
  Search, X, ChevronRight, Leaf, Flame, Star, Clock,
  Lock, ShoppingBag, ChevronDown, Filter, Package,
} from 'lucide-react';
import { useApp } from '../../AppContext';
import { PRESET_RECIPES, PresetRecipeData } from '../../presetRecipes';
import { ProRecipeType, VeganType } from '../../proTypes';

// ─── Pack definitions ─────────────────────────────────────────────────────────
const STARTER_PACK_IDS = new Set([
  'dal-tadka', 'khichdi', 'palak-paneer', 'pasta-arrabiata', 'rajma-masala',
]);

// Recipe id → pack name
const SPECIAL_PACK_MAP: Record<string, string> = {
  'kadai-chicken':    'North Indian Pack',
  'butter-chicken':   'North Indian Pack',
  'dum-biryani':      'Mughlai Pack',
  'fish-curry':       'Coastal Cuisine Pack',
  'mushroom-stir-fry': 'Asian Fusion Pack',
};

// Locked teaser packs (not yet in preset data)
const LOCKED_PACKS = [
  {
    id: 'thai-cuisine',
    name: 'Thai Cuisine Pack',
    desc: '12 authentic Thai recipes — Pad Thai, Green Curry, Tom Yum & more',
    color: '#16a34a',
    recipeCount: 12,
    price: '₹299',
  },
  {
    id: 'mediterranean',
    name: 'Mediterranean Pack',
    desc: 'Hummus, Falafel, Shawarma, and coastal Mediterranean classics',
    color: '#0891b2',
    recipeCount: 8,
    price: '₹249',
  },
  {
    id: 'chinese',
    name: 'Chinese Cuisine Pack',
    desc: 'Fried rice, Manchurian, Noodles & dim sum favourites',
    color: '#dc2626',
    recipeCount: 10,
    price: '₹199',
  },
];

const DISH_TYPE_COLORS: Record<VeganType, { text: string; bg: string; border: string }> = {
  'non-veg': { text: '#dc2626', bg: 'rgba(220,38,38,0.09)', border: 'rgba(220,38,38,0.25)' },
  'veg':     { text: '#16a34a', bg: 'rgba(22,163,74,0.09)',  border: 'rgba(22,163,74,0.25)' },
  'vegan':   { text: '#15803d', bg: 'rgba(21,128,61,0.09)',  border: 'rgba(21,128,61,0.25)' },
};

const TYPE_COLORS: Record<ProRecipeType, string> = {
  gravy: '#2563eb', 'semi-dry': '#7c3aed', dry: '#d97706',
  saute: '#059669', boil: '#0891b2', fry: '#ea580c', steam: '#0e7490',
};

const COMPLEXITY_COLORS = {
  easy: '#16a34a', medium: '#d97706', hard: '#dc2626',
};

// ─── Filter state type ────────────────────────────────────────────────────────
interface FilterState {
  dietType: '' | VeganType;
  consistency: '' | 'thin' | 'medium' | 'thick';
  recipeType: '' | ProRecipeType;
  complexity: '' | 'easy' | 'medium' | 'hard';
}

function RecipeCard({
  recipe,
  onSelect,
  isLocked,
  packName,
}: {
  recipe: PresetRecipeData;
  onSelect: () => void;
  isLocked: boolean;
  packName?: string;
}) {
  const dtColor = DISH_TYPE_COLORS[recipe.dishType];
  const typeColor = TYPE_COLORS[recipe.recipeType] ?? '#2563eb';
  const complexColor = COMPLEXITY_COLORS[recipe.complexity];

  return (
    <button
      onClick={isLocked ? undefined : onSelect}
      style={{
        width: '100%', textAlign: 'left',
        background: isLocked ? 'rgba(148,163,184,0.08)' : 'rgba(255,255,255,0.88)',
        border: `1px solid ${isLocked ? 'rgba(148,163,184,0.2)' : 'rgba(148,163,184,0.2)'}`,
        borderRadius: 16, overflow: 'hidden', cursor: isLocked ? 'default' : 'pointer',
        display: 'flex', flexDirection: 'column',
        transition: 'border-color 0.15s',
        boxShadow: '0 2px 10px rgba(0,0,0,0.06)',
        opacity: isLocked ? 0.85 : 1,
      }}
    >
      {/* Thumbnail */}
      <div style={{ position: 'relative', height: 130, overflow: 'hidden', flexShrink: 0 }}>
        <img
          src={recipe.imageUrl}
          alt={recipe.name}
          style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block', filter: isLocked ? 'grayscale(30%) brightness(0.85)' : 'none' }}
        />
        <div style={{
          position: 'absolute', inset: 0,
          background: isLocked
            ? 'linear-gradient(to top, rgba(0,0,0,0.75) 0%, rgba(0,0,0,0.25) 55%, rgba(0,0,0,0.1) 100%)'
            : 'linear-gradient(to top, rgba(0,0,0,0.65) 0%, rgba(0,0,0,0.1) 55%, transparent 100%)',
        }} />

        {/* Pack badge */}
        {packName && (
          <div style={{ position: 'absolute', top: 10, right: 10 }}>
            <span style={{ fontSize: 8, fontWeight: 700, color: '#fff', background: 'rgba(100,116,139,0.75)', borderRadius: 5, padding: '2px 7px', backdropFilter: 'blur(8px)' }}>
              {packName}
            </span>
          </div>
        )}

        {/* Top badges */}
        <div style={{ position: 'absolute', top: 10, left: 10, display: 'flex', gap: 5 }}>
          <span style={{ fontSize: 9, fontWeight: 700, color: dtColor.text, border: `1px solid ${dtColor.border}`, borderRadius: 5, padding: '2px 6px', backdropFilter: 'blur(8px)', background: 'rgba(255,255,255,0.85)' }}>
            {recipe.dishType === 'non-veg' ? '🍗' : recipe.dishType === 'veg' ? '🥦' : '🌱'} {recipe.dishType}
          </span>
          <span style={{ fontSize: 9, fontWeight: 700, color: typeColor, background: 'rgba(255,255,255,0.85)', border: `1px solid ${typeColor}40`, borderRadius: 5, padding: '2px 6px', backdropFilter: 'blur(8px)', textTransform: 'uppercase' }}>
            {recipe.recipeType}
          </span>
        </div>

        {/* Name bottom-left */}
        <div style={{ position: 'absolute', bottom: 10, left: 12 }}>
          <div style={{ fontSize: 11, color: '#fff', fontWeight: 700 }}>{recipe.name}</div>
          <div style={{ fontSize: 9, color: 'rgba(255,255,255,0.8)', marginTop: 1 }}>{recipe.category}</div>
        </div>

        {/* Lock overlay or arrow button */}
        {isLocked ? (
          <div style={{
            position: 'absolute', bottom: 10, right: 10,
            width: 32, height: 32, borderRadius: 9,
            background: 'rgba(100,116,139,0.7)', border: '1px solid rgba(255,255,255,0.3)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Lock size={14} color="#fff" />
          </div>
        ) : (
          <div style={{
            position: 'absolute', bottom: 10, right: 10,
            width: 28, height: 28, borderRadius: 8,
            background: 'rgba(255,255,255,0.9)', border: '1px solid rgba(255,255,255,0.6)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <ChevronRight size={14} color="#1e293b" />
          </div>
        )}
      </div>

      {/* Info row */}
      <div style={{ padding: '10px 12px', display: 'flex', alignItems: 'center', gap: 10 }}>
        <p style={{ flex: 1, fontSize: 10, color: '#64748b', margin: 0, lineHeight: 1.4 }}>
          {recipe.shortDesc}
        </p>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, flexShrink: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <Clock size={10} color="#94a3b8" />
            <span style={{ fontSize: 9, color: '#94a3b8' }}>{recipe.tentativeMinutes} min</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
            <span style={{ fontSize: 9, color: complexColor, background: `${complexColor}12`, border: `1px solid ${complexColor}28`, borderRadius: 4, padding: '1px 5px', fontWeight: 600 }}>
              {recipe.complexity}
            </span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <span style={{ fontSize: 9, color: '#94a3b8' }}>Serves {recipe.serves}</span>
          </div>
        </div>
      </div>

      {/* Purchase CTA if locked */}
      {isLocked && (
        <div style={{ padding: '0 12px 12px' }}>
          <button
            onClick={e => { e.stopPropagation(); alert(`Purchase ${packName} to unlock this recipe.`); }}
            style={{
              width: '100%', padding: '8px 0', borderRadius: 8, cursor: 'pointer',
              background: 'linear-gradient(135deg, #1e40af, #3b82f6)',
              border: 'none', color: '#fff', fontSize: 11, fontWeight: 700,
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5,
            }}
          >
            <ShoppingBag size={12} /> Purchase Pack to Unlock
          </button>
        </div>
      )}
    </button>
  );
}

function LockedPackCard({ pack }: { pack: typeof LOCKED_PACKS[0] }) {
  return (
    <div style={{
      background: 'rgba(255,255,255,0.75)',
      border: '1px solid rgba(148,163,184,0.2)',
      borderRadius: 16, padding: '14px',
      boxShadow: '0 2px 8px rgba(0,0,0,0.05)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 10 }}>
        <div style={{ width: 44, height: 44, borderRadius: 13, background: `${pack.color}14`, border: `1px solid ${pack.color}30`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
          <Package size={20} color={pack.color} />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 2 }}>
            <span style={{ fontSize: 13, color: '#0f172a', fontWeight: 700 }}>{pack.name}</span>
            <Lock size={11} color="#94a3b8" />
          </div>
          <span style={{ fontSize: 10, color: '#64748b' }}>{pack.recipeCount} recipes · {pack.price}</span>
        </div>
      </div>
      <p style={{ fontSize: 11, color: '#64748b', margin: '0 0 10px', lineHeight: 1.5 }}>{pack.desc}</p>
      <button
        style={{
          width: '100%', padding: '10px 0', borderRadius: 10, cursor: 'pointer',
          background: `${pack.color}10`, border: `1px solid ${pack.color}30`,
          color: pack.color, fontSize: 12, fontWeight: 700,
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
        }}
      >
        <ShoppingBag size={13} /> Purchase — {pack.price}
      </button>
    </div>
  );
}

export function PresetRecipeLibraryScreen() {
  const navigate = useNavigate();
  const { setPresetSetup } = useApp();
  const [query, setQuery] = useState('');
  const [showFilterPanel, setShowFilterPanel] = useState(false);
  const [activePackTab, setActivePackTab] = useState<'starter' | 'special'>('starter');
  const [filters, setFilters] = useState<FilterState>({
    dietType: '', consistency: '', recipeType: '', complexity: '',
  });

  const hasActiveFilter = Object.values(filters).some(v => v !== '');

  const clearFilters = () => setFilters({ dietType: '', consistency: '', recipeType: '', complexity: '' });

  const starterRecipes = PRESET_RECIPES.filter(r => STARTER_PACK_IDS.has(r.id));
  const specialRecipes = PRESET_RECIPES.filter(r => !STARTER_PACK_IDS.has(r.id));

  const applyFilters = (list: PresetRecipeData[]) => {
    let result = list;
    if (query.trim()) {
      const q = query.toLowerCase();
      result = result.filter(r =>
        r.name.toLowerCase().includes(q) ||
        r.category.toLowerCase().includes(q) ||
        r.shortDesc.toLowerCase().includes(q) ||
        r.dishType.includes(q) ||
        r.recipeType.includes(q),
      );
    }
    if (filters.dietType) result = result.filter(r => r.dishType === filters.dietType);
    if (filters.consistency) result = result.filter(r => r.consistency === filters.consistency);
    if (filters.recipeType) result = result.filter(r => r.recipeType === filters.recipeType);
    if (filters.complexity) result = result.filter(r => r.complexity === filters.complexity);
    return result;
  };

  const displayedStarter = applyFilters(starterRecipes);
  const displayedSpecial = applyFilters(specialRecipes);

  function handleSelect(recipe: PresetRecipeData) {
    setPresetSetup({
      presetId: recipe.id,
      name: recipe.name,
      dishType: recipe.dishType,
      recipeType: recipe.recipeType,
      quantity: recipe.quantity,
      quantityUnit: recipe.quantityUnit,
      consistency: recipe.consistency,
      healthRichRatio: recipe.healthRichRatio,
      ingredients: recipe.minutes
        .flatMap(m => m.ingredients)
        .filter((ing, idx, arr) => arr.findIndex(x => x.id === ing.id) === idx),
    });
    navigate('/preset-setup');
  }

  const DIET_OPTS: { label: string; value: FilterState['dietType'] }[] = [
    { label: 'All', value: '' },
    { label: '🥦 Veg', value: 'veg' },
    { label: '🍗 Non-Veg', value: 'non-veg' },
    { label: '🌱 Vegan', value: 'vegan' },
  ];
  const CONSISTENCY_OPTS: { label: string; value: FilterState['consistency'] }[] = [
    { label: 'All', value: '' },
    { label: 'Thin', value: 'thin' },
    { label: 'Medium', value: 'medium' },
    { label: 'Thick', value: 'thick' },
  ];
  const TYPE_OPTS: { label: string; value: FilterState['recipeType'] }[] = [
    { label: 'All', value: '' },
    { label: 'Gravy', value: 'gravy' },
    { label: 'Semi-Dry', value: 'semi-dry' },
    { label: 'Dry', value: 'dry' },
    { label: 'Sauté', value: 'saute' },
    { label: 'Boil', value: 'boil' },
    { label: 'Fry', value: 'fry' },
    { label: 'Steam', value: 'steam' },
  ];
  const COMPLEXITY_OPTS: { label: string; value: FilterState['complexity'] }[] = [
    { label: 'All', value: '' },
    { label: 'Easy', value: 'easy' },
    { label: 'Medium', value: 'medium' },
    { label: 'Hard', value: 'hard' },
  ];

  return (
    <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column', overflowY: 'auto', background: 'linear-gradient(160deg, #f0f4ff 0%, #f7f9ff 60%, #fafbff 100%)' }}>

      {/* ── Sticky Header ── */}
      <div style={{
        position: 'sticky', top: 0, zIndex: 10,
        background: 'rgba(245,247,255,0.97)', backdropFilter: 'blur(20px)',
        borderBottom: '1px solid rgba(148,163,184,0.18)',
        padding: '14px 16px 10px',
        flexShrink: 0,
        boxShadow: '0 1px 8px rgba(0,0,0,0.05)',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
          <button
            onClick={() => navigate('/')}
            style={{ background: 'rgba(255,255,255,0.8)', border: '1px solid rgba(148,163,184,0.25)', borderRadius: 8, padding: '5px 10px', cursor: 'pointer', color: '#64748b', fontSize: 12, boxShadow: '0 1px 3px rgba(0,0,0,0.06)' }}
          >
            ← Back
          </button>
          <div style={{ flex: 1 }}>
            <p style={{ fontSize: 10, color: '#94a3b8', margin: 0 }}>On2Cook Pro</p>
            <h1 style={{ fontSize: 17, color: '#0f172a', fontWeight: 800, margin: 0, letterSpacing: -0.4 }}>
              Select a Recipe
            </h1>
          </div>
          <div style={{
            width: 34, height: 34, borderRadius: 10, flexShrink: 0,
            background: 'linear-gradient(135deg, #1e40af, #3b82f6)',
            boxShadow: '0 4px 12px rgba(37,99,235,0.25)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Star size={15} color="#fff" />
          </div>
        </div>

        {/* Search + Filter row */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
          <div style={{
            flex: 1, display: 'flex', alignItems: 'center', gap: 8,
            background: 'rgba(255,255,255,0.85)', border: '1px solid rgba(148,163,184,0.25)',
            borderRadius: 10, padding: '8px 11px',
            boxShadow: '0 1px 4px rgba(0,0,0,0.05)',
          }}>
            <Search size={14} color="#94a3b8" />
            <input
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder="Search recipes, cuisine, type…"
              style={{
                flex: 1, background: 'none', border: 'none', outline: 'none',
                color: '#0f172a', fontSize: 13, caretColor: '#2563eb',
              }}
            />
            {query && (
              <button onClick={() => setQuery('')} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
                <X size={13} color="#94a3b8" />
              </button>
            )}
          </div>
          <button
            onClick={() => setShowFilterPanel(p => !p)}
            style={{
              display: 'flex', alignItems: 'center', gap: 5, padding: '8px 12px', borderRadius: 10, cursor: 'pointer',
              background: showFilterPanel || hasActiveFilter ? 'rgba(37,99,235,0.12)' : 'rgba(255,255,255,0.85)',
              border: showFilterPanel || hasActiveFilter ? '1px solid rgba(37,99,235,0.35)' : '1px solid rgba(148,163,184,0.25)',
              color: showFilterPanel || hasActiveFilter ? '#2563eb' : '#64748b',
              fontSize: 12, fontWeight: 600, flexShrink: 0,
              boxShadow: '0 1px 4px rgba(0,0,0,0.05)',
            }}
          >
            <Filter size={13} />
            Filter
            {hasActiveFilter && (
              <span style={{ width: 16, height: 16, borderRadius: '50%', background: '#2563eb', color: '#fff', fontSize: 9, fontWeight: 800, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                {Object.values(filters).filter(v => v !== '').length}
              </span>
            )}
            <ChevronDown size={12} style={{ transform: showFilterPanel ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }} />
          </button>
        </div>

        {/* Filter dropdown panel */}
        {showFilterPanel && (
          <div style={{
            background: 'rgba(255,255,255,0.97)', border: '1px solid rgba(148,163,184,0.22)',
            borderRadius: 12, padding: '12px', marginBottom: 4,
            boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
            display: 'flex', flexDirection: 'column', gap: 12,
          }}>
            {/* Diet Type */}
            <div>
              <div style={{ fontSize: 10, color: '#64748b', fontWeight: 700, marginBottom: 6, letterSpacing: 0.4 }}>DIET TYPE</div>
              <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap' }}>
                {DIET_OPTS.map(o => (
                  <button key={o.value} onClick={() => setFilters(f => ({ ...f, dietType: o.value }))}
                    style={{ padding: '5px 10px', borderRadius: 7, cursor: 'pointer', fontSize: 11, fontWeight: 600, border: 'none', background: filters.dietType === o.value ? 'rgba(37,99,235,0.12)' : 'rgba(148,163,184,0.1)', color: filters.dietType === o.value ? '#2563eb' : '#475569' }}>
                    {o.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Gravy Consistency */}
            <div>
              <div style={{ fontSize: 10, color: '#64748b', fontWeight: 700, marginBottom: 6, letterSpacing: 0.4 }}>GRAVY CONSISTENCY</div>
              <div style={{ display: 'flex', gap: 5 }}>
                {CONSISTENCY_OPTS.map(o => (
                  <button key={o.value} onClick={() => setFilters(f => ({ ...f, consistency: o.value }))}
                    style={{ flex: 1, padding: '5px 0', borderRadius: 7, cursor: 'pointer', fontSize: 11, fontWeight: 600, border: 'none', background: filters.consistency === o.value ? 'rgba(8,145,178,0.12)' : 'rgba(148,163,184,0.1)', color: filters.consistency === o.value ? '#0891b2' : '#475569' }}>
                    {o.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Recipe Type */}
            <div>
              <div style={{ fontSize: 10, color: '#64748b', fontWeight: 700, marginBottom: 6, letterSpacing: 0.4 }}>RECIPE TYPE</div>
              <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap' }}>
                {TYPE_OPTS.map(o => (
                  <button key={o.value} onClick={() => setFilters(f => ({ ...f, recipeType: o.value }))}
                    style={{ padding: '5px 10px', borderRadius: 7, cursor: 'pointer', fontSize: 11, fontWeight: 600, border: 'none', background: filters.recipeType === o.value ? 'rgba(124,58,237,0.12)' : 'rgba(148,163,184,0.1)', color: filters.recipeType === o.value ? '#7c3aed' : '#475569' }}>
                    {o.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Complexity */}
            <div>
              <div style={{ fontSize: 10, color: '#64748b', fontWeight: 700, marginBottom: 6, letterSpacing: 0.4 }}>COMPLEXITY</div>
              <div style={{ display: 'flex', gap: 5 }}>
                {COMPLEXITY_OPTS.map(o => (
                  <button key={o.value} onClick={() => setFilters(f => ({ ...f, complexity: o.value }))}
                    style={{ flex: 1, padding: '5px 0', borderRadius: 7, cursor: 'pointer', fontSize: 11, fontWeight: 600, border: 'none', background: filters.complexity === o.value ? 'rgba(217,119,6,0.12)' : 'rgba(148,163,184,0.1)', color: filters.complexity === o.value ? '#d97706' : '#475569' }}>
                    {o.label}
                  </button>
                ))}
              </div>
            </div>

            {hasActiveFilter && (
              <button onClick={clearFilters} style={{ padding: '7px 0', borderRadius: 8, cursor: 'pointer', fontSize: 11, fontWeight: 700, border: 'none', background: 'rgba(239,68,68,0.08)', color: '#dc2626' }}>
                Clear All Filters
              </button>
            )}
          </div>
        )}

        {/* Pack tabs */}
        <div style={{ display: 'flex', gap: 5, background: 'rgba(255,255,255,0.6)', borderRadius: 10, padding: 3 }}>
          {([
            { key: 'starter', label: '🎁 Starter Pack', subtitle: 'Free' },
            { key: 'special', label: '👑 Special Packs', subtitle: 'Premium' },
          ] as const).map(tab => (
            <button
              key={tab.key}
              onClick={() => setActivePackTab(tab.key)}
              style={{
                flex: 1, padding: '7px 8px', borderRadius: 8, cursor: 'pointer', border: 'none',
                background: activePackTab === tab.key ? '#fff' : 'transparent',
                boxShadow: activePackTab === tab.key ? '0 1px 6px rgba(0,0,0,0.1)' : 'none',
                display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1,
                transition: 'all 0.15s',
              }}
            >
              <span style={{ fontSize: 11, fontWeight: 700, color: activePackTab === tab.key ? '#0f172a' : '#64748b' }}>{tab.label}</span>
              <span style={{ fontSize: 9, color: activePackTab === tab.key ? (tab.key === 'starter' ? '#16a34a' : '#7c3aed') : '#94a3b8', fontWeight: 600 }}>{tab.subtitle}</span>
            </button>
          ))}
        </div>
      </div>

      {/* ── Recipe list ── */}
      <div style={{ padding: '14px 14px 32px', display: 'flex', flexDirection: 'column', gap: 12, flex: 1 }}>

        {activePackTab === 'starter' && (
          <>
            {/* Starter Pack banner */}
            <div style={{ background: 'linear-gradient(135deg, rgba(22,163,74,0.1), rgba(74,222,128,0.05))', border: '1px solid rgba(22,163,74,0.25)', borderRadius: 14, padding: '12px 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
              <div style={{ width: 36, height: 36, borderRadius: 10, background: 'rgba(22,163,74,0.12)', border: '1px solid rgba(22,163,74,0.25)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <Leaf size={16} color="#16a34a" />
              </div>
              <div>
                <div style={{ fontSize: 12, color: '#16a34a', fontWeight: 700 }}>Starter Pack — Free & Pre-Loaded</div>
                <div style={{ fontSize: 10, color: '#64748b', marginTop: 2 }}>General everyday recipes, accessible by default on your device</div>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ fontSize: 11, color: '#94a3b8' }}>
                {displayedStarter.length} recipe{displayedStarter.length !== 1 ? 's' : ''}
              </span>
              {hasActiveFilter && (
                <button onClick={clearFilters} style={{ fontSize: 10, color: '#2563eb', background: 'rgba(37,99,235,0.08)', border: '1px solid rgba(37,99,235,0.22)', borderRadius: 4, padding: '1px 6px', cursor: 'pointer' }}>
                  Clear filters
                </button>
              )}
            </div>

            {displayedStarter.length === 0 && (
              <div style={{ textAlign: 'center', paddingTop: 30 }}>
                <div style={{ fontSize: 28, marginBottom: 8 }}>🍳</div>
                <p style={{ fontSize: 13, color: '#94a3b8' }}>No recipes match your filters</p>
              </div>
            )}

            {displayedStarter.map(recipe => (
              <RecipeCard
                key={recipe.id}
                recipe={recipe}
                onSelect={() => handleSelect(recipe)}
                isLocked={false}
              />
            ))}
          </>
        )}

        {activePackTab === 'special' && (
          <>
            {/* Special Packs banner */}
            <div style={{ background: 'linear-gradient(135deg, rgba(124,58,237,0.1), rgba(167,139,250,0.05))', border: '1px solid rgba(124,58,237,0.25)', borderRadius: 14, padding: '12px 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
              <div style={{ width: 36, height: 36, borderRadius: 10, background: 'rgba(124,58,237,0.12)', border: '1px solid rgba(124,58,237,0.25)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <Flame size={16} color="#7c3aed" />
              </div>
              <div>
                <div style={{ fontSize: 12, color: '#7c3aed', fontWeight: 700 }}>Special Packs — Premium Recipes</div>
                <div style={{ fontSize: 10, color: '#64748b', marginTop: 2 }}>Curated cuisine packs — purchase to unlock all recipes in a pack</div>
              </div>
            </div>

            {/* Locked teaser packs */}
            <div style={{ fontSize: 11, color: '#64748b', fontWeight: 700, marginBottom: -4 }}>Coming Soon Packs</div>
            {LOCKED_PACKS.map(pack => (
              <LockedPackCard key={pack.id} pack={pack} />
            ))}

            {/* Already-unlocked special recipes (shown as locked in app context) */}
            <div style={{ fontSize: 11, color: '#64748b', fontWeight: 700, marginTop: 4 }}>Available Packs</div>

            {['North Indian Pack', 'Mughlai Pack', 'Coastal Cuisine Pack', 'Asian Fusion Pack'].map(packName => {
              const packRecipes = displayedSpecial.filter(r => SPECIAL_PACK_MAP[r.id] === packName);
              if (packRecipes.length === 0) return null;
              return (
                <div key={packName} style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                    <Lock size={11} color="#94a3b8" />
                    <span style={{ fontSize: 11, color: '#64748b', fontWeight: 700 }}>{packName}</span>
                    <span style={{ fontSize: 9, color: '#7c3aed', background: 'rgba(124,58,237,0.08)', border: '1px solid rgba(124,58,237,0.22)', borderRadius: 4, padding: '1px 6px', fontWeight: 700 }}>₹199</span>
                  </div>
                  {packRecipes.map(recipe => (
                    <RecipeCard
                      key={recipe.id}
                      recipe={recipe}
                      onSelect={() => handleSelect(recipe)}
                      isLocked={true}
                      packName={packName}
                    />
                  ))}
                </div>
              );
            })}

            {displayedSpecial.length === 0 && (
              <div style={{ textAlign: 'center', paddingTop: 20 }}>
                <p style={{ fontSize: 13, color: '#94a3b8' }}>No special recipes match your filters</p>
              </div>
            )}
          </>
        )}

        {/* Footer hint */}
        <div style={{ textAlign: 'center', paddingTop: 8 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, justifyContent: 'center' }}>
            <Leaf size={11} color="#16a34a" />
            <span style={{ fontSize: 10, color: '#94a3b8' }}>Pomodori Curated Recipes</span>
            <Flame size={11} color="#ea580c" />
          </div>
          <p style={{ fontSize: 9, color: '#cbd5e1', marginTop: 4 }}>
            Select any recipe to customise factors before opening the Pro Editor
          </p>
        </div>
      </div>
    </div>
  );
}
