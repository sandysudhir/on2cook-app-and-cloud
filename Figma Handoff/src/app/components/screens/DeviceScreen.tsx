import { Cpu, Wifi, Thermometer, Zap, Waves, RotateCcw, Droplets, ShieldCheck, RefreshCw } from 'lucide-react';

const modules = [
  { icon: Zap, label: 'Microwave', status: 'Ready', color: '#fbbf24', power: '750W max' },
  { icon: Waves, label: 'Induction', status: 'Ready', color: '#f87171', power: '1200W max' },
  { icon: RotateCcw, label: 'Stirrer', status: 'Ready', color: '#c084fc', power: '5 speeds' },
  { icon: Droplets, label: 'Water', status: 'Ready', color: '#22d3ee', power: '0–500ml' },
];

export function DeviceScreen() {
  return (
    <div className="flex flex-col min-h-screen px-5 pt-14 pb-6">
      {/* Header */}
      <div className="mb-6">
        <h1 style={{ fontSize: '24px', color: '#f1f5f9', fontWeight: 700 }}>Device Status</h1>
        <p style={{ fontSize: '13px', color: '#475569', marginTop: 2 }}>On2Cook Pro — Connected</p>
      </div>

      {/* Main status card */}
      <div
        className="rounded-2xl p-5 mb-5 relative overflow-hidden"
        style={{
          background: 'linear-gradient(135deg, rgba(30,64,175,0.4) 0%, rgba(59,130,246,0.15) 100%)',
          border: '1px solid rgba(96,165,250,0.3)',
        }}
      >
        <div
          className="absolute top-0 right-0 w-48 h-48 rounded-full opacity-10"
          style={{ background: '#60a5fa', filter: 'blur(50px)', transform: 'translate(30%, -30%)' }}
        />
        <div className="flex items-center gap-4 mb-4">
          <div
            className="w-14 h-14 rounded-2xl flex items-center justify-center"
            style={{ background: 'rgba(96,165,250,0.15)', border: '1px solid rgba(96,165,250,0.3)' }}
          >
            <Cpu size={28} color="#60a5fa" />
          </div>
          <div>
            <p style={{ fontSize: '18px', color: '#f1f5f9', fontWeight: 700 }}>On2Cook Pro</p>
            <div className="flex items-center gap-2 mt-1">
              <div className="w-2 h-2 rounded-full" style={{ background: '#4ade80' }} />
              <span style={{ fontSize: '12px', color: '#4ade80', fontWeight: 500 }}>Online & Ready</span>
            </div>
          </div>
        </div>
        <div className="grid grid-cols-3 gap-3">
          {[
            { icon: Wifi, label: 'Wi-Fi', value: 'Connected', color: '#60a5fa' },
            { icon: Thermometer, label: 'Temp', value: '24°C', color: '#fbbf24' },
            { icon: ShieldCheck, label: 'Lid', value: 'Closed ✓', color: '#4ade80' },
          ].map(({ icon: Icon, label, value, color }) => (
            <div
              key={label}
              className="rounded-xl p-3 text-center"
              style={{ background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)' }}
            >
              <Icon size={16} color={color} className="mx-auto mb-1" />
              <p style={{ fontSize: '10px', color: '#64748b' }}>{label}</p>
              <p style={{ fontSize: '11px', color, fontWeight: 600, marginTop: 1 }}>{value}</p>
            </div>
          ))}
        </div>
      </div>

      {/* Module status */}
      <div className="mb-5">
        <h2 style={{ fontSize: '14px', color: '#f1f5f9', fontWeight: 700, marginBottom: 12 }}>
          Cooking Modules
        </h2>
        <div className="space-y-2.5">
          {modules.map(({ icon: Icon, label, status, color, power }) => (
            <div
              key={label}
              className="flex items-center gap-3 rounded-2xl px-4 py-3"
              style={{
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.08)',
              }}
            >
              <div
                className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
                style={{ background: `${color}18`, border: `1px solid ${color}30` }}
              >
                <Icon size={18} color={color} />
              </div>
              <div className="flex-1">
                <p style={{ fontSize: '14px', color: '#e2e8f0', fontWeight: 600 }}>{label}</p>
                <p style={{ fontSize: '11px', color: '#475569', marginTop: 1 }}>{power}</p>
              </div>
              <span
                className="px-2.5 py-1 rounded-full"
                style={{
                  fontSize: '11px',
                  color: '#4ade80',
                  background: 'rgba(74,222,128,0.1)',
                  border: '1px solid rgba(74,222,128,0.25)',
                  fontWeight: 500,
                }}
              >
                {status}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Firmware */}
      <div
        className="rounded-2xl px-4 py-4 flex items-center justify-between"
        style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)' }}
      >
        <div>
          <p style={{ fontSize: '13px', color: '#94a3b8', fontWeight: 500 }}>Firmware Version</p>
          <p style={{ fontSize: '14px', color: '#e2e8f0', fontWeight: 600, marginTop: 2 }}>v2.4.1 — Up to date</p>
        </div>
        <button
          className="flex items-center gap-1.5 px-3 py-2 rounded-xl active:scale-95 transition-transform"
          style={{ background: 'rgba(96,165,250,0.1)', border: '1px solid rgba(96,165,250,0.2)' }}
        >
          <RefreshCw size={13} color="#60a5fa" />
          <span style={{ fontSize: '12px', color: '#60a5fa' }}>Check</span>
        </button>
      </div>
    </div>
  );
}
