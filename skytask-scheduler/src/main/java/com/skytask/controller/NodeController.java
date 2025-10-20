package com.skytask.controller;

import com.skytask.annotation.RequirePermission;
import com.skytask.dto.HeartbeatResponse;
import com.skytask.dto.NodeMetricsResponse;
import com.skytask.model.NodeInfo;
import com.skytask.service.NodeService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scheduler/nodes")
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @GetMapping
    @RequirePermission("node:read")
    public List<NodeInfo> list() {
        return nodeService.listNodes();
    }

    @GetMapping("/metrics")
    @RequirePermission("node:read")
    public NodeMetricsResponse metrics() {
        return nodeService.nodeMetrics();
    }

    @GetMapping("/{nodeId}/heartbeat")
    @RequirePermission("node:read")
    public HeartbeatResponse heartbeat(@PathVariable String nodeId) {
        return nodeService.heartbeat(nodeId);
    }

    @PostMapping("/{nodeId}/offline")
    @RequirePermission("config:write")
    public ResponseEntity<NodeInfo> offline(@PathVariable String nodeId) {
        return ResponseEntity.ok(nodeService.offline(nodeId));
    }

    @PostMapping("/{nodeId}/rebalance")
    @RequirePermission("config:write")
    public ResponseEntity<NodeInfo> rebalance(@PathVariable String nodeId) {
        return ResponseEntity.ok(nodeService.rebalance(nodeId));
    }
}
