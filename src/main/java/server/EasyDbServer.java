package server;

import cluster.ClusterConfig;
import cluster.ClusterManager;
import handler.ClusterHandler;
import handler.CollectionCmdHandler;
import handler.DelHandler;
import handler.ExistsHandler;
import handler.FlushHandler;
import handler.GetHandler;
import handler.KeysHandler;
import handler.MdelHandler;
import handler.MgetHandler;
import handler.MsetHandler;
import handler.MupdHandler;
import handler.PingHandler;
import handler.QuitHandler;
import handler.SetHandler;
import handler.TypeHandler;
import store.Compactor;
import store.NormalStore;
import store.PersistentStore;
import store.lsm.LSMTree;
import util.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

public class EasyDbServer {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        Path dataDir = Paths.get("./data");
        boolean lsmMode = false;
        boolean clusterMode = false;
        Path clusterConfigPath = null;
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) port = Integer.parseInt(args[++i]);
            else if ("--data".equals(args[i]) && i + 1 < args.length) dataDir = Paths.get(args[++i]);
            else if ("--storage".equals(args[i]) && i + 1 < args.length) lsmMode = "lsm".equals(args[++i]);
            else if ("--cluster".equals(args[i])) clusterMode = true;
            else if ("--cluster-config".equals(args[i]) && i + 1 < args.length) clusterConfigPath = Paths.get(args[++i]);
        }

        PersistentStore persistentStore = new PersistentStore(dataDir);
        NormalStore store;
        LSMTree lsmTree = null;
        Compactor compactor = null;
        ClusterManager clusterManager = null;

        if (lsmMode) {
            // LSM-Tree mode
            lsmTree = new LSMTree(dataDir, persistentStore);
            PersistentStore.replay(dataDir, new NormalStore(persistentStore)); // replay into a temp to populate
            lsmTree.loadExistingSSTables();
            lsmTree.startCompaction();
            store = new NormalStore(lsmTree);
            Logger.info("LSM-Tree mode. Starting compaction...");
        } else {
            // Legacy mode: in-memory HashMap + JSON Lines
            store = new NormalStore(persistentStore);
            PersistentStore.replay(dataDir, store);
            Logger.info("Replay complete. " + store.size() + " keys loaded.");
            compactor = new Compactor(dataDir);
            compactor.start();
        }

        // Cluster mode
        if (clusterMode && clusterConfigPath != null) {
            ClusterConfig clusterCfg = ClusterConfig.load(clusterConfigPath);
            clusterManager = new ClusterManager(clusterCfg, store);
            clusterManager.start();
            Logger.info("Cluster mode enabled. Node: " + clusterCfg.nodeId());
        }

        CommandDispatcher dispatcher = new CommandDispatcher(store);
        if (clusterManager != null) dispatcher.setClusterManager(clusterManager);
        dispatcher.register(new SetHandler())
                .register(new GetHandler())
                .register(new MgetHandler())
                .register(new DelHandler())
                .register(new MsetHandler())
                .register(new MdelHandler())
                .register(new MupdHandler())
                .register(new CollectionCmdHandler())
                .register(new FlushHandler())
                .register(new KeysHandler())
                .register(new ExistsHandler())
                .register(new TypeHandler())
                .register(new PingHandler())
                .register(new QuitHandler())
                .register(new ClusterHandler(clusterManager));

        SocketServerController server = new SocketServerController(port, dispatcher);
        final Compactor finalCompactor = compactor;
        final LSMTree finalLsmTree = lsmTree;
        final ClusterManager finalClusterManager = clusterManager;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Shutting down...");
            try { server.close(); } catch (Exception ignored) {}
            try { persistentStore.close(); } catch (Exception ignored) {}
            if (finalCompactor != null) finalCompactor.stop();
            if (finalLsmTree != null) {
                try { finalLsmTree.close(); } catch (Exception ignored) {}
            }
            if (finalClusterManager != null) {
                try { finalClusterManager.close(); } catch (Exception ignored) {}
            }
        }));

        server.start();
    }
}
