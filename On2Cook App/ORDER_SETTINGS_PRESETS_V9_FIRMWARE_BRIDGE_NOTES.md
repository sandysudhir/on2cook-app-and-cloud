# On2Cook v9 — Settings Preset Firmware Bridge

This version updates the v8 static Settings/Mode Presets work so the app can talk to the existing firmware using the firmware's current BLE pathways.

## What changed

- Mode presets are no longer only static UI values.
- Each app preset is converted into a hidden firmware-readable recipe JSON file.
- The recipe JSON uses the firmware's existing `Ingredients` and `Instruction` schema.
- The app uploads the generated JSON through the existing BLE file-transfer characteristic.
- After upload, the app can send `recipe=<APP_PRESET_NAME>` through the existing BLE command path.
- For immediate preset runs, the app sends `ingredients=100` after recipe selection so the firmware advances through the ingredient stage and starts cooking.

## Firmware compatibility approach

No low-level BLE UUID, service, toolbar, HomeActivity, or firmware-facing connection layer was changed.

The bridge intentionally reuses these firmware-supported commands/paths:

- Recipe upload header: `{ "RECIPE": "...", "SIZE": "...", "SAVE": "1" }`
- Packet transfer: `PNO=<n>,DATA=<chunk>`
- Transfer completion: `COMPLETE`
- Recipe execution: `recipe=<recipeName>`
- Ingredient advance/start: `ingredients=100`

## Preset conversion rules

Each `ModePresetStep` becomes one or more firmware recipe instructions:

- Induction power → `Induction_power`
- Induction time → `Induction_on_time`
- Microwave power → `Magnetron_power`
- Microwave time → `Magnetron_on_time`
- Stirrer speed → `stirrer_on` code: 0 off, 1 low, 2 medium, 3 high, 4 very high
- Pump duration → `pump_on`
- Threshold temperature → `threshold`
- Wait/rest time → `wait_time`
- Lid status → `lid`

If a step has a Power Drop After value, the app splits that step into two firmware instructions: before-drop and after-drop.

## UI behavior

- `Save Preset to Device` uploads the preset recipe file to the On2Cook device.
- `Run Preset on Device` uploads if needed, then selects the recipe and starts it.
- Preset tiles in Manual Control now run the selected preset on the device.

## Limitations still requiring firmware/team validation

- Full Gradle build was not run here because the Gradle wrapper requires an internet download in this sandbox.
- This uses the existing recipe engine rather than adding a new permanent firmware `MODEPRESET_SAVE` command.
- If the firmware team wants these presets visible as native knob menu items without the recipe engine, a firmware-side persistent preset structure should still be added later.
