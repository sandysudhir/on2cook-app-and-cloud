Update the current On2Cook Professional Recipe Editor and Live Cooking UI with this specific RECIPE COMPLETION FLOW change only. Keep the rest of the app exactly as it is unless required to support this update.

These changes must be added to the existing flow without disturbing the current timeline editor, playhead logic, landscape iPhone layout, preview sidebar sizing, notch-safe editor behavior, or the existing summary architecture.

========================================================
NEW FEATURE — END RECIPE BUTTON + RECIPE COMPLETED SCREEN + TAP-TO-SUMMARY FLOW
========================================================

We now need a proper recipe ending flow.

Current need:
I want an End Recipe button in the live cooking experience so that the chef can manually finish the recipe when needed, even if the timeline has not naturally reached the very end yet.

The recipe can therefore end in 2 ways:
1. Automatically when the full recipe timeline completes
2. Manually when the chef taps an End Recipe button

Both of these should lead into a new intermediate completion screen before showing the full final summary.

========================================================
PART 1 — ADD END RECIPE BUTTON
========================================================

Add a clearly visible but well-integrated “End Recipe” button into the live cooking screen.

Requirements:
- The End Recipe button should exist in the landscape live cooking mode
- It should be accessible but not visually too dominant
- It should feel like a deliberate action
- It should match the premium design language of the app
- It should sit logically near other live controls such as pause / stop / resume
- It should be easy for the chef to tap while cooking
- It should not clutter the interface

Behavior:
- If the chef taps End Recipe, the recipe should terminate immediately
- The app should finalize the currently recorded cooking data up to that moment
- The app should then transition to a new “Recipe Completed” minimal screen
- This should happen even if the recipe ended before the originally planned total timeline was fully completed

The button should feel like:
- a controlled completion action
- not an error
- not a destructive action
- more like “Finish Cooking” / “End Recipe” in a premium smart cooking system

You may use the label:
- End Recipe

or a slightly more polished equivalent if it fits the interface better, but preserve the intent very clearly.

========================================================
PART 2 — RECIPE COMPLETION TRIGGER LOGIC
========================================================

Update the recipe completion logic so that completion can happen in 2 ways.

A. AUTOMATIC COMPLETION
- If the recipe reaches the end of the timeline naturally, it is considered complete
- At that moment, transition to the new Recipe Completed screen

B. MANUAL COMPLETION
- If the chef taps End Recipe before the recipe has naturally finished, it is also considered complete
- The system should finalize the recipe based on the actual executed state up to that point
- Then transition to the same Recipe Completed screen

Important:
Both completion paths should feel unified and lead to the same completion experience.

========================================================
PART 3 — NEW INTERMEDIATE SCREEN: “RECIPE COMPLETED” MINIMAL SCREEN
========================================================

Before the full summary screen, create a new minimal intermediate completion screen.

This is important.

When the recipe finishes, do NOT jump directly to the full summary.
Instead, first show a very clean, minimal, elegant “Recipe Completed” screen.

This screen should feel calm, premium, and conclusive.

Purpose:
- clearly tell the chef that the recipe has been completed
- show the time to recipe completion
- allow a short pause before entering the full summary
- wait for the chef’s tap to continue

This screen should appear after:
- automatic completion
- manual completion through the End Recipe button

========================================================
PART 4 — CONTENT OF THE RECIPE COMPLETED SCREEN
========================================================

The Recipe Completed screen should be very minimal.

It should show:
- a clear message that the recipe is completed
- the total time to recipe completion
- a live continuing timer that keeps counting after completion until the chef taps the screen
- a subtle instruction that tapping anywhere will continue to the summary

Main message:
- Recipe Completed

Supporting information:
- Time to Recipe Completion
- show the actual recipe completion time clearly

After completion, do not immediately open the summary.
Instead:
- remain on this minimal completion screen
- continue running a post-completion timer
- when the chef taps the screen, then move to the summary screen

This allows the chef to see how much time has passed since the recipe finished, for example if they are taking a few seconds before reviewing the result.

========================================================
PART 5 — POST-COMPLETION TIMER LOGIC
========================================================

This part is critical.

Once the recipe is completed and the Recipe Completed screen appears:
- start or continue a timer that counts how much time has passed since completion
- this timer should remain visible on the Recipe Completed screen
- the timer should keep increasing until the chef taps the screen to proceed

This timer is not the main cooking timer anymore.
It is a post-completion elapsed time indicator.

Purpose:
- it tells the chef how much time has passed after the recipe ended
- useful before viewing the summary
- helpful for real cooking situations where the chef might wait a few moments before interacting

Display idea:
- Completed 00:00 ago
or
- Time since completion: 00:12
or similar clean phrasing

But keep the wording premium, simple, and elegant.

Important distinction:
This screen should show both:
1. the actual recipe completion time
2. the ongoing elapsed time after completion until the chef taps

========================================================
PART 6 — TAP TO CONTINUE TO SUMMARY
========================================================

The Recipe Completed screen should not have to show many buttons.
It should be minimal.

Behavior:
- The chef taps anywhere on the screen
- Then the app transitions to the full summary screen
- This should feel simple and intuitive

Include a subtle prompt such as:
- Tap anywhere to view summary
or
- Tap to continue
or another equally minimal prompt

Keep it visually understated.

========================================================
PART 7 — SUMMARY SCREEN INTEGRATION
========================================================

After the chef taps the Recipe Completed screen, open the existing full recipe summary screen.

Update the summary flow to account for the new completion behavior.

The summary should now reflect:
- whether the recipe ended automatically or manually
- actual executed cooking duration
- total hold time
- miscellaneous activity time
- actual completed recipe data up to the point of ending
- full factor summary for lid, induction, microwave, stirrer, and water

If the recipe was ended manually before the planned end:
- summarize the recipe using the actual executed state only
- do not pretend the unfinished future timeline was completed
- preserve truthfulness of execution

If the recipe completed automatically:
- summarize the fully completed run

This must integrate smoothly with the existing portrait final recipe sheet / export view.

========================================================
PART 8 — LIVE COOKING SCREEN UPDATE
========================================================

Update the live cooking landscape screen to include the new End Recipe action.

Requirements:
- keep all existing live cooking logic the same
- keep the moving playhead
- keep past locked / future editable behavior
- keep current active minute and 15-second block logic
- keep the timeline scrollable
- keep all row behaviors unchanged

Only add:
- End Recipe button
- associated completion flow

Placement:
- near pause / stop / resume controls
- clearly visible
- premium and clean
- not oversized
- not alarming unless appropriate

Do not turn it into a destructive-looking red emergency control unless it fits the existing style very carefully.

========================================================
PART 9 — MINIMAL COMPLETION SCREEN VISUAL STYLE
========================================================

The Recipe Completed screen should feel extremely minimal and premium.

Use the existing design language:
- deep blue gradient
- dark glassmorphism if needed
- subtle glow
- elegant typography
- calm spacing
- premium smart kitchen feel

But this screen should be even more minimal than the live cooking screen.

Visual priorities:
- strong completion message
- clearly readable completion time
- clearly readable post-completion elapsed timer
- subtle tap-to-continue hint

Possible composition:
- centered message block
- large completion title
- medium-sized time-to-completion
- smaller live elapsed-after-completion timer
- subtle bottom hint for tap interaction

The screen should feel resolved, calm, and intentional.

========================================================
PART 10 — RECIPE COMPLETION DATA MODEL BEHAVIOR
========================================================

Update the interface logic so that recipe completion freezes the cooking record appropriately.

When the recipe ends:
- capture the actual final executed timeline
- capture actual total cook time
- capture actual hold time
- capture final state of all factors
- freeze further editing for that session
- transition to Recipe Completed screen
- only after tap proceed to summary

If manual End Recipe is used:
- capture data only up to that exact point
- summary must reflect partial-but-finished execution honestly

========================================================
PART 11 — SCREENS TO UPDATE
========================================================

Update these screens / states:

1. Landscape Live Cooking Screen
- add End Recipe button
- connect End Recipe logic

2. New Intermediate Recipe Completed Screen
- minimal screen
- Recipe Completed message
- time to recipe completion
- ongoing timer until tap
- tap anywhere to continue

3. Portrait Final Recipe Summary / Export View
- integrate the new completion flow
- reflect automatic vs manual completion truthfully
- summarize actual executed data only

========================================================
PART 12 — IMPORTANT UX RULES
========================================================

Keep these behaviors:
- the recipe may end automatically when timeline completes
- the chef may manually end it earlier using End Recipe
- both routes go to the same Recipe Completed screen first
- the Recipe Completed screen waits for the chef’s tap
- until that tap, a completion-elapsed timer keeps counting
- after tap, open the summary

This is the desired flow:
Live Cooking → Recipe Completed screen → Tap anywhere → Full Summary

========================================================
DO NOT CHANGE
========================================================

- Do not change the main visual style
- Do not change the timeline editing grammar
- Do not change the 1-minute / 15-second structure
- Do not change lid, induction, microwave, stirrer, water, or hold logic
- Do not change the current landscape iPhone sizing approach
- Do not change the notch-safe editor improvements
- Do not change the enlarged preview sidebar behavior
- Only add the recipe completion flow and integrate it cleanly

========================================================
OUTCOME REQUIRED
========================================================

Deliver an updated On2Cook app flow where:
1. Live cooking includes an End Recipe button
2. Recipes can end automatically or manually
3. Ending the recipe opens a minimal Recipe Completed screen first
4. That screen shows the recipe completion time
5. That screen keeps a timer running until the chef taps
6. On tap, the app opens the full final summary
7. The summary truthfully reflects the actual executed recipe duration and factors