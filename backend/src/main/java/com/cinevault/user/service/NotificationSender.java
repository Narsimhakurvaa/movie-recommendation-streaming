package com.cinevault.user.service;

/**
 * Delivers transactional messages to users.
 *
 * <p>An interface so that the account flows depend on the <em>capability</em>
 * of sending a message, not on a mail library. The bundled implementation logs
 * the link, which keeps password reset and email verification fully testable
 * locally; a production deployment supplies an SMTP or provider-backed bean.
 */
public interface NotificationSender {

    /**
     * @param email    recipient address
     * @param rawToken the single-use token to embed in the reset link
     */
    void sendPasswordReset(String email, String rawToken);

    /**
     * @param email    recipient address
     * @param rawToken the single-use token to embed in the verification link
     */
    void sendEmailVerification(String email, String rawToken);
}
