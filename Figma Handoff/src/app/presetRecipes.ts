// ─── Preset Recipe Library + Timeline Generator ──────────────────────────────
import {
  ProRecipe, ProMinute, ProIngredient, WaterBlocks,
  VeganType, ProRecipeType, ConsistencyType, StirrerSpeedType,
} from './proTypes';

// ─── Extended preset metadata ─────────────────────────────────────────────────
export interface PresetRecipeData extends ProRecipe {
  shortDesc: string;
  imageUrl: string;
  category: string;
  complexity: 'easy' | 'medium' | 'hard';
  serves: number;
}

// ─── Factor param bag used both by generator and setup screen ─────────────────
export interface TimelineFactors {
  dishType: VeganType;
  recipeType: ProRecipeType;
  quantity: number;
  quantityUnit: string;
  consistency: ConsistencyType;
  healthRichRatio: number;
  ingredients: ProIngredient[];
}

// ─── Minute count by recipe type (base for 400 g) ────────────────────────────
const BASE_MIN: Record<ProRecipeType, number> = {
  gravy: 9, 'semi-dry': 7, dry: 5, saute: 4, boil: 8, fry: 5, steam: 6,
};

// ─── Timeline generator ───────────────────────────────────────────────────────
export function generateTimeline(factors: TimelineFactors): ProMinute[] {
  const { dishType, recipeType, quantity, consistency, ingredients } = factors;
  const isNonVeg = dishType === 'non-veg';

  // How many minutes to generate
  const extraFromQty = Math.max(0, Math.floor((quantity - 400) / 200));
  const totalMin = Math.min(12, Math.max(3, BASE_MIN[recipeType] + extraFromQty));

  // Bucket ingredients by group
  const byGroup = (groups: string[]) =>
    ingredients.filter(i => groups.includes(i.group ?? ''));
  const fats    = byGroup(['fats']);
  const spices  = byGroup(['spices', 'aromatics']);
  const protein = byGroup(['protein']);
  const vegs    = byGroup(['vegetables']);
  const sauce   = byGroup(['sauce', 'base']);
  const finish  = byGroup(['dairy', 'finish']);
  const other   = byGroup(['']);

  // Map minute index → { lidOpen, duration, ings }
  const lidMap: Record<number, { duration: number; ings: ProIngredient[] }> = {};

  function placeLid(idx: number, dur: number, ings: ProIngredient[]) {
    if (idx >= 0 && idx < totalMin && ings.length > 0) {
      lidMap[idx] = { duration: dur, ings };
    }
  }

  placeLid(0, 40, fats);
  placeLid(1, 20, spices);
  if (protein.length) placeLid(2, 15, protein);
  if (vegs.length)    placeLid(3, 15, vegs);
  const sauceMin = Math.floor(totalMin * 0.42);
  if (sauce.length && sauceMin >= 4) placeLid(sauceMin, 0, sauce);
  if (finish.length)  placeLid(totalMin - 1, 15, finish);
  if (other.length)   placeLid(Math.min(2, totalMin - 1), 20, other);

  const minutes: ProMinute[] = [];

  for (let i = 0; i < totalMin; i++) {
    const p = totalMin > 1 ? i / (totalMin - 1) : 0;  // 0..1 progress
    const lid = lidMap[i];
    const lidOpen = !!lid;
    const lidDur  = lid?.duration ?? 0;
    const ings    = lid?.ings ?? [];

    // Induction power curve
    let iBase: number;
    if (i === 0)                        iBase = 100;
    else if (recipeType === 'boil' || recipeType === 'steam')
                                         iBase = p < 0.25 ? 80 : 60;
    else if (recipeType === 'saute' || recipeType === 'fry')
                                         iBase = p < 0.35 ? (isNonVeg ? 100 : 80) : 60;
    else if (p < 0.15)                   iBase = 100;
    else if (p < 0.45)                   iBase = isNonVeg ? 80 : 80;
    else if (p < 0.75)                   iBase = 60;
    else                                 iBase = isNonVeg ? 60 : 40;

    // Microwave (non-veg non-boil, mid phase)
    const mwOn = !lidOpen
      && !['boil','steam','dry'].includes(recipeType)
      && !['vegan','veg'].includes(dishType)
      && p > 0.3 && p < 0.85;

    // Water blocks
    let wb: WaterBlocks = [false, false, false, false];
    if (recipeType === 'boil' || recipeType === 'steam') {
      if (i <= 1) wb = [true, true, false, false];
    } else if (!['dry','fry','saute'].includes(recipeType)) {
      if (p > 0.38 && p < 0.58) {
        if (consistency === 'thin')   wb = [true, true, false, false];
        else if (consistency === 'medium') wb = [true, false, false, false];
      }
    }

    // Stirrer
    let stirActive: boolean;
    let stirSpeed: StirrerSpeedType;
    if (recipeType === 'steam')                        { stirActive = false; stirSpeed = 'low'; }
    else if (recipeType === 'boil')                    { stirActive = true;  stirSpeed = p < 0.3 ? 'medium' : 'low'; }
    else if (recipeType === 'saute' || recipeType === 'fry') { stirActive = true;  stirSpeed = 'high'; }
    else if (i === 0)                                  { stirActive = false; stirSpeed = 'low'; }
    else                                               { stirActive = true;  stirSpeed = p < 0.5 ? 'high' : 'medium'; }

    const subBlocks = ([0, 1, 2, 3] as const).map(si => {
      const isLidBlock = lidOpen && si === 0;
      return {
        inductionPower: isLidBlock ? 0 : iBase,
        microwaveActive: mwOn && !isLidBlock,
        microwavePower: 800,
        stirrerActive: stirActive && !isLidBlock,
        stirrerSpeed: stirSpeed,
        stirrerMode: (stirActive && !isLidBlock ? 'continuous' : 'off') as 'continuous' | 'pulse' | 'off',
      };
    }) as ProMinute['subBlocks'];

    minutes.push({
      id: `gen-${i}-${Date.now() + i}`,
      minuteIndex: i,
      lidOpen,
      lidOpenDuration: lidDur,
      subBlocks,
      waterBlocks: wb,
      ingredients: ings,
    });
  }

  return minutes;
}

// ─── Helper: build ingredient ─────────────────────────────────────────────────
function ing(
  id: string, name: string, qty: number,
  unit: ProIngredient['unit'], group: string,
): ProIngredient {
  return { id, name, quantity: qty, unit, group };
}

// ─── Preset recipe factory ────────────────────────────────────────────────────
function makePreset(
  base: Omit<PresetRecipeData, 'minutes' | 'tentativeMinutes'>,
): PresetRecipeData {
  const factors: TimelineFactors = {
    dishType: base.dishType,
    recipeType: base.recipeType,
    quantity: base.quantity,
    quantityUnit: base.quantityUnit,
    consistency: base.consistency,
    healthRichRatio: base.healthRichRatio,
    ingredients: base.minutes ? [] : [],
  };
  // Use the ingredients from the base for generation
  const allIngredients = (base as any)._ingredients as ProIngredient[];
  factors.ingredients = allIngredients ?? [];

  const mins = generateTimeline(factors);
  return {
    ...base,
    tentativeMinutes: mins.length,
    minutes: mins,
  };
}

// ─── Preset Recipe Library ────────────────────────────────────────────────────

const _kadaiChickenIngs: ProIngredient[] = [
  ing('i-oil', 'Oil', 40, 'ml', 'fats'),
  ing('i-ghee', 'Ghee', 20, 'g', 'fats'),
  ing('i-ws', 'Whole Spices', 10, 'g', 'spices'),
  ing('i-mc', 'Marinated Chicken', 300, 'g', 'protein'),
  ing('i-vm', 'Vegetables Mix', 110, 'g', 'vegetables'),
  ing('i-rgm', 'Raw Gravy Mix', 309, 'g', 'sauce'),
  ing('i-bt', 'Butter', 20, 'g', 'dairy'),
  ing('i-cr', 'Cream', 30, 'g', 'dairy'),
];

const _butterChickenIngs: ProIngredient[] = [
  ing('bc-oil', 'Oil', 30, 'ml', 'fats'),
  ing('bc-butter', 'Butter', 30, 'g', 'fats'),
  ing('bc-spice', 'Whole Spices', 8, 'g', 'spices'),
  ing('bc-chicken', 'Chicken Pieces', 350, 'g', 'protein'),
  ing('bc-base', 'Tomato Onion Paste', 300, 'g', 'sauce'),
  ing('bc-cream', 'Fresh Cream', 60, 'ml', 'dairy'),
  ing('bc-kasuri', 'Kasuri Methi', 5, 'g', 'finish'),
];

const _biryaniIngs: ProIngredient[] = [
  ing('br-oil', 'Oil', 50, 'ml', 'fats'),
  ing('br-spice', 'Biryani Spices', 15, 'g', 'spices'),
  ing('br-chicken', 'Chicken Leg Pieces', 500, 'g', 'protein'),
  ing('br-rice', 'Basmati Rice', 400, 'g', 'base'),
  ing('br-saffron', 'Saffron Milk', 30, 'ml', 'finish'),
  ing('br-fried-onion', 'Fried Onions', 50, 'g', 'finish'),
];

const _dalTadkaIngs: ProIngredient[] = [
  ing('dt-oil', 'Oil', 30, 'ml', 'fats'),
  ing('dt-ghee', 'Ghee', 15, 'g', 'fats'),
  ing('dt-spice', 'Cumin + Mustard', 5, 'g', 'spices'),
  ing('dt-dal', 'Yellow Dal', 200, 'g', 'base'),
  ing('dt-water', 'Water', 400, 'ml', 'base'),
  ing('dt-tomato', 'Tomato', 80, 'g', 'vegetables'),
  ing('dt-coriander', 'Fresh Coriander', 10, 'g', 'finish'),
];

const _palakPaneerIngs: ProIngredient[] = [
  ing('pp-oil', 'Oil', 25, 'ml', 'fats'),
  ing('pp-spice', 'Spices', 8, 'g', 'spices'),
  ing('pp-paneer', 'Paneer Cubes', 200, 'g', 'protein'),
  ing('pp-spinach', 'Blanched Spinach Puree', 250, 'g', 'sauce'),
  ing('pp-cream', 'Cream', 30, 'ml', 'dairy'),
  ing('pp-butter', 'Butter', 10, 'g', 'dairy'),
];

const _rajmaIngs: ProIngredient[] = [
  ing('rj-oil', 'Oil', 30, 'ml', 'fats'),
  ing('rj-spice', 'Whole Spices', 6, 'g', 'spices'),
  ing('rj-rajma', 'Soaked Rajma', 250, 'g', 'base'),
  ing('rj-onion', 'Onion Tomato Paste', 200, 'g', 'sauce'),
  ing('rj-masala', 'Rajma Masala', 15, 'g', 'spices'),
  ing('rj-coriander', 'Coriander', 10, 'g', 'finish'),
];

const _pastaIngs: ProIngredient[] = [
  ing('pa-oil', 'Olive Oil', 30, 'ml', 'fats'),
  ing('pa-garlic', 'Garlic', 15, 'g', 'aromatics'),
  ing('pa-pasta', 'Penne Pasta', 200, 'g', 'base'),
  ing('pa-tomato', 'Crushed Tomatoes', 300, 'g', 'sauce'),
  ing('pa-herbs', 'Basil + Oregano', 5, 'g', 'finish'),
];

const _mushroomIngs: ProIngredient[] = [
  ing('ms-oil', 'Sesame Oil', 25, 'ml', 'fats'),
  ing('ms-garlic', 'Garlic + Ginger', 12, 'g', 'aromatics'),
  ing('ms-mushroom', 'Button Mushrooms', 250, 'g', 'protein'),
  ing('ms-vegs', 'Bell Peppers', 150, 'g', 'vegetables'),
  ing('ms-soy', 'Soy Sauce', 20, 'ml', 'sauce'),
];

const _fishCurryIngs: ProIngredient[] = [
  ing('fc-oil', 'Coconut Oil', 35, 'ml', 'fats'),
  ing('fc-spice', 'Curry Leaves + Spices', 8, 'g', 'spices'),
  ing('fc-fish', 'Fish Pieces', 300, 'g', 'protein'),
  ing('fc-base', 'Coconut Tomato Curry Base', 300, 'g', 'sauce'),
  ing('fc-coconut', 'Coconut Milk', 100, 'ml', 'dairy'),
];

const _khichdiIngs: ProIngredient[] = [
  ing('kh-ghee', 'Ghee', 20, 'g', 'fats'),
  ing('kh-cumin', 'Cumin Seeds', 5, 'g', 'spices'),
  ing('kh-rice', 'Rice', 150, 'g', 'base'),
  ing('kh-dal', 'Moong Dal', 100, 'g', 'base'),
  ing('kh-vegs', 'Mixed Vegetables', 100, 'g', 'vegetables'),
  ing('kh-water', 'Water', 500, 'ml', 'base'),
];

// Build the timeline for each preset
function buildPreset(
  id: string,
  name: string,
  shortDesc: string,
  imageUrl: string,
  category: string,
  complexity: PresetRecipeData['complexity'],
  serves: number,
  dishType: VeganType,
  recipeType: ProRecipeType,
  quantity: number,
  quantityUnit: string,
  consistency: ConsistencyType,
  healthRichRatio: number,
  ingredients: ProIngredient[],
): PresetRecipeData {
  const factors: TimelineFactors = {
    dishType, recipeType, quantity, quantityUnit, consistency, healthRichRatio, ingredients,
  };
  const minutes = generateTimeline(factors);
  return {
    id, name, shortDesc, imageUrl, category, complexity, serves,
    dishType, recipeType, quantity, quantityUnit, consistency,
    healthRichRatio, tentativeMinutes: minutes.length, minutes,
    notes: '',
  };
}

export const PRESET_RECIPES: PresetRecipeData[] = [
  buildPreset(
    'kadai-chicken', 'Kadai Chicken',
    'Bold restaurant-style chicken in a spiced pepper-tomato gravy',
    'https://images.unsplash.com/photo-1606791422814-b32c705e3e2f?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&w=600',
    'North Indian', 'medium', 3,
    'non-veg', 'gravy', 800, 'g', 'medium', 45,
    _kadaiChickenIngs,
  ),
  buildPreset(
    'butter-chicken', 'Butter Chicken',
    'Silky makhani sauce with tender chicken — India\'s most loved dish',
    'https://images.unsplash.com/photo-1603894584373-5ac82b2ae398?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&w=600',
    'North Indian', 'medium', 3,
    'non-veg', 'gravy', 800, 'g', 'thin', 65,
    _butterChickenIngs,
  ),
  buildPreset(
    'dum-biryani', 'Dum Biryani',
    'Aromatic saffron rice layered with spiced chicken — cooked on steam',
    'https://images.unsplash.com/photo-1599043513900-ed6fe01d3833?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&w=600',
    'Mughlai', 'hard', 4,
    'non-veg', 'steam', 1000, 'g', 'thick', 55,
    _biryaniIngs,
  ),
  buildPreset(
    'dal-tadka', 'Dal Tadka',
    'Comforting yellow lentils with a smoky cumin-ghee tadka',
    'https://images.unsplash.com/photo-1727018742095-14c9a05aa71a?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&w=600',
    'Indian Classics', 'easy', 2,
    'veg', 'boil', 500, 'g', 'medium', 20,
    _dalTadkaIngs,
  ),
  buildPreset(
    'palak-paneer', 'Palak Paneer',
    'Creamy spinach curry with golden pan-seared paneer',
    'https://images.unsplash.com/photo-1767114915936-745dd372f1d8?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&w=600',
    'North Indian', 'medium', 2,
    'veg', 'gravy', 500, 'g', 'medium', 35,
    _palakPaneerIngs,
  ),
  buildPreset(
    'rajma-masala', 'Rajma Masala',
    'Hearty kidney beans in a rich tomato-onion masala',
    'https://images.unsplash.com/photo-1623690856012-85292929be28?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&w=600',
    'Indian Classics', 'easy', 3,
    'veg', 'gravy', 600, 'g', 'thick', 25,
    _rajmaIngs,
  ),
  buildPreset(
    'pasta-arrabiata', 'Pasta Arrabiata',
    'Fiery penne in a garlic-chilli tomato sauce — Italian soul food',
    'https://images.unsplash.com/photo-1673971372358-769a28fa4c81?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&w=600',
    'Italian', 'easy', 2,
    'veg', 'semi-dry', 400, 'g', 'medium', 30,
    _pastaIngs,
  ),
  buildPreset(
    'mushroom-stirfry', 'Mushroom Stir Fry',
    'Quick wok-tossed mushrooms and peppers in umami soy glaze',
    'https://images.unsplash.com/photo-1652282557988-f19b23769c39?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&w=600',
    'Asian Fusion', 'easy', 2,
    'veg', 'saute', 400, 'g', 'dry', 15,
    _mushroomIngs,
  ),
  buildPreset(
    'fish-curry', 'Kerala Fish Curry',
    'Coastal coconut fish curry with tamarind and curry leaves',
    'https://images.unsplash.com/photo-1606791422814-b32c705e3e2f?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&w=600',
    'South Indian', 'medium', 3,
    'non-veg', 'gravy', 600, 'g', 'thin', 30,
    _fishCurryIngs,
  ),
  buildPreset(
    'vegetable-khichdi', 'Vegetable Khichdi',
    'Wholesome one-pot rice and lentil comfort bowl with seasonal vegs',
    'https://images.unsplash.com/photo-1727018742095-14c9a05aa71a?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&w=600',
    'Comfort Food', 'easy', 2,
    'vegan', 'boil', 500, 'g', 'thick', 10,
    _khichdiIngs,
  ),
];
