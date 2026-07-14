package client.gui;

import client.SocketClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class GuiClient extends JFrame {

    private final JTextField hostField = new JTextField("127.0.0.1", 10);
    private final JTextField portField = new JTextField("8080", 5);
    private final JTextArea resultArea = new JTextArea(10, 50);
    private final JTable kvTable = new JTable(new DefaultTableModel(new String[]{"键", "值", "类型"}, 0));
    private final JButton connectBtn = new JButton("连接");
    private final JButton sendBtn = new JButton("发送");
    private final JButton refreshBtn = new JButton("刷新");
    private final JLabel statusLabel = new JLabel("未连接");
    private final JLabel collLabel = new JLabel("");

    private final Map<String, String> commandMap = new LinkedHashMap<>();
    private final JComboBox<String> cmdCombo;

    private final JComboBox<String> typeCombo = new JComboBox<>(
            new String[]{"string", "number", "bool", "list", "map", "set"});

    // 单行输入模式
    private final JLabel label1 = new JLabel("Key:");
    private final JTextField field1 = new JTextField(12);
    private final JLabel label2 = new JLabel("Value:");
    private final JTextField field2 = new JTextField(12);
    private final JLabel label3 = new JLabel("TTL:");
    private final JTextField field3 = new JTextField(6);

    // 批量输入模式（文本域）
    private final JLabel batchLabel = new JLabel("批量: ");
    private final JTextArea batchArea = new JTextArea(4, 30);
    private final JScrollPane batchScroll = new JScrollPane(batchArea);
    private final JLabel batchHint = new JLabel("每行: key value  (空格分隔，一行一对)");

    private SocketClient client;
    private boolean connected;
    private String currentCollection = "";

    public GuiClient() {
        setTitle("easy-db GUI 客户端");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        initCommands();

        // -- Top: 连接面板 --
        JPanel connPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connPanel.add(new JLabel("主机:")); connPanel.add(hostField);
        connPanel.add(new JLabel("端口:")); connPanel.add(portField);
        connPanel.add(connectBtn); connPanel.add(statusLabel);
        connPanel.add(Box.createHorizontalStrut(20));
        connPanel.add(new JLabel("Collection:"));
        collLabel.setFont(collLabel.getFont().deriveFont(Font.BOLD));
        collLabel.setForeground(Color.GRAY); collLabel.setText("(默认)");
        connPanel.add(collLabel);
        add(connPanel, BorderLayout.NORTH);

        // -- Center --
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        JPanel cmdPanel = new JPanel(new BorderLayout(5, 5));

        // 输入区（单行 + 批量两套，按需切换显示）
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));

        // 上行: 命令 + 单行参数
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        cmdCombo = new JComboBox<>(commandMap.keySet().toArray(new String[0]));
        cmdCombo.setPreferredSize(new Dimension(150, 25));
        cmdCombo.addActionListener(this::onCommandChanged);
        row1.add(new JLabel("命令:")); row1.add(cmdCombo);
        row1.add(label1); row1.add(field1);
        row1.add(label2); row1.add(field2);
        row1.add(new JLabel("类型:")); row1.add(typeCombo);
        row1.add(label3); row1.add(field3);
        row1.add(sendBtn);

        // 下行: 批量输入区（默认隐藏）
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        batchArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        batchArea.setToolTipText("每行一对: key value，空格分隔");
        row2.add(batchLabel);
        row2.add(batchScroll);
        row2.add(batchHint);

        inputPanel.add(row1);
        inputPanel.add(row2);
        cmdPanel.add(inputPanel, BorderLayout.NORTH);

        // 结果
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(BorderFactory.createTitledBorder("响应结果"));
        cmdPanel.add(resultScroll, BorderLayout.CENTER);
        splitPane.setTopComponent(cmdPanel);

        // Bottom: 表
        kvTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        kvTable.getColumnModel().getColumn(1).setPreferredWidth(220);
        kvTable.getColumnModel().getColumn(2).setPreferredWidth(50);
        JScrollPane tableScroll = new JScrollPane(kvTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("键值浏览"));
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(refreshBtn, BorderLayout.NORTH);
        bottomPanel.add(tableScroll, BorderLayout.CENTER);
        splitPane.setBottomComponent(bottomPanel);
        splitPane.setDividerLocation(280);
        splitPane.setResizeWeight(0.6);
        add(splitPane, BorderLayout.CENTER);

        connectBtn.addActionListener(this::toggleConnection);
        sendBtn.addActionListener(this::sendCommand);
        refreshBtn.addActionListener(e -> refreshKeys());
        sendBtn.setEnabled(false); refreshBtn.setEnabled(false);
        onCommandChanged(null);

        pack(); setSize(900, 680); setLocationRelativeTo(null);
    }

    private void initCommands() {
        commandMap.put("PING   — 健康检查", "PING");
        commandMap.put("SET    — 设置键值", "SET");
        commandMap.put("GET    — 查询键值", "GET");
        commandMap.put("DEL    — 删除键", "DEL");
        commandMap.put("EXISTS — 判断存在", "EXISTS");
        commandMap.put("TYPE   — 值类型", "TYPE");
        commandMap.put("KEYS   — 匹配键名", "KEYS");
        commandMap.put("MGET   — 批量查询", "MGET");
        commandMap.put("MSET   — 批量设置", "MSET");
        commandMap.put("MDEL   — 批量删除", "MDEL");
        commandMap.put("MUPD   — 批量更新", "MUPD");
        commandMap.put("FLUSH  — 清空全部", "FLUSH");
        commandMap.put("USE    — 切换集合", "USE");
        commandMap.put("COLL. LIST", "COLLECTION LIST");
        commandMap.put("COLL. KEYS", "COLLECTION KEYS");
        commandMap.put("COLL. GET", "COLLECTION GET");
        commandMap.put("COLL. SET", "COLLECTION SET");
        commandMap.put("COLL. DEL", "COLLECTION DEL");
        commandMap.put("CLUSTER INFO", "CLUSTER INFO");
        commandMap.put("CLUSTER ROLE", "CLUSTER ROLE");
        commandMap.put("CLUSTER LEADER", "CLUSTER LEADER");
    }

    // ── 命令切换 ──

    private void onCommandChanged(ActionEvent e) {
        String verb = getSelectedVerb();
        // 隐藏所有
        hideAllSingle();
        typeCombo.setVisible(false);
        batchScroll.setVisible(false); batchLabel.setVisible(false); batchHint.setVisible(false);

        switch (verb) {
            case "PING": case "FLUSH":
            case "CLUSTER INFO": case "CLUSTER ROLE": case "CLUSTER LEADER":
            case "COLLECTION LIST":
                break;

            case "GET": case "DEL": case "EXISTS": case "TYPE":
                showSingle(label1, field1, "Key:", "输入 key");
                break;

            case "SET":
                showSingle(label1, field1, "Key:", "输入 key");
                showSingle(label2, field2, "Value:", "输入 value");
                showSingle(label3, field3, "TTL(秒):", "可选");
                typeCombo.setVisible(true);
                break;

            case "KEYS":
                showSingle(label1, field1, "Pattern:", "如 * 或 user:*");
                if (field1.getText().isEmpty() || field1.getText().equals("输入 key")) field1.setText("*");
                break;

            // ── 批量操作: 使用文本域 ──
            case "MSET": case "MUPD":
                showBatch("批量键值对:", "k1 v1\nk2 v2\nk3 v3", "每行一对, key value 空格分隔");
                showSingle(label3, field3, "TTL(秒):", "可选");
                typeCombo.setVisible(true);
                break;

            case "MGET": case "MDEL":
                showBatch("批量 Keys:", "k1\nk2\nk3", "每行一个 key");
                break;

            case "USE":
                showSingle(label1, field1, "集合名:", "* 退出");
                break;

            case "COLLECTION KEYS": case "COLLECTION GET": case "COLLECTION DEL":
                showSingle(label1, field1, "集合名:", "如 user");
                showSingle(label2, field2, "Key:", "输入 key");
                break;

            case "COLLECTION SET":
                showSingle(label1, field1, "集合名:", "如 user");
                showSingle(label2, field2, "Key:", "输入 key");
                showSingle(label3, field3, "Value:", "输入 value");
                typeCombo.setVisible(true);
                break;
        }
        pack();
    }

    private void hideAllSingle() {
        label1.setVisible(false); field1.setVisible(false);
        label2.setVisible(false); field2.setVisible(false);
        label3.setVisible(false); field3.setVisible(false);
    }

    private void showSingle(JLabel label, JTextField field, String text, String tooltip) {
        label.setText(text); label.setVisible(true);
        field.setVisible(true); field.setToolTipText(tooltip);
    }

    private void showBatch(String labelText, String placeholder, String hint) {
        batchLabel.setText(labelText);
        batchLabel.setVisible(true);
        batchArea.setText(placeholder);
        batchArea.setVisible(true);
        batchScroll.setVisible(true);
        batchHint.setText(hint);
        batchHint.setVisible(true);
    }

    // ── value 包装 ──

    private String wrapValue(String raw, String type) {
        if (raw == null || raw.isEmpty()) return raw;
        switch (type) {
            case "number": case "bool": return raw.trim();
            case "list":
                if (raw.startsWith("[")) return raw;
                StringBuilder lb = new StringBuilder("[");
                for (String s : raw.split(",")) {
                    if (lb.length() > 1) lb.append(",");
                    lb.append("\"").append(s.trim()).append("\"");
                }
                lb.append("]"); return lb.toString();
            case "set":
                if (raw.startsWith("(")) return raw;
                StringBuilder sb2 = new StringBuilder("(");
                for (String s : raw.split(",")) {
                    if (sb2.length() > 1) sb2.append(",");
                    sb2.append("\"").append(s.trim()).append("\"");
                }
                sb2.append(")"); return sb2.toString();
            case "map":
                if (raw.startsWith("{")) return raw;
                StringBuilder mb = new StringBuilder("{");
                for (String s : raw.split(",")) {
                    if (mb.length() > 1) mb.append(",");
                    String[] kv = s.split(":", 2);
                    mb.append("\"").append(kv[0].trim()).append("\":\"")
                      .append(kv.length > 1 ? kv[1].trim() : "").append("\"");
                }
                mb.append("}"); return mb.toString();
            default: return raw;
        }
    }

    private String inferTypeLabel(String wireValue) {
        if (wireValue == null || wireValue.equals("(nil)") || wireValue.startsWith("(error)")) return "-";
        String t = wireValue.trim();
        // 去掉外层引号
        if (t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
        // 现在 t 是真正的值内容
        if (t.startsWith("[") && t.endsWith("]")) return "list";
        if (t.startsWith("{") && t.endsWith("}")) return "map";
        if (t.startsWith("(") && t.endsWith(")")) return "set";
        if ("true".equals(t) || "false".equals(t)) return "bool";
        try { Double.parseDouble(t); return "number"; } catch (NumberFormatException ignored) {}
        return "string";
    }

    /** 格式化显示值：list→[], map→{}, set→(), string/number/bool 去掉引号 */
    private String formatDisplayValue(String wireValue) {
        if (wireValue == null || "(nil)".equals(wireValue)) return "(空)";
        if (wireValue.startsWith("(error)")) return wireValue;
        String t = wireValue.trim();
        // 去掉外层引号
        if (t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1).replace("\\\"", "\"");
        return t;
    }

    private String getSelectedVerb() {
        return commandMap.getOrDefault((String) cmdCombo.getSelectedItem(), "PING");
    }

    // ── 连接 ──

    private void toggleConnection(ActionEvent e) {
        if (connected) disconnect(); else connect();
    }

    private void connect() {
        try {
            client = new SocketClient(hostField.getText().trim(),
                    Integer.parseInt(portField.getText().trim()));
            client.connect();
            connected = true;
            currentCollection = "";
            collLabel.setText("(默认)"); collLabel.setForeground(Color.GRAY);
            connectBtn.setText("断开");
            statusLabel.setText("已连接"); statusLabel.setForeground(Color.GREEN.darker());
            sendBtn.setEnabled(true); refreshBtn.setEnabled(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "连接失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void disconnect() {
        try { if (client != null) client.close(); } catch (Exception ignored) {}
        connected = false;
        connectBtn.setText("连接"); statusLabel.setText("未连接"); statusLabel.setForeground(Color.RED);
        sendBtn.setEnabled(false); refreshBtn.setEnabled(false);
    }

    // ── 发送 ──

    private void sendCommand(ActionEvent e) {
        if (!connected) return;
        String verb = getSelectedVerb();
        String wireCmd = buildWireCommand();
        if (wireCmd == null) return;

        try {
            boolean multi = verb.equals("KEYS") || verb.equals("MGET")
                    || verb.startsWith("COLLECTION") || verb.startsWith("CLUSTER");
            String result = multi ? client.sendCommandMulti(wireCmd) : client.sendCommand(wireCmd);

            if ("USE".equals(verb) && result != null && result.contains("OK")) {
                String v = field1.getText().trim();
                currentCollection = v.equals("*") || v.isEmpty() ? "" : v;
                collLabel.setText(currentCollection.isEmpty() ? "(默认)" : currentCollection);
                collLabel.setForeground(currentCollection.isEmpty() ? Color.GRAY : Color.BLUE);
            }
            if ("FLUSH".equals(verb) && result != null && result.equals("OK")) {
                currentCollection = "";
                collLabel.setText("(默认)"); collLabel.setForeground(Color.GRAY);
            }
            resultArea.setText(result != null ? result : "(无响应)");
        } catch (IOException ex) {
            resultArea.setText("(错误) " + ex.getMessage());
        }
    }

    private String buildWireCommand() {
        String verb = getSelectedVerb();
        String f1 = field1.isVisible() ? field1.getText().trim() : "";
        String f2 = field2.isVisible() ? field2.getText().trim() : "";
        String f3 = field3.isVisible() ? field3.getText().trim() : "";

        switch (verb) {
            case "PING": return "PING";
            case "FLUSH": return "FLUSH";
            case "CLUSTER INFO": return "CLUSTER INFO";
            case "CLUSTER ROLE": return "CLUSTER ROLE";
            case "CLUSTER LEADER": return "CLUSTER LEADER";
            case "COLLECTION LIST": return "COLLECTION LIST";

            case "GET": case "DEL": case "EXISTS": case "TYPE":
                if (f1.isEmpty()) return null;
                return verb + " " + f1;

            case "SET":
                if (f1.isEmpty() || f2.isEmpty()) { warn("Key 和 Value 不能为空"); return null; }
                f2 = wrapValue(f2, (String) typeCombo.getSelectedItem());
                return f3.isEmpty() ? "SET " + f1 + " " + f2 : "SET " + f1 + " " + f2 + " " + f3;

            case "KEYS":
                return "KEYS " + (f1.isEmpty() ? "*" : f1);

            // 批量操作：从文本域读取
            case "MSET": case "MUPD":
                return buildBatchKV(verb, f3);
            case "MGET": case "MDEL":
                return buildBatchKeys(verb);

            case "USE":
                return "USE " + (f1.isEmpty() ? "*" : f1);

            case "COLLECTION KEYS": case "COLLECTION GET": case "COLLECTION DEL":
                if (f1.isEmpty() || f2.isEmpty()) return null;
                return verb + " " + f1 + " " + f2;

            case "COLLECTION SET":
                if (f1.isEmpty() || f2.isEmpty() || f3.isEmpty()) { warn("三项都不能为空"); return null; }
                f3 = wrapValue(f3, (String) typeCombo.getSelectedItem());
                return "COLLECTION SET " + f1 + " " + f2 + " " + f3;

            default: return verb;
        }
    }

    /** 从批量文本域构建 MSET/MUPD 命令：每行 "key value" */
    private String buildBatchKV(String verb, String ttl) {
        StringBuilder sb = new StringBuilder(verb);
        for (String line : batchArea.getText().split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts.length < 2) continue;
            String v = typeCombo.isVisible() ? wrapValue(parts[1], (String) typeCombo.getSelectedItem()) : parts[1];
            sb.append(" ").append(parts[0]).append(" ").append(v);
        }
        if (!ttl.isEmpty()) sb.append(" ").append(ttl);
        if (sb.length() == verb.length()) { warn("批量输入框为空"); return null; }
        return sb.toString();
    }

    /** 从批量文本域构建 MGET/MDEL 命令：每行一个 key */
    private String buildBatchKeys(String verb) {
        StringBuilder sb = new StringBuilder(verb);
        for (String line : batchArea.getText().split("\n")) {
            line = line.trim();
            if (!line.isEmpty()) sb.append(" ").append(line);
        }
        if (sb.length() == verb.length()) { warn("批量输入框为空"); return null; }
        return sb.toString();
    }

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "提示", JOptionPane.WARNING_MESSAGE);
    }

    private void refreshKeys() {
        if (!connected) return;
        try {
            DefaultTableModel model = (DefaultTableModel) kvTable.getModel();
            model.setRowCount(0);
            for (String line : client.sendCommandMulti("KEYS *").split("\n")) {
                if ("*END".equals(line)) break;
                String key = line.replaceAll("^\"|\"$", "").replace("\\\"", "\"");
                try {
                    String val = client.sendCommand("GET " + key);
                    model.addRow(new Object[]{key, formatDisplayValue(val), inferTypeLabel(val)});
                } catch (IOException ex) { model.addRow(new Object[]{key, "(错误)", "-"}); }
            }
            statusLabel.setText("已加载 " + model.getRowCount() + " 个键"); statusLabel.setForeground(Color.BLACK);
        } catch (Exception ex) { resultArea.setText("(错误) 刷新失败: " + ex.getMessage()); }
    }

    public static void main(String[] args) {
        String host = args.length >= 1 ? args[0] : "127.0.0.1";
        String port = args.length >= 2 ? args[1] : "8080";
        SwingUtilities.invokeLater(() -> {
            GuiClient gui = new GuiClient();
            gui.hostField.setText(host);
            gui.portField.setText(port);
            gui.setVisible(true);
        });
    }
}
