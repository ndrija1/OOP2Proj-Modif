package app;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import gui.MainFrame;
import util.Log;

/**
 * Application entry point. All Swing components must be created on the
 * event-dispatch thread, hence the invokeLater.
 *
 * Useful flags:
 *   java -Dapp.debug=true app.Main   - verbose logging of every layer
 *   java -Dmods=all app.Main         - enable all practice modifications (MODS.md)
 *   java app.SelfTest                - run the headless self-test instead
 */
public final class Main {

    public static void main(String[] args) {
        try {
            // Native look so the app feels at home on the lab machines.
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            Log.error("Main", "could not set system look and feel, using default", e);
        }

        Log.info("Main", "starting (debug logging "
                + (Log.isDebugEnabled() ? "ON" : "off, enable with -Dapp.debug=true") + ")");
        util.Mods.logStatus(); // which practice modifications are active (see MODS.md)

        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
