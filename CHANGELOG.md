# On2Cook App and Cloud Change Log

## 2026-06-15

- Disabled automatic recipe uploads when a BLE device connects.
- Changed device connect behavior to send only date/time, status, and firmware checks.
- Changed the former "Sync selected" device action to an inventory-only check.
- Changed cook/order start flow to check device inventory and upload only the single recipe being cooked if it is missing.
- Cleared stale saved upload states on app startup so old "Recipe uploading x/y" messages do not resume visually.
- Bumped web cache version to `20260615c` and deployed updated files to `https://www.on2cook.net`.
