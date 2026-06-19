package com.arena.cpj.contest;

import com.arena.cpj.contest.dto.ContestProblemSummaryResponse;
import com.arena.cpj.contest.dto.ContestSummaryResponse;
import com.arena.cpj.event.SseService;
import com.arena.cpj.leaderboard.LeaderboardRepository;
import com.arena.cpj.leaderboard.LeaderboardService;
import com.arena.cpj.leaderboard.dto.LeaderboardEntryDto;
import com.arena.cpj.user.User;
import com.arena.cpj.user.UserRepository;
import com.arena.cpj.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
public class ContestControllerDocTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ContestService contestService;

    @MockitoBean
    private LeaderboardRepository leaderboardRepository;

    @MockitoBean
    private LeaderboardService leaderboardService;

    @MockitoBean
    private SseService sseService;

    @Test
    void getCurrentContests_Success() throws Exception {
        User user = User.builder()
                .id(1L)
                .name("Sajid Khan")
                .rollNo("22CS101")
                .email("sajid@college.edu")
                .role(UserRole.STUDENT)
                .build();
        when(userRepository.findByActiveSessionToken("STU-TOKEN")).thenReturn(Optional.of(user));

        ContestSummaryResponse contest = ContestSummaryResponse.builder()
                .id(1L)
                .title("Monthly Contest 1")
                .description("Monthly Competitive Programming Contest")
                .startTime(Instant.now().plusSeconds(3600))
                .durationMins(120)
                .phase(ContestPhase.UPCOMING)
                .problemIds(List.of(10L))
                .build();

        when(contestService.getCurrentContest()).thenReturn(List.of(contest));

        mockMvc.perform(get("/api/contests/current")
                        .header("X-Roll-No", "STU-TOKEN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Monthly Contest 1"))
                .andDo(document("contest-get-current",
                        requestHeaders(
                                headerWithName("X-Roll-No").description("Active session token for student authentication.")
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("The database ID of the contest."),
                                fieldWithPath("[].title").description("The title of the contest."),
                                fieldWithPath("[].description").description("The detailed description of the contest."),
                                fieldWithPath("[].startTime").description("The start time of the contest (ISO instant format)."),
                                fieldWithPath("[].durationMins").description("Duration of the contest in minutes."),
                                fieldWithPath("[].phase").description("Current contest phase (UPCOMING, LIVE, FINISHED)."),
                                fieldWithPath("[].problemIds").description("List of problem IDs assigned to this contest.")
                        )
                ));
    }

    @Test
    void getLeaderboard_Success() throws Exception {
        User user = User.builder()
                .id(1L)
                .name("Sajid Khan")
                .rollNo("22CS101")
                .email("sajid@college.edu")
                .role(UserRole.STUDENT)
                .build();
        when(userRepository.findByActiveSessionToken("STU-TOKEN")).thenReturn(Optional.of(user));

        LeaderboardEntryDto entry = LeaderboardEntryDto.builder()
                .rank(1)
                .userId(1L)
                .name("Sajid Khan")
                .rollNo("22CS101")
                .score(300)
                .lastAcTime(LocalDateTime.now())
                .status("Active")
                .solvedCount(3)
                .totalQuestions(5)
                .maxScore(500)
                .violations(0)
                .build();

        when(leaderboardService.getLeaderboard(1L)).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/contests/{contestId}/leaderboard", 1L)
                        .header("X-Roll-No", "STU-TOKEN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sajid Khan"))
                .andDo(document("contest-get-leaderboard",
                        requestHeaders(
                                headerWithName("X-Roll-No").description("Active session token for student authentication.")
                        ),
                        responseFields(
                                fieldWithPath("[].rank").description("The rank of the participant."),
                                fieldWithPath("[].userId").description("The database ID of the participant user."),
                                fieldWithPath("[].name").description("The name of the participant."),
                                fieldWithPath("[].rollNo").description("The roll number of the participant."),
                                fieldWithPath("[].score").description("Total score obtained by the participant."),
                                fieldWithPath("[].lastAcTime").description("The time of the last accepted submission (ISO date-time format)."),
                                fieldWithPath("[].status").description("Workspace status of the student (Active or Locked)."),
                                fieldWithPath("[].solvedCount").description("Number of questions solved."),
                                fieldWithPath("[].totalQuestions").description("Total number of questions in the contest."),
                                fieldWithPath("[].maxScore").description("Maximum possible score in the contest."),
                                fieldWithPath("[].violations").description("Number of tab-switching violations recorded.")
                        )
                ));
    }
}
