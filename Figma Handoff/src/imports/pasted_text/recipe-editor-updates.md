Update the current On2Cook Professional Recipe Editor and Live Cooking UI with these 2 specific layout/usability changes only. Keep the rest of the app exactly as it is unless required to support these updates.

These are refinement changes to improve the iPhone landscape usability of the editor.

IMPORTANT:
Do not redesign the whole app.
Do not change the logic of lid, induction, microwave, stirrer, water, hold, or playhead behavior.
Only optimize the layout behavior for the editor overlays and the preview sidebar.

========================================================
CHANGE 1 — PREVENT EDITOR PANELS FROM COLLIDING WITH THE IPHONE NOTCH
========================================================

Current issue:
When I open the editor of any section, the left side of the editor menu overlaps or collides with the mobile notch area.

This must be fixed.

New requirement:
Any editor panel, modal, drawer, overlay, or expanded editing UI opened in landscape mode must fully respect the iPhone landscape safe areas, especially the notch side.

Rules:
- No editor content should sit underneath the notch
- No buttons, labels, tabs, close icons, segmented controls, or editable fields should collide with the notch
- The editor must feel intentionally designed for a real iPhone in landscape orientation
- Maintain proper safe-area padding on the notch side and on the opposite side as well
- Ensure all editor panels are visually balanced after shifting away from the notch
- Preserve elegance and premium spacing while making the layout safe and usable

Apply this notch-safe optimization to:
1. Induction editor
2. Microwave editor
3. Stirrer editor
4. Water editor
5. Lid / ingredient editor
6. Any contextual editor drawer or modal opened from the timeline
7. Any right-side or center overlay that may currently stretch too far toward the notch

Preferred behavior:
- Reposition the editor content inward from the screen edge
- Respect landscape safe areas
- Add correct left and right padding
- Keep touch targets clear and reachable
- Ensure headers, close buttons, toggles, segmented controls, and content grids remain fully visible

Design intent:
The editor should feel native to an iPhone landscape screen, not like a generic wide overlay pasted onto the screen.

========================================================
CHANGE 2 — MAKE THE PREVIEW / INSPECTOR SIDEBAR LARGER
========================================================

Current issue:
When I click on a full 1-minute block, the preview sidebar that appears is too small.

New requirement:
The preview / inspector sidebar shown after clicking a 1-minute block should be larger.

Target size:
- The preview sidebar should occupy approximately 1/4 of the total screen width
- It should feel clearly readable, useful, and intentional
- It should not feel cramped or too narrow

Apply this to the compact preview / inspector panel that opens when:
- single-clicking a 1-minute induction block
- single-clicking a 1-minute microwave block
- single-clicking a 1-minute stirrer block
- single-clicking a 1-minute water block
- single-clicking a lid-related block
- single-clicking other timeline blocks that open a compact detail preview

Requirements for the enlarged preview sidebar:
- Increase width to around 25% of the whole landscape screen
- Improve readability of all summary information inside it
- Give more breathing room for labels, values, chips, and mini 15-second breakdown previews
- Keep it compact enough that the timeline still remains useful and visible
- Maintain the premium visual style and existing app hierarchy

The larger preview sidebar should be able to comfortably show:
- selected row title and icon
- selected minute label
- 4 x 15-second breakdown
- summary values
- ingredient preview where relevant
- water total where relevant
- stirrer speed preview where relevant
- microwave ON/OFF preview where relevant
- induction values where relevant

========================================================
LANDSCAPE LAYOUT REBALANCING
========================================================

Because of the above 2 changes, rebalance the landscape layout carefully.

Please optimize the landscape screen into clear zones:
- left fixed row-label zone
- center timeline zone
- larger preview / inspector zone on the right
- editor overlays that respect safe areas and avoid notch collision

General layout goals:
- preserve usability on a real iPhone landscape screen
- avoid cramped content
- avoid oversized overlays that feel like desktop panels
- keep the timeline readable
- keep the preview sidebar informative and comfortable
- keep all edit panels safe-area aware

========================================================
EDITOR PANEL BEHAVIOR
========================================================

When opening a larger editor from double-click:
- the editor should respect landscape safe areas
- it should not extend into the notch area
- content should feel centered or intelligently offset
- important controls should remain reachable
- use balanced padding and width constraints
- preserve the premium blue-glass style

You may use:
- a notch-safe floating panel
- a safe-area-aware side sheet
- a centered modal shifted away from the notch
- a constrained-width editing drawer

But the final result must feel polished and native to the iPhone landscape layout.

========================================================
PREVIEW SIDEBAR BEHAVIOR
========================================================

When opening the compact preview from single click:
- make the sidebar visibly larger
- use approximately one-quarter of the total screen width
- keep it aligned cleanly
- make its internal hierarchy stronger
- allow better readability of the 15-second breakdowns and summaries
- do not make it too dominant or overwhelming

The timeline should still remain visible and usable next to it.

========================================================
VISUAL STYLE
========================================================

Keep the current visual direction exactly the same:
- deep blue gradient
- dark glassmorphism
- subtle glow
- premium spacing
- rounded cards
- elegant typography
- high-end smart kitchen feel

Do not change the overall style.
Only optimize the editor usability and preview panel sizing.

========================================================
DO NOT CHANGE
========================================================

- Do not change the main timeline logic
- Do not change the row structure
- Do not change the 1-minute / 15-second editing grammar
- Do not change playhead logic
- Do not change microwave, induction, stirrer, water, lid, or hold rules
- Do not redesign the full app architecture
- Do not revert the current good screen sizing
- Only fix the notch collision and enlarge the preview sidebar

========================================================
OUTCOME REQUIRED
========================================================

Deliver a refined landscape iPhone UI where:
1. All editor menus / expanded editors fully avoid colliding with the mobile notch
2. The preview / inspector sidebar opened on single-click is larger, about 1/4 of the screen width
3. The layout still feels elegant, premium, mobile-first, and practical for real use
4. The rest of the existing On2Cook app logic remains unchanged