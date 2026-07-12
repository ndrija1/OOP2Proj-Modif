package gui;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

import io.DataFileException;
import io.FileService;
import io.LoadedData;
import logic.InactivityMonitor;
import logic.SimulationEngine;
import model.ValidationException;
import model.World;
import util.Log;

/**
 * The application window. Owns the three long-lived objects (the {@link World},
 * the {@link SimulationEngine} and the {@link InactivityMonitor}) and wires
 * them to the tabs; everything else communicates through those objects'
 * listener interfaces, never directly between panels.
 */
public class MainFrame extends JFrame {

    private final World world = new World();
    private final SimulationEngine engine;
    private final InactivityMonitor inactivityMonitor;

    private final MapTab mapTab;
    private TimeoutWarningDialog warningDialog; // created lazily on first warning

    public MainFrame() {
        super("Air Traffic Simulator  (OOP2 2025/26)");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(1280, 720);
        setLocationByPlatform(true);

        // --- engine: ticks arrive on a background thread, hop to the EDT ----
        engine = new SimulationEngine(world,
                () -> SwingUtilities.invokeLater(this::onSimulationUpdated));

        // --- inactivity monitor (phase A auto-shutdown) ----------------------
        inactivityMonitor = new InactivityMonitor(new InactivityMonitor.Listener() {
            @Override
            public void warning(int secondsRemaining) {
                showOrUpdateWarning(secondsRemaining);
            }

            @Override
            public void timedOut() {
                Log.info("MainFrame", "closing due to inactivity");
                shutdown();
            }
        });

        // --- tabs ------------------------------------------------------------
        mapTab = new MapTab(world, engine, inactivityMonitor);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Map & Simulation", mapTab);
        tabs.addTab("Airports", new AirportsPanel(world));
        tabs.addTab("Flights", new FlightsPanel(world,engine));
        setContentPane(tabs);

        setJMenuBar(buildMenuBar());

        // Any key press or mouse press/wheel anywhere in the application counts
        // as user activity and refills the 60-second inactivity budget.
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                boolean isActivity =
                        event.getID() == MouseEvent.MOUSE_PRESSED
                     || event.getID() == MouseEvent.MOUSE_WHEEL
                     || event.getID() == KeyEvent.KEY_PRESSED;
                if (!isActivity) return;
                // While the warning dialog is open, only its "continue" button
                // may extend the session - random clicks must not.
                if (warningDialog != null && warningDialog.isVisible()) return;
                Log.debug("MainFrame", "activity event: " + event.paramString()
                        + " on " + event.getSource().getClass().getSimpleName());
                inactivityMonitor.reset();
            }
        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        inactivityMonitor.start();
    }

    // -------------------------------------------------------------- menu bar

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");

        JMenuItem open = new JMenuItem("Open (CSV or JSON)...");
        open.addActionListener(e -> openFile());
        JMenuItem saveCsv = new JMenuItem("Save as CSV...");
        saveCsv.addActionListener(e -> saveFile("csv"));
        JMenuItem saveJson = new JMenuItem("Save as JSON...");
        saveJson.addActionListener(e -> saveFile("json"));
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> shutdown());

        file.add(open);
        file.addSeparator();
        file.add(saveCsv);
        file.add(saveJson);
        file.addSeparator();
        file.add(exit);
        bar.add(file);
        return bar;
    }

    // ------------------------------------------------------------ file menu

    private void openFile() {
        JFileChooser chooser = newChooser();
        chooser.setDialogTitle("Open data file");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            LoadedData data = FileService.load(file.toPath());
            world.replaceAll(data.airports, data.flights);
            engine.reset(); // a running simulation would show stale data
            Ui.info(this, "Loaded " + data.airports.size() + " airports and "
                    + data.flights.size() + " flights from \"" + file.getName() + "\".");
        } catch (DataFileException | ValidationException ex) {
            Ui.error(this, ex.getMessage());
        }
    }

    private void saveFile(String extension) {
        JFileChooser chooser = newChooser();
        chooser.setDialogTitle("Save data as ." + extension);
        chooser.setSelectedFile(new File("data." + extension));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith("." + extension)) {
            file = new File(file.getParentFile(), file.getName() + "." + extension);
        }
        if (file.exists() && !Ui.confirm(this,
                "The file \"" + file.getName() + "\" already exists. Overwrite it?")) {
            return;
        }
        try {
            FileService.save(file.toPath(), world.getAirports(), world.getFlights());
            Ui.info(this, "Saved " + world.getAirports().size() + " airports and "
                    + world.getFlights().size() + " flights to \"" + file.getName() + "\".");
        } catch (DataFileException ex) {
            Ui.error(this, ex.getMessage());
        }
    }

    private JFileChooser newChooser() {
        // Start in the bundled data folder when it exists - the test files live there.
        File dataDir = new File("data");
        JFileChooser chooser = new JFileChooser(dataDir.isDirectory() ? dataDir : null);
        chooser.setFileFilter(new FileNameExtensionFilter("Data files (*.csv, *.json)",
                "csv", "json"));
        return chooser;
    }

    // ----------------------------------------------------- inactivity dialog

    private void showOrUpdateWarning(int secondsRemaining) {
        if (warningDialog == null) {
            warningDialog = new TimeoutWarningDialog(this, inactivityMonitor::reset);
        }
        warningDialog.setSecondsRemaining(secondsRemaining);
        if (!warningDialog.isVisible()) {
            warningDialog.setLocationRelativeTo(this);
            // CAREFUL: this method runs inside the monitor's swing-timer tick.
            // Modal setVisible(true) blocks until the dialog closes, and a
            // swing timer does not fire again until the previous tick RETURNS
            // (event coalescing) - calling setVisible directly here would
            // freeze the countdown at 5s forever. Deferring the call with
            // invokeLater lets this tick finish first; the timer then keeps
            // ticking through the modal dialog's event pump, updating the
            // label and firing the timeout at zero.
            SwingUtilities.invokeLater(() -> {
                // If the user acted in the meantime the warning is obsolete.
                if (inactivityMonitor.getRemainingSeconds()
                        <= logic.InactivityMonitor.WARNING_SECONDS) {
                    warningDialog.setVisible(true); // blocks here, NOT in the tick
                }
            });
        }
    }

    // --------------------------------------------------------------- engine

    private void onSimulationUpdated() {
        mapTab.refreshSimulationUi();
    }

    // ------------------------------------------------------------- shutdown

    private void shutdown() {
        Log.info("MainFrame", "shutting down");
        engine.reset();
        inactivityMonitor.stop();
        dispose();
        System.exit(0);
    }
}
