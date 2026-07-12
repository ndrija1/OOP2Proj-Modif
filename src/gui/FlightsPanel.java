package gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;

import model.Flight;
import model.ValidationException;
import model.World;
import util.TimeUtil;

/**
 * Phase A tab for flights: form on top, table of all flights below.
 *
 * Origin and destination are picked from combo boxes that always mirror the
 * current airport list - that way a flight to a non-existent airport cannot
 * even be typed (the model still re-validates, as a second line of defense).
 */
public class FlightsPanel extends JPanel {

    private final World world;
    private final logic.SimulationEngine engine; // [MOD X22]

    private final JComboBox<String> fromBox = new JComboBox<>();
    private final JComboBox<String> toBox = new JComboBox<>();
    private final JTextField departureField = new JTextField(5);
    private final JTextField durationField = new JTextField(5);
    private final JTable table;
    private final FlightTableModel tableModel;

    public FlightsPanel(World world,logic.SimulationEngine engine) {
        this.world = world;
        this.engine = engine;

        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // --- input form ------------------------------------------------------
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        form.add(new JLabel("From:"));
        form.add(fromBox);
        form.add(new JLabel("To:"));
        form.add(toBox);
        form.add(new JLabel("Departure (HH:MM):"));
        form.add(departureField);
        form.add(new JLabel("Duration (min):"));
        form.add(durationField);
        JButton addButton = new JButton("Add flight");
        addButton.addActionListener(e -> addFlight());
        form.add(addButton);
        add(form, BorderLayout.NORTH);

        // --- table -----------------------------------------------------------
        tableModel = new FlightTableModel();
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // [MOD X7] double-click a row to toggle that flight's priority
        if (util.Mods.X7_PRIORITY) {
            table.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        int row = table.rowAtPoint(e.getPoint());
                        if (row >= 0) world.togglePriority(world.getFlights().get(row));
                    }
                }
            });
        }

        // --- removal ---------------------------------------------------------
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton removeButton = new JButton("Remove selected flight");
        removeButton.addActionListener(e -> removeSelected());
        south.add(removeButton);
        add(south, BorderLayout.SOUTH);

        world.addListener(() -> {
            refreshAirportBoxes();
            tableModel.fireTableDataChanged();
        });
        refreshAirportBoxes();
    }

    private void addFlight() {
        try {
            String from = (String) fromBox.getSelectedItem();
            String to = (String) toBox.getSelectedItem();
            if (from == null || to == null) {
                throw new ValidationException(
                        "There are no airports yet. Add at least two airports on the "
                        + "Airports tab before creating a flight.");
            }
            int departure = TimeUtil.parseTimeOfDay(departureField.getText());
            int duration = parseDuration(durationField.getText());
            Flight added = new Flight(from, to, departure, duration);
            world.addFlight(added);
            // [MOD X22] if a run is in progress, inject it so it flies now
            if (util.Mods.X22_ADD_FLIGHT_LIVE) engine.addFlightLive(added);
            departureField.setText("");
            durationField.setText("");
        } catch (ValidationException ex) {
            Ui.error(this, ex.getMessage());
        }
    }

    private int parseDuration(String text) throws ValidationException {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException(
                    "Flight duration must not be empty. Enter the duration in minutes, e.g. 120.");
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new ValidationException(
                    "Flight duration must be a whole number of minutes, but \"" + trimmed
                    + "\" was entered.");
        }
    }

    private void removeSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            Ui.warn(this, "First select a flight in the table, then press Remove.");
            return;
        }
        world.removeFlight(row);
    }

    /** Keeps both combo boxes in sync with the airport list, preserving the choice. */
    private void refreshAirportBoxes() {
        String previousFrom = (String) fromBox.getSelectedItem();
        String previousTo = (String) toBox.getSelectedItem();

        DefaultComboBoxModel<String> fromModel = new DefaultComboBoxModel<>();
        DefaultComboBoxModel<String> toModel = new DefaultComboBoxModel<>();
        for (model.Airport a : world.getAirports()) {
            fromModel.addElement(a.getCode());
            toModel.addElement(a.getCode());
        }
        fromBox.setModel(fromModel);
        toBox.setModel(toModel);
        if (previousFrom != null) fromBox.setSelectedItem(previousFrom);
        if (previousTo != null) toBox.setSelectedItem(previousTo);
    }

    /** Read-only table view over the world's flight list. */
    private class FlightTableModel extends AbstractTableModel {
        private final String[] columns = { "From", "To", "Departure", "Duration (min)" };

        @Override public int getRowCount()    { return world.getFlights().size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c]; }

        @Override
        public Object getValueAt(int row, int col) {
            Flight f = world.getFlights().get(row);
            switch (col) {
                case 0:  return (util.Mods.X7_PRIORITY && world.isPriority(f)
                        ? "* " : "") + f.getFrom(); // [MOD X7] star = priority
                case 1:  return f.getTo();
                case 2:  return f.getDepartureText();
                default: return f.getDurationMinutes();
            }
        }
    }
}
