# On2Cook Cloud QA - Tomorrow Morning Runbook

Date prepared: July 9, 2026

## Verified Tonight Without Physical Device

- App starts locally without the startup failure screen.
- Desktop and mobile-width layouts render without page-level horizontal overflow.
- Dashboard shows On2Cook Cloud, the compact top tabs, device access cards, pending orders, and notification badge.
- Offline devices are not selectable for cooking; order cards show `Connect device first`.
- D1 and D2 open the Device Details page in the current visual style.
- Device Details keeps the Current Recipe card and shows recipe name/status, step/remaining placeholders, progress area, and visible Induction, Microwave, Stirrer, and Water tiles.
- `View Queue` scrolls to the editable queue timeline.
- Quick Assign recipe controls are disabled while the selected device is offline.
- Manual Mode now disables induction, microwave, stirrer, pump, recipe sync, and manual recipe run controls while the selected device is offline.
- Live Logs and historical Logs are separate:
  - Live Logs opens the real-time panel and documents `livelog=ON` / `livelog=OFF`.
  - Historical Logs opens the stored-log browser and documents `LOGSTATUS=?`, `LISTLOGS`, and `READLOG=`.
- Recipes on Device opens as a device-specific page and disables Refresh/Add while offline.
- Notifications drawer opens with Order, Device, Cooking, Error, and Logs/Sync categories, timestamps, and action buttons.
- Order Details opens with customer details, order summary, items, and order info.
- Recipes, Queue, Manual, and Global Recipes tabs switch correctly.
- Edit Recipe now opens the native editor instead of the blank embedded Pro Studio shell.
- Recipe editor configure screen shows Diet Type, Recipe Type, Quantity, Consistency, Ingredients, and Open Pro Timeline Editor.
- Pro Timeline Editor shows minute cells, 4 x 15-second blocks, and Lid, Induction, Microwave, Stirrer, and Water rows.
- Pro Timeline supports add/remove minute, copy selected block, Save Final, Save and Run, and Live Cook controls.
- Live Cook opens the ready/ingredients overlay.
- End Recipe during active live cooking asks for confirmation, then shows an aborted result with actual run time and since-abort timer.
- Recipe Sheet opens from the result and shows summary, ingredients, cooking steps, water/slurry/induction/microwave values, photo placeholders, Save to Library, Back to Editor, and Return to Queue.
- Save-to-library code requires a new recipe name and refuses the original name or any existing recipe name.
- Recipe start retry no longer auto-sends `ingredients=100`; it waits for explicit confirmation.
- Closing Live Logs while offline no longer attempts to send `livelog=OFF`.
- The latest firmware manifest is available at `firmware/latest/manifest.json` with version `IN-V9-260626`.
- The web UI blocks recipe/manual commands while firmware is being checked or updated.
- The Android APK build completed successfully: `On2Cook-Cloud-Mobile-APK-2026-07-09-firmware-update.apk`.

## Not Verified Tonight

Physical BLE/device behavior still needs the cooker:

- Browser Bluetooth pairing stability on Windows.
- Native Android BLE/classic BLE behavior.
- Native Android OTA firmware update against the real cooker.
- Whether Android shows a Wi-Fi-network approval prompt while switching to `ON2COOK_OTA`.
- Actual `LISTRECIPES`, `DELETE=<recipeName>`, recipe upload, `recipe=<name>`, `ingredients=100`, `add_confirm=`, and `stop=100` command delivery.
- Live telemetry values from firmware.
- Stored log file listing and reading from firmware.
- Real completion/abort messages from device firmware.

## Tomorrow Morning Device Test Sequence

1. Open Chrome or Edge on Windows and load `https://www.on2cook.net/`.
2. Hard refresh once so `app.js?v=20260710a` and service worker `on2cook-cloud-v89` are active.
3. Turn on only the first cooker and wait for BLE advertising.
4. Open Device Details for D1, click Connect, and select the intended cooker.
5. Confirm D1 shows Connected and locks to that exact Bluetooth name.
6. If the same cooker appears connected under D2/D3, click the D1 repair action (`Use Device X cooker here`) and confirm the cooker moves back to D1.
7. Confirm the firmware notice appears while the app sends `Firmware=?`.
8. In Chrome/Edge browser mode, confirm the app warns that automatic OTA requires the Android APK if the connected firmware is older than `IN-V9-260626`.
9. Install/open `On2Cook-Cloud-Mobile-APK-2026-07-09-firmware-update.apk` for the actual OTA test.
10. Connect D1 in the APK. If the device firmware is older, confirm the app blocks cooking, starts OTA, sends `OTA:true,SIZE=<bytes>`, waits for `USE_WIFI`, switches to `ON2COOK_OTA`, uploads to `http://192.168.4.1/update`, and shows the updated firmware version.
11. If Android asks to allow the temporary `ON2COOK_OTA` Wi-Fi, approve it and keep the phone close to the cooker.
12. After firmware completes, reconnect D1 and click Status and Firmware. Confirm `WORKSTATUS=IDLE` and firmware `IN-V9-260626` appear.
13. Open Recipes on Device and click Refresh from device. Confirm actual recipe names appear from `LISTRECIPES`.
14. Pick one order whose recipe is already on the device. Click Cook Now or assign to D1.
15. Confirm the app does not upload all recipes on connect and does not send `ingredients=100` automatically.
16. Confirm the app sends only `recipe=<firmware recipe name>` for the selected recipe and waits for ingredient confirmation.
17. Confirm ingredients on the device or screen, then verify the device starts cooking.
18. During cooking, verify Current Recipe updates step, remaining time, progress, induction, microwave, stirrer, and water.
19. Open Live Logs. Confirm `livelog=ON` starts streaming values. Close it and confirm `livelog=OFF`.
20. Add two upcoming queue items for D1. Reorder them and verify only D1 queue changes.
21. Use View Queue to confirm cooked history, NOW, and upcoming queue are separated.
22. Abort from the screen. Confirm the app sends `stop=100`, shows aborted status, and D1 becomes ready for the next recipe.
23. Let one recipe complete normally. Confirm last cooked recipe appears at the top with since-completion time.
24. While idle, open bottom Logs. Confirm `LOGSTATUS=?`, then `LISTLOGS`, then `READLOG=<filename>` work.
25. Repeat the same connection lock and recipe run test for D2 with a second cooker if available.
26. Verify D1 actions never affect D2, and D2 actions never affect D1.

## Known Expected States

- If a device is offline, Manual Mode hardware commands are disabled.
- If a device is offline, Recipes on Device Refresh/Add and Device Details Check/Read Recipes are disabled.
- Live Logs can be opened offline for explanation, but Start/Stop stream buttons are disabled.
- Historical Logs can be opened offline for explanation, but actual log listing requires an idle connected device.
- `ingredients=100` exists only as an explicit ingredient-confirmation action, not as an automatic post-recipe command.
- Firmware update states block cooking and manual hardware commands until the device is current or the firmware update is explicitly resolved.
- Automatic OTA firmware upload is expected to work from the Android APK/native bridge, not from desktop Chrome Web Bluetooth.
- New order polling must not force the app back to Orders. If you are on Manual Mode, Recipes, Queue, Global Recipes, or a safe device/detail panel, refresh/re-render should preserve that tab, panel, and scroll position.
