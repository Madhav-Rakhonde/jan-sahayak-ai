package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Async
    public void sendVerificationEmail(User user, String token) {
        String verificationUrl = frontendBaseUrl + "/verify-email?token=" + token;
        log.info("Preparing to send email verification link to {}: {}", user.getEmail(), verificationUrl);

        if (mailSender == null || fromEmail == null || fromEmail.trim().isEmpty()) {
            log.warn("==========================================================================");
            log.warn("⚠️ SMTP IS NOT FULLY CONFIGURABLE OR EMAIL SENDER USERNAME IS EMPTY.");
            log.warn("--- DEVELOPMENT EMAIL VERIFICATION LINK FOR {} ---", user.getEmail());
            log.warn("LINK: {}", verificationUrl);
            log.warn("==========================================================================");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject("Verify your Govlyx Account");

            String htmlContent = buildHtmlTemplate(user.getActualUsername(), verificationUrl);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Verification email successfully sent to {}", user.getEmail());

        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error while sending verification email to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    private String buildHtmlTemplate(String username, String verificationUrl) {
        String safeUsername = org.springframework.web.util.HtmlUtils.htmlEscape(username != null ? username : "");
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head>"
                + "  <meta charset='utf-8'>"
                + "  <style>"
                + "    body { font-family: 'Inter', sans-serif; background-color: #f4f5f7; margin: 0; padding: 0; }"
                + "    .container { max-width: 600px; margin: 40px auto; background: #ffffff; border-radius: 16px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); overflow: hidden; border: 1px solid #e1e4e8; }"
                + "    .header { background: #1d4ed8; color: #ffffff; padding: 32px; text-align: center; }"
                + "    .header h1 { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: -0.025em; }"
                + "    .content { padding: 40px 32px; color: #374151; line-height: 1.6; }"
                + "    .content h2 { margin-top: 0; color: #111827; font-size: 20px; font-weight: 600; }"
                + "    .btn-container { text-align: center; margin: 32px 0; }"
                + "    .btn { display: inline-block; background-color: #1d4ed8; color: #ffffff !important; text-decoration: none; padding: 14px 28px; font-weight: 600; border-radius: 12px; font-size: 16px; box-shadow: 0 4px 6px rgba(29, 78, 216, 0.15); transition: background-color 0.2s; }"
                + "    .footer { background: #f9fafb; padding: 24px 32px; text-align: center; color: #6b7280; font-size: 13px; border-top: 1px solid #f3f4f6; }"
                + "    .footer a { color: #1d4ed8; text-decoration: none; }"
                + "  </style>"
                + "</head>"
                + "<body>"
                + "  <div class='container'>"
                + "    <div class='header'>"
                + "      <h1>Govlyx Portal</h1>"
                + "    </div>"
                + "    <div class='content'>"
                + "      <h2>Welcome, " + safeUsername + "!</h2>"
                + "      <p>Thank you for joining Govlyx. To complete your registration and activate your account, please verify your email address by clicking the button below:</p>"
                + "      <div class='btn-container'>"
                + "        <a href='" + verificationUrl + "' class='btn'>Verify Email Address</a>"
                + "      </div>"
                + "      <p>If the button doesn't work, you can copy and paste the following link into your browser:</p>"
                + "      <p style='word-break: break-all; color: #2563eb; font-size: 14px;'>" + verificationUrl + "</p>"
                + "      <p>This verification link will expire in 24 hours.</p>"
                + "    </div>"
                + "    <div class='footer'>"
                + "      <p>This is an automated message, please do not reply directly to this email.</p>"
                + "      <p>&copy; 2026 Govlyx. All rights reserved.</p>"
                + "    </div>"
                + "  </div>"
                + "</body>"
                + "</html>";
    }
}
