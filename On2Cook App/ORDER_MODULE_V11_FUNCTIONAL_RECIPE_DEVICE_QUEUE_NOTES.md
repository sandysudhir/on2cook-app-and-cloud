# On2Cook v11 functional recipe/device queue update

This package starts from `On2Cook_individual_streams_v10_realtime_devices_recipe_run.zip` and focuses on making the Orders / Devices / Recipes / More areas operational with connected On2Cook devices.

## Main changes

- Added 10 firmware-readable demo recipe ZIP assets under `app/src/main/assets/order_recipes/`.
- Recipe screens now load the first 10 available recipes from the existing Room database, plus bundled recipe ZIP assets as fallback.
- Recipe tiles can be assigned to connected devices through the Select Device flow.
- Device detail screens now include `Assign Recipe / Add to Queue`.
- Device detail recipe picker can start recipes immediately on idle devices or add recipes to the local per-device queue when a device is busy.
- Queue timeline now uses local running/queued/completed maps instead of only static examples.
- Completion handling now listens for firmware completion / idle messages and displays a popup naming the completed device and recipe.
- Pressing OK on the completion popup sends the next queued recipe to that device.
- Settings remain available under More with dummy login; preset modes can be converted into recipe-style firmware JSON and routed through device selection.
- BLE file upload chunking was fixed so small recipe JSON files under 500 bytes are sent correctly.
- BLE upload retry handlers are now per MAC address to reduce interference between multiple device file transfers.
- Existing top toolbar/Bluetooth/On2Cook/plus layer is untouched.

## Firmware command path used

The app uses the existing firmware recipe/file-transfer path:

```
DELETE=<recipeName>
{"RECIPE":"<recipeName>","SIZE":"<bytes> ","SAVE":"1"}
PNO=<n>,DATA=<chunk>
COMPLETE
recipe=<recipeName>
ingredients=100
STATUS=?
```

## Notes

- Device numbers are assigned from connected Bluetooth devices in connection order: first connected = Device 1, second connected = Device 2, etc.
- Queues are in-memory for this build. Persistence/backend login/database is still the next phase.
- Full Gradle build was not possible in the sandbox because the Gradle wrapper attempts to download Gradle from `services.gradle.org`, and internet access is blocked.

## 2026-06-15 APK order-first flow update

- Reworked the native Android order path so tapping an order card opens an order-focused device workspace instead of leaving Orders and Devices as separate dead-end screens.
- The `renderSelectDevice(...)` screen now acts like an order-to-device workspace with a highlighted device card, left and right arrows, and swipe handling on the device card itself.
- Quick device chips now open the same workspace already focused on the selected device instead of only repainting the order list.
- Pending order primary actions now move into the workspace first, so the operator can stay inside the order flow and choose or review the target device before sending the cook command.
- After a start or queue action from the Orders tab, the app stays in the order workspace rather than dropping the operator back into a generic list.
- Added this running markdown log requirement to the workflow so each change batch can be tracked in the same file.

## Validation notes

- `HomeFragment.kt` was updated for the new order-first APK interaction flow.
- A Gradle compile was attempted locally with Android Studio's bundled JBR, but the project currently fails earlier in kapt with a pre-existing JDK/module compatibility error:
  `IllegalAccessError ... KaptJavaCompiler cannot access com.sun.tools.javac.main.JavaCompiler`
- That compile failure is an environment/build-chain issue, not a syntax error reported against the new order-workspace changes themselves.
