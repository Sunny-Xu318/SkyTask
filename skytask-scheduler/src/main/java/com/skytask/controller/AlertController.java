package com.skytask.controller;

import com.skytask.annotation.RequirePermission;
import com.skytask.dto.AlertRuleRequest;
import com.skytask.model.AlertRule;
import com.skytask.service.AlertService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/rules")
    @RequirePermission("config:write")
    public List<AlertRule> rules() {
        return alertService.findAll();
    }

    @PostMapping("/rules")
    @RequirePermission("config:write")
    public ResponseEntity<AlertRule> create(@Valid @RequestBody AlertRuleRequest request) {
        AlertRule rule = alertService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    @PutMapping("/rules/{ruleId}")
    @RequirePermission("config:write")
    public AlertRule update(@PathVariable String ruleId, @Valid @RequestBody AlertRuleRequest request) {
        return alertService.update(ruleId, request);
    }

    @DeleteMapping("/rules/{ruleId}")
    @RequirePermission("config:write")
    public ResponseEntity<Void> delete(@PathVariable String ruleId) {
        alertService.delete(ruleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    @RequirePermission("config:write")
    public ResponseEntity<Void> testChannel(@RequestBody AlertRuleRequest request) {
        alertService.testChannel(request);
        return ResponseEntity.accepted().build();
    }
}
