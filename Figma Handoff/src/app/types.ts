export interface MicrowaveSettings {
  enabled: boolean;
  duration: number; // seconds
  power: number; // 10-100
  startDelay: number; // seconds
}

export interface InductionSettings {
  enabled: boolean;
  duration: number;
  power: number;
  startDelay: number;
}

export type StirrerMode = 'continuous' | 'pulse';
export type StirrerDirection = 'clockwise' | 'counter' | 'alternate';

export interface StirrerSettings {
  enabled: boolean;
  mode: StirrerMode;
  direction: StirrerDirection;
  speed: number; // 1-5
  duration: number;
  pulseOn: number; // seconds
  pulseOff: number; // seconds
}

export type WaterDispenseType = 'single' | 'pulse' | 'continuous';

export interface WaterSettings {
  enabled: boolean;
  dispenseType: WaterDispenseType;
  amount: number; // ml
  triggerAt: number; // seconds from step start
  doses: number;
  dosGap: number; // seconds between doses
}

export interface RecipeStep {
  id: string;
  microwave: MicrowaveSettings;
  induction: InductionSettings;
  stirrer: StirrerSettings;
  water: WaterSettings;
}

export interface Recipe {
  id: string;
  name: string;
  steps: RecipeStep[];
  createdAt: string;
  lastUsed?: string;
  isFavorite: boolean;
  category?: string;
}

export function getStepDuration(step: RecipeStep): number {
  const durations = [
    step.microwave.enabled ? step.microwave.startDelay + step.microwave.duration : 0,
    step.induction.enabled ? step.induction.startDelay + step.induction.duration : 0,
    step.stirrer.enabled ? step.stirrer.duration : 0,
    step.water.enabled ? step.water.triggerAt + 15 : 0,
  ];
  return Math.max(...durations, 30);
}

export function getRecipeTotalDuration(recipe: Recipe): number {
  return recipe.steps.reduce((acc, step) => acc + getStepDuration(step), 0);
}

export function formatTime(seconds: number): string {
  const s = Math.max(0, Math.round(seconds));
  const m = Math.floor(s / 60);
  const rem = s % 60;
  return `${m.toString().padStart(2, '0')}:${rem.toString().padStart(2, '0')}`;
}

export function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return s > 0 ? `${m}m ${s}s` : `${m}m`;
}

export function createDefaultStep(id: string): RecipeStep {
  return {
    id,
    microwave: { enabled: false, duration: 120, power: 80, startDelay: 0 },
    induction: { enabled: true, duration: 180, power: 60, startDelay: 0 },
    stirrer: {
      enabled: true,
      mode: 'continuous',
      direction: 'clockwise',
      speed: 3,
      duration: 180,
      pulseOn: 5,
      pulseOff: 3,
    },
    water: { enabled: false, dispenseType: 'single', amount: 100, triggerAt: 0, doses: 1, dosGap: 10 },
  };
}
