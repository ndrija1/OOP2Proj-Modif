# Practice folder — reconstruct the 9 core classes

This is a full working copy of the project, **except** the 9 load-bearing
classes have been blanked to stubs (signatures kept, bodies replaced with
`// TODO (practice)`). Your job: fill them back in from understanding, one at a
time, checking after each.

The **answer key** is the untouched project next door: `..ꈰ200_2026\`.
Only peek when stuck — the goal is to reconstruct, not transcribe.

## Order (easy → hard)

| # | File | Verified by |
|---|------|-------------|
| 1 | `src/model/ValidationException.java` | compiles |
| 2 | `src/model/Airport.java` | `selftest.bat` |
| 3 | `src/model/Flight.java` | `selftest.bat` |
| 4 | `src/model/World.java` | `selftest.bat` |
| 5 | `src/logic/ScheduledFlight.java` | `selftest.bat` |
| 6 | `src/logic/DepartureScheduler.java` | `selftest.bat` (runway rule) |
| 7 | `src/logic/ActivePlane.java` | `run-debug.bat` (planes fly) |
| 8 | `src/logic/InactivityMonitor.java` | `run-debug.bat` (idle 65s → closes) |
| 9 | `src/logic/SimulationEngine.java` | `run-debug.bat` (start/pause/reset) |

## Loop per file

1. Open the stub — the header comment lists exactly what it must do.
2. Write the body from memory (glance at the answer key only if stuck).
3. `build.bat` — must compile.
4. `selftest.bat` for #1–6, or `run-debug.bat` for #7–9.
5. Green? Next file.

## What the self-test proves as you go

`selftest.bat` has 16 checks. They light up in stages:

- After **#1–3** (ValidationException, Airport, Flight): the file-loading,
  round-trip and validation-rejection checks pass (~13 of 16) — because the
  copied codecs build Airports/Flights directly.
- After **#4–6** (World, ScheduledFlight, DepartureScheduler): the last checks
  — `replaceAll` + the `00:30 / 00:40 / 00:50` runway rule — go green.

When all 16 say PASS, your model + logic core is provably correct.
`ActivePlane`, `InactivityMonitor` and `SimulationEngine` (#7–9) aren't in the
self-test (they're timer/thread code) — verify those by running the app.

## Reset a single file

Deleted too much or want a clean slate for one class? Copy that one file back
from the answer key `..ꈰ200_2026\` and re-stub it yourself, or just diff your
version against it when you're done to see what you missed.
