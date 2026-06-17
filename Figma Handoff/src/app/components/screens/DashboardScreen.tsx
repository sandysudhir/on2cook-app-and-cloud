import { useState } from 'react';
import {
  Zap, Waves, RotateCcw, Droplets, ThermometerSun, Wifi,
  Clock, ChefHat, TrendingUp, Activity, Calendar, Flame,
} from 'lucide-react';

const WEEKLY_COOKS = [4, 6, 3, 7, 5, 8, 2];
const DAYS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
const MAX_COOK = 8;

const recentRecipes = [
  { name: 'Kadai Chicken', time: '18 min', type: 'non-veg', when: 'Today, 12:30 PM' },
  { name: 'Dal Tadka', time: '22 min', type: 'veg', when: 'Yesterday, 7:10 PM' },
  { name: 'Butter Paneer', time: '15 min', type: 'veg', when: 'Mon, 1:00 PM' },
  { name: 'Egg Bhurji', time: '9 min', type: 'non-veg', when: 'Mon, 8:20 AM' },
];

const moduleStats = [
  { icon: Zap, label: 'Microwave', color: '#ec4899', usage: '68%', sessions: 14, unit: 'sessions' },
  { icon: Waves, label: 'Induction', color: '#f97316', usage: '92%', sessions: 19, unit: 'sessions' },
  { icon: RotateCcw, label: 'Stirrer', color: '#14b8a6', usage: '55%', sessions: 11, unit: 'sessions' },
  { icon: Droplets, label: 'Water', color: '#22d3ee', usage: '34%', sessions: 7, unit: 'sessions' },
];

export function DashboardScreen() {
  const [activeTab, setActiveTab] = useState<'week' | 'month'>('week');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100%', overflowY: 'auto', padding: '56px 18px 24px', gap: 18, background: 'linear-gradient(160deg, #f0f4ff 0%, #f7f9ff 60%, #fafbff 100%)' }}>
      {/* Header */}
      <div>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 3 }}>
          <div>
            <h1 style={{ fontSize: 22, color: '#1e293b', fontWeight: 800, margin: 0 }}>Dashboard</h1>
            <p style={{ fontSize: 12, color: '#94a3b8', margin: 0, marginTop: 2 }}>On2Cook Pro — Activity Overview</p>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'rgba(74,222,128,0.1)', border: '1px solid rgba(74,222,128,0.3)', borderRadius: 20, padding: '4px 10px' }}>
            <div style={{ width: 6, height: 6, borderRadius: '50%', background: '#4ade80' }} />
            <span style={{ fontSize: 10, color: '#16a34a', fontWeight: 600 }}>Online</span>
          </div>
        </div>
      </div>

      {/* Summary cards */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        {[
          { icon: ChefHat, label: 'Total Cooks', value: '94', sub: 'all time', color: '#2563eb' },
          { icon: Clock, label: 'Cook Time', value: '28h', sub: 'this month', color: '#7c3aed' },
          { icon: Flame, label: 'Energy Used', value: '3.2 kWh', sub: 'this week', color: '#f97316' },
          { icon: TrendingUp, label: 'Avg Duration', value: '17 min', sub: 'per cook', color: '#0891b2' },
        ].map(({ icon: Icon, label, value, sub, color }) => (
          <div key={label} style={{ background: 'rgba(255,255,255,0.72)', backdropFilter: 'blur(14px)', border: '1px solid rgba(148,163,184,0.2)', borderRadius: 16, padding: '14px 14px' }}>
            <div style={{ width: 30, height: 30, borderRadius: 9, background: `${color}14`, border: `1px solid ${color}30`, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 10 }}>
              <Icon size={15} color={color} />
            </div>
            <div style={{ fontSize: 20, color: '#1e293b', fontWeight: 800, lineHeight: 1, marginBottom: 2 }}>{value}</div>
            <div style={{ fontSize: 10, color: '#64748b' }}>{label}</div>
            <div style={{ fontSize: 9, color: '#94a3b8', marginTop: 1 }}>{sub}</div>
          </div>
        ))}
      </div>

      {/* Weekly activity */}
      <div style={{ background: 'rgba(255,255,255,0.72)', backdropFilter: 'blur(14px)', border: '1px solid rgba(148,163,184,0.2)', borderRadius: 18, padding: '16px 16px 14px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
            <Activity size={14} color="#2563eb" />
            <span style={{ fontSize: 13, color: '#1e293b', fontWeight: 700 }}>Cooking Activity</span>
          </div>
          <div style={{ display: 'flex', gap: 4 }}>
            {(['week', 'month'] as const).map(t => (
              <button key={t} onClick={() => setActiveTab(t)} style={{ padding: '3px 10px', borderRadius: 8, cursor: 'pointer', fontSize: 10, fontWeight: 600, border: 'none', background: activeTab === t ? '#2563eb' : 'rgba(148,163,184,0.12)', color: activeTab === t ? '#fff' : '#64748b' }}>
                {t === 'week' ? 'Week' : 'Month'}
              </button>
            ))}
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 6, height: 64 }}>
          {WEEKLY_COOKS.map((count, i) => {
            const h = Math.max(6, Math.round((count / MAX_COOK) * 56));
            const isToday = i === 6;
            return (
              <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
                <div style={{ width: '100%', height: h, borderRadius: '4px 4px 2px 2px', background: isToday ? '#2563eb' : 'rgba(37,99,235,0.22)', transition: 'height 0.3s' }} />
                <span style={{ fontSize: 8, color: isToday ? '#2563eb' : '#94a3b8', fontWeight: isToday ? 700 : 400 }}>{DAYS[i]}</span>
              </div>
            );
          })}
        </div>
        <div style={{ marginTop: 10, fontSize: 10, color: '#94a3b8', textAlign: 'center' }}>
          35 cooks this week · <span style={{ color: '#2563eb', fontWeight: 600 }}>+12% vs last week</span>
        </div>
      </div>

      {/* Module usage */}
      <div style={{ background: 'rgba(255,255,255,0.72)', backdropFilter: 'blur(14px)', border: '1px solid rgba(148,163,184,0.2)', borderRadius: 18, padding: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 14 }}>
          <Zap size={14} color="#f97316" />
          <span style={{ fontSize: 13, color: '#1e293b', fontWeight: 700 }}>Module Usage</span>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {moduleStats.map(({ icon: Icon, label, color, usage, sessions }) => (
            <div key={label} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <div style={{ width: 28, height: 28, borderRadius: 8, background: `${color}14`, border: `1px solid ${color}28`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <Icon size={13} color={color} />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                  <span style={{ fontSize: 11, color: '#334155', fontWeight: 600 }}>{label}</span>
                  <span style={{ fontSize: 10, color, fontWeight: 700 }}>{usage}</span>
                </div>
                <div style={{ height: 5, borderRadius: 3, background: 'rgba(148,163,184,0.18)', overflow: 'hidden' }}>
                  <div style={{ height: '100%', width: usage, borderRadius: 3, background: color, transition: 'width 0.4s' }} />
                </div>
                <div style={{ fontSize: 9, color: '#94a3b8', marginTop: 3 }}>{sessions} sessions this week</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Recent recipes */}
      <div style={{ background: 'rgba(255,255,255,0.72)', backdropFilter: 'blur(14px)', border: '1px solid rgba(148,163,184,0.2)', borderRadius: 18, padding: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 14 }}>
          <Calendar size={14} color="#7c3aed" />
          <span style={{ fontSize: 13, color: '#1e293b', fontWeight: 700 }}>Recent Cooks</span>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {recentRecipes.map(r => (
            <div key={r.name} style={{ display: 'flex', alignItems: 'center', gap: 10, background: 'rgba(148,163,184,0.06)', border: '1px solid rgba(148,163,184,0.14)', borderRadius: 12, padding: '9px 12px' }}>
              <div style={{ width: 32, height: 32, borderRadius: 10, background: r.type === 'veg' ? 'rgba(74,222,128,0.12)' : 'rgba(239,68,68,0.1)', border: `1px solid ${r.type === 'veg' ? 'rgba(74,222,128,0.3)' : 'rgba(239,68,68,0.22)'}`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <ChefHat size={14} color={r.type === 'veg' ? '#16a34a' : '#dc2626'} />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 12, color: '#1e293b', fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.name}</div>
                <div style={{ fontSize: 9, color: '#94a3b8', marginTop: 1 }}>{r.when}</div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 3, background: 'rgba(148,163,184,0.1)', borderRadius: 6, padding: '3px 7px', flexShrink: 0 }}>
                <Clock size={9} color="#64748b" />
                <span style={{ fontSize: 10, color: '#64748b', fontWeight: 600 }}>{r.time}</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Device health */}
      <div style={{ background: 'rgba(255,255,255,0.72)', backdropFilter: 'blur(14px)', border: '1px solid rgba(148,163,184,0.2)', borderRadius: 18, padding: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 12 }}>
          <ThermometerSun size={14} color="#0891b2" />
          <span style={{ fontSize: 13, color: '#1e293b', fontWeight: 700 }}>Device Health</span>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8 }}>
          {[
            { label: 'Temp', value: '24°C', color: '#4ade80', ok: true },
            { label: 'Wi-Fi', value: 'Strong', color: '#60a5fa', ok: true },
            { label: 'Firmware', value: 'v2.4.1', color: '#a78bfa', ok: true },
          ].map(({ label, value, color, ok }) => (
            <div key={label} style={{ background: `${color}0d`, border: `1px solid ${color}28`, borderRadius: 12, padding: '10px 8px', textAlign: 'center' }}>
              <div style={{ fontSize: 11, color, fontWeight: 700 }}>{value}</div>
              <div style={{ fontSize: 9, color: '#94a3b8', marginTop: 2 }}>{label}</div>
              {ok && <div style={{ width: 5, height: 5, borderRadius: '50%', background: color, margin: '5px auto 0' }} />}
            </div>
          ))}
        </div>
      </div>

      {/* Device connection card */}
      <div style={{ background: 'linear-gradient(135deg,rgba(37,99,235,0.12),rgba(59,130,246,0.06))', border: '1px solid rgba(96,165,250,0.28)', borderRadius: 18, padding: '14px 16px', display: 'flex', alignItems: 'center', gap: 12 }}>
        <div style={{ width: 38, height: 38, borderRadius: 12, background: 'rgba(96,165,250,0.15)', border: '1px solid rgba(96,165,250,0.28)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
          <Wifi size={18} color="#3b82f6" />
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 12, color: '#1e293b', fontWeight: 700 }}>On2Cook Pro Connected</div>
          <div style={{ fontSize: 10, color: '#64748b', marginTop: 1 }}>192.168.1.42 · Last sync 2 min ago</div>
        </div>
        <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#4ade80', boxShadow: '0 0 6px rgba(74,222,128,0.6)', flexShrink: 0 }} />
      </div>
    </div>
  );
}
