package com.arena.cpj.admin;

import com.arena.cpj.admin.dto.CreateUserRequest;
import com.arena.cpj.admin.dto.UserResponse;
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
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
public class AdminUserControllerDocTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AdminUserService adminUserService;

    @Test
    void createUser_Success() throws Exception {
        // Authenticating as Admin
        User adminUser = User.builder()
                .id(1L)
                .name("Admin")
                .rollNo("admin")
                .email("admin@college.edu")
                .role(UserRole.ADMIN)
                .build();
        when(userRepository.findByActiveSessionToken("ADM-TOKEN")).thenReturn(Optional.of(adminUser));

        // Stubbing create response
        UserResponse responseDto = UserResponse.builder()
                .id(2L)
                .name("Student User")
                .rollNo("22CS102")
                .email("student@college.edu")
                .branch("CS")
                .role(UserRole.STUDENT)
                .build();
        when(adminUserService.create(any(CreateUserRequest.class))).thenReturn(responseDto);

        CreateUserRequest request = new CreateUserRequest();
        request.setName("Student User");
        request.setRollNo("22CS102");
        request.setEmail("student@college.edu");
        request.setBranch("CS");
        request.setRole(UserRole.STUDENT);

        mockMvc.perform(post("/api/admin/users")
                        .header("X-Roll-No", "ADM-TOKEN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.name").value("Student User"))
                .andDo(document("admin-create-user",
                        requestHeaders(
                                headerWithName("X-Roll-No").description("Active session token for admin authentication.")
                        ),
                        requestFields(
                                fieldWithPath("name").description("The user's full name."),
                                fieldWithPath("rollNo").description("The unique roll number or username."),
                                fieldWithPath("email").description("The unique email address."),
                                fieldWithPath("branch").description("The student's branch (optional)."),
                                fieldWithPath("role").description("The role of the user (e.g. STUDENT, ADMIN).")
                        ),
                        responseFields(
                                fieldWithPath("id").description("The database ID of the created user."),
                                fieldWithPath("name").description("The full name of the user."),
                                fieldWithPath("rollNo").description("The roll number of the user."),
                                fieldWithPath("branch").description("The branch of the user."),
                                fieldWithPath("email").description("The email address of the user."),
                                fieldWithPath("role").description("The role of the user.")
                        )
                ));
    }
}
