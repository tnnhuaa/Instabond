package com.instabond.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Async
    public void sendOtpEmail(String toEmail, String otpCode) {
        log.info("Sending OTP email to: {}", toEmail);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject("Verify your email - Instabond");

            String htmlContent = String.format(
                    "<html>" +
                            "<body style='margin: 0; padding: 0; font-family: \"Segoe UI\", Helvetica, Arial, sans-serif; background-color: #f9f9f9;'>" +
                            "  <table width='100%%' border='0' cellspacing='0' cellpadding='0'>" +
                            "    <tr>" +
                            "      <td align='center' style='padding: 40px 0;'>" +
                            "        <table width='400' border='0' cellspacing='0' cellpadding='0' style='background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                            "          " +
                            "          <tr>" +
                            "            <td align='center' style='background-color: #e3d5ff; padding: 30px 20px;'>" +
                            "              <h1 style='margin: 0; color: #121212; font-size: 28px; letter-spacing: -1px;'>Instabond</h1>" +
                            "            </td>" +
                            "          </tr>" +
                            "          " +
                            "          <tr>" +
                            "            <td style='padding: 40px 30px; text-align: center;'>" +
                            "              <h2 style='color: #121212; margin-bottom: 10px; font-size: 22px;'>Verification Code</h2>" +
                            "              <p style='color: #555555; font-size: 15px; line-height: 1.5;'>Please use the following One-Time Password (OTP) to complete your registration. This code is valid for <b>5 minutes</b>.</p>" +
                            "              <div style='margin: 30px 0; padding: 20px; background-color: #f4f0ff; border-radius: 12px; border: 1px dashed #e3d5ff;'>" +
                            "                <span style='font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #121212;'>%s</span>" +
                            "              </div>" +
                            "              <p style='color: #888888; font-size: 13px;'>If you didn't request this code, you can safely ignore this email.</p>" +
                            "            </td>" +
                            "          </tr>" +
                            "          " +
                            "          <tr>" +
                            "            <td style='padding: 0 30px 30px 30px; text-align: center; border-top: 1px solid #eeeeee; padding-top: 20px;'>" +
                            "              <p style='color: #b0b0b0; font-size: 12px; margin: 0;'>&copy; 2026 Instabond Inc. All rights reserved.</p>" +
                            "            </td>" +
                            "          </tr>" +
                            "        </table>" +
                            "      </td>" +
                            "    </tr>" +
                            "  </table>" +
                            "</body>" +
                            "</html>", otpCode
            );

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Successfully sent OTP email to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }
}
