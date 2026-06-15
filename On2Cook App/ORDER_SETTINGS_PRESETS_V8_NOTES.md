# On2Cook V8 Static Kotlin Package Notes

This package is based on the last v7 queue/devices/recipes package and adds the Settings/Login/Mode Presets static Kotlin screens inside `HomeFragment.kt`.

## Added screens

- Login screen for Settings access.
- Sectioned Settings screen using the selected Option 2 style.
- Cooking Presets / Mode Presets list.
- Compact multi-step preset screens for Start, Bake, Grill, Steam, Reheat and Fry.
- Step editor screen with variable fields per step:
  - Induction power/time
  - Microwave power/time
  - Power-drop timing and power after drop
  - Stirrer speed
  - Pump duration
  - Spray amount
  - Threshold temperature
  - Wait/rest time
  - Lid status
- Volume screen.
- Version/info screen.
- Manual Control screen with standard knob modes below manual controls and recommended recipes below.

## Preset model

The new static model does not extend the firmware's existing `menu_settings[8]` one-row structure. It introduces a recipe-instruction-style app model:

- `ModePreset`
- `ModePresetStep`

These are currently local/in-memory static objects in `HomeFragment.kt`, so the UI is flexible and ready for backend/local persistence later.

## Existing functionality preserved

- Original Cook / Orders / Fry opening screen remains.
- Existing Cook and Fry click behavior remains.
- Existing Bluetooth / On2Cook / plus firmware-facing toolbar remains untouched.
- `HomeActivity.kt`, `BleService.kt`, and `activity_home.xml` are not modified.

## Validation notes

- `fragment_home.xml` parses successfully.
- `HomeFragment.kt` brace balance checked.
- Full Gradle build was not possible in the sandbox because Gradle wrapper attempts to download Gradle from `services.gradle.org`, and internet access is blocked.
