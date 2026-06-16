# On2Cook App and Cloud Change Log

## 2026-06-16

- Fixed device cards so completed or aborted recipes no longer keep rendering as a highlighted live execution timeline.
- Kept finished/aborted recipe detail available through the last-recipe sheet tab while reserving the execution timeline for active cooking only.
- Bumped web cache version to `20260616a` / service-worker cache `v32`.

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
- Expanded the Pro Timeline editor toward the Figma landscape workflow: 5% induction control per 15-second block, microwave on/off blocks, four stirrer modes, per-block water quantity, Edit button back to main ingredients, and Live Cook simulation with ready/ingredients prompts, 180-second ingredient hold, pause/resume, moving playhead, completion, and abort states.
- Bumped web cache version to `20260615g` / service-worker cache `v27`.
- Added End Recipe confirmation in Live Cook, requiring an explicit Proceed action before aborting early; any other action cancels the pending abort.
- Added completed/aborted Live Cook result screen with actual run time, planned time, and time since completion/abort, plus the same last-run metrics on each device card.
- Bumped web cache version to `20260615h` / service-worker cache `v28`.
- Added a Recipe Sheet modal for Live Cook results and device last-run history, showing outcome, actual/planned time, time since finish, ingredients, profile data, and cooking steps.
- Added compact last-recipe and active-run tabs on device cards so operators can open the latest recipe sheet/details directly above the queue.
- Bumped web cache version to `20260615i` / service-worker cache `v29`.
- Added post-cook library saving from the Recipe Sheet with required new recipe name validation, separate final recipe creation, automatic recipe/device availability updates, and cloud sync attempt.
- Added finished-dish photo capture/upload placeholders and Back to Editor / Return Home finish-session actions.
- Bumped web cache version to `20260615j` / service-worker cache `v30`.
- Made the completed/aborted Live Cook result screen dismissible by tapping anywhere outside its action button, returning operators to the Queue screen.
- Changed post-cook Recipe Sheet Back/Return actions and Save to Library to return to Queue, and removed the disruptive automatic switch to the Recipes tab after saving.
- Ensured viewing or saving a post-cook sheet nudges queue scheduling so photo/save work does not block the next queued recipe.
- Bumped web cache version to `20260615k` / service-worker cache `v31`.
- Restored the Android APK startup path to the native Home screen instead of auto-opening the cloud WebView.
- Kept the existing Home choices visible and changed the Home `Orders` tile to open the On2Cook Cloud WebView only when tapped.
