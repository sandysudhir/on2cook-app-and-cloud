# On2Cook App and Cloud Change Log

## 2026-07-14

- Fixed the Order Details contrast regression by isolating its light color scheme from the dark dashboard, using opaque cards and dark text for headings, labels, values, and item details.
- Bumped web cache version to `20260714b` / service-worker cache `v96` and refreshed the APK cloud URL.
- Added permission-controlled `Remove` actions to every device queue surface, including the Queue tab, device card, Device Details, and the native-style Manual Mode queue.
- Added a confirmation step that returns the selected queued order to Pending without deleting the order, completed history, or current cook.
- Held manually removed orders out of automatic routing until an operator explicitly assigns or starts them again.
- Upgraded the Queue tab from a read-only list to the same device-specific drag, up/down, Make Next, Cook Now, and Remove controls used elsewhere.
- Added queue-removal regression coverage; all eight queue-policy tests now pass.
- Bumped web cache version to `20260714a` / service-worker cache `v95` and refreshed the APK cloud URL.

## 2026-07-13

- Unified post-completion queue handling: manual routing now waits at `Ready for next` with an explicit `Cook now` action, while auto-routing keeps automatic handoff.
- Added device-specific queue controls to the main device card, Device Details, and Manual Mode, including hold-and-drag priority changes, up/down fallbacks, Make Next, Cook Now, and explicit Stop Current & Cook Now.
- Added administrator-controlled queue permissions. Main admins always control priority, kitchen managers are enabled by default, and operators can be granted or denied queue changes independently while still being able to start the first idle item.
- Added automated queue-policy tests covering reorder, drag placement, manual/automatic handoff, explicit abort handoff, and role permissions.
- Fixed multi-item handoff so the first queued recipe starts without being requeued behind the remaining jobs.
- Bumped web cache version to `20260713e` / service-worker cache `v94` and refreshed the APK cloud URL.
- Added a prominent, sticky `Abort recipe` action to the active device Manual Mode screen instead of hiding it inside collapsed cooking diagnostics.
- Added an abort confirmation step that identifies the device and recipe, preserves upcoming queue jobs, and only then uses the existing firmware `stop=100` command.
- Verified the Manual Mode abort and cancel flow at desktop and 390 x 844 mobile sizes without horizontal overflow; cancelling returns to the active cooking screen.
- Bumped web cache version to `20260713c` / service-worker cache `v92` and refreshed the APK cloud URL.
- Replaced the idle Manual Mode controls with the original-style live cooking timeline whenever a device is running a recipe.
- Mapped recipe-mode status from the firmware `MODE`, `IND_RUN`, `MAG_RUN`, power, stirrer, and pump fields so active induction/microwave/stirrer states no longer appear idle; inactive sources stay neutral and an active stirrer is green.
- Kept manual intervention controls collapsed during cooking and removed inappropriate Start/Play controls for sources that are already running.
- Unified Orders, Recipe List, Quick Assign, and device Manual Mode around one ordered per-device queue. Manual Mode now keeps its recipe picker visible, shows every upcoming recipe, supports up/down reordering, and reflects the same order on the main device card.
- Queuing no longer checks or uploads recipe files. A queued recipe is checked and uploaded only when it reaches the front and is about to cook.
- Migrated duplicate saved seed-recipe IDs to stable unique IDs, preventing one selected recipe from resolving to another recipe with the same old internal ID.
- Preserved the device Manual Mode scroll position across targeted queue/selection updates.
- Verified two-recipe queue insertion, reorder, shared D1 rendering, unique option IDs, and a 390 x 844 mobile layout without horizontal overflow.
- Bumped web cache version to `20260713b` / service-worker cache `v91` and refreshed the APK cloud URL.
- Stabilized device Manual Mode so induction/magnetron/stirrer changes keep their selected UI value while the device acknowledgement/status refresh catches up.
- Changed manual status refreshes from immediate repeated polling to a coalesced delayed refresh, reducing screen jumps while operators tap controls.
- Fixed Manual Mode Sprinkle/Spray semantics: Sprinkle is fixed at one 10 ml shot, while Spray uses a visible count starting at 1 and sends count x 10 ml.
- Made Manual Mode recipe cards clickable and restricted Recommended recipes to the selected device's cooked history, device inventory, or explicitly allowed recipes.
- Improved Manual Mode contrast and scrollbars so pump/spray values and vertical movement are visible on the native-style screen.
- Bumped web cache version to `20260713a` / service-worker cache `v90` and refreshed the APK cloud URL.

## 2026-07-10

- Rebuilt device Manual Mode from the old native Android manual screen instead of the cloud settings screen.
- Added the old-app manual controls for induction, magnetron, stirrer, Sprinkle/pump, and Spray/purge with the existing firmware command strings unchanged.
- Added missing manual BLE command paths for `INDPROCESSTIME=`, `MAGPOWER=`, `MAGPROCESSTIME=`, `PURGE=ON,`, and `PURGE=OFF`.
- Bumped web cache version to `20260710a` / service-worker cache `v89` and refreshed the APK cloud URL.

## 2026-07-09

- Added a latest-firmware package under `On2Cook Cloud/firmware/latest/` using the current firmware build `IN-V9-260626`.
- Added connect-time firmware gating: after a cooker connects the app checks `Firmware=?`, blocks recipe/manual commands while firmware is checking, required, or updating, and shows a firmware update notice instead of allowing cooking too early.
- Added Android native OTA bridge support using the existing firmware protocol: `OTA:true,SIZE:<bytes>`, `USE_WIFI`, `ON2COOK_OTA` / `12345678`, and HTTP upload to `http://192.168.4.1/update`.
- Added firmware update progress/completion/failure events back into the cloud UI, including a completion popup with the updated version.
- Added slot-repair support so a physical cooker already connected in another slot can be moved back to the intended D1-D5 window instead of forcing a power cycle.
- Browser mode now warns that automatic firmware OTA requires the Android APK because Chrome cannot switch the computer/phone Wi-Fi into the cooker OTA access point by itself.
- Bumped web cache version to `20260709a` / service-worker cache `v88`, added the firmware manifest/binary to the web bundle, and built APK `On2Cook-Cloud-Mobile-APK-2026-07-09-firmware-update.apk`.

## 2026-07-08

- Fixed disconnected/offline device rendering so Live Logs and Status no longer show stale live packets after a cooker is switched off or disconnected.
- Disconnected devices now clear live feed entries, reset live telemetry outputs to idle/off, and show a clear “connect the device” state instead of old recipe/stirrer/power values.
- Bumped web cache version to `20260708i` / service-worker cache `v87` and refreshed the APK cloud URL.

- Removed Manual Mode from the kitchen-level top navigation; the main On2Cook Cloud screen now stays focused on Orders, Recipes, Queue, and Global Recipes.
- Added Manual Mode as a device-specific screen from each device card and Device Details, so manual recipe runs, induction, microwave, stirrer, pump, and status commands are always bound to one cooker slot.
- Bumped web cache version to `20260708h` / service-worker cache `v86` and refreshed the APK cloud URL.

- Reworked Live Logs so `livelog=ON` shows a stable live dashboard, compact recent events, and raw BLE traffic in a diagnostic disclosure instead of an endless stack of large log cards.
- Added parsing for firmware `log=...` sensor packets, recipe packets, and manual quick-start packets so manual mode and recipe mode display differently.
- Updated Device Status to show the last captured live values after disconnects, including recipe/manual/sensor values, rather than only a raw CSV line.
- Hardened Web Bluetooth reconnect by reusing remembered GATT sessions before forcing disconnect, closing stale local sessions before reconnect, and cleaning up local BLE sessions on page hide.
- Added canonical production redirect from `https://on2cook.net` to `https://www.on2cook.net` so Chrome Bluetooth permissions stay on one origin.
- Bumped web cache version to `20260708g` / service-worker cache `v85`, deployed to iPage FTP, and rebuilt the APK wrapper URL.

- Added dedicated Status and Firmware windows for each device so those buttons always open a usable panel, even when no live BLE data is available.
- Made Live Logs controllable from its own screen: Start/Stop/Clear remain clickable, disconnected devices show a clear “Please connect the device” message, and stopping clears stale live-log errors.
- Bumped web cache version to `20260708d` / service-worker cache `v82` and refreshed the APK cloud URL.

- Expanded the device locked-cooker label into its own wrapping header band so longer Bluetooth names are readable instead of being truncated.
- Corrected last-run timing labels: completed/aborted recipes now show `Idle before next` only when a next recipe has started, otherwise they show `Since completion` or `Since abort`.
- Bumped web cache version to `20260708c` / service-worker cache `v81` and refreshed the APK cloud URL.

- Refreshed the Android APK WebView entry so the Orders cloud workspace opens the latest APK-specific mobile surface with native BLE bridge support.
- Preserved the native home screen flow: Cook, Orders, and Fry remain on the app home screen, and the cloud order/device workspace opens only from Orders.
- Prepared a new APK build for phone testing with the current responsive/mobile-optimized On2Cook Cloud UI.
- Tightened phone-width login and APK surface CSS so 390px mobile WebView renders without horizontal overflow.
- Deployed the refreshed web bundle to `on2cook.net` with cache version `20260708b` / service-worker cache `v80`.

## 2026-07-06

- Added four-channel step visibility across device timelines, live cooking summaries, and recipe sheets: Water, Slurry, Induction, and Microwave now appear for each recipe step.
- Mapped Water to `pump_on` and Slurry to `purge_on` while keeping induction/microwave power from the existing firmware recipe JSON.
- Bumped web cache version to `20260706e` / service-worker cache `v57`.

## 2026-07-06

- Fixed device screen ordering so the last completed/aborted recipe stays at the top, live cooking appears in the center, and only genuinely queued recipes appear at the bottom.
- Tightened queue filtering so completed or aborted orders cannot reappear as queued work from stale device queue IDs.
- Bumped web cache version to `20260706d` / service-worker cache `v56`.

## 2026-07-06

- Reworked each device queue into a large-screen prep view: the next recipe now shows starts-in time, wall-clock start time, cook duration, and the first ingredients to prepare from the recipe JSON.
- Removed inventory/serial details from the visible device tile so the operator can see current cooking, upcoming prep, and queue information without scrolling.
- Bumped web cache version to `20260706c` / service-worker cache `v55`.

## 2026-07-06

- Simplified each device cooking screen so live execution shows the current step, next manual ingredient/water check, urgent countdown prompts, abort access, and a compact timeline preview instead of the full telemetry/timeline stack.
- Collapsed live firmware/status details into a small tap-to-open device info tab so operators can focus on what to add next.
- Bumped web cache version to `20260706b` / service-worker cache `v54`.

## 2026-07-06

- Restyled the main On2Cook Cloud control screen to match the supplied mobile dashboard reference with a cleaner header, live device/busy-order counters, icon tabs, device access cards, and stronger order cards.
- Added an explicit Available devices strip under every pending/queued order so connected and recipe-eligible devices can be selected directly per order.
- Added the same connected-device selector under selected recipe cards, so recipes only show devices that are actually connected and enabled for that recipe.
- Kept the top tab navigation visible for web and APK/mobile layouts while preserving existing order/device actions.
- Bumped web cache version to `20260706a` / service-worker cache `v53`.

## 2026-07-03

- Locked saved Web Bluetooth pairings to their assigned Device 1-5 windows so reconnects no longer fall back to a different cooker unless pairing is cleared.
- Changed Connect All on Chrome/Edge to reconnect only already-assigned cooker windows and leave unassigned windows untouched.
- Added a visible locked-cooker label on each device window and explanatory lock status inside Device Details.
- Bumped web cache version to `20260703a` / service-worker cache `v52`.

## 2026-06-25

- Fixed device allowed-recipe toggles so manually unselected recipes stay unselected after refresh, restore, or cloud recipe merge.
- Split device inventory chips from allowed-recipe chips: inventory now shows as read-only green storage status, while orange/grey chips remain the device permission toggles.
- Bumped web cache version to `20260625a` / service-worker cache `v51`.

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
- Added unauthenticated guest KOT order bridge endpoint at `/api/orders/bridge` for temporary POS/API testing.
- Added `scripts/kot_order_bridge_sender.py`, which resets bridge orders, sends three full KOT payloads immediately, then sends four more at one-minute intervals.
- Integrated bridge polling in the web app so active bridge orders replace local demo current/incoming orders.
- Updated FTP deployment to avoid overwriting the server-side bridge runtime order store.
- Bumped web cache version to `20260622c` / service-worker cache `v49`.
- Hardened Windows Web Bluetooth connection handling with per-slot connection locks, stale saved-pairing fallback, one GATT retry, and duplicate physical-device protection.
- Updated the home Bluetooth connect-all flow so each device slot connects independently and partial failures do not reset other slots.
- Bumped web cache version to `20260622d` / service-worker cache `v50`.

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
