package com.arena.cpj.auth;

import com.arena.cpj.common.BadRequestException;
import com.arena.cpj.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int MAX_ATTEMPTS = 3;
    private static final int OTP_EXPIRY_MINUTES = 5;

    private final OtpVerificationRepository otpRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void generateAndSendOtp(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("No registered email address found for your account. Please contact an admin.");
        }

        // 1. Delete any existing OTP verifications for this user
        otpRepository.deleteByUserId(user.getId());

        // 2. Generate a secure 6-digit OTP
        int otpNumber = 100000 + secureRandom.nextInt(900000);
        String otpCode = String.valueOf(otpNumber);

        // 3. Save new OTP to the database
        OtpVerification verification = OtpVerification.builder()
                .user(user)
                .otpCode(otpCode)
                .expiresAt(Instant.now().plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES))
                .build();
        otpRepository.save(verification);

        log.info("Generated OTP for user ID: {} (expires in {} mins)", user.getId(), OTP_EXPIRY_MINUTES);

        // 4. Send the OTP email asynchronously
        emailService.sendOtpEmail(user.getEmail(), otpCode);
    }

    @Transactional
    public void verifyOtp(User user, String code) {
        OtpVerification verification = otpRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new UnauthorizedException("OTP not requested. Please login again."));

        if (Instant.now().isAfter(verification.getExpiresAt())) {
            otpRepository.delete(verification);
            throw new UnauthorizedException("OTP has expired. Please request a new one.");
        }

        if (verification.getAttempts() >= MAX_ATTEMPTS) {
            otpRepository.delete(verification);
            throw new UnauthorizedException("Too many failed attempts. Please request a new OTP.");
        }

        if (verification.getOtpCode().equals(code.trim())) {
            // Success! Consume and delete the OTP so it cannot be reused
            otpRepository.delete(verification);
            log.info("OTP verification successful for user ID: {}", user.getId());
        } else {
            // Increment attempt counter
            int attempts = verification.getAttempts() + 1;
            verification.setAttempts(attempts);
            otpRepository.save(verification);

            int remaining = MAX_ATTEMPTS - attempts;
            if (remaining <= 0) {
                otpRepository.delete(verification);
                throw new UnauthorizedException("Invalid OTP. Too many failed attempts. Please request a new OTP.");
            }
            throw new UnauthorizedException("Invalid OTP code. Attempts remaining: " + remaining);
        }
    }
}
