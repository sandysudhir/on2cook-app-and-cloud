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
- Integrated the Figma-style Edit Recipe flow into the cloud app: Global Recipes now behaves as a Select Recipe screen, selected recipes open a visual minute editor, and every minute is split into four 15-second blocks for lid, induction, microwave, stirrer, and water.
- Saving from the new editor creates a Final Modified recipe while preserving the firmware recipe JSON format; Run Recipe can save and send only the selected final recipe to a chosen connected device.
- Bumped web cache version to `20260615e` / service-worker cache `v25`.
- Added the Figma-style Configure Recipe step before the timeline editor, including diet type, recipe type, quantity/unit, consistency, editable ingredients, recipe preview, and an explicit Open Pro Timeline Editor handoff.
- Prevented stale professional editor drafts from reopening after refresh and bumped web cache version to `20260615f` / service-worker cache `v26`.
