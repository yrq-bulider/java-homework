package client.gui;

import client.SocketClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.List;

/**
 * Swing GUI client for easy-db.
 */
public class GuiClient extends JFrame {

    private final JTextField hostField = new JTextField("127.0.0.1", 10);
    private final JTextField portField = new JTextField("9090", 5);
    private final JTextField cmdField = new JTextField(40);
    private final JTextArea resultArea = new JTextArea(12, 50);
    private final JTable kvTable = new JTable(new DefaultTableModel(new String[]{"Key", "Value"}, 0));
    private final JButton connectBtn = new JButton("Connect");
    private final JButton sendBtn = new JButton("Send");
    private final JButton refreshBtn = new JButton("Refresh");
    private final JLabel statusLabel = new JLabel("Disconnected");

    private SocketClient client;
    private boolean connected;

    public GuiClient() {
        setTitle("easy-db GUI Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        // -- Top: Connection panel --
        JPanel connPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connPanel.add(new JLabel("Host:"));
        connPanel.add(hostField);
        connPanel.add(new JLabel("Port:"));
        connPanel.add(portField);
        connPanel.add(connectBtn);
        connPanel.add(statusLabel);
        add(connPanel, BorderLayout.NORTH);

        // -- Center: Split pane with command + result --
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        // Top half: command + result
        JPanel cmdPanel = new JPanel(new BorderLayout());
        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        inputRow.add(new JLabel("Command:"));
        inputRow.add(cmdField);
        inputRow.add(sendBtn);
        inputRow.add(refreshBtn);
        cmdPanel.add(inputRow, BorderLayout.NORTH);
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Response"));
        cmdPanel.add(resultScroll, BorderLayout.CENTER);
        splitPane.setTopComponent(cmdPanel);

        // Bottom half: key-value browser
        JScrollPane tableScroll = new JScrollPane(kvTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Key-Value Browser"));
        splitPane.setBottomComponent(tableScroll);
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.6);
        add(splitPane, BorderLayout.CENTER);

        // -- Actions --
        connectBtn.addActionListener(this::toggleConnection);
        sendBtn.addActionListener(this::sendCommand);
        refreshBtn.addActionListener(e -> refreshKeys());
        cmdField.addActionListener(this::sendCommand);

        pack();
        setSize(800, 600);
        setLocationRelativeTo(null);
    }

    private void toggleConnection(ActionEvent e) {
        if (connected) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        try {
            String host = hostField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            client = new SocketClient(host, port);
            client.connect();
            connected = true;
            connectBtn.setText("Disconnect");
            statusLabel.setText("Connected to " + host + ":" + port);
            statusLabel.setForeground(Color.GREEN.darker());
            sendBtn.setEnabled(true);
            refreshBtn.setEnabled(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Connection failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void disconnect() {
        try {
            if (client != null) client.close();
        } catch (Exception ignored) {}
        connected = false;
        connectBtn.setText("Connect");
        statusLabel.setText("Disconnected");
        statusLabel.setForeground(Color.RED);
        sendBtn.setEnabled(false);
        refreshBtn.setEnabled(false);
    }

    private void sendCommand(ActionEvent e) {
        if (!connected) return;
        String cmd = cmdField.getText().trim();
        if (cmd.isEmpty()) return;

        try {
            String result;
            if (cmd.toUpperCase().startsWith("KEYS")) {
                result = client.sendCommandMulti(cmd);
            } else {
                result = client.sendCommand(cmd);
            }
            resultArea.setText(result != null ? result : "(no response)");
        } catch (IOException ex) {
            resultArea.setText("(error) " + ex.getMessage());
        }
    }

    private void refreshKeys() {
        if (!connected) return;
        try {
            String result = client.sendCommandMulti("KEYS *");
            String[] lines = result.split("\n");
            DefaultTableModel model = (DefaultTableModel) kvTable.getModel();
            model.setRowCount(0);

            for (String line : lines) {
                if ("*END".equals(line)) break;
                String key = line.replaceAll("^\"|\"$", "").replace("\\\"", "\"");
                try {
                    String val = client.sendCommand("GET " + key);
                    String displayVal = val != null && val.startsWith("\"")
                            ? val.substring(1, val.length() - 1).replace("\\\"", "\"")
                            : val;
                    if ("(nil)".equals(displayVal)) displayVal = "(nil)";
                    model.addRow(new Object[]{key, displayVal});
                } catch (IOException ex) {
                    model.addRow(new Object[]{key, "(error)"});
                }
            }
            statusLabel.setText("Loaded " + model.getRowCount() + " keys");
            statusLabel.setForeground(Color.BLACK);
        } catch (Exception ex) {
            resultArea.setText("(error) refresh failed: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GuiClient().setVisible(true));
    }
}
