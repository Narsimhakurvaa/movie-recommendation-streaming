package com.cinevault.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Development implementation that writes the action link to the application log
 * instead of sending an email.
 *
 * <p>This is a genuine, working delivery channel for local use - the developer
 * copies the link from the console and the flow completes end to end. It is not
 * a stub that pretends to succeed while doing nothing.
 *
 * <h2>Why this is excluded from production</h2>
 * <p>A password-reset token is a bearer credential: anyone holding it can take
 * over the account until it expires. Writing it to the application log would
 * copy that credential into every log aggregator, backup and support tool that
 * ingests stdout, so this bean is restricted with {@code @Profile("!prod")}.
 *
 * <p>Belt and braces: {@link NotificationSenderGuard} additionally fails
 * startup if the {@code prod} profile is active and no real sender has been
 * supplied, so a production deployment cannot silently fall back to a channel
 * that leaks tokens - or to no delivery at all.
 *
 * <p>Annotated {@link ConditionalOnMissingBean} so that adding a real sender to
 * the context replaces it automatically, with no configuration change.
 */
@Component
@Profile("!prod")
@ConditionalOnMissingBean(ignored = LoggingNotificationSender.class, value = NotificationSender.class)
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    private final String frontendUrl;

    public LoggingNotificationSender(@Value("${cinevault.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void sendPasswordReset(String email, String rawToken) {
        // The token is a credential. It is printed here only because this
        // implementation is confined to non-production profiles.
        log.warn("""

                ------------------------------------------------------------------
                 PASSWORD RESET (development delivery - no email was sent)
                 To: {}
                 Link: {}/reset-password?token={}
                ------------------------------------------------------------------""",
                maskEmail(email), frontendUrl, rawToken);
    }

    @Override
    public void sendEmailVerification(String email, String rawToken) {
        log.warn("""

                ------------------------------------------------------------------
                 EMAIL VERIFICATION (development delivery - no email was sent)
                 To: {}
                 Link: {}/verify-email?token={}
                ------------------------------------------------------------------""",
                maskEmail(email), frontendUrl, rawToken);
    }

    /** Keeps full addresses out of the log while remaining recognisable. */
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
