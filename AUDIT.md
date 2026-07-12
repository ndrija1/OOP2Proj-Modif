# Audit checklist

Legend: `[auto]` = already verified by selftest.bat or a scripted run on 2026-06-10;
`[manual]` = needs a human in front of the GUI. Run everything with
`run-debug.bat` — the log tells you what the program *thinks* is happening,
which makes most checks one-glance.

## Phase A — data entry & display (20 pts)

- [manual] Add a valid airport (e.g. `Test / TST / 20 / 30`) → appears in the
  table, on the map, in the map's checkbox list, and in both Flights combos.
- [auto+manual] Validation messages are specific, not generic. Try each and
  read the message:
  - empty name; empty/blank coordinates
  - code `bg`, `BEGX`, `beg`, `B3G` → "exactly 3 uppercase letters"
  - duplicate code (add `BEG` twice) → names the existing airport
  - x = 181, x = -181, y = 91, y = -91 → states the allowed range and the value you typed
  - coordinate `abc` → "must be a number"
  - decimal comma `10,5` is ACCEPTED (convenience, not a bug)
- [manual] Add a valid flight → appears in flights table.
- [auto+manual] Flight validation: same from/to; departure `25:00`, `12:60`,
  `8.30`, empty; duration `0`, `-5`, `abc`, empty; adding a flight when there
  are no airports yet.
- [manual] Remove an airport that flights use → confirmation dialog states how
  many flights go with it; after confirming, those flights are gone everywhere.
- [auto] CSV load: `data\test.csv` → exactly 16 airports, 27 flights.
- [auto] JSON load: `data\test.json` → same numbers, same data.
- [auto] Save CSV → reload → identical. Save JSON → reload → identical.
- [manual] File error messages (each must explain the problem + suggest a fix,
  and must NOT wipe the currently loaded data):
  - nonexistent path (type a fake name into the chooser)
  - a `.txt` file → "unsupported file type"
  - CSV with the header line deleted → "does not contain the expected columns"
  - CSV with a 3-column airport row → message names the line number
  - CSV flight referencing an undefined code → message names the flight
  - JSON with a missing `"code"` field / a string where a number belongs /
    truncated file → message names the element index or line/column
- [auto] 60 s inactivity → app closes by itself (scripted: closed at exactly 60 s).
- [manual] The last-5-seconds dialog: counts down LIVE (5,4,3,2,1), the
  "continue" button restores the full 60 s, ignoring the dialog closes the app
  at 0. The X button on the dialog does nothing (intentional).
- [manual] Any click or key press resets the countdown (watch
  `countdown: ...s remaining` jump back to 59 in the debug log).

## Phase B — map (30 pts)

- [manual] Airports are gray squares with the code next to them; positions
  make sense (BEG center, SYD bottom-right, LAX far left, GRU bottom-left).
- [manual] Window resize → map rescales, squares stay clickable.
- [manual] Click an airport → blinks red ~3x/second. Click the SAME airport →
  blink stops. Click a DIFFERENT airport while one blinks → selection moves.
- [manual] While an airport is selected the inactivity countdown is FROZEN:
  select one, wait 70+ s → no dialog, log says `tick skipped, paused by
  [airport-selected]`. Deselect → countdown resumes from where it stopped.
- [manual] Checkbox list: unticking hides the airport (and its label) from the
  map; clicking where it was does NOT select it; re-ticking brings it back.
- [manual] Unticking the currently SELECTED airport also deselects it
  (otherwise an invisible blinking airport would freeze the timeout forever).
- [manual] Loading a file while airports are hidden/selected → stale selection
  and stale checkboxes are cleaned up, no crash.

## Phase C — simulation (40 pts)

- [manual] Start → clock begins at 00:00 and runs at 1 s real = 10 sim min
  (after 6 real seconds the clock must show 01:00).
- [auto] Runway rule: BEG flights scheduled 00:30 / 00:30 / 00:35 actually
  take off at 00:30 / 00:40 / 00:50 (selftest; also visible in the debug log
  as `takeoff at ...` lines, and on screen in the first seconds of a run).
- [manual] Planes are blue circles moving in straight lines, smoothly (no
  stutter), labels readable; planes vanish on landing and the
  "landed: x / 27" counter increases.
- [manual] Pause → clock and planes freeze. Resume → continues exactly where
  it stopped, no jump (pausing for a long time must not fast-forward planes).
- [manual] Reset → clock 00:00, no planes, Start works again from scratch.
- [manual] Start with zero flights (fresh app) → friendly error, not a crash.
- [manual] Inactivity countdown is frozen the whole time a simulation exists
  (running OR paused) — log shows `paused by [simulation]`; after Reset the
  countdown resumes.
- [manual] Add/remove airports or flights WHILE a sim runs → the running sim
  is unaffected (it uses a snapshot); Reset + Start picks up the new data.
- [manual] Let a full day pass (~2.5 min) → clock rolls over to "(day 2)",
  late flights (GRU 23:10, dep + 600 min) land normally past midnight.
- [manual] Selecting/deselecting airports and toggling checkboxes during a
  running sim works and doesn't disturb the animation.

## Cross-cutting / code-level

- [auto] `build.bat` compiles cleanly on a plain JDK (tested on JDK 25,
  `-encoding UTF-8`); no external libraries anywhere.
- [auto] `selftest.bat` — 16/16 checks pass.
- [manual] No exception stack traces in the console during any of the above.
- [manual] Closing via File > Exit and via the window X both terminate the
  process (check no java.exe left behind in Task Manager).
- [code] Layering: `model` imports nothing from gui/logic/io; `io` imports
  only model+util; `logic` imports model+util (plus javax.swing.Timer in
  InactivityMonitor — deliberate, documented); only `gui` touches Swing
  components. Verify with: `grep -r "import gui" src\model src\io src\logic`.
- [code] Threading: every mutable engine field is only touched inside
  `synchronized` methods; the GUI gets copies (`getPlaneViews`); all Swing
  calls happen on the EDT (`invokeLater` in MainFrame's engine listener).
- [code] Both timers (engine `java.util.Timer`, monitor `javax.swing.Timer`)
  are daemons / stopped on shutdown, so the JVM can always exit.

## Known fixed bugs (regression-test these)

1. **Timeout dialog froze the countdown** (fixed 2026-06-10): the modal
   dialog was opened directly inside the monitor's timer tick; swing timers
   don't fire again until the previous tick returns, so the countdown stuck
   at 5 s forever and the app never auto-closed while the window was open.
   Now the dialog is shown via `invokeLater`. Regression check: leave the app
   untouched for 65 s with the window in the foreground → dialog counts
   5→1 live and the app closes at 0.
