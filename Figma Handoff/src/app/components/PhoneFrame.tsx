import React from 'react';

interface PhoneFrameProps {
  children: React.ReactNode;
}

export function PhoneFrame({ children }: PhoneFrameProps) {
  return (
    <div
      style={{
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'radial-gradient(ellipse 90% 80% at 50% 30%, #c8d9f0 0%, #dde8f8 40%, #edf2fc 70%, #f3f6ff 100%)',
        padding: '24px 20px',
        fontFamily: "'Space Grotesk', sans-serif",
      }}
    >
      {/* Phone body */}
      <div style={{ position: 'relative', flexShrink: 0 }}>
        {/* Mute / Side toggle button */}
        <div style={{ position: 'absolute', left: -4, top: 102, width: 4, height: 28, borderRadius: '3px 0 0 3px', background: 'linear-gradient(180deg,#b0b0b2,#9a9a9c)', boxShadow: '-2px 0 4px rgba(0,0,0,0.2)' }} />
        {/* Volume Up */}
        <div style={{ position: 'absolute', left: -4, top: 148, width: 4, height: 58, borderRadius: '3px 0 0 3px', background: 'linear-gradient(180deg,#b0b0b2,#9a9a9c)', boxShadow: '-2px 0 4px rgba(0,0,0,0.2)' }} />
        {/* Volume Down */}
        <div style={{ position: 'absolute', left: -4, top: 220, width: 4, height: 58, borderRadius: '3px 0 0 3px', background: 'linear-gradient(180deg,#b0b0b2,#9a9a9c)', boxShadow: '-2px 0 4px rgba(0,0,0,0.2)' }} />
        {/* Power button */}
        <div style={{ position: 'absolute', right: -4, top: 170, width: 4, height: 88, borderRadius: '0 3px 3px 0', background: 'linear-gradient(180deg,#b0b0b2,#9a9a9c)', boxShadow: '2px 0 4px rgba(0,0,0,0.2)' }} />

        {/* Chassis */}
        <div
          style={{
            width: 414,
            height: 868,
            background: 'linear-gradient(160deg,#d8d8da 0%,#c4c4c6 30%,#b8b8ba 100%)',
            borderRadius: 55,
            padding: 12,
            boxShadow:
              '0 0 0 1px rgba(255,255,255,0.6), 0 0 0 2px rgba(0,0,0,0.15), 0 50px 120px rgba(0,0,0,0.25), 0 20px 40px rgba(0,0,0,0.12), inset 0 1px 0 rgba(255,255,255,0.5)',
          }}
        >
          {/* Screen */}
          <div
            style={{
              width: 390,
              height: 844,
              borderRadius: 44,
              overflow: 'hidden',
              background: '#f0f4ff',
              position: 'relative',
              transform: 'translate(0,0)',
            }}
          >
            {/* Dynamic Island */}
            <div
              style={{
                position: 'absolute',
                top: 12,
                left: '50%',
                transform: 'translateX(-50%)',
                width: 126,
                height: 37,
                background: '#000',
                borderRadius: 20,
                zIndex: 200,
                pointerEvents: 'none',
              }}
            />

            {/* Status Bar – overlaid at top */}
            <div
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                height: 56,
                display: 'flex',
                alignItems: 'flex-end',
                justifyContent: 'space-between',
                padding: '0 30px 8px',
                zIndex: 150,
                pointerEvents: 'none',
              }}
            >
              <span style={{ fontSize: 15, color: '#0f172a', fontWeight: 700, letterSpacing: -0.5 }}>9:41</span>
              <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                {/* Cell signal */}
                <div style={{ display: 'flex', gap: 2, alignItems: 'flex-end' }}>
                  {[5, 8, 11, 14].map((h, i) => (
                    <div key={i} style={{ width: 3, height: h, background: i < 3 ? '#0f172a' : 'rgba(15,23,42,0.3)', borderRadius: 1.5 }} />
                  ))}
                </div>
                {/* WiFi */}
                <svg width="17" height="13" viewBox="0 0 17 13" fill="#0f172a" style={{ opacity: 0.85 }}>
                  <circle cx="8.5" cy="11.5" r="1.5" />
                  <path d="M5.2 8.3C6.1 7.3 7.3 6.7 8.5 6.7s2.4.6 3.3 1.6l1.5-1.5C12 5.2 10.3 4.2 8.5 4.2S5 5.2 3.7 6.8z" />
                  <path d="M1.8 5.5C3.4 3.6 5.8 2.4 8.5 2.4s5.1 1.2 6.7 3.1l1.5-1.5C14.8 1.8 11.9.4 8.5.4S2.2 1.8.3 4z" />
                </svg>
                {/* Battery */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <div style={{ width: 26, height: 13, border: '1.5px solid rgba(15,23,42,0.45)', borderRadius: 4, padding: '2px', position: 'relative' }}>
                    <div style={{ width: '78%', height: '100%', background: '#16a34a', borderRadius: 2 }} />
                  </div>
                  <div style={{ width: 2.5, height: 6, background: 'rgba(15,23,42,0.35)', borderRadius: '0 1.5px 1.5px 0' }} />
                </div>
              </div>
            </div>

            {/* App content — padded below status bar */}
            <div style={{ position: 'absolute', inset: 0, paddingTop: 56 }}>
              {children}
            </div>

            {/* Home indicator */}
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
          </div>
        </div>

        {/* Subtle reflection / glare on top edge */}
        <div
          style={{
            position: 'absolute',
            top: 12,
            left: 60,
            right: 60,
            height: 1,
            background: 'linear-gradient(90deg, transparent, rgba(255,255,255,0.7), transparent)',
            pointerEvents: 'none',
          }}
        />
      </div>
    </div>
  );
}