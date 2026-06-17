Update the current On2Cook Professional Recipe Editor and Live Cooking UI with this specific WATER LOGIC change only. Keep the rest of the app exactly as it is unless required to support this update.

WATER LOGIC MUST NOW FOLLOW THE SAME STRUCTURAL TIMELINE RULE AS THE OTHER COOKING FACTORS.

Important change:
Previously water may have been treated as a second-based input.
Now water must behave exactly like the other timeline-controlled systems.

New water structure:
- Each 1-minute water block is divided into 4 equal sub-blocks
- Each sub-block represents 15 seconds
- Each 15-second water sub-block equals 150 ml water
- Water can only be turned ON or OFF at the 15-second sub-block level
- No free second-by-second editing in the main workflow
- No arbitrary custom duration inside a minute
- The chef can only select or deselect from the 4 available 15-second blocks

This means:
- 0 selected blocks = 0 ml
- 1 selected block = 150 ml
- 2 selected blocks = 300 ml
- 3 selected blocks = 450 ml
- 4 selected blocks = 600 ml

Apply this change consistently across all relevant screens and states:
1. Landscape Professional Recipe Editor
2. Landscape Live Cooking Screen
3. Water inspector panel
4. Expanded water edit modal / drawer
5. Final recipe summary / export where water is shown

WATER ROW IN THE MAIN TIMELINE
Redesign the water row so it visually behaves like the induction and microwave rows in terms of block structure.

Requirements:
- Every minute in the water row must visibly show 4 equal 15-second sub-blocks
- Active water sub-blocks should appear clearly selected
- Inactive water sub-blocks should remain clearly unselected
- Use the existing water color language: cyan / blue
- The water row should still feel visually distinct from induction and microwave, but structurally it should now match them
- Active water blocks should be easy to scan quickly in the timeline
- The row should feel clean, premium, and touch-friendly on the landscape iPhone layout

Suggested visual treatment:
- Use filled cyan blocks, glowing cells, or droplet-accented active states
- Show inactive blocks as subtle outlined or muted cells
- Make active vs inactive contrast very obvious
- Preserve the premium dark blue glassmorphism visual language of the rest of the app

SINGLE CLICK BEHAVIOR FOR WATER
Update water single-click behavior.

When the chef single-clicks a 1-minute water block:
- Open the compact inspector
- Show the 4 x 15-second water sub-blocks for that minute
- Clearly indicate which of the 4 sub-blocks are ON
- Clearly indicate which are OFF
- Show total water for that minute
- Show total water in ml based on the number of active blocks
- Keep this view compact and easy to understand

The compact inspector should show examples like:
- 1/4 active = 150 ml
- 2/4 active = 300 ml
- 3/4 active = 450 ml
- 4/4 active = 600 ml

DOUBLE CLICK BEHAVIOR FOR WATER
Update water double-click behavior.

When the chef double-clicks a 1-minute water block:
- Open a detailed water editor
- The editor should show the same 4 available 15-second blocks
- The chef can only toggle each of the 4 blocks ON or OFF
- Do not provide free timing input
- Do not provide second-by-second manual sliders
- Do not provide arbitrary ml input for now
- Keep the editing rule strict and consistent with the 4-block timeline grammar

Inside the expanded water editor:
- Label each sub-block clearly as 15 sec / 150 ml
- Make the 4 blocks large and touch-friendly
- Show selected and unselected states clearly
- Show the dynamic total selected water for that minute
- Show this total updating live as the chef selects or deselects blocks

Example totals to display dynamically:
- 0 blocks selected = 0 ml
- 1 block selected = 150 ml
- 2 blocks selected = 300 ml
- 3 blocks selected = 450 ml
- 4 blocks selected = 600 ml

EDITOR UX FOR WATER
The water editor should feel consistent with the other editors but simpler.

Design goals:
- same editing grammar as induction and microwave
- clean 4-cell structure
- highly readable
- touch-friendly
- minimal but premium
- no extra complexity

Include:
- water icon
- minute label
- 4 block toggles
- ml total summary
- cancel / apply or direct live update depending on your existing app logic

LIVE COOKING SCREEN UPDATE
Update the live cooking screen so that water is also shown in the same 4-sub-block structure.

Requirements:
- In live mode, the current minute’s water row must show which 15-second water blocks are active
- The moving playhead should pass through the water row consistently like the others
- Past water blocks should appear completed
- Future water blocks should remain editable if they are to the right of the playhead
- Current active water block should be clearly highlighted
- Keep everything readable in the iPhone landscape layout

FINAL RECIPE / EXPORT UPDATE
Update the final portrait recipe sheet and any summary panels so water is summarized using this new logic.

Requirements:
- Water should be reported in 15-second block units and ml totals
- For each minute or step, the summary should reflect selected water blocks
- Display the resulting total water clearly and concisely
- The logic should match the edited water timeline exactly

IMPORTANT CONSISTENCY RULE
Water must now behave like the other timeline systems.
It should no longer be treated as a free second-based event editor in the primary workflow.
It must follow the same 1-minute block → 4 sub-block structure as induction, microwave, and the rest of the timeline logic.

DO NOT CHANGE
- Do not change the main visual style
- Do not change the overall app architecture
- Do not change the landscape iPhone layout style
- Do not change the playhead logic
- Do not change lid, induction, microwave, stirrer, or hold logic
- Only update water so it becomes structurally aligned with the same 4 x 15-second editing system

Outcome required:
Make the water row feel fully native to the same timeline system as the other factors, while still looking visually unique as the water layer.