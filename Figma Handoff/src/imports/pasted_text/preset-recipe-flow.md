Update the current On2Cook app flow and UI with this specific PRESET RECIPE SELECTION + PRE-EDITOR SETUP FLOW change only. Keep the rest of the app exactly as it is unless required to support this update.

Do not change the existing Professional Recipe Editor logic.
Do not change the existing live cooking logic.
Do not change the existing microwave, induction, stirrer, water, lid, hold, playhead, summary, or end-recipe flow.
Only add and integrate this new preset-based recipe entry flow and editable preset controls in the recipe editor.

========================================================
NEW FEATURE — PRESET RECIPE SELECTION FLOW BEFORE ENTERING PRO TIMELINE
========================================================

We now need a proper starting flow before the Professional Recipe Editor opens.

Current requirement:
When the user taps the Pro Timeline / Professional Recipe option, the app should not directly open the timeline editor first.

Instead, it should open a PRESET RECIPE SELECTION FLOW.

This flow is based on recipes already prepared and preloaded by Pomodori / product stakeholders.

These recipes are preset recipes already available in the product ecosystem.

The goal:
- let the chef select a preset recipe first
- preload recipe-related factors and defaults
- then allow the chef to edit these preset factors
- then open the main Professional Recipe Editor with the generated timeline
- then keep the existing timeline editing logic exactly as it is

========================================================
PART 1 — PRESET RECIPE LIBRARY SCREEN BEFORE THE EDITOR
========================================================

When the user clicks Pro Timeline / Professional Recipe, show a new intermediate recipe selection screen first.

This screen should display a list / library of PRESET RECIPES that have already been loaded into the system by Pomodori / stakeholders.

This is not the saved-recipe history screen.
This is a dedicated preset recipe selection screen specifically for entering the pro recipe creation workflow.

Purpose:
- choose a base recipe from a library of preloaded stakeholder-approved recipes
- use that selection to preload the editable setup factors
- then generate the main timeline

This preset library screen should feel premium, minimal, and fast to use.

It should show recipe cards or list items with relevant summary information.

Each preset recipe item may show:
- recipe name
- veg / non-veg tag
- recipe type
- thumbnail if available
- short descriptor
- typical quantity / serving reference if useful
- maybe a small consistency or style tag if useful

Design goal:
This screen should feel like selecting a professional base recipe from an intelligent cooking system.

========================================================
PART 2 — FACTORS THAT MUST BE PRESET AND EDITABLE
========================================================

The main preset factors we are now focusing on are:

- Veg / Non-veg
- Item / Dish name
- Recipe type
- Quantity of the ingredients
- Consistency selector

These factors are critical.

When a preset recipe is selected, these values should already be pre-filled based on that recipe.

Example:
If a preset recipe is selected, the next screen should already contain:
- its dish name
- whether it is veg or non-veg
- recipe type such as gravy / dry / semi-dry / sauté / boil
- its default quantity
- its default consistency

These become the editable preset inputs before opening the main timeline editor.

========================================================
PART 3 — PRESET RECIPE CONFIGURATION SCREEN AFTER RECIPE SELECTION
========================================================

After the chef selects one preset recipe from the preset recipe library, open a second setup screen before the main editor.

This second screen is a PRE-EDITOR CONFIGURATION SCREEN.

Purpose:
- show the selected recipe and its presets
- allow editing of the important input factors before generating the timeline
- allow ingredient editing before entering the full professional timeline editor

This screen must include editable controls for:
- Veg / Non-veg
- Item / Dish name
- Recipe type
- Quantity of ingredients
- Consistency selector

Additionally, this screen should also allow the user to review and edit ingredients before entering the main timeline editor.

So this screen should support:
- selected recipe name
- editable ingredient list
- editable ingredient quantities
- editable preset factors
- a clear CTA such as Continue to Timeline Editor / Generate Timeline / Open Pro Editor

This screen should feel like the recipe setup / refinement stage before the actual programming timeline opens.

========================================================
PART 4 — INGREDIENT EDITING IN THE PRE-EDITOR SETUP SCREEN
========================================================

On this pre-editor setup screen, add ingredient editing capability.

This means after a preset recipe is selected:
- the chef can review the recipe’s preloaded ingredients
- the chef can edit ingredient names if needed
- the chef can edit ingredient quantities
- the chef can add ingredients
- the chef can remove ingredients
- the chef can reorder ingredients if relevant

This should happen before entering the main Professional Recipe Editor.

Design goal:
This pre-editor stage should let the chef refine the recipe foundation before the system generates the editable cooking timeline.

Ingredient UI can be:
- chips
- cards
- compact rows
- editable list items

But it must remain premium, clear, and easy to use on a real iPhone screen.

========================================================
PART 5 — TIMELINE GENERATION AFTER PRESET CONFIGURATION
========================================================

After the chef selects the preset recipe and edits the setup factors and ingredients, then the app should open the main Professional Recipe Editor.

At that point:
- the app should generate the base timeline using the selected preset recipe plus the edited factor values
- the app should open the existing landscape Pro Recipe Editor
- the existing timeline logic should remain the same
- the chef should then continue working with the timeline the way we already designed it

Important:
This new flow is only changing the starting point.
It should not replace the current main editor logic.

The professional editor should still show:
- Lid
- Induction
- Microwave
- Stirrer
- Water
- Hold
and all existing behavior already defined.

========================================================
PART 6 — EDITABLE PRESET CONTROLS INSIDE THE MAIN RECIPE EDITOR
========================================================

In addition to the new preset flow before entering the editor, we also want these preset factors to remain editable inside the Professional Recipe Editor itself.

This is very important.

In the top bar of the Professional Recipe Editor, include editable controls for:
- Veg / Non-veg
- Item / Dish name
- Recipe type
- Quantity of ingredients
- Consistency selector

These controls should now be active inputs, not just passive labels.

Meaning:
When the chef changes these values in the top bar during recipe editing, the underlying timeline should update accordingly.

This should feel like controlled recipe regeneration / timeline adjustment, not a full reset unless needed.

========================================================
PART 7 — TOP BAR PRESET CONTROL LOGIC IN THE MAIN EDITOR
========================================================

Inside the Professional Recipe Editor, the top bar should now do more than display information.

It should let the chef actively adjust the base recipe factors while editing the recipe.

When the chef edits:
- Veg / Non-veg
- Item / Dish name
- Recipe type
- Quantity of ingredients
- Consistency

the system should update the baseline / generated timeline accordingly.

Important behavior:
- keep the existing editable timeline logic
- keep manual chef edits possible
- preserve the idea that the system provides a base timeline first
- allow the chef to continue editing blocks manually after the regeneration / update

Design goal:
The chef should feel that the system is giving an intelligent default timeline that reacts to these high-level recipe parameters, while still allowing detailed manual control afterward.

========================================================
PART 8 — HOW TIMELINE UPDATES SHOULD BE REPRESENTED
========================================================

When the chef changes one of the preset factors in the editor top bar, the timeline should adjust accordingly.

Examples of how the timeline may respond:
- changing quantity may lengthen or compress parts of the timeline
- changing consistency may alter water blocks, heating pattern, or hold expectations
- changing recipe type may alter induction, microwave, stirrer, or water structure
- changing veg / non-veg may alter base heating profile
- changing item / dish name may switch the underlying recipe logic if tied to a known preset

Important:
Do not make this feel chaotic.
The update should feel intelligent, controlled, and clear.

A good UX behavior:
- show that the system is updating the generated baseline
- then allow the chef to continue refining manually
- preserve the existing concept where the app gives a painting / canvas to the chef, and the chef edits it afterward

If helpful, visually distinguish:
- system-generated structure
- chef-edited structure

Keep the rest of the editor logic unchanged.

========================================================
PART 9 — FLOW OF THE NEW EXPERIENCE
========================================================

The updated flow should now be:

Home / Dashboard
→ Tap Pro Timeline / Professional Recipe
→ Open Preset Recipe Library
→ Select one preset recipe
→ Open Pre-Editor Configuration Screen
→ Edit preset factors and ingredients
→ Continue to Professional Recipe Editor
→ See generated timeline
→ Further edit timeline manually using the existing logic
→ Optionally still edit high-level preset factors from top bar and let timeline update accordingly
→ Continue cooking flow as already designed

This full flow should feel polished, logical, and easy to understand.

========================================================
PART 10 — SCREENS TO ADD OR UPDATE
========================================================

Add / update these screens:

1. PRESET RECIPE LIBRARY SCREEN
A screen that opens when Pro Timeline is tapped.
It shows stakeholder-provided preset recipes.

2. PRE-EDITOR CONFIGURATION SCREEN
A screen after recipe selection where the chef can edit:
- Veg / Non-veg
- Item / Dish name
- Recipe type
- Quantity of ingredients
- Consistency
- ingredient list and quantities

3. PROFESSIONAL RECIPE EDITOR TOP BAR
Update the existing top bar so these same factors remain editable there and can affect the timeline generation / baseline structure.

Do not remove any of the existing major editor capabilities unless needed for this new integration.

========================================================
PART 11 — PRESET RECIPE LIBRARY UX DETAILS
========================================================

The preset recipe library screen should feel practical and premium.

Requirements:
- easy scanning of recipes
- clear recipe cards or rows
- fast selection
- suitable for iPhone portrait or whichever current mode you use before the landscape editor
- show recipe tags clearly
- preserve premium blue-glass style
- do not make it feel like a generic file picker

Useful elements:
- search bar if helpful
- filter chips if useful
- recipe cards or compact list
- veg / non-veg tag
- recipe type tag
- quick summary

This screen should feel like a curated professional recipe starter library.

========================================================
PART 12 — PRE-EDITOR CONFIGURATION UX DETAILS
========================================================

This screen should be concise and practical.

It should let the chef quickly:
- confirm the selected recipe
- adjust preset values
- adjust ingredients
- then continue

The layout should remain realistic for actual phone screen usage.
Do not make it overly large or conceptual.

Possible composition:
- selected recipe header
- editable fields / selectors for preset factors
- ingredient editor section
- bottom CTA to continue into Professional Recipe Editor

The screen should feel like a smart setup form, not a boring spreadsheet.

========================================================
PART 13 — MAIN EDITOR TOP BAR UX UPDATE
========================================================

Update the top bar in the Professional Recipe Editor so these preset inputs are editable but still compact.

They should fit realistically within the landscape iPhone interface.

Use compact controls such as:
- chips
- dropdown selectors
- segmented controls
- compact editable fields
- inline selectors
- bottom sheets if required

The top bar must remain readable and not overcrowded.
If needed, use progressive disclosure or compact edit entry points.

But the user must still be able to adjust:
- Veg / Non-veg
- Item / Dish name
- Recipe type
- Quantity
- Consistency

from inside the editor.

========================================================
PART 14 — LOGIC INTEGRATION RULE
========================================================

The core idea must remain:

The system first provides a pre-generated base recipe timeline.
Then the chef edits it.

This should happen both:
- after preset recipe selection and setup
- after top-bar factor changes in the editor

So the app should always feel like:
“Here is the intelligently generated baseline timeline based on your selected recipe and factors.”
Then:
“You can now refine it manually.”

Do not remove the manual chef-control philosophy.

========================================================
PART 15 — VISUAL STYLE
========================================================

Keep the same visual style:
- deep blue gradient
- dark glassmorphism
- premium spacing
- subtle glows
- rounded cards
- elegant typography
- high-end smart kitchen feel

All new screens and controls must match the current app.

========================================================
DO NOT CHANGE
========================================================

- Do not change the main timeline editing grammar
- Do not change the 1-minute / 15-second structure
- Do not change live cooking logic
- Do not change end recipe / completion / summary flow
- Do not change microwave, induction, stirrer, water, lid, or hold behavior
- Do not change the notch-safe editor fixes
- Do not change the enlarged preview sidebar behavior
- Only add the preset recipe selection flow, pre-editor setup screen, ingredient edit step, and editable top-bar factor controls tied to timeline updates

========================================================
OUTCOME REQUIRED
========================================================

Deliver an updated On2Cook app flow where:
1. Pro Timeline first opens a preset recipe library of stakeholder-provided recipes
2. Selecting a recipe opens a setup screen with editable preset factors and ingredients
3. After this, the app opens the existing Professional Recipe Editor with a generated base timeline
4. Inside the main editor, the same preset factors remain editable in the top bar
5. Changing those factors can intelligently update the generated timeline
6. The chef still keeps full manual editing control over the timeline afterward