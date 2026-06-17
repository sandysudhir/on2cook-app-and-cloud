import { Outlet, useLocation } from 'react-router';
import { BottomNav } from './BottomNav';
import { PhoneFrame } from './PhoneFrame';

export function Root() {
  const location = useLocation();
  const hideNav =
    location.pathname === '/cooking' ||
    location.pathname === '/export' ||
    location.pathname === '/preset-library' ||
    location.pathname === '/preset-setup';

  return (
    <PhoneFrame>
      <div
        style={{
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          background: 'linear-gradient(165deg, #0d1b35 0%, #050e22 60%, #030b1a 100%)',
          fontFamily: "'Space Grotesk', sans-serif",
          position: 'relative',
        }}
      >
        {/* Scrollable content area — fills available space */}
        <div
          style={{
            flex: 1,
            overflowY: 'auto',
            overflowX: 'hidden',
          }}
        >
          <Outlet />
        </div>

        {/* Bottom nav sits at the bottom of the phone screen (not fixed) */}
        {!hideNav && <BottomNav />}
      </div>
    </PhoneFrame>
  );
}