package logic;

import model.Airport;

/**
 * One plane in the air. Position is a PURE FUNCTION of simulated time
 * (linear interpolation along the current leg), so late ticks cause no
 * drift. Redirect/holding state generalizes the same idea to new legs.
 */
class ActivePlane {

    final ScheduledFlight scheduled;
    double x, y;
    boolean arrived;

    // ===== SHARED redirect core (used by C2/W3/W4/X13/X18) ==================
    // ONE mechanism, five callers: a plane abandons its planned route and flies
    // a NEW leg from its current position to any airport. Each mod is just a
    // different reason to call redirect():
    //   C2  storm hit      -> redirect(scheduled.from)   (turn back home)
    //   W3  airport closed  -> redirect(replacement)
    //   W4  manual/two-click -> redirect(user's choice)
    //   X13 collision       -> slowDown() reuses redirect + stretches the leg
    //   X18 emergency (E)   -> redirect(nearest open airport)
    boolean redirected;              // no longer flying the planned route
    Airport redirectTarget;          // where it is going instead
    private double legStartX, legStartY;  // where the new leg began
    private double legStartTime;          // when it began (sim minutes)
    private double legDuration;           // how long it takes (same speed as planned)
    // ===== end shared redirect core =========================================
    double progress; // 0..1 along the CURRENT leg (planned or redirected) [MOD X3]

    // ===== [MOD X10] holding pattern ========================================
    boolean holding;                 // circling above the destination
    private double holdStartTime;    // when the circling began (sim minutes)
    static final double HOLD_RADIUS = 3.0;       // world units
    static final double HOLD_RAD_PER_MIN = 0.25; // angular speed of the circle
    // ===== [end MOD X10] ====================================================

    ActivePlane(ScheduledFlight scheduled) {
        this.scheduled = scheduled;
        this.x = scheduled.from.getX();
        this.y = scheduled.from.getY();
    }

    void updatePosition(double simMinutes) {
        // [MOD X10] a holding plane circles its target; the ENGINE decides
        // when it may finally land (arrived stays false until then).
        if (holding) {
            Airport target = redirected ? redirectTarget : scheduled.to;
            double angle = (simMinutes - holdStartTime) * HOLD_RAD_PER_MIN;
            x = target.getX() + HOLD_RADIUS * Math.cos(angle);
            y = target.getY() + HOLD_RADIUS * Math.sin(angle);
            return;
        }
        // A redirected plane (shared core: C2/W3/W4/X13/X18) flies its OWN leg
        // from the turn point to redirectTarget, rebased in time. This branch
        // is what actually MOVES a redirected plane - without it, redirect()
        // would only set flags and nothing would change on screen.
        if (redirected) {
            double t = (simMinutes - legStartTime) / legDuration;
            if (t >= 1.0) { t = 1.0; arrived = true; }
            progress = t; // [MOD X3] progress restarts on the redirected leg
            x = legStartX + (redirectTarget.getX() - legStartX) * t;
            y = legStartY + (redirectTarget.getY() - legStartY) * t;
            return;
        }
        // NOTE: no 'double' here - this assigns the FIELD (a local declaration
        // would shadow it and the field would stay 0 forever) [MOD X3]
        progress=(simMinutes-scheduled.actualDepartureMinutes)/(scheduled.flight.getDurationMinutes());
        if(progress>=1.0){
            progress=1.0;
            arrived=true;
        }else if(progress<0){
            progress=0;
        }
        x=scheduled.from.getX()+progress*(scheduled.to.getX()-scheduled.from.getX());
        y=scheduled.from.getY()+progress*(scheduled.to.getY()-scheduled.from.getY());
    }
    /** [MOD C2] storm hit: going home is just a redirect to the origin. */
    void turnBack(double simMinutes) {
        redirect(scheduled.from, simMinutes);
    }
    /**
     * SHARED redirect core (C2/W3/W4/X13/X18). Abandon the planned route and
     * fly to newTarget from the CURRENT position, at the SAME SPEED as the
     * original flight:
     *   speed = plannedDistance / plannedDuration,  newDuration = newDistance / speed.
     */
    void redirect(Airport newTarget, double simMinutes) {
        redirected = true;
        redirectTarget = newTarget;
        legStartX = x;
        legStartY = y;
        legStartTime = simMinutes;
        double plannedDistance = Math.hypot(scheduled.to.getX() - scheduled.from.getX(),
                scheduled.to.getY() - scheduled.from.getY());
        double speed = plannedDistance / scheduled.flight.getDurationMinutes();
        double newDistance = Math.hypot(newTarget.getX() - x, newTarget.getY() - y);
        legDuration = Math.max(newDistance / Math.max(speed, 1e-9), 0.001);
    }

    /** [MOD X10] cannot land yet: start circling above the destination. */
    void enterHolding(double simMinutes) {
        holding = true;
        arrived = false;      // undo the arrival - the landing was refused
        holdStartTime = simMinutes;
    }

    // ===== [MOD X13] collision avoidance ====================================
    boolean evading; // already slowed once - never twice
    static final double COLLISION_RADIUS = 1.5; // world units

    /** Same path, 60% speed for the REST of the flight (reuses redirect). */
    void slowDown(double simMinutes) {
        Airport target = redirected ? redirectTarget : scheduled.to;
        redirect(target, simMinutes); // rebase the leg from the current position
        legDuration /= 0.6;           // stretch it -> slower
        evading = true;
    }
    // ===== [end MOD X13] ====================================================
}
