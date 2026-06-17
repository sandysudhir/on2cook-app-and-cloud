import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router';
import { Wifi, Bell, ChefHat, Plus, Clock, Star, Zap, PlayCircle, ArrowRight, Thermometer, LayoutGrid, Download, X, ChevronRight, RefreshCw, ChevronDown, Bluetooth, Pencil, Check } from 'lucide-react';
import { useApp } from '../../AppContext';
import { formatDuration, getRecipeTotalDuration } from '../../types';

const OTA_NOTIFICATIONS = [
  {
    id: 'ota-1',
    version: 'v2.5.0',
    title: 'Firmware Update Available',
    description: 'Improved microwave power precision (+5% accuracy), faster boot time, and fix for Wi-Fi reconnect on sleep.',
    features: ['Microwave power accuracy improved', 'Boot time reduced by 1.2s', 'Wi-Fi auto-reconnect fix'],
    size: '4.2 MB',
    date: 'Today',
    isNew: true,
  },
  {
    id: 'ota-2',
    version: 'v2.4.9',
    title: 'Stability Patch',
    description: 'Addresses rare stirrer stall on high-speed mode, and improves induction response at low power settings.',
    features: ['Stirrer stall fix at high speed', 'Induction low-power response improved'],
    size: '1.8 MB',
    date: '3 days ago',
    isNew: false,
  },
];

const MODULE_COLORS = {
  microwave: '#d97706',
  induction: '#dc2626',
  stirrer: '#7c3aed',
  water: '#0891b2',
};

type DeviceConnection = 'bluetooth' | 'wifi';
interface DeviceEntry {
  id: string;
  name: string;
  model: string;
  connection: DeviceConnection;
  status: 'online' | 'offline' | 'pairing';
  temp: string;
}

const DEFAULT_DEVICES: DeviceEntry[] = [
  { id: 'd1', name: 'On2Cook Pro', model: 'On2Cook Pro', connection: 'wifi', status: 'online', temp: '24°C' },
  { id: 'd2', name: 'Kitchen Home', model: 'On2Cook Home Kitchen', connection: 'bluetooth', status: 'offline', temp: '—' },
  { id: 'd3', name: 'Office Device', model: 'On2Cook Pro 2', connection: 'wifi', status: 'offline', temp: '—' },
];

export function HomeScreen() {
  const navigate = useNavigate();
  const { recipes, startNewRecipe, setCurrentRecipe, setCookingState, startProRecipe } = useApp();
  const favorites = recipes.filter((r) => r.isFavorite);
  const recent = recipes.filter((r) => r.lastUsed).slice(0, 3);
  const [showNotifications, setShowNotifications] = useState(false);
  const [updatingId, setUpdatingId] = useState<string | null>(null);
  const [updatedIds, setUpdatedIds] = useState<Set<string>>(new Set());

  // Device dropdown state
  const [devices, setDevices] = useState<DeviceEntry[]>(DEFAULT_DEVICES);
  const [selectedDeviceId, setSelectedDeviceId] = useState('d1');
  const [showDeviceDropdown, setShowDeviceDropdown] = useState(false);
  const [connectingId, setConnectingId] = useState<string | null>(null);
  const [editingDeviceId, setEditingDeviceId] = useState<string | null>(null);
  const [editingDeviceName, setEditingDeviceName] = useState('');
  const dropdownRef = useRef<HTMLDivElement>(null);

  const selectedDevice = devices.find(d => d.id === selectedDeviceId) ?? devices[0];

  useEffect(() => {
    function handleOutside(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setShowDeviceDropdown(false);
        setEditingDeviceId(null);
      }
    }
    if (showDeviceDropdown) document.addEventListener('mousedown', handleOutside);
    return () => document.removeEventListener('mousedown', handleOutside);
  }, [showDeviceDropdown]);

  function handleSelectDevice(id: string) {
    setConnectingId(id);
    setSelectedDeviceId(id);
    setTimeout(() => {
      setDevices(prev => prev.map(d => ({
        ...d,
        status: d.id === id ? 'online' : (d.status === 'online' ? 'offline' : d.status),
      })));
      setConnectingId(null);
      setShowDeviceDropdown(false);
    }, 1200);
  }

  function startEditingDevice(device: DeviceEntry, e: React.MouseEvent) {
    e.stopPropagation();
    setEditingDeviceId(device.id);
    setEditingDeviceName(device.name);
  }

  function saveDeviceName(id: string) {
    if (editingDeviceName.trim()) {
      setDevices(prev => prev.map(d => d.id === id ? { ...d, name: editingDeviceName.trim() } : d));
    }
    setEditingDeviceId(null);
  }

  function handleUpdate(id: string) {
    setUpdatingId(id);
    setTimeout(() => {
      setUpdatingId(null);
      setUpdatedIds(prev => new Set(prev).add(id));
    }, 2500);
  }

  function handleCreate() {
    startNewRecipe();
    navigate('/create');
  }

  function handleProEditor() {
    navigate('/preset-library');
  }

  function handleEditRecipe(recipe: typeof recipes[0]) {
    setCurrentRecipe(recipe);
    navigate('/create');
  }

  function handleStartCooking(recipe: typeof recipes[0]) {
    setCookingState({ recipe, isActive: true, isPaused: false });
    navigate('/cooking');
  }

  return (
    <div className="flex flex-col min-h-screen px-5 pt-14 pb-6" style={{ background: 'linear-gradient(160deg, #f0f4ff 0%, #f7f9ff 60%, #fafbff 100%)' }}>
      {/* Header */}
      <div className="flex items-center justify-between mb-7">
        <div className="flex items-center gap-3">
          <div
            className="w-10 h-10 rounded-xl flex items-center justify-center"
            style={{ background: 'linear-gradient(135deg, #1e40af, #3b82f6)', boxShadow: '0 4px 12px rgba(59,130,246,0.3)' }}
          >
            <ChefHat size={20} color="#fff" />
          </div>
          <div>
            <p style={{ fontSize: '11px', color: '#64748b', fontWeight: 500 }}>ON2COOK PRO</p>
            <div className="flex items-center gap-1.5">
              <div className="w-1.5 h-1.5 rounded-full" style={{ background: '#16a34a' }} />
              <span style={{ fontSize: '12px', color: '#16a34a', fontWeight: 500 }}>Connected</span>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <button
            className="w-9 h-9 rounded-xl flex items-center justify-center"
            style={{ background: 'rgba(255,255,255,0.8)', border: '1px solid rgba(148,163,184,0.22)', boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}
          >
            <Wifi size={16} color="#94a3b8" />
          </button>
          <button
            onClick={() => setShowNotifications(p => !p)}
            className="w-9 h-9 rounded-xl flex items-center justify-center relative"
            style={{
              background: showNotifications ? 'rgba(37,99,235,0.1)' : 'rgba(255,255,255,0.8)',
              border: showNotifications ? '1px solid rgba(37,99,235,0.3)' : '1px solid rgba(148,163,184,0.22)',
              boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
            }}
          >
            <Bell size={16} color={showNotifications ? '#2563eb' : '#94a3b8'} />
            {!updatedIds.has('ota-1') && (
              <div
                className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full"
                style={{ background: '#ef4444', border: '1px solid #f0f4ff' }}
              />
            )}
          </button>
        </div>
      </div>

      {/* Notification Panel */}
      {showNotifications && (
        <div
          style={{
            position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, zIndex: 40,
            background: 'rgba(0,0,0,0.2)',
          }}
          onClick={() => setShowNotifications(false)}
        >
          <div
            onClick={e => e.stopPropagation()}
            style={{
              position: 'absolute', top: 0, left: 0, right: 0,
              background: 'rgba(248,250,255,0.99)', backdropFilter: 'blur(24px)',
              borderBottom: '1px solid rgba(148,163,184,0.22)',
              boxShadow: '0 8px 32px rgba(0,0,0,0.12)',
              borderRadius: '0 0 20px 20px',
              padding: '16px 16px 18px',
              maxHeight: '80vh', overflowY: 'auto',
            }}
          >
            {/* Panel header */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div style={{ width: 32, height: 32, borderRadius: 10, background: 'rgba(37,99,235,0.1)', border: '1px solid rgba(37,99,235,0.22)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Bell size={15} color="#2563eb" />
                </div>
                <div>
                  <div style={{ fontSize: 14, color: '#0f172a', fontWeight: 800 }}>Notifications</div>
                  <div style={{ fontSize: 10, color: '#94a3b8' }}>{OTA_NOTIFICATIONS.filter(n => !updatedIds.has(n.id)).length} unread</div>
                </div>
              </div>
              <button
                onClick={() => setShowNotifications(false)}
                style={{ width: 28, height: 28, borderRadius: 8, background: 'rgba(148,163,184,0.1)', border: '1px solid rgba(148,163,184,0.2)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
              >
                <X size={14} color="#64748b" />
              </button>
            </div>

            {/* OTA section label */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 10 }}>
              <Download size={12} color="#7c3aed" />
              <span style={{ fontSize: 11, color: '#7c3aed', fontWeight: 700 }}>OTA Firmware Updates</span>
            </div>

            {/* Notification cards */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {OTA_NOTIFICATIONS.map(notif => {
                const isUpdated = updatedIds.has(notif.id);
                const isUpdating = updatingId === notif.id;
                return (
                  <div key={notif.id} style={{
                    background: isUpdated ? 'rgba(74,222,128,0.06)' : notif.isNew ? 'rgba(37,99,235,0.04)' : 'rgba(255,255,255,0.8)',
                    border: `1px solid ${isUpdated ? 'rgba(74,222,128,0.3)' : notif.isNew ? 'rgba(37,99,235,0.22)' : 'rgba(148,163,184,0.2)'}`,
                    borderRadius: 14, padding: '13px 14px',
                    boxShadow: '0 1px 6px rgba(0,0,0,0.05)',
                  }}>
                    {/* Header row */}
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                        <div style={{ width: 28, height: 28, borderRadius: 8, background: isUpdated ? 'rgba(74,222,128,0.12)' : 'rgba(124,58,237,0.1)', border: `1px solid ${isUpdated ? 'rgba(74,222,128,0.3)' : 'rgba(124,58,237,0.22)'}`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                          {isUpdated ? <ChevronRight size={13} color="#16a34a" /> : <Download size={13} color="#7c3aed" />}
                        </div>
                        <div>
                          <div style={{ fontSize: 12, color: '#0f172a', fontWeight: 700 }}>{notif.title}</div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                            <span style={{ fontSize: 10, color: '#7c3aed', fontWeight: 700 }}>{notif.version}</span>
                            <span style={{ fontSize: 9, color: '#94a3b8' }}>·</span>
                            <span style={{ fontSize: 9, color: '#94a3b8' }}>{notif.size}</span>
                            <span style={{ fontSize: 9, color: '#94a3b8' }}>·</span>
                            <span style={{ fontSize: 9, color: '#94a3b8' }}>{notif.date}</span>
                          </div>
                        </div>
                      </div>
                      {notif.isNew && !isUpdated && (
                        <span style={{ fontSize: 8, color: '#2563eb', background: 'rgba(37,99,235,0.1)', border: '1px solid rgba(37,99,235,0.25)', borderRadius: 4, padding: '1px 5px', fontWeight: 700 }}>NEW</span>
                      )}
                    </div>

                    {/* Description */}
                    <p style={{ fontSize: 11, color: '#475569', margin: '0 0 8px', lineHeight: 1.5 }}>{notif.description}</p>

                    {/* Feature list */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 3, marginBottom: 10 }}>
                      {notif.features.map((f, i) => (
                        <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                          <div style={{ width: 4, height: 4, borderRadius: '50%', background: '#7c3aed', flexShrink: 0 }} />
                          <span style={{ fontSize: 10, color: '#64748b' }}>{f}</span>
                        </div>
                      ))}
                    </div>

                    {/* CTA */}
                    {isUpdated ? (
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5, padding: '9px 0', background: 'rgba(74,222,128,0.1)', border: '1px solid rgba(74,222,128,0.25)', borderRadius: 9 }}>
                        <ChevronRight size={13} color="#16a34a" />
                        <span style={{ fontSize: 12, color: '#16a34a', fontWeight: 700 }}>Updated Successfully</span>
                      </div>
                    ) : (
                      <button
                        onClick={() => handleUpdate(notif.id)}
                        disabled={!!updatingId}
                        style={{
                          width: '100%', padding: '9px 0', borderRadius: 9, cursor: updatingId ? 'not-allowed' : 'pointer',
                          background: isUpdating ? 'rgba(37,99,235,0.12)' : 'linear-gradient(135deg, #1e40af, #3b82f6)',
                          border: isUpdating ? '1px solid rgba(37,99,235,0.3)' : 'none',
                          color: isUpdating ? '#2563eb' : '#fff', fontSize: 12, fontWeight: 700,
                          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
                          opacity: updatingId && !isUpdating ? 0.5 : 1,
                        }}
                      >
                        {isUpdating ? (
                          <>
                            <RefreshCw size={13} style={{ animation: 'spin 0.8s linear infinite' }} />
                            Updating firmware…
                          </>
                        ) : (
                          <>
                            <Download size={13} /> Update Now
                          </>
                        )}
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Greeting */}
      <div className="mb-7">
        <p style={{ fontSize: '14px', color: '#64748b', fontWeight: 400 }}>Welcome back, Chef 👋</p>
        <h1 style={{ fontSize: '26px', color: '#0f172a', fontWeight: 700, lineHeight: 1.2, marginTop: 2 }}>
          Let's Cook Something
          <br />
          <span style={{ color: '#2563eb' }}>Amazing</span> Today
        </h1>
      </div>

      {/* Device Status Card with Dropdown */}
      <div className="relative mb-6" ref={dropdownRef}>
        <button
          onClick={() => setShowDeviceDropdown(p => !p)}
          className="w-full rounded-2xl p-4 text-left relative overflow-hidden"
          style={{
            background: 'linear-gradient(135deg, rgba(219,234,254,0.95) 0%, rgba(239,246,255,0.98) 100%)',
            border: `1px solid ${showDeviceDropdown ? 'rgba(147,197,253,0.8)' : 'rgba(147,197,253,0.5)'}`,
            boxShadow: '0 4px 20px rgba(59,130,246,0.1)',
            cursor: 'pointer',
          }}
        >
          <div
            className="absolute top-0 right-0 w-40 h-40 rounded-full opacity-20"
            style={{ background: '#93c5fd', filter: 'blur(40px)', transform: 'translate(20%, -20%)' }}
          />
          <div className="flex items-center justify-between">
            <div>
              <div className="flex items-center gap-1.5">
                <p style={{ fontSize: '12px', color: '#2563eb', fontWeight: 500 }}>Device Status</p>
                <ChevronDown size={12} color="#2563eb" style={{ transform: showDeviceDropdown ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }} />
              </div>
              <p style={{ fontSize: '18px', color: '#0f172a', fontWeight: 700, marginTop: 2 }}>{selectedDevice.name}</p>
              <div className="flex items-center gap-2 mt-2">
                <span
                  className="px-2 py-0.5 rounded-full"
                  style={{ fontSize: '11px', background: selectedDevice.status === 'online' ? 'rgba(22,163,74,0.12)' : 'rgba(148,163,184,0.12)', color: selectedDevice.status === 'online' ? '#16a34a' : '#94a3b8', border: `1px solid ${selectedDevice.status === 'online' ? 'rgba(22,163,74,0.3)' : 'rgba(148,163,184,0.25)'}` }}
                >
                  {selectedDevice.status === 'online' ? 'Ready to Cook' : 'Offline'}
                </span>
                <span
                  className="px-2 py-0.5 rounded-full flex items-center gap-1"
                  style={{ fontSize: '11px', background: 'rgba(37,99,235,0.1)', color: '#2563eb', border: '1px solid rgba(37,99,235,0.2)' }}
                >
                  {selectedDevice.connection === 'bluetooth' ? <Bluetooth size={9} /> : <Wifi size={9} />}
                  {selectedDevice.connection === 'bluetooth' ? 'Bluetooth' : 'Wi-Fi'}
                </span>
              </div>
            </div>
            <div className="flex flex-col items-end gap-2">
              {selectedDevice.status === 'online' && (
                <div className="flex items-center gap-1.5">
                  <Thermometer size={14} color="#d97706" />
                  <span style={{ fontSize: '13px', color: '#d97706', fontWeight: 600 }}>{selectedDevice.temp}</span>
                </div>
              )}
              <div
                className="w-12 h-12 rounded-xl flex items-center justify-center"
                style={{ background: selectedDevice.status === 'online' ? 'rgba(37,99,235,0.1)' : 'rgba(148,163,184,0.1)', border: `1px solid ${selectedDevice.status === 'online' ? 'rgba(37,99,235,0.2)' : 'rgba(148,163,184,0.2)'}` }}
              >
                <Zap size={22} color={selectedDevice.status === 'online' ? '#2563eb' : '#94a3b8'} />
              </div>
            </div>
          </div>
        </button>

        {/* Dropdown panel */}
        {showDeviceDropdown && (
          <div
            className="absolute left-0 right-0 z-20 rounded-2xl overflow-hidden"
            style={{
              top: 'calc(100% + 6px)',
              background: 'rgba(255,255,255,0.98)',
              border: '1px solid rgba(147,197,253,0.5)',
              boxShadow: '0 8px 32px rgba(37,99,235,0.15)',
              backdropFilter: 'blur(20px)',
            }}
          >
            <div style={{ padding: '10px 14px 6px', borderBottom: '1px solid rgba(148,163,184,0.15)' }}>
              <span style={{ fontSize: 10, color: '#94a3b8', fontWeight: 600, letterSpacing: 0.5 }}>CONNECTED DEVICES</span>
            </div>
            {devices.map(device => {
              const isSelected = device.id === selectedDeviceId;
              const isConnecting = connectingId === device.id;
              const isEditing = editingDeviceId === device.id;
              return (
                <div
                  key={device.id}
                  onClick={() => !isEditing && handleSelectDevice(device.id)}
                  style={{
                    padding: '11px 14px', cursor: isEditing ? 'default' : 'pointer',
                    background: isSelected ? 'rgba(37,99,235,0.05)' : 'transparent',
                    borderBottom: '1px solid rgba(148,163,184,0.1)',
                    display: 'flex', alignItems: 'center', gap: 10,
                    transition: 'background 0.15s',
                  }}
                >
                  {/* Status dot */}
                  <div style={{
                    width: 8, height: 8, borderRadius: '50%', flexShrink: 0,
                    background: device.status === 'online' ? '#4ade80' : isConnecting ? '#fbbf24' : 'rgba(148,163,184,0.35)',
                    boxShadow: device.status === 'online' ? '0 0 6px rgba(74,222,128,0.6)' : 'none',
                  }} />

                  {/* Name + model */}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    {isEditing ? (
                      <input
                        autoFocus
                        value={editingDeviceName}
                        onChange={e => setEditingDeviceName(e.target.value)}
                        onKeyDown={e => { if (e.key === 'Enter') saveDeviceName(device.id); if (e.key === 'Escape') setEditingDeviceId(null); }}
                        onClick={e => e.stopPropagation()}
                        style={{ width: '100%', fontSize: 13, fontWeight: 600, color: '#0f172a', background: 'rgba(37,99,235,0.06)', border: '1px solid rgba(37,99,235,0.28)', borderRadius: 6, padding: '3px 8px', outline: 'none', boxSizing: 'border-box' }}
                      />
                    ) : (
                      <p style={{ fontSize: 13, color: isSelected ? '#1e40af' : '#0f172a', fontWeight: isSelected ? 700 : 500, margin: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{device.name}</p>
                    )}
                    <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginTop: 2 }}>
                      <span style={{ fontSize: 10, color: '#94a3b8' }}>{device.model}</span>
                      <span style={{ width: 3, height: 3, borderRadius: '50%', background: '#cbd5e1' }} />
                      <span style={{ fontSize: 10, color: '#94a3b8', display: 'flex', alignItems: 'center', gap: 2 }}>
                        {device.connection === 'bluetooth' ? <Bluetooth size={8} /> : <Wifi size={8} />}
                        {device.connection === 'bluetooth' ? 'BT' : 'Wi-Fi'}
                      </span>
                    </div>
                  </div>

                  {/* Connecting indicator or edit/check */}
                  {isConnecting ? (
                    <div style={{ width: 16, height: 16, borderRadius: '50%', border: '2px solid rgba(37,99,235,0.25)', borderTopColor: '#3b82f6', animation: 'spin 0.8s linear infinite', flexShrink: 0 }} />
                  ) : isEditing ? (
                    <button
                      onClick={e => { e.stopPropagation(); saveDeviceName(device.id); }}
                      style={{ width: 26, height: 26, borderRadius: 7, background: 'rgba(74,222,128,0.1)', border: '1px solid rgba(74,222,128,0.3)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}
                    >
                      <Check size={12} color="#16a34a" />
                    </button>
                  ) : (
                    <button
                      onClick={e => startEditingDevice(device, e)}
                      style={{ width: 26, height: 26, borderRadius: 7, background: 'rgba(148,163,184,0.1)', border: '1px solid rgba(148,163,184,0.2)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}
                    >
                      <Pencil size={11} color="#94a3b8" />
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Quick Actions */}
      <div className="grid grid-cols-2 gap-3 mb-7">
        <button
          onClick={handleCreate}
          className="rounded-2xl p-4 flex flex-col items-start gap-3 text-left active:scale-95 transition-transform"
          style={{
            background: 'linear-gradient(135deg, #1e40af 0%, #3b82f6 100%)',
            boxShadow: '0 8px 24px rgba(59,130,246,0.28)',
          }}
        >
          <div
            className="w-9 h-9 rounded-xl flex items-center justify-center"
            style={{ background: 'rgba(255,255,255,0.2)' }}
          >
            <Plus size={18} color="#fff" />
          </div>
          <div>
            <p style={{ fontSize: '14px', color: '#fff', fontWeight: 700 }}>Create Recipe</p>
            <p style={{ fontSize: '11px', color: 'rgba(255,255,255,0.75)', marginTop: 1 }}>Quick step editor</p>
          </div>
        </button>

        <button
          onClick={handleProEditor}
          className="rounded-2xl p-4 flex flex-col items-start gap-3 text-left active:scale-95 transition-transform"
          style={{
            background: 'linear-gradient(135deg, rgba(234,88,12,0.12) 0%, rgba(249,115,22,0.08) 100%)',
            border: '1px solid rgba(249,115,22,0.3)',
            boxShadow: '0 4px 16px rgba(249,115,22,0.12)',
          }}
        >
          <div
            className="w-9 h-9 rounded-xl flex items-center justify-center"
            style={{ background: 'rgba(249,115,22,0.15)', border: '1px solid rgba(249,115,22,0.25)' }}
          >
            <LayoutGrid size={18} color="#ea580c" />
          </div>
          <div>
            <p style={{ fontSize: '14px', color: '#c2410c', fontWeight: 700 }}>Edit Recipe</p>
            <p style={{ fontSize: '11px', color: 'rgba(194,65,12,0.7)', marginTop: 1 }}>Visual recipe editor</p>
          </div>
        </button>

        <button
          onClick={() => navigate('/library')}
          className="rounded-2xl p-4 flex flex-col items-start gap-3 text-left active:scale-95 transition-transform col-span-2"
          style={{
            background: 'rgba(255,255,255,0.8)',
            border: '1px solid rgba(148,163,184,0.22)',
            boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
            <div className="flex items-center gap-3">
              <div
                className="w-9 h-9 rounded-xl flex items-center justify-center"
                style={{ background: 'rgba(124,58,237,0.1)', border: '1px solid rgba(124,58,237,0.2)' }}
              >
                <Star size={18} color="#7c3aed" />
              </div>
              <div>
                <p style={{ fontSize: '14px', color: '#0f172a', fontWeight: 700 }}>My Recipes</p>
                <p style={{ fontSize: '11px', color: '#94a3b8', marginTop: 1 }}>{recipes.length} saved</p>
              </div>
            </div>
            <ArrowRight size={16} color="#94a3b8" />
          </div>
        </button>
      </div>

      {/* Favorites */}
      {favorites.length > 0 && (
        <div className="mb-7">
          <div className="flex items-center justify-between mb-3">
            <h2 style={{ fontSize: '16px', color: '#0f172a', fontWeight: 700 }}>Favourites</h2>
            <button
              onClick={() => navigate('/library')}
              className="flex items-center gap-1"
              style={{ fontSize: '12px', color: '#2563eb' }}
            >
              View All <ArrowRight size={12} />
            </button>
          </div>
          <div className="flex gap-3 overflow-x-auto pb-2" style={{ scrollbarWidth: 'none' }}>
            {favorites.map((recipe) => {
              const totalDuration = getRecipeTotalDuration(recipe);
              return (
                <div
                  key={recipe.id}
                  className="flex-shrink-0 rounded-2xl p-4 cursor-pointer active:scale-95 transition-transform"
                  style={{
                    width: 170,
                    background: 'rgba(255,255,255,0.85)',
                    border: '1px solid rgba(148,163,184,0.2)',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
                  }}
                  onClick={() => handleEditRecipe(recipe)}
                >
                  <div className="flex items-center justify-between mb-3">
                    <span
                      className="px-2 py-0.5 rounded-full"
                      style={{ fontSize: '10px', background: 'rgba(217,119,6,0.1)', color: '#d97706', border: '1px solid rgba(217,119,6,0.2)' }}
                    >
                      ★ Fav
                    </span>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleStartCooking(recipe); }}
                      className="w-7 h-7 rounded-full flex items-center justify-center"
                      style={{ background: 'rgba(37,99,235,0.12)', border: '1px solid rgba(37,99,235,0.25)' }}
                    >
                      <PlayCircle size={14} color="#2563eb" />
                    </button>
                  </div>
                  <p style={{ fontSize: '13px', color: '#0f172a', fontWeight: 600, lineHeight: 1.3 }}>{recipe.name}</p>
                  <div className="flex items-center gap-1 mt-2">
                    <Clock size={11} color="#94a3b8" />
                    <span style={{ fontSize: '11px', color: '#94a3b8' }}>{formatDuration(totalDuration)}</span>
                    <span style={{ fontSize: '11px', color: '#cbd5e1', marginLeft: 4 }}>{recipe.steps.length} steps</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Recent Cooks */}
      {recent.length > 0 && (
        <div>
          <div className="flex items-center justify-between mb-3">
            <h2 style={{ fontSize: '16px', color: '#0f172a', fontWeight: 700 }}>Recent Cooks</h2>
          </div>
          <div className="flex flex-col gap-2.5">
            {recent.map((recipe) => {
              const totalDuration = getRecipeTotalDuration(recipe);
              const activeMods = recipe.steps[0];
              const tags = [
                activeMods?.microwave.enabled && { label: 'MW', color: MODULE_COLORS.microwave },
                activeMods?.induction.enabled && { label: 'IH', color: MODULE_COLORS.induction },
                activeMods?.stirrer.enabled && { label: 'ST', color: MODULE_COLORS.stirrer },
                activeMods?.water.enabled && { label: 'W', color: MODULE_COLORS.water },
              ].filter(Boolean) as { label: string; color: string }[];
              return (
                <div
                  key={recipe.id}
                  className="rounded-2xl p-4 flex items-center gap-3 cursor-pointer active:scale-95 transition-transform"
                  style={{
                    background: 'rgba(255,255,255,0.8)',
                    border: '1px solid rgba(148,163,184,0.18)',
                    boxShadow: '0 1px 6px rgba(0,0,0,0.05)',
                  }}
                  onClick={() => handleEditRecipe(recipe)}
                >
                  <div
                    className="w-11 h-11 rounded-xl flex-shrink-0 flex items-center justify-center"
                    style={{ background: 'rgba(37,99,235,0.08)', border: '1px solid rgba(37,99,235,0.15)' }}
                  >
                    <ChefHat size={20} color="#2563eb" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p style={{ fontSize: '14px', color: '#0f172a', fontWeight: 600 }} className="truncate">{recipe.name}</p>
                    <div className="flex items-center gap-2 mt-1">
                      <span style={{ fontSize: '11px', color: '#94a3b8' }}>{recipe.steps.length} steps · {formatDuration(totalDuration)}</span>
                      <div className="flex gap-1">
                        {tags.slice(0, 3).map((tag) => (
                          <span
                            key={tag.label}
                            style={{ fontSize: '9px', color: tag.color, background: `${tag.color}18`, border: `1px solid ${tag.color}35`, padding: '1px 5px', borderRadius: 4 }}
                          >
                            {tag.label}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                  <button
                    onClick={(e) => { e.stopPropagation(); handleStartCooking(recipe); }}
                    className="flex-shrink-0 w-8 h-8 rounded-xl flex items-center justify-center"
                    style={{ background: 'rgba(37,99,235,0.1)', border: '1px solid rgba(37,99,235,0.2)' }}
                  >
                    <PlayCircle size={16} color="#2563eb" />
                  </button>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}