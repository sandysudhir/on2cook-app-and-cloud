import { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router';
import {
  X,
  Pause,
  Play,
  StopCircle,
  Zap,
  Waves,
  Droplets,
  RotateCcw,
  CheckCircle2,
  Circle,
  Loader2,
} from 'lucide-react';
import { useApp } from '../../AppContext';
import {
  Recipe,
  RecipeStep,
  getStepDuration,
  getRecipeTotalDuration,
  formatTime,
} from '../../types';

// ── Ring definitions ──────────────────────────────────────────────────────────
const RINGS = [
  { key: 'total', label: 'Total', color: '#60a5fa', radius: 118, strokeWidth: 10 },
  { key: 'microwave', label: 'Microwave', color: '#fbbf24', radius: 98, strokeWidth: 9 },
  { key: 'induction', label: 'Induction', color: '#f87171', radius: 78, strokeWidth: 9 },
  { key: 'stirrer', label: 'Stirrer', color: '#c084fc', radius: 58, strokeWidth: 8 },
  { key: 'water', label: 'Water', color: '#22d3ee', radius: 38, strokeWidth: 8 },
] as const;

const SVG_SIZE = 260;
const CX = SVG_SIZE / 2;
const CY = SVG_SIZE / 2;

function getArcProgress(radius: number, progress: number): string {
  const circumference = 2 * Math.PI * radius;
  return circumference.toString();
}

function getArcOffset(radius: number, progress: number): number {
  const circumference = 2 * Math.PI * radius;
  return circumference * (1 - Math.min(1, Math.max(0, progress)));
}

// ── Concentric Rings SVG ──────────────────────────────────────────────────────
function CookingRings({
  totalProgress,
  microwaveProgress,
  inductionProgress,
  stirrerProgress,
  waterProgress,
  microwaveActive,
  inductionActive,
  stirrerActive,
  waterActive,
}: {
  totalProgress: number;
  microwaveProgress: number;
  inductionProgress: number;
  stirrerProgress: number;
  waterProgress: number;
  microwaveActive: boolean;
  inductionActive: boolean;
  stirrerActive: boolean;
  waterActive: boolean;
}) {
  const progresses = {
    total: totalProgress,
    microwave: microwaveProgress,
    induction: inductionProgress,
    stirrer: stirrerProgress,
    water: waterProgress,
  };
  const actives = {
    total: true,
    microwave: microwaveActive,
    induction: inductionActive,
    stirrer: stirrerActive,
    water: waterActive,
  };

  return (
    <svg
      width={SVG_SIZE}
      height={SVG_SIZE}
      viewBox={`0 0 ${SVG_SIZE} ${SVG_SIZE}`}
      style={{ overflow: 'visible' }}
    >
      <defs>
        {RINGS.map((ring) => (
          <filter key={`glow-${ring.key}`} id={`glow-${ring.key}`} x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="3" result="coloredBlur" />
            <feMerge>
              <feMergeNode in="coloredBlur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        ))}
      </defs>

      {RINGS.map((ring) => {
        const prog = progresses[ring.key] ?? 0;
        const isActive = actives[ring.key] ?? false;
        const circumference = 2 * Math.PI * ring.radius;
        const dashoffset = getArcOffset(ring.radius, prog);
        const enabled = ring.key === 'total' || prog > 0 || isActive;

        return (
          <g key={ring.key}>
            {/* Background track */}
            <circle
              cx={CX}
              cy={CY}
              r={ring.radius}
              fill="none"
              stroke={ring.color}
              strokeWidth={ring.strokeWidth}
              opacity={enabled ? 0.12 : 0.05}
            />
            {/* Progress arc */}
            {enabled && (
              <circle
                cx={CX}
                cy={CY}
                r={ring.radius}
                fill="none"
                stroke={ring.color}
                strokeWidth={ring.strokeWidth}
                strokeDasharray={circumference}
                strokeDashoffset={dashoffset}
                strokeLinecap="round"
                transform={`rotate(-90, ${CX}, ${CY})`}
                opacity={prog > 0 ? 1 : 0.15}
                filter={isActive && prog > 0 ? `url(#glow-${ring.key})` : undefined}
                style={{ transition: 'stroke-dashoffset 1s linear' }}
              />
            )}
            {/* Glow dot at progress head */}
            {isActive && prog > 0 && prog < 1 && (
              (() => {
                const angle = -90 + prog * 360;
                const rad = (angle * Math.PI) / 180;
                const x = CX + ring.radius * Math.cos(rad);
                const y = CY + ring.radius * Math.sin(rad);
                return (
                  <circle
                    cx={x}
                    cy={y}
                    r={ring.strokeWidth / 2 + 1}
                    fill={ring.color}
                    filter={`url(#glow-${ring.key})`}
                  />
                );
              })()
            )}
          </g>
        );
      })}

      {/* Water pulse dots */}
      {waterActive && (
        <>
          {[0, 120, 240].map((offset) => {
            const angle = ((totalProgress * 360 + offset) % 360 - 90) * (Math.PI / 180);
            const r = RINGS[4].radius;
            const x = CX + r * Math.cos(angle);
            const y = CY + r * Math.sin(angle);
            return (
              <circle key={offset} cx={x} cy={y} r={3} fill="#22d3ee" opacity={0.6} />
            );
          })}
        </>
      )}
    </svg>
  );
}

// ── Status Badge ──────────────────────────────────────────────────────────────
function ModuleBadge({
  icon,
  label,
  active,
  color,
  detail,
}: {
  icon: React.ReactNode;
  label: string;
  active: boolean;
  color: string;
  detail?: string;
}) {
  return (
    <div
      className="flex flex-col items-center gap-1 rounded-xl px-3 py-2.5"
      style={{
        background: active ? `${color}12` : 'rgba(255,255,255,0.7)',
        border: `1px solid ${active ? color + '35' : 'rgba(148,163,184,0.2)'}`,
        minWidth: 64,
        boxShadow: active ? `0 2px 12px ${color}18` : '0 1px 3px rgba(0,0,0,0.05)',
        transition: 'all 0.3s',
      }}
    >
      <div style={{ color: active ? color : '#94a3b8' }}>{icon}</div>
      <span style={{ fontSize: '10px', color: active ? color : '#94a3b8', fontWeight: 600 }}>{label}</span>
      {detail && (
        <span style={{ fontSize: '9px', color: active ? `${color}cc` : '#94a3b8' }}>{detail}</span>
      )}
    </div>
  );
}

// ── Step Timeline ─────────────────────────────────────────────────────────────
function StepTimeline({ steps, currentIdx, completedIdx }: {
  steps: RecipeStep[];
  currentIdx: number;
  completedIdx: number[];
}) {
  return (
    <div className="flex items-center gap-0 px-6">
      {steps.map((step, idx) => {
        const isCompleted = completedIdx.includes(idx);
        const isCurrent = idx === currentIdx;
        const isUpcoming = !isCompleted && !isCurrent;
        return (
          <div key={step.id} className="flex items-center flex-1">
            <div className="flex flex-col items-center gap-1">
              <div
                className="w-7 h-7 rounded-full flex items-center justify-center"
                style={{
                  background: isCompleted
                    ? 'rgba(22,163,74,0.15)'
                    : isCurrent
                    ? 'rgba(37,99,235,0.15)'
                    : 'rgba(148,163,184,0.1)',
                  border: `1px solid ${isCompleted ? 'rgba(22,163,74,0.4)' : isCurrent ? 'rgba(37,99,235,0.4)' : 'rgba(148,163,184,0.25)'}`,
                  boxShadow: isCurrent ? '0 0 10px rgba(37,99,235,0.2)' : 'none',
                }}
              >
                {isCompleted ? (
                  <CheckCircle2 size={14} color="#16a34a" />
                ) : isCurrent ? (
                  <Loader2 size={14} color="#2563eb" className="animate-spin" />
                ) : (
                  <Circle size={14} color="#cbd5e1" />
                )}
              </div>
              <span
                style={{
                  fontSize: '9px',
                  color: isCompleted ? '#16a34a' : isCurrent ? '#2563eb' : '#94a3b8',
                  fontWeight: isCurrent ? 700 : 400,
                }}
              >
                {idx + 1}
              </span>
            </div>
            {idx < steps.length - 1 && (
              <div
                className="flex-1 h-px mx-1"
                style={{
                  background: isCompleted
                    ? 'rgba(22,163,74,0.35)'
                    : 'rgba(148,163,184,0.2)',
                }}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}

// ── Main Screen ───────────────────────────────────────────────────────────────
export function LiveCookingScreen() {
  const navigate = useNavigate();
  const { cookingState, setCookingState } = useApp();
  const [elapsed, setElapsed] = useState(0);
  const [isPaused, setIsPaused] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const recipe = cookingState?.recipe;

  useEffect(() => {
    if (!recipe) { navigate('/'); return; }
  }, [recipe]);

  useEffect(() => {
    if (!recipe) return;
    if (isPaused) {
      if (intervalRef.current) clearInterval(intervalRef.current);
      return;
    }
    intervalRef.current = setInterval(() => {
      setElapsed((prev) => {
        const total = getRecipeTotalDuration(recipe);
        if (prev >= total) {
          clearInterval(intervalRef.current!);
          return prev;
        }
        return prev + 1;
      });
    }, 1000);
    return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
  }, [recipe, isPaused]);

  if (!recipe) return null;

  // Determine current step
  const totalDuration = getRecipeTotalDuration(recipe);
  let accum = 0;
  let currentStepIdx = 0;
  let stepElapsed = elapsed;
  const completedSteps: number[] = [];

  for (let i = 0; i < recipe.steps.length; i++) {
    const dur = getStepDuration(recipe.steps[i]);
    if (elapsed >= accum + dur) {
      completedSteps.push(i);
      accum += dur;
    } else {
      currentStepIdx = i;
      stepElapsed = elapsed - accum;
      break;
    }
  }

  if (elapsed >= totalDuration) {
    currentStepIdx = recipe.steps.length - 1;
    stepElapsed = getStepDuration(recipe.steps[currentStepIdx]);
  }

  const currentStep = recipe.steps[currentStepIdx];
  const currentStepDuration = getStepDuration(currentStep);
  const totalProgress = elapsed / totalDuration;
  const isFinished = elapsed >= totalDuration;

  // Module progress within current step
  const mw = currentStep.microwave;
  const ind = currentStep.induction;
  const str = currentStep.stirrer;
  const wat = currentStep.water;

  const mwActive = mw.enabled && stepElapsed >= mw.startDelay && stepElapsed < mw.startDelay + mw.duration;
  const indActive = ind.enabled && stepElapsed >= ind.startDelay && stepElapsed < ind.startDelay + ind.duration;
  const strActive = str.enabled && stepElapsed < str.duration;
  const watActive = wat.enabled && stepElapsed >= wat.triggerAt && stepElapsed < wat.triggerAt + 15;

  const mwProgress = mw.enabled ? Math.min(1, Math.max(0, (stepElapsed - mw.startDelay) / mw.duration)) : 0;
  const indProgress = ind.enabled ? Math.min(1, Math.max(0, (stepElapsed - ind.startDelay) / ind.duration)) : 0;
  const strProgress = str.enabled ? Math.min(1, stepElapsed / str.duration) : 0;
  const watProgress = wat.enabled && stepElapsed >= wat.triggerAt ? Math.min(1, (stepElapsed - wat.triggerAt) / 15) : 0;

  const remaining = Math.max(0, totalDuration - elapsed);

  const activeModuleNames = [
    mwActive && 'Microwave',
    indActive && 'Induction',
    strActive && 'Stirrer',
    watActive && 'Water',
  ].filter(Boolean) as string[];

  function handleStop() {
    setCookingState(null);
    navigate('/');
  }

  return (
    <div
      className="flex flex-col min-h-screen px-5 pt-12 pb-8"
      style={{ position: 'relative', overflow: 'hidden', background: 'linear-gradient(160deg, #f0f4ff 0%, #f7f9ff 60%, #fafbff 100%)' }}
    >
      {/* Background glow */}
      <div
        style={{
          position: 'absolute',
          top: '20%',
          left: '50%',
          transform: 'translateX(-50%)',
          width: 300,
          height: 300,
          borderRadius: '50%',
          background: isFinished
            ? 'radial-gradient(circle, rgba(22,163,74,0.08) 0%, transparent 70%)'
            : 'radial-gradient(circle, rgba(37,99,235,0.08) 0%, transparent 70%)',
          pointerEvents: 'none',
          transition: 'background 1s',
        }}
      />

      {/* Header */}
      <div className="flex items-center justify-between mb-6 relative z-10">
        <button
          onClick={handleStop}
          className="w-9 h-9 rounded-xl flex items-center justify-center"
          style={{ background: 'rgba(220,38,38,0.08)', border: '1px solid rgba(220,38,38,0.2)', boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}
        >
          <X size={17} color="#dc2626" />
        </button>
        <div className="text-center">
          <p style={{ fontSize: '11px', color: '#94a3b8', fontWeight: 500 }}>NOW COOKING</p>
          <p style={{ fontSize: '14px', color: '#0f172a', fontWeight: 700 }}>{recipe.name}</p>
        </div>
        <div
          className="px-2.5 py-1 rounded-lg"
          style={{
            background: isFinished
              ? 'rgba(22,163,74,0.1)'
              : isPaused
              ? 'rgba(217,119,6,0.1)'
              : 'rgba(37,99,235,0.1)',
            border: `1px solid ${isFinished ? 'rgba(22,163,74,0.3)' : isPaused ? 'rgba(217,119,6,0.3)' : 'rgba(37,99,235,0.3)'}`,
          }}
        >
          <span
            style={{
              fontSize: '11px',
              fontWeight: 600,
              color: isFinished ? '#16a34a' : isPaused ? '#d97706' : '#2563eb',
            }}
          >
            {isFinished ? 'Done ✓' : isPaused ? 'Paused' : 'Active'}
          </span>
        </div>
      </div>

      {/* Concentric Rings */}
      <div className="flex items-center justify-center mb-4 relative z-10">
        <div style={{ position: 'relative', width: SVG_SIZE, height: SVG_SIZE }}>
          <CookingRings
            totalProgress={totalProgress}
            microwaveProgress={mwProgress}
            inductionProgress={indProgress}
            stirrerProgress={strProgress}
            waterProgress={watProgress}
            microwaveActive={mwActive}
            inductionActive={indActive}
            stirrerActive={strActive}
            waterActive={watActive}
          />
          {/* Center info */}
          <div
            style={{
              position: 'absolute',
              inset: 0,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <span style={{ fontSize: '10px', color: '#64748b', fontWeight: 500 }}>
              Step {currentStepIdx + 1} of {recipe.steps.length}
            </span>
            <span
              style={{
                fontSize: '34px',
                color: isFinished ? '#16a34a' : '#0f172a',
                fontWeight: 700,
                letterSpacing: '-1px',
                lineHeight: 1.1,
                transition: 'color 0.5s',
              }}
            >
              {isFinished ? '✓' : formatTime(remaining)}
            </span>
            <span style={{ fontSize: '10px', color: '#94a3b8', marginTop: 2 }}>
              {isFinished ? 'Cooking Complete!' : 'remaining'}
            </span>
            {!isFinished && activeModuleNames.length > 0 && (
              <div
                className="mt-2 px-2 py-0.5 rounded-full"
                style={{ background: 'rgba(37,99,235,0.08)', border: '1px solid rgba(37,99,235,0.2)' }}
              >
                <span style={{ fontSize: '9px', color: '#2563eb', fontWeight: 500 }}>
                  {activeModuleNames.join(' + ')}
                </span>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Ring Legend */}
      <div className="flex justify-center gap-3 mb-5 flex-wrap">
        {RINGS.map((ring) => (
          <div key={ring.key} className="flex items-center gap-1.5">
            <div style={{ width: 8, height: 8, borderRadius: '50%', background: ring.color }} />
            <span style={{ fontSize: '10px', color: '#64748b' }}>{ring.label}</span>
          </div>
        ))}
      </div>

      {/* Module Status */}
      <div className="flex justify-between gap-2 mb-5 relative z-10">
        <ModuleBadge
          icon={<Zap size={16} />}
          label="Micro"
          active={mwActive}
          color="#d97706"
          detail={mw.enabled ? `${mw.power}%` : '—'}
        />
        <ModuleBadge
          icon={<Waves size={16} />}
          label="Induct"
          active={indActive}
          color="#dc2626"
          detail={ind.enabled ? `${ind.power}%` : '—'}
        />
        <ModuleBadge
          icon={<RotateCcw size={16} />}
          label="Stirrer"
          active={strActive}
          color="#7c3aed"
          detail={str.enabled ? `L${str.speed}` : '—'}
        />
        <ModuleBadge
          icon={<Droplets size={16} />}
          label="Water"
          active={watActive}
          color="#0891b2"
          detail={wat.enabled ? `${wat.amount}ml` : '—'}
        />
      </div>

      {/* Step Timeline */}
      <div
        className="rounded-2xl p-4 mb-5 relative z-10"
        style={{
          background: 'rgba(255,255,255,0.8)',
          border: '1px solid rgba(148,163,184,0.2)',
          boxShadow: '0 2px 8px rgba(0,0,0,0.05)',
        }}
      >
        <p style={{ fontSize: '11px', color: '#64748b', fontWeight: 500, marginBottom: 12 }}>
          STEP PROGRESS
        </p>
        <StepTimeline
          steps={recipe.steps}
          currentIdx={currentStepIdx}
          completedIdx={completedSteps}
        />
        <div className="flex justify-between mt-3 px-1">
          <span style={{ fontSize: '10px', color: '#94a3b8' }}>
            Elapsed: {formatTime(elapsed)}
          </span>
          <span style={{ fontSize: '10px', color: '#94a3b8' }}>
            Total: {formatTime(totalDuration)}
          </span>
        </div>
      </div>

      {/* Controls */}
      {isFinished ? (
        <button
          onClick={handleStop}
          className="w-full rounded-2xl py-4 flex items-center justify-center gap-2 active:scale-95 transition-transform"
          style={{
            background: 'linear-gradient(135deg, #15803d, #16a34a)',
            boxShadow: '0 8px 24px rgba(22,163,74,0.25)',
          }}
        >
          <CheckCircle2 size={20} color="#fff" />
          <span style={{ fontSize: '16px', color: '#fff', fontWeight: 700 }}>Done — Return Home</span>
        </button>
      ) : (
        <div className="flex gap-3 relative z-10">
          <button
            onClick={() => setIsPaused((p) => !p)}
            className="flex-1 rounded-2xl py-4 flex items-center justify-center gap-2 active:scale-95 transition-transform"
            style={{
              background: isPaused ? 'rgba(37,99,235,0.1)' : 'rgba(217,119,6,0.08)',
              border: `1px solid ${isPaused ? 'rgba(37,99,235,0.3)' : 'rgba(217,119,6,0.28)'}`,
              boxShadow: '0 1px 6px rgba(0,0,0,0.05)',
            }}
          >
            {isPaused ? (
              <><Play size={18} color="#2563eb" /><span style={{ fontSize: '15px', color: '#2563eb', fontWeight: 700 }}>Resume</span></>
            ) : (
              <><Pause size={18} color="#d97706" /><span style={{ fontSize: '15px', color: '#d97706', fontWeight: 700 }}>Pause</span></>
            )}
          </button>
          <button
            onClick={handleStop}
            className="w-14 rounded-2xl flex items-center justify-center active:scale-95 transition-transform"
            style={{
              background: 'rgba(220,38,38,0.08)',
              border: '1px solid rgba(220,38,38,0.25)',
            }}
          >
            <StopCircle size={20} color="#dc2626" />
          </button>
        </div>
      )}
    </div>
  );
}