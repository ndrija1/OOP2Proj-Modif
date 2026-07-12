package gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;

import model.Airport;
import model.ValidationException;
import model.World;

/**
 * Phase A tab for airports: an input form at the top, the table of all
 * airports in the middle, and a remove button at the bottom.
 *
 * All validation lives in the model ({@link Airport} and {@link World});
 * this panel only converts text fields to values and shows the model's error
 * messages to the user.
 */
public class AirportsPanel extends JPanel {

    private final World world;

    private final JTextField nameField = new JTextField(16);
    private final JTextField codeField = new JTextField(4);
    private final JTextField xField = new JTextField(6);
    private final JTextField yField = new JTextField(6);
    private final JTable table;
    private final AirportTableModel tableModel;

    public AirportsPanel(World world) {
        this.world = world;

        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // --- input form ------------------------------------------------------
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        form.add(new JLabel("Name:"));
        form.add(nameField);
        form.add(new JLabel("Code (3 letters):"));
        form.add(codeField);
        form.add(new JLabel("X (-180..180):"));
        form.add(xField);
        form.add(new JLabel("Y (-90..90):"));
        form.add(yField);
        JButton addButton = new JButton("Add airport");
        addButton.addActionListener(e -> addAirport());
        form.add(addButton);
        add(form, BorderLayout.NORTH);

        // --- table -----------------------------------------------------------
        tableModel = new AirportTableModel();
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // --- removal ---------------------------------------------------------
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton removeButton = new JButton("Remove selected airport");
        removeButton.addActionListener(e -> removeSelected());
        south.add(removeButton);
        add(south, BorderLayout.SOUTH);

        world.addListener(tableModel::fireTableDataChanged);
    }

    private void addAirport() {
        try {
            double x = parseCoordinate(xField.getText(), "X");
            double y = parseCoordinate(yField.getText(), "Y");
            world.addAirport(new Airport(nameField.getText(), codeField.getText(), x, y));
            // success: clear the form so the next entry is quick
            nameField.setText("");
            codeField.setText("");
            xField.setText("");
            yField.setText("");
            nameField.requestFocusInWindow();
        } catch (ValidationException ex) {
            Ui.error(this, ex.getMessage());
        }
    }

    /** Accepts both "10.5" and "10,5" so a decimal comma is not a trap. */
    private double parseCoordinate(String text, String axis) throws ValidationException {
        String trimmed = text == null ? "" : text.trim().replace(',', '.');
        if (trimmed.isEmpty()) {
            throw new ValidationException(axis + " coordinate must not be empty. "
                    + "Enter a number, e.g. " + (axis.equals("X") ? "10" : "45") + ".");
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            throw new ValidationException(
                    axis + " coordinate must be a number, but \"" + text.trim()
                    + "\" was entered.");
        }
    }

    private void removeSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            Ui.warn(this, "First select an airport in the table, then press Remove.");
            return;
        }
        Airport airport = world.getAirports().get(row);
        int dependentFlights = world.countFlightsUsing(airport.getCode());
        if (dependentFlights > 0) {
            boolean yes = Ui.confirm(this,
                    dependentFlights + " flight(s) use airport " + airport.getCode()
                    + " and will be removed together with it. Continue?");
            if (!yes) return;
        }
        world.removeAirport(row);
    }

    /** Read-only table view over the world's airport list. */
    private class AirportTableModel extends AbstractTableModel {
        private final String[] columns = { "Code", "Name", "X", "Y" };

        @Override public int getRowCount()    { return world.getAirports().size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c]; }

        @Override
        public Object getValueAt(int row, int col) {
            Airport a = world.getAirports().get(row);
            switch (col) {
                case 0:  return a.getCode();
                case 1:  return a.getName();
                case 2:  return a.getX();
                default: return a.getY();
            }
        }
    }
}
