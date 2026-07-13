package client.gui;

import client.SocketClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;

/**
 * easy-db Swing GUI 客户端
 */
public class GuiClient extends JFrame {

    private final JTextField hostField = new JTextField("127.0.0.1", 10);
    private final JTextField portField = new JTextField("9090", 5);
    private final JTextField cmdField = new JTextField(40);
    private final JTextArea resultArea = new JTextArea(12, 50);
    private final JTable kvTable = new JTable(new DefaultTableModel(new String[]{"键", "值"}, 0));
    private final JButton connectBtn = new JButton("连接");
    private final JButton sendBtn = new JButton("发送");
    private final JButton refreshBtn = new JButton("刷新");
    private final JLabel statusLabel = new JLabel("未连接");

    private SocketClient client;
    private boolean connected;

    public GuiClient() {
        setTitle("easy-db GUI 客户端");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        // -- Top: 连接面板 --
        JPanel connPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connPanel.add(new JLabel("主机:"));
        connPanel.add(hostField);
        connPanel.add(new JLabel("端口:"));
        connPanel.add(portField);
        connPanel.add(connectBtn);
        connPanel.add(statusLabel);
        add(connPanel, BorderLayout.NORTH);

        // -- Center: 命令 + 响应 --
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        JPanel cmdPanel = new JPanel(new BorderLayout());
        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        inputRow.add(new JLabel("命令:"));
        inputRow.add(cmdField);
        inputRow.add(sendBtn);
        inputRow.add(refreshBtn);
        cmdPanel.add(inputRow, BorderLayout.NORTH);
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(BorderFactory.createTitledBorder("响应结果"));
        cmdPanel.add(resultScroll, BorderLayout.CENTER);
        splitPane.setTopComponent(cmdPanel);

        // Bottom half: 键值浏览
        JScrollPane tableScroll = new JScrollPane(kvTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("键值浏览"));
        splitPane.setBottomComponent(tableScroll);
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.6);
        add(splitPane, BorderLayout.CENTER);

        // -- 事件绑定 --
        connectBtn.addActionListener(this::toggleConnection);
        sendBtn.addActionListener(this::sendCommand);
        refreshBtn.addActionListener(e -> refreshKeys());
        cmdField.addActionListener(this::sendCommand);

        // 初始状态：未连接时禁用发送和刷新
        sendBtn.setEnabled(false);
        refreshBtn.setEnabled(false);

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
            connectBtn.setText("断开");
            statusLabel.setText("已连接 " + host + ":" + port);
            statusLabel.setForeground(Color.GREEN.darker());
            sendBtn.setEnabled(true);
            refreshBtn.setEnabled(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "连接失败: " + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void disconnect() {
        try {
            if (client != null) client.close();
        } catch (Exception ignored) {}
        connected = false;
        connectBtn.setText("连接");
        statusLabel.setText("未连接");
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
            resultArea.setText(result != null ? result : "(无响应)");
        } catch (IOException ex) {
            resultArea.setText("(错误) " + ex.getMessage());
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
                    if ("(nil)".equals(displayVal)) displayVal = "(空)";
                    model.addRow(new Object[]{key, displayVal});
                } catch (IOException ex) {
                    model.addRow(new Object[]{key, "(错误)"});
                }
            }
            statusLabel.setText("已加载 " + model.getRowCount() + " 个键");
            statusLabel.setForeground(Color.BLACK);
        } catch (Exception ex) {
            resultArea.setText("(错误) 刷新失败: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GuiClient().setVisible(true));
    }
}
