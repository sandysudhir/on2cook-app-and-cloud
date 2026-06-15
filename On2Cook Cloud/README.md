# On2Cook Cloud PWA

Standalone browser app for the first On2Cook web testing route:

- Web Bluetooth direct connection for Chrome, Edge, and Chrome Android.
- Up to five On2Cook devices in one session.
- Mobile-phone aspect screens for orders and each connected device.
- Dummy POS/manual orders.
- Ten bundled Android order recipe ZIPs as seed recipes.
- ACK-driven JSON upload and firmware run commands.
- Supabase-ready local data layer and schema.

## Run locally

From this folder:

```powershell
python -m http.server 5177
```

Then open:

```text
http://localhost:5177
```

Web Bluetooth requires HTTPS or localhost. On a deployed cloud URL, use HTTPS.

## Hardware test route

1. Open the app in Chrome or Edge.
2. Use `Connect` on a device screen.
3. Select the On2Cook BLE device.
4. On the order screen, pick `Cook Now` on a pending order.
5. The app uploads the recipe JSON if needed, sends `recipe=<name>`, then sends `ingredients=100` after recipe selection ACK or Android-compatible fallback.

## Supabase

The app works locally without Supabase. To enable Supabase sync, add the project URL and anon key in Settings, then apply `supabase-schema.sql` in the Supabase SQL editor.

## Notes

Chrome Web Bluetooth does not expose the BLE MAC address. The app stores Chrome's origin-scoped `device.id`, the BLE name, and the uploaded serial-number photo.
