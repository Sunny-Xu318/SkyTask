package com.skytask.service;

import com.skytask.config.SchedulerDynamicProperties;
import com.skytask.dto.HeartbeatResponse;
import com.skytask.dto.NodeMetricsResponse;
import com.skytask.exception.ResourceNotFoundException;
import com.skytask.model.NodeInfo;
import com.skytask.model.NodeStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NodeService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration HEARTBEAT_WINDOW = Duration.ofMinutes(5);

    private final SchedulerDynamicProperties properties;
    private final MeterRegistry meterRegistry;

    private final Map<String, NodeRuntime> nodes = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> shardAssignments = new ConcurrentHashMap<>();
    private final Counter shardMigrationCounter;
    private final Counter nodeAutoOfflineCounter;

    public NodeService(SchedulerDynamicProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.shardMigrationCounter = meterRegistry.counter("skytask_shard_migrations_total");
        this.nodeAutoOfflineCounter = meterRegistry.counter("skytask_nodes_auto_offline_total");
    }

    @PostConstruct
    public void seedNodes() {
        if (!nodes.isEmpty()) {
            return;
        }
        registerNode(
                NodeInfo.builder()
                        .id("worker-001")
                        .name("worker-001")
                        .cluster("default")
                        .host("10.0.0.11:9200")
                        .status(NodeStatus.ONLINE)
                        .cpu(45)
                        .memory(58)
                        .runningTasks(34)
                        .backlog(2)
                        .delay(120)
                        .registerTime(OffsetDateTime.now().minusDays(12))
                        .alertLevel("NORMAL")
                        .lastHeartbeat(OffsetDateTime.now().minusSeconds(5))
                        .build(),
                Set.of("alpha-nightly-report#0", "alpha-cache-refresh#0"));

        registerNode(
                NodeInfo.builder()
                        .id("worker-002")
                        .name("worker-002")
                        .cluster("default")
                        .host("10.0.0.12:9200")
                        .status(NodeStatus.ONLINE)
                        .cpu(63)
                        .memory(70)
                        .runningTasks(27)
                        .backlog(4)
                        .delay(98)
                        .registerTime(OffsetDateTime.now().minusDays(9))
                        .alertLevel("NORMAL")
                        .lastHeartbeat(OffsetDateTime.now().minusSeconds(7))
                        .build(),
                Set.of("alpha-nightly-report#1", "beta-order-sync#0"));

        registerNode(
                NodeInfo.builder()
                        .id("worker-003")
                        .name("worker-003")
                        .cluster("default")
                        .host("10.0.0.13:9200")
                        .status(NodeStatus.OFFLINE)
                        .cpu(0)
                        .memory(0)
                        .runningTasks(0)
                        .backlog(0)
                        .delay(0)
                        .registerTime(OffsetDateTime.now().minusDays(4))
                        .alertLevel("DEGRADED")
                        .lastHeartbeat(OffsetDateTime.now().minusMinutes(10))
                        .build(),
                Set.of("beta-order-sync#1"));
    }

    private NodeRuntime registerNode(NodeInfo info, Set<String> shards) {
        NodeRuntime runtime = new NodeRuntime(info);
        nodes.put(info.getId(), runtime);
        shardAssignments.putIfAbsent(info.getId(), ConcurrentHashMap.newKeySet());
        if (shards != null && !shards.isEmpty()) {
            shardAssignments.get(info.getId()).addAll(shards);
        }
        return runtime;
    }

    public List<NodeInfo> listNodes() {
        return nodes.values().stream().map(this::toView).collect(Collectors.toList());
    }

    public NodeMetricsResponse nodeMetrics() {
        List<NodeInfo> nodeList = listNodes();
        long total = nodeList.size();
        long online = nodeList.stream().filter(node -> node.getStatus() == NodeStatus.ONLINE).count();
        long offline = total - online;
        double avgCpu = nodeList.stream().mapToInt(NodeInfo::getCpu).average().orElse(0.0);
        double avgMemory = nodeList.stream().mapToInt(NodeInfo::getMemory).average().orElse(0.0);
        return NodeMetricsResponse.builder()
                .totalNodes(total)
                .onlineNodes(online)
                .offlineNodes(offline)
                .avgCpu(Math.round(avgCpu * 10.0) / 10.0)
                .avgMemory(Math.round(avgMemory * 10.0) / 10.0)
                .build();
    }

    public NodeInfo offline(String nodeId) {
        NodeRuntime runtime = requireNode(nodeId);
        NodeInfo updated = runtime.update(info -> info.toBuilder()
                .status(NodeStatus.OFFLINE)
                .cpu(0)
                .memory(0)
                .runningTasks(0)
                .backlog(0)
                .delay(0)
                .alertLevel("MANUAL_OFFLINE")
                .lastHeartbeat(OffsetDateTime.now())
                .build());
        migrateShardsFromNode(nodeId);
        return toView(runtime);
    }

    public NodeInfo rebalance(String nodeId) {
        NodeRuntime runtime = requireNode(nodeId);
        rebalanceShards(nodeId);
        runtime.update(info -> info.toBuilder()
                .alertLevel("REBALANCING")
                .delay(Math.max(60, info.getDelay() - 20))
                .runningTasks(Math.max(0, info.getRunningTasks() - 5))
                .build());
        return toView(runtime);
    }

    public HeartbeatResponse heartbeat(String nodeId) {
        NodeRuntime runtime = nodes.computeIfAbsent(nodeId, id -> registerNode(
                NodeInfo.builder()
                        .id(id)
                        .name(id)
                        .cluster("default")
                        .host("unknown")
                        .status(NodeStatus.ONLINE)
                        .cpu(0)
                        .memory(0)
                        .runningTasks(0)
                        .backlog(0)
                        .delay(0)
                        .registerTime(OffsetDateTime.now())
                        .alertLevel("NORMAL")
                        .lastHeartbeat(OffsetDateTime.now())
                        .build(),
                Collections.emptySet()));

        ThreadLocalRandom random = ThreadLocalRandom.current();
        runtime.markHeartbeat(random.nextInt(35, 80), random.nextInt(40, 85));

        List<Map<String, Object>> logs = random.ints(0, 5)
                .limit(6)
                .mapToObj(i -> Map.<String, Object>of(
                        "time", OffsetDateTime.now().minusMinutes(i * 5L).format(TIME_FORMAT),
                        "status", i == 0 ? "success" : (i == 3 ? "warning" : "info"),
                        "message", i == 3 ? "心跳延迟升高" : "节点健康检查正�?"))
                .collect(Collectors.toList());

        return HeartbeatResponse.builder()
                .nodeId(nodeId)
                .name(runtime.current().getName())
                .latest(OffsetDateTime.now().format(DATE_TIME_FORMAT))
                .avgLatency(random.nextDouble(120, 320))
                .lastAlert(runtime.current().getAlertLevel())
                .logs(logs)
                .build();
    }

    @Scheduled(fixedDelayString = "${skytask.scheduler.nodes.health-check-interval-ms:30000}")
    public void checkHealth() {
        Instant now = Instant.now();
        long timeoutSeconds = properties.getNodes().getHeartbeatTimeoutSeconds();
        for (Map.Entry<String, NodeRuntime> entry : nodes.entrySet()) {
            NodeRuntime runtime = entry.getValue();
            if (runtime.isOnline() && runtime.heartbeatAge(now) > timeoutSeconds) {
                log.warn("Node {} missed heartbeat for {} seconds, marking offline", entry.getKey(), runtime.heartbeatAge(now));
                runtime.update(info -> info.toBuilder()
                        .status(NodeStatus.OFFLINE)
                        .alertLevel("AUTO_OFFLINE")
                        .build());
                migrateShardsFromNode(entry.getKey());
                nodeAutoOfflineCounter.increment();
            }
        }
    }

    private void rebalanceShards(String nodeId) {
        Set<String> shards = shardAssignments.getOrDefault(nodeId, Collections.emptySet());
        if (shards.isEmpty()) {
            return;
        }
        List<String> candidateNodes = nodes.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(nodeId) && entry.getValue().isOnline())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (candidateNodes.isEmpty()) {
            log.warn("No candidate nodes available to rebalance shards from {}", nodeId);
            return;
        }
        Iterator<String> iterator = cycle(candidateNodes);
        int migrations = 0;
        List<String> shardsToMove = new ArrayList<>(shards);
        int shardsToReassign = Math.max(1, shardsToMove.size() / 3);
        for (int i = 0; i < shardsToReassign; i++) {
            String shard = shardsToMove.get(i);
            String target = iterator.next();
            shardAssignments.get(nodeId).remove(shard);
            shardAssignments.computeIfAbsent(target, k -> ConcurrentHashMap.newKeySet()).add(shard);
            migrations++;
        }
        if (migrations > 0) {
            shardMigrationCounter.increment(migrations);
            log.info("Manually rebalanced {} shards from node {} to {}", migrations, nodeId, candidateNodes);
        }
    }

    private void migrateShardsFromNode(String nodeId) {
        Set<String> shards = shardAssignments.getOrDefault(nodeId, Collections.emptySet());
        if (shards.isEmpty()) {
            shardAssignments.remove(nodeId);
            return;
        }
        List<String> candidateNodes = nodes.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(nodeId) && entry.getValue().isOnline())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (candidateNodes.isEmpty()) {
            log.warn("Unable to migrate shards for {} because no healthy nodes are available", nodeId);
            return;
        }
        Iterator<String> iterator = cycle(candidateNodes);
        int migrations = 0;
        for (String shard : shards) {
            String target = iterator.next();
            shardAssignments.computeIfAbsent(target, k -> ConcurrentHashMap.newKeySet()).add(shard);
            migrations++;
        }
        shardAssignments.remove(nodeId);
        if (migrations > 0) {
            shardMigrationCounter.increment(migrations);
            log.info("Migrated {} shards from offline node {} to {}", migrations, nodeId, candidateNodes);
        }
    }

    private NodeRuntime requireNode(String nodeId) {
        NodeRuntime runtime = nodes.get(nodeId);
        if (runtime == null) {
            throw new ResourceNotFoundException("节点不存�? " + nodeId);
        }
        return runtime;
    }

    private NodeInfo toView(NodeRuntime runtime) {
        NodeInfo current = runtime.current();
        int shardCount = shardAssignments.getOrDefault(current.getId(), Collections.emptySet()).size();
        return current.toBuilder()
                .shardCount(shardCount)
                .lastHeartbeat(runtime.lastHeartbeat())
                .build();
    }

    private static <T> Iterator<T> cycle(List<T> list) {
        if (list.isEmpty()) {
            return Collections.emptyIterator();
        }
        return new Iterator<>() {
            private int index;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public T next() {
                T value = list.get(index);
                index = (index + 1) % list.size();
                return value;
            }
        };
    }

    private final class NodeRuntime {
        private volatile NodeInfo snapshot;
        private volatile Instant lastBeat;

        private NodeRuntime(NodeInfo info) {
            this.snapshot = info;
            this.lastBeat = info.getLastHeartbeat() != null
                    ? info.getLastHeartbeat().toInstant()
                    : Instant.now();
            meterRegistry.gauge(
                    "skytask_node_heartbeat_age_seconds",
                    Tags.of("nodeId", info.getId(), "cluster", info.getCluster()),
                    this,
                    runtime -> runtime.heartbeatAgeSeconds());
        }

        private NodeInfo current() {
            return snapshot;
        }

        private OffsetDateTime lastHeartbeat() {
            return OffsetDateTime.ofInstant(lastBeat, ZoneId.systemDefault());
        }

        private NodeInfo update(java.util.function.Function<NodeInfo, NodeInfo> updater) {
            this.snapshot = updater.apply(this.snapshot);
            return this.snapshot;
        }

        private void markHeartbeat(int cpu, int memory) {
            this.lastBeat = Instant.now();
            this.snapshot = this.snapshot.toBuilder()
                    .status(NodeStatus.ONLINE)
                    .cpu(cpu)
                    .memory(memory)
                    .alertLevel("NORMAL")
                    .lastHeartbeat(OffsetDateTime.ofInstant(lastBeat, ZoneId.systemDefault()))
                    .build();
        }

        private boolean isOnline() {
            return snapshot.getStatus() == NodeStatus.ONLINE;
        }

        private long heartbeatAge(Instant now) {
            return Duration.between(lastBeat, now).getSeconds();
        }

        private double heartbeatAgeSeconds() {
            return Duration.between(lastBeat, Instant.now()).toMillis() / 1000.0;
        }
    }
}
