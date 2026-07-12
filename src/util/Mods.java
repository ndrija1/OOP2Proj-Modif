package util;

/**
 * Central on/off switches for the practice modifications.
 *
 * Every modification is gated at its ENTRY POINTS (a click branch, a paint
 * call) by one of these flags. With all flags off the application behaves
 * exactly like the base project - the extra code is present but unreachable.
 *
 * How to activate:
 *     java -Dmod.b2=true -cp out app.Main      one modification
 *     java -Dmods=all    -cp out app.Main      everything (see run-mods.bat)
 *
 * The flags are read once at class-load time and are final, so there is no
 * runtime cost inside the paint/click hot paths beyond a boolean check.
 * See MODS.md for what each modification does and where its code lives.
 */
public final class Mods {

    /** B2: rectangle multi-select on the map (two clicks + live preview). */
    public static final boolean B2_RECT_SELECT = enabled("mod.b2");

    /** C1: click a plane to show a live info box (position, departure). */
    public static final boolean C1_PLANE_INFO = enabled("mod.c1");

    /** C2: right-click storms; planes that enter one return to their origin. */
    public static final boolean C2_STORMS = enabled("mod.c2");

    /** W1: click a plane to draw its origin->destination route line. */
    public static final boolean W1_ROUTE_LINE = enabled("mod.w1");

    /** W2: click an airport to draw its flight connections, two levels deep. */
    public static final boolean W2_CONNECTIVITY = enabled("mod.w2");

    /**W3: REROUTING*/
    public static final boolean W3_REROUTING = enabled("mod.w3");

    /** W4: SHIFT+click a plane to manually redirect it to another airport. */
    public static final boolean W4_PLANE_REDIRECT = enabled("mod.w4");

    /** X1: fading position trail behind every plane. */
    public static final boolean X1_TRAIL = enabled("mod.x1");

    /** X2: live statistics in the status bar. */
    public static final boolean X2_STATS = enabled("mod.x2");

    /** X3: percentage of route completed next to every plane. */
    public static final boolean X3_PERCENT = enabled("mod.x3");

    /** X4: button that delays all not-yet-departed flights of the selected airport. */
    public static final boolean X4_DELAY = enabled("mod.x4");

    /** X5: airports with more than 5 flights get a second runway. */
    public static final boolean X5_TWO_RUNWAYS = enabled("mod.x5");

    /** X6: no takeoffs between 00:00 and 04:00; queued flights start at 04:00. */
    public static final boolean X6_NIGHT_BAN = enabled("mod.x6");

    /** X7: double-click a flight in the table to prioritize its takeoff slot. */
    public static final boolean X7_PRIORITY = enabled("mod.x7");

    /** X8: buttons that change the simulation speed while it runs. */
    public static final boolean X8_SIM_SPEED = enabled("mod.x8");

    /** X10: max one landing per 10 min per airport; early/blocked planes circle. */
    public static final boolean X10_HOLDING = enabled("mod.x10");

    /** X12: plane dot size follows flight phase (climb / cruise / descent). */
    public static final boolean X12_ALTITUDE = enabled("mod.x12");

    /** X13: touching plane circles -> the younger plane slows down. */
    public static final boolean X13_AVOIDANCE = enabled("mod.x13");

    /** X14: a night shadow sweeps the map (longitude <-> local time). */
    public static final boolean X14_DAY_NIGHT = enabled("mod.x14");

    /** X15: SHIFT+right-click fog circles hide planes from VIEW only. */
    public static final boolean X15_FOG = enabled("mod.x15");

    /** X16: live event log panel (takeoffs, landings, redirects, closures). */
    public static final boolean X16_EVENT_LOG = enabled("mod.x16");

    /** X17: selecting an airport shows its takeoff/landing/holding balance. */
    public static final boolean X17_AIRPORT_STATS = enabled("mod.x17");

    /** X18: press E with a plane selected -> emergency landing at the nearest open airport. */
    public static final boolean X18_EMERGENCY = enabled("mod.x18");

    /** X19: "Save report..." button writes a CSV of all completed flights. */
    public static final boolean X19_REPORT = enabled("mod.x19");

    /** X20: an airport parks at most 3 landed planes; when full, arrivals circle. */
    public static final boolean X20_PARKING = enabled("mod.x20");

    /** X21: mouse wheel zooms around the cursor; arrow keys pan; HOME resets. */
    public static final boolean X21_ZOOM = enabled("mod.x21");

    /** X22: flights added during a running simulation take off live (no restart). */
    public static final boolean X22_ADD_FLIGHT_LIVE = enabled("mod.x22");

    /** X23: rewind - the sim clock can run backward (state derived from time). */
    public static final boolean X23_REWIND = enabled("mod.x23");

    /** X24: click a plane -> its origin and destination airports blink red. */
    public static final boolean X24_BLINK_ENDPOINTS = enabled("mod.x24");

    /** X25: click a plane -> focus on it, dim all OTHER planes. */
    public static final boolean X25_FOCUS = enabled("mod.x25");

    /** X26: click a plane -> that plane blinks (red ring on/off). */
    public static final boolean X26_BLINK_SELECTED = enabled("mod.x26");

    /** X27: click a plane -> highlight every plane departing from the same airport. */
    public static final boolean X27_SAME_ORIGIN = enabled("mod.x27");

    /** X28: click airport A then airport B -> draw the shortest flight route (BFS). */
    public static final boolean X28_SHORTEST_PATH = enabled("mod.x28");

    private Mods() {} // static flags only, never instantiated

    private static boolean enabled(String key) {
        if ("all".equalsIgnoreCase(System.getProperty("mods"))) return true;
        return Boolean.getBoolean(key); // true only if -D<key>=true was given
    }

    /** Called once at startup so the log records which mods are active. */
    public static void logStatus() {
        Log.info("Mods", "B2 rectangle select: " + onOff(B2_RECT_SELECT)
                + " | C1 plane info: " + onOff(C1_PLANE_INFO)
                + " | C2 storms: " + onOff(C2_STORMS)
                + " | W1 route line: " + onOff(W1_ROUTE_LINE)
                + " | W2 connectivity: " + onOff(W2_CONNECTIVITY)
                + " | W3 rerouting: " + onOff(W3_REROUTING)
                + " | W4 plane redirect: " + onOff(W4_PLANE_REDIRECT)
                + " | X1 trail: " + onOff(X1_TRAIL)
                + " | X2 stats: " + onOff(X2_STATS)
                + " | X3 percent: " + onOff(X3_PERCENT)
                + " | X4 delay: " + onOff(X4_DELAY)
                + " | X5 two runways: " + onOff(X5_TWO_RUNWAYS)
                + " | X6 night ban: " + onOff(X6_NIGHT_BAN)
                + " | X7 priority: " + onOff(X7_PRIORITY)
                + " | X8 sim speed: " + onOff(X8_SIM_SPEED)
                + " | X10 holding: " + onOff(X10_HOLDING)
                + " | X12 altitude: " + onOff(X12_ALTITUDE)
                + " | X13 avoidance: " + onOff(X13_AVOIDANCE)
                + " | X14 day/night: " + onOff(X14_DAY_NIGHT)
                + " | X15 fog: " + onOff(X15_FOG)
                + " | X16 event log: " + onOff(X16_EVENT_LOG)
                + " | X17 airport stats: " + onOff(X17_AIRPORT_STATS)
                + " | X18 emergency: " + onOff(X18_EMERGENCY)
                + " | X19 report: " + onOff(X19_REPORT)
                + " | X20 parking: " + onOff(X20_PARKING)
                + " | X21 zoom: " + onOff(X21_ZOOM)
                + " | X22 add flight live: " + onOff(X22_ADD_FLIGHT_LIVE)
                + " | X23 rewind: " + onOff(X23_REWIND)
                + " | X24 blink endpoints: " + onOff(X24_BLINK_ENDPOINTS)
                + " | X25 focus: " + onOff(X25_FOCUS)
                + " | X26 blink selected: " + onOff(X26_BLINK_SELECTED)
                + " | X27 same origin: " + onOff(X27_SAME_ORIGIN)
                + " | X28 shortest path: " + onOff(X28_SHORTEST_PATH)
        );
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "off";
    }
}
