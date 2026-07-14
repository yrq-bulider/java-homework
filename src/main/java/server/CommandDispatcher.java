package server;

import cluster.ClusterManager;
import handler.CommandHandler;
import protocol.Request;
import protocol.Response;
import store.NormalStore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CommandDispatcher {
    private final Map<String, CommandHandler> handlers = new HashMap<>();
    private final NormalStore store;
    private ClusterManager clusterManager;

    private static final Set<String> WRITE_VERBS = new HashSet<>(Arrays.asList(
            "SET", "DEL", "MSET", "MDEL", "MUPD", "FLUSH"
    ));

    public CommandDispatcher(NormalStore store) {
        this.store = store;
    }

    public void setClusterManager(ClusterManager cm) {
        this.clusterManager = cm;
    }

    public CommandDispatcher register(CommandHandler handler) {
        handlers.put(handler.verb().toUpperCase(), handler);
        return this;
    }

    public Response dispatch(Request req) {
        CommandHandler h = handlers.get(req.verb());
        if (h == null) return Response.error("unknown command '" + req.verb() + "'");
        try {
            // Follower 拒绝写操作：仅做备份，不接受写入
            if (WRITE_VERBS.contains(req.verb()) && clusterManager != null) {
                if (!clusterManager.isLeader() && clusterManager.leaderId() != null) {
                    return Response.error("not the leader, current leader is " + clusterManager.leaderId());
                }
            }
            Response res = h.handle(req, store);
            // Leader 写操作成功后复制到 Follower
            if (WRITE_VERBS.contains(req.verb()) && clusterManager != null && clusterManager.isLeader()) {
                StringBuilder cmd = new StringBuilder(req.verb());
                for (String a : req.args()) cmd.append(" ").append(a);
                clusterManager.replicate(cmd.toString());
            }
            return res;
        } catch (RuntimeException e) {
            return Response.error(e.getMessage() == null ? "internal error" : e.getMessage());
        }
    }

    public boolean isQuit(Request req) { return req.isQuit(); }
}
