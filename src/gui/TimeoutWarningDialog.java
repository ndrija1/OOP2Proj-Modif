package gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

/**
 * The dialog required by phase A: during the last 5 seconds before the
 * automatic shutdown it tells the user how many seconds remain and offers a
 * button to keep working.
 *
 * The dialog is MODAL, but the countdown still advances while it is open:
 * the {@link logic.InactivityMonitor} is driven by a javax.swing.Timer, and a
 * modal dialog's event pump keeps dispatching timer events. MainFrame updates
 * the label through {@link #setSecondsRemaining(int)} on every tick.
 */
public class TimeoutWarningDialog extends JDialog {

    private final JLabel countdownLabel = new JLabel(" ", SwingConstants.CENTER);

    /**
     * @param onContinue invoked when the user chooses to keep working
     *                   (the caller resets the inactivity monitor)
     */
    public TimeoutWarningDialog(Frame owner, Runnable onContinue) {
        super(owner, "Are you still there?", true);
        // The user must answer with the button - closing via X is ignored,
        // otherwise the dialog could be dismissed without resetting the timer.
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(15, 20, 10, 20));

        content.add(new JLabel("No activity has been detected for a while.",
                SwingConstants.CENTER), BorderLayout.NORTH);
        countdownLabel.setFont(countdownLabel.getFont().deriveFont(16f));
        content.add(countdownLabel, BorderLayout.CENTER);

        JButton continueButton = new JButton("Yes, continue working");
        continueButton.addActionListener(e -> {
            onContinue.run();
            setVisible(false);
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttons.add(continueButton);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setSize(Math.max(getWidth(), 380), getHeight());
    }

    /** Updates the live countdown text; called once per second by MainFrame. */
    public void setSecondsRemaining(int seconds) {
        countdownLabel.setText("The application will close in " + seconds
                + (seconds == 1 ? " second." : " seconds."));
    }
}
