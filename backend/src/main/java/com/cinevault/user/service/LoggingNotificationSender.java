package com.cinevault.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Development implementation that writes the action link to the application log
 * instead of sending an email.
 *
 * <p>This is a genuine, working delivery channel for local use - the developer
 * copies the link from the console and the flow completes end to end. It is not
 * a stub that pretends to succeed while doing nothing.
 *
 * <p>Annotated {@link ConditionalOnMissingBean} so that adding a real sender to
 * the context replaces it automatically, with no configuration change.
 */
@Component
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
        // implementation exists specifically for local development, and the
        // class must never be active in production (see NotificationSender).
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
