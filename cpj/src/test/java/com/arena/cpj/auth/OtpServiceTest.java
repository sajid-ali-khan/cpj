package com.arena.cpj.auth;

import com.arena.cpj.common.BadRequestException;
import com.arena.cpj.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpVerificationRepository otpRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private OtpService otpService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Sajid Khan")
                .rollNo("22CS101")
                .email("sajid@college.edu")
                .build();
    }

    @Test
    void generateAndSendOtp_Success() {
        otpService.generateAndSendOtp(testUser);

        verify(otpRepository, times(1)).deleteByUserId(testUser.getId());
        verify(otpRepository, times(1)).save(any(OtpVerification.class));
        verify(emailService, times(1)).sendOtpEmail(eq("sajid@college.edu"), anyString());
    }

    @Test
    void generateAndSendOtp_ThrowsException_WhenEmailIsEmpty() {
        testUser.setEmail("");
        assertThrows(BadRequestException.class, () -> otpService.generateAndSendOtp(testUser));
    }

    @Test
    void verifyOtp_Success() {
        OtpVerification verification = OtpVerification.builder()
                .user(testUser)
                .otpCode("123456")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(0)
                .build();

        when(otpRepository.findTopByUserIdOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(Optional.of(verification));

        assertDoesNotThrow(() -> otpService.verifyOtp(testUser, "123456"));
        verify(otpRepository, times(1)).delete(verification);
    }

    @Test
    void verifyOtp_ThrowsException_WhenOtpExpired() {
        OtpVerification verification = OtpVerification.builder()
                .user(testUser)
                .otpCode("123456")
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .attempts(0)
                .build();

        when(otpRepository.findTopByUserIdOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(Optional.of(verification));

        assertThrows(UnauthorizedException.class, () -> otpService.verifyOtp(testUser, "123456"));
        verify(otpRepository, times(1)).delete(verification);
    }

    @Test
    void verifyOtp_IncrementsAttempts_WhenOtpIncorrect() {
        OtpVerification verification = OtpVerification.builder()
                .user(testUser)
                .otpCode("123456")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(0)
                .build();

        when(otpRepository.findTopByUserIdOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(Optional.of(verification));

        assertThrows(UnauthorizedException.class, () -> otpService.verifyOtp(testUser, "654321"));
        assertEquals(1, verification.getAttempts());
        verify(otpRepository, times(1)).save(verification);
    }

    @Test
    void verifyOtp_ThrowsException_WhenAttemptsExceeded() {
        OtpVerification verification = OtpVerification.builder()
                .user(testUser)
                .otpCode("123456")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(3)
                .build();

        when(otpRepository.findTopByUserIdOrderByCreatedAtDesc(testUser.getId()))
                .thenReturn(Optional.of(verification));

        assertThrows(UnauthorizedException.class, () -> otpService.verifyOtp(testUser, "123456"));
        verify(otpRepository, times(1)).delete(verification);
    }
}
