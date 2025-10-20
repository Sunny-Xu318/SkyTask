package com.skytask.service;

import com.skytask.config.SchedulerDynamicProperties;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertNotificationService {

    private final SchedulerDynamicProperties properties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public void notifyFailure(long taskId, String taskName, double failureRate, long sampleCount) {
        List<String> channels = properties.getAlert().getNotifyChannels();
        if (CollectionUtils.isEmpty(channels)) {
            log.debug("No notify channels configured for task {}, skip alert dispatch", taskId);
            return;
        }
        if (channels.stream().anyMatch(channel -> "EMAIL".equalsIgnoreCase(channel))) {
            dispatchEmail(taskId, taskName, failureRate, sampleCount);
        }
    }

    private void dispatchEmail(long taskId, String taskName, double failureRate, long sampleCount) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        List<String> recipients = properties.getAlerting().getEmailRecipients();
        if (mailSender == null || CollectionUtils.isEmpty(recipients)) {
            log.warn("Email alert requested but mail sender or recipients are not configured (task: {})", taskId);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipients.stream().filter(Objects::nonNull).toArray(String[]::new));
            message.setSubject(String.format(Locale.ENGLISH, "[SkyTask] Task %s failure rate %.2f%%", taskName, failureRate));
            message.setText(String.format(Locale.ENGLISH,
                    "Task %s (ID: %d) has exceeded the failure threshold.%nFailure rate: %.2f%%%nSamples: %d%nAction: task has been marked as degraded.",
                    taskName, taskId, failureRate, sampleCount));
            mailSender.send(message);
            log.info("Sent failure alert email for task {} to {}", taskId, recipients);
        } catch (MailException ex) {
            log.error("Failed to send alert email for task {}: {}", taskId, ex.getMessage(), ex);
        }
    }
}
