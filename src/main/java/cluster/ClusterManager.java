package cluster;

import store.NormalStore;
import util.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Core cluster manager: handles role transitions, heartbeat, election, and replication.
 * Implements a simplified Raft-like consensus protocol.
 */
public class ClusterManager implements AutoCloseable {

    private final ClusterConfig config;
    private final NormalStore store;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Raft state
    private final AtomicInteger currentTerm = new AtomicInteger(0);
    private final AtomicReference<String> votedFor = new AtomicReference<>(null);
    private final AtomicReference<String> leaderId = new AtomicReference<>(null);
    private final AtomicReference<NodeInfo.Role> role = new AtomicReference<>(NodeInfo.Role.FOLLOWER);
    private final AtomicLong lastHeartbeat = new AtomicLong(System.currentTimeMillis());
    private final Random random = new Random();

    private ServerSocket clusterServer;
    private volatile boolean running = true;

    // Track other nodes' vote responses during election
    private final ConcurrentHashMap<String, Boolean> votes = new ConcurrentHashMap<>();

    public ClusterManager(ClusterConfig config, NormalStore store) {
        this.config = config;
        this.store = store;
    }

    public void start() throws IOException {
        NodeInfo self = config.self();
        clusterServer = new ServerSocket(config.clusterPort());
        Logger.info("Cluster listening on port " + config.clusterPort() + " (node " + config.nodeId() + ")");

        // Accept cluster connections
        pool.submit(() -> {
            while (running) {
                try {
                    Socket s = clusterServer.accept();
                    pool.submit(() -> handleClusterConnection(s));
                } catch (IOException e) {
                    if (running) Logger.warn("cluster accept: " + e.getMessage());
                }
            }
        });

        // Election timeout watcher
        scheduler.scheduleAtFixedRate(this::checkElectionTimeout,
                config.electionTimeoutMaxMs(), 100, TimeUnit.MILLISECONDS);

        // Heartbeat sender (leader only)
        scheduler.scheduleAtFixedRate(this::sendHeartbeats,
                500, config.heartbeatIntervalMs(), TimeUnit.MILLISECONDS);

        Logger.info("Node " + config.nodeId() + " started as FOLLOWER");
    }

    // ---- Heartbeat ----

    private void sendHeartbeats() {
        if (role.get() != NodeInfo.Role.LEADER) return;
        for (NodeInfo peer : config.otherPeers()) {
            try {
                sendMessage(peer, "HEARTBEAT " + currentTerm.get() + " " + config.nodeId());
            } catch (IOException ignored) {}
        }
    }

    // ---- Election ----

    private void checkElectionTimeout() {
        if (role.get() == NodeInfo.Role.LEADER) return;
        long elapsed = System.currentTimeMillis() - lastHeartbeat.get();
        long timeout = config.electionTimeoutMinMs()
                + random.nextInt((int) (config.electionTimeoutMaxMs() - config.electionTimeoutMinMs()));
        if (elapsed > timeout) {
            startElection();
        }
    }

    private synchronized void startElection() {
        // Become CANDIDATE
        int term = currentTerm.incrementAndGet();
        role.set(NodeInfo.Role.CANDIDATE);
        votedFor.set(config.nodeId());
        leaderId.set(null);
        votes.clear();
        votes.put(config.nodeId(), true); // vote for self
        Logger.info("Starting election for term " + term);

        int voterCount = config.peers().size();
        int majority = voterCount / 2 + 1;

        for (NodeInfo peer : config.otherPeers()) {
            pool.submit(() -> {
                try {
                    String resp = sendMessage(peer, "VOTE_REQUEST " + term + " " + config.nodeId());
                    if (resp != null && resp.startsWith("VOTE_GRANTED")) {
                        int respTerm = Integer.parseInt(resp.split(" ")[1]);
                        if (respTerm == term) {
                            votes.put(peer.nodeId(), true);
                        }
                    }
                } catch (Exception ignored) {}
                // Check if we won
                if (role.get() == NodeInfo.Role.CANDIDATE
                        && countVotes() >= majority) {
                    becomeLeader(term);
                }
            });
        }
        // If only 1 node, immediately become leader
        if (voterCount <= 1) becomeLeader(term);
    }

    private int countVotes() {
        int yes = 0;
        for (Boolean v : votes.values()) if (v) yes++;
        return yes;
    }

    private void becomeLeader(int term) {
        if (role.get() != NodeInfo.Role.CANDIDATE) return;
        role.set(NodeInfo.Role.LEADER);
        leaderId.set(config.nodeId());
        currentTerm.set(term);
        Logger.info("Node " + config.nodeId() + " became LEADER for term " + term);
        // Immediately send heartbeat to establish authority
        sendHeartbeats();
    }

    private void stepDown(int higherTerm) {
        currentTerm.set(higherTerm);
        role.set(NodeInfo.Role.FOLLOWER);
        votedFor.set(null);
        Logger.info("Stepping down to FOLLOWER, term " + higherTerm);
    }

    // ---- Replication ----

    /** Leader replicates a write operation to all followers. */
    public void replicate(String opLine) {
        if (role.get() != NodeInfo.Role.LEADER) return;
        for (NodeInfo peer : config.otherPeers()) {
            pool.submit(() -> {
                try {
                    sendMessage(peer, "REPLICATE " + currentTerm.get() + " " + opLine);
                } catch (IOException ignored) {}
            });
        }
    }

    /** Forward a write command from a follower to the leader. */
    public String forwardToLeader(String command) throws IOException {
        String leader = leaderId.get();
        if (leader == null || leader.equals(config.nodeId())) return null;
        for (NodeInfo peer : config.peers()) {
            if (peer.nodeId().equals(leader)) {
                return sendMessage(peer, "FORWARD " + command);
            }
        }
        return null;
    }

    // ---- Cluster connection handler ----

    private void handleClusterConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null && running) {
                String[] parts = line.split(" ", 3);
                String verb = parts[0];

                switch (verb) {
                    case "HEARTBEAT": {
                        int term = Integer.parseInt(parts[1]);
                        String ldrId = parts[2];
                        if (term >= currentTerm.get()) {
                            lastHeartbeat.set(System.currentTimeMillis());
                            leaderId.set(ldrId);
                            if (role.get() == NodeInfo.Role.CANDIDATE || role.get() == NodeInfo.Role.LEADER) {
                                if (term > currentTerm.get()) stepDown(term);
                            }
                            role.set(NodeInfo.Role.FOLLOWER);
                        }
                        break;
                    }
                    case "VOTE_REQUEST": {
                        int term = Integer.parseInt(parts[1]);
                        String candidateId = parts[2];
                        if (term > currentTerm.get()) {
                            stepDown(term);
                        }
                        boolean grant = term >= currentTerm.get()
                                && (votedFor.get() == null || votedFor.get().equals(candidateId));
                        if (grant) {
                            votedFor.set(candidateId);
                            out.write("VOTE_GRANTED " + term); out.newLine(); out.flush();
                        }
                        break;
                    }
                    case "REPLICATE": {
                        int term = Integer.parseInt(parts[1]);
                        String opData = parts.length > 2 ? parts[2] : "";
                        if (term >= currentTerm.get()) {
                            lastHeartbeat.set(System.currentTimeMillis());
                            // Apply the operation locally
                            applyReplicatedOp(opData);
                        }
                        break;
                    }
                    case "FORWARD": {
                        // Leader receives forwarded command from a follower
                        String cmd = parts.length > 2 ? parts[2] : "";
                        String result = applyClientCommand(cmd);
                        out.write(result); out.newLine(); out.flush();
                        break;
                    }
                }
            }
        } catch (IOException e) {
            // connection lost
        }
    }

    private void applyReplicatedOp(String opData) {
        // opData format: "SET key value 0" or "DEL key" etc.
        try {
            String[] tokens = opData.split(" ");
            String verb = tokens[0].toUpperCase();
            switch (verb) {
                case "SET":
                    if (tokens.length >= 3) {
                        long ttl = tokens.length >= 4 ? Long.parseLong(tokens[3]) : 0;
                        store.set(tokens[1], tokens[2], ttl);
                    }
                    break;
                case "DEL":
                    if (tokens.length >= 2) store.del(tokens[1]);
                    break;
                case "FLUSH":
                    store.flush();
                    break;
            }
        } catch (Exception e) {
            Logger.warn("replication apply failed: " + e.getMessage());
        }
    }

    private String applyClientCommand(String cmd) {
        // Simplified: delegate to store via protocol cycle
        // In production, this would parse and dispatch properly
        try {
            String[] tokens = cmd.split(" ");
            String verb = tokens[0].toUpperCase();
            switch (verb) {
                case "SET":
                    long ttl = tokens.length >= 4 ? Long.parseLong(tokens[3]) : 0;
                    store.set(tokens[1], tokens[2], ttl);
                    return "OK";
                case "DEL":
                    return "(integer) " + (store.del(tokens[1]) ? 1 : 0);
                case "FLUSH":
                    store.flush();
                    return "OK";
                default:
                    return "(error) ERR unknown command";
            }
        } catch (Exception e) {
            return "(error) ERR " + e.getMessage();
        }
    }

    // ---- Utility ----

    private String sendMessage(NodeInfo peer, String message) throws IOException {
        try (Socket s = new Socket(peer.host(), peer.clusterPort());
             BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            w.write(message); w.newLine(); w.flush();
            s.setSoTimeout(2000);
            return r.readLine();
        }
    }

    public boolean isLeader() { return role.get() == NodeInfo.Role.LEADER; }
    public String leaderId() { return leaderId.get(); }
    public int currentTerm() { return currentTerm.get(); }
    public NodeInfo.Role role() { return role.get(); }

    @Override
    public void close() throws Exception {
        running = false;
        scheduler.shutdownNow();
        pool.shutdownNow();
        if (clusterServer != null) clusterServer.close();
    }
}
