# On2Cook App and Cloud Change Log

## 2026-06-22

- Simplified the Kitchen Login gate to two choices only: Sign in with Email and Continue as Guest User.
- Removed the visible Role Behaviour/debug section and first-setup/hardware-test cards from the user login screen.
- Changed cloud login role handling so existing cloud profiles decide privileges, while new non-master profiles default to normal operator access.
- Sanitized NoCodeBackend/API JSON parser failures so users see clean cloud-unavailable messages instead of internal `Unexpected token` or `doctype` errors.
- Removed visible development labels from the cloud UI and renamed the settings integration panel to Cloud sync.
- Bumped web cache version to `20260622a` / service-worker cache `v47`.
- Moved manual recipe running out of individual device cards and into the Manual Mode tab.
- Added Manual Mode recipe/device selection with visible Idle, Running, Queue, Syncing, and Offline device states.
- Manual recipe starts now run immediately on idle connected devices and queue behind active work on busy connected devices.
- Bumped web cache version to `20260622b` / service-worker cache `v48`.

## 2026-06-19

- Added host-level Back navigation for the Figma Pro Studio / Pro Timer modal so operators can step out of the landscape timer flow.
- Added Pro Studio route messaging so the cloud host and Android APK can switch the editor to portrait setup screens and landscape timer screens.
- Preserved the Figma Pro Timer layout proportions while scaling the landscape frame to the actual available device viewport.
- Fixed stale saved modals so an old Pro Studio iframe cannot cover the Orders or Recipes screen after a refresh or APK relaunch.
- Improved small-screen responsive layout so the large desktop orchestration hero is hidden and the phone rail uses one actual screen width.
- Added an Orders screen Device Access strip with D1-D5 buttons near the top so device selection is accessible without hunting below the order list.
- Bumped web cache version to `20260619b` / service-worker cache `v46`.
- Built APK locally with JDK 11 and Kotlin in-process compilation: `On2Cook-UI-Orientation-Fix-2026-06-19.apk`.

## 2026-06-17

- Added a login gate for On2Cook Cloud with sign in, first admin setup, and a hardware-test demo bypass.
- Added role-aware permissions for `main_admin`, `kitchen_manager`, `owner`, `operator`, and `cook`.
- Added master-admin people management fields for email, mobile, WhatsApp, role, status, and recipe permissions.
- Restricted Global Recipes, recipe import, recipe selection, and editor access based on the logged-in user's permissions.
- Added NoCodeBackend profile permission fields: `can_add_recipes`, `can_edit_recipes`, and `can_manage_recipe_access`.
- Documented the login, permission, and NoCodeBackend profile contract in `On2Cook Cloud/docs/LOGIN_AND_PERMISSION_MODEL.md`.
- Updated the iPage FTP deploy script to skip the local `data/` recipe ZIP archive during routine app uploads.
- Bumped web cache version to `20260617a` / service-worker cache `v39`.
- Added APK mode for the cloud WebView so `?apk=1` opens directly on the On2Cook Demo Kitchen phone panel without the desktop orchestration hero.
- Added persistent new-order notices that stay at the top until tapped or dismissed; tapping returns to the Orders screen without automatic navigation.
- Added native BLE connected-device snapshot dispatch in the APK bridge so already-connected devices appear connected inside the cloud WebView.
- Added WebView event queuing, renderer recovery, and safer native BLE bridge error handling to reduce abrupt APK crashes.
- Bumped web cache version to `20260617b` / service-worker cache `v40`.
- Built APK: `On2Cook-Cloud-ApkMode-BleSnapshot-2026-06-17.apk`.
- Fixed Android APK device-screen navigation by adding APK-only Home/D1-D5 screen buttons and explicit touch/pointer swipe handling for the cloud rail.
- Changed APK mode layout so the rail owns horizontal movement while each phone body keeps smooth vertical scrolling.
- Added a native WebView touch guard so Android parent views do not steal gestures from the cloud screen.
- Bumped web cache version to `20260617d` / service-worker cache `v42` and deployed to `https://www.on2cook.net`.
- Built APK: `On2Cook-Cloud-Android-Swipe-Fix-2026-06-17.apk`.
- Integrated the supplied Figma recipe app as `pro-studio`, preserving its Select Recipe, Configure Recipe, Pro Timeline Editor, Live Cook, completion, and recipe sheet screens.
- Changed On2Cook Cloud recipe editing to open the Figma Pro Studio flow seeded with the selected cloud recipe instead of the simplified recreated editor.
- Fixed service-worker navigation handling so `/pro-studio/` is cached and served separately from the On2Cook Cloud shell.
- Bumped web cache version to `20260617e` / service-worker cache `v44` and deployed the Pro Studio assets to `https://www.on2cook.net/pro-studio/`.

## 2026-06-16

- Added an on-screen Abort recipe action for active device work; it sends the firmware command `stop=100` and waits for the normal device abort/completion notification to clear the run.
- Added a firmware log browser in Device Details: `LISTLOGS` lists device logs, `LOGFILE=...` entries are shown as selectable files, and `READLOG=<file>` streams the selected log into the screen.
- Guarded active `READLOG` transfers so raw log chunks are not interpreted as telemetry/status commands.
- Bumped web cache version to `20260616g` / service-worker cache `v38`.
- Built APK: `On2Cook-Abort-And-Firmware-Logs-2026-06-16.apk`.
- Added a native Android BLE bridge for the cloud WebView APK, so Connect, commands, and recipe file packets use the existing `BleService` instead of Web Bluetooth inside Android WebView.
- Updated the cloud BLE transport to prefer `window.On2CookNativeBle` in the APK while keeping normal Web Bluetooth for Chrome/Edge.
- Bumped web cache version to `20260616e` / service-worker cache `v36`.
- Built native-BLE test APK: `On2Cook-Native-BLE-Bridge-2026-06-16.apk`.
- Added a home-screen Bluetooth connect-all button on the On2Cook Demo Kitchen panel.
- Added native APK connect-all scanning so the WebView can connect all discovered On2Cook devices into Device 1-5 slots from one tap.
- Added native auto-reconnect scanning after unexpected BLE disconnects.
- Bumped web cache version to `20260616f` / service-worker cache `v37`.
- Built connect-all APK: `On2Cook-Home-Bluetooth-Connect-All-2026-06-16.apk`.
- Fixed device cards so completed or aborted recipes no longer keep rendering as a highlighted live execution timeline.
- Kept finished/aborted recipe detail available through the last-recipe sheet tab while reserving the execution timeline for active cooking only.
- Bumped web cache version to `20260616a` / service-worker cache `v32`.
- Added durable device activity logging: meaningful device events are retained locally after disconnects and sent to NoCodeBackend `cook_logs` as non-blocking cloud log entries.
- Increased retained per-device local activity from 30 to 100 entries and added a saved device log section inside Device Details.
- Changed the Logs button so disconnected devices open saved history instead of trying a BLE firmware log request.
- Bumped web cache version to `20260616b` / service-worker cache `v33`.
- Preserved page, horizontal device rail, and individual phone-panel scroll positions across app re-renders and service-worker refreshes.
- Bumped web cache version to `20260616c` / service-worker cache `v34`.
- Changed completed recipe wait timing so "Since completion" freezes when the next recipe actually starts cooking on firmware.
- Disabled automatic `ingredients=100`; ingredient completion now requires explicit confirmation on the device or via the web screen button.
- Bumped web cache version to `20260616d` / service-worker cache `v35`.
- Built finalized Android debug APK for current native Home + cloud Orders/WebView flow: `On2Cook App/On2Cook-Final-Cloud-Orders-2026-06-16.apk`.

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
