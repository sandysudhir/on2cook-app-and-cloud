# On2Cook Orders Module V7 Notes

Visual and interaction update based on the latest order-screen feedback.

## Updated
- Original opening screen remains the default: Cook / Orders / Fry.
- Order item card keeps one card per independent cookable item.
- Quantity display now uses the API/KOT quantity string shown directly as `Qty`, without generated `Portion` text when KOT portion is unavailable.
- Removed duplicate `Recipe: <same item name>` line from order cards and details.
- Quick device chips show only ON devices.
- Free/idle ON devices are sorted first.
- Busy/used devices are shown with red border and light red fill.
- Quick-device strip has left/right arrows for horizontal navigation.
- Pending item primary action becomes `Cook Now`, `Add to Queue`, or `View Devices` depending on device availability/selection.
- Full Select Device screen shows all devices, sorted as free first, then busy, then offline.
- Device detail now includes a queue slider/timeline: cooked history above, NOW anchor, upcoming queue below.
- Recipes screen includes pinnable favorite recipes; tapping the star pins/unpins and pinned recipes remain on top.

## Preserved
- Existing top firmware/BLE toolbar layer is untouched.
- Existing Cook and Fry opening actions are preserved.
- Firmware/BLE files are not modified.
