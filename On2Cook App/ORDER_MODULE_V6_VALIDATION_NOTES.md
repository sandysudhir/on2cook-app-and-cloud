# On2Cook Orders Module V6 - Validation Notes

This package is based on release 26.2 and keeps the original HomeFragment opening mode screen as the default.

## Opening screen
- Original top layer is preserved: Bluetooth / On2Cook / plus button remains in the Activity toolbar.
- HomeFragment opens with three mode cards: Cook, Orders, Fry.
- Cook and Fry keep the original click flow into the existing recipe/manual/fry path.
- Orders opens the new Orders module.

## Orders module
- Current Orders and Previous Orders remain inside the Orders module.
- Each order card represents one independent item/cooking job.
- Same-order items share the same accent color.
- Special instructions are displayed clearly and are not used to modify recipe parameters.
- Quick device chips show only ON devices.
- Full Select Device screen shows all devices, including offline devices.
- Repeat/Re-cook is available from completed/failed items.

## Devices module
- Device tab is functional.
- Tapping any device opens a Device Detail view.
- Device Detail shows live cooking, queue, daily stats, top recipes, month/lifetime counters, identity/firmware, installation/exhaust, accessories, health and service logs.

## Recipes module
- Current waiting orders are shown first.
- Favorite recipe cards are shown below.
- Tapping a recipe opens Recipe Details and Cook / Select Device.
- Existing Recipe Library and Fry Mode remain reachable.

## More module
- More tab is functional and includes a return to the opening Cook / Orders / Fry screen.
- Existing FTP/log/OTA pathways are left in the original app flow.

## Static validation performed
- fragment_home.xml parsed successfully as XML.
- HomeFragment.kt brace balance checked.
- Gradle build could not be executed in this environment because the Gradle wrapper attempts to download Gradle from services.gradle.org and internet access is blocked.
