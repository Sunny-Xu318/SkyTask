package com.skytask.admin.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.skytask.admin.client.SchedulerTaskClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/tasks")
@RequiredArgsConstructor
public class AdminTaskController {

    private final SchedulerTaskClient schedulerTaskClient;

    @GetMapping
    @SentinelResource("adminTaskList")
    @PreAuthorize("hasAuthority('task:read')")
    public ResponseEntity<Object> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "owner", required = false) String owner,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(schedulerTaskClient.listTasks(keyword, status, owner, tags, page, size));
    }

    @GetMapping("/metrics")
    @SentinelResource("adminTaskMetrics")
    @PreAuthorize("hasAuthority('task:read')")
    public ResponseEntity<Object> metrics(
            @RequestParam(value = "range", defaultValue = "24h") String range) {
        return ResponseEntity.ok(schedulerTaskClient.metrics(range));
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("hasAuthority('task:read')")
    public ResponseEntity<Object> detail(@PathVariable("taskId") String taskId) {
        return ResponseEntity.ok(schedulerTaskClient.taskDetail(taskId));
    }

    @GetMapping("/{taskId}/records")
    @PreAuthorize("hasAuthority('task:read')")
    public ResponseEntity<Object> records(
            @PathVariable("taskId") String taskId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(schedulerTaskClient.taskRecords(taskId, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('task:write')")
    public ResponseEntity<Object> create(@RequestBody Object request) {
        return ResponseEntity.ok(schedulerTaskClient.createTask(request));
    }

    @PutMapping("/{taskId}")
    @PreAuthorize("hasAuthority('task:write')")
    public ResponseEntity<Object> update(@PathVariable("taskId") String taskId, @RequestBody Object request) {
        return ResponseEntity.ok(schedulerTaskClient.updateTask(taskId, request));
    }

    @PutMapping("/{taskId}/status")
    @PreAuthorize("hasAuthority('task:write')")
    public ResponseEntity<Object> toggle(@PathVariable("taskId") String taskId, @RequestBody Object request) {
        return ResponseEntity.ok(schedulerTaskClient.toggleTask(taskId, request));
    }

    @PostMapping("/{taskId}/trigger")
    @PreAuthorize("hasAuthority('task:trigger')")
    public ResponseEntity<Object> trigger(
            @PathVariable("taskId") String taskId, @RequestBody(required = false) Object request) {
        return ResponseEntity.ok(schedulerTaskClient.triggerTask(taskId, request));
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasAuthority('task:write')")
    public ResponseEntity<Void> delete(@PathVariable("taskId") String taskId) {
        schedulerTaskClient.deleteTask(taskId);
        return ResponseEntity.noContent().build();
    }
}
