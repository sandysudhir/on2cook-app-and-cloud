Update the current On2Cook Professional Recipe Editor and Live Cooking UI with this specific STIRRER VISUAL LOGIC change only. Keep the rest of the app exactly as it is unless required to support this update.

IMPORTANT CHANGE:
Right now, during cooking, the stirrer blocks appear as full bars whenever the stirrer is ON, regardless of actual stirrer speed.

This must be corrected.

NEW STIRRER DISPLAY RULE:
The visual height / intensity of the stirrer bars must reflect the selected stirrer speed.

Use a 5-level speed system:
- Speed 1
- Speed 2
- Speed 3
- Speed 4
- Speed 5

New display behavior:
- Speed 5 = full-height stirrer bar
- Speed 4 = slightly lower bar
- Speed 3 = medium-height bar
- Speed 2 = lower bar
- Speed 1 = smallest bar
- OFF = no active bar / inactive state

This change should make the stirrer row visually communicate not just ON/OFF, but also the intensity / speed level of stirring.

Apply this change consistently across:
1. Landscape Professional Recipe Editor
2. Landscape Live Cooking Screen
3. Stirrer inspector panel
4. Expanded stirrer edit modal / drawer
5. Final recipe summary / export wherever stirrer behavior is summarized visually

STIRRER ROW IN THE MAIN TIMELINE
Redesign the stirrer row so that each active 15-second stirrer sub-block visually represents speed level.

Requirements:
- Keep the same 1-minute block structure
- Keep the same 4 x 15-second sub-block structure
- Each active stirrer sub-block should now display a vertical bar or activity bar whose height corresponds to stirrer speed
- The bar must no longer always appear full height when ON
- OFF blocks must remain visually inactive
- The row should still be easy to scan quickly
- The row should feel premium, clean, and touch-friendly in the landscape iPhone layout

Visual logic:
- Speed 5 = 100% bar height
- Speed 4 = 80% bar height
- Speed 3 = 60% bar height
- Speed 2 = 40% bar height
- Speed 1 = 20% bar height
- OFF = no filled bar

You may refine the exact percentages slightly for visual balance, but the hierarchy must be very clear and instantly readable.

VISUAL STYLE
Keep the stirrer row visually distinct from other rows.

Suggested treatment:
- retain the existing stirrer color language, such as teal / kinetic tone
- use compact vertical activity bars, waveform-like bars, or filled level bars inside each 15-second stirrer cell
- active higher-speed cells should feel more energetic
- lower-speed cells should feel lighter and quieter
- keep it elegant, minimal, and premium
- do not make it look like an audio equalizer or music app
- it should still feel like a cooking control interface

SINGLE CLICK BEHAVIOR FOR STIRRER
Update stirrer single-click behavior.

When the chef single-clicks a 1-minute stirrer block:
- Open the compact inspector
- Show the 4 x 15-second stirrer sub-blocks for that minute
- Clearly indicate the speed level for each sub-block
- Show OFF where stirrer is inactive
- Keep the view compact and easy to read

Examples:
- 5 / 5 / 3 / OFF
- 2 / 2 / 2 / 2
- OFF / 3 / 4 / 5

DOUBLE CLICK BEHAVIOR FOR STIRRER
Update stirrer double-click behavior.

When the chef double-clicks a 1-minute stirrer block:
- Open the detailed stirrer editor
- Keep the existing stirrer logic and editing model
- The chef should still be able to control stirrer parameters such as ON/OFF, continuous / pulse, direction, repeat pattern, and speed
- But the visual representation inside the editor should now clearly show the speed of each 15-second block using bar height or equivalent intensity indication

Inside the expanded stirrer editor:
- Show the 4 available 15-second blocks
- Each block should visibly reflect its selected speed level
- Allow the chef to set speed 1, 2, 3, 4, or 5 for each sub-block
- OFF should also be available
- Keep the interface clean and touch-friendly
- Show the selected speed numerically and visually

LIVE COOKING SCREEN UPDATE
Update the live cooking screen so the stirrer row also reflects actual speed visually.

Requirements:
- In live mode, the current minute’s stirrer row must show the correct bar height for each 15-second block
- The moving playhead should pass through the stirrer row consistently like the others
- Past stirrer blocks should appear completed
- Future stirrer blocks should remain editable if they are to the right of the playhead
- Current active stirrer block should be clearly highlighted
- The user should be able to understand at a glance whether the stirrer is running slowly, moderately, or at full speed

This is the key outcome:
The stirrer row must communicate intensity, not just activity.

FINAL RECIPE / EXPORT UPDATE
Update the final portrait recipe sheet and any summary panels so stirrer behavior is represented more accurately.

Requirements:
- Where stirrer is summarized, include the speed-based logic
- Do not summarize stirrer as only ON/OFF
- Show that stirring intensity varied by segment where relevant
- Keep the summary concise and readable

IMPORTANT CONSISTENCY RULE
The stirrer row must now visually encode speed in the timeline itself.
It should no longer show identical full bars for every active stirrer segment.
The height / fill / intensity of the stirrer blocks must reflect speed level 1 through 5.

DO NOT CHANGE
- Do not change the main visual style
- Do not change the overall app architecture
- Do not change the iPhone landscape layout system
- Do not change the playhead logic
- Do not change lid, induction, microwave, water, or hold logic
- Only update stirrer so its timeline bars represent real speed intensity

Outcome required:
Make the stirrer row feel more intelligent and truthful by showing actual stirrer speed visually inside each 15-second block instead of showing all ON states as identical full bars.