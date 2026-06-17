Update the current On2Cook Professional Recipe Editor and Live Cooking UI with this specific MICROWAVE EDITOR LOGIC change only. Keep the rest of the app exactly as it is unless required to support this update.

IMPORTANT CHANGE:
Microwave should no longer allow power-level selection.

Remove all microwave power options such as:
- 40
- 60
- 80
- 100
or any other percentage / stepped power setting.

NEW MICROWAVE RULE:
Microwave editing must now be simple ON / OFF only at the 15-second sub-block level.

This means:
- Each 1-minute microwave block is still divided into 4 equal sub-blocks
- Each sub-block still represents 15 seconds
- For each 15-second sub-block, the chef can only choose:
  - ON
  - OFF
- No microwave intensity selection
- No microwave percentage selection
- No power slider
- No stepped power ladder
- No numeric power editing

Apply this change consistently across:
1. Landscape Professional Recipe Editor
2. Landscape Live Cooking Screen
3. Microwave inspector panel
4. Expanded microwave edit modal / drawer
5. Final recipe summary / export wherever microwave behavior is shown

MICROWAVE ROW IN THE MAIN TIMELINE
Update the microwave row so it reflects a pure ON / OFF logic only.

Requirements:
- Keep the same 1-minute block structure
- Keep the same 4 x 15-second sub-block structure
- Remove any visual implication of different microwave power levels
- Each 15-second sub-block should simply appear either:
  - active / ON
  - inactive / OFF
- The microwave row should remain visually distinct from induction
- Use the existing microwave color language such as red / magenta / deep warm-red
- Active microwave cells should be clearly visible
- Inactive cells should be muted / unfilled / outlined

The row should feel:
- clean
- premium
- easy to scan
- touch-friendly
- consistent with the rest of the timeline system

SINGLE CLICK BEHAVIOR FOR MICROWAVE
When the chef single-clicks a 1-minute microwave block:
- Open the compact inspector
- Show the 4 x 15-second microwave sub-blocks for that minute
- Clearly indicate which blocks are ON
- Clearly indicate which blocks are OFF
- Do not show any power values
- Do not show any percentages
- Keep the summary simple and concise

Example patterns:
- ON / ON / OFF / OFF
- OFF / ON / ON / ON
- ON / OFF / ON / OFF

DOUBLE CLICK BEHAVIOR FOR MICROWAVE
When the chef double-clicks a 1-minute microwave block:
- Open the detailed microwave editor
- The editor should show the same 4 available 15-second blocks
- The chef can only toggle each block ON or OFF
- Do not provide any microwave power control
- Do not provide numeric values
- Do not provide percent values
- Do not provide stepped options like 40 / 60 / 80 / 100

Inside the microwave editor:
- Show the 4 available 15-second cells clearly
- Each cell should have a strong ON state and OFF state
- The interaction should feel simple, fast, and highly usable
- Keep the UI minimal and premium
- Keep it fully consistent with the other timeline editors

EDITOR UX FOR MICROWAVE
The microwave editor should now be simpler than induction.

Design goals:
- very quick to understand
- very quick to edit
- no extra complexity
- ON / OFF only
- clean touch-friendly toggles for the 4 sub-blocks
- visually distinct from induction so users do not confuse them

LIVE COOKING SCREEN UPDATE
Update the live cooking screen so microwave also follows the new ON / OFF only logic.

Requirements:
- In live mode, the microwave row must show which 15-second microwave blocks are ON
- The moving playhead should pass through the microwave row consistently like the others
- Past microwave blocks should appear completed
- Future microwave blocks should remain editable if they are to the right of the playhead
- Current active microwave block should be clearly highlighted
- There should be no visual suggestion of varying microwave strength
- Microwave should appear as binary ON / OFF only

IMPORTANT LID RULE MUST REMAIN
Keep the existing lid-microwave safety behavior unchanged:
- Whenever lid opens, microwave stops immediately
- During lid-open blocks, microwave row should show forced OFF state

This must remain clearly visible in the timeline and in live playback.

FINAL RECIPE / EXPORT UPDATE
Update the final portrait recipe sheet and any summary panels so microwave is summarized using ON / OFF timing logic only.

Requirements:
- Do not show microwave power percentages
- Do not show microwave intensity values
- Summarize microwave as active / inactive timing by 15-second block or step
- Keep the summary concise and readable

IMPORTANT CONSISTENCY RULE
Microwave must now behave as a binary timeline-controlled system:
- 1-minute block
- 4 sub-blocks
- each sub-block = 15 seconds
- each sub-block = ON or OFF only

Do not allow microwave power selection anywhere in the interface.

DO NOT CHANGE
- Do not change the main visual style
- Do not change the overall app architecture
- Do not change the iPhone landscape layout system
- Do not change the playhead logic
- Do not change lid, induction, water, stirrer, or hold logic
- Only update microwave so it becomes a pure ON / OFF editor at the 15-second block level

Outcome required:
Make microwave editing simple and binary, with only ON / OFF control for each 15-second block and no power selection at all.