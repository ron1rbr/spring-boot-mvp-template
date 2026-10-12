package com.example.template.common.email;

/**
 * Deliberately generic (to/subject/body), not "sendPasswordResetEmail(...)".
 * The template composes the reset-specific subject/body inside
 * PasswordResetService; this interface stays reusable for whatever the
 * next feature needs to email (welcome message, invoice, invite).
 */
public interface EmailSender {

  void send(String to, String subject, String body);

}
