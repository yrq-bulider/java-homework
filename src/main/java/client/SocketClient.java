package client;

import util.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SocketClient implements AutoCloseable {
    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    public SocketClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /** Send one command line and return the response (first line). */
    public String sendCommand(String command) throws IOException {
        if (socket == null) connect();
        out.write(command);
        out.newLine();
        out.flush();
        return in.readLine();
    }

    @Override
    public void close() {
        try { if (socket != null) socket.close(); } catch (IOException e) {
            Logger.warn("close: " + e.getMessage());
        }
    }
}
