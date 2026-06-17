import { useNavigate, useLocation } from 'react-router';
import { Home, BookOpen, LayoutDashboard, HeadphonesIcon } from 'lucide-react';

const navItems = [
  { icon: Home, label: 'Home', path: '/' },
  { icon: BookOpen, label: 'Recipes', path: '/library' },
  { icon: LayoutDashboard, label: 'Dashboard', path: '/dashboard' },
  { icon: HeadphonesIcon, label: 'Support', path: '/support' },
];

export function BottomNav() {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <div
      style={{
        flexShrink: 0,
        background: 'rgba(245,247,255,0.97)',
        backdropFilter: 'blur(20px)',
        borderTop: '1px solid rgba(148,163,184,0.2)',
        paddingBottom: 18,
        boxShadow: '0 -1px 12px rgba(0,0,0,0.06)',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-around',
          padding: '10px 16px 0',
        }}
      >
        {navItems.map(({ icon: Icon, label, path }) => {
          const isActive =
            path === '/' ? location.pathname === '/' : location.pathname.startsWith(path);
          return (
            <button
              key={path}
              onClick={() => navigate(path)}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 4,
                padding: '6px 16px',
                borderRadius: 12,
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                WebkitTapHighlightColor: 'transparent',
              }}
            >
              <Icon size={22} style={{ color: isActive ? '#2563eb' : '#94a3b8' }} />
              <span
                style={{
                  fontSize: 11,
                  color: isActive ? '#2563eb' : '#94a3b8',
                  fontWeight: isActive ? 600 : 400,
                  fontFamily: "'Space Grotesk', sans-serif",
                }}
              >
                {label}
              </span>
              {isActive && (
                <div style={{ width: 4, height: 4, borderRadius: '50%', background: '#2563eb' }} />
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}