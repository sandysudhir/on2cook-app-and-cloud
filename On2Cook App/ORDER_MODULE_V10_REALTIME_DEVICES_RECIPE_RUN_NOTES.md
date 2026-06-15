# On2Cook v10 - Real-time devices and recipe run bridge

Updated from v9 firmware-bridge package.

## What changed

- Added device-number mapping to real connected Bluetooth devices.
  - Device 1 = first connected BLE device.
  - Device 2 = second connected BLE device.
  - Device 3 = third connected BLE device.
  - Remaining devices show as not connected/offline until paired and connected.
- Added public BLE service helpers:
  - `getConnectedDeviceAddresses()`
  - `getConnectedDeviceName(macAddress)`
- Orders and recipe cards now support firmware run flow:
  - Tap `Cook Now`.
  - Select connected idle device.
  - App sends `recipe=<recipeName>` to that device.
  - App sends `ingredients=100` after selection so the firmware starts from app flow.
- If a matching local recipe JSON exists in the app DB, the app uploads it first over the existing BLE recipe file-transfer path, then runs it.
- If no local recipe JSON is found, the app sends the existing firmware recipe command directly. The recipe must already exist on the device.
- Busy connected devices can receive queued jobs locally.
- Device tab now uses live firmware status messages:
  - `WORKSTATUS=IDLE`
  - `RECIPE=...,MODE=Cooking,...`
  - `FRYQUICKSTART=...`
  - `INDQUICKSTART=... / MAGQUICKSTART=...`
- Device screen now shows connected count, real-time state, MAC tail, current recipe/mode/step, remaining seconds, and queue status.

## Firmware layer status

- Existing BLE UUIDs and firmware commands are preserved.
- No Activity toolbar, BLE service UUID, or firmware service contract was changed.
- Existing firmware recipe, file transfer, status, and ingredient commands are reused.

## Important behaviour

- If only one On2Cook device is connected, only Device 1 is selectable.
- Device 2 and Device 3 remain unavailable/offline until those BLE devices are connected.
- Free devices show before busy devices.
- Busy devices show as red/in-use and can accept local queue items.
- If all connected devices are busy, order cards show View Devices instead of Cook Now.

## Validation

- `HomeFragment.kt` and `BleService.kt` brace balance checked.
- `fragment_home.xml` was not changed in v10.
- Full Gradle build could not be completed in this sandbox because Gradle wrapper attempts to download Gradle from services.gradle.org, and internet access is blocked.
