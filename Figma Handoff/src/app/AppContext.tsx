import React, { createContext, useContext, useState } from 'react';
import { Recipe, RecipeStep, createDefaultStep } from './types';
import { ProRecipe, DEFAULT_PRO_RECIPE, ProIngredient, VeganType, ProRecipeType, ConsistencyType } from './proTypes';

// ─── Recipe Completion Data ────────────────────────────────────────────────────
export interface RecipeCompletionData {
  elapsedSec: number;
  totalPlannedSec: number;
  wasManual: boolean;
  completedAt: number;
}

// ─── Preset Setup Data (carried between library → setup → editor) ─────────────
export interface PresetSetupData {
  presetId: string;
  name: string;
  dishType: VeganType;
  recipeType: ProRecipeType;
  quantity: number;
  quantityUnit: string;
  consistency: ConsistencyType;
  healthRichRatio: number;
  ingredients: ProIngredient[];
}

const defaultRecipes: Recipe[] = [
  {
    id: '1',
    name: 'Creamy Pasta Sauce',
    isFavorite: true,
    createdAt: '2026-01-15',
    lastUsed: '2026-04-01',
    category: 'Italian',
    steps: [
      {
        id: 's1-1',
        microwave: { enabled: false, duration: 0, power: 80, startDelay: 0 },
        induction: { enabled: true, duration: 180, power: 60, startDelay: 0 },
        stirrer: { enabled: true, mode: 'continuous', direction: 'clockwise', speed: 2, duration: 180, pulseOn: 5, pulseOff: 3 },
        water: { enabled: false, dispenseType: 'single', amount: 0, triggerAt: 0, doses: 1, dosGap: 0 },
      },
      {
        id: 's1-2',
        microwave: { enabled: true, duration: 120, power: 70, startDelay: 30 },
        induction: { enabled: true, duration: 120, power: 40, startDelay: 0 },
        stirrer: { enabled: true, mode: 'pulse', direction: 'alternate', speed: 3, duration: 120, pulseOn: 10, pulseOff: 5 },
        water: { enabled: true, dispenseType: 'single', amount: 50, triggerAt: 15, doses: 1, dosGap: 0 },
      },
      {
        id: 's1-3',
        microwave: { enabled: false, duration: 0, power: 80, startDelay: 0 },
        induction: { enabled: true, duration: 60, power: 80, startDelay: 0 },
        stirrer: { enabled: true, mode: 'continuous', direction: 'clockwise', speed: 4, duration: 60, pulseOn: 5, pulseOff: 3 },
        water: { enabled: false, dispenseType: 'single', amount: 0, triggerAt: 0, doses: 1, dosGap: 0 },
      },
    ],
  },
  {
    id: '2',
    name: 'Oatmeal Delight',
    isFavorite: true,
    createdAt: '2026-01-10',
    lastUsed: '2026-03-28',
    category: 'Breakfast',
    steps: [
      {
        id: 's2-1',
        microwave: { enabled: true, duration: 90, power: 100, startDelay: 0 },
        induction: { enabled: false, duration: 0, power: 50, startDelay: 0 },
        stirrer: { enabled: true, mode: 'continuous', direction: 'clockwise', speed: 2, duration: 90, pulseOn: 5, pulseOff: 3 },
        water: { enabled: true, dispenseType: 'single', amount: 200, triggerAt: 0, doses: 1, dosGap: 0 },
      },
      {
        id: 's2-2',
        microwave: { enabled: true, duration: 60, power: 80, startDelay: 0 },
        induction: { enabled: false, duration: 0, power: 50, startDelay: 0 },
        stirrer: { enabled: true, mode: 'pulse', direction: 'clockwise', speed: 1, duration: 60, pulseOn: 5, pulseOff: 10 },
        water: { enabled: false, dispenseType: 'single', amount: 0, triggerAt: 0, doses: 1, dosGap: 0 },
      },
    ],
  },
  {
    id: '3',
    name: 'Scrambled Eggs',
    isFavorite: false,
    createdAt: '2026-01-12',
    lastUsed: undefined,
    category: 'Breakfast',
    steps: [
      {
        id: 's3-1',
        microwave: { enabled: false, duration: 0, power: 80, startDelay: 0 },
        induction: { enabled: true, duration: 120, power: 40, startDelay: 0 },
        stirrer: { enabled: true, mode: 'pulse', direction: 'alternate', speed: 2, duration: 120, pulseOn: 3, pulseOff: 5 },
        water: { enabled: false, dispenseType: 'single', amount: 0, triggerAt: 0, doses: 1, dosGap: 0 },
      },
    ],
  },
  {
    id: '4',
    name: 'Tomato Bisque',
    isFavorite: false,
    createdAt: '2026-01-08',
    lastUsed: '2026-03-15',
    category: 'Soups',
    steps: [
      {
        id: 's4-1',
        microwave: { enabled: false, duration: 0, power: 80, startDelay: 0 },
        induction: { enabled: true, duration: 240, power: 70, startDelay: 0 },
        stirrer: { enabled: true, mode: 'continuous', direction: 'clockwise', speed: 2, duration: 240, pulseOn: 5, pulseOff: 3 },
        water: { enabled: true, dispenseType: 'single', amount: 300, triggerAt: 0, doses: 1, dosGap: 0 },
      },
      {
        id: 's4-2',
        microwave: { enabled: true, duration: 120, power: 80, startDelay: 0 },
        induction: { enabled: true, duration: 120, power: 50, startDelay: 0 },
        stirrer: { enabled: true, mode: 'continuous', direction: 'clockwise', speed: 3, duration: 120, pulseOn: 5, pulseOff: 3 },
        water: { enabled: false, dispenseType: 'single', amount: 0, triggerAt: 0, doses: 1, dosGap: 0 },
      },
    ],
  },
  {
    id: '5',
    name: 'Spiced Chai Latte',
    isFavorite: false,
    createdAt: '2026-02-03',
    lastUsed: '2026-04-02',
    category: 'Beverages',
    steps: [
      {
        id: 's5-1',
        microwave: { enabled: false, duration: 0, power: 80, startDelay: 0 },
        induction: { enabled: true, duration: 150, power: 80, startDelay: 0 },
        stirrer: { enabled: true, mode: 'continuous', direction: 'clockwise', speed: 2, duration: 150, pulseOn: 5, pulseOff: 3 },
        water: { enabled: true, dispenseType: 'single', amount: 250, triggerAt: 5, doses: 1, dosGap: 0 },
      },
    ],
  },
];

interface CookingState {
  recipe: Recipe;
  isActive: boolean;
  isPaused: boolean;
}

interface AppContextType {
  recipes: Recipe[];
  setRecipes: React.Dispatch<React.SetStateAction<Recipe[]>>;
  currentRecipe: Recipe | null;
  setCurrentRecipe: React.Dispatch<React.SetStateAction<Recipe | null>>;
  cookingState: CookingState | null;
  setCookingState: React.Dispatch<React.SetStateAction<CookingState | null>>;
  saveRecipeToLibrary: (recipe: Recipe) => void;
  toggleFavorite: (id: string) => void;
  deleteRecipe: (id: string) => void;
  startNewRecipe: () => Recipe;
  proRecipe: ProRecipe | null;
  setProRecipe: React.Dispatch<React.SetStateAction<ProRecipe | null>>;
  startProRecipe: (recipe?: ProRecipe) => void;
  completionData: RecipeCompletionData | null;
  setCompletionData: React.Dispatch<React.SetStateAction<RecipeCompletionData | null>>;
  presetSetup: PresetSetupData | null;
  setPresetSetup: React.Dispatch<React.SetStateAction<PresetSetupData | null>>;
  savedProRecipes: ProRecipe[];
  saveProRecipeToLibrary: (recipe: ProRecipe) => void;
}

const AppContext = createContext<AppContextType | null>(null);

function readCloudBridgePayload(): Partial<PresetSetupData & { proRecipe: ProRecipe }> | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem('on2cook-pro-studio-recipe');
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function AppProvider({ children }: { children: React.ReactNode }) {
  const bridgePayload = readCloudBridgePayload();
  const [recipes, setRecipes] = useState<Recipe[]>(defaultRecipes);
  const [currentRecipe, setCurrentRecipe] = useState<Recipe | null>(null);
  const [cookingState, setCookingState] = useState<CookingState | null>(null);
  const [proRecipe, setProRecipe] = useState<ProRecipe | null>(bridgePayload?.proRecipe ?? { ...DEFAULT_PRO_RECIPE });
  const [completionData, setCompletionData] = useState<RecipeCompletionData | null>(null);
  const [presetSetup, setPresetSetup] = useState<PresetSetupData | null>(
    bridgePayload?.presetId
      ? {
          presetId: String(bridgePayload.presetId),
          name: String(bridgePayload.name || 'On2Cook Recipe'),
          dishType: bridgePayload.dishType || 'veg',
          recipeType: bridgePayload.recipeType || 'gravy',
          quantity: Number(bridgePayload.quantity || 500),
          quantityUnit: String(bridgePayload.quantityUnit || 'g'),
          consistency: bridgePayload.consistency || 'medium',
          healthRichRatio: Number(bridgePayload.healthRichRatio || 35),
          ingredients: Array.isArray(bridgePayload.ingredients) ? bridgePayload.ingredients : [],
        }
      : null
  );
  const [savedProRecipes, setSavedProRecipes] = useState<ProRecipe[]>([]);

  function saveRecipeToLibrary(recipe: Recipe) {
    setRecipes((prev) => {
      const idx = prev.findIndex((r) => r.id === recipe.id);
      if (idx >= 0) {
        const updated = [...prev];
        updated[idx] = recipe;
        return updated;
      }
      return [recipe, ...prev];
    });
  }

  function toggleFavorite(id: string) {
    setRecipes((prev) => prev.map((r) => (r.id === id ? { ...r, isFavorite: !r.isFavorite } : r)));
  }

  function deleteRecipe(id: string) {
    setRecipes((prev) => prev.filter((r) => r.id !== id));
  }

  function startNewRecipe(): Recipe {
    const id = Date.now().toString();
    const recipe: Recipe = {
      id,
      name: 'New Recipe',
      steps: [createDefaultStep(`${id}-step-1`)],
      createdAt: new Date().toISOString().split('T')[0],
      isFavorite: false,
      category: 'Custom',
    };
    setCurrentRecipe(recipe);
    return recipe;
  }

  function startProRecipe(recipe?: ProRecipe) {
    setProRecipe(recipe ?? { ...DEFAULT_PRO_RECIPE, id: `pro-${Date.now()}` });
  }

  function saveProRecipeToLibrary(recipe: ProRecipe) {
    setSavedProRecipes(prev => {
      const idx = prev.findIndex(r => r.id === recipe.id);
      if (idx >= 0) {
        const updated = [...prev];
        updated[idx] = recipe;
        return updated;
      }
      return [recipe, ...prev];
    });
  }

  return (
    <AppContext.Provider
      value={{
        recipes,
        setRecipes,
        currentRecipe,
        setCurrentRecipe,
        cookingState,
        setCookingState,
        saveRecipeToLibrary,
        toggleFavorite,
        deleteRecipe,
        startNewRecipe,
        proRecipe,
        setProRecipe,
        startProRecipe,
        completionData,
        setCompletionData,
        presetSetup,
        setPresetSetup,
        savedProRecipes,
        saveProRecipeToLibrary,
      }}
    >
      {children}
    </AppContext.Provider>
  );
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used inside AppProvider');
  return ctx;
}
