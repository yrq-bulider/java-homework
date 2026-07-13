package handler;

import cluster.ClusterManager;
import protocol.Request;
import protocol.Response;
import store.NormalStore;

import java.util.Arrays;

/** CLUSTER command for querying cluster status. */
public class ClusterHandler implements CommandHandler {
    private final ClusterManager clusterManager;

    public ClusterHandler(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }

    @Override public String verb() { return "CLUSTER"; }

    @Override public Response handle(Request req, NormalStore store) {
        if (clusterManager == null)
            return Response.error("cluster mode not enabled");

        String sub = req.argCount() >= 1 ? req.args().get(0).toUpperCase() : "INFO";

        switch (sub) {
            case "INFO":
                return Response.multi(Arrays.asList(
                        "role:" + clusterManager.role(),
                        "term:" + clusterManager.currentTerm(),
                        "leader:" + (clusterManager.leaderId() != null ? clusterManager.leaderId() : "none"),
                        "isLeader:" + clusterManager.isLeader()
                ));
            case "ROLE":
                return Response.value(clusterManager.role().name());
            case "LEADER":
                return Response.value(clusterManager.leaderId() != null ? clusterManager.leaderId() : "(none)");
            default:
                return Response.error("unknown CLUSTER sub-command: " + sub);
        }
    }
}
