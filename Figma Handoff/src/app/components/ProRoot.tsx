import React from 'react';
import { Outlet, useLocation } from 'react-router';
import { LandscapePhoneFrame } from './LandscapePhoneFrame';
import { useEffect } from 'react';
import { announceProStudioRoute } from '../proStudioBridge';

/**
 * Full-screen landscape layout for professional editor screens.
 * Uses a landscape iPhone frame — no bottom nav.
 */
export function ProRoot() {
  const location = useLocation();
  useEffect(() => {
    announceProStudioRoute(location);
  }, [location]);

  return (
    <LandscapePhoneFrame>
      <div
        style={{
          width: '100%',
          height: '100%',
          background: 'linear-gradient(165deg, #0d1b35 0%, #050e22 60%, #030b1a 100%)',
          fontFamily: "'Space Grotesk', sans-serif",
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Outlet />
      </div>
    </LandscapePhoneFrame>
  );
}
