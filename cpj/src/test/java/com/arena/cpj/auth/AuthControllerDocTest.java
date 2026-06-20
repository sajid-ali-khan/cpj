package com.arena.cpj.auth;

import com.arena.cpj.user.User;
import com.arena.cpj.user.UserRepository;
import com.arena.cpj.user.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
public class AuthControllerDocTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private OtpService otpService;

    @Test
    void sendOtp_Success() throws Exception {
        User user = User.builder()
                .id(1L)
                .name("Sajid Khan")
                .rollNo("22CS101")
                .email("sajid@college.edu")
                .role(UserRole.STUDENT)
                .build();

        when(userRepository.findByRollNo("22CS101")).thenReturn(Optional.of(user));
        doNothing().when(otpService).generateAndSendOtp(any(User.class));

        AuthController.SendOtpRequest request = new AuthController.SendOtpRequest();
        request.setRollNumber("22CS101");

        mockMvc.perform(post("/api/student/login/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andDo(document("student-login-send-otp",
                        requestFields(
                                fieldWithPath("rollNumber").description("The student's roll number.")
                        ),
                        responseFields(
                                fieldWithPath("success").description("Indicates if the OTP was successfully sent."),
                                fieldWithPath("message").description("Confirmation message with masked email.")
                        )
                ));
    }

    @Test
    void verifyOtp_Success() throws Exception {
        User user = User.builder()
                .id(1L)
                .name("Sajid Khan")
                .rollNo("22CS101")
                .email("sajid@college.edu")
                .role(UserRole.STUDENT)
                .build();

        when(userRepository.findByRollNo("22CS101")).thenReturn(Optional.of(user));
        doNothing().when(otpService).verifyOtp(any(User.class), any(String.class));
        when(userRepository.save(any(User.class))).thenReturn(user);

        AuthController.VerifyOtpRequest request = new AuthController.VerifyOtpRequest();
        request.setRollNumber("22CS101");
        request.setOtp("123456");

        mockMvc.perform(post("/api/student/login/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andDo(document("student-login-verify-otp",
                        requestFields(
                                fieldWithPath("rollNumber").description("The student's roll number."),
                                fieldWithPath("otp").description("The OTP code received by email.")
                        ),
                        responseFields(
                                fieldWithPath("token").description("Active session token for subsequent authenticated requests."),
                                fieldWithPath("userType").description("The type of user logging in ('student')."),
                                fieldWithPath("user.rollNumber").description("The roll number of the logged-in student."),
                                fieldWithPath("user.name").description("The name of the logged-in student."),
                                fieldWithPath("user.branch").description("The branch of the student."),
                                fieldWithPath("user.email").description("The email address of the student.")
                        )
                ));
    }
}
