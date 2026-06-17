I want you to design and implement a premium mobile app UI/UX for an On2Cook smart cooking system.

Please read this carefully and convert it into a highly polished, production-quality app concept in Figma / Figma Make style. Do not make it look like a finance app, but use the provided style reference for the visual language only: deep blue gradient background, soft glassmorphism cards, elegant rounded corners, premium shadows, smooth modern typography, glowing highlights, refined icons, and rich depth.

IMPORTANT:
- Use my hand sketch as the functional reference.
- Use the visual screenshot only as a style and UI mood reference.
- Keep the app minimal, premium, futuristic, clean, and very intuitive for kitchen use.
- This is for a cooking machine, so all labels, icons, controls, and user flows must feel relevant to food/cooking, not banking.

========================================
1. PRODUCT CONTEXT
========================================

This app is for On2Cook, a smart cooking appliance that combines multiple cooking actions in one process:

1. Microwave
2. Induction heating
3. Stirrer
4. Water dispensing

A user should be able to create a cooking program made of multiple steps.
Each cooking step can control these 4 systems independently or in combination.

The app should help the user:
- create a new cooking sequence
- adjust each factor logically
- save recipes to memory
- autosave while editing
- view all steps in a clean timeline / step-based interface
- start cooking
- monitor cooking live with a beautiful progress visualization
- see what is active, what is completed, and what is coming next

This must feel like a premium smart appliance app, not a generic form screen.

========================================
2. CORE APP IDEA
========================================

A recipe is made up of steps.

Each step can configure:
- Microwave
- Induction
- Stirrer
- Water

The user can create step 1, step 2, step 3, step 4, etc.
Each step has its own cooking settings.
At the end, the user presses “Start Cooking”.

During cooking, there should be a dynamic live cooking screen with a strong visual timer system, ideally using concentric circles / circular progress rings to represent:
- total cooking time
- microwave timing
- induction timing
- stirrer timing / pulses
- water dosing timing

This live screen should clearly show:
- whole recipe countdown
- current step
- which functions are currently running
- which functions are completed
- which function is about to start next
- progress in a very beautiful and intuitive way

========================================
3. FUNCTIONAL LOGIC FOR EACH COOKING MODULE
========================================

Please improve the raw sketch logic into a more realistic cooking UX for On2Cook.

A. MICROWAVE
Controls:
- on / off toggle
- active duration
- power percentage
- optional start delay within step

Recommended fields:
- Microwave: ON/OFF
- Duration: in sec/min
- Power: 10% to 100%
- Start at: immediately or after delay

Display style:
- icon + compact card + slider / dial + toggle

B. INDUCTION
Controls:
- on / off toggle
- active duration
- power percentage
- optional start delay within step

Recommended fields:
- Induction: ON/OFF
- Duration
- Power: 10% to 100%
- Start at: immediately or after delay

C. STIRRER
This needs better logic than just on/off.
The stirrer should allow control over how and when it rotates.

Recommended controls:
- Stirrer: ON/OFF
- Mode:
  1. Continuous
  2. Pulse
- Direction:
  1. Clockwise
  2. Counter-clockwise
  3. Alternate
- Speed / intensity level:
  Low / Medium / High
  or 1–5 levels
- If pulse mode:
  - rotate for X sec
  - pause for Y sec
  - repeat until step ends
- Optional total active duration

This is important because cooking may require:
- gentle mixing
- periodic stirring
- stronger mixing
- alternate-direction scraping

D. WATER DISPENSING
This also needs better cooking logic.

Recommended controls:
- Water: ON/OFF
- Dispense type:
  1. Single shot
  2. Pulse dosing
  3. Continuous for fixed duration
- Dispense trigger timing:
  - at start of step
  - after X sec
  - multiple events inside step
- Quantity control:
  - use ml if possible
  - or dispense duration if machine logic is time-based
- If time-based, show estimated ml equivalence

Recommended UX:
- Water amount
- Timing of water release
- Number of doses
- Gap between doses if pulsed

Example:
- Dispense 30 ml at 00:10
- Dispense 20 ml at 00:40
or
- dispense for 2 sec after 15 sec delay

========================================
4. OTHER IMPORTANT FACTORS TO ADD
========================================

Please add these additional app features intelligently, based on the sketch and what would make sense for a smart cooking product:

- Recipe name field
- Create New recipe
- Autosave status indicator
- Save to Memory button
- Step tabs / step cards at bottom
- Add step button
- Duplicate step
- Delete step
- Reorder steps
- Total recipe time preview
- Estimated cook outcome preview area
- Start Cooking CTA
- Pause / Resume / Stop while cooking
- Manual override mode for each module during cooking
- Current active status indicator for each module
- Device connection status
- Safety lock / lid closed / ready state indicator if needed
- Optional favorites / saved recipes section

========================================
5. SCREEN STRUCTURE I WANT
========================================

Please create a complete app concept with these main screens:

SCREEN 1: HOME / DASHBOARD
Purpose:
- premium landing screen for the appliance
- access saved recipes
- quick start
- recent recipes
- create new recipe
- machine status

Suggested content:
- greeting / device name / connection badge
- “Create New Recipe”
- “Saved Recipes”
- “Recent Cooks”
- “Quick Manual Control”
- maybe small recipe cards
- total premium dark blue style

SCREEN 2: CREATE / EDIT RECIPE
This is the most important screen.

Layout idea:
- Top bar: Back, recipe name, autosave, memory/save icon
- Summary card: total steps, total estimated time
- Step selector: Step 1 / Step 2 / Step 3 / Step 4 as elegant tabs or pills
- Main configuration area:
  - Microwave card
  - Induction card
  - Stirrer card
  - Water card
- Each card should have:
  - icon
  - title
  - toggle
  - key settings
  - compact but premium interaction
- Bottom CTA:
  - Add Step
  - Save Recipe
  - Start Cooking

This should feel very well organized, not cramped.

SCREEN 3: LIVE COOKING / ACTIVE PROCESS
This is the hero screen.

I want a beautiful, futuristic live cooking UI with circular visualization.

Use concentric progress rings:
- Outer ring = total recipe time
- Inner ring 1 = microwave
- Inner ring 2 = induction
- Inner ring 3 = stirrer
- Inner ring 4 = water events

Center area should show:
- current step number
- recipe name
- time remaining
- “Microwave Active”, “Induction Active”, etc.
- next upcoming event

Also show:
- progress by step
- completed vs active vs upcoming
- beautiful animation logic
- pause, resume, stop
- manual control shortcuts
- maybe small timeline underneath

Important:
This screen should instantly communicate what is happening in the machine.

Examples of visual behavior:
- active ring glows
- completed ring segment fades to subtle completed color
- upcoming event shown as markers
- water events shown as pulse dots or droplet markers
- stirrer pulses shown as repeated mini segments

SCREEN 4: RECIPE SAVED / MEMORY LIBRARY
Purpose:
- show stored recipes
- search
- favorite
- edit
- duplicate
- start directly

========================================
6. LIVE COOKING VISUALIZATION DETAILS
========================================

Please refine the timer concept in the best possible way.

I want a sophisticated cooking progress system, not a simple countdown.

Possible visualization logic:
- Outer circle = total recipe countdown
- One circle for microwave active window
- One circle for induction active window
- Stirrer shown as segmented pulse arcs
- Water shown as droplets / event markers on timeline
- Center text shows:
  - “Step 2 of 4”
  - “03:28 remaining”
  - “Induction + Stirrer active”
- Secondary area:
  - completed actions
  - upcoming actions in next 10 sec
  - total elapsed vs remaining

You may improve this if you find a better structure, but keep the core idea of concentric progress rings.

========================================
7. VISUAL STYLE DIRECTION
========================================

Use the reference image only for style inspiration:
- dark navy / royal blue gradient base
- glassmorphism cards
- subtle transparency
- rounded rectangles
- soft ambient glow
- premium, minimal icons
- elegant spacing
- high-end futuristic feel

But adapt the visual language to cooking tech:
- replace finance charts/cards with recipe / process cards
- use food-tech appropriate icons
- use cooking-specific UI labels
- the app should feel like a smart kitchen operating system

Visual tone:
- premium
- modern
- trustworthy
- high-tech
- slightly futuristic
- uncluttered
- easy for kitchen staff and product owners both

Typography:
- clean sans-serif
- strong hierarchy
- bold section titles
- compact but legible parameter text
- excellent readability in dark mode

========================================
8. UX REQUIREMENTS
========================================

The app should be easy enough that:
- a chef can use it
- a kitchen staff member can use it
- a product tester can understand it
- a non-technical user can still create a cooking sequence

Make the interaction model simple:
- sliders where useful
- toggles for ON/OFF
- segmented options for stirrer mode/direction
- time picker for durations
- pill tabs for steps
- expandable cards for advanced settings

Do not make the UI overly technical or crowded.

========================================
9. DESIGN SYSTEM / COMPONENTS
========================================

Please create a consistent component system:
- top nav bars
- step pills
- module cards
- toggles
- sliders
- time selectors
- CTA buttons
- status chips
- live progress rings
- saved recipe cards
- bottom navigation if needed

All components should feel consistent and ready for real app use.

========================================
10. IMPLEMENTATION REQUEST
========================================

Please do the following:

1. Understand the sketch and convert it into a clean UX structure
2. Improve the logic where needed for a real cooking workflow
3. Design the app screens in the premium blue-glass visual style
4. Make the live cooking progress screen outstanding
5. Keep the system practical and believable for On2Cook cooking
6. Create a polished mobile app concept, not just a wireframe
7. If possible, generate actual editable UI screens / components / prototype structure

Deliver:
- full app screen concepts
- component styling
- interaction suggestions
- improved cooking logic
- live cooking visualization system
- clean screen hierarchy

Please make strong design decisions where needed instead of asking too many questions.
Use the sketch as intent, but elevate it into a world-class smart cooking app.