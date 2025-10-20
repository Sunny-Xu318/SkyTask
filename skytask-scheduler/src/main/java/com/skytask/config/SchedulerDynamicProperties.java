package com.skytask.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "skytask.scheduler")
@Data
public class SchedulerDynamicProperties {

    private RetryProperties retry = new RetryProperties();
    private AlertProperties alert = new AlertProperties();
    private AlertingProperties alerting = new AlertingProperties();
    private NodeProperties nodes = new NodeProperties();

    @Data
    public static class RetryProperties {
        /**
         * 默认的重试策略（EXP_BACKOFF / FIXED_INTERVAL / NONE）
         */
        private String defaultPolicy = "EXP_BACKOFF";
        /**
         * 最大重试次数
         */
        private int maxAttempts = 3;
        /**
         * 基础退避时间（毫秒）
         */
        private int baseBackoffMs = 60_000;
        /**
         * 任务超时时间（秒）
         */
        private int timeoutSeconds = 300;
    }

    @Data
    public static class AlertProperties {
        /**
         * 失败率告警阈值（百分比）
         */
        private int failureRateThreshold = 30;
        /**
         * 自动降级前最少需要采集的样本量
         */
        private int autoDegradeMinSamples = 10;
        /**
         * 告警通知的通道
         */
        private List<String> notifyChannels = new ArrayList<>(List.of("EMAIL"));
    }

    @Data
    public static class AlertingProperties {
        /**
         * 邮件通知的收件人列表
         */
        private List<String> emailRecipients = new ArrayList<>();
        /**
         * 再次触发自动升级的冷静时间（分钟）
         */
        private int escalationCooldownMinutes = 10;
    }

    @Data
    public static class NodeProperties {
        /**
         * 心跳超时时间（秒）
         */
        private int heartbeatTimeoutSeconds = 90;
        /**
         * 节点健康检查的周期（毫秒）
         */
        private long healthCheckIntervalMs = 30_000L;
    }
}
