package cluster;

import util.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Cluster configuration, typically loaded from a JSON file. */
public class ClusterConfig {
    private final String nodeId;
    private final int clusterPort;
    private final List<NodeInfo> peers;
    private final long heartbeatIntervalMs;
    private final long electionTimeoutMinMs;
    private final long electionTimeoutMaxMs;

    public ClusterConfig(String nodeId, int clusterPort, List<NodeInfo> peers,
                         long heartbeatIntervalMs, long electionTimeoutMinMs, long electionTimeoutMaxMs) {
        this.nodeId = nodeId;
        this.clusterPort = clusterPort;
        this.peers = peers;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.electionTimeoutMinMs = electionTimeoutMinMs;
        this.electionTimeoutMaxMs = electionTimeoutMaxMs;
    }

    /** Load from JSON config file. */
    @SuppressWarnings("unchecked")
    public static ClusterConfig load(Path configFile) throws IOException {
        String json = new String(Files.readAllBytes(configFile), java.nio.charset.StandardCharsets.UTF_8);
        Map<String, Object> cfg = JsonUtil.fromJson(json, Map.class);
        String nodeId = (String) cfg.get("nodeId");
        int clusterPort = ((Number) cfg.get("clusterPort")).intValue();
        List<Map<String, Object>> peerList = (List<Map<String, Object>>) cfg.get("peers");
        List<NodeInfo> peers = new java.util.ArrayList<>();
        for (Map<String, Object> p : peerList) {
            peers.add(new NodeInfo(
                (String) p.get("nodeId"),
                (String) p.get("host"),
                ((Number) p.get("port")).intValue(),
                ((Number) p.get("clusterPort")).intValue()
            ));
        }
        return new ClusterConfig(nodeId, clusterPort, peers, 200, 300, 500);
    }

    public String nodeId() { return nodeId; }
    public int clusterPort() { return clusterPort; }
    public List<NodeInfo> peers() { return peers; }
    public long heartbeatIntervalMs() { return heartbeatIntervalMs; }
    public long electionTimeoutMinMs() { return electionTimeoutMinMs; }
    public long electionTimeoutMaxMs() { return electionTimeoutMaxMs; }

    /** Find this node in the peer list. */
    public NodeInfo self() {
        for (NodeInfo p : peers) if (p.nodeId().equals(nodeId)) return p;
        return null;
    }

    /** All peers except self. */
    public List<NodeInfo> otherPeers() {
        List<NodeInfo> others = new java.util.ArrayList<>();
        for (NodeInfo p : peers) if (!p.nodeId().equals(nodeId)) others.add(p);
        return others;
    }
}
