package server;

import handler.DelHandler;
import handler.FlushHandler;
import handler.MdelHandler;
import handler.MsetHandler;
import handler.PingHandler;
import handler.QuitHandler;
import handler.SetHandler;
import store.Compactor;
import store.NormalStore;
import store.PersistentStore;
import util.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

public class EasyDbServer {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        Path dataDir = Paths.get("./data");
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) port = Integer.parseInt(args[++i]);
            else if ("--data".equals(args[i]) && i + 1 < args.length) dataDir = Paths.get(args[++i]);
        }

        PersistentStore persistentStore = new PersistentStore(dataDir);
        NormalStore store = new NormalStore(persistentStore);

        // Replay existing data
        PersistentStore.replay(dataDir, store);
        Logger.info("Replay complete. " + store.size() + " keys loaded.");

        Compactor compactor = new Compactor(dataDir);
        compactor.start();

        CommandDispatcher dispatcher = new CommandDispatcher(store)
                .register(new SetHandler())
                .register(new DelHandler())
                .register(new MsetHandler())
                .register(new MdelHandler())
                .register(new FlushHandler())
                .register(new PingHandler())
                .register(new QuitHandler());

        SocketServerController server = new SocketServerController(port, dispatcher);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutting down...");
            try { server.close(); } catch (Exception ignored) {}
            try { persistentStore.close(); } catch (Exception ignored) {}
            compactor.stop();
        }));

        server.start();
    }
}
