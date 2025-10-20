package com.skytask.controller;

import com.skytask.annotation.RequirePermission;
import com.skytask.dto.PageResponse;
import com.skytask.dto.TaskMetricsResponse;
import com.skytask.dto.TaskRequest;
import com.skytask.dto.TaskStatusUpdateRequest;
import com.skytask.dto.TaskTriggerRequest;
import com.skytask.model.TaskExecutionRecord;
import com.skytask.model.TaskInfo;
import com.skytask.service.TaskService;
import com.skytask.service.dto.TaskQuery;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/metrics")
    @RequirePermission("task:read")
    public TaskMetricsResponse metrics(@RequestParam(value = "range", defaultValue = "24h") String range) {
        return taskService.loadMetrics(range);
    }

    @GetMapping
    @RequirePermission("task:read")
    public PageResponse<TaskInfo> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "owner", required = false) String owner,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        List<String> resolvedTags = tags;
        if (resolvedTags != null && resolvedTags.size() == 1 && resolvedTags.get(0).contains(",")) {
            resolvedTags = List.of(resolvedTags.get(0).split(","));
        }
        if (CollectionUtils.isEmpty(resolvedTags)) {
            resolvedTags = Collections.emptyList();
        }
        TaskQuery query = TaskQuery.builder()
                .keyword(keyword)
                .status(status)
                .owner(owner)
                .tags(resolvedTags)
                .page(page)
                .size(size)
                .build();
        return taskService.listTasks(query);
    }

    @GetMapping("/{taskId}")
    @RequirePermission("task:read")
    public TaskInfo detail(@PathVariable String taskId) {
        return taskService.getTask(taskId);
    }

    @PostMapping
    @RequirePermission("task:write")
    public ResponseEntity<TaskInfo> create(@Valid @RequestBody TaskRequest request) {
        TaskInfo task = taskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    @PutMapping("/{taskId}")
    @RequirePermission("task:write")
    public TaskInfo update(@PathVariable String taskId, @Valid @RequestBody TaskRequest request) {
        return taskService.updateTask(taskId, request);
    }

    @DeleteMapping("/{taskId}")
    @RequirePermission("task:write")
    public ResponseEntity<Void> delete(@PathVariable String taskId) {
        taskService.deleteTask(taskId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{taskId}/status")
    @RequirePermission("task:write")
    public TaskInfo togglePut(@PathVariable String taskId, @Valid @RequestBody TaskStatusUpdateRequest request) {
        return taskService.toggleTask(taskId, request.getEnabled());
    }

    @PatchMapping("/{taskId}/status")
    @RequirePermission("task:write")
    public TaskInfo togglePatch(@PathVariable String taskId, @Valid @RequestBody TaskStatusUpdateRequest request) {
        return taskService.toggleTask(taskId, request.getEnabled());
    }

    @PostMapping("/{taskId}/trigger")
    @RequirePermission("task:trigger")
    public ResponseEntity<TaskExecutionRecord> trigger(
            @PathVariable String taskId, @RequestBody TaskTriggerRequest request) {
        TaskExecutionRecord record = taskService.triggerTask(taskId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(record);
    }

    @GetMapping("/{taskId}/records")
    @RequirePermission("task:read")
    public PageResponse<TaskExecutionRecord> records(
            @PathVariable String taskId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "range", required = false) String range) {
        return taskService.getExecutions(taskId, page, size, range);
    }

    @GetMapping("/cron/suggestions")
    @RequirePermission("task:read")
    public List<Map<String, String>> cronSuggestions(@RequestParam(value = "keyword", required = false) String keyword) {
        return taskService.suggestCron(keyword);
    }

    @GetMapping("/export")
    @RequirePermission("task:read")
    public ResponseEntity<byte[]> export() {
        PageResponse<TaskInfo> response =
                taskService.listTasks(TaskQuery.builder().page(1).size(Integer.MAX_VALUE).build());
        StringBuilder csv = new StringBuilder();
        csv.append("taskId,taskName,owner,status,lastTrigger\n");
        response.getRecords().forEach(task -> csv.append(task.getId())
                .append(',')
                .append(task.getName())
                .append(',')
                .append(task.getOwner())
                .append(',')
                .append(task.isEnabled() ? "ENABLED" : "DISABLED")
                .append(',')
                .append(task.getLastTrigger() != null ? task.getLastTrigger() : "-")
                .append('\n'));

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename("tasks.csv").build());
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
