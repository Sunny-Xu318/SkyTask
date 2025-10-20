package com.skytask.service;

import com.skytask.dto.AlertRuleRequest;
import com.skytask.dto.PageResponse;
import com.skytask.exception.ResourceNotFoundException;
import com.skytask.model.AlertRule;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

    private final ConcurrentMap<String, AlertRule> rules = new ConcurrentHashMap<>();

    @PostConstruct
    public void seedRules() {
        if (!rules.isEmpty()) {
            return;
        }
        register(AlertRule.builder()
                .id(generateId())
                .name("任务失败率告警")
                .metric("FAILURE_RATE")
                .threshold(5.0)
                .channels(List.of("EMAIL", "DINGTALK"))
                .subscribers(List.of("zhangqiang@corp.com", "ops@corp.com"))
                .enabled(true)
                .createdAt(OffsetDateTime.now().minusDays(20))
                .updatedAt(OffsetDateTime.now().minusDays(2))
                .build());
        register(AlertRule.builder()
                .id(generateId())
                .name("执行节点下线告警")
                .metric("NODE_OFFLINE")
                .threshold(1.0)
                .channels(List.of("DINGTALK"))
                .subscribers(List.of("noc@corp.com"))
                .enabled(true)
                .createdAt(OffsetDateTime.now().minusDays(15))
                .updatedAt(OffsetDateTime.now().minusDays(1))
                .build());
    }

    private void register(AlertRule rule) {
        rules.put(rule.getId(), rule);
    }

    public List<AlertRule> findAll() {
        return new ArrayList<>(rules.values());
    }

    public AlertRule create(AlertRuleRequest request) {
        AlertRule rule = mapRule(request).toBuilder()
                .id(generateId())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        rules.put(rule.getId(), rule);
        return rule;
    }

    public AlertRule update(String id, AlertRuleRequest request) {
        AlertRule existing = rules.get(id);
        if (existing == null) {
            throw new ResourceNotFoundException("告警规则不存在: " + id);
        }
        AlertRule updated = mapRule(request).toBuilder()
                .id(id)
                .createdAt(existing.getCreatedAt())
                .updatedAt(OffsetDateTime.now())
                .build();
        rules.put(id, updated);
        return updated;
    }

    public void delete(String id) {
        AlertRule removed = rules.remove(id);
        if (removed == null) {
            throw new ResourceNotFoundException("告警规则不存在: " + id);
        }
    }

    public AlertRule toggle(String id, boolean enabled) {
        AlertRule existing = rules.get(id);
        if (existing == null) {
            throw new ResourceNotFoundException("告警规则不存在: " + id);
        }
        AlertRule updated = existing.toBuilder()
                .enabled(enabled)
                .updatedAt(OffsetDateTime.now())
                .build();
        rules.put(id, updated);
        return updated;
    }

    public PageResponse<AlertRule> page(int page, int size) {
        List<AlertRule> all = new ArrayList<>(rules.values());
        int resolvedPage = Math.max(page, 1);
        int resolvedSize = Math.max(size, 10);
        int fromIndex = (resolvedPage - 1) * resolvedSize;
        int toIndex = Math.min(fromIndex + resolvedSize, all.size());
        List<AlertRule> records = fromIndex >= all.size() ? List.of() : all.subList(fromIndex, toIndex);
        return PageResponse.<AlertRule>builder()
                .records(records)
                .page(resolvedPage)
                .size(resolvedSize)
                .total(all.size())
                .build();
    }

    public void testChannel(AlertRuleRequest request) {
        // TODO integrate with email/dingtalk webhook senders
    }

    private AlertRule mapRule(AlertRuleRequest request) {
        return AlertRule.builder()
                .name(request.getName())
                .metric(request.getMetric())
                .threshold(request.getThreshold())
                .channels(request.getChannels() != null ? request.getChannels() : new ArrayList<>())
                .subscribers(request.getSubscribers() != null ? request.getSubscribers() : new ArrayList<>())
                .enabled(request.isEnabled())
                .build();
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }
}
