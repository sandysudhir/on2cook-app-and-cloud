import React, { useState, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router';
import {
  Play, Plus, Trash2, Undo2, Redo2, Save,
  Copy, ChevronLeft, ChevronRight, X, Check, AlertTriangle,
  PanelRightClose, PanelRightOpen, Leaf, FlameKindling, Settings2, RefreshCw,
} from 'lucide-react';
import { useApp } from '../../AppContext';
import {
  ProMinute, WaterBlocks, StirrerSpeedType,
  createDefaultMinute, getMinuteWaterMl, getActiveWaterBlocks, DEFAULT_PRO_RECIPE,
  VeganType, ProRecipeType, ConsistencyType,
} from '../../proTypes';
import { generateTimeline, TimelineFactors } from '../../presetRecipes';

// ─── Landscape layout constants ───────────────────────────────────────────────
const CELL_W = 76;
const ROW_H = 44;
const LABEL_W = 54;
const HEADER_H = 26;
const INSPECTOR_W = 214;
const INSPECTOR_COLLAPSED_W = 30;
const DI_SAFE = 50;
const POWER_STEPS = [0, 40, 60, 80, 100];

// ─── Color helpers ────────────────────────────────────────────────────────────
function indColor(p: number): string {
  if (!p) return 'rgba(148,163,184,0.18)';
  if (p <= 40) return '#d97706'; if (p <= 60) return '#f97316';
  if (p <= 80) return '#ef4444'; return '#dc2626';
}
function indBg(p: number): string {
  if (!p) return 'rgba(148,163,184,0.06)';
  if (p <= 40) return 'rgba(217,119,6,0.12)'; if (p <= 60) return 'rgba(249,115,22,0.12)';
  if (p <= 80) return 'rgba(239,68,68,0.12)'; return 'rgba(220,38,38,0.15)';
}
const MW_ON_COLOR = '#ec4899';
const MW_ON_BG = 'rgba(236,72,153,0.10)';
const MW_OFF_BG = 'rgba(148,163,184,0.06)';

// ─── Stirrer speed → bar height + color ──────────────────────────────────────
const STIR_BAR_H: Record<StirrerSpeedType, number> = {
  'low': 8, 'medium': 15, 'high': 22, 'very-high': 30,
};
const STIR_COLOR: Record<StirrerSpeedType, string> = {
  'low': '#5eead4', 'medium': '#2dd4bf', 'high': '#14b8a6', 'very-high': '#0d9488',
};
const STIR_LABEL: Record<StirrerSpeedType, string> = {
  'low': 'Lo', 'medium': 'Md', 'high': 'Hi', 'very-high': 'VH',
};
const STIR_FULL_LABEL: Record<StirrerSpeedType, string> = {
  'low': 'Low', 'medium': 'Medium', 'high': 'High', 'very-high': 'Very High',
};
const STIR_SPEEDS: StirrerSpeedType[] = ['low', 'medium', 'high', 'very-high'];

function stirrerBarH(active: boolean, speed: StirrerSpeedType): number {
  return active ? (STIR_BAR_H[speed] ?? 15) : 4;
}
function stirrerBarColor(active: boolean, speed: StirrerSpeedType): string {
  return active ? (STIR_COLOR[speed] ?? '#14b8a6') : 'rgba(148,163,184,0.22)';
}

// ─── Row metadata ─────────────────────────────────────────────────────────────
type RowKey = 'lid' | 'induction' | 'microwave' | 'stirrer' | 'water';
const ROW_META: { key: RowKey; label: string; letter: string; color: string }[] = [
  { key: 'lid',       label: 'Lid',       letter: 'L', color: '#4ade80' },
  { key: 'induction', label: 'Induction', letter: 'I', color: '#f97316' },
  { key: 'microwave', label: 'Microwave', letter: 'M', color: '#ec4899' },
  { key: 'stirrer',   label: 'Stirrer',   letter: 'S', color: '#14b8a6' },
  { key: 'water',     label: 'Water',     letter: 'W', color: '#22d3ee' },
];

interface CellRef { minuteIdx: number; row: RowKey }
type EditModal = 'lid' | 'induction' | 'microwave' | 'stirrer' | 'water' | null;

// ─── Shared button styles ─────────────────────────────────────────────────────
const iconBtn: React.CSSProperties = {
  width: 30, height: 30, borderRadius: 8, cursor: 'pointer',
  display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
  background: 'rgba(255,255,255,0.8)', border: '1px solid rgba(148,163,184,0.25)',
};

// ─── Mini Cell Components ─────────────────────────────────────────────────────

function LidCell({ minute, selected, onClick }: { minute: ProMinute; selected: boolean; onClick: () => void }) {
  return (
    <div onClick={onClick} style={{
      width: CELL_W, height: ROW_H, cursor: 'pointer', flexShrink: 0,
      background: minute.lidOpen ? 'rgba(74,222,128,0.12)' : 'rgba(148,163,184,0.05)',
      border: `1px solid ${selected ? '#4ade80' : minute.lidOpen ? 'rgba(74,222,128,0.35)' : 'rgba(148,163,184,0.15)'}`,
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 2,
      boxShadow: selected ? '0 0 0 1px #4ade8055' : 'none',
    }}>
      {minute.lidOpen ? (
        <>
          <span style={{ fontSize: 9, color: '#16a34a', fontWeight: 700 }}>OPEN</span>
          {minute.ingredients.length > 0 && <span style={{ fontSize: 8, color: 'rgba(22,163,74,0.7)' }}>+{minute.ingredients.length}</span>}
          <span style={{ fontSize: 7, color: 'rgba(22,163,74,0.6)' }}>{minute.lidOpenDuration}s</span>
        </>
      ) : (
        <span style={{ fontSize: 9, color: 'rgba(100,116,139,0.4)' }}>—</span>
      )}
    </div>
  );
}

function InductionCell({ minute, selected, onClick }: { minute: ProMinute; selected: boolean; onClick: () => void }) {
  const avg = Math.round(minute.subBlocks.reduce((a, b) => a + b.inductionPower, 0) / 4);
  return (
    <div onClick={onClick} style={{
      width: CELL_W, height: ROW_H, cursor: 'pointer', flexShrink: 0, position: 'relative',
      background: indBg(avg),
      border: `1px solid ${selected ? '#f97316' : 'rgba(148,163,184,0.15)'}`,
      display: 'flex', alignItems: 'flex-end', padding: '3px 3px', gap: 1,
      boxShadow: selected ? '0 0 0 1px rgba(249,115,22,0.4)' : 'none',
    }}>
      {minute.subBlocks.map((sb, i) => {
        const h = Math.max(2, Math.round((sb.inductionPower / 100) * (ROW_H - 10)));
        return <div key={i} style={{ flex: 1, height: h, borderRadius: '2px 2px 0 0', background: indColor(sb.inductionPower), opacity: 0.88 }} />;
      })}
      {avg > 0 && <span style={{ position: 'absolute', top: 3, right: 4, fontSize: 9, color: '#d97706', fontWeight: 700 }}>{avg}</span>}
    </div>
  );
}

function MicrowaveCell({ minute, selected, onClick }: { minute: ProMinute; selected: boolean; onClick: () => void }) {
  const forcedOff = minute.lidOpen;
  const onCount = forcedOff ? 0 : minute.subBlocks.filter(s => s.microwaveActive).length;
  const anyOn = onCount > 0;
  const pct = Math.round((onCount / 4) * 100);
  return (
    <div onClick={onClick} style={{
      width: CELL_W, height: ROW_H, cursor: 'pointer', flexShrink: 0, position: 'relative',
      background: anyOn ? MW_ON_BG : MW_OFF_BG,
      border: `1px solid ${selected ? '#ec4899' : 'rgba(148,163,184,0.15)'}`,
      display: 'flex', alignItems: 'flex-end', padding: '3px 3px', gap: 1,
      boxShadow: selected ? '0 0 0 1px rgba(236,72,153,0.4)' : 'none',
    }}>
      {forcedOff ? (
        <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <span style={{ fontSize: 8, color: 'rgba(236,72,153,0.4)', fontWeight: 700 }}>OFF</span>
        </div>
      ) : (
        minute.subBlocks.map((sb, i) => {
          const h = sb.microwaveActive ? ROW_H - 10 : 4;
          return (
            <div key={i} style={{
              flex: 1,
              height: h,
              borderRadius: '2px 2px 0 0',
              background: sb.microwaveActive ? MW_ON_COLOR : 'rgba(148,163,184,0.2)',
              boxShadow: sb.microwaveActive ? `0 0 6px rgba(236,72,153,0.45)` : 'none',
              transition: 'height 0.15s',
              opacity: sb.microwaveActive ? 0.9 : 1,
            }} />
          );
        })
      )}
      {anyOn && (
        <span style={{ position: 'absolute', top: 3, right: 4, fontSize: 8, color: '#ec4899', fontWeight: 700 }}>{pct}%</span>
      )}
    </div>
  );
}

function StirrerCell({ minute, selected, onClick }: { minute: ProMinute; selected: boolean; onClick: () => void }) {
  const anyActive = minute.subBlocks.some(s => s.stirrerActive);
  return (
    <div onClick={onClick} style={{
      width: CELL_W, height: ROW_H, cursor: 'pointer', flexShrink: 0,
      background: anyActive ? 'rgba(20,184,166,0.08)' : 'rgba(148,163,184,0.05)',
      border: `1px solid ${selected ? '#14b8a6' : 'rgba(148,163,184,0.15)'}`,
      display: 'flex', alignItems: 'center', padding: '0 5px', gap: 2,
      boxShadow: selected ? '0 0 0 1px rgba(20,184,166,0.35)' : 'none',
    }}>
      {minute.subBlocks.map((sb, i) => {
        const h = stirrerBarH(sb.stirrerActive, sb.stirrerSpeed);
        const col = stirrerBarColor(sb.stirrerActive, sb.stirrerSpeed);
        return (
          <div key={i} style={{
            flex: 1, height: h, borderRadius: 3,
            background: col,
            boxShadow: sb.stirrerActive ? `0 0 4px ${col}80` : 'none',
            transition: 'height 0.15s',
          }} />
        );
      })}
    </div>
  );
}

function WaterCell({ minute, selected, onClick }: { minute: ProMinute; selected: boolean; onClick: () => void }) {
  const forcedOff = minute.lidOpen;
  const activeCount = forcedOff ? 0 : getActiveWaterBlocks(minute.waterBlocks);
  const totalMl = forcedOff ? 0 : getMinuteWaterMl(minute.waterBlocks);
  const blockW = Math.floor(CELL_W / 4);

  return (
    <div onClick={forcedOff ? undefined : onClick} style={{
      width: CELL_W, height: ROW_H, cursor: forcedOff ? 'not-allowed' : 'pointer', flexShrink: 0, position: 'relative',
      background: forcedOff ? 'rgba(148,163,184,0.05)' : activeCount > 0 ? 'rgba(6,182,212,0.07)' : 'rgba(148,163,184,0.05)',
      border: `1px solid ${selected && !forcedOff ? '#06b6d4' : activeCount > 0 ? 'rgba(6,182,212,0.25)' : 'rgba(148,163,184,0.15)'}`,
      boxShadow: selected && !forcedOff ? '0 0 0 1px rgba(6,182,212,0.35)' : 'none',
      display: 'flex', alignItems: 'center', padding: '0 3px', gap: 2,
      opacity: forcedOff ? 0.4 : 1,
    }}>
      {forcedOff ? (
        <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <span style={{ fontSize: 7, color: 'rgba(148,163,184,0.6)', fontWeight: 700 }}>OFF</span>
        </div>
      ) : (
        <>
          {minute.waterBlocks.map((active, i) => (
            <div key={i} style={{
              width: blockW - 2, height: active ? 28 : 6, borderRadius: 3, flexShrink: 0,
              background: active ? 'linear-gradient(180deg, #22d3ee, #0891b2)' : 'rgba(148,163,184,0.2)',
              boxShadow: active ? '0 0 6px rgba(34,211,238,0.4)' : 'none',
              transition: 'height 0.15s, background 0.15s',
            }} />
          ))}
          {totalMl > 0 && (
            <span style={{ position: 'absolute', bottom: 3, right: 3, fontSize: 8, color: '#0891b2', fontWeight: 700 }}>
              {totalMl}ml
            </span>
          )}
        </>
      )}
    </div>
  );
}


// ─── Inspector Panel (Right Side) ─────────────────────────────────────────────
function InspectorPanel({
  cell, minutes, collapsed, onToggle, onEdit,
}: {
  cell: CellRef | null; minutes: ProMinute[]; collapsed: boolean;
  onToggle: () => void; onEdit: (modal: EditModal) => void;
}) {
  const width = collapsed ? INSPECTOR_COLLAPSED_W : INSPECTOR_W;
  const minute = cell ? minutes[cell.minuteIdx] : null;
  const row = cell ? ROW_META.find(r => r.key === cell.row)! : null;

  const indAvg = minute ? Math.round(minute.subBlocks.reduce((a, b) => a + b.inductionPower, 0) / 4) : 0;
  const mwOnCount = minute ? (minute.lidOpen ? 0 : minute.subBlocks.filter(s => s.microwaveActive).length) : 0;

  return (
    <div style={{
      width, flexShrink: 0, height: '100%',
      background: 'rgba(248,250,255,0.97)',
      borderLeft: '1px solid rgba(148,163,184,0.2)',
      display: 'flex', flexDirection: 'column',
      transition: 'width 0.2s ease',
      overflow: 'hidden',
    }}>
      {/* Toggle button */}
      <button
        onClick={onToggle}
        style={{
          width: '100%', height: 36, flexShrink: 0, cursor: 'pointer',
          background: 'rgba(148,163,184,0.08)', border: 'none',
          borderBottom: '1px solid rgba(148,163,184,0.18)',
          display: 'flex', alignItems: 'center',
          justifyContent: collapsed ? 'center' : 'flex-end',
          padding: collapsed ? 0 : '0 10px', color: '#94a3b8',
        }}
      >
        {collapsed ? <PanelRightOpen size={14} /> : <PanelRightClose size={14} />}
      </button>

      {!collapsed && (
        <div style={{ flex: 1, overflowY: 'auto', padding: '12px 12px 10px' }}>
          {!cell || !minute || !row ? (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, paddingTop: 20 }}>
              <div style={{ width: 32, height: 32, borderRadius: 10, background: 'rgba(148,163,184,0.1)', border: '1px solid rgba(148,163,184,0.2)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <span style={{ fontSize: 14, opacity: 0.3 }}>☰</span>
              </div>
              <p style={{ fontSize: 10, color: '#94a3b8', textAlign: 'center', lineHeight: 1.5 }}>Tap a cell to inspect</p>
            </div>
          ) : (
            <>
              {/* Header */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                <span style={{ width: 26, height: 26, borderRadius: 7, background: `${row.color}18`, border: `1px solid ${row.color}55`, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, color: row.color, fontWeight: 700, flexShrink: 0 }}>
                  {row.letter}
                </span>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 12, color: '#1e293b', fontWeight: 700 }}>{row.label}</div>
                  <div style={{ fontSize: 9, color: '#94a3b8' }}>Min {cell.minuteIdx + 1} · {(cell.minuteIdx + 1) * 60}s total</div>
                </div>
              </div>

              {/* Content */}
              {cell.row === 'lid' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
                  <div style={{ background: minute.lidOpen ? 'rgba(74,222,128,0.08)' : 'rgba(148,163,184,0.07)', border: `1px solid ${minute.lidOpen ? 'rgba(74,222,128,0.3)' : 'rgba(148,163,184,0.2)'}`, borderRadius: 8, padding: '8px 11px' }}>
                    <div style={{ fontSize: 13, color: minute.lidOpen ? '#16a34a' : '#94a3b8', fontWeight: 700 }}>
                      {minute.lidOpen ? `OPEN · ${minute.lidOpenDuration}s` : 'CLOSED'}
                    </div>
                  </div>
                  {minute.ingredients.map(ing => {
                    const KCAL: Record<string, number> = {
                      'ghee':900,'oil':884,'olive oil':884,'coconut oil':892,'butter':717,
                      'chicken':165,'beef':250,'mutton':294,'lamb':294,'pork':242,
                      'fish':136,'salmon':208,'tuna':130,'shrimp':99,'prawn':99,
                      'egg':155,'paneer':265,'tofu':76,
                      'onion':40,'garlic':149,'ginger':80,'tomato':18,'potato':77,
                      'carrot':41,'spinach':23,'broccoli':34,'cauliflower':25,'peas':81,
                      'capsicum':20,'bell pepper':20,'mushroom':22,'zucchini':17,
                      'eggplant':25,'cabbage':25,'corn':86,'pumpkin':26,'celery':16,
                      'rice':365,'flour':364,'wheat':340,'oats':389,'bread':265,
                      'pasta':371,'noodle':138,'dal':341,'lentil':116,'chickpea':364,
                      'beans':347,'kidney bean':333,
                      'milk':61,'cream':340,'yogurt':59,'curd':98,'cheese':402,
                      'salt':0,'sugar':387,'honey':304,'soy sauce':53,
                      'tomato paste':82,'vinegar':21,'ketchup':97,
                      'cumin':375,'turmeric':354,'coriander':298,'chili':282,
                      'paprika':282,'pepper':251,'garam masala':379,'cardamom':311,
                      'cinnamon':247,'clove':274,'fenugreek':323,
                      'almond':579,'cashew':553,'peanut':567,'sesame':573,
                      'walnut':654,'pistachio':562,'water':0,
                    };
                    const PIECE_G: Record<string, number> = {
                      egg:60,onion:100,tomato:120,potato:150,garlic:5,
                      chicken:200,lemon:60,lime:60,mushroom:18,carrot:80,
                    };
                    const nameKey = ing.name.toLowerCase();
                    const dbEntry = Object.entries(KCAL).find(([k]) => nameKey.includes(k));
                    const toG = (q: number, u: string): number => {
                      if (u==='g') return q; if (u==='ml') return q;
                      if (u==='tsp') return q*5; if (u==='tbsp') return q*15;
                      if (u==='piece') { const pw=Object.entries(PIECE_G).find(([k])=>nameKey.includes(k)); return q*(pw?pw[1]:80); }
                      return q;
                    };
                    const kcal = dbEntry ? Math.round((dbEntry[1]/100)*toG(ing.quantity,ing.unit)) : null;
                    return (
                      <div key={ing.id} style={{ background: 'rgba(148,163,184,0.08)', border: '1px solid rgba(148,163,184,0.2)', borderRadius: 7, padding: '6px 9px' }}>
                        <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', gap:4, marginBottom:2 }}>
                          <div style={{ fontSize:11, color:'#1e293b', fontWeight:600, minWidth:0, overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>{ing.name}</div>
                          {kcal !== null && (
                            <span style={{ fontSize:9, color:'#d97706', flexShrink:0, background:'rgba(245,158,11,0.1)', border:'1px solid rgba(245,158,11,0.28)', borderRadius:4, padding:'1px 5px', fontWeight:700 }}>
                              ~{kcal} kcal
                            </span>
                          )}
                        </div>
                        <div style={{ fontSize:9, color:'#94a3b8' }}>{ing.quantity}{ing.unit}</div>
                      </div>
                    );
                  })}
                  {/* Total calorie row */}
                  {minute.ingredients.length > 0 && (() => {
                    const KCAL: Record<string,number> = {
                      'ghee':900,'oil':884,'olive oil':884,'coconut oil':892,'butter':717,
                      'chicken':165,'beef':250,'mutton':294,'lamb':294,'pork':242,
                      'fish':136,'salmon':208,'tuna':130,'shrimp':99,'prawn':99,
                      'egg':155,'paneer':265,'tofu':76,
                      'onion':40,'garlic':149,'ginger':80,'tomato':18,'potato':77,
                      'carrot':41,'spinach':23,'broccoli':34,'cauliflower':25,'peas':81,
                      'capsicum':20,'bell pepper':20,'mushroom':22,'zucchini':17,
                      'eggplant':25,'cabbage':25,'corn':86,'pumpkin':26,'celery':16,
                      'rice':365,'flour':364,'wheat':340,'oats':389,'bread':265,
                      'pasta':371,'noodle':138,'dal':341,'lentil':116,'chickpea':364,
                      'beans':347,'kidney bean':333,
                      'milk':61,'cream':340,'yogurt':59,'curd':98,'cheese':402,
                      'salt':0,'sugar':387,'honey':304,'soy sauce':53,
                      'tomato paste':82,'vinegar':21,'ketchup':97,
                      'cumin':375,'turmeric':354,'coriander':298,'chili':282,
                      'paprika':282,'pepper':251,'garam masala':379,'cardamom':311,
                      'cinnamon':247,'clove':274,'fenugreek':323,
                      'almond':579,'cashew':553,'peanut':567,'sesame':573,
                      'walnut':654,'pistachio':562,'water':0,
                    };
                    const PIECE_G: Record<string,number> = {egg:60,onion:100,tomato:120,potato:150,garlic:5,chicken:200,lemon:60,lime:60,mushroom:18,carrot:80};
                    const toG2=(q:number,u:string,nk:string):number=>{
                      if(u==='g')return q;if(u==='ml')return q;if(u==='tsp')return q*5;if(u==='tbsp')return q*15;
                      if(u==='piece'){const pw=Object.entries(PIECE_G).find(([k])=>nk.includes(k));return q*(pw?pw[1]:80);}
                      return q;
                    };
                    let total=0; let hasUnknown=false;
                    minute.ingredients.forEach(ing=>{
                      const nk=ing.name.toLowerCase();
                      const e=Object.entries(KCAL).find(([k])=>nk.includes(k));
                      if(e) total+=Math.round((e[1]/100)*toG2(ing.quantity,ing.unit,nk));
                      else hasUnknown=true;
                    });
                    if(total===0&&!hasUnknown) return null;
                    return (
                      <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', background:'rgba(245,158,11,0.07)', border:'1px solid rgba(245,158,11,0.22)', borderRadius:7, padding:'5px 9px', marginTop:2 }}>
                        <span style={{ fontSize:9, color:'#92400e', fontWeight:600 }}>Total this minute</span>
                        <span style={{ fontSize:12, color:'#d97706', fontWeight:800 }}>~{total} kcal{hasUnknown?'+':''}</span>
                      </div>
                    );
                  })()}
                </div>
              )}

              {cell.row === 'induction' && (
                <div style={{ display: 'flex', gap: 5 }}>
                  {[0, 1, 2, 3].map(i => {
                    const sb = minute.subBlocks[i];
                    const power = sb.inductionPower;
                    const color = indColor(power);
                    const barH = Math.max(3, Math.round((power / 100) * 36));
                    return (
                      <div key={i} style={{ flex: 1, background: 'rgba(148,163,184,0.08)', border: '1px solid rgba(148,163,184,0.18)', borderRadius: 8, padding: '8px 4px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 5 }}>
                        <div style={{ width: '80%', height: 38, display: 'flex', alignItems: 'flex-end', justifyContent: 'center' }}>
                          <div style={{ width: '65%', height: barH, borderRadius: '3px 3px 0 0', background: power > 0 ? color : 'rgba(148,163,184,0.2)' }} />
                        </div>
                        <div style={{ fontSize: 12, color: power > 0 ? color : '#94a3b8', fontWeight: 700 }}>{power || '—'}</div>
                        <div style={{ fontSize: 8, color: '#94a3b8' }}>{i * 15}s</div>
                      </div>
                    );
                  })}
                </div>
              )}

              {cell.row === 'microwave' && (
                <div style={{ display: 'flex', gap: 5 }}>
                  {minute.subBlocks.map((sb, i) => {
                    const on = !minute.lidOpen && sb.microwaveActive;
                    return (
                      <div key={i} style={{ flex: 1, background: on ? 'rgba(236,72,153,0.1)' : 'rgba(148,163,184,0.08)', border: `1px solid ${on ? 'rgba(236,72,153,0.35)' : 'rgba(148,163,184,0.18)'}`, borderRadius: 8, padding: '10px 4px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
                        <div style={{ width: 14, height: 14, borderRadius: '50%', background: on ? MW_ON_COLOR : 'rgba(148,163,184,0.25)', boxShadow: on ? '0 0 8px rgba(236,72,153,0.6)' : 'none', flexShrink: 0 }} />
                        <div style={{ fontSize: 12, color: on ? MW_ON_COLOR : '#94a3b8', fontWeight: 700 }}>{on ? 'ON' : 'OFF'}</div>
                        {on && <div style={{ fontSize: 8, color: '#ec4899', fontWeight: 700 }}>ON</div>}
                        <div style={{ fontSize: 8, color: '#94a3b8' }}>{i * 15}s</div>
                      </div>
                    );
                  })}
                </div>
              )}

              {cell.row === 'stirrer' && (
                <div style={{ display: 'flex', gap: 5 }}>
                  {minute.subBlocks.map((sb, i) => {
                    const h = stirrerBarH(sb.stirrerActive, sb.stirrerSpeed);
                    const col = stirrerBarColor(sb.stirrerActive, sb.stirrerSpeed);
                    return (
                      <div key={i} style={{ flex: 1, background: 'rgba(148,163,184,0.08)', border: `1px solid ${sb.stirrerActive ? 'rgba(20,184,166,0.28)' : 'rgba(148,163,184,0.18)'}`, borderRadius: 8, padding: '8px 3px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 5 }}>
                        <div style={{ width: '70%', height: 36, display: 'flex', alignItems: 'flex-end', justifyContent: 'center' }}>
                          <div style={{ width: '60%', height: h, borderRadius: '3px 3px 0 0', background: col, boxShadow: sb.stirrerActive ? `0 0 6px ${col}70` : 'none' }} />
                        </div>
                        <div style={{ fontSize: 11, color: sb.stirrerActive ? col : '#94a3b8', fontWeight: 700 }}>
                          {sb.stirrerActive ? STIR_LABEL[sb.stirrerSpeed] : 'OFF'}
                        </div>
                        <div style={{ fontSize: 8, color: '#94a3b8' }}>{i * 15}s</div>
                      </div>
                    );
                  })}
                </div>
              )}

              {cell.row === 'water' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
                  <div style={{ display: 'flex', gap: 5 }}>
                    {minute.waterBlocks.map((active, i) => (
                      <div key={i} style={{ flex: 1, background: active ? 'rgba(6,182,212,0.1)' : 'rgba(148,163,184,0.08)', border: `1px solid ${active ? 'rgba(34,211,238,0.4)' : 'rgba(148,163,184,0.18)'}`, borderRadius: 8, padding: '8px 3px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 5 }}>
                        <div style={{ width: 12, height: 12, borderRadius: '50%', background: active ? 'linear-gradient(135deg,#22d3ee,#0891b2)' : 'rgba(148,163,184,0.25)', boxShadow: active ? '0 0 7px rgba(34,211,238,0.5)' : 'none' }} />
                        <div style={{ fontSize: 11, color: active ? '#0891b2' : '#94a3b8', fontWeight: 700 }}>{active ? 'ON' : '—'}</div>
                        <div style={{ fontSize: 8, color: '#94a3b8' }}>{i * 15}s</div>
                      </div>
                    ))}
                  </div>
                  <div style={{ background: 'rgba(6,182,212,0.07)', border: '1px solid rgba(6,182,212,0.18)', borderRadius: 8, padding: '7px 10px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: 9, color: '#94a3b8' }}>{getActiveWaterBlocks(minute.waterBlocks)}/4 active</span>
                    <span style={{ fontSize: 14, color: '#0891b2', fontWeight: 700 }}>{getMinuteWaterMl(minute.waterBlocks)} ml</span>
                  </div>
                </div>
              )}

              {/* Summary chips */}
              {cell.row === 'induction' && (
                <div style={{ marginTop: 8, display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 9, color: '#f97316', background: 'rgba(249,115,22,0.1)', border: '1px solid rgba(249,115,22,0.25)', borderRadius: 5, padding: '2px 6px' }}>IH avg: {indAvg || '—'}</span>
                </div>
              )}
              {cell.row === 'microwave' && (
                <div style={{ marginTop: 8, display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 9, color: '#ec4899', background: 'rgba(236,72,153,0.1)', border: '1px solid rgba(236,72,153,0.25)', borderRadius: 5, padding: '2px 6px' }}>{mwOnCount}/4 blocks ON · {Math.round((mwOnCount / 4) * 100)}%</span>
                  {minute.lidOpen && <span style={{ fontSize: 9, color: 'rgba(236,72,153,0.6)', background: 'rgba(236,72,153,0.07)', border: '1px solid rgba(236,72,153,0.2)', borderRadius: 5, padding: '2px 6px' }}>Forced OFF (lid open)</span>}
                </div>
              )}

              {/* Edit button */}
              <button
                onClick={() => onEdit(cell.row as EditModal)}
                style={{
                  marginTop: 12, width: '100%', padding: '10px 0', borderRadius: 9,
                  cursor: 'pointer', background: `${row.color}14`,
                  border: `1px solid ${row.color}40`, color: row.color,
                  fontSize: 12, fontWeight: 700, letterSpacing: 0.3,
                }}
              >
                Edit ✎
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}

// ─── Modal Shell ──────────────────────────────────────────────────────────────
function ModalShell({ title, color, onClose, children }: { title: string; color: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div
      style={{ position: 'fixed', inset: 0, zIndex: 50, background: 'rgba(0,0,0,0.35)', backdropFilter: 'blur(4px)' }}
      onClick={onClose}
    >
      <div
        onClick={e => e.stopPropagation()}
        style={{
          position: 'absolute',
          left: DI_SAFE,
          right: 0,
          bottom: 0,
          maxHeight: '84%',
          overflowY: 'auto',
          background: 'linear-gradient(180deg,#f8faff,#f0f4ff)',
          border: '1px solid rgba(148,163,184,0.22)',
          borderTop: '1px solid rgba(148,163,184,0.3)',
          borderRadius: '16px 16px 0 0',
          paddingBottom: 18,
          boxShadow: '0 -8px 40px rgba(0,0,0,0.15)',
        }}
      >
        {/* Sticky header */}
        <div style={{
          padding: '11px 16px 10px',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          borderBottom: '1px solid rgba(148,163,184,0.18)',
          position: 'sticky', top: 0,
          background: 'rgba(248,250,255,0.98)',
          zIndex: 1,
          borderRadius: '16px 16px 0 0',
        }}>
          <span style={{ fontSize: 13, color, fontWeight: 700 }}>{title}</span>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94a3b8' }}><X size={15} /></button>
        </div>
        <div style={{ padding: '12px 16px' }}>{children}</div>
      </div>
    </div>
  );
}

function PowerPicker({ onChange, color }: { onChange: (v: number) => void; color: string }) {
  return (
    <div style={{ display: 'flex', gap: 5 }}>
      {POWER_STEPS.map(p => (
        <button key={p} onClick={() => onChange(p)} style={{ flex: 1, padding: '8px 0', borderRadius: 7, cursor: 'pointer', background: 'rgba(255,255,255,0.85)', border: '1px solid rgba(148,163,184,0.25)', color: '#64748b', fontSize: 12, fontWeight: 600 }}>
          {p === 0 ? 'OFF' : p}
        </button>
      ))}
    </div>
  );
}

function InductionEditModal({ minute, onSave, onClose }: { minute: ProMinute; onSave: (m: ProMinute) => void; onClose: () => void }) {
  const [sbs, setSbs] = useState(minute.subBlocks.map(s => s.inductionPower));
  const [sel, setSel] = useState<number[]>([]);
  const toggle = (i: number) => setSel(p => p.includes(i) ? p.filter(x => x !== i) : [...p, i]);
  const apply = (p: number) => { const n = [...sbs]; (sel.length > 0 ? sel : [0,1,2,3]).forEach(i => { n[i] = p; }); setSbs(n); };

  return (
    <ModalShell title="✎ Induction — 15s Blocks" color="#f97316" onClose={onClose}>
      <p style={{ fontSize: 10, color: '#64748b', marginBottom: 10 }}>Tap to select blocks, then set power level</p>
      <div style={{ display: 'flex', gap: 6, marginBottom: 12 }}>
        {[0,1,2,3].map(i => {
          const sel_ = sel.includes(i);
          return (
            <div key={i} onClick={() => toggle(i)} style={{ flex: 1, borderRadius: 10, cursor: 'pointer', padding: '10px 4px', background: sel_ ? 'rgba(249,115,22,0.12)' : 'rgba(148,163,184,0.08)', border: `2px solid ${sel_ ? '#f97316' : 'rgba(148,163,184,0.22)'}`, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 3 }}>
              <div style={{ width: '70%', height: Math.max(4, Math.round((sbs[i]/100)*28)), borderRadius: 3, background: sbs[i] > 0 ? indColor(sbs[i]) : 'rgba(148,163,184,0.22)' }} />
              <span style={{ fontSize: 13, color: sbs[i] > 0 ? indColor(sbs[i]) : '#94a3b8', fontWeight: 700 }}>{sbs[i] || '—'}</span>
              <span style={{ fontSize: 8, color: '#94a3b8' }}>{i*15}–{(i+1)*15}s</span>
              {sel_ && <Check size={10} color="#f97316" />}
            </div>
          );
        })}
      </div>
      <div style={{ marginBottom: 12 }}><PowerPicker onChange={apply} color="#f97316" /></div>
      <button onClick={() => { onSave({ ...minute, subBlocks: minute.subBlocks.map((sb, i) => ({ ...sb, inductionPower: sbs[i] })) as ProMinute['subBlocks'] }); onClose(); }} style={{ width: '100%', padding: '11px 0', borderRadius: 10, cursor: 'pointer', background: 'linear-gradient(135deg,#ea580c,#f97316)', border: 'none', color: '#fff', fontSize: 13, fontWeight: 700 }}>Save Induction</button>
    </ModalShell>
  );
}

function MicrowaveEditModal({ minute, onSave, onClose }: { minute: ProMinute; onSave: (m: ProMinute) => void; onClose: () => void }) {
  const [active, setActive] = useState(minute.subBlocks.map(s => s.microwaveActive));
  const onCount = active.filter(Boolean).length;
  const pct = Math.round((onCount / 4) * 100);
  const toggleAll = () => { const any = active.some(a => !a); setActive(active.map(() => any)); };

  return (
    <ModalShell title="✎ Microwave — 15s Blocks" color="#ec4899" onClose={onClose}>
      {minute.lidOpen && (
        <div style={{ background: 'rgba(236,72,153,0.07)', border: '1px solid rgba(236,72,153,0.22)', borderRadius: 8, padding: '7px 10px', marginBottom: 10, display: 'flex', gap: 5, alignItems: 'center' }}>
          <AlertTriangle size={12} color="#ec4899" />
          <span style={{ fontSize: 10, color: '#ec4899' }}>Lid open — MW forced OFF during open period</span>
        </div>
      )}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <span style={{ fontSize: 10, color: '#64748b' }}>Per 15-second segment · <span style={{ color: '#ec4899', fontWeight: 700 }}>{pct}%</span></span>
        <button onClick={toggleAll} style={{ fontSize: 10, color: '#ec4899', background: 'rgba(236,72,153,0.08)', border: '1px solid rgba(236,72,153,0.28)', borderRadius: 6, padding: '3px 8px', cursor: 'pointer' }}>Toggle All</button>
      </div>
      <div style={{ display: 'flex', gap: 6, marginBottom: 14 }}>
        {[0,1,2,3].map(i => {
          const forced = minute.lidOpen;
          const on = active[i] && !forced;
          return (
            <button
              key={i}
              onClick={() => { if (!forced) setActive(active.map((v, j) => j === i ? !v : v)); }}
              style={{
                flex: 1, height: 90, borderRadius: 10,
                cursor: forced ? 'not-allowed' : 'pointer',
                background: on ? 'rgba(236,72,153,0.12)' : 'rgba(148,163,184,0.08)',
                border: `2px solid ${on ? '#ec4899' : 'rgba(148,163,184,0.22)'}`,
                display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 5,
                opacity: forced ? 0.45 : 1,
              }}
            >
              <div style={{
                width: 18, height: 18, borderRadius: '50%',
                background: on ? MW_ON_COLOR : 'rgba(148,163,184,0.25)',
                boxShadow: on ? '0 0 12px rgba(236,72,153,0.6)' : 'none',
              }} />
              <span style={{ fontSize: 13, color: on ? MW_ON_COLOR : '#94a3b8', fontWeight: 700 }}>
                {forced ? 'OFF' : on ? 'ON' : 'OFF'}
              </span>
              <span style={{ fontSize: 8, color: '#94a3b8' }}>{i*15}–{(i+1)*15}s</span>
            </button>
          );
        })}
      </div>

      <button
        onClick={() => {
          onSave({ ...minute, subBlocks: minute.subBlocks.map((sb, i) => ({ ...sb, microwaveActive: active[i], microwavePower: 800 })) as ProMinute['subBlocks'] });
          onClose();
        }}
        style={{ width: '100%', padding: '11px 0', borderRadius: 10, cursor: 'pointer', background: 'linear-gradient(135deg,#9d174d,#ec4899)', border: 'none', color: '#fff', fontSize: 13, fontWeight: 700 }}
      >
        Save Microwave
      </button>
    </ModalShell>
  );
}

function StirrerEditModal({ minute, onSave, onClose }: { minute: ProMinute; onSave: (m: ProMinute) => void; onClose: () => void }) {
  const [sbs, setSbs] = useState(minute.subBlocks.map(s => ({ active: s.stirrerActive, speed: s.stirrerSpeed as StirrerSpeedType })));
  const toggleAll = () => { const any = sbs.some(s => !s.active); setSbs(sbs.map(s => ({ ...s, active: any }))); };

  return (
    <ModalShell title="✎ Stirrer — 15s Pattern" color="#14b8a6" onClose={onClose}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
        <span style={{ fontSize: 10, color: '#64748b' }}>Per 15-second segment</span>
        <button onClick={toggleAll} style={{ fontSize: 10, color: '#14b8a6', background: 'rgba(20,184,166,0.1)', border: '1px solid rgba(20,184,166,0.28)', borderRadius: 6, padding: '3px 8px', cursor: 'pointer' }}>Toggle All</button>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {[0,1,2,3].map(i => {
          const col = stirrerBarColor(sbs[i].active, sbs[i].speed);
          const barH = stirrerBarH(sbs[i].active, sbs[i].speed);
          return (
            <div key={i} style={{ background: 'rgba(148,163,184,0.07)', border: `1px solid ${sbs[i].active ? 'rgba(20,184,166,0.28)' : 'rgba(148,163,184,0.2)'}`, borderRadius: 10, padding: '9px 12px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <div style={{ width: 10, height: 30, display: 'flex', alignItems: 'flex-end' }}>
                    <div style={{ width: '100%', height: barH, borderRadius: '2px 2px 0 0', background: col, boxShadow: sbs[i].active ? `0 0 6px ${col}70` : 'none', transition: 'height 0.15s' }} />
                  </div>
                  <span style={{ fontSize: 12, color: '#1e293b', fontWeight: 600 }}>{i*15}–{(i+1)*15}s</span>
                  {sbs[i].active && (
                    <span style={{ fontSize: 10, color: col, fontWeight: 700 }}>{STIR_FULL_LABEL[sbs[i].speed]}</span>
                  )}
                </div>
                <button
                  onClick={() => setSbs(sbs.map((s, j) => j === i ? { ...s, active: !s.active } : s))}
                  style={{ fontSize: 11, color: sbs[i].active ? '#14b8a6' : '#94a3b8', background: sbs[i].active ? 'rgba(20,184,166,0.12)' : 'rgba(148,163,184,0.1)', border: `1px solid ${sbs[i].active ? 'rgba(20,184,166,0.38)' : 'rgba(148,163,184,0.25)'}`, borderRadius: 6, padding: '3px 10px', cursor: 'pointer', fontWeight: 600 }}
                >
                  {sbs[i].active ? 'ON' : 'OFF'}
                </button>
              </div>
              {sbs[i].active && (
                <div style={{ display: 'flex', gap: 4 }}>
                  <span style={{ fontSize: 10, color: '#64748b', alignSelf: 'center', marginRight: 2 }}>Speed:</span>
                  {STIR_SPEEDS.map(spd => {
                    const nCol = STIR_COLOR[spd];
                    const nH = STIR_BAR_H[spd];
                    return (
                      <button
                        key={spd}
                        onClick={() => setSbs(sbs.map((s, j) => j === i ? { ...s, speed: spd } : s))}
                        style={{ flex: 1, padding: '10px 0 5px', borderRadius: 7, cursor: 'pointer', fontSize: 10, fontWeight: 700, background: sbs[i].speed === spd ? `${nCol}18` : 'rgba(148,163,184,0.08)', border: `1px solid ${sbs[i].speed === spd ? nCol : 'rgba(148,163,184,0.2)'}`, color: sbs[i].speed === spd ? nCol : '#64748b', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}
                      >
                        <div style={{ width: '40%', height: Math.round(nH * 0.55) + 2, borderRadius: 2, background: sbs[i].speed === spd ? nCol : 'rgba(148,163,184,0.25)', transition: 'height 0.1s' }} />
                        {STIR_LABEL[spd]}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </div>
      <button onClick={() => { onSave({ ...minute, subBlocks: minute.subBlocks.map((sb, i) => ({ ...sb, stirrerActive: sbs[i].active, stirrerSpeed: sbs[i].speed })) as ProMinute['subBlocks'] }); onClose(); }} style={{ width: '100%', marginTop: 12, padding: '11px 0', borderRadius: 10, cursor: 'pointer', background: 'linear-gradient(135deg,#0d9488,#14b8a6)', border: 'none', color: '#fff', fontSize: 13, fontWeight: 700 }}>Save Stirrer</button>
    </ModalShell>
  );
}

function WaterEditModal({ minute, onSave, onClose }: { minute: ProMinute; onSave: (m: ProMinute) => void; onClose: () => void }) {
  const [blocks, setBlocks] = useState<WaterBlocks>([...minute.waterBlocks] as WaterBlocks);
  const activeCount = blocks.filter(Boolean).length;
  const totalMl = activeCount * 150;
  const toggleBlock = (i: number) => setBlocks(prev => {
    const next = [...prev] as WaterBlocks;
    next[i] = !next[i];
    return next;
  });
  const toggleAll = () => {
    const allOn = blocks.every(Boolean);
    setBlocks([!allOn, !allOn, !allOn, !allOn]);
  };

  return (
    <ModalShell title="💧 Water — 15s Block Editor" color="#22d3ee" onClose={onClose}>
      {minute.lidOpen && (
        <div style={{ background: 'rgba(34,211,238,0.07)', border: '1px solid rgba(34,211,238,0.22)', borderRadius: 8, padding: '7px 10px', marginBottom: 10, display: 'flex', gap: 5, alignItems: 'center' }}>
          <AlertTriangle size={12} color="#0891b2" />
          <span style={{ fontSize: 10, color: '#0891b2' }}>Lid open — water cannot be dispensed while lid is open</span>
        </div>
      )}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <p style={{ fontSize: 10, color: '#64748b', margin: 0 }}>Each block = 15 sec = 150 ml · tap to toggle</p>
        <button onClick={toggleAll} disabled={minute.lidOpen} style={{ fontSize: 10, color: '#0891b2', background: 'rgba(34,211,238,0.1)', border: '1px solid rgba(34,211,238,0.28)', borderRadius: 6, padding: '3px 9px', cursor: minute.lidOpen ? 'not-allowed' : 'pointer', opacity: minute.lidOpen ? 0.45 : 1 }}>
          {blocks.every(Boolean) ? 'Clear All' : 'All ON'}
        </button>
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        {blocks.map((active, i) => (
          <button
            key={i}
            onClick={() => !minute.lidOpen && toggleBlock(i)}
            style={{
              flex: 1, padding: '16px 4px', borderRadius: 12,
              cursor: minute.lidOpen ? 'not-allowed' : 'pointer',
              background: active && !minute.lidOpen ? 'rgba(6,182,212,0.12)' : 'rgba(148,163,184,0.08)',
              border: `2px solid ${active && !minute.lidOpen ? '#22d3ee' : 'rgba(148,163,184,0.22)'}`,
              display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8,
              boxShadow: active && !minute.lidOpen ? '0 0 14px rgba(34,211,238,0.25)' : 'none',
              opacity: minute.lidOpen ? 0.4 : 1,
              transition: 'all 0.15s',
            }}
          >
            <div style={{
              width: 28, height: 28, borderRadius: '50% 50% 50% 50% / 60% 60% 40% 40%',
              background: active ? 'linear-gradient(180deg,#22d3ee,#0891b2)' : 'rgba(148,163,184,0.25)',
              boxShadow: active ? '0 0 10px rgba(34,211,238,0.5)' : 'none',
              flexShrink: 0,
            }} />
            <div style={{ fontSize: 13, color: active ? '#0891b2' : '#94a3b8', fontWeight: 700 }}>
              {active ? 'ON' : 'OFF'}
            </div>
            <div style={{ fontSize: 9, color: active ? '#0891b2' : '#94a3b8' }}>
              {i * 15}–{(i + 1) * 15}s
            </div>
            <div style={{ fontSize: 9, color: active ? '#0891b2' : '#94a3b8', fontWeight: 600 }}>
              {active ? '150 ml' : '—'}
            </div>
            {active && <Check size={12} color="#0891b2" />}
          </button>
        ))}
      </div>

      <div style={{
        background: activeCount > 0 ? 'rgba(6,182,212,0.08)' : 'rgba(148,163,184,0.07)',
        border: `1px solid ${activeCount > 0 ? 'rgba(34,211,238,0.3)' : 'rgba(148,163,184,0.2)'}`,
        borderRadius: 10, padding: '10px 14px', marginBottom: 14,
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      }}>
        <span style={{ fontSize: 11, color: '#64748b' }}>{activeCount}/4 blocks active</span>
        <span style={{ fontSize: 20, color: activeCount > 0 ? '#0891b2' : '#94a3b8', fontWeight: 700, letterSpacing: -0.5 }}>
          {totalMl} ml
        </span>
      </div>

      <button
        onClick={() => { onSave({ ...minute, waterBlocks: blocks }); onClose(); }}
        style={{ width: '100%', padding: '12px 0', borderRadius: 10, cursor: 'pointer', background: 'linear-gradient(135deg,#0e7490,#22d3ee)', border: 'none', color: '#fff', fontSize: 13, fontWeight: 700 }}
      >
        Save Water Blocks
      </button>
    </ModalShell>
  );
}

const ING_UNITS: ProMinute['ingredients'][number]['unit'][] = ['g', 'ml', 'tsp', 'tbsp', 'piece'];

function LidEditModal({ minute, onSave, onClose }: { minute: ProMinute; onSave: (m: ProMinute) => void; onClose: () => void }) {
  const [lidOpen, setLidOpen] = useState(minute.lidOpen);
  const [duration, setDuration] = useState(minute.lidOpenDuration);
  const [ingredients, setIngredients] = useState([...minute.ingredients]);
  const [newName, setNewName] = useState('');
  const [newQty, setNewQty] = useState(100);
  const [newUnit, setNewUnit] = useState<ProMinute['ingredients'][number]['unit']>('g');

  function addIngredient() {
    if (!newName.trim()) return;
    setIngredients(prev => [...prev, { id: `ing-${Date.now()}`, name: newName.trim(), quantity: newQty, unit: newUnit }]);
    setNewName('');
    setNewQty(100);
    setNewUnit('g');
  }

  function removeIngredient(id: string) {
    setIngredients(prev => prev.filter(i => i.id !== id));
  }

  return (
    <ModalShell title="✎ Lid State" color="#16a34a" onClose={onClose}>
      {/* Open / Closed toggle */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
        {['Open', 'Closed'].map(s => {
          const active = s === 'Open' ? lidOpen : !lidOpen;
          return (
            <button key={s} onClick={() => setLidOpen(s === 'Open')} style={{ flex: 1, padding: '12px 0', borderRadius: 10, cursor: 'pointer', background: active ? (s === 'Open' ? 'rgba(74,222,128,0.12)' : 'rgba(239,68,68,0.1)') : 'rgba(148,163,184,0.08)', border: `2px solid ${active ? (s === 'Open' ? '#4ade80' : '#f87171') : 'rgba(148,163,184,0.22)'}`, color: active ? (s === 'Open' ? '#16a34a' : '#f87171') : '#64748b', fontSize: 13, fontWeight: 700 }}>
              {s === 'Open' ? '○ Open' : '● Closed'}
            </button>
          );
        })}
      </div>

      {/* Duration */}
      {lidOpen && (
        <div style={{ marginBottom: 14 }}>
          <p style={{ fontSize: 11, color: '#64748b', marginBottom: 8 }}>Open duration (seconds)</p>
          <div style={{ display: 'flex', gap: 6 }}>
            {[10, 15, 20, 30, 40, 45, 60].map(v => (
              <button key={v} onClick={() => setDuration(v)} style={{ flex: 1, padding: '8px 0', borderRadius: 7, cursor: 'pointer', background: duration === v ? 'rgba(74,222,128,0.12)' : 'rgba(148,163,184,0.08)', border: `1px solid ${duration === v ? '#4ade80' : 'rgba(148,163,184,0.22)'}`, color: duration === v ? '#16a34a' : '#64748b', fontSize: 11, fontWeight: 600 }}>{v}s</button>
            ))}
          </div>
        </div>
      )}

      {/* Add Ingredients section */}
      {lidOpen && (
        <>
          {/* Divider */}
          <div style={{ borderTop: '1px solid rgba(148,163,184,0.18)', margin: '4px 0 14px' }} />

          <p style={{ fontSize: 11, color: '#16a34a', fontWeight: 700, marginBottom: 10 }}>Add Ingredients</p>

          {/* Existing ingredients */}
          {ingredients.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginBottom: 12 }}>
              {ingredients.map(ing => (
                <div key={ing.id} style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'rgba(74,222,128,0.06)', border: '1px solid rgba(74,222,128,0.22)', borderRadius: 9, padding: '7px 10px' }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 11, color: '#1e293b', fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{ing.name}</div>
                    <div style={{ fontSize: 9, color: '#64748b', marginTop: 1 }}>{ing.quantity}{ing.unit}</div>
                  </div>
                  <button onClick={() => removeIngredient(ing.id)} style={{ width: 22, height: 22, borderRadius: 6, cursor: 'pointer', background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.22)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <Trash2 size={10} color="#f87171" />
                  </button>
                </div>
              ))}
            </div>
          )}

          {/* Add new ingredient form */}
          <div style={{ background: 'rgba(148,163,184,0.07)', border: '1px solid rgba(148,163,184,0.18)', borderRadius: 11, padding: '11px 11px 10px' }}>
            <div style={{ marginBottom: 8 }}>
              <input
                value={newName}
                onChange={e => setNewName(e.target.value)}
                placeholder="Ingredient name…"
                onKeyDown={e => { if (e.key === 'Enter') addIngredient(); }}
                style={{ width: '100%', padding: '7px 10px', borderRadius: 8, background: 'rgba(255,255,255,0.9)', border: '1px solid rgba(148,163,184,0.28)', color: '#1e293b', fontSize: 12, outline: 'none', boxSizing: 'border-box', fontFamily: "'Space Grotesk', sans-serif" }}
              />
            </div>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
              <input
                type="number"
                value={newQty}
                min={1}
                onChange={e => setNewQty(Math.max(1, parseInt(e.target.value) || 1))}
                style={{ width: 60, padding: '6px 8px', borderRadius: 8, background: 'rgba(255,255,255,0.9)', border: '1px solid rgba(148,163,184,0.28)', color: '#d97706', fontSize: 12, fontWeight: 700, outline: 'none', textAlign: 'center', fontFamily: "'Space Grotesk', sans-serif" }}
              />
              <div style={{ display: 'flex', gap: 4, flex: 1 }}>
                {ING_UNITS.map(u => (
                  <button key={u} onClick={() => setNewUnit(u)} style={{ flex: 1, padding: '5px 0', borderRadius: 6, cursor: 'pointer', fontSize: 9, fontWeight: 700, background: newUnit === u ? 'rgba(74,222,128,0.12)' : 'rgba(148,163,184,0.08)', border: `1px solid ${newUnit === u ? '#4ade80' : 'rgba(148,163,184,0.2)'}`, color: newUnit === u ? '#16a34a' : '#64748b' }}>{u}</button>
                ))}
              </div>
              <button
                onClick={addIngredient}
                style={{ width: 30, height: 30, borderRadius: 8, cursor: 'pointer', background: newName.trim() ? 'rgba(74,222,128,0.15)' : 'rgba(148,163,184,0.08)', border: `1px solid ${newName.trim() ? '#4ade80' : 'rgba(148,163,184,0.2)'}`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}
              >
                <Plus size={13} color={newName.trim() ? '#16a34a' : '#94a3b8'} />
              </button>
            </div>
          </div>
        </>
      )}

      <button
        onClick={() => { onSave({ ...minute, lidOpen, lidOpenDuration: lidOpen ? duration : 0, ingredients: lidOpen ? ingredients : [] }); onClose(); }}
        style={{ marginTop: 14, width: '100%', padding: '11px 0', borderRadius: 10, cursor: 'pointer', background: 'linear-gradient(135deg,#15803d,#4ade80)', border: 'none', color: '#fff', fontSize: 13, fontWeight: 700 }}
      >
        Save Lid State
      </button>
    </ModalShell>
  );
}

// ─── Main Professional Editor Screen ─────────────────────────────────────────

// ─── Meta Edit Modal ──────────────────────────────────────────────────────────
const DISH_TYPES_META: { value: VeganType; label: string; emoji: string; color: string }[] = [
  { value: 'veg',     label: 'Veg',     emoji: '🥦', color: '#16a34a' },
  { value: 'non-veg', label: 'Non-Veg', emoji: '🍗', color: '#dc2626' },
  { value: 'vegan',   label: 'Vegan',   emoji: '🌱', color: '#15803d' },
];
const RECIPE_TYPES_META: { value: ProRecipeType; label: string; color: string }[] = [
  { value: 'gravy',    label: 'Gravy',    color: '#2563eb' },
  { value: 'semi-dry', label: 'Semi-Dry', color: '#7c3aed' },
  { value: 'dry',      label: 'Dry',      color: '#d97706' },
  { value: 'saute',    label: 'Sauté',    color: '#059669' },
  { value: 'boil',     label: 'Boil',     color: '#0891b2' },
  { value: 'fry',      label: 'Fry',      color: '#ea580c' },
  { value: 'steam',    label: 'Steam',    color: '#0e7490' },
];

function MetaEditModal({
  recipeName, dishType, recipeType, consistency, quantity, quantityUnit,
  onApply, onClose,
}: {
  recipeName: string; dishType: VeganType; recipeType: ProRecipeType;
  consistency: ConsistencyType; quantity: number; quantityUnit: string;
  onApply: (dt: VeganType, rt: ProRecipeType, c: ConsistencyType, q: number, qu: string, n: string) => void;
  onClose: () => void;
}) {
  const [name, setName]       = useState(recipeName);
  const [dt, setDt]           = useState<VeganType>(dishType);
  const [rt, setRt]           = useState<ProRecipeType>(recipeType);
  const [con, setCon]         = useState<ConsistencyType>(consistency);
  const [qty, setQty]         = useState(quantity);
  const [qUnit, setQUnit]     = useState(quantityUnit);

  return (
    <ModalShell title="⚙ Recipe Factors" color="#7c3aed" onClose={onClose}>
      <p style={{ fontSize: 10, color: '#64748b', marginBottom: 14 }}>
        Adjust factors below — the timeline will regenerate to match.
      </p>

      {/* Name */}
      <div style={{ marginBottom: 14 }}>
        <p style={{ fontSize: 10, color: '#64748b', marginBottom: 6 }}>DISH NAME</p>
        <input
          value={name}
          onChange={e => setName(e.target.value)}
          style={{ width: '100%', padding: '8px 11px', borderRadius: 8, background: 'rgba(255,255,255,0.9)', border: '1px solid rgba(148,163,184,0.3)', color: '#1e293b', fontSize: 13, fontWeight: 700, outline: 'none', boxSizing: 'border-box' }}
        />
      </div>

      {/* Diet type */}
      <div style={{ marginBottom: 14 }}>
        <p style={{ fontSize: 10, color: '#64748b', marginBottom: 6 }}>DIET TYPE</p>
        <div style={{ display: 'flex', gap: 6 }}>
          {DISH_TYPES_META.map(d => (
            <button key={d.value} onClick={() => setDt(d.value)} style={{ flex: 1, padding: '8px 4px', borderRadius: 8, cursor: 'pointer', background: dt === d.value ? `${d.color}12` : 'rgba(148,163,184,0.08)', border: `${dt === d.value ? 2 : 1}px solid ${dt === d.value ? d.color : 'rgba(148,163,184,0.22)'}`, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
              <span style={{ fontSize: 16 }}>{d.emoji}</span>
              <span style={{ fontSize: 10, color: dt === d.value ? d.color : '#64748b', fontWeight: 600 }}>{d.label}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Recipe type */}
      <div style={{ marginBottom: 14 }}>
        <p style={{ fontSize: 10, color: '#64748b', marginBottom: 6 }}>RECIPE TYPE</p>
        <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap' }}>
          {RECIPE_TYPES_META.map(r => (
            <button key={r.value} onClick={() => setRt(r.value)} style={{ padding: '5px 10px', borderRadius: 7, cursor: 'pointer', fontSize: 10, fontWeight: 600, background: rt === r.value ? `${r.color}12` : 'rgba(148,163,184,0.08)', border: `${rt === r.value ? 2 : 1}px solid ${rt === r.value ? r.color : 'rgba(148,163,184,0.2)'}`, color: rt === r.value ? r.color : '#64748b' }}>
              {r.label}
            </button>
          ))}
        </div>
      </div>

      {/* Quantity */}
      <div style={{ marginBottom: 14 }}>
        <p style={{ fontSize: 10, color: '#64748b', marginBottom: 6 }}>QUANTITY</p>
        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <button onClick={() => setQty(q => Math.max(100, q - 100))} style={{ width: 32, height: 32, borderRadius: 7, background: 'rgba(255,255,255,0.85)', border: '1px solid rgba(148,163,184,0.25)', cursor: 'pointer', color: '#64748b', fontSize: 16, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>−</button>
          <input type="number" value={qty} onChange={e => setQty(parseInt(e.target.value) || 0)} style={{ flex: 1, padding: '7px 10px', borderRadius: 7, background: 'rgba(255,255,255,0.9)', border: '1px solid rgba(148,163,184,0.28)', color: '#d97706', fontSize: 14, fontWeight: 700, outline: 'none', textAlign: 'center', boxSizing: 'border-box' }} />
          <button onClick={() => setQty(q => q + 100)} style={{ width: 32, height: 32, borderRadius: 7, background: 'rgba(255,255,255,0.85)', border: '1px solid rgba(148,163,184,0.25)', cursor: 'pointer', color: '#64748b', fontSize: 16, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>+</button>
          {['g', 'ml'].map(u => (
            <button key={u} onClick={() => setQUnit(u)} style={{ padding: '7px 10px', borderRadius: 7, cursor: 'pointer', fontSize: 11, fontWeight: 700, background: qUnit === u ? 'rgba(217,119,6,0.1)' : 'rgba(148,163,184,0.08)', border: `1px solid ${qUnit === u ? 'rgba(217,119,6,0.4)' : 'rgba(148,163,184,0.2)'}`, color: qUnit === u ? '#d97706' : '#64748b' }}>{u}</button>
          ))}
        </div>
      </div>

      {/* Consistency */}
      <div style={{ marginBottom: 16 }}>
        <p style={{ fontSize: 10, color: '#64748b', marginBottom: 6 }}>CONSISTENCY</p>
        <div style={{ display: 'flex', gap: 6 }}>
          {(['thin', 'medium', 'thick'] as ConsistencyType[]).map(c => (
            <button key={c} onClick={() => setCon(c)} style={{ flex: 1, padding: '8px 4px', borderRadius: 8, cursor: 'pointer', background: con === c ? 'rgba(34,211,238,0.08)' : 'rgba(148,163,184,0.08)', border: `${con === c ? 2 : 1}px solid ${con === c ? 'rgba(34,211,238,0.45)' : 'rgba(148,163,184,0.2)'}`, color: con === c ? '#0891b2' : '#64748b', fontSize: 11, fontWeight: 600 }}>{c}</button>
          ))}
        </div>
      </div>

      {/* Estimated regeneration note */}
      <div style={{ background: 'rgba(124,58,237,0.07)', border: '1px solid rgba(124,58,237,0.18)', borderRadius: 8, padding: '8px 12px', marginBottom: 14, display: 'flex', alignItems: 'center', gap: 8 }}>
        <RefreshCw size={12} color="#7c3aed" />
        <span style={{ fontSize: 10, color: '#64748b' }}>
          Timeline will auto-regenerate when you apply. Your previous manual edits will be replaced.
        </span>
      </div>

      <button
        onClick={() => onApply(dt, rt, con, qty, qUnit, name)}
        style={{ width: '100%', padding: '11px 0', borderRadius: 10, cursor: 'pointer', background: 'linear-gradient(135deg, rgba(109,40,217,0.9), rgba(192,132,252,0.8))', border: '1px solid rgba(192,132,252,0.4)', color: '#fff', fontSize: 13, fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7 }}
      >
        <RefreshCw size={13} /> Apply &amp; Regenerate Timeline
      </button>
    </ModalShell>
  );
}

export function ProfessionalEditorScreen() {
  const navigate = useNavigate();
  const { proRecipe, setProRecipe, saveProRecipeToLibrary } = useApp();
  const scrollRef = useRef<HTMLDivElement>(null);

  const recipe = proRecipe ?? { ...DEFAULT_PRO_RECIPE };
  const [minutes, setMinutes] = useState<ProMinute[]>(recipe.minutes);
  const [recipeName, setRecipeName] = useState(recipe.name);
  const [dishType, setDishType]     = useState<VeganType>(recipe.dishType);
  const [recipeType, setRecipeType] = useState<ProRecipeType>(recipe.recipeType);
  const [consistency, setConsistency] = useState<ConsistencyType>(recipe.consistency);
  const [quantity, setQuantity]     = useState(recipe.quantity);
  const [quantityUnit, setQuantityUnit] = useState(recipe.quantityUnit);
  const [showMetaEdit, setShowMetaEdit] = useState(false);
  const [regenerating, setRegenerating] = useState(false);
  const [showSaveDialog, setShowSaveDialog] = useState(false);
  const [saveDialogName, setSaveDialogName] = useState('');
  const [savedToLibrary, setSavedToLibrary] = useState(false);

  const [selectedCell, setSelectedCell] = useState<CellRef | null>(null);
  const [editModal, setEditModal] = useState<EditModal>(null);
  const [inspectorCollapsed, setInspectorCollapsed] = useState(false);

  // History for undo/redo
  const [history, setHistory] = useState<ProMinute[][]>([recipe.minutes]);
  const [histIdx, setHistIdx] = useState(0);

  const pushHistory = useCallback((next: ProMinute[]) => {
    setHistory(h => [...h.slice(0, histIdx + 1), next]);
    setHistIdx(i => i + 1);
  }, [histIdx]);

  const undo = () => {
    if (histIdx <= 0) return;
    const newIdx = histIdx - 1;
    setMinutes(history[newIdx]);
    setHistIdx(newIdx);
  };
  const redo = () => {
    if (histIdx >= history.length - 1) return;
    const newIdx = histIdx + 1;
    setMinutes(history[newIdx]);
    setHistIdx(newIdx);
  };

  function updateMinute(idx: number, upd: ProMinute) {
    const next = minutes.map((m, i) => i === idx ? upd : m);
    setMinutes(next);
    pushHistory(next);
  }

  function addMinute() {
    const next = [...minutes, createDefaultMinute(minutes.length)];
    setMinutes(next); pushHistory(next);
  }

  function removeMinute() {
    if (minutes.length <= 1) return;
    const selIdx = selectedCell?.minuteIdx;
    const rmIdx = selIdx !== undefined ? selIdx : minutes.length - 1;
    const next = minutes.filter((_, i) => i !== rmIdx).map((m, i) => ({ ...m, minuteIndex: i }));
    setMinutes(next); pushHistory(next);
    setSelectedCell(null);
  }

  function copyMinute() {
    if (!selectedCell) return;
    const src = minutes[selectedCell.minuteIdx];
    const copy = { ...src, id: `min-${minutes.length}-${Date.now()}`, minuteIndex: minutes.length };
    const next = [...minutes, copy];
    setMinutes(next); pushHistory(next);
  }

  function saveRecipe() {
    setProRecipe({ ...recipe, minutes, name: recipeName, dishType, recipeType, consistency, quantity, quantityUnit });
  }

  function handleSaveToLibrary() {
    setSaveDialogName(recipeName);
    setShowSaveDialog(true);
  }

  function confirmSaveToLibrary() {
    if (!saveDialogName.trim()) return;
    const updated = { ...recipe, minutes, name: saveDialogName.trim(), dishType, recipeType, consistency, quantity, quantityUnit, id: `pro-saved-${Date.now()}` };
    setProRecipe({ ...recipe, minutes, name: recipeName, dishType, recipeType, consistency, quantity, quantityUnit });
    saveProRecipeToLibrary(updated);
    setSavedToLibrary(true);
    setShowSaveDialog(false);
  }

  function regenerateTimeline(overrides?: Partial<TimelineFactors>) {
    const factors: TimelineFactors = {
      dishType: overrides?.dishType ?? dishType,
      recipeType: overrides?.recipeType ?? recipeType,
      quantity: overrides?.quantity ?? quantity,
      quantityUnit: overrides?.quantityUnit ?? quantityUnit,
      consistency: overrides?.consistency ?? consistency,
      healthRichRatio: recipe.healthRichRatio,
      ingredients: recipe.minutes.flatMap(m => m.ingredients)
        .filter((ing, idx, arr) => arr.findIndex(x => x.id === ing.id) === idx),
    };
    setRegenerating(true);
    setTimeout(() => {
      const next = generateTimeline(factors);
      setMinutes(next);
      pushHistory(next);
      setRegenerating(false);
    }, 320);
  }

  function applyMetaChanges(
    newDt: VeganType, newRt: ProRecipeType,
    newCon: ConsistencyType, newQty: number, newQUnit: string, newName: string,
  ) {
    setDishType(newDt);
    setRecipeType(newRt);
    setConsistency(newCon);
    setQuantity(newQty);
    setQuantityUnit(newQUnit);
    setRecipeName(newName);
    setShowMetaEdit(false);
    regenerateTimeline({ dishType: newDt, recipeType: newRt, consistency: newCon, quantity: newQty, quantityUnit: newQUnit });
  }

  const timelineContentW = LABEL_W + minutes.length * CELL_W + 48;

  // Compute total calories across all ingredients in all minutes
  const KCAL_DB: Record<string, number> = {
    'ghee':900,'oil':884,'olive oil':884,'coconut oil':892,'butter':717,
    'chicken':165,'beef':250,'mutton':294,'lamb':294,'pork':242,
    'fish':136,'salmon':208,'tuna':130,'shrimp':99,'prawn':99,
    'egg':155,'paneer':265,'tofu':76,
    'onion':40,'garlic':149,'ginger':80,'tomato':18,'potato':77,
    'carrot':41,'spinach':23,'broccoli':34,'cauliflower':25,'peas':81,
    'capsicum':20,'bell pepper':20,'mushroom':22,'zucchini':17,
    'eggplant':25,'cabbage':25,'corn':86,'pumpkin':26,'celery':16,
    'rice':365,'flour':364,'wheat':340,'oats':389,'bread':265,
    'pasta':371,'noodle':138,'dal':341,'lentil':116,'chickpea':364,
    'beans':347,'kidney bean':333,
    'milk':61,'cream':340,'yogurt':59,'curd':98,'cheese':402,
    'salt':0,'sugar':387,'honey':304,'soy sauce':53,
    'tomato paste':82,'vinegar':21,'ketchup':97,
    'cumin':375,'turmeric':354,'coriander':298,'chili':282,
    'paprika':282,'pepper':251,'garam masala':379,'cardamom':311,
    'cinnamon':247,'clove':274,'fenugreek':323,
    'almond':579,'cashew':553,'peanut':567,'sesame':573,
    'walnut':654,'pistachio':562,'water':0,
  };
  const PIECE_G_DB: Record<string, number> = {
    egg:60,onion:100,tomato:120,potato:150,garlic:5,chicken:200,lemon:60,lime:60,mushroom:18,carrot:80,
  };
  function ingToG(qty: number, unit: string, nameKey: string): number {
    if (unit==='g') return qty; if (unit==='ml') return qty;
    if (unit==='tsp') return qty*5; if (unit==='tbsp') return qty*15;
    if (unit==='piece') { const pw=Object.entries(PIECE_G_DB).find(([k])=>nameKey.includes(k)); return qty*(pw?pw[1]:80); }
    return qty;
  }
  const allIngredients = minutes.flatMap(m => m.ingredients)
    .filter((ing, idx, arr) => arr.findIndex(x => x.id === ing.id) === idx);
  let totalCalories = 0;
  allIngredients.forEach(ing => {
    const nk = ing.name.toLowerCase();
    const e = Object.entries(KCAL_DB).find(([k]) => nk.includes(k));
    if (e) totalCalories += Math.round((e[1] / 100) * ingToG(ing.quantity, ing.unit, nk));
  });

  const handleCellClick = (minuteIdx: number, row: RowKey) => {
    setSelectedCell(prev =>
      prev?.minuteIdx === minuteIdx && prev?.row === row ? null : { minuteIdx, row }
    );
  };

  const handleEdit = (modal: EditModal) => {
    setEditModal(modal);
  };

  const dishTypeColor = dishType === 'vegan' ? '#15803d' : dishType === 'veg' ? '#16a34a' : '#dc2626';
  const dishTypeEmoji = dishType === 'vegan' ? '🌱' : dishType === 'veg' ? '🥦' : '🍗';

  return (
    <div style={{
      display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden',
      paddingLeft: DI_SAFE,
      position: 'relative',
      background: 'linear-gradient(160deg,#f0f4ff 0%,#f7f9ff 60%,#fafbff 100%)',
    }}>

      {/* ── TOP BAR ─────────────────────────────────────────────────────────── */}
      <div style={{
        height: 46, flexShrink: 0, display: 'flex', alignItems: 'center', gap: 6,
        padding: '0 10px 0 8px',
        background: 'rgba(248,250,255,0.97)', backdropFilter: 'blur(20px)',
        borderBottom: '1px solid rgba(148,163,184,0.2)',
      }}>
        {/* Back */}
        <button onClick={() => navigate('/preset-library')} style={{ ...iconBtn, gap: 3, width: 'auto', padding: '0 10px', fontSize: 12, color: '#64748b' }}>
          <ChevronLeft size={14} /> Back
        </button>

        {/* Recipe title & meta */}
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
          <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
            <span style={{ fontSize: 13, color: '#0f172a', fontWeight: 700, letterSpacing: -0.3, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{recipeName}</span>
            <div style={{ display: 'flex', gap: 5, alignItems: 'center' }}>
              <span style={{ fontSize: 9, color: dishTypeColor, background: `${dishTypeColor}12`, border: `1px solid ${dishTypeColor}30`, borderRadius: 4, padding: '1px 5px', fontWeight: 600 }}>{dishTypeEmoji} {dishType}</span>
              <span style={{ fontSize: 9, color: '#2563eb', background: 'rgba(37,99,235,0.1)', border: '1px solid rgba(37,99,235,0.22)', borderRadius: 4, padding: '1px 5px' }}>{recipeType}</span>
              <span style={{ fontSize: 9, color: '#0891b2', background: 'rgba(8,145,178,0.09)', border: '1px solid rgba(8,145,178,0.28)', borderRadius: 4, padding: '1px 6px', fontWeight: 700 }}>
                ⏱ {minutes.length >= 60 ? `${Math.floor(minutes.length / 60)}h${minutes.length % 60 > 0 ? ` ${minutes.length % 60}m` : ''}` : `${minutes.length} min`}
              </span>
              <span style={{ fontSize: 9, color: '#7c3aed', background: 'rgba(124,58,237,0.09)', border: '1px solid rgba(124,58,237,0.28)', borderRadius: 4, padding: '1px 6px', fontWeight: 700 }}>
                🍽 {quantity} {quantityUnit}
              </span>
              {totalCalories > 0 && (
                <span style={{ fontSize: 9, color: '#d97706', background: 'rgba(217,119,6,0.09)', border: '1px solid rgba(217,119,6,0.28)', borderRadius: 4, padding: '1px 6px', fontWeight: 700 }}>
                  🔥 {totalCalories} kcal
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Consistency chip */}
        <span style={{ fontSize: 10, color: '#7c3aed', background: 'rgba(124,58,237,0.08)', border: '1px solid rgba(124,58,237,0.22)', borderRadius: 6, padding: '3px 8px', flexShrink: 0 }}>
          {consistency}
        </span>

        {/* Health ratio */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 5, flexShrink: 0 }}>
          <Leaf size={12} color={recipe.healthRichRatio < 50 ? '#16a34a' : '#94a3b8'} />
          <div style={{ width: 42, height: 5, borderRadius: 3, background: 'rgba(148,163,184,0.22)', overflow: 'hidden' }}>
            <div style={{ width: `${recipe.healthRichRatio}%`, height: '100%', background: recipe.healthRichRatio < 40 ? '#4ade80' : recipe.healthRichRatio > 70 ? '#f97316' : '#fbbf24', borderRadius: 3 }} />
          </div>
          <FlameKindling size={12} color={recipe.healthRichRatio > 60 ? '#f97316' : '#94a3b8'} />
        </div>

        <div style={{ width: 1, height: 20, background: 'rgba(148,163,184,0.25)', flexShrink: 0 }} />

        {/* Edit meta factors */}
        <button
          onClick={() => setShowMetaEdit(true)}
          style={{ ...iconBtn, gap: 4, width: 'auto', padding: '0 10px', fontSize: 11, color: '#7c3aed', background: 'rgba(124,58,237,0.08)', border: '1px solid rgba(124,58,237,0.22)' }}
        >
          <Settings2 size={12} /> Edit
        </button>

        {/* Save to Library */}
        <button
          onClick={handleSaveToLibrary}
          style={{ ...iconBtn, gap: 4, width: 'auto', padding: '0 10px', fontSize: 11, color: savedToLibrary ? '#4ade80' : '#16a34a', background: savedToLibrary ? 'rgba(74,222,128,0.15)' : 'rgba(74,222,128,0.08)', border: `1px solid ${savedToLibrary ? 'rgba(74,222,128,0.5)' : 'rgba(74,222,128,0.28)'}` }}
        >
          <Save size={12} /> {savedToLibrary ? 'Saved ✓' : 'Save'}
        </button>

        {/* Go Live */}
        <button
          onClick={() => { saveRecipe(); navigate('/pro-editor/live'); }}
          style={{ height: 30, padding: '0 12px', borderRadius: 8, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 5, background: 'linear-gradient(135deg,#1e40af,#3b82f6)', border: 'none', color: '#fff', fontSize: 12, fontWeight: 700, flexShrink: 0 }}
        >
          <Play size={12} /> Live Cook
        </button>
      </div>

      {/* ── Regenerating overlay ─────────────────────────────────────────────── */}
      {regenerating && (
        <div style={{
          position: 'absolute', inset: 0, zIndex: 40,
          background: 'rgba(240,244,255,0.88)', backdropFilter: 'blur(6px)',
          display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 12,
        }}>
          <div style={{ width: 44, height: 44, borderRadius: '50%', border: '2px solid rgba(37,99,235,0.25)', borderTopColor: '#3b82f6', animation: 'spin 0.8s linear infinite' }} />
          <p style={{ fontSize: 13, color: '#2563eb', fontWeight: 600 }}>Regenerating timeline…</p>
          <p style={{ fontSize: 10, color: '#94a3b8' }}>Applying updated recipe factors</p>
        </div>
      )}

      {/* ── Meta Edit Modal ──────────────────────────────────────────────────── */}
      {showMetaEdit && (
        <MetaEditModal
          recipeName={recipeName}
          dishType={dishType}
          recipeType={recipeType}
          consistency={consistency}
          quantity={quantity}
          quantityUnit={quantityUnit}
          onApply={applyMetaChanges}
          onClose={() => setShowMetaEdit(false)}
        />
      )}

      {/* ── MAIN AREA ───────────────────────────────────────────────────────── */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden', minHeight: 0 }}>

        {/* ── LEFT LABELS ─────────────────────────────────────────────────── */}
        <div style={{
          width: LABEL_W, flexShrink: 0, display: 'flex', flexDirection: 'column',
          background: 'rgba(248,250,255,0.97)', borderRight: '1px solid rgba(148,163,184,0.18)',
        }}>
          <div style={{ height: HEADER_H, borderBottom: '1px solid rgba(148,163,184,0.15)' }} />
          {ROW_META.map(r => (
            <div key={r.key} style={{
              height: ROW_H, display: 'flex', alignItems: 'center', justifyContent: 'center',
              borderBottom: '1px solid rgba(148,163,184,0.12)',
              flexDirection: 'column', gap: 2,
            }}>
              <span style={{ width: 22, height: 22, borderRadius: 6, background: `${r.color}15`, border: `1px solid ${r.color}40`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, color: r.color, fontWeight: 800 }}>{r.letter}</span>
            </div>
          ))}
        </div>

        {/* ── TIMELINE SCROLL AREA ─────────────────────────────────────────── */}
        <div
          ref={scrollRef}
          style={{
            flex: 1, overflowX: 'auto', overflowY: 'hidden',
            scrollbarWidth: 'thin',
            scrollbarColor: 'rgba(148,163,184,0.3) transparent',
            background: '#f5f7ff',
          }}
        >
          <div style={{
            display: 'flex', flexDirection: 'column',
            height: '100%', minWidth: timelineContentW, position: 'relative',
          }}>
            {/* Minute header */}
            <div style={{
              display: 'flex', height: HEADER_H, flexShrink: 0,
              background: 'rgba(248,250,255,0.98)', borderBottom: '1px solid rgba(148,163,184,0.18)',
              position: 'sticky', top: 0, zIndex: 5,
            }}>
              {minutes.map((m, idx) => (
                <div key={m.id} style={{
                  width: CELL_W, flexShrink: 0, borderRight: '1px solid rgba(148,163,184,0.12)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  background: selectedCell?.minuteIdx === idx ? 'rgba(37,99,235,0.07)' : 'transparent',
                }}>
                  <span style={{ fontSize: 8, color: selectedCell?.minuteIdx === idx ? '#2563eb' : '#94a3b8', fontWeight: selectedCell?.minuteIdx === idx ? 700 : 400, whiteSpace: 'nowrap' }}>
                    Min {idx + 1}
                  </span>
                </div>
              ))}
              <div style={{ width: 48, flexShrink: 0 }} />
            </div>

            {/* Row cells */}
            {ROW_META.map(rowMeta => (
              <div key={rowMeta.key} style={{
                display: 'flex', height: ROW_H,
                borderBottom: '1px solid rgba(148,163,184,0.1)',
              }}>
                {minutes.map((min, midx) => {
                  const sel = selectedCell?.minuteIdx === midx && selectedCell?.row === rowMeta.key;
                  const onClick = () => handleCellClick(midx, rowMeta.key);
                  switch (rowMeta.key) {
                    case 'lid':       return <LidCell key={min.id} minute={min} selected={sel} onClick={onClick} />;
                    case 'induction': return <InductionCell key={min.id} minute={min} selected={sel} onClick={onClick} />;
                    case 'microwave': return <MicrowaveCell key={min.id} minute={min} selected={sel} onClick={onClick} />;
                    case 'stirrer':   return <StirrerCell key={min.id} minute={min} selected={sel} onClick={onClick} />;
                    case 'water':     return <WaterCell key={min.id} minute={min} selected={sel} onClick={onClick} />;
                    default:          return null;
                  }
                })}
                {/* Tail padding / add button zone */}
                <div style={{ width: 48, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  {rowMeta.key === 'lid' && (
                    <button onClick={addMinute} style={{ width: 24, height: 24, borderRadius: 7, background: 'rgba(37,99,235,0.08)', border: '1px solid rgba(37,99,235,0.22)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <Plus size={12} color="#2563eb" />
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* ── INSPECTOR ───────────────────────────────────────────────────── */}
        <InspectorPanel
          cell={selectedCell}
          minutes={minutes}
          collapsed={inspectorCollapsed}
          onToggle={() => setInspectorCollapsed(p => !p)}
          onEdit={handleEdit}
        />
      </div>

      {/* ── BOTTOM CONTROLS BAR ─────────────────────────────────────────────── */}
      <div style={{
        height: 44, flexShrink: 0, display: 'flex', alignItems: 'center', gap: 4,
        padding: '0 10px', background: 'rgba(248,250,255,0.97)', backdropFilter: 'blur(20px)',
        borderTop: '1px solid rgba(148,163,184,0.2)',
      }}>
        {/* Minute ops */}
        <button onClick={addMinute} style={{ ...iconBtn, gap: 4, width: 'auto', padding: '0 10px', fontSize: 11, color: '#2563eb' }}>
          <Plus size={13} /> Min
        </button>
        <button onClick={removeMinute} style={{ ...iconBtn, gap: 4, width: 'auto', padding: '0 10px', fontSize: 11, color: '#f87171' }}>
          <Trash2 size={13} />
        </button>
        {selectedCell && (
          <button onClick={copyMinute} style={{ ...iconBtn, gap: 4, width: 'auto', padding: '0 10px', fontSize: 11, color: '#64748b' }}>
            <Copy size={12} /> Copy
          </button>
        )}

        <div style={{ flex: 1 }} />

        {/* Selected cell label */}
        {selectedCell && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
            <span style={{ fontSize: 10, color: '#94a3b8' }}>Selected:</span>
            <span style={{ fontSize: 10, color: ROW_META.find(r => r.key === selectedCell.row)?.color ?? '#64748b', fontWeight: 600 }}>
              {ROW_META.find(r => r.key === selectedCell.row)?.label} · Minute {selectedCell.minuteIdx + 1}
            </span>
            <button
              onClick={() => setEditModal(selectedCell.row as EditModal)}
              style={{ fontSize: 10, color: '#fff', background: 'linear-gradient(135deg,#1e40af,#3b82f6)', border: 'none', borderRadius: 6, padding: '3px 10px', cursor: 'pointer', fontWeight: 700 }}
            >
              Edit ✎
            </button>
          </div>
        )}

        <div style={{ flex: 1 }} />

        {/* Undo / Redo */}
        <button onClick={undo} disabled={histIdx <= 0} style={{ ...iconBtn, opacity: histIdx <= 0 ? 0.3 : 1 }}>
          <Undo2 size={13} color="#64748b" />
        </button>
        <button onClick={redo} disabled={histIdx >= history.length - 1} style={{ ...iconBtn, opacity: histIdx >= history.length - 1 ? 0.3 : 1 }}>
          <Redo2 size={13} color="#64748b" />
        </button>

        <div style={{ width: 1, height: 20, background: 'rgba(148,163,184,0.25)', margin: '0 4px' }} />

        {/* Scroll navigation */}
        <button onClick={() => scrollRef.current?.scrollBy({ left: -CELL_W * 2, behavior: 'smooth' })} style={iconBtn}>
          <ChevronLeft size={14} color="#64748b" />
        </button>
        <button onClick={() => scrollRef.current?.scrollBy({ left: CELL_W * 2, behavior: 'smooth' })} style={iconBtn}>
          <ChevronRight size={14} color="#64748b" />
        </button>
      </div>

      {/* ── MODALS ──────────────────────────────────────────────────────────── */}
      {editModal === 'lid' && selectedCell && (
        <LidEditModal
          minute={minutes[selectedCell.minuteIdx]}
          onSave={m => updateMinute(selectedCell.minuteIdx, m)}
          onClose={() => setEditModal(null)}
        />
      )}
      {editModal === 'induction' && selectedCell && (
        <InductionEditModal
          minute={minutes[selectedCell.minuteIdx]}
          onSave={m => updateMinute(selectedCell.minuteIdx, m)}
          onClose={() => setEditModal(null)}
        />
      )}
      {editModal === 'microwave' && selectedCell && (
        <MicrowaveEditModal
          minute={minutes[selectedCell.minuteIdx]}
          onSave={m => updateMinute(selectedCell.minuteIdx, m)}
          onClose={() => setEditModal(null)}
        />
      )}
      {editModal === 'stirrer' && selectedCell && (
        <StirrerEditModal
          minute={minutes[selectedCell.minuteIdx]}
          onSave={m => updateMinute(selectedCell.minuteIdx, m)}
          onClose={() => setEditModal(null)}
        />
      )}
      {editModal === 'water' && selectedCell && (
        <WaterEditModal
          minute={minutes[selectedCell.minuteIdx]}
          onSave={m => updateMinute(selectedCell.minuteIdx, m)}
          onClose={() => setEditModal(null)}
        />
      )}

      {/* ── Save to Library dialog ───────────────────────────────────────────── */}
      {showSaveDialog && (
        <div
          style={{ position: 'absolute', inset: 0, zIndex: 60, background: 'rgba(0,0,0,0.4)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
          onClick={() => setShowSaveDialog(false)}
        >
          <div
            onClick={e => e.stopPropagation()}
            style={{ background: 'linear-gradient(160deg,#f8faff,#f0f4ff)', border: '1px solid rgba(148,163,184,0.22)', borderRadius: 16, padding: '24px 22px', width: 300, boxShadow: '0 12px 40px rgba(0,0,0,0.18)' }}
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
              onKeyDown={e => { if (e.key === 'Enter') confirmSaveToLibrary(); }}
              placeholder="Enter recipe name…"
              style={{ width: '100%', padding: '11px 13px', borderRadius: 10, background: 'rgba(255,255,255,0.9)', border: '1px solid rgba(148,163,184,0.3)', color: '#0f172a', fontSize: 14, fontWeight: 600, outline: 'none', boxSizing: 'border-box', marginBottom: 14 }}
            />
            <div style={{ display: 'flex', gap: 8 }}>
              <button onClick={() => setShowSaveDialog(false)} style={{ flex: 1, padding: '10px 0', borderRadius: 9, cursor: 'pointer', background: 'rgba(148,163,184,0.1)', border: '1px solid rgba(148,163,184,0.25)', color: '#64748b', fontSize: 13, fontWeight: 600 }}>
                Cancel
              </button>
              <button
                onClick={confirmSaveToLibrary}
                disabled={!saveDialogName.trim()}
                style={{ flex: 1, padding: '10px 0', borderRadius: 9, cursor: saveDialogName.trim() ? 'pointer' : 'not-allowed', background: saveDialogName.trim() ? 'linear-gradient(135deg,#1e40af,#3b82f6)' : 'rgba(148,163,184,0.2)', border: 'none', color: saveDialogName.trim() ? '#fff' : '#94a3b8', fontSize: 13, fontWeight: 700 }}
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
