package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import logic.InactivityMonitor;
import logic.SimulationEngine;
import model.Airport;
import model.ValidationException;
import model.World;

/**
 * The "Map & Simulation" tab: the map in the middle, the airport filter list
 * (phase B checkboxes) on the right, and the simulation controls + clock at
 * the bottom.
 *
 * Also the place where the two inactivity-pause rules are wired up:
 *  - countdown pauses while an airport is selected (phase B),
 *  - countdown pauses while the simulation is not stopped (phase C).
 */
public class MapTab extends JPanel {

    private final World world;
    private final SimulationEngine engine;
    private final InactivityMonitor inactivityMonitor;

    private final MapPanel mapPanel;
    private final JPanel filterList = new JPanel();

    private final JButton startButton = new JButton("Start");
    private final JButton pauseButton = new JButton("Pause");
    private final JButton resetButton = new JButton("Reset");
    private final JLabel clockLabel = new JLabel("00:00");
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton closeAirportButton = new JButton("Close airport...");// [MOD W3]
    private final JButton delayButton = new JButton("Delay +60 min"); // [MOD X4]
    private final javax.swing.JTextArea eventArea = new javax.swing.JTextArea(); // [MOD X16]

    public MapTab(World world, SimulationEngine engine, InactivityMonitor inactivityMonitor) {
        this.world = world;
        this.engine = engine;
        this.inactivityMonitor = inactivityMonitor;

        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // --- centre: the map; selection changes drive the inactivity pause ---
        mapPanel = new MapPanel(world, engine, selected ->{
                inactivityMonitor.setPaused("airport-selected", selected != null);
                if(util.Mods.W3_REROUTING) refreshCloseButton(selected);
                if (util.Mods.X4_DELAY) delayButton.setEnabled(selected != null);});// [MOD W3]
        // [MOD B2] rectangle selection freezes the countdown under its OWN
        // reason key, so it cannot cancel the single-selection pause.
        if (util.Mods.B2_RECT_SELECT) {
            mapPanel.setMultiSelectionListener(active ->
                    inactivityMonitor.setPaused("rect-selection", active));
        }
        add(mapPanel, BorderLayout.CENTER);

        // --- right: airport filter checkboxes -------------------------------
        filterList.setLayout(new BoxLayout(filterList, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(filterList,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(new TitledBorder("Airports on map"));
        scroll.setPreferredSize(new Dimension(280, 0));

        // [MOD X16] the filter list and the event log share the east side
        JPanel east = new JPanel(new BorderLayout(0, 6));
        east.add(scroll, BorderLayout.CENTER);
        if (util.Mods.X16_EVENT_LOG) {
            eventArea.setEditable(false);
            JScrollPane evScroll = new JScrollPane(eventArea);
            evScroll.setBorder(new TitledBorder("Events"));
            evScroll.setPreferredSize(new Dimension(280, 180));
            east.add(evScroll, BorderLayout.SOUTH);
        }
        add(east, BorderLayout.EAST);
        // --- bottom: controls + clock ---------------------------------------
        add(buildControlBar(), BorderLayout.SOUTH);

        world.addListener(this::rebuildFilterList);
        rebuildFilterList();
        refreshSimulationUi();
    }

    private JPanel buildControlBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));

        startButton.addActionListener(e -> startOrResume());
        pauseButton.addActionListener(e -> engine.pause());
        resetButton.addActionListener(e -> engine.reset());

        if(util.Mods.W3_REROUTING) {
            closeAirportButton.setEnabled(false);
            closeAirportButton.addActionListener(e -> closeOrReopenSelected());
            bar.add(closeAirportButton);
        }
        // [MOD X4] delay the selected airport's future departures
        if (util.Mods.X4_DELAY) {
            delayButton.setEnabled(false);
            delayButton.addActionListener(e -> {
                Airport sel = mapPanel.getSelected();
                if (sel != null) engine.delayAirport(sel.getCode(), 60);
            });
            bar.add(delayButton);
        }

        // [MOD X8] simulation speed buttons
        if (util.Mods.X8_SIM_SPEED) {
            for (double f : new double[]{0.5, 1, 2, 5}) {
                JButton speed = new JButton(f == (long) f ? (long) f + "x" : f + "x");
                speed.addActionListener(e -> engine.setSpeedFactor(f));
                bar.add(speed);
            }
        }

        // [MOD X23] rewind/scrub buttons: negative speed runs the clock back.
        // Self-contained (includes +1x/+2x) so it works without X8.
        if (util.Mods.X23_REWIND) {
            for (double f : new double[]{-2, -1, 1, 2}) {
                JButton b = new JButton((f > 0 ? "+" : "") + (long) f + "x");
                b.addActionListener(e -> engine.setSpeedFactor(f));
                bar.add(b);
            }
        }

        // [MOD X19] export the flight report
        if (util.Mods.X19_REPORT) {
            JButton reportButton = new JButton("Save report...");
            reportButton.addActionListener(e -> saveReport());
            bar.add(reportButton);
        }

        clockLabel.setFont(clockLabel.getFont().deriveFont(Font.BOLD, 20f));

        bar.add(startButton);
        bar.add(pauseButton);
        bar.add(resetButton);
        bar.add(Box.createHorizontalStrut(20));
        bar.add(new JLabel("Simulation time:"));
        bar.add(clockLabel);
        bar.add(Box.createHorizontalStrut(20));
        bar.add(statusLabel);
        return bar;
    }

    private void startOrResume() {
        try {
            if (engine.getState() == SimulationEngine.State.PAUSED) {
                engine.resume();
            } else {
                java.util.List<String> warnings = engine.start();
                if (!warnings.isEmpty()) {
                    Ui.warn(this, String.join("\n", warnings));
                }
            }
        } catch (ValidationException ex) {
            Ui.error(this, ex.getMessage());
        }
    }

    /**
     * Called (on the event-dispatch thread) after every engine tick and state
     * change: repaints the planes, updates the clock and the button states,
     * and keeps the inactivity countdown frozen while the simulation lives.
     */
    public void refreshSimulationUi() {
        SimulationEngine.State state = engine.getState();

        clockLabel.setText(util.TimeUtil.formatClock(engine.getSimMinutes()));
        if (state == SimulationEngine.State.STOPPED) {
            statusLabel.setText("Simulation stopped.");
        } else {
            String prefix = (state == SimulationEngine.State.PAUSED) ? "PAUSED  -  " : "";
            statusLabel.setText(prefix
                    + "planes in air: " + engine.getActiveCount()
                    + ", landed: " + engine.getCompletedFlights()
                    + " / " + engine.getTotalFlights());
            // [MOD X2] extended live statistics
            if (util.Mods.X2_STATS) {
                statusLabel.setText(statusLabel.getText()
                        + "  |  takeoffs: " + engine.getTakeoffCount()
                        + ", redirected: " + engine.getRedirectedCount()
                        + String.format(", avg delay: %.1f min", engine.getAverageDelayMinutes()));
            }
        }

        startButton.setText(state == SimulationEngine.State.PAUSED ? "Resume" : "Start");
        startButton.setEnabled(state != SimulationEngine.State.RUNNING);
        pauseButton.setEnabled(state == SimulationEngine.State.RUNNING);
        resetButton.setEnabled(state != SimulationEngine.State.STOPPED);

        // Phase C rule: the inactivity countdown is paused for the whole
        // lifetime of a simulation run (running or paused).
        inactivityMonitor.setPaused("simulation", state != SimulationEngine.State.STOPPED);

        // [MOD X16] newest event on top
        if (util.Mods.X16_EVENT_LOG) {
            java.util.List<String> ev = engine.getEvents();
            StringBuilder sb = new StringBuilder();
            for (int i = ev.size() - 1; i >= 0; i--) sb.append(ev.get(i)).append('\n');
            eventArea.setText(sb.toString());
        }

        mapPanel.repaint();
    }

    // ===== [MOD X19] flight report ==========================================
    // The ENGINE owns the data (synchronized getter with the CSV header);
    // this method only chooses the file and writes the lines.
    private void saveReport() {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setSelectedFile(new java.io.File("flight-report.csv"));
        if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        try {
            java.util.List<String> lines = engine.getReportLines();
            java.nio.file.Files.write(chooser.getSelectedFile().toPath(), lines,
                    java.nio.charset.StandardCharsets.UTF_8);
            Ui.info(this, "Report saved: " + (lines.size() - 1) + " completed flight(s).");
        } catch (java.io.IOException ex) {
            Ui.error(this, "Could not write the report: " + ex.getMessage()
                    + ". Check that the location is writable.");
        }
    }
    // ===== [end MOD X19] ====================================================

    /** Rebuilds the checkbox list whenever airports are added/removed/loaded. */
    private void rebuildFilterList() {
        filterList.removeAll();
        for (Airport a : world.getAirports()) {
            JCheckBox box = new JCheckBox(
                    a.getCode() + "  -  " + a.getName() + "  (" + a.getX() + ", " + a.getY() + ")",
                    mapPanel.isAirportVisible(a.getCode()));
            box.addActionListener(e ->
                    mapPanel.setAirportVisible(a.getCode(), box.isSelected()));
            filterList.add(box);
        }
        filterList.revalidate();
        filterList.repaint();
    }
    // ===== [MOD W3] close/reopen UI =========================================
    private void refreshCloseButton(Airport selected) {
        closeAirportButton.setEnabled(selected!=null);
        closeAirportButton.setText(selected!=null && engine.isAirportClosed(selected.getCode())?"Reopen: "+selected.getCode():"Close airport");
    }

    private void closeOrReopenSelected() {
        Airport sel=mapPanel.getSelected();
        if(sel==null) return;
        if(engine.isAirportClosed(sel.getCode())) {
            engine.reopenAirport(sel.getCode());
        }
        else{
            // choose the replacement among the OTHER airports
            java.util.List<String> options = new java.util.ArrayList<>();
            for(Airport a : world.getAirports()) {
                if (!a.getCode().equals(sel.getCode())
                        && !engine.isAirportClosed(a.getCode())) options.add(a.getCode());            }
            if(options.isEmpty()) {
                Ui.warn(this, "There is no other airport to redirect flights to.");
                return;
            }
            Object choice=javax.swing.JOptionPane.showInputDialog(this,
                    "Airport " + sel.getCode() + " is closing. Redirect its incoming flights to:",
                    "Close airport", javax.swing.JOptionPane.QUESTION_MESSAGE,
                    null, options.toArray(), options.get(0));
            if(choice==null) return;
            engine.closeAirport(sel.getCode(), world.findAirport((String)choice));
        }
        refreshCloseButton(sel);
        mapPanel.repaint();
    }
}
