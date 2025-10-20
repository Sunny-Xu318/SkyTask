package com.skytask.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "skytask-scheduler", path = "/api/tasks", configuration = com.skytask.admin.config.FeignTenantHeaderConfig.class)
public interface SchedulerTaskClient {

    @GetMapping
    Object listTasks(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "owner", required = false) String owner,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size);

    @GetMapping("/{taskId}")
    Object taskDetail(@PathVariable("taskId") String taskId);

    @GetMapping("/metrics")
    Object metrics(@RequestParam(value = "range", defaultValue = "24h") String range);

    @GetMapping("/{taskId}/records")
    Object taskRecords(
            @PathVariable("taskId") String taskId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size);

    @PostMapping
    Object createTask(@RequestBody Object request);

    @PutMapping("/{taskId}")
    Object updateTask(@PathVariable("taskId") String taskId, @RequestBody Object request);

    @PutMapping("/{taskId}/status")
    Object toggleTask(@PathVariable("taskId") String taskId, @RequestBody Object request);

    @DeleteMapping("/{taskId}")
    void deleteTask(@PathVariable("taskId") String taskId);

    @PostMapping("/{taskId}/trigger")
    Object triggerTask(@PathVariable("taskId") String taskId, @RequestBody(required = false) Object request);
}
