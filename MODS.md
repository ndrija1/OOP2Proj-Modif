# MODS — practice modifications and their switches

All modifications are OFF by default: the app then behaves exactly like the
base project. Each one is gated at its entry points by a flag in
[`util.Mods`](src/util/Mods.java), read from JVM system properties.

## How to activate

```
run-mods.bat                              everything + debug logging
java -Dmods=all      -cp out app.Main     everything
java -Dmod.c2=true   -cp out app.Main     one modification
java -Dmod.b2=true -Dmod.c1=true ...      any combination
```

The startup log always prints which mods are active:
`[INFO ] Mods: B2 rectangle select: off | C1 plane info: ON | C2 storms: ON`

## The modifications

| Flag | Mod | What it does | Code lives in |
|---|---|---|---|
| `mod.b2` | **B2 rectangle multi-select** | click empty map = first corner, dashed preview follows mouse, second click selects all airports inside (steady red); clicking a selected airport cancels; pauses the inactivity countdown under its own `"rect-selection"` reason | `MapPanel` (`[MOD B2]` sections), wiring in `MapTab` |
| `mod.c1` | **C1 plane info box** | click a plane → live info box (position, scheduled + actual departure) that follows it; click again to close; box disappears by itself when the plane lands | `MapPanel` (`[MOD C1]`), snapshot fields in `SimulationEngine.PlaneView` |
| `mod.c2` | **C2 storms** | right-click empty map and HOLD → red circle grows until release; a plane entering a storm turns orange and flies back to its origin at the same speed; right-click a storm deletes it; reset clears storms | `MapPanel` (`[MOD C2]`, input + painting), `SimulationEngine` (storm data + tick check), `ActivePlane` (return leg) |
| `mod.w1` | **W1 route line** | click a plane → dashed green line from its origin to its destination; shares the click/selection with C1; line disappears when the plane lands | `MapPanel` (`[MOD W1]`), route endpoints in `PlaneView` |
| `mod.w2` | **W2 connectivity** | click an airport → purple lines to all airports connected by one flight, then from those to THEIR neighbours (depth 2, undirected, each airport shown once); recomputed on every data/visibility change | `MapPanel` (`[MOD W2]`) |
| `mod.w3` | **W3 close airport** | select an airport + "Close airport..." button → pick a replacement in a dialog; planes in the air divert (orange), future takeoffs divert at launch, flights FROM a closed airport are held on the ground until it reopens; closed airports are crossed out; chains of closures resolve at use-time, so reopening restores older routes. Also: **CTRL+click an airport** → then click the replacement (two-click, no dialog); CTRL+click a closed airport = reopen | `MapTab` (button + dialog), `SimulationEngine` (`redirects` map, `resolveReplacement`, held takeoffs), `MapPanel` (crossed-out painting, two-click) |
| `mod.w4` | **W4 redirect one plane** | SHIFT+click a plane → dialog to pick its new destination; or **CTRL+click a plane** → then click the target airport (two-click). The plane turns from its CURRENT position at the same speed | `MapPanel` (`[MOD W4]`, `[MOD W3+W4]` two-click), `SimulationEngine.redirectPlane` |

Two-click targeting (`[MOD W3+W4]` in MapPanel): CTRL+click arms a plane or an
airport (orange ring/frame + yellow instruction strip), the NEXT plain click
picks the target; any other click cancels. The armed state OWNS the next click,
so its branches come first in `handleClick`. Dialogs remain available in
parallel — same engine core (`ActivePlane.redirect`), two input facades.

## Invented (X) modifications

| Flag | Mod | What it does | Code lives in |
|---|---|---|---|
| `mod.x1` | **trails** | fading dot trail behind every plane; history lives in the VIEW (map remembers what it has seen) | `MapPanel` |
| `mod.x2` | **live stats** | status bar adds takeoffs / redirected / avg delay (delay computed up front from the timetable) | `SimulationEngine` getters, `MapTab` |
| `mod.x3` | **percent** | route percentage next to each plane, read from the snapshot's `progress`; restarts on a redirected leg | `PlaneView.progress`, `MapPanel` |
| `mod.x4` | **delay airport** | button shifts all not-yet-departed flights of the selected airport +60 min (immutable copies + re-sort) | `SimulationEngine.delayAirport`, `MapTab` |
| `mod.x5` | **two runways** | airports with >5 flights: "last takeoff+10" generalizes to "oldest of last k takeoffs+10" | `DepartureScheduler` |
| `mod.x6` | **night ban** | no takeoffs 00:00–04:00: bump to 04:00, then re-apply the runway rule; night overlay on the map | `DepartureScheduler`, `MapPanel` |
| `mod.x7` | **priority** | double-click a flight row = priority; scheduler sorts priority first, +10 rule shifts the rest | `World` (runtime set), `DepartureScheduler`, `FlightsPanel` |
| `mod.x8` | **sim speed** | 0.5x/1x/2x/5x buttons; no jumps because time is ACCUMULATED per tick | `SimulationEngine.speedFactor`, `MapTab` |
| `mod.x10` | **holding** | max one landing per 10 min per airport; blocked/early planes CIRCLE; closing (W3) then means "circle until reopen" | `ActivePlane` (orbit), `SimulationEngine` (landing veto) |
| `mod.x12` | **altitude** | dot size follows flight phase (climb/cruise/descent), derived purely from `progress` | `MapPanel.paintPlanes` |
| `mod.x13` | **avoidance** | touching plane circles: the YOUNGER plane slows to 60% (deterministic, once, via redirect rebase) | `ActivePlane.slowDown`, `SimulationEngine.tick` |
| `mod.x14` | **day/night** | night shadow sweeps the map: longitude ↔ local time (15° = 1 h) | `MapPanel.paintDayNight` |
| `mod.x15` | **fog** | SHIFT+right-click fog circles hide planes from VIEW only (opposite of storms: view state, engine untouched) | `MapPanel` |
| `mod.x16` | **event log** | live panel: takeoffs, landings, redirects, closures — engine records, GUI displays | `SimulationEngine.addEvent`, `MapTab` |
| `mod.x17` | **airport stats** | selected airport shows takeoffs/landings/holding-now box | `SimulationEngine.getAirportStats`, `MapPanel` |
| `mod.x18` | **emergency** | select a plane, press **E** → diverts to the NEAREST open airport | `SimulationEngine.emergencyLand`, `MapPanel` key listener |
| `mod.x19` | **flight report** | "Save report..." writes a CSV: flight, scheduled, actual takeoff, real landing place, redirected | `SimulationEngine` records, `MapTab.saveReport` |
| `mod.x20` | **parking** | an airport parks max 3 landed planes; when full, arrivals circle (same landing-veto mechanism as X10) | `SimulationEngine` landing decision |
| `mod.x21` | **zoom & pan** | wheel zooms around the cursor (≤8x), arrows pan, HOME resets; lives ONLY in the 4 transform methods | `MapPanel` transforms + wheel/key listeners |

Not implemented (sketches discussed): X9 wind, X11 fuel, moving storms,
storm avoidance by detour, X6-rotation (return flights).

## How the gating works (say this at the defense)

- Every mod has clearly marked sections: search for `[MOD B2]`, `[MOD C1]`,
  `[MOD C2]`.
- Gates sit only at ENTRY POINTS — a click branch, a paint call, a listener
  wiring. The helper methods/fields below them are unreachable when the flag
  is off, so no `if` is needed inside them.
- The ENGINE has no flags at all: storms can only be created through the
  gated GUI handler, so with the mod off the storm list stays empty and the
  tick check loops over nothing. Same for `PlaneView`'s extra fields — unused
  data, zero behavior change.
- Flags are `static final`, read once at class load — the hot paths pay one
  boolean check.

## Quick demo script (all mods on)

1. Load `data\test.csv`, Start the simulation.
2. Click a plane → info box follows it (C1). Click again → closes.
3. Right-click-HOLD over the BEG–FRA corridor → storm grows (C2).
   Watch a plane enter, turn orange, fly home. Right-click the storm → gone.
4. Click empty map, move mouse (dashed preview), click again → airports
   inside turn steady red (B2). Debug log shows `paused by [rect-selection]`.
5. Click one of the red airports → rectangle and selection clear.
6. Click a plane → dashed green route line appears with the info box (W1+C1).
7. Click BEG → purple connectivity lines two levels deep (W2); debug log says
   `connectivity from BEG: 10 line(s)`.
8. Select JFK → "Close airport..." → pick YYZ: planes divert, JFK crossed out;
   select JFK again → button says "Reopen: JFK" (W3).
9. CTRL+click a plane → orange ring + yellow strip → click SYD: it diverts
   (W4 two-click). SHIFT+click a plane → same via dialog.
10. CTRL+click an open airport → click another airport: closed with that
    replacement; CTRL+click the crossed-out airport → reopened (W3 two-click).
