package server;

import util.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketServerController implements AutoCloseable {
    private final int port;
    private final CommandDispatcher dispatcher;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    public SocketServerController(int port, CommandDispatcher dispatcher) {
        this.port = port;
        this.dispatcher = dispatcher;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        Logger.info("Server listening on port " + port);
        while (running) {
            try {
                Socket client = serverSocket.accept();
                threadPool.submit(new RequestHandler(client, dispatcher));
            } catch (IOException e) {
                if (running) Logger.warn("accept error: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        running = false;
        threadPool.shutdownNow();
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
    }
}
