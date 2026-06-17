import { useState } from 'react';
import {
  HelpCircle, MessageCircle, BookOpen, ChevronDown, ChevronRight,
  Wifi, AlertTriangle, Thermometer, RotateCcw, Zap, Waves,
  Mail, Phone, ExternalLink, CheckCircle2, Wrench, GraduationCap,
  UserCheck, ChefHat, Star, Award,
} from 'lucide-react';

const faqs = [
  {
    q: 'Why is the microwave not turning on?',
    a: 'The microwave is automatically disabled whenever the lid is open. Ensure the lid is firmly closed and the seal indicator shows green before starting a microwave-enabled recipe.',
  },
  {
    q: 'How do I calibrate the induction power?',
    a: 'Go to Settings → Device Calibration and follow the guided procedure. Calibration takes 5 minutes and should be done every 3 months for accurate results.',
  },
  {
    q: 'The stirrer is making a grinding noise — what should I do?',
    a: 'Turn off the device and inspect the stirrer blade for any food debris. Soak in warm water for 10 minutes, rinse, and retry. If the noise persists, contact support.',
  },
  {
    q: 'Can I run a recipe with both induction and microwave at the same time?',
    a: 'Yes — On2Cook Pro is designed for simultaneous multi-energy cooking. The timeline editor lets you configure both independently for each 15-second block.',
  },
  {
    q: 'My device lost Wi-Fi connection mid-cook. What happened to the recipe?',
    a: 'The device stores the full recipe locally before cooking starts, so a Wi-Fi drop during cooking does not interrupt the cook. The recipe continues as planned and syncs when reconnected.',
  },
  {
    q: 'How do I add custom power levels to a microwave block?',
    a: 'Open the Professional Recipe Editor, tap the microwave row for any minute, and use the edit modal. Each 15-second sub-block can be toggled ON/OFF with its own power level (180W–900W).',
  },
];

const guides = [
  { icon: BookOpen, title: 'Getting Started Guide', desc: 'Setup, first cook, and basic navigation', color: '#2563eb' },
  { icon: Zap, title: 'Microwave Cooking Tips', desc: 'Power levels, timing, and best practices', color: '#ec4899' },
  { icon: Waves, title: 'Induction Mastery', desc: 'Understanding power curves and presets', color: '#f97316' },
  { icon: RotateCcw, title: 'Stirrer Patterns', desc: 'Speed modes and when to use each', color: '#14b8a6' },
  { icon: Thermometer, title: 'Temperature Control', desc: 'Consistent results with smart hold logic', color: '#d97706' },
  { icon: Wifi, title: 'Connectivity & Sync', desc: 'Wi-Fi setup, app pairing, and offline mode', color: '#8b5cf6' },
];

const serviceCategories = [
  {
    icon: Wrench,
    title: 'Repair & Breakdown',
    desc: 'Device not working? Schedule a repair or report a breakdown.',
    color: '#dc2626',
    badge: 'Same Day',
  },
  {
    icon: GraduationCap,
    title: 'Chef Training',
    desc: 'Learn advanced cooking techniques from certified professional chefs.',
    color: '#7c3aed',
    badge: 'Online & Offline',
  },
  {
    icon: UserCheck,
    title: 'Hire an Expert',
    desc: 'Get a device expert to help with setup, calibration, or optimisation.',
    color: '#0891b2',
    badge: 'On Demand',
  },
  {
    icon: ChefHat,
    title: 'Hire a Chef',
    desc: 'Book a professional chef for events, special occasions, or meal prep.',
    color: '#16a34a',
    badge: 'Premium',
  },
  {
    icon: Star,
    title: 'Recipe Consultation',
    desc: 'Get personalised recipe advice tailored to your dietary preferences.',
    color: '#d97706',
    badge: 'Personalised',
  },
  {
    icon: Award,
    title: 'Annual Maintenance',
    desc: 'Schedule a comprehensive annual device check-up and cleaning service.',
    color: '#2563eb',
    badge: 'Recommended',
  },
];

const statusItems = [
  { label: 'App Service', status: 'Operational', ok: true },
  { label: 'Recipe Sync', status: 'Operational', ok: true },
  { label: 'Device Cloud', status: 'Operational', ok: true },
  { label: 'OTA Updates', status: 'Maintenance', ok: false },
];

export function SupportScreen() {
  const [openFaq, setOpenFaq] = useState<number | null>(null);
  const [activeSection, setActiveSection] = useState<'faq' | 'guides' | 'services' | 'contact'>('faq');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100%', overflowY: 'auto', padding: '56px 18px 24px', gap: 18, background: 'linear-gradient(160deg, #f0f4ff 0%, #f7f9ff 60%, #fafbff 100%)' }}>
      {/* Header */}
      <div>
        <h1 style={{ fontSize: 22, color: '#1e293b', fontWeight: 800, margin: 0 }}>Support</h1>
        <p style={{ fontSize: 12, color: '#94a3b8', margin: 0, marginTop: 2 }}>Help Centre & Troubleshooting</p>
      </div>

      {/* Section tabs */}
      <div style={{ display: 'flex', gap: 5, background: 'rgba(255,255,255,0.6)', border: '1px solid rgba(148,163,184,0.2)', borderRadius: 12, padding: 4 }}>
        {([
          { key: 'faq', label: 'FAQ' },
          { key: 'guides', label: 'Guides' },
          { key: 'services', label: 'Services' },
          { key: 'contact', label: 'Contact' },
        ] as const).map(({ key, label }) => (
          <button
            key={key}
            onClick={() => setActiveSection(key)}
            style={{
              flex: 1, padding: '7px 0', borderRadius: 9, cursor: 'pointer', fontSize: 11, fontWeight: 600, border: 'none',
              background: activeSection === key ? 'rgba(37,99,235,0.12)' : 'transparent',
              color: activeSection === key ? '#2563eb' : '#64748b',
              transition: 'all 0.15s',
            }}
          >
            {label}
          </button>
        ))}
      </div>

      {/* FAQ Section */}
      {activeSection === 'faq' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 2 }}>
            <HelpCircle size={14} color="#2563eb" />
            <span style={{ fontSize: 13, color: '#1e293b', fontWeight: 700 }}>Frequently Asked Questions</span>
          </div>
          {faqs.map((faq, i) => {
            const isOpen = openFaq === i;
            return (
              <div
                key={i}
                style={{
                  background: isOpen ? 'rgba(255,255,255,0.95)' : 'rgba(255,255,255,0.75)',
                  backdropFilter: 'blur(14px)',
                  border: `1px solid ${isOpen ? 'rgba(37,99,235,0.28)' : 'rgba(148,163,184,0.2)'}`,
                  borderRadius: 14,
                  overflow: 'hidden',
                  transition: 'border-color 0.15s',
                  boxShadow: isOpen ? '0 2px 10px rgba(37,99,235,0.06)' : '0 1px 4px rgba(0,0,0,0.04)',
                }}
              >
                <button
                  onClick={() => setOpenFaq(isOpen ? null : i)}
                  style={{
                    width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                    gap: 10, padding: '13px 14px', background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left',
                  }}
                >
                  <span style={{ fontSize: 12, color: '#1e293b', fontWeight: 600, flex: 1, lineHeight: 1.4 }}>{faq.q}</span>
                  {isOpen ? <ChevronDown size={14} color="#2563eb" /> : <ChevronRight size={14} color="#94a3b8" />}
                </button>
                {isOpen && (
                  <div style={{ padding: '0 14px 14px', fontSize: 11, color: '#475569', lineHeight: 1.6, borderTop: '1px solid rgba(148,163,184,0.15)' }}>
                    <div style={{ paddingTop: 10 }}>{faq.a}</div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Guides Section */}
      {activeSection === 'guides' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 2 }}>
            <BookOpen size={14} color="#7c3aed" />
            <span style={{ fontSize: 13, color: '#1e293b', fontWeight: 700 }}>Learning Guides</span>
          </div>
          {guides.map(({ icon: Icon, title, desc, color }) => (
            <div
              key={title}
              style={{
                background: 'rgba(255,255,255,0.8)', backdropFilter: 'blur(14px)',
                border: '1px solid rgba(148,163,184,0.2)', borderRadius: 14, padding: '13px 14px',
                display: 'flex', alignItems: 'center', gap: 12, cursor: 'pointer',
                boxShadow: '0 1px 5px rgba(0,0,0,0.05)',
              }}
            >
              <div style={{ width: 38, height: 38, borderRadius: 11, background: `${color}12`, border: `1px solid ${color}28`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <Icon size={17} color={color} />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 12, color: '#1e293b', fontWeight: 700 }}>{title}</div>
                <div style={{ fontSize: 10, color: '#64748b', marginTop: 2 }}>{desc}</div>
              </div>
              <ExternalLink size={13} color="#94a3b8" />
            </div>
          ))}
        </div>
      )}

      {/* Services Section */}
      {activeSection === 'services' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 2 }}>
            <Wrench size={14} color="#dc2626" />
            <span style={{ fontSize: 13, color: '#1e293b', fontWeight: 700 }}>Service Categories</span>
          </div>
          <p style={{ fontSize: 11, color: '#64748b', margin: '0 0 4px', lineHeight: 1.5 }}>
            Professional services for your On2Cook Pro device and cooking experience.
          </p>
          {serviceCategories.map(({ icon: Icon, title, desc, color, badge }) => (
            <div
              key={title}
              style={{
                background: 'rgba(255,255,255,0.82)', backdropFilter: 'blur(14px)',
                border: '1px solid rgba(148,163,184,0.2)', borderRadius: 14, padding: '14px',
                cursor: 'pointer', boxShadow: '0 1px 5px rgba(0,0,0,0.05)',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                <div style={{ width: 40, height: 40, borderRadius: 12, background: `${color}12`, border: `1px solid ${color}28`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <Icon size={18} color={color} />
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 3 }}>
                    <div style={{ fontSize: 13, color: '#1e293b', fontWeight: 700 }}>{title}</div>
                    <span style={{ fontSize: 9, color, background: `${color}12`, border: `1px solid ${color}28`, borderRadius: 5, padding: '1px 6px', fontWeight: 600, flexShrink: 0 }}>{badge}</span>
                  </div>
                  <div style={{ fontSize: 11, color: '#64748b', lineHeight: 1.5 }}>{desc}</div>
                </div>
              </div>
              <button
                style={{
                  marginTop: 10, width: '100%', padding: '9px 0', borderRadius: 9, cursor: 'pointer',
                  background: `${color}10`, border: `1px solid ${color}28`,
                  color, fontSize: 12, fontWeight: 700,
                }}
              >
                Book Now →
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Contact Section */}
      {activeSection === 'contact' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {/* System status */}
          <div style={{ background: 'rgba(255,255,255,0.8)', backdropFilter: 'blur(14px)', border: '1px solid rgba(148,163,184,0.2)', borderRadius: 16, padding: '14px 14px', boxShadow: '0 1px 5px rgba(0,0,0,0.05)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 12 }}>
              <CheckCircle2 size={14} color="#16a34a" />
              <span style={{ fontSize: 13, color: '#1e293b', fontWeight: 700 }}>System Status</span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
              {statusItems.map(({ label, status, ok }) => (
                <div key={label} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontSize: 11, color: '#475569' }}>{label}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 5, background: ok ? 'rgba(74,222,128,0.1)' : 'rgba(251,191,36,0.12)', border: `1px solid ${ok ? 'rgba(74,222,128,0.3)' : 'rgba(251,191,36,0.35)'}`, borderRadius: 6, padding: '2px 8px' }}>
                    <div style={{ width: 5, height: 5, borderRadius: '50%', background: ok ? '#4ade80' : '#fbbf24' }} />
                    <span style={{ fontSize: 9, color: ok ? '#16a34a' : '#92400e', fontWeight: 600 }}>{status}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Report issue */}
          <div style={{ background: 'rgba(255,255,255,0.8)', backdropFilter: 'blur(14px)', border: '1px solid rgba(239,68,68,0.18)', borderRadius: 16, padding: '14px 14px', boxShadow: '0 1px 5px rgba(0,0,0,0.05)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 10 }}>
              <AlertTriangle size={14} color="#ef4444" />
              <span style={{ fontSize: 13, color: '#1e293b', fontWeight: 700 }}>Report an Issue</span>
            </div>
            <p style={{ fontSize: 11, color: '#64748b', margin: '0 0 12px', lineHeight: 1.5 }}>
              Experiencing a problem with your device or the app? Describe the issue and our team will get back to you within 24 hours.
            </p>
            <textarea
              placeholder="Describe the issue you're experiencing…"
              style={{
                width: '100%', minHeight: 80, padding: '10px 12px', borderRadius: 10,
                background: 'rgba(255,255,255,0.9)', border: '1px solid rgba(148,163,184,0.28)',
                color: '#1e293b', fontSize: 11, resize: 'none', outline: 'none',
                boxSizing: 'border-box', fontFamily: "'Space Grotesk', sans-serif", lineHeight: 1.5,
              }}
            />
            <button style={{ marginTop: 10, width: '100%', padding: '11px 0', borderRadius: 10, cursor: 'pointer', background: 'linear-gradient(135deg,#dc2626,#ef4444)', border: 'none', color: '#fff', fontSize: 12, fontWeight: 700 }}>
              Submit Report
            </button>
          </div>

          {/* Contact options */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div style={{ fontSize: 12, color: '#64748b', fontWeight: 600, marginBottom: 2 }}>Direct Contact</div>
            {[
              { icon: Mail, label: 'Email Support', sub: 'support@on2cook.com', color: '#2563eb' },
              { icon: Phone, label: 'Call Us', sub: '+91 98765 43210', color: '#16a34a' },
              { icon: MessageCircle, label: 'Live Chat', sub: 'Available 9 AM – 6 PM IST', color: '#7c3aed' },
            ].map(({ icon: Icon, label, sub, color }) => (
              <div key={label} style={{ background: 'rgba(255,255,255,0.8)', backdropFilter: 'blur(14px)', border: '1px solid rgba(148,163,184,0.2)', borderRadius: 14, padding: '12px 14px', display: 'flex', alignItems: 'center', gap: 12, cursor: 'pointer', boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
                <div style={{ width: 34, height: 34, borderRadius: 10, background: `${color}12`, border: `1px solid ${color}28`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <Icon size={15} color={color} />
                </div>
                <div>
                  <div style={{ fontSize: 12, color: '#1e293b', fontWeight: 700 }}>{label}</div>
                  <div style={{ fontSize: 10, color: '#64748b', marginTop: 1 }}>{sub}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
