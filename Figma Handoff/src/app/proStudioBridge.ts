import type { Location } from 'react-router';

type OrientationMode = 'portrait' | 'landscape';

declare global {
  interface Window {
    On2CookNativeBle?: {
      setOrientation?: (mode: OrientationMode) => string;
    };
  }
}

function orientationForPath(pathname: string): OrientationMode {
  return pathname.startsWith('/pro-editor') ? 'landscape' : 'portrait';
}

export function announceProStudioRoute(location: Location) {
  const orientation = orientationForPath(location.pathname);
  const payload = {
    type: 'on2cook-pro-studio-route',
    path: location.pathname,
    hash: window.location.hash,
    orientation,
  };

  try {
    window.parent?.postMessage(payload, window.location.origin);
  } catch {
    // Parent shell is optional when Pro Studio is opened directly.
  }

  try {
    window.On2CookNativeBle?.setOrientation?.(orientation);
  } catch {
    // Android bridge is only present inside the APK WebView.
  }

  if (orientation !== 'landscape') {
    try {
      screen.orientation?.unlock?.();
    } catch {
      // Browser support varies; native APK handles this reliably.
    }
  }
}
