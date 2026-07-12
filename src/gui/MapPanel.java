package gui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.*;
import java.util.List;

import javax.swing.JPanel;

import logic.SimulationEngine;
import model.Airport;
import model.Flight;
import model.World;
import util.Log;
import util.Mods;

/**
 * The map itself (phase B + C): paints airports as gray squares with their
 * codes, the selected airport blinking red, and active planes as blue circles.
 *
 * This panel only DRAWS and handles clicks on itself. It does not know about
 * timers, the inactivity rules or the control buttons - whoever is interested
 * in the selection subscribes through {@link SelectionListener} (MapTab does,
 * and forwards the information to the inactivity monitor).
 *
 * MODIFICATIONS in this file (all gated by {@link util.Mods} flags, all off
 * by default - see MODS.md for the full catalog):
 *   [B2] rectangle multi-select      [C1] plane info box     [C2] storms input
 *   [W1] route line                  [W2] connectivity       [W3+W4] two-click
 *   [X1] trails    [X3] percent      [X6] night overlay      [X12] altitude
 *   [X14] day/night terminator       [X15] fog               [X17] airport stats
 *   [X18] emergency key              [X21] zoom & pan (transform methods)
 */
public class MapPanel extends JPanel {

    /** Notified when the user selects or deselects an airport. */
    public interface SelectionListener {
        /** @param selected the selected airport, or null after deselection */
        void selectionChanged(Airport selected);
    }

    private static final int SQUARE_SIZE = 14;       // airport square edge, pixels
    private static final int PLANE_SIZE = 12;        // plane circle diameter, pixels
    private static final int MARGIN = 25;            // empty border inside the panel
    private static final int BLINK_INTERVAL_MS = 350;
    private static final int CLICK_SLACK = 3;        // extra pixels around a square that still count as a hit

    private final World world;
    private final SimulationEngine engine;
    private final SelectionListener selectionListener;

    /** Codes of airports hidden via the checkbox list (view state, not model data). */
    private final Set<String> hiddenCodes = new HashSet<>();

    private Airport selected;        // currently selected airport, or null
    private boolean blinkOn = true;  // toggled by blinkTimer while something is selected
    private final javax.swing.Timer blinkTimer;

    // ===== [MOD B2] rectangle multi-select: state ==========================
    private Point rectAnchor;        // non-null = first corner placed (ARMED)
    private Point rectPreview;       // current mouse position while armed
    private Rectangle selectionRect; // non-null = rectangle finalized (COMPLETED)
    private final Set<Airport> multiSelected = new HashSet<>();
    private java.util.function.Consumer<Boolean> multiSelectionListener;
    // ===== [end MOD B2] =====================================================

    // ===== [MOD C1] plane info: state =======================================
    private String selectedPlaneId;  // stable ID of the watched plane, null = none
    // ===== [end MOD C1] =====================================================

    // ===== [MOD X1] plane trails: state =====================================
    // The HISTORY lives in the VIEW: the map remembers the last N positions
    // it has SEEN for each plane id - the engine knows nothing about trails.
    private final Map<String, Deque<double[]>> trails = new HashMap<>();
    private static final int TRAIL_LENGTH = 20;
    // ===== [end MOD X1] =====================================================

    // ===== [MOD W3=close-airport / W4=redirect-plane] two-click targeting ===
    // "REDIRECT A PLANE by clicking a second airport" lives here (not a dialog).
    // CTRL+click arms one of these; the NEXT plain click picks the target
    // (any other click cancels). The dialogs remain available in parallel.
    // See handleTargetingArm() below for the arming, handleClick() for the
    // second click that executes it.
    private String redirectArmedPlaneId; // W4: plane waiting for a target airport
    private Airport closeArmedAirport;   // W3: airport waiting for its replacement
    // ===== [end two-click targeting] ========================================

    // ===== [MOD C2] storms: state ============================================
    private final javax.swing.Timer stormGrowTimer; // initialized in the constructor
    // ===== [end MOD C2] ======================================================

    // ===== [MOD W2] connectivity: state =====================================
    private final List<Airport[]> connectivityLines = new ArrayList<>(); // {from,to} pairs
    // ===== [end MOD W2] =====================================================

    // ===== [MOD X28] shortest path (BFS): state =============================
    private Airport pathSource;                 // first airport clicked, waiting for target
    private List<Airport> shortestPath;         // resolved A..B route, or null
    // ===== [end MOD X28] ====================================================

    // ===== [MOD X15] fog: pure VIEW state (planes fly normally underneath) ==
    private final List<double[]> fogs = new ArrayList<>(); // {x, y, radius} world
    private static final double FOG_RADIUS = 8.0;
    // ===== [end MOD X15] ====================================================

    // ===== [MOD X21] zoom & pan: state ======================================
    private double zoom = 1.0;         // 1 = whole world visible, up to 8x
    private double panX = 0, panY = 0; // view offset in pixels
    // ===== [end MOD X21] ====================================================

    public MapPanel(World world, SimulationEngine engine, SelectionListener selectionListener) {
        this.world = world;
        this.engine = engine;
        this.selectionListener = selectionListener;

        setBackground(Color.WHITE);

        // Drives the red blinking of the selected airport.
        blinkTimer = new javax.swing.Timer(BLINK_INTERVAL_MS, e -> {
            blinkOn = !blinkOn;
            repaint();
        });

        // [MOD C2] grows the newest storm while the right mouse button is held.
        // (Initialized here, not at the declaration: field initializers run
        // before the constructor assigns 'engine'.)
        stormGrowTimer = new javax.swing.Timer(50, e -> {
            engine.growLatestStorm(0.5);
            repaint();
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow(); // [MOD X18] clicks route keys to the map

                // [DEBUG] log exactly what was clicked + which modifiers.
                // Only prints under -Dapp.debug=true; invaluable at the defense
                // ("show me what you clicked").
                if (Log.isDebugEnabled()) {
                    SimulationEngine.PlaneView pv = findPlaneAt(e.getX(), e.getY());
                    Airport ha = findAirportAt(e.getX(), e.getY());
                    Log.debug("Click", (pv != null ? "plane " + pv.label
                            : ha != null ? "airport " + ha.getCode() : "empty map")
                            + " [" + (e.isShiftDown() ? "shift " : "")
                            + (e.isControlDown() ? "ctrl " : "")
                            + (javax.swing.SwingUtilities.isRightMouseButton(e) ? "right" : "left")
                            + "]");
                }

                // [MOD X15] SHIFT + right click toggles a fog circle
                if (Mods.X15_FOG && e.isShiftDown()
                        && javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    handleFogPress(e.getX(), e.getY());
                    return;
                }

                // [MOD C2] right mouse button is reserved for storms
                // (left button already carries selection + rectangle duties).
                if (Mods.C2_STORMS && javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    handleStormPress(e.getX(), e.getY());
                    return;
                }

                // [MOD W4] SHIFT + left click on a plane redirects that plane
                // via a DIALOG (plain click is taken by C1/W1, right click by storms)
                if (Mods.W4_PLANE_REDIRECT && e.isShiftDown()
                        && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    handlePlaneRedirect(e.getX(), e.getY());
                    return;
                }
                // [MOD W3+W4] CTRL + left click arms TWO-CLICK targeting:
                // plane -> next click picks its new destination;
                // airport -> next click picks the replacement for closing it
                // (CTRL+click on a CLOSED airport reopens it immediately).
                if (e.isControlDown() && javax.swing.SwingUtilities.isLeftMouseButton(e)
                        && (Mods.W4_PLANE_REDIRECT || Mods.W3_REROUTING)) {
                    handleTargetingArm(e.getX(), e.getY());
                    return;
                }
                handleClick(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                stormGrowTimer.stop(); // [MOD C2] release ends the growth
            }
        });

        // [MOD B2] the live rectangle preview needs mouse MOTION events -
        // note this is a separate listener channel from clicks.
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (Mods.B2_RECT_SELECT && rectAnchor != null) {
                    rectPreview = e.getPoint();
                    repaint();
                }
            }
        });

        // [MOD X18] key input needs focus; clicks give it to us
        setFocusable(true);
        addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (Mods.X18_EMERGENCY && e.getKeyCode() == java.awt.event.KeyEvent.VK_E
                        && selectedPlaneId != null) {
                    Airport sentTo = engine.emergencyLand(selectedPlaneId);
                    Log.debug("Map", sentTo == null ? "emergency ignored (landed?)"
                            : "emergency -> " + sentTo.getCode());
                    repaint();
                }
                // [DEBUG] jump-to-time: PAGE_UP skips the sim forward 1h, END
                // skips 6h. Forward only - the tick loop is level-based (it
                // compares to simMinutes), so one big tick launches/lands
                // everything in between. Backward is unsafe: takeoffs/landings
                // are destructive list mutations that do not un-happen.
                if (Log.isDebugEnabled()) {
                    if (e.getKeyCode() == java.awt.event.KeyEvent.VK_PAGE_UP) {
                        engine.skipForward(60);  repaint();
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_END) {
                        engine.skipForward(360); repaint();
                    }
                }
                // [MOD X21] arrow keys pan, HOME resets the view
                if (Mods.X21_ZOOM) {
                    int step = 60;
                    switch (e.getKeyCode()) {
                        case java.awt.event.KeyEvent.VK_LEFT:  panX += step; repaint(); break;
                        case java.awt.event.KeyEvent.VK_RIGHT: panX -= step; repaint(); break;
                        case java.awt.event.KeyEvent.VK_UP:    panY += step; repaint(); break;
                        case java.awt.event.KeyEvent.VK_DOWN:  panY -= step; repaint(); break;
                        case java.awt.event.KeyEvent.VK_HOME:
                            zoom = 1.0; panX = 0; panY = 0; repaint(); break;
                    }
                }
            }
        });

        // [MOD X21] mouse wheel zooms around the cursor: the world point under
        // the mouse must stay under the mouse, so pan is re-solved from the
        // transform equation after every zoom change.
        addMouseWheelListener(e -> {
            if (!Mods.X21_ZOOM) return;
            double wx = toWorldX(e.getX());
            double wy = toWorldY(e.getY());
            double factor = e.getWheelRotation() < 0 ? 1.15 : 1 / 1.15;
            zoom = Math.max(1.0, Math.min(8.0, zoom * factor));
            int usableX = Math.max(1, getWidth() - 2 * MARGIN);
            int usableY = Math.max(1, getHeight() - 2 * MARGIN);
            panX = e.getX() - MARGIN
                    - (wx - Airport.MIN_X) / (Airport.MAX_X - Airport.MIN_X) * usableX * zoom;
            panY = e.getY() - MARGIN
                    - (Airport.MAX_Y - wy) / (Airport.MAX_Y - Airport.MIN_Y) * usableY * zoom;
            if (zoom == 1.0) { panX = 0; panY = 0; } // fully zoomed out = home view
            repaint();
        });

        // If an airport disappears (deleted, or replaced by a file load) the
        // selection and the hidden-set must not keep pointing at it.
        world.addListener(this::pruneViewState);
    }

    // ----------------------------------------------------------- selection

    public Airport getSelected() {
        return selected;
    }

    /**
     * Left-click dispatch. Branch ORDER is the specification:
     *   1. [B2] click a selected airport inside the finished rectangle -> cancel
     *   2. [B2] second click while armed -> finalize the rectangle
     *   3. [C1] planes are drawn on top of airports, so they win the hit-test
     *   4. [B2] click on empty map -> arm a new rectangle
     *   5. base: toggle single-airport selection
     */
    private void handleClick(int px, int py) {
        Airport hit = findAirportAt(px, py);

        // [MOD W4] armed by CTRL+click on a plane: THIS click picks the
        // target airport - any other click cancels. Checked before everything
        // else, because the armed state OWNS the next click.
        if (redirectArmedPlaneId != null) {
            if (hit != null && !engine.isAirportClosed(hit.getCode())) {
                engine.redirectPlane(redirectArmedPlaneId, hit);
            } else {
                Log.debug("Map", "two-click redirect cancelled");
            }
            redirectArmedPlaneId = null;
            repaint();
            return;
        }
        // [MOD W3] armed by CTRL+click on an airport: THIS click picks the
        // replacement (must be another OPEN airport) - anything else cancels.
        if (closeArmedAirport != null) {
            if (hit != null && hit != closeArmedAirport
                    && !engine.isAirportClosed(hit.getCode())) {
                engine.closeAirport(closeArmedAirport.getCode(), hit);
            } else {
                Log.debug("Map", "two-click close cancelled");
            }
            closeArmedAirport = null;
            repaint();
            return;
        }

        if (Mods.B2_RECT_SELECT) {
            if (selectionRect != null && hit != null && multiSelected.contains(hit)) {
                cancelRectangle();
                return;
            }
            if (rectAnchor != null) {
                finalizeRectangle(new Point(px, py));
                return;
            }
        }

        // [MOD X18] emergency also needs the plane selection, so it keeps
        // this branch alive even when C1/W1 are off.
        if (Mods.C1_PLANE_INFO || Mods.W1_ROUTE_LINE || Mods.X18_EMERGENCY
                || Mods.X24_BLINK_ENDPOINTS || Mods.X25_FOCUS
                || Mods.X26_BLINK_SELECTED || Mods.X27_SAME_ORIGIN) {
            SimulationEngine.PlaneView plane = findPlaneAt(px, py);
            if (plane != null) {
                selectedPlaneId = plane.id.equals(selectedPlaneId) ? null : plane.id;
                // [MOD X24/X26] anything that BLINKS needs blinkTimer, which
                // otherwise only runs for a selected AIRPORT - start it here too.
                if (Mods.X24_BLINK_ENDPOINTS || Mods.X26_BLINK_SELECTED) {
                    if (selectedPlaneId != null) { blinkOn = true; blinkTimer.restart(); }
                    else if (selected == null) { blinkTimer.stop(); }
                }
                Log.debug("Map", selectedPlaneId == null
                        ? "plane info closed" : "plane info opened: " + plane.label);
                repaint();
                return;
            }
        }

        if (hit == null) {
            if (Mods.B2_RECT_SELECT) {
                cancelRectangle();               // a new rectangle discards any old one
                rectAnchor = new Point(px, py);
                rectPreview = rectAnchor;
                Log.debug("Map", "rectangle armed at " + px + "," + py);
                repaint();
            }
            return; // base behavior: clicks on empty map do nothing
        }

        // [MOD X28] first airport click = source, second = target -> shortest path
        if (Mods.X28_SHORTEST_PATH && hit != null) {
            if (pathSource == null) {
                pathSource = hit;
                shortestPath = null;
                Log.debug("Map", "path source " + hit.getCode() + " - now click a destination");
            } else {
                shortestPath = shortestPath(pathSource, hit);
                Log.info("Map", shortestPath == null
                        ? "no route " + pathSource.getCode() + " -> " + hit.getCode()
                        : "shortest " + pathSource.getCode() + " -> " + hit.getCode()
                          + ": " + (shortestPath.size() - 1) + " flight(s)");
                pathSource = null;
            }
            setSelected(hit);
            return;
        }

        if (hit == selected) {
            setSelected(null);            // clicking the selected airport deselects it
        } else {
            setSelected(hit);
        }
    }

    // ===== [MOD X28] shortest path through the flight graph (BFS) ============
    // Airports = nodes, flights = DIRECTED edges (you fly from -> to). BFS
    // explores level by level, so the FIRST time it reaches the target is via
    // the fewest flights. cameFrom remembers who discovered each airport, which
    // lets us walk the path back. This is W2 connectivity without a depth
    // limit, plus a queue and a cameFrom map.
    private List<Airport> shortestPath(Airport from, Airport to) {
        Queue<Airport> queue = new ArrayDeque<>();
        Set<Airport> visited = new HashSet<>();
        Map<Airport, Airport> cameFrom = new HashMap<>();
        queue.add(from);
        visited.add(from);
        while (!queue.isEmpty()) {
            Airport cur = queue.poll();
            if (cur == to) break;
            for (Flight f : world.getFlights()) {
                if (!f.getFrom().equals(cur.getCode())) continue; // only edges leaving cur
                Airport next = world.findAirport(f.getTo());
                if (next == null || visited.contains(next)) continue;
                visited.add(next);
                cameFrom.put(next, cur);
                queue.add(next);
            }
        }
        if (!visited.contains(to)) return null; // no route
        List<Airport> path = new ArrayList<>();
        for (Airport a = to; a != null; a = cameFrom.get(a)) path.add(0, a);
        return path;
    }

    private void paintShortestPath(Graphics2D g2) {
        if (shortestPath == null || shortestPath.size() < 2) return;
        g2.setColor(new Color(200, 0, 200)); // magenta route
        g2.setStroke(new BasicStroke(3));
        for (int i = 0; i < shortestPath.size() - 1; i++) {
            Airport a = shortestPath.get(i), b = shortestPath.get(i + 1);
            g2.drawLine(toScreenX(a.getX()), toScreenY(a.getY()),
                    toScreenX(b.getX()), toScreenY(b.getY()));
        }
    }
    // ===== [end MOD X28] ====================================================

    private void setSelected(Airport airport) {
        selected = airport;
        if (selected != null) {
            blinkOn = true;
            blinkTimer.restart();
            Log.debug("Map", "selected airport " + selected.getCode());
        } else {
            blinkTimer.stop();
            Log.debug("Map", "selection cleared");
        }
        if (Mods.W2_CONNECTIVITY) recomputeConnectivity(); // [MOD W2]

        selectionListener.selectionChanged(selected);
        repaint();
    }

    private Airport findAirportAt(int px, int py) {
        // Iterate in reverse so the airport drawn on top wins when squares overlap.
        List<Airport> airports = world.getAirports();
        for (int i = airports.size() - 1; i >= 0; i--) {
            Airport a = airports.get(i);
            if (hiddenCodes.contains(a.getCode())) continue;
            int sx = toScreenX(a.getX());
            int sy = toScreenY(a.getY());
            int half = SQUARE_SIZE / 2 + CLICK_SLACK;
            if (Math.abs(px - sx) <= half && Math.abs(py - sy) <= half) {
                return a;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ filtering

    /** Called by the checkbox list to show/hide one airport on the map. */
    public void setAirportVisible(String code, boolean visible) {
        if (visible) {
            hiddenCodes.remove(code);
        } else {
            hiddenCodes.add(code);
            // Hiding the selected airport also deselects it - a blinking
            // invisible square would freeze the inactivity timer forever.
            if (selected != null && selected.getCode().equals(code)) {
                setSelected(null);
            }
            // [MOD B2] same rule for the multi-selection (no-op when the mod
            // is off - the set is always empty then).
            multiSelected.removeIf(a -> a.getCode().equals(code));
            if (multiSelected.isEmpty() && selectionRect != null) cancelRectangle();
        }
        if (Mods.W2_CONNECTIVITY) recomputeConnectivity(); // [MOD W2] visibility affects the walk
        repaint();
    }

    public boolean isAirportVisible(String code) {
        return !hiddenCodes.contains(code);
    }

    /** Drops view state that refers to airports that no longer exist. */
    private void pruneViewState() {
        hiddenCodes.removeIf(code -> world.findAirport(code) == null);
        if (selected != null && world.findAirport(selected.getCode()) != selected) {
            setSelected(null);
        }
        // [MOD B2] ghost airports must not stay multi-selected either.
        multiSelected.removeIf(a -> world.findAirport(a.getCode()) != a);
        if (multiSelected.isEmpty() && selectionRect != null) cancelRectangle();
        // [MOD W3] a deleted airport cannot stay armed for closing
        if (closeArmedAirport != null
                && world.findAirport(closeArmedAirport.getCode()) != closeArmedAirport) {
            closeArmedAirport = null;
        }
        if (Mods.W2_CONNECTIVITY) recomputeConnectivity(); // [MOD W2] data changed under us
        pathSource = null; shortestPath = null; // [MOD X28] drop a possibly-stale route
        repaint();
    }

    // ------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        paintGrid(g2);
        if (Mods.X14_DAY_NIGHT) paintDayNight(g2);
        if (Mods.C2_STORMS) paintStorms(g2);              // under everything else
        if (Mods.W2_CONNECTIVITY) paintConnectivity(g2);
        if (Mods.X28_SHORTEST_PATH) paintShortestPath(g2);
        if (Mods.B2_RECT_SELECT) paintSelectionRect(g2);
        paintAirports(g2);
        if (Mods.X15_FOG) paintFog(g2); // [MOD X15]
        // One snapshot per frame: both plane painters must draw the same instant.
        List<SimulationEngine.PlaneView> planes = engine.getPlaneViews();
        if (Mods.X1_TRAIL) {
            updateTrails(planes);
            paintTrails(g2);
        }
        if (Mods.W1_ROUTE_LINE) paintRouteLine(g2, planes);
        paintPlanes(g2, planes);
        if (Mods.C1_PLANE_INFO) paintPlaneInfo(g2, planes);
        if (Mods.X17_AIRPORT_STATS) paintAirportStats(g2);
        if (Mods.X6_NIGHT_BAN) paintNightOverlay(g2);
        if (Mods.W3_REROUTING || Mods.W4_PLANE_REDIRECT) {
            paintTargetingHint(g2, planes); // on top of everything
        }
        if (Log.isDebugEnabled()) paintDebugHud(g2); // [DEBUG] very top layer
    }

    /** [DEBUG] on-screen engine state, so the defense demo needs no console. */
    private void paintDebugHud(Graphics2D g2) {
        String[] lines = {
                "state: " + engine.getState(),
                "sim:   " + util.TimeUtil.formatClock(engine.getSimMinutes()),
                "speed: " + engine.getSpeedFactor() + "x",
                "air:   " + engine.getActiveCount(),
                "done:  " + engine.getCompletedFlights() + " / " + engine.getTotalFlights(),
                "zoom:  " + String.format("%.1f", zoom),
        };
        g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm = g2.getFontMetrics();
        int w = 0;
        for (String l : lines) w = Math.max(w, fm.stringWidth(l));
        int bx = getWidth() - w - 16, by = 8;
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(bx, by, w + 10, fm.getHeight() * lines.length + 6);
        g2.setColor(Color.WHITE);
        int ty = by + fm.getAscent() + 3;
        for (String l : lines) { g2.drawString(l, bx + 5, ty); ty += fm.getHeight(); }
    }

    /** Light coordinate grid + axes, so positions are easier to judge. */
    private void paintGrid(Graphics2D g2) {
        g2.setColor(new Color(235, 235, 235));
        for (int x = -180; x <= 180; x += 30) {
            int sx = toScreenX(x);
            g2.drawLine(sx, toScreenY(90), sx, toScreenY(-90));
        }
        for (int y = -90; y <= 90; y += 30) {
            int sy = toScreenY(y);
            g2.drawLine(toScreenX(-180), sy, toScreenX(180), sy);
        }
        // axes (x = 0 and y = 0) slightly darker
        g2.setColor(new Color(210, 210, 210));
        g2.drawLine(toScreenX(0), toScreenY(90), toScreenX(0), toScreenY(-90));
        g2.drawLine(toScreenX(-180), toScreenY(0), toScreenX(180), toScreenY(0));
        // map border
        g2.setColor(Color.LIGHT_GRAY);
        g2.setStroke(new BasicStroke(1));
        g2.drawRect(toScreenX(-180), toScreenY(90),
                toScreenX(180) - toScreenX(-180), toScreenY(-90) - toScreenY(90));
    }

    private void paintAirports(Graphics2D g2) {
        Font codeFont = getFont().deriveFont(Font.BOLD, 11f);
        g2.setFont(codeFont);
        FontMetrics fm = g2.getFontMetrics();

        // [MOD X24] resolve the selected plane's origin/destination ONCE, so its
        // two airports blink together with the plane. PlaneView carries the
        // from/to coordinates (W1), so we match airports by coordinate.
        SimulationEngine.PlaneView selPlane = null;
        if (Mods.X24_BLINK_ENDPOINTS && selectedPlaneId != null) {
            selPlane = findViewById(engine.getPlaneViews(), selectedPlaneId);
        }

        for (Airport a : world.getAirports()) {
            if (hiddenCodes.contains(a.getCode())) continue;
            int sx = toScreenX(a.getX());
            int sy = toScreenY(a.getY());

            // [MOD X24] is this airport an endpoint of the selected plane's route?
            boolean endpoint = selPlane != null
                    && ((a.getX() == selPlane.fromX && a.getY() == selPlane.fromY)
                     || (a.getX() == selPlane.toX   && a.getY() == selPlane.toY));
            // Blinking red = single selection or [X24] a route endpoint;
            // steady red = [MOD B2] rectangle selection (visually distinct).
            boolean red = ((a == selected || endpoint) && blinkOn) || multiSelected.contains(a);
            g2.setColor(red ? Color.RED : Color.GRAY);
            g2.fillRect(sx - SQUARE_SIZE / 2, sy - SQUARE_SIZE / 2, SQUARE_SIZE, SQUARE_SIZE);

            // [MOD W3] frame around the airport armed for two-click closing
            if (a == closeArmedAirport) {
                g2.setColor(Color.ORANGE);
                g2.drawRect(sx - SQUARE_SIZE, sy - SQUARE_SIZE, SQUARE_SIZE * 2, SQUARE_SIZE * 2);
            }
            // [MOD W3] closed airports are crossed out
            if (Mods.W3_REROUTING && engine.isAirportClosed(a.getCode())) {
                g2.setColor(Color.BLACK);
                g2.drawLine(sx - SQUARE_SIZE / 2, sy - SQUARE_SIZE / 2,
                        sx + SQUARE_SIZE / 2, sy + SQUARE_SIZE / 2);
                g2.drawLine(sx - SQUARE_SIZE / 2, sy + SQUARE_SIZE / 2,
                        sx + SQUARE_SIZE / 2, sy - SQUARE_SIZE / 2);
            }

            g2.setColor(Color.DARK_GRAY);
            g2.drawString(a.getCode(), sx + SQUARE_SIZE / 2 + 3, sy + fm.getAscent() / 2 - 1);
        }
    }

    private void paintPlanes(Graphics2D g2, List<SimulationEngine.PlaneView> planes) {
        if (planes.isEmpty()) return;

        Font labelFont = getFont().deriveFont(Font.PLAIN, 10f);
        g2.setFont(labelFont);

        // [MOD X25/X26/X27] resolve the clicked plane once (its origin drives X27)
        SimulationEngine.PlaneView selPlane = (selectedPlaneId != null)
                ? findViewById(planes, selectedPlaneId) : null;

        for (SimulationEngine.PlaneView p : planes) {
            if (Mods.X15_FOG && isInFog(p.x, p.y)) continue; // hidden, still flying
            // [MOD X12] altitude from flight phase, read straight from progress:
            // climb = first 10%, descent = last 10%, cruise in between.
            int size = PLANE_SIZE;
            if (Mods.X12_ALTITUDE) {
                double alt = Math.min(p.progress / 0.1, (1.0 - p.progress) / 0.1);
                alt = Math.max(0, Math.min(1, alt));
                size = (int) (PLANE_SIZE * (0.6 + 0.4 * alt)); // 60%..100% of full size
            }

            int sx = toScreenX(p.x);
            int sy = toScreenY(p.y);

            boolean isSelected = p.id.equals(selectedPlaneId);
            boolean sameOrigin = selPlane != null
                    && p.fromX == selPlane.fromX && p.fromY == selPlane.fromY;

            // [MOD C2] planes fleeing a storm are orange (never true when off).
            Color base = p.redirected ? new Color(230, 120, 0) : Color.BLUE;
            // [MOD X25] focus: dim every plane that is NOT the clicked one.
            if (Mods.X25_FOCUS && selPlane != null && !isSelected) {
                base = new Color(205, 205, 205);
            }
            // [MOD X27] highlight planes sharing the clicked plane's origin
            // (green wins over the X25 dim - applied after it).
            if (Mods.X27_SAME_ORIGIN && sameOrigin && !isSelected) {
                base = new Color(0, 160, 0);
            }
            g2.setColor(base);
            g2.fillOval(sx - size / 2, sy - size / 2, size, size);

            // [MOD X26] the clicked plane blinks: a red ring on/off with blinkOn.
            if (Mods.X26_BLINK_SELECTED && isSelected && blinkOn) {
                g2.setColor(Color.RED);
                g2.drawOval(sx - size, sy - size, size * 2, size * 2);
            }
            // [MOD W4] ring around the plane armed for two-click redirect
            if (p.id.equals(redirectArmedPlaneId)) {
                g2.setColor(Color.ORANGE);
                g2.drawOval(sx - PLANE_SIZE, sy - PLANE_SIZE, PLANE_SIZE * 2, PLANE_SIZE * 2);
            }
            g2.setColor(new Color(0, 0, 140));
            // [MOD X3] the label is already painted - percentage is just more text
            String text = Mods.X3_PERCENT
                    ? p.label + "  " + (int) (p.progress * 100) + "%"
                    : p.label;
            g2.drawString(text, sx + PLANE_SIZE / 2 + 2, sy - PLANE_SIZE / 2);
        }
    }

    // ------------------------------------------- world <-> screen transform
    // World x in [-180,180] maps onto the panel width, y in [-90,90] onto the
    // height (positive y is UP, so the screen coordinate is flipped).

    private int toScreenX(double worldX) {
        // [MOD X21] zoom and pan live ONLY here (and in the inverses below):
        // every painter and every hit-test goes through these four methods,
        // so the whole map zooms for free - single point of truth.
        int usable = Math.max(1, getWidth() - 2 * MARGIN);
        return (int) Math.round(MARGIN + panX + (worldX - Airport.MIN_X)
                / (Airport.MAX_X - Airport.MIN_X) * usable * zoom);
    }

    private int toScreenY(double worldY) {
        int usable = Math.max(1, getHeight() - 2 * MARGIN);
        return (int) Math.round(MARGIN + panY + (Airport.MAX_Y - worldY)
                / (Airport.MAX_Y - Airport.MIN_Y) * usable * zoom);
    }

    // [MOD C2] pixels -> world coordinates (exact inverses of toScreenX/Y).
    // Storms are clicked in pixels but live in the engine in world units.

    private double toWorldX(int px) {
        int usable = Math.max(1, getWidth() - 2 * MARGIN);
        return Airport.MIN_X + (px - MARGIN - panX)
                * (Airport.MAX_X - Airport.MIN_X) / (usable * zoom);
    }

    private double toWorldY(int py) {
        int usable = Math.max(1, getHeight() - 2 * MARGIN);
        return Airport.MAX_Y - (py - MARGIN - panY)
                * (Airport.MAX_Y - Airport.MIN_Y) / (usable * zoom);
    }

    // ===== [MOD B2] rectangle multi-select ==================================
    // State machine: IDLE -(click empty map)-> ARMED -(second click)-> COMPLETED.
    // Clicking a selected airport inside the completed rectangle cancels all.

    /** MapTab wires the inactivity pause through this (true = airports multi-selected). */
    public void setMultiSelectionListener(java.util.function.Consumer<Boolean> l) {
        this.multiSelectionListener = l;
    }

    /** Second corner placed: normalize, collect visible airports inside. */
    private void finalizeRectangle(Point corner) {
        selectionRect = normalized(rectAnchor, corner);
        rectAnchor = null;
        rectPreview = null;
        multiSelected.clear();
        for (Airport a : world.getAirports()) {
            if (hiddenCodes.contains(a.getCode())) continue;
            if (selectionRect.contains(toScreenX(a.getX()), toScreenY(a.getY()))) {
                multiSelected.add(a);
            }
        }
        if (multiSelected.isEmpty()) {
            selectionRect = null; // caught nothing -> no way to cancel it later, drop it now
        }
        Log.debug("Map", "rectangle finalized: " + multiSelected.size() + " airport(s) selected");
        notifyMultiSelection();
        repaint();
    }

    /** Deselect everything and remove the rectangle. */
    private void cancelRectangle() {
        if (selectionRect == null && rectAnchor == null) return;
        rectAnchor = null;
        rectPreview = null;
        selectionRect = null;
        multiSelected.clear();
        Log.debug("Map", "rectangle cancelled");
        notifyMultiSelection();
        repaint();
    }

    private void notifyMultiSelection() {
        if (multiSelectionListener != null) {
            multiSelectionListener.accept(!multiSelected.isEmpty());
        }
    }

    /** The user may drag up-left; min/max makes the rectangle valid either way. */
    private static Rectangle normalized(Point a, Point b) {
        return new Rectangle(Math.min(a.x, b.x), Math.min(a.y, b.y),
                Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    /** Dashed outline: preview while armed, kept once finalized. */
    private void paintSelectionRect(Graphics2D g2) {
        Rectangle r = (rectAnchor != null && rectPreview != null)
                ? normalized(rectAnchor, rectPreview)
                : selectionRect;
        if (r == null) return;
        g2.setColor(new Color(0, 90, 200));
        g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10, new float[]{4}, 0));
        g2.draw(r);
    }
    // ===== [end MOD B2] =====================================================

    // ===== [MOD C1] plane info box ==========================================
    // Planes are snapshots rebuilt every tick, so the panel stores a stable ID
    // and re-resolves it against the current snapshot each frame. A failed
    // lookup means the plane landed -> the selection clears itself.

    private SimulationEngine.PlaneView findPlaneAt(int px, int py) {
        List<SimulationEngine.PlaneView> planes = engine.getPlaneViews();
        for (int i = planes.size() - 1; i >= 0; i--) {
            SimulationEngine.PlaneView p = planes.get(i);
            int dx = px - toScreenX(p.x);
            int dy = py - toScreenY(p.y);
            int r = PLANE_SIZE / 2 + CLICK_SLACK;
            if (dx * dx + dy * dy <= r * r) return p; // inside the circle
        }
        return null;
    }

    /** Painted (not a dialog) so the 200 ms repaint keeps it glued to the plane. */
    private void paintPlaneInfo(Graphics2D g2, List<SimulationEngine.PlaneView> planes) {
        if (selectedPlaneId == null) return;
        SimulationEngine.PlaneView p = null;
        for (SimulationEngine.PlaneView pv : planes) {
            if (pv.id.equals(selectedPlaneId)) {
                p = pv;
                break;
            }
        }
        if (p == null) {          // the plane landed while we were watching
            selectedPlaneId = null;
            return;
        }
        String[] lines = {
                "Flight: " + p.label,
                String.format("x: %.1f       y: %.1f", p.x, p.y),
                "Departure: " + p.scheduledDeparture
                        + (p.actualDeparture.equals(p.scheduledDeparture)
                           ? "" : " (actual " + p.actualDeparture + ")")
        };
        g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm = g2.getFontMetrics();
        int textW = 0;
        for (String s : lines) {
            textW = Math.max(textW, fm.stringWidth(s));
        }
        int boxW = textW + 12, boxH = fm.getHeight() * lines.length + 8;
        int bx = toScreenX(p.x) + PLANE_SIZE, by = toScreenY(p.y) + PLANE_SIZE;
        if (bx + boxW > getWidth()) bx = toScreenX(p.x) - boxW - PLANE_SIZE;   // keep the box
        if (by + boxH > getHeight()) by = toScreenY(p.y) - boxH - PLANE_SIZE;  // on screen
        g2.setColor(new Color(255, 255, 235));
        g2.fillRect(bx, by, boxW, boxH);
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(1));
        g2.drawRect(bx, by, boxW, boxH);
        int ty = by + fm.getAscent() + 4;
        for (String s : lines) {
            g2.drawString(s, bx + 6, ty);
            ty += fm.getHeight();
        }
    }
    // ===== [end MOD C1] =====================================================

    // ===== [MOD C2] storms: input + painting ================================
    // A held mouse button emits NO events, so growth is driven by
    // stormGrowTimer, bracketed by mousePressed (start) and mouseReleased (stop).
    // Storm data lives in the ENGINE (world coordinates, synchronized);
    // this panel only converts clicks and paints StormView copies.

    /** Right-click: delete the storm under the cursor, or create a new one. */
    private void handleStormPress(int px, int py) {
        double wx = toWorldX(px), wy = toWorldY(py);
        if (engine.removeStormAt(wx, wy)) {
            Log.debug("Map", "storm removed");
        } else {
            engine.addStorm(wx, wy);
            stormGrowTimer.start(); // grows until the button is released
        }
        repaint();
    }

    private void paintStorms(Graphics2D g2) {
        for (SimulationEngine.StormView s : engine.getStormViews()) {
            // y-flip again: the TOP of the circle is the LARGER world-y.
            int x1 = toScreenX(s.x - s.radius), x2 = toScreenX(s.x + s.radius);
            int y1 = toScreenY(s.y + s.radius), y2 = toScreenY(s.y - s.radius);
            g2.setColor(new Color(255, 0, 0, 50));       // translucent fill
            g2.fillOval(x1, y1, x2 - x1, y2 - y1);
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(1));
            g2.drawOval(x1, y1, x2 - x1, y2 - y1);
        }
    }
    // ===== [end MOD C2] =====================================================

    // ===== [MOD W1] route line of the selected plane ========================
    // Reuses C1's selection: one click, two independent painters.

        private void paintRouteLine(Graphics2D g2, List<SimulationEngine.PlaneView> planes) {
            if (selectedPlaneId == null) return;
            SimulationEngine.PlaneView p = findViewById(planes, selectedPlaneId);
            if (p == null) {              // landed; clear even when C1 is off
                selectedPlaneId = null;
                return;
            }
            g2.setColor(new Color(0, 140, 60));
            g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10, new float[]{6, 4}, 0));
            g2.drawLine(toScreenX(p.fromX), toScreenY(p.fromY),
                    toScreenX(p.toX),   toScreenY(p.toY));
        }
    // ===== [end MOD W1] =====================================================
    /** @return the current snapshot with that id, or null if it landed. */
    private SimulationEngine.PlaneView findViewById(
            List<SimulationEngine.PlaneView> planes, String id) {
        for (SimulationEngine.PlaneView v : planes) {
            if (v.id.equals(id)) return v;
        }
        return null;
    }

        // ===== [MOD W2] connectivity lines, two levels deep =====================
    // Computed on selection change and on world change - never during paint.
    // The walk is a 2-level BFS: expand the selected airport, then expand each
    // depth-1 neighbour. 'visited' keeps every airport in the picture exactly
    // once, so the result is a clean discovery tree, not a tangle.
    // Flights are treated as UNDIRECTED: a flight between two airports connects
    // them both ways ("povezan preko jednog leta", not "dostizan").
    private void recomputeConnectivity(){
        connectivityLines.clear();
        if(selected!=null){
            Set<Airport>visited=new HashSet<>();
            visited.add(selected);
            List<Airport>depth1=new ArrayList<>();
            collectiveNeighbours(selected,visited,depth1);
            for(Airport a:depth1){
                collectiveNeighbours(a,visited,null);
            }
            Log.debug("Map", "connectivity from " + selected.getCode()
                    + ": " + connectivityLines.size() + " line(s)");
        }
        repaint();
    }
    /** Adds a line and marks visited for every unvisited one-flight neighbour. */
    private void collectiveNeighbours(Airport from,Set<Airport>visited,List<Airport> collectInto){
        for(Flight f:world.getFlights()){
            String otherCode=null;
            if(f.getFrom().equals(from.getCode()))otherCode=f.getTo();
            else if(f.getTo().equals(from.getCode()))otherCode=f.getFrom();
            if(otherCode==null)continue;

            Airport other=world.findAirport(otherCode);
            if(other==null ||visited.contains(other)) continue;
            if(hiddenCodes.contains(otherCode))continue;
            visited.add(other);
            connectivityLines.add(new Airport[]{from,other});
            if (collectInto != null) collectInto.add(other);
        }
    }
    private void paintConnectivity(Graphics2D g2) {
        if(connectivityLines.isEmpty()) return;
        g2.setColor(new Color(120, 60, 200));
        g2.setStroke(new BasicStroke(2));
        for(Airport[]line :connectivityLines){
            g2.drawLine(toScreenX(line[0].getX()),toScreenY(line[0].getY()),toScreenX(line[1].getX()),toScreenY(line[1].getY()));
        }
    }

    // ===== [MOD X17] airport balance box ====================================
    // Same pattern as the C1 plane box: the ENGINE owns the numbers
    // (synchronized getter), the painter only draws them.
    private void paintAirportStats(Graphics2D g2) {
        if (selected == null) return;
        int[] s = engine.getAirportStats(selected.getCode());
        String[] lines = { selected.getCode() + ":",
                "takeoffs: " + s[0], "landings: " + s[1], "holding now: " + s[2] };
        g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm = g2.getFontMetrics();
        int w = 0;
        for (String l : lines) w = Math.max(w, fm.stringWidth(l));
        int bx = toScreenX(selected.getX()) + SQUARE_SIZE,
            by = toScreenY(selected.getY()) + SQUARE_SIZE;
        if (bx + w + 12 > getWidth()) bx = toScreenX(selected.getX()) - w - 12 - SQUARE_SIZE;
        if (by + fm.getHeight() * lines.length + 8 > getHeight()) {
            by = toScreenY(selected.getY()) - fm.getHeight() * lines.length - 8 - SQUARE_SIZE;
        }
        g2.setColor(new Color(235, 245, 255));
        g2.fillRect(bx, by, w + 12, fm.getHeight() * lines.length + 8);
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(1));
        g2.drawRect(bx, by, w + 12, fm.getHeight() * lines.length + 8);
        int ty = by + fm.getAscent() + 4;
        for (String l : lines) { g2.drawString(l, bx + 6, ty); ty += fm.getHeight(); }
    }
    // ===== [end MOD X17] ====================================================

    // ===== [MOD W3=close-airport / W4=redirect-plane] two-click ARMING ======
    // This is the "click a plane, then click the destination airport" redirect
    // (W4) and the "click an airport, then click its replacement" close (W3).
    // CTRL+click dispatch: planes win over airports (drawn on top), a click
    // on empty map just clears any armed state.
    private void handleTargetingArm(int px, int py) {
        if (Mods.W4_PLANE_REDIRECT) {
            SimulationEngine.PlaneView plane = findPlaneAt(px, py);
            if (plane != null) {
                redirectArmedPlaneId = plane.id;
                closeArmedAirport = null;
                Log.debug("Map", "two-click armed: choose target airport for " + plane.label);
                repaint();
                return;
            }
        }
        if (Mods.W3_REROUTING) {
            Airport a = findAirportAt(px, py);
            if (a != null) {
                if (engine.isAirportClosed(a.getCode())) {
                    engine.reopenAirport(a.getCode()); // CTRL+click a closed airport = reopen
                } else {
                    closeArmedAirport = a;
                    redirectArmedPlaneId = null;
                    Log.debug("Map", "two-click armed: choose replacement for closing "
                            + a.getCode());
                }
                repaint();
                return;
            }
        }
        redirectArmedPlaneId = null; // CTRL+click on empty map cancels
        closeArmedAirport = null;
        repaint();
    }

    /** Yellow instruction strip while a two-click action waits for its target. */
    private void paintTargetingHint(Graphics2D g2, List<SimulationEngine.PlaneView> planes) {
        // self-cleanup: the armed plane may have landed in the meantime
        if (redirectArmedPlaneId != null
                && findViewById(planes, redirectArmedPlaneId) == null) {
            redirectArmedPlaneId = null;
        }
        String hint = null;
        if (redirectArmedPlaneId != null) {
            hint = "Click an airport to redirect the plane (any other click cancels)";
        } else if (closeArmedAirport != null) {
            hint = "Click a replacement airport to close " + closeArmedAirport.getCode()
                    + " (any other click cancels)";
        }
        if (hint == null) return;
        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(new Color(255, 245, 180));
        g2.fillRect(8, 8, fm.stringWidth(hint) + 14, fm.getHeight() + 8);
        g2.setColor(Color.DARK_GRAY);
        g2.drawRect(8, 8, fm.stringWidth(hint) + 14, fm.getHeight() + 8);
        g2.drawString(hint, 15, 8 + fm.getAscent() + 4);
    }
    // ===== [end MOD W3+W4] ==================================================

    // ===== [MOD W4] manual plane redirect ===================================
    private void handlePlaneRedirect(int px, int py) {
        SimulationEngine.PlaneView plane=findPlaneAt(px,py);
        if(plane==null) return;
        java.util.List<String> options = new ArrayList<>();
        for (Airport a : world.getAirports()) {
            if (!engine.isAirportClosed(a.getCode())) options.add(a.getCode()); // [W3 interplay]
        }
        if (options.isEmpty()) return;

        Object choice = javax.swing.JOptionPane.showInputDialog(this,
                "Redirect flight " + plane.label + " to:", "Redirect plane",
                javax.swing.JOptionPane.QUESTION_MESSAGE, null,
                options.toArray(), options.get(0));
        if (choice == null) return; // user cancelled
        // The plane KEPT FLYING while the dialog was open (the engine never
        // stops) - redirect() starts the new leg from its CURRENT position,
        // and redirectPlane() returns false if it landed in the meantime.
        Airport target = world.findAirport((String) choice);
        if (!engine.redirectPlane(plane.id, target)) {
            Log.debug("Map", "redirect ignored: " + plane.label + " already landed");
        }
        repaint();
    }
    // ===== [end MOD W4] =====================================================

    // ===== [MOD X1] plane trails ============================================
    private void updateTrails(List<SimulationEngine.PlaneView> planes) {
        Set<String>alive=new HashSet<>();
        for(SimulationEngine.PlaneView p:planes){
            alive.add(p.id);
            Deque<double[]>trail=trails.computeIfAbsent(p.id,k->new ArrayDeque<>());
            double[]last=trail.peekLast();
            if(last==null || last[0]!=p.x || last[1]!=p.y) {
                trail.addLast(new double[]{p.x,p.y});
                if(trail.size()>TRAIL_LENGTH)trail.removeFirst();
            }
        }
        trails.keySet().retainAll(alive);// landed planes lose their trail
    }
    private void paintTrails(Graphics2D g2){
        for(Deque<double[]>trail:trails.values()){
            int i=0,n=trail.size();
            for(double[]pos:trail){
                int alpha=30+(int)(90.0* ++i/n);
                g2.setColor(new Color(0,0,255,alpha));
                g2.fillOval(toScreenX(pos[0])-2,toScreenY(pos[1])-2,4,4);
            }
        }
    }
    // ===== [end MOD X1] =====================================================

    // ===== [MOD X6] night overlay ===========================================
    private void paintNightOverlay(Graphics2D g2){
        int minuteOfDay=((int)engine.getSimMinutes())%(24*60);
        if(minuteOfDay<4*60){
            g2.setColor(new Color(10, 10, 60, 70)); // translucent night blue
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
    }
    // ===== [end MOD X6] =====================================================
    // ===== [MOD X14] day/night terminator ===================================
    // Local time at longitude x = sim time + x/15 hours (15 deg per hour).
    // Columns whose local time is outside 06-18h get the night shade.
    private void paintDayNight(Graphics2D g2) {
        double hours = (engine.getSimMinutes() / 60.0) % 24;
        g2.setColor(new Color(10, 10, 60, 50));
        for (int x = -180; x < 180; x += 5) {
            double local = (hours + (x + 2.5) / 15.0 + 24) % 24;
            if (local < 6 || local >= 18) {
                g2.fillRect(toScreenX(x), toScreenY(90),
                        toScreenX(x + 5) - toScreenX(x), toScreenY(-90) - toScreenY(90));
            }
        }
    }
    // ===== [end MOD X14] ====================================================

    // ===== [MOD X15] fog ====================================================
    private void handleFogPress(int px, int py) {
        double wx = toWorldX(px), wy = toWorldY(py);
        boolean removed = fogs.removeIf(f ->
                Math.hypot(wx - f[0], wy - f[1]) <= f[2]); // click inside = remove
        if (!removed) fogs.add(new double[]{wx, wy, FOG_RADIUS});
        Log.debug("Map", removed ? "fog removed" : "fog added");
        repaint();
    }

    private boolean isInFog(double wx, double wy) {
        for (double[] f : fogs) {
            if (Math.hypot(wx - f[0], wy - f[1]) <= f[2]) return true;
        }
        return false;
    }

    private void paintFog(Graphics2D g2) {
        for (double[] f : fogs) {
            int x1 = toScreenX(f[0] - f[2]), x2 = toScreenX(f[0] + f[2]);
            int y1 = toScreenY(f[1] + f[2]), y2 = toScreenY(f[1] - f[2]);
            g2.setColor(new Color(200, 200, 200, 120));
            g2.fillOval(x1, y1, x2 - x1, y2 - y1);
            g2.setColor(Color.GRAY);
            g2.drawOval(x1, y1, x2 - x1, y2 - y1);
        }
    }
    // ===== [end MOD X15] ====================================================
}
