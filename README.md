# Air Traffic Simulator — OOP2 project 2025/26

Java Swing application implementing all three phases (A, B, C) of the
OOP2 project specification. No external libraries — plain JDK only
(JSON is parsed by the hand-written `util.Json`).

## Build & run

```
build.bat       compile everything into out\
run.bat         start the application
run-debug.bat   start with verbose logging (every timer reset, takeoff, landing, dialog)
selftest.bat    headless test of the model / file / scheduler layers (no GUI)
```

Or manually: `javac -encoding UTF-8 -d out` over `src\**\*.java`, then
`java -cp out app.Main`.

Test data from the specification is in `data\test.csv` and `data\test.json`
(File → Open starts in that folder).

## Architecture — layers and who may talk to whom

```
app          entry points (Main, SelfTest)
 └─ gui      Swing windows & painting   (MainFrame, MapTab, MapPanel, panels, dialogs)
     └─ logic   simulation & timing     (SimulationEngine, DepartureScheduler, InactivityMonitor)
         └─ model  data + rules         (World, Airport, Flight, ValidationException)
 └─ io       file formats               (CsvCodec, JsonCodec, FileService)  → uses model
 └─ util     helpers                    (Json, TimeUtil, Log)               → used by all
```

Dependencies point strictly downward. Panels never reference each other:
everything reacts to `World.Listener` (data changed) or the engine callback
(simulation tick). That is why adding an airport automatically refreshes the
table, both combo boxes, the map and the checkbox list without any of those
knowing about the form.

### Where each spec requirement lives

| Requirement (phase) | Implementation |
|---|---|
| Input + validation of airports/flights (A) | `model.Airport`, `model.Flight`, `model.World` (constructors throw `ValidationException` with user-ready messages) |
| Tabular display (A) | `gui.AirportsPanel`, `gui.FlightsPanel` (`AbstractTableModel` over the `World` lists) |
| CSV + JSON load/save with helpful errors (A) | `io.CsvCodec`, `io.JsonCodec`, `io.FileService` — errors carry line numbers / element indices and a suggested fix |
| 60 s auto-exit, countdown dialog in last 5 s (A) | `logic.InactivityMonitor` + `gui.TimeoutWarningDialog` (wired in `MainFrame`) |
| Map: gray squares + codes (B) | `gui.MapPanel.paintAirports` |
| Click to select, red blinking, click again to deselect (B) | `gui.MapPanel` (`handleClick`, blink `javax.swing.Timer`) |
| Inactivity timer paused while an airport is selected (B) | `MapTab` forwards selection to `InactivityMonitor.setPaused("airport-selected", …)` |
| Checkbox filter list (B) | `gui.MapTab.rebuildFilterList` → `MapPanel.setAirportVisible` |
| Sim from 00:00, 1 s real = 10 sim min (C) | `logic.SimulationEngine` (time derived from `System.nanoTime()` deltas — drift-free, smooth) |
| Max one takeoff per 10 min per airport, queueing (C) | `logic.DepartureScheduler` — the whole timetable is computed **before** the run starts, deterministically |
| Blue circles, straight line, 200 ms updates (C) | `logic.ActivePlane` (position is a pure function of sim time) + `MapPanel.paintPlanes` |
| Start / pause / reset (C) | `SimulationEngine` state machine + buttons in `MapTab` |
| Inactivity timer paused during simulation (C) | `MapTab.refreshSimulationUi` → `setPaused("simulation", …)` |
| Timers & threads (C) | engine runs on a daemon `java.util.Timer` background thread; all GUI updates hop to the EDT via `SwingUtilities.invokeLater`; engine state is `synchronized`, snapshots are copied for painting |

## Debugging

* `run-debug.bat` (i.e. `-Dapp.debug=true`) prints every layer's actions:
  inactivity resets/pauses, the computed timetable (including which flights
  were delayed by the runway rule), every takeoff/landing with the sim time,
  every dialog shown to the user.
* `selftest.bat` verifies the non-GUI layers in one second: file round-trips,
  validation rejections, and the runway rule against the spec's test data
  (BEG 00:30 / 00:30 / 00:35 must become 00:30 / 00:40 / 00:50).
* The departure timetable is computed up front (`DepartureScheduler`), so a
  whole simulation run is reproducible — no race-dependent takeoff order.

## Design decisions worth knowing at the defense

* **Swing over AWT** — proper double buffering (no flicker), `JTable`,
  `JTabbedPane`, modal dialogs that keep pumping timer events (this is what
  makes the live countdown inside the timeout dialog work).
* **Model is immutable** (`Airport`, `Flight` have final fields) — invalid
  objects cannot exist, and the simulation can safely snapshot them.
* **Visibility checkboxes are view state**, stored in `MapPanel`, not in the
  model — hiding an airport on the map is not a property of the airport.
* **One pause mechanism, many reasons**: `InactivityMonitor.setPaused(reason,
  on/off)` with string keys, so the phase-B rule (selection) and the phase-C
  rule (simulation) cannot accidentally cancel each other.
* The timeout dialog ignores its window-close button: the only way to keep
  the session alive is the explicit "continue" button, as the spec implies.
