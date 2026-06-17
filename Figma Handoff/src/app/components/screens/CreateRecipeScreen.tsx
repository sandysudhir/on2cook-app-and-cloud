import { useState, useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router';
import {
  ArrowLeft,
  BookmarkCheck,
  Plus,
  Copy,
  Trash2,
  PlayCircle,
  Check,
  Zap,
  Waves,
  Droplets,
  RotateCcw,
  ChevronUp,
  ChevronDown,
} from 'lucide-react';
import { useApp } from '../../AppContext';
import {
  Recipe,
  RecipeStep,
  MicrowaveSettings,
  InductionSettings,
  StirrerSettings,
  WaterSettings,
  createDefaultStep,
  getStepDuration,
  getRecipeTotalDuration,
  formatDuration,
} from '../../types';

// ── Helpers ──────────────────────────────────────────────────────────────────
function secToDisplay(sec: number): string {
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return m > 0 ? `${m}m ${s.toString().padStart(2, '0')}s` : `${s}s`;
}

// ── Time Stepper ──────────────────────────────────────────────────────────────
function TimeStepper({ value, onChange, min = 0, max = 3600, step = 15 }: {
  value: number;
  onChange: (v: number) => void;
  min?: number;
  max?: number;
  step?: number;
}) {
  return (
    <div className="flex items-center gap-2">
      <button
        onClick={() => onChange(Math.max(min, value - step))}
        className="w-7 h-7 rounded-lg flex items-center justify-center active:scale-95 transition-transform"
        style={{ background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.1)' }}
      >
        <ChevronDown size={14} color="#94a3b8" />
      </button>
      <span style={{ fontSize: '14px', color: '#e2e8f0', fontWeight: 600, minWidth: 64, textAlign: 'center' }}>
        {secToDisplay(value)}
      </span>
      <button
        onClick={() => onChange(Math.min(max, value + step))}
        className="w-7 h-7 rounded-lg flex items-center justify-center active:scale-95 transition-transform"
        style={{ background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.1)' }}
      >
        <ChevronUp size={14} color="#94a3b8" />
      </button>
    </div>
  );
}

// ── Power Slider ──────────────────────────────────────────────────────────────
function PowerSlider({ value, onChange, color }: { value: number; onChange: (v: number) => void; color: string }) {
  return (
    <div className="flex items-center gap-3">
      <div className="relative flex-1 h-1.5 rounded-full" style={{ background: 'rgba(255,255,255,0.1)' }}>
        <div
          className="absolute left-0 top-0 h-full rounded-full"
          style={{ width: `${value}%`, background: `linear-gradient(90deg, ${color}88, ${color})` }}
        />
        <input
          type="range"
          min={10}
          max={100}
          step={10}
          value={value}
          onChange={(e) => onChange(Number(e.target.value))}
          className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
        />
      </div>
      <span style={{ fontSize: '13px', color, fontWeight: 700, minWidth: 36 }}>{value}%</span>
    </div>
  );
}

// ── Toggle Switch ─────────────────────────────────────────────────────────────
function Toggle({ value, onChange, color }: { value: boolean; onChange: (v: boolean) => void; color: string }) {
  return (
    <button
      onClick={() => onChange(!value)}
      className="relative flex-shrink-0 rounded-full transition-all active:scale-95"
      style={{
        width: 44,
        height: 24,
        background: value ? color : 'rgba(255,255,255,0.1)',
        border: `1px solid ${value ? color : 'rgba(255,255,255,0.12)'}`,
        boxShadow: value ? `0 0 12px ${color}60` : 'none',
        transition: 'all 0.2s',
      }}
    >
      <div
        className="absolute top-0.5 rounded-full"
        style={{
          width: 20,
          height: 20,
          background: '#fff',
          left: value ? 'calc(100% - 22px)' : 2,
          transition: 'left 0.2s',
          boxShadow: '0 1px 4px rgba(0,0,0,0.4)',
        }}
      />
    </button>
  );
}

// ── Pill Select ───────────────────────────────────────────────────────────────
function PillSelect<T extends string>({
  options,
  value,
  onChange,
  color,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (v: T) => void;
  color: string;
}) {
  return (
    <div className="flex gap-1.5 flex-wrap">
      {options.map((opt) => (
        <button
          key={opt.value}
          onClick={() => onChange(opt.value)}
          className="px-3 py-1 rounded-lg text-xs transition-all active:scale-95"
          style={{
            background: value === opt.value ? `${color}25` : 'rgba(255,255,255,0.06)',
            border: `1px solid ${value === opt.value ? color : 'rgba(255,255,255,0.1)'}`,
            color: value === opt.value ? color : '#64748b',
            fontWeight: value === opt.value ? 600 : 400,
            fontSize: '11px',
          }}
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
}

// ── Speed Dots ────────────────────────────────────────────────────────────────
function SpeedDots({ value, onChange, color }: { value: number; onChange: (v: number) => void; color: string }) {
  return (
    <div className="flex gap-2">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          onClick={() => onChange(n)}
          className="w-7 h-7 rounded-full flex items-center justify-center text-xs transition-all active:scale-90"
          style={{
            background: n <= value ? `${color}25` : 'rgba(255,255,255,0.06)',
            border: `1px solid ${n <= value ? color : 'rgba(255,255,255,0.1)'}`,
            color: n <= value ? color : '#475569',
            fontWeight: 600,
            fontSize: '11px',
          }}
        >
          {n}
        </button>
      ))}
    </div>
  );
}

// ── Row Label ─────────────────────────────────────────────────────────────────
function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 py-1.5">
      <span style={{ fontSize: '12px', color: '#64748b', fontWeight: 500, flexShrink: 0 }}>{label}</span>
      <div className="flex-1 min-w-0 flex justify-end">{children}</div>
    </div>
  );
}

// ── Module Card ────────────────────────────────────────────────────────────────
function ModuleCard({
  icon,
  title,
  color,
  enabled,
  onToggle,
  children,
}: {
  icon: ReactNode;
  title: string;
  color: string;
  enabled: boolean;
  onToggle: () => void;
  children: ReactNode;
}) {
  return (
    <div
      className="rounded-2xl overflow-hidden mb-3"
      style={{
        background: enabled
          ? `linear-gradient(135deg, ${color}12 0%, rgba(255,255,255,0.04) 100%)`
          : 'rgba(255,255,255,0.03)',
        border: `1px solid ${enabled ? color + '35' : 'rgba(255,255,255,0.08)'}`,
        boxShadow: enabled ? `0 0 20px ${color}18` : 'none',
        transition: 'all 0.3s',
      }}
    >
      <div className="flex items-center justify-between px-4 py-3.5">
        <div className="flex items-center gap-3">
          <div
            className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
            style={{
              background: enabled ? `${color}22` : 'rgba(255,255,255,0.06)',
              border: `1px solid ${enabled ? color + '40' : 'rgba(255,255,255,0.08)'}`,
            }}
          >
            {icon}
          </div>
          <span style={{ fontSize: '15px', color: enabled ? '#f1f5f9' : '#64748b', fontWeight: 600 }}>
            {title}
          </span>
        </div>
        <Toggle value={enabled} onChange={onToggle} color={color} />
      </div>
      {enabled && (
        <div className="px-4 pb-4 pt-0" style={{ borderTop: `1px solid ${color}20` }}>
          {children}
        </div>
      )}
    </div>
  );
}

// ── Microwave Card ────────────────────────────────────────────────────────────
function MicrowaveCard({ settings, onChange }: {
  settings: MicrowaveSettings;
  onChange: (s: MicrowaveSettings) => void;
}) {
  const color = '#fbbf24';
  return (
    <ModuleCard
      icon={<Zap size={18} color={settings.enabled ? color : '#475569'} />}
      title="Microwave"
      color={color}
      enabled={settings.enabled}
      onToggle={() => onChange({ ...settings, enabled: !settings.enabled })}
    >
      <div className="mt-3 space-y-3">
        <Row label="Duration">
          <TimeStepper value={settings.duration} onChange={(v) => onChange({ ...settings, duration: v })} step={15} min={15} />
        </Row>
        <Row label="Power">
          <div style={{ width: 160 }}>
            <PowerSlider value={settings.power} onChange={(v) => onChange({ ...settings, power: v })} color={color} />
          </div>
        </Row>
        <Row label="Start Delay">
          <TimeStepper value={settings.startDelay} onChange={(v) => onChange({ ...settings, startDelay: v })} step={5} />
        </Row>
      </div>
    </ModuleCard>
  );
}

// ── Induction Card ────────────────────────────────────────────────────────────
function InductionCard({ settings, onChange }: {
  settings: InductionSettings;
  onChange: (s: InductionSettings) => void;
}) {
  const color = '#f87171';
  return (
    <ModuleCard
      icon={<Waves size={18} color={settings.enabled ? color : '#475569'} />}
      title="Induction"
      color={color}
      enabled={settings.enabled}
      onToggle={() => onChange({ ...settings, enabled: !settings.enabled })}
    >
      <div className="mt-3 space-y-3">
        <Row label="Duration">
          <TimeStepper value={settings.duration} onChange={(v) => onChange({ ...settings, duration: v })} step={15} min={15} />
        </Row>
        <Row label="Power">
          <div style={{ width: 160 }}>
            <PowerSlider value={settings.power} onChange={(v) => onChange({ ...settings, power: v })} color={color} />
          </div>
        </Row>
        <Row label="Start Delay">
          <TimeStepper value={settings.startDelay} onChange={(v) => onChange({ ...settings, startDelay: v })} step={5} />
        </Row>
      </div>
    </ModuleCard>
  );
}

// ── Stirrer Card ──────────────────────────────────────────────────────────────
function StirrerCard({ settings, onChange }: {
  settings: StirrerSettings;
  onChange: (s: StirrerSettings) => void;
}) {
  const color = '#c084fc';
  return (
    <ModuleCard
      icon={<RotateCcw size={18} color={settings.enabled ? color : '#475569'} />}
      title="Stirrer"
      color={color}
      enabled={settings.enabled}
      onToggle={() => onChange({ ...settings, enabled: !settings.enabled })}
    >
      <div className="mt-3 space-y-3">
        <Row label="Mode">
          <PillSelect
            options={[
              { value: 'continuous', label: 'Continuous' },
              { value: 'pulse', label: 'Pulse' },
            ]}
            value={settings.mode}
            onChange={(v) => onChange({ ...settings, mode: v })}
            color={color}
          />
        </Row>
        <Row label="Direction">
          <PillSelect
            options={[
              { value: 'clockwise', label: '↻ CW' },
              { value: 'counter', label: '↺ CCW' },
              { value: 'alternate', label: '⇄ Alt' },
            ]}
            value={settings.direction}
            onChange={(v) => onChange({ ...settings, direction: v })}
            color={color}
          />
        </Row>
        <Row label="Speed">
          <SpeedDots value={settings.speed} onChange={(v) => onChange({ ...settings, speed: v })} color={color} />
        </Row>
        <Row label="Duration">
          <TimeStepper value={settings.duration} onChange={(v) => onChange({ ...settings, duration: v })} step={15} min={15} />
        </Row>
        {settings.mode === 'pulse' && (
          <>
            <Row label="On for">
              <TimeStepper value={settings.pulseOn} onChange={(v) => onChange({ ...settings, pulseOn: v })} step={1} min={1} max={60} />
            </Row>
            <Row label="Off for">
              <TimeStepper value={settings.pulseOff} onChange={(v) => onChange({ ...settings, pulseOff: v })} step={1} min={1} max={60} />
            </Row>
          </>
        )}
      </div>
    </ModuleCard>
  );
}

// ── Water Card ────────────────────────────────────────────────────────────────
function WaterCard({ settings, onChange }: {
  settings: WaterSettings;
  onChange: (s: WaterSettings) => void;
}) {
  const color = '#22d3ee';
  return (
    <ModuleCard
      icon={<Droplets size={18} color={settings.enabled ? color : '#475569'} />}
      title="Water"
      color={color}
      enabled={settings.enabled}
      onToggle={() => onChange({ ...settings, enabled: !settings.enabled })}
    >
      <div className="mt-3 space-y-3">
        <Row label="Dispense">
          <PillSelect
            options={[
              { value: 'single', label: 'Single' },
              { value: 'pulse', label: 'Pulse' },
              { value: 'continuous', label: 'Flow' },
            ]}
            value={settings.dispenseType}
            onChange={(v) => onChange({ ...settings, dispenseType: v })}
            color={color}
          />
        </Row>
        <Row label="Amount (ml)">
          <div className="flex items-center gap-2">
            <button
              onClick={() => onChange({ ...settings, amount: Math.max(0, settings.amount - 25) })}
              className="w-7 h-7 rounded-lg flex items-center justify-center"
              style={{ background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.1)' }}
            >
              <ChevronDown size={14} color="#94a3b8" />
            </button>
            <span style={{ fontSize: '14px', color: '#e2e8f0', fontWeight: 600, minWidth: 52, textAlign: 'center' }}>
              {settings.amount} ml
            </span>
            <button
              onClick={() => onChange({ ...settings, amount: Math.min(1000, settings.amount + 25) })}
              className="w-7 h-7 rounded-lg flex items-center justify-center"
              style={{ background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.1)' }}
            >
              <ChevronUp size={14} color="#94a3b8" />
            </button>
          </div>
        </Row>
        <Row label="Release At">
          <TimeStepper value={settings.triggerAt} onChange={(v) => onChange({ ...settings, triggerAt: v })} step={5} />
        </Row>
        {settings.dispenseType === 'pulse' && (
          <>
            <Row label="# Doses">
              <div className="flex items-center gap-2">
                <button
                  onClick={() => onChange({ ...settings, doses: Math.max(1, settings.doses - 1) })}
                  className="w-7 h-7 rounded-lg flex items-center justify-center"
                  style={{ background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.1)' }}
                >
                  <ChevronDown size={14} color="#94a3b8" />
                </button>
                <span style={{ fontSize: '14px', color: '#e2e8f0', fontWeight: 600, minWidth: 28, textAlign: 'center' }}>
                  {settings.doses}
                </span>
                <button
                  onClick={() => onChange({ ...settings, doses: Math.min(10, settings.doses + 1) })}
                  className="w-7 h-7 rounded-lg flex items-center justify-center"
                  style={{ background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.1)' }}
                >
                  <ChevronUp size={14} color="#94a3b8" />
                </button>
              </div>
            </Row>
            <Row label="Dose Gap">
              <TimeStepper value={settings.dosGap} onChange={(v) => onChange({ ...settings, dosGap: v })} step={5} min={5} />
            </Row>
          </>
        )}
      </div>
    </ModuleCard>
  );
}

// ── Main Screen ───────────────────────────────────────────────────────────────
export function CreateRecipeScreen() {
  const navigate = useNavigate();
  const { currentRecipe, setCurrentRecipe, saveRecipeToLibrary, setCookingState } = useApp();
  const [autoSaved, setAutoSaved] = useState(false);
  const [activeStepIdx, setActiveStepIdx] = useState(0);
  const [editingName, setEditingName] = useState(false);
  const nameRef = useRef<HTMLInputElement>(null);

  const recipe = currentRecipe;

  useEffect(() => {
    if (!recipe) navigate('/');
  }, [recipe]);

  useEffect(() => {
    if (!recipe) return;
    const timer = setTimeout(() => {
      setAutoSaved(true);
      setTimeout(() => setAutoSaved(false), 2000);
    }, 1000);
    return () => clearTimeout(timer);
  }, [recipe]);

  if (!recipe) return null;

  const activeStep = recipe.steps[activeStepIdx] ?? recipe.steps[0];
  const totalDuration = getRecipeTotalDuration(recipe);

  function updateStep(updatedStep: RecipeStep) {
    const steps = recipe!.steps.map((s) => (s.id === updatedStep.id ? updatedStep : s));
    setCurrentRecipe({ ...recipe!, steps });
  }

  function addStep() {
    const id = `${recipe!.id}-step-${Date.now()}`;
    const newStep = createDefaultStep(id);
    const newSteps = [...recipe!.steps, newStep];
    setCurrentRecipe({ ...recipe!, steps: newSteps });
    setActiveStepIdx(newSteps.length - 1);
  }

  function duplicateStep(idx: number) {
    const src = recipe!.steps[idx];
    const id = `${recipe!.id}-step-${Date.now()}`;
    const dup: RecipeStep = { ...src, id };
    const newSteps = [...recipe!.steps];
    newSteps.splice(idx + 1, 0, dup);
    setCurrentRecipe({ ...recipe!, steps: newSteps });
    setActiveStepIdx(idx + 1);
  }

  function deleteStep(idx: number) {
    if (recipe!.steps.length <= 1) return;
    const newSteps = recipe!.steps.filter((_, i) => i !== idx);
    setCurrentRecipe({ ...recipe!, steps: newSteps });
    setActiveStepIdx(Math.min(idx, newSteps.length - 1));
  }

  function handleSave() {
    saveRecipeToLibrary(recipe!);
    setAutoSaved(true);
    setTimeout(() => setAutoSaved(false), 2500);
  }

  function handleStartCooking() {
    saveRecipeToLibrary(recipe!);
    setCookingState({ recipe: recipe!, isActive: true, isPaused: false });
    navigate('/cooking');
  }

  return (
    <div className="flex flex-col min-h-screen">
      {/* Top Bar */}
      <div
        className="flex items-center gap-3 px-5 pt-14 pb-4 sticky top-0 z-20"
        style={{
          background: 'rgba(5,14,34,0.9)',
          backdropFilter: 'blur(20px)',
          borderBottom: '1px solid rgba(255,255,255,0.06)',
        }}
      >
        <button
          onClick={() => navigate('/')}
          className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
          style={{ background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)' }}
        >
          <ArrowLeft size={17} color="#94a3b8" />
        </button>
        <div className="flex-1 min-w-0">
          {editingName ? (
            <input
              ref={nameRef}
              value={recipe.name}
              onChange={(e) => setCurrentRecipe({ ...recipe, name: e.target.value })}
              onBlur={() => setEditingName(false)}
              onKeyDown={(e) => e.key === 'Enter' && setEditingName(false)}
              autoFocus
              className="w-full bg-transparent outline-none"
              style={{ fontSize: '16px', color: '#f1f5f9', fontWeight: 700, fontFamily: "'Space Grotesk', sans-serif" }}
            />
          ) : (
            <button onClick={() => setEditingName(true)} className="text-left w-full truncate">
              <span style={{ fontSize: '16px', color: '#f1f5f9', fontWeight: 700 }}>{recipe.name}</span>
            </button>
          )}
          <div className="flex items-center gap-2 mt-0.5">
            <span
              className="flex items-center gap-1"
              style={{ fontSize: '11px', color: autoSaved ? '#4ade80' : '#475569' }}
            >
              {autoSaved ? <><Check size={10} /> Autosaved</> : '● Editing'}
            </span>
            <span style={{ fontSize: '11px', color: '#334155' }}>·</span>
            <span style={{ fontSize: '11px', color: '#475569' }}>
              {recipe.steps.length} steps · {formatDuration(totalDuration)}
            </span>
          </div>
        </div>
        <button
          onClick={handleSave}
          className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
          style={{
            background: 'rgba(96,165,250,0.15)',
            border: '1px solid rgba(96,165,250,0.3)',
          }}
        >
          <BookmarkCheck size={17} color="#60a5fa" />
        </button>
      </div>

      {/* Step Selector */}
      <div className="px-5 py-4">
        <div className="flex items-center gap-2 overflow-x-auto pb-1" style={{ scrollbarWidth: 'none' }}>
          {recipe.steps.map((step, idx) => {
            const dur = getStepDuration(step);
            const isActive = idx === activeStepIdx;
            return (
              <button
                key={step.id}
                onClick={() => setActiveStepIdx(idx)}
                className="flex-shrink-0 rounded-xl px-3 py-2 text-center transition-all active:scale-95"
                style={{
                  background: isActive
                    ? 'linear-gradient(135deg, #1e40af, #3b82f6)'
                    : 'rgba(255,255,255,0.05)',
                  border: `1px solid ${isActive ? 'transparent' : 'rgba(255,255,255,0.1)'}`,
                  boxShadow: isActive ? '0 4px 16px rgba(59,130,246,0.3)' : 'none',
                }}
              >
                <div style={{ fontSize: '12px', color: isActive ? '#fff' : '#64748b', fontWeight: 600 }}>
                  Step {idx + 1}
                </div>
                <div style={{ fontSize: '10px', color: isActive ? 'rgba(255,255,255,0.7)' : '#475569' }}>
                  {formatDuration(dur)}
                </div>
              </button>
            );
          })}
          <button
            onClick={addStep}
            className="flex-shrink-0 w-16 rounded-xl py-2 flex flex-col items-center justify-center gap-0.5 active:scale-95 transition-transform"
            style={{
              background: 'rgba(255,255,255,0.04)',
              border: '1px dashed rgba(255,255,255,0.15)',
            }}
          >
            <Plus size={14} color="#475569" />
            <span style={{ fontSize: '10px', color: '#475569' }}>Add</span>
          </button>
        </div>
      </div>

      {/* Step Actions */}
      <div className="flex items-center gap-2 px-5 mb-3">
        <button
          onClick={() => duplicateStep(activeStepIdx)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg active:scale-95 transition-transform"
          style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}
        >
          <Copy size={13} color="#64748b" />
          <span style={{ fontSize: '12px', color: '#64748b' }}>Duplicate</span>
        </button>
        <button
          onClick={() => deleteStep(activeStepIdx)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg active:scale-95 transition-transform"
          style={{
            background: recipe.steps.length <= 1 ? 'rgba(255,255,255,0.03)' : 'rgba(248,113,113,0.08)',
            border: '1px solid rgba(248,113,113,0.15)',
            opacity: recipe.steps.length <= 1 ? 0.4 : 1,
          }}
        >
          <Trash2 size={13} color="#f87171" />
          <span style={{ fontSize: '12px', color: '#f87171' }}>Delete</span>
        </button>
      </div>

      {/* Module Cards */}
      <div className="px-5 pb-4 flex-1">
        {activeStep && (
          <>
            <MicrowaveCard
              settings={activeStep.microwave}
              onChange={(s) => updateStep({ ...activeStep, microwave: s })}
            />
            <InductionCard
              settings={activeStep.induction}
              onChange={(s) => updateStep({ ...activeStep, induction: s })}
            />
            <StirrerCard
              settings={activeStep.stirrer}
              onChange={(s) => updateStep({ ...activeStep, stirrer: s })}
            />
            <WaterCard
              settings={activeStep.water}
              onChange={(s) => updateStep({ ...activeStep, water: s })}
            />
          </>
        )}
      </div>

      {/* Bottom CTA */}
      <div
        className="sticky bottom-0 px-5 py-4"
        style={{
          background: 'rgba(5,14,34,0.95)',
          backdropFilter: 'blur(20px)',
          borderTop: '1px solid rgba(255,255,255,0.06)',
        }}
      >
        <button
          onClick={handleStartCooking}
          className="w-full rounded-2xl py-4 flex items-center justify-center gap-2 active:scale-95 transition-transform"
          style={{
            background: 'linear-gradient(135deg, #1d4ed8 0%, #3b82f6 50%, #60a5fa 100%)',
            boxShadow: '0 8px 32px rgba(59,130,246,0.4)',
          }}
        >
          <PlayCircle size={20} color="#fff" />
          <span style={{ fontSize: '16px', color: '#fff', fontWeight: 700 }}>Start Cooking</span>
        </button>
      </div>
    </div>
  );
}