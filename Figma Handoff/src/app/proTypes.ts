// ─── Professional Timeline Recipe Types ─────────────────────────────────────

export type VeganType = 'vegan' | 'veg' | 'non-veg';
export type ProRecipeType = 'gravy' | 'dry' | 'semi-dry' | 'saute' | 'boil' | 'fry' | 'steam';
export type ConsistencyType = 'thin' | 'medium' | 'thick';
export type StirrerModeType = 'continuous' | 'pulse' | 'off';
export type StirrerSpeedType = 'low' | 'medium' | 'high' | 'very-high';

export interface ProSubBlock {
  inductionPower: number;         // 0 | 40 | 60 | 80 | 100
  microwaveActive: boolean;
  microwavePower: number;         // watts: 180 | 360 | 540 | 720 | 800 | 900
  stirrerActive: boolean;
  stirrerSpeed: StirrerSpeedType; // 'low' | 'medium' | 'high' | 'very-high'
  stirrerMode: StirrerModeType;
}

// ─── Water: 4 × 15-second sub-blocks, each = 150 ml ─────────────────────────
// waterBlocks[0] = 0–15s, [1] = 15–30s, [2] = 30–45s, [3] = 45–60s
// true = water ON for that 15s block (150 ml), false = OFF (0 ml)
export type WaterBlocks = [boolean, boolean, boolean, boolean];

/** Returns total ml for a minute based on its water blocks (each active block = 150 ml) */
export function getMinuteWaterMl(waterBlocks: WaterBlocks): number {
  return waterBlocks.filter(Boolean).length * 150;
}

/** Returns how many blocks are active */
export function getActiveWaterBlocks(waterBlocks: WaterBlocks): number {
  return waterBlocks.filter(Boolean).length;
}

export interface ProIngredient {
  id: string;
  name: string;
  quantity: number;
  unit: 'g' | 'ml' | 'piece' | 'tsp' | 'tbsp';
  group?: string;
}

export interface ProMinute {
  id: string;
  minuteIndex: number;
  lidOpen: boolean;
  lidOpenDuration: number;    // seconds (0–60) lid stays open within this minute
  subBlocks: [ProSubBlock, ProSubBlock, ProSubBlock, ProSubBlock];
  waterBlocks: WaterBlocks;   // replaces waterEvents — 4 × 15s toggles
  ingredients: ProIngredient[];
}

export interface ProRecipe {
  id: string;
  name: string;
  dishType: VeganType;
  recipeType: ProRecipeType;
  quantity: number;
  quantityUnit: string;
  consistency: ConsistencyType;
  healthRichRatio: number;    // 0 = healthy, 100 = rich
  tentativeMinutes: number;
  minutes: ProMinute[];
  notes?: string;
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function sb(ind: number, mw: boolean, stir = true, speed: StirrerSpeedType = 'medium', mwPower = 800): ProSubBlock {
  return {
    inductionPower: ind,
    microwaveActive: mw,
    microwavePower: mwPower,
    stirrerActive: stir,
    stirrerSpeed: speed,
    stirrerMode: stir ? 'continuous' : 'off',
  };
}

function makeMin(
  index: number,
  lidOpen: boolean,
  lidOpenDuration: number,
  powers: [[number, boolean], [number, boolean], [number, boolean], [number, boolean]],
  waterBlocks: WaterBlocks,
  ingredients: ProIngredient[]
): ProMinute {
  return {
    id: `min-${index}`,
    minuteIndex: index,
    lidOpen,
    lidOpenDuration,
    subBlocks: [
      sb(powers[0][0], powers[0][1]),
      sb(powers[1][0], powers[1][1]),
      sb(powers[2][0], powers[2][1]),
      sb(powers[3][0], powers[3][1]),
    ],
    waterBlocks,
    ingredients,
  };
}

function ing(id: string, name: string, quantity: number, unit: ProIngredient['unit'], group?: string): ProIngredient {
  return { id, name, quantity, unit, group };
}

export function createDefaultSubBlock(): ProSubBlock {
  return { inductionPower: 60, microwaveActive: false, microwavePower: 800, stirrerActive: true, stirrerSpeed: 'medium', stirrerMode: 'continuous' };
}

export function createDefaultMinute(index: number): ProMinute {
  const s = createDefaultSubBlock();
  return {
    id: `min-${index}-${Date.now()}`,
    minuteIndex: index,
    lidOpen: false,
    lidOpenDuration: 0,
    subBlocks: [{ ...s }, { ...s }, { ...s }, { ...s }],
    waterBlocks: [false, false, false, false],
    ingredients: [],
  };
}

export function getTotalHoldTime(minutes: ProMinute[]): number {
  return minutes.reduce((acc, m) => acc + (m.lidOpen ? m.lidOpenDuration : 0), 0);
}

export function getProRecipeTotalSeconds(recipe: ProRecipe): number {
  return recipe.minutes.length * 60;
}

/** Total water in ml across all minutes */
export function getRecipeTotalWaterMl(recipe: ProRecipe): number {
  return recipe.minutes.reduce((acc, m) => acc + getMinuteWaterMl(m.waterBlocks), 0);
}

// ─── Default Kadai Chicken Recipe ────────────────────────────────────────────

export const DEFAULT_PRO_RECIPE: ProRecipe = {
  id: 'kadai-chicken',
  name: 'Kadai Chicken',
  dishType: 'non-veg',
  recipeType: 'gravy',
  quantity: 800,
  quantityUnit: 'g',
  consistency: 'medium',
  healthRichRatio: 40,
  tentativeMinutes: 9,
  minutes: [
    // Min 0 – Heat oil & ghee (lid open 40s)
    makeMin(0, true, 40,
      [[100, false], [100, false], [0, false], [0, false]],
      [false, false, false, false],
      [ing('i-oil', 'Oil', 40, 'ml', 'fats'), ing('i-ghee', 'Ghee', 20, 'g', 'fats')]
    ),
    // Min 1 – Whole spices (lid open 20s)
    makeMin(1, true, 20,
      [[100, false], [0, false], [0, false], [0, false]],
      [false, false, false, false],
      [ing('i-ws', 'Whole Spices', 10, 'g', 'spices')]
    ),
    // Min 2 – Add marinated chicken (lid open 15s)
    makeMin(2, true, 15,
      [[80, false], [80, true], [80, true], [80, true]],
      [false, false, false, false],
      [ing('i-mc', 'Marinated Chicken', 300, 'g', 'protein')]
    ),
    // Min 3 – Add vegetables mix (lid open 15s)
    makeMin(3, true, 15,
      [[60, false], [60, true], [60, true], [60, true]],
      [false, false, false, false],
      [ing('i-vm', 'Vegetables Mix', 110, 'g', 'vegetables')]
    ),
    // Min 4 – Raw gravy mix + water injection (first 15s block = 150 ml)
    makeMin(4, false, 0,
      [[60, true], [60, true], [60, true], [60, true]],
      [true, false, false, false],
      [ing('i-rgm', 'Raw Gravy Mix', 309, 'g', 'sauce')]
    ),
    // Min 5–8 – Continued gravy cooking
    makeMin(5, false, 0, [[60, true], [60, true], [60, true], [60, true]], [false, false, false, false], []),
    makeMin(6, false, 0, [[60, true], [60, true], [60, true], [60, true]], [false, false, false, false], []),
    makeMin(7, false, 0, [[60, true], [60, true], [60, true], [60, true]], [false, false, false, false], []),
    makeMin(8, false, 0, [[60, true], [60, true], [60, true], [60, true]], [false, false, false, false], []),
    // Min 9 – Butter & cream finish (lid open 15s)
    makeMin(9, true, 15,
      [[60, false], [60, true], [60, true], [60, true]],
      [false, false, false, false],
      [ing('i-bt', 'Butter', 20, 'g', 'dairy'), ing('i-cr', 'Cream', 30, 'g', 'dairy')]
    ),
  ],
};