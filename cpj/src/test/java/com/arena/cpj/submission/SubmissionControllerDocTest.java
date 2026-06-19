package com.arena.cpj.submission;

import com.arena.cpj.submission.dto.*;
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

import java.util.List;
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
public class SubmissionControllerDocTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SubmissionService submissionService;

    @Test
    void submitSolution_Success() throws Exception {
        User user = User.builder()
                .id(1L)
                .name("Sajid Khan")
                .rollNo("22CS101")
                .email("sajid@college.edu")
                .role(UserRole.STUDENT)
                .build();
        when(userRepository.findByActiveSessionToken("STU-TOKEN")).thenReturn(Optional.of(user));

        SubmitResponse submitResponse = SubmitResponse.builder()
                .submissionId(42L)
                .build();
        when(submissionService.submitAsync(any(SubmitRequest.class))).thenReturn(submitResponse);

        SubmitRequest request = new SubmitRequest();
        request.setContestId(1L);
        request.setQuestionId(10L);
        request.setRollNumber("22CS101");
        request.setLanguage("java");
        request.setCode("public class Main { public static void main(String[] args) {} }");

        mockMvc.perform(post("/api/submit")
                        .header("X-Roll-No", "STU-TOKEN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value(42L))
                .andDo(document("submission-submit",
                        requestHeaders(
                                headerWithName("X-Roll-No").description("Active session token for student authentication.")
                        ),
                        requestFields(
                                fieldWithPath("contestId").description("The ID of the contest."),
                                fieldWithPath("questionId").description("The ID of the problem/question being solved."),
                                fieldWithPath("rollNumber").description("The roll number of the student."),
                                fieldWithPath("language").description("Language identifier (e.g. 'cpp', 'java', 'python')."),
                                fieldWithPath("code").description("The solution source code.")
                        ),
                        responseFields(
                                fieldWithPath("submissionId").description("The unique ID generated for this submission.")
                        )
                ));
    }

    @Test
    void compileCode_Success() throws Exception {
        User user = User.builder()
                .id(1L)
                .name("Sajid Khan")
                .rollNo("22CS101")
                .email("sajid@college.edu")
                .role(UserRole.STUDENT)
                .build();
        when(userRepository.findByActiveSessionToken("STU-TOKEN")).thenReturn(Optional.of(user));

        CompileResponse.TestCaseResult result = CompileResponse.TestCaseResult.builder()
                .stdin("test input")
                .expectedOutput("Hello World")
                .actualOutput("Hello World")
                .verdict("Accepted")
                .stderr("")
                .success(true)
                .build();

        CompileResponse compileResponse = CompileResponse.builder()
                .success(true)
                .status("SUCCESS")
                .output("Hello World")
                .consoleOutput("Execution finished successfully")
                .testCaseResults(List.of(result))
                .build();
        when(submissionService.compileAndRun(any(CompileRequest.class))).thenReturn(compileResponse);

        CompileRequest request = new CompileRequest();
        request.setContestId(1L);
        request.setQuestionId(10L);
        request.setLanguage("python");
        request.setCode("print('Hello World')");
        request.setCustomInput("test input");

        mockMvc.perform(post("/api/compile")
                        .header("X-Roll-No", "STU-TOKEN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.output").value("Hello World"))
                .andDo(document("submission-compile",
                        requestHeaders(
                                headerWithName("X-Roll-No").description("Active session token for student authentication.")
                        ),
                        requestFields(
                                fieldWithPath("contestId").description("The ID of the contest (optional)."),
                                fieldWithPath("questionId").description("The ID of the problem (optional)."),
                                fieldWithPath("code").description("The code block to compile and run."),
                                fieldWithPath("language").description("Language identifier (e.g. 'cpp', 'java', 'python')."),
                                fieldWithPath("customInput").description("Optional custom input to feed via stdin.")
                        ),
                        responseFields(
                                fieldWithPath("success").description("True if execution finished without fatal errors."),
                                fieldWithPath("status").description("Status message of the compile and run action."),
                                fieldWithPath("output").description("Standard output from running the program."),
                                fieldWithPath("consoleOutput").description("Console logs containing compiler or execution feedback."),
                                fieldWithPath("testCaseResults").description("List of individual test case evaluation results."),
                                fieldWithPath("testCaseResults[].stdin").description("Standard input given to the test case."),
                                fieldWithPath("testCaseResults[].expectedOutput").description("Expected output for the test case."),
                                fieldWithPath("testCaseResults[].actualOutput").description("Actual output produced by the program."),
                                fieldWithPath("testCaseResults[].verdict").description("The verdict of the test case (e.g., 'Accepted', 'Wrong Answer')."),
                                fieldWithPath("testCaseResults[].stderr").description("Standard error trace for the test case execution."),
                                fieldWithPath("testCaseResults[].success").description("True if the test case passed successfully.")
                        )
                ));
    }
}
