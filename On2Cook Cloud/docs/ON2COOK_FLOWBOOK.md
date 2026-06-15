# On2Cook Web App + BLE Flowbook

This document is the single reference for how the On2Cook web app, Android reference app, and firmware are supposed to exchange data.

It has two jobs:

1. Define the rules for every send/retrieve step.
2. Review the current run/disconnect problem against those rules.

## Source files reviewed

Web app:

- `C:\Users\baps\Downloads\on2cook-web-pwa\src\ble-transport.js`
- `C:\Users\baps\Downloads\on2cook-web-pwa\src\app.js`
- `C:\Users\baps\Downloads\on2cook-web-pwa\src\data-store.js`

Android reference:

- `C:\Users\baps\Downloads\on2cook-application\android-app\app\src\main\java\com\invent\ontocook\multiple_connection\ui\HomeFragment.kt`
- `C:\Users\baps\Downloads\on2cook-application\android-app\app\src\main\java\com\invent\ontocook\services\BLEBoundService.kt`
- `C:\Users\baps\Downloads\on2cook-application\android-app\app\src\main\java\com\invent\ontocook\multiple_connection\service\BleService.kt`

Firmware:

- `C:\Users\baps\Downloads\On2cook_Firmware\On2cook_Firmware\On2Cook\src\BLE_Controller.cpp`
- `C:\Users\baps\Downloads\On2cook_Firmware\On2cook_Firmware\On2Cook\src\ON2COOK_DISPLAY_ENC.cpp`

## 1. System layers

There are four different layers in the current system:

1. Cloud/app data layer
   - Recipes come from cloud storage or a recipe finder source.
   - Orders come from POS or manual entry.
   - Device metadata, allowed recipes, queue settings, logs metadata, and user settings live in the app/backend layer.

2. Web or Android client
   - Renders orders, recipes, devices, settings, and manual mode.
   - Translates user actions into BLE commands.
   - Tracks queue state and device UI state.

3. BLE transport
   - A command characteristic is used for control/status/manual commands.
   - A file characteristic is used for recipe JSON transfer and log file transfer handshakes.

4. Firmware/device runtime
   - Stores recipes locally in `/recipe/<name>.txt`.
   - Executes ingredient mode and cooking mode.
   - Emits status/telemetry/completion signals back to the client.

## 2. BLE characteristics

The web app uses:

- Service UUID: `ab0828b1-198e-4351-b779-901fa0e0371e`
- Command characteristic: `4ac8a682-9736-4e5d-932b-e9b31405049c`
- File characteristic: `4ac8c682-9736-4e5d-932b-e9b31405049c`

Protocol split:

- Command characteristic:
  - `STATUS=?`
  - `Firmware=?`
  - `recipe=<name>`
  - `ingredients=100`
  - `add_confirm=<step>`
  - `INDQUICKSTART=...`
  - `INDPOWER=...`
  - `PUMP=...`
  - `LISTRECIPES`
  - `LISTLOGS`
  - `READLOG=...`
  - `DELETE=...`

- File characteristic:
  - recipe header JSON
  - `PNO=<n>,DATA=...`
  - `COMPLETE`

## 3. Data ownership rules

These rules matter because several bugs came from mixing ownership boundaries.

### 3.1 Device recipe storage

- The device is the source of truth for recipes physically stored on the machine.
- Stored recipe path is firmware-side, usually `/recipe/<name>.txt`.
- The app may cache known recipe names, but that cache is only a hint.
- The app must not assume a cached recipe still exists after device reset or reflash unless firmware inventory confirms it.

### 3.2 Active cooking state

- The device is the source of truth for actual execution.
- The app may show `starting`, `ingredient`, `cooking`, `paused`, `awaiting_confirmation`, or `idle`.
- The app must not mark a recipe as truly cooking until the device has moved beyond plain selection acknowledgement.

### 3.3 Queue ownership

- Orders and assignments live in the app.
- The app decides which device should receive which order.
- The device only knows what was sent to it.

### 3.4 Stop/completion ownership

- For recipe flow, `stop=100` is device-driven.
- The app must not send `stop=100` during the normal recipe flow.
- Completion is signaled by the device via `INSTR_RUN=COMPLETE`, `RECIPE=COMPLETE`, or other mode-specific completion messages.

## 4. App-side state model

The current web app keeps these important state buckets per device:

- connection: `disconnected`, `connecting`, `connected`
- current job:
  - `currentJobId`
  - `queueOrderIds`
  - `completionConfirmationPending`
- known recipes:
  - `allowedRecipeIds`
  - `availableRecipeNames`
  - `syncedRecipeNames`
  - `syncedRecipeSignatures`
- transport/runtime:
  - `startupGuardUntil`
  - `telemetry.workStatus`
  - `telemetry.currentRecipe`
  - `telemetry.stepNo`
  - `telemetry.magTime`
  - `telemetry.indTime`
  - `telemetry.indPower`
  - `telemetry.magPower`
  - `telemetry.mode`
  - `telemetry.status`
  - `telemetry.pumpOn`

Important rule:

- `startupGuardUntil` exists because the firmware can still emit `WORKSTATUS=IDLE` during the early selection/start window.
- That guard prevents the UI from dropping a just-started job too early.

## 5. Canonical BLE command/response rules

This is the practical contract the app must follow.

| Phase | App sends | Expected device reply | App next action | Must not do |
|---|---|---|---|---|
| Connect | GATT connect + start notifications | Notifications become active | Ask for status, then firmware version | Assume device is idle before status is checked |
| Status | `STATUS=?` | `WORKSTATUS=IDLE` or a recipe/manual telemetry line | Update UI state | Treat every `IDLE` during startup as final |
| Firmware | `Firmware=?` | `Firmware=<version>` | Store firmware version | None |
| List recipes | `LISTRECIPES` | `ACK`, then recipe list data, then `LISTRECIPES=COMPLETE` | Build device recipe inventory | Blindly trust empty results if firmware does not actually stream names |
| Delete recipe | `DELETE=<name>` | firmware-specific ack on file channel | Only used when explicit overwrite is intended | Delete before every run |
| Upload header | `{"RECIPE":"<name>","SIZE":"<bytes> ","SAVE":"1"}` on file channel | `ACK` | Start packet loop | Start sending data before header ack |
| Upload packet | `PNO=<n>,DATA=...` | `ACK` | Send next packet | Send packets in parallel |
| Upload complete | `COMPLETE` on file channel | `ACK` | Mark transfer complete | Send `recipe=<name>` before transfer finished |
| Recipe select | `recipe=<name>` | `ACK_command` | Wait for ingredient or cooking telemetry; request status after a short delay | Treat `ACK_command` as proof that cooking has started |
| Ingredient advance | `ingredients=100` | `ACK_command` | Wait for cooking telemetry | Send it repeatedly or too early in a loop |
| Step complete | device sends `INSTR_RUN=COMPLETE` | `INSTR_RUN=COMPLETE` | Show step finished; then send `add_confirm=<step>` when user/app wants next instruction | Mark full recipe complete here |
| Instruction ack | `add_confirm=<step>` or `add_confirm=<step>,magTime=<x>,indTime=<y>` | `ACK_command` or timer echo | Continue to next instruction | Skip step number tracking |
| Recipe complete | device sends `RECIPE=COMPLETE` | `RECIPE=COMPLETE` | Move order to completion confirmation state | Auto-clear without user confirmation if workflow requires confirmation |
| Manual induction | `INDQUICKSTART=START/STOP/PAUSE/RESUME` | quickstart status telemetry | Refresh status and update manual UI | Mix manual mode with a live recipe run |
| Manual power | `INDPOWER=<delta>` | `INDPOWER=<value>` or status telemetry | Update manual controls | Allow power change before induction is active |
| Pump | `PUMP=ON,<ticks>` or `PUMP=OFF` | echo or status telemetry | Update pump UI | Assume `<ticks>` is ml; firmware treats it as 10 ml units |
| List logs | `LISTLOGS` | `ACK`, then `LOGFILE=...`, then `LISTLOGS=COMPLETE` | Show file list | Start `READLOG` while one is already running |
| Read log | `READLOG=<file>` | `ACK` or `READLOG=BUSY/DEVICE_BUSY`, then stream data | Save log text | Ignore `DEVICE_BUSY` while a recipe is running |

## 5.1 Canonical command dictionary

These names come directly from the firmware parser and emitters. They should be treated as the exact contract.

### Commands the app sends on the command characteristic

- `STATUS=?`
- `Firmware=?`
- `DATETIME=<epochSeconds>`
- `recipe=<recipeName>`
- `ingredients=<value>`
- `add_confirm=<stepNo>`
- `add_confirm=<stepNo>,magTime=<delta>,indTime=<delta>`
- `stop=<value>`
- `INDQUICKSTART=START`
- `INDQUICKSTART=STOP`
- `INDQUICKSTART=PAUSE`
- `INDQUICKSTART=RESUME`
- `INDPOWER=<delta>`
- `PUMP=ON,<ticks>`
- `PUMP=OFF`
- `LISTRECIPES`
- `LISTLOGS`
- `READLOG=<fileName>`
- `DELETE=<recipeName>`
- `DELETE=LOGS`

### Messages the firmware sends back on the command characteristic

- `ACK_command`
- `WORKSTATUS=IDLE`
- `RECIPE=<recipeName>,MODE=Receipe_Sel`
- `RECIPE=<recipeName>,MODE=Ingredient,STEPNO=<n>`
- `RECIPE=<recipeName>,MODE=Cooking,STEPNO=<n>,IND_RUN=<secs>,MAG_RUN=<secs>,STATUS=START,...`
- `RECIPE=<recipeName>,MODE=Cooking,STEPNO=<n>,IND_RUN=<secs>,MAG_RUN=<secs>,STATUS=PAUSE,...`
- `RECIPE=NONE`
- `RECIPE=COMPLETE`
- `INSTR_RUN=START`
- `INSTR_RUN=COMPLETE`
- `stop=100`
- `INDPOWER=<value>`
- `Firmware=<version>`
- `LISTRECIPES=COMPLETE`
- `LISTRECIPES=ERROR`
- `LISTLOGS=COMPLETE`
- `LISTLOGS=ERROR`
- `READLOG=BUSY`
- `READLOG=DEVICE_BUSY`
- `READLOG=CANCELLED`

### Messages the app sends on the file characteristic

- `{"RECIPE":"<recipeName>","SIZE":"<byteCount> ","SAVE":"1"}`
- `PNO=<packetNo>,DATA=<payload>`
- `COMPLETE`

### Messages the firmware sends back on the file characteristic

- `ACK`
- `ACK_CANCEL`

### Case sensitivity rules

The firmware command parser uses case-sensitive substring matching for many incoming commands. Because of that, the app must preserve these exact keys:

- Use lowercase `recipe=`, not uppercase `RECIPE=`
- Use lowercase `ingredients=`
- Use lowercase `add_confirm=`
- Use uppercase `STATUS=?`
- Use mixed-case `Firmware=?`
- Use uppercase `DATETIME=`
- Use uppercase `INDPOWER=`
- Use uppercase `PUMP=`
- Use uppercase `LISTRECIPES`
- Use uppercase `LISTLOGS`
- Use uppercase `READLOG=`
- Use uppercase `DELETE=`
- Use uppercase `COMPLETE` on the file characteristic

Important:

- The `recipe` command key is lowercase in both the firmware parser and the Android reference app.
- The `RECIPE=` form is firmware output telemetry, not the command spelling the app should send.

### Recipe name rules

The recipe name after `recipe=` must match the device-side stored recipe name format as closely as possible.

- The firmware stores uploaded recipes under `/recipe/<name>.txt`
- On SD-card inventory, the firmware currently keeps the `/recipe/` prefix inside `recipe_names[]`
- On SPIFFS upload, the file path is `/recipe/<name>.txt`
- Recipe selection uses substring matching:

```cpp
if (strstr(data, recipe_names[i]))
```

That means name formatting differences can still break execution even when the command key is correct.

Examples of risky differences:

- extra `/recipe/` prefix on one side but not the other
- trailing spaces in the stored name
- truncation to 30 characters in the app
- punctuation removed by app-side sanitizing
- firmware-side filename length limits

## 6. Recipe run flow, step by step

This is the most important section because the current bug is inside this flow.

### 6.1 Preconditions before starting a recipe

Before the app sends `recipe=<name>`:

1. BLE connection must be active.
2. Notifications must already be started on both characteristics.
3. The device should be checked for idle status.
4. The app should know which recipe to run.
5. If the device already has the recipe, do not upload it again.
6. If the device does not have the recipe, upload only the specific missing recipe needed now.

### 6.2 Correct just-in-time recipe upload rule

The correct policy is:

1. Check device inventory if the firmware truly supports returning it.
2. If the target recipe is already on the device, skip upload.
3. If it is missing, upload only that recipe JSON.
4. Do not push the full library to every device on every run.

### 6.3 Correct upload handshake

If upload is required:

1. Optional delete only if overwrite is explicitly intended.
2. Send file header:

```text
{"RECIPE":"SANDWICH ALTERAT","SIZE":"2404 ","SAVE":"1"}
```

3. Wait for `ACK`.
4. Send packets in order:

```text
PNO=1,DATA=...
PNO=2,DATA=...
...
```

5. Wait for one `ACK` per packet.
6. Send `COMPLETE`.
7. Wait for final `ACK`.
8. Only after the transfer is fully done may the app send `recipe=<name>`.

### 6.4 Correct recipe selection handshake

When starting a stored recipe:

1. Send:

```text
recipe=<firmwareRecipeName>
```

2. Firmware replies:

```text
ACK_command
```

3. This reply only means "recipe command accepted". It does not yet mean the device matched the recipe name, entered recipe selection mode, or started cooking.

4. The next valid state can be one of these:
   - `RECIPE=<name>,MODE=Receipe_Sel`
   - `RECIPE=<name>,MODE=Ingredient,STEPNO=<n>`
   - `RECIPE=<name>,MODE=Cooking,...`
   - temporarily `WORKSTATUS=IDLE` during transition

5. The app must not treat `ACK_command` by itself as a successful recipe start.

6. The app must wait for one of these before advancing:
   - `RECIPE=<name>,MODE=Receipe_Sel`
   - `RECIPE=<name>,MODE=Ingredient,STEPNO=<n>`
   - `RECIPE=<name>,MODE=Cooking,...`
   - `RECIPE=NONE`

7. If `RECIPE=NONE` arrives, the app must stop the run flow immediately and must not send `ingredients=100`.

### 6.5 Correct ingredient-stage rule

The firmware uses an ingredient stage before cooking. The app must respect it.

Rule:

1. After recipe selection, the app waits for ingredient mode if possible.
2. Once ingredient mode is seen, the app can send:

```text
ingredients=100
```

3. The firmware responds with:

```text
ACK_command
```

4. That advances the device out of ingredient stage and into cooking.

Important:

- A fallback timer that sends `ingredients=100` before ingredient mode is confirmed is not firmware-aligned.
- The Android reference flow waits on the recipe/ingredient state machine rather than treating `ACK_command` as a full selection success.

## 6.6 Known failure points in the current web app

These are the highest-risk mismatches observed in the current code review.

1. `recipe=` casing is not the problem.
   - The firmware expects lowercase `recipe` on input.
   - The Android reference app also sends lowercase `recipe=`.

2. `ACK_command` is currently too weak to be used as the recipe-start success signal.
   - It only confirms the command was received by the parser.
   - It does not confirm the recipe name matched `recipe_names[]`.

3. Recipe-name matching can still fail even after `ACK_command`.
   - The actual selection path uses substring matching against stored firmware-side names.
   - Any mismatch in truncation, path prefix, spaces, or punctuation can cause the command to be accepted but the recipe to fail to resolve.

4. Inventory name normalization can hide mismatches.
   - The app currently normalizes inventory names for UI and comparison.
   - That is useful for display, but it is not proof that the exact command value after `recipe=` will be accepted by the firmware selection path.

5. BLE disconnects are not deliberately triggered by the recipe-start commands in the reviewed firmware path.
   - The explicit BLE teardown/restart code is in the OTA flow.
   - If a disconnect happens during recipe start, it is more likely a crash, reboot, BLE stack failure, or protocol-sequencing problem than an intentional recipe-stop action.

5. Android reference behavior:
   - send `recipe=<name>`
   - request status after about 650 ms
   - if ingredient mode has still not been observed, use a fallback `ingredients=100` around 1800 ms

This is the same behavior the web app should mirror.

### 6.6 Correct cooking telemetry rule

Once cooking starts, firmware emits messages like:

```text
RECIPE=SANDWICH ALTERAT,MODE=Cooking,STEPNO=1,IND_RUN=20,MAG_RUN=0,STATUS=START,STIRRER=0,LOW,PUMP=0,
```

App rules:

1. Show recipe name.
2. Show mode.
3. Show step number.
4. Show induction and magnetron remaining seconds.
5. Show pause/start state.
6. Show stirrer and pump state.

### 6.7 Correct instruction completion rule

When a single instruction finishes, firmware emits:

```text
INSTR_RUN=COMPLETE
```

App rules:

1. Do not mark the whole recipe complete yet.
2. Mark the instruction step complete.
3. If the operator must advance, send:

```text
add_confirm=<currentStep>
```

4. If time edits are needed, send:

```text
add_confirm=<currentStep>,magTime=<delta>,indTime=<delta>
```

5. Firmware replies with either `ACK_command` or a timer echo such as:

```text
magTime=120,indTime=60
```

### 6.8 Correct completion rule

A recipe is complete only when the device says so, for example:

```text
RECIPE=COMPLETE
```

At that point:

1. Move the order into completion confirmation state.
2. Keep the order visible until the operator confirms it.
3. Only then release the device back to idle scheduling.

## 7. Firmware-side state rules

These rules come from the actual firmware handlers.

### 7.1 `recipe=<name>`

Firmware behavior:

- Copies the recipe name into the working runtime state.
- Sets `flag_RecipeSelfromBle = 1`.
- Sends:

```text
ACK_command
```

Important:

- This does not itself mean the recipe is already running.

### 7.2 `ingredients=<n>`

Firmware behavior:

- Interprets the numeric ingredient selector.
- `100` is a special "complete/advance ingredient selection" value.
- Sends:

```text
ACK_command
```

### 7.3 `add_confirm=...`

Firmware behavior:

- Either adjusts timers or confirms the step.
- Sends `ACK_command` for a plain confirm.
- Sends timer values if a time adjustment is included.

### 7.4 `stop=<n>`

Firmware behavior:

- `stop=100` triggers a device-side long-press stop path.
- The device can also emit `stop=100` back to the client.

Important rule:

- In the normal recipe run flow, the client should not be the one generating `stop=100`.

### 7.5 `WORKSTATUS=IDLE`

Firmware behavior:

- Can appear when the device is truly idle.
- Can also appear during state transitions, especially if no active recipe telemetry line has been emitted yet.

Important rule:

- Clients must not instantly destroy the active job on the first transitional `IDLE` if a recipe start was just initiated.

## 8. Order flow rules

The app-side order flow should be:

1. Orders enter `pending`.
2. Depending on settings:
   - manual assignment: operator chooses device
   - auto route: scheduler picks the best connected/allowed device

3. Once a device is selected:
   - if the device is busy, move the order to that device queue
   - if the device is idle, start the BLE run flow

4. Order states:
   - `pending`
   - `queued`
   - `starting`
   - `cooking`
   - `awaiting_confirmation`
   - `completed/history`

5. A device becomes schedulable again only after:
   - recipe completion signal
   - operator confirmation

## 9. Manual mode rules

Manual mode is separate from recipe mode.

Current manual mode commands exposed in the web app:

- `INDQUICKSTART=START`
- `INDQUICKSTART=STOP`
- `INDQUICKSTART=PAUSE`
- `INDQUICKSTART=RESUME`
- `INDPOWER=<delta>`
- `PUMP=ON,<ticks>`
- `PUMP=OFF`

Rules:

1. Manual mode should only operate on a connected device.
2. It should not be used while a recipe job is in progress.
3. Power adjustments should only be allowed when quickstart is active.
4. Pump units are 10 ml ticks in firmware, not raw ml.

## 10. Log retrieval rules

The log flow is separate from recipe execution.

1. To list logs:

```text
LISTLOGS
```

2. Firmware may send:
   - `ACK`
   - `LOGFILE=<file>`
   - `LISTLOGS=COMPLETE`
   - `LISTLOGS=ERROR`

3. To fetch one log:

```text
READLOG=<filename>
```

4. Firmware may respond:
   - `ACK`
   - `READLOG=BUSY`
   - `READLOG=DEVICE_BUSY`
   - `READLOG=START:...`
   - streamed `READLOG=...`

5. The app should not start a log read while the device is cooking.

## 11. Rules that must never be broken

These are the hard rules for version 1.

1. Do not send `stop=100` from the web app during the normal recipe flow.
2. Do not upload every recipe to every device.
3. Do not delete a recipe unless overwrite is truly intended.
4. Do not treat `ACK_command` from `recipe=<name>` as proof that cooking has begun.
5. Do not mark the full recipe complete on `INSTR_RUN=COMPLETE`.
6. Do not clear a just-started job on one early `WORKSTATUS=IDLE` transition.
7. Do not allow queue assignment to disconnected devices.
8. Do not assume app-side cached recipe inventory equals device truth.
9. Do not issue a second file transfer while one transfer is still waiting for packet ACKs.
10. Do not mix manual mode and recipe mode on the same device at the same time.

## 12. Current problem review

This section is the direct review of the issue you asked about.

### 12.1 What is definitely working

These parts are present in the current web app code:

1. BLE connection and notification setup.
2. Command and file characteristics are separated correctly.
3. Per-device write serialization exists in the web transport.
4. The web app does not intentionally send `stop=100`.
5. The web app mirrors the Android reference timing pattern for:
   - post-selection status request at about 650 ms
   - fallback `ingredients=100` at about 1800 ms
6. The UI has a startup guard to avoid clearing a recipe too early on transitional idle messages.
7. Manual mode commands for induction and pump exist.

### 12.2 What is not reliable yet

There are still real protocol and runtime gaps.

#### A. The device is genuinely disconnecting during run

This is not only a static-page/UI issue.

Why this is real:

- The web app flips to disconnected only when `gattserverdisconnected` fires.
- Your screenshots show:
  - connected
  - `ACK_command`
  - then disconnected

So there is a transport/session break during or immediately after recipe start.

#### B. Inventory confirmation is not trustworthy in the current firmware path

The current firmware `ListRecipeFiles()` implementation reviewed here prints recipe names to serial and only sends:

```text
LISTRECIPES=COMPLETE
```

over BLE at the end.

That means:

- `readRecipesAvailable()` in the web app can only be fully correct if the firmware actually emits recipe names over BLE.
- In the reviewed firmware path, that does not appear to happen.
- So app-side "device already has this recipe" logic may be relying partly on cached names, not confirmed device names.

This is a protocol gap, not just a UI gap.

#### C. `ACK_command` after `recipe=<name>` is being reached, but that is only the first checkpoint

The current run reaches:

1. BLE connected
2. `STATUS=?`
3. `WORKSTATUS=IDLE`
4. `recipe=<name>`
5. `ACK_command`

But the full run still needs:

6. ingredient/cooking telemetry
7. `ingredients=100` at the correct time
8. stable cooking telemetry
9. `INSTR_RUN=COMPLETE`
10. `add_confirm=<step>`
11. final `RECIPE=COMPLETE`

The disconnect is happening before that full chain becomes stable.

#### D. Transitional idle is still a dangerous part of this firmware

The firmware can emit `WORKSTATUS=IDLE` in windows where the app has already started a recipe.

That means every client must handle early idle carefully.

The web app already added a startup guard for this, which helps the UI, but it does not solve a physical BLE disconnect.

#### E. The problem is not "the web app is sending stop"

From the current reviewed web app transport:

- `abortRecipe()` explicitly throws instead of sending `stop=100`.
- There is no normal run path that sends `stop=100`.

So if a stop-like behavior occurs during run, it is more likely one of these:

1. firmware enters a stop/abort path on its own
2. firmware interprets another signal as a stop path
3. the BLE session drops before the recipe state machine fully advances

#### F. Recipe loss after reset is not explained by the current safer web run path

The current web flow no longer blindly deletes or uploads on every run.

So if recipes disappear after power cycle or reset, that points more toward:

1. firmware storage mode differences between SPIFFS and SD
2. flash/restart behavior
3. a firmware build/storage persistence issue

That is separate from the immediate run-disconnect bug.

### 12.3 Most likely root causes right now

Based on the reviewed code, the strongest current suspects are:

1. A BLE transport stability mismatch between Web Bluetooth and the firmware during the early `recipe=<name>` transition.
2. The device-side state machine still hitting an unstable path between recipe acknowledgement and ingredient/cooking mode.
3. Recipe inventory over BLE not being fully implemented, causing the app to operate with incomplete certainty about what is actually stored.
4. Browser-side BLE reconnect behavior being weaker than the Android RxAndroidBle reference when the firmware is busy.

### 12.4 What the protocol says the app should do next

To isolate the issue cleanly, the next debugging sequence should be:

1. Connect.
2. Send `STATUS=?`.
3. Confirm idle.
4. Send only `recipe=<name>`.
5. Record the exact next device message.
6. If still connected, wait for ingredient mode.
7. Only then send `ingredients=100`.
8. Record the next telemetry line.
9. On `INSTR_RUN=COMPLETE`, send `add_confirm=<step>`.

If disconnect happens before step 7, the break is in the selection/start transition.

If disconnect happens after step 7, the break is in ingredient-to-cooking transition.

If disconnect happens after `INSTR_RUN=COMPLETE`, the break is in step-advance handling.

## 13. Practical conclusion

The current main blocker is not that the app is static anymore. The blocker is that the recipe execution handshake is only partially stabilizing in the browser:

- connect works
- basic status works
- recipe select reaches `ACK_command`
- but the run is not surviving the full transition into stable cooking and completion

The most important protocol truths are:

1. `ACK_command` is not enough.
2. `ingredients=100` must happen at the right moment.
3. `add_confirm=<step>` is required for instruction progression.
4. `stop=100` must remain device-driven.
5. inventory confirmation needs a real BLE list response from firmware if we want safe just-in-time syncing.
