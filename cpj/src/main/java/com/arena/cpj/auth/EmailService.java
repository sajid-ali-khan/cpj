package com.arena.cpj.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendOtpEmail(String toEmail, String otpCode) {
        log.info("Sending OTP email to: {}", toEmail);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("alikhan8019340863@gmail.com");
            message.setTo(toEmail);
            message.setSubject("Your CPJ Contest Login OTP");
            message.setText("Hello Student,\n\n" +
                    "Your One-Time Password (OTP) for Competitive Programming Judge login is: " + otpCode + "\n\n" +
                    "This OTP is valid for 5 minutes. Please do not share this code with anyone.\n\n" +
                    "Good luck with your contest!\n\n" +
                    "Best Regards,\n" +
                    "CPJ Team");
            mailSender.send(message);
            log.info("OTP email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email to: {}", toEmail, e);
        }
    }
}
