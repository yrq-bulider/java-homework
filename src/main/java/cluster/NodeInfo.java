package cluster;

/** Information about a cluster node. */
public class NodeInfo {
    public enum Role { LEADER, FOLLOWER, CANDIDATE }

    private final String nodeId;
    private final String host;
    private final int clientPort;
    private final int clusterPort;
    private volatile Role role;

    public NodeInfo(String nodeId, String host, int clientPort, int clusterPort) {
        this.nodeId = nodeId;
        this.host = host;
        this.clientPort = clientPort;
        this.clusterPort = clusterPort;
        this.role = Role.FOLLOWER;
    }

    public String nodeId() { return nodeId; }
    public String host() { return host; }
    public int clientPort() { return clientPort; }
    public int clusterPort() { return clusterPort; }
    public Role role() { return role; }
    public void setRole(Role r) { this.role = r; }

    @Override public String toString() {
        return String.format("%s(%s:%d)", nodeId, host, clientPort);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof NodeInfo)) return false;
        return nodeId.equals(((NodeInfo) o).nodeId);
    }

    @Override public int hashCode() { return nodeId.hashCode(); }
}
