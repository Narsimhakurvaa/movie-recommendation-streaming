package com.cinevault.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

/**
 * Refuses to start the production profile without a real notification channel.
 *
 * <h2>Why this exists</h2>
 * <p>{@link LoggingNotificationSender} is excluded from {@code prod} because it
 * writes password-reset tokens to the log. That exclusion alone would leave a
 * worse failure mode: with no {@link NotificationSender} bean at all, the
 * account-recovery services would fail to inject and the application would die
 * with an opaque {@code NoSuchBeanDefinitionException} deep in the startup
 * trace.
 *
 * <p>This guard turns that into an explicit, actionable message at startup. A
 * misconfigured deployment fails immediately and loudly rather than either
 * leaking credentials into logs or breaking password reset at the moment a user
 * needs it.
 */
@Configuration
@Profile("prod")
public class NotificationSenderGuard {

    private static final Logger log = LoggerFactory.getLogger(NotificationSenderGuard.class);

    private final ObjectProvider<NotificationSender> notificationSenders;

    public NotificationSenderGuard(ObjectProvider<NotificationSender> notificationSenders) {
        this.notificationSenders = notificationSenders;
    }

    @PostConstruct
    void verifyRealSenderIsConfigured() {
        NotificationSender sender = notificationSenders.getIfAvailable();

        if (sender == null) {
            throw new IllegalStateException("""
                    No NotificationSender bean is configured, and the development \
                    logging sender is disabled under the 'prod' profile because it \
                    writes password-reset tokens to the application log.

                    Supply a real implementation (SMTP, SES, SendGrid, Postmark, ...) \
                    as a Spring bean implementing com.cinevault.user.service.NotificationSender. \
                    Password reset and email verification cannot function without one.""");
        }

        if (sender instanceof LoggingNotificationSender) {
            // Defensive: should be unreachable while the @Profile("!prod")
            // restriction is in place, but an explicit failure is far better
            // than silently logging credentials in production.
            throw new IllegalStateException(
                    "LoggingNotificationSender must never be active in production: "
                            + "it writes password-reset tokens to the application log.");
        }

        log.info("Notification delivery: {}", sender.getClass().getSimpleName());
    }
}
