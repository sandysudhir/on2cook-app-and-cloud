# On2Cook App and Cloud Change Log

## 2026-06-15

- Disabled automatic recipe uploads when a BLE device connects.
- Changed device connect behavior to send only date/time, status, and firmware checks.
- Changed the former "Sync selected" device action to an inventory-only check.
- Changed cook/order start flow to check device inventory and upload only the single recipe being cooked if it is missing.
- Cleared stale saved upload states on app startup so old "Recipe uploading x/y" messages do not resume visually.
- Bumped web cache version to `20260615c` and deployed updated files to `https://www.on2cook.net`.
- Improved mobile browser layout: compact hero, full-width snapped phone cards, single-page vertical scrolling, fitted five-tab header, and service-worker update handling.
- Bumped web cache version to `20260615d` / service-worker cache `v24`.
- Rebuilt Android test APK as a cloud WebView shell that opens `https://www.on2cook.net/?apk=1`, preserves portrait mobile rendering, supports file upload for serial photos, and routes launcher splash into the cloud UI.
