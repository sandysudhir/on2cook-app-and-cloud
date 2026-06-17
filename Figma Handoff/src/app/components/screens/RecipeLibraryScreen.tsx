import { Recipe, getRecipeTotalDuration, formatDuration } from '../../types';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import {
  Search,
  Heart,
  PlayCircle,
  Pencil,
  Trash2,
  Copy,
  ChefHat,
  Clock,
  Layers,
  Star,
  Plus,
  Zap,
  Waves,
  Droplets,
  RotateCcw,
} from 'lucide-react';
import { useApp } from '../../AppContext';

const CATEGORY_COLORS: Record<string, string> = {
  Italian: '#f97316',
  Breakfast: '#fbbf24',
  Soups: '#22d3ee',
  Beverages: '#c084fc',
  Custom: '#60a5fa',
  Default: '#94a3b8',
};

function getCategoryColor(cat?: string) {
  return CATEGORY_COLORS[cat ?? 'Default'] ?? CATEGORY_COLORS.Default;
}

function ModuleDot({ color, enabled }: { color: string; enabled: boolean }) {
  return (
    <div
      style={{
        width: 7,
        height: 7,
        borderRadius: '50%',
        background: enabled ? color : 'transparent',
        border: `1px solid ${enabled ? color : 'rgba(148,163,184,0.3)'}`,
      }}
    />
  );
}

function RecipeCard({
  recipe,
  onStart,
  onEdit,
  onDuplicate,
  onDelete,
  onFavorite,
}: {
  recipe: Recipe;
  onStart: () => void;
  onEdit: () => void;
  onDuplicate: () => void;
  onDelete: () => void;
  onFavorite: () => void;
}) {
  const totalDuration = getRecipeTotalDuration(recipe);
  const catColor = getCategoryColor(recipe.category);
  const [showActions, setShowActions] = useState(false);

  const hasMW = recipe.steps.some((s) => s.microwave.enabled);
  const hasInd = recipe.steps.some((s) => s.induction.enabled);
  const hasStr = recipe.steps.some((s) => s.stirrer.enabled);
  const hasWat = recipe.steps.some((s) => s.water.enabled);

  return (
    <div
      className="rounded-2xl overflow-hidden mb-3"
      style={{
        background: 'rgba(255,255,255,0.85)',
        border: '1px solid rgba(148,163,184,0.2)',
        boxShadow: '0 2px 8px rgba(0,0,0,0.05)',
      }}
    >
      <div className="flex items-center gap-3 px-4 py-3.5">
        {/* Icon */}
        <div
          className="w-11 h-11 rounded-xl flex items-center justify-center flex-shrink-0"
          style={{
            background: `${catColor}18`,
            border: `1px solid ${catColor}30`,
          }}
        >
          <ChefHat size={20} color={catColor} />
        </div>

        {/* Info */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <p
              className="truncate"
              style={{ fontSize: '14px', color: '#0f172a', fontWeight: 600 }}
            >
              {recipe.name}
            </p>
            {recipe.isFavorite && <Star size={12} color="#fbbf24" fill="#fbbf24" />}
          </div>
          <div className="flex items-center gap-3 mt-1">
            <div className="flex items-center gap-1">
              <Layers size={10} color="#64748b" />
              <span style={{ fontSize: '11px', color: '#64748b' }}>{recipe.steps.length} steps</span>
            </div>
            <div className="flex items-center gap-1">
              <Clock size={10} color="#64748b" />
              <span style={{ fontSize: '11px', color: '#64748b' }}>{formatDuration(totalDuration)}</span>
            </div>
            {recipe.category && (
              <span
                style={{
                  fontSize: '9px',
                  color: catColor,
                  background: `${catColor}15`,
                  border: `1px solid ${catColor}30`,
                  padding: '1px 6px',
                  borderRadius: 6,
                }}
              >
                {recipe.category}
              </span>
            )}
          </div>
          {/* Module dots */}
          <div className="flex items-center gap-1.5 mt-2">
            <span style={{ fontSize: '9px', color: '#64748b' }}>Modules:</span>
            <ModuleDot color="#fbbf24" enabled={hasMW} />
            <ModuleDot color="#f87171" enabled={hasInd} />
            <ModuleDot color="#c084fc" enabled={hasStr} />
            <ModuleDot color="#22d3ee" enabled={hasWat} />
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center gap-2 flex-shrink-0">
          <button
            onClick={onFavorite}
            className="w-8 h-8 rounded-xl flex items-center justify-center active:scale-90 transition-transform"
            style={{
              background: recipe.isFavorite ? 'rgba(251,191,36,0.12)' : 'rgba(148,163,184,0.1)',
              border: `1px solid ${recipe.isFavorite ? 'rgba(251,191,36,0.35)' : 'rgba(148,163,184,0.2)'}`,
            }}
          >
            <Heart
              size={14}
              color={recipe.isFavorite ? '#fbbf24' : '#94a3b8'}
              fill={recipe.isFavorite ? '#fbbf24' : 'transparent'}
            />
          </button>
          <button
            onClick={onStart}
            className="w-8 h-8 rounded-xl flex items-center justify-center active:scale-90 transition-transform"
            style={{
              background: 'rgba(37,99,235,0.1)',
              border: '1px solid rgba(37,99,235,0.25)',
            }}
          >
            <PlayCircle size={16} color="#2563eb" />
          </button>
          <button
            onClick={() => setShowActions((p) => !p)}
            className="w-8 h-8 rounded-xl flex items-center justify-center active:scale-90 transition-transform"
            style={{
              background: 'rgba(148,163,184,0.1)',
              border: '1px solid rgba(148,163,184,0.2)',
            }}
          >
            <span style={{ fontSize: '16px', color: '#64748b', lineHeight: 1 }}>⋯</span>
          </button>
        </div>
      </div>

      {/* Expanded actions */}
      {showActions && (
        <div
          className="flex gap-2 px-4 pb-3"
          style={{ borderTop: '1px solid rgba(148,163,184,0.15)' }}
        >
          <button
            onClick={() => { onEdit(); setShowActions(false); }}
            className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl active:scale-95 transition-transform"
            style={{ background: 'rgba(37,99,235,0.07)', border: '1px solid rgba(37,99,235,0.18)' }}
          >
            <Pencil size={12} color="#2563eb" />
            <span style={{ fontSize: '12px', color: '#2563eb' }}>Edit</span>
          </button>
          <button
            onClick={() => { onDuplicate(); setShowActions(false); }}
            className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl active:scale-95 transition-transform"
            style={{ background: 'rgba(148,163,184,0.08)', border: '1px solid rgba(148,163,184,0.2)' }}
          >
            <Copy size={12} color="#64748b" />
            <span style={{ fontSize: '12px', color: '#64748b' }}>Duplicate</span>
          </button>
          <button
            onClick={() => { onDelete(); setShowActions(false); }}
            className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl active:scale-95 transition-transform"
            style={{ background: 'rgba(248,113,113,0.08)', border: '1px solid rgba(248,113,113,0.2)' }}
          >
            <Trash2 size={12} color="#f87171" />
            <span style={{ fontSize: '12px', color: '#f87171' }}>Delete</span>
          </button>
        </div>
      )}
    </div>
  );
}

export function RecipeLibraryScreen() {
  const navigate = useNavigate();
  const { recipes, setCurrentRecipe, setCookingState, toggleFavorite, deleteRecipe, saveRecipeToLibrary, startNewRecipe } = useApp();
  const [query, setQuery] = useState('');

  const filtered = recipes.filter(
    (r) =>
      r.name.toLowerCase().includes(query.toLowerCase()) ||
      (r.category ?? '').toLowerCase().includes(query.toLowerCase())
  );

  const favorites = filtered.filter((r) => r.isFavorite);
  const all = filtered;

  function handleEdit(recipe: Recipe) {
    setCurrentRecipe(recipe);
    navigate('/create');
  }

  function handleStart(recipe: Recipe) {
    setCookingState({ recipe, isActive: true, isPaused: false });
    navigate('/cooking');
  }

  function handleDuplicate(recipe: Recipe) {
    const newId = Date.now().toString();
    const dup: Recipe = {
      ...recipe,
      id: newId,
      name: `${recipe.name} (Copy)`,
      createdAt: new Date().toISOString().split('T')[0],
      lastUsed: undefined,
      isFavorite: false,
      steps: recipe.steps.map((s, i) => ({ ...s, id: `${newId}-step-${i}` })),
    };
    saveRecipeToLibrary(dup);
  }

  function handleCreate() {
    startNewRecipe();
    navigate('/create');
  }

  return (
    <div
      className="flex flex-col min-h-screen px-5 pt-14 pb-4"
      style={{ background: 'linear-gradient(160deg, #f0f4ff 0%, #f7f9ff 60%, #fafbff 100%)' }}
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 style={{ fontSize: '24px', color: '#0f172a', fontWeight: 700 }}>My Recipes</h1>
          <p style={{ fontSize: '13px', color: '#64748b', marginTop: 2 }}>
            {recipes.length} recipes saved
          </p>
        </div>
        <button
          onClick={handleCreate}
          className="w-10 h-10 rounded-xl flex items-center justify-center active:scale-90 transition-transform"
          style={{
            background: 'linear-gradient(135deg, #1e40af, #3b82f6)',
            boxShadow: '0 4px 16px rgba(59,130,246,0.35)',
          }}
        >
          <Plus size={18} color="#fff" />
        </button>
      </div>

      {/* Search */}
      <div
        className="flex items-center gap-2 px-4 py-3 rounded-xl mb-5"
        style={{
          background: 'rgba(255,255,255,0.85)',
          border: '1px solid rgba(148,163,184,0.22)',
          boxShadow: '0 1px 4px rgba(0,0,0,0.05)',
        }}
      >
        <Search size={16} color="#94a3b8" />
        <input
          type="text"
          placeholder="Search recipes..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="flex-1 bg-transparent outline-none"
          style={{
            fontSize: '14px',
            color: '#0f172a',
            fontFamily: "'Space Grotesk', sans-serif",
          }}
        />
        {query && (
          <button onClick={() => setQuery('')}>
            <span style={{ fontSize: '14px', color: '#94a3b8' }}>×</span>
          </button>
        )}
      </div>

      {/* Module legend */}
      <div
        className="rounded-xl px-4 py-3 mb-5 flex items-center gap-4"
        style={{
          background: 'rgba(255,255,255,0.7)',
          border: '1px solid rgba(148,163,184,0.18)',
        }}
      >
        <span style={{ fontSize: '10px', color: '#64748b', fontWeight: 600 }}>MODULE DOTS:</span>
        {[
          { icon: Zap, color: '#fbbf24', label: 'MW' },
          { icon: Waves, color: '#f87171', label: 'IH' },
          { icon: RotateCcw, color: '#c084fc', label: 'ST' },
          { icon: Droplets, color: '#22d3ee', label: 'W' },
        ].map(({ icon: Icon, color, label }) => (
          <div key={label} className="flex items-center gap-1">
            <div style={{ width: 7, height: 7, borderRadius: '50%', background: color }} />
            <span style={{ fontSize: '10px', color: '#64748b' }}>{label}</span>
          </div>
        ))}
      </div>

      {/* Favorites */}
      {favorites.length > 0 && !query && (
        <div className="mb-5">
          <h2 style={{ fontSize: '14px', color: '#0f172a', fontWeight: 700, marginBottom: 12 }}>
            ⭐ Favourites
          </h2>
          {favorites.map((recipe) => (
            <RecipeCard
              key={recipe.id}
              recipe={recipe}
              onStart={() => handleStart(recipe)}
              onEdit={() => handleEdit(recipe)}
              onDuplicate={() => handleDuplicate(recipe)}
              onDelete={() => deleteRecipe(recipe.id)}
              onFavorite={() => toggleFavorite(recipe.id)}
            />
          ))}
        </div>
      )}

      {/* All recipes */}
      <div>
        <h2 style={{ fontSize: '14px', color: '#0f172a', fontWeight: 700, marginBottom: 12 }}>
          {query ? `Results (${filtered.length})` : 'All Recipes'}
        </h2>
        {all.length === 0 ? (
          <div
            className="rounded-2xl py-12 flex flex-col items-center gap-3"
            style={{
              background: 'rgba(255,255,255,0.7)',
              border: '1px dashed rgba(148,163,184,0.3)',
            }}
          >
            <ChefHat size={32} color="#94a3b8" />
            <p style={{ fontSize: '14px', color: '#64748b' }}>
              {query ? 'No recipes found' : 'No recipes yet'}
            </p>
            <button
              onClick={handleCreate}
              className="px-4 py-2 rounded-xl active:scale-95 transition-transform"
              style={{
                background: 'rgba(37,99,235,0.1)',
                border: '1px solid rgba(37,99,235,0.25)',
                fontSize: '13px',
                color: '#2563eb',
              }}
            >
              + Create your first recipe
            </button>
          </div>
        ) : (
          all.map((recipe) => (
            <RecipeCard
              key={recipe.id}
              recipe={recipe}
              onStart={() => handleStart(recipe)}
              onEdit={() => handleEdit(recipe)}
              onDuplicate={() => handleDuplicate(recipe)}
              onDelete={() => deleteRecipe(recipe.id)}
              onFavorite={() => toggleFavorite(recipe.id)}
            />
          ))
        )}
      </div>
    </div>
  );
}
