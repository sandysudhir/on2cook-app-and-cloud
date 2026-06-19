import React, { useEffect, useState } from 'react';

interface LandscapePhoneFrameProps {
  children: React.ReactNode;
}

/**
 * A realistic iPhone landscape frame (844×390 screen area).
 * The phone is shown in landscape orientation with Dynamic Island on the left short side.
 * position:fixed descendants are captured within the screen div via CSS transform.
 */
export function LandscapePhoneFrame({ children }: LandscapePhoneFrameProps) {
  const [scale, setScale] = useState(1);

  useEffect(() => {
    const updateScale = () => {
      const width = Math.max(320, window.innerWidth || 868);
      const height = Math.max(240, window.innerHeight || 414);
      const nextScale = Math.min(1, (width - 12) / 868, (height - 12) / 414);
      setScale(Math.max(0.34, Number.isFinite(nextScale) ? nextScale : 1));
    };
    updateScale();
    window.addEventListener('resize', updateScale);
    window.addEventListener('orientationchange', updateScale);
    return () => {
      window.removeEventListener('resize', updateScale);
      window.removeEventListener('orientationchange', updateScale);
    };
  }, []);

  return (
    <div
      style={{
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'radial-gradient(ellipse 90% 80% at 50% 30%, #c8d9f0 0%, #dde8f8 40%, #edf2fc 70%, #f3f6ff 100%)',
        padding: '6px',
        fontFamily: "'Space Grotesk', sans-serif",
        overflow: 'hidden',
      }}
    >
      {/* Phone body (landscape: wide × short) */}
      <div style={{ width: 868 * scale, height: 414 * scale, position: 'relative', flexShrink: 0 }}>
      <div style={{ position: 'relative', width: 868, height: 414, transform: `scale(${scale})`, transformOrigin: 'top left' }}>
        {/* Volume / top buttons — on top edge when landscape */}
        <div style={{ position: 'absolute', top: -4, left: 100, width: 28, height: 4, borderRadius: '3px 3px 0 0', background: 'linear-gradient(90deg,#b0b0b2,#9a9a9c)', boxShadow: '0 -2px 4px rgba(0,0,0,0.2)' }} />
        <div style={{ position: 'absolute', top: -4, left: 148, width: 58, height: 4, borderRadius: '3px 3px 0 0', background: 'linear-gradient(90deg,#b0b0b2,#9a9a9c)', boxShadow: '0 -2px 4px rgba(0,0,0,0.2)' }} />
        <div style={{ position: 'absolute', top: -4, left: 220, width: 58, height: 4, borderRadius: '3px 3px 0 0', background: 'linear-gradient(90deg,#b0b0b2,#9a9a9c)', boxShadow: '0 -2px 4px rgba(0,0,0,0.2)' }} />
        {/* Power / lock button — on bottom edge */}
        <div style={{ position: 'absolute', bottom: -4, left: 170, width: 88, height: 4, borderRadius: '0 0 3px 3px', background: 'linear-gradient(90deg,#b0b0b2,#9a9a9c)', boxShadow: '0 2px 4px rgba(0,0,0,0.2)' }} />

        {/* Chassis */}
        <div
          style={{
            width: 868,
            height: 414,
            background: 'linear-gradient(160deg,#d8d8da 0%,#c4c4c6 30%,#b8b8ba 100%)',
            borderRadius: 55,
            padding: 12,
            boxShadow:
              '0 0 0 1px rgba(255,255,255,0.6), 0 0 0 2px rgba(0,0,0,0.15), 0 40px 100px rgba(0,0,0,0.22), 0 16px 40px rgba(0,0,0,0.1), inset 0 1px 0 rgba(255,255,255,0.5)',
          }}
        >
          {/* Screen */}
          <div
            style={{
              width: 844,
              height: 390,
              borderRadius: 44,
              overflow: 'hidden',
              background: '#f0f4ff',
              position: 'relative',
              transform: 'translate(0,0)',
            }}
          >
            {/* Dynamic Island — left short side (landscape left) */}
            <div
              style={{
                position: 'absolute',
                left: 12,
                top: '50%',
                transform: 'translateY(-50%)',
                width: 37,
                height: 122,
                background: '#000',
                borderRadius: 20,
                zIndex: 200,
                pointerEvents: 'none',
              }}
            />

            {/* Home indicator — bottom edge */}
            <div
              style={{
                position: 'absolute',
                bottom: 9,
                left: '50%',
                transform: 'translateX(-50%)',
                width: 134,
                height: 5,
                background: 'rgba(0,0,0,0.18)',
                borderRadius: 3,
                zIndex: 200,
                pointerEvents: 'none',
              }}
            />

            {/* Minimal status indicators — top right corner */}
            <div
              style={{
                position: 'absolute',
                top: 8,
                right: 14,
                display: 'flex',
                alignItems: 'center',
                gap: 5,
                zIndex: 150,
                pointerEvents: 'none',
              }}
            >
              <div style={{ display: 'flex', gap: 1.5, alignItems: 'flex-end' }}>
                {[4, 7, 10, 13].map((h, i) => (
                  <div key={i} style={{ width: 3, height: h, background: i < 3 ? 'rgba(15,23,42,0.7)' : 'rgba(15,23,42,0.25)', borderRadius: 1 }} />
                ))}
              </div>
              <div style={{ width: 22, height: 11, border: '1.5px solid rgba(15,23,42,0.4)', borderRadius: 3, padding: '1.5px' }}>
                <div style={{ width: '75%', height: '100%', background: '#16a34a', borderRadius: 1.5 }} />
              </div>
            </div>

            {/* App content fills the screen */}
            <div style={{ position: 'absolute', inset: 0 }}>
              {children}
            </div>
          </div>
        </div>

        {/* Top-edge reflection */}
        <div
          style={{
            position: 'absolute',
            top: 12,
            left: 80,
            right: 80,
            height: 1,
            background: 'linear-gradient(90deg, transparent, rgba(255,255,255,0.7), transparent)',
            pointerEvents: 'none',
          }}
        />
      </div>
      </div>
    </div>
  );
}
