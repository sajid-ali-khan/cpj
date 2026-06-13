package com.arena.cpj.admin;

import com.arena.cpj.admin.dto.CreateTestCaseRequest;
import com.arena.cpj.admin.dto.TestCaseResponse;
import com.arena.cpj.common.BadRequestException;
import com.arena.cpj.common.NotFoundException;
import com.arena.cpj.problem.Problem;
import com.arena.cpj.problem.ProblemRepository;
import com.arena.cpj.problem.TestCase;
import com.arena.cpj.problem.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminTestCaseService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    @Transactional
    public TestCaseResponse create(Long problemId, CreateTestCaseRequest request) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new NotFoundException("Problem not found: " + problemId));

        if (request.getExpectedOutput() == null || request.getExpectedOutput().isBlank()) {
            throw new BadRequestException("expectedOutput is required");
        }

        TestCase testCase = TestCase.builder()
                .problem(problem)
                .stdin(request.getStdin())
                .expectedOutput(request.getExpectedOutput())
                .isSample(request.isSample())
                .build();

        return toResponse(testCaseRepository.save(testCase));
    }

    @Transactional(readOnly = true)
    public List<TestCaseResponse> list(Long problemId) {
        if (!problemRepository.existsById(problemId)) {
            throw new NotFoundException("Problem not found: " + problemId);
        }
        return testCaseRepository.findByProblemId(problemId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(Long testCaseId) {
        if (!testCaseRepository.existsById(testCaseId)) {
            throw new NotFoundException("Test case not found: " + testCaseId);
        }
        testCaseRepository.deleteById(testCaseId);
    }

    @Transactional
    public void uploadCSV(Long problemId, org.springframework.web.multipart.MultipartFile file) throws Exception {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new NotFoundException("Problem not found: " + problemId));

        String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = content.split("\n");
        System.out.println("uploadCSV: problemId = " + problemId + ", lines = " + lines.length + ", size = " + file.getSize() + " bytes");
        if (lines.length == 0) return;

        char delim = ',';
        if (lines[0].contains(";")) delim = ';';
        else if (lines[0].contains("\t")) delim = '\t';
        System.out.println("uploadCSV: detected delimiter = '" + delim + "'");

        int startIdx = 0;
        String firstLine = lines[0].toLowerCase();
        if (firstLine.contains("input") || firstLine.contains("stdin") || firstLine.contains("output")) {
            startIdx = 1;
            System.out.println("uploadCSV: skipping header row: " + lines[0].trim());
        }

        int savedCount = 0;
        for (int i = startIdx; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            List<String> parts = parseCSVLine(line, delim);
            System.out.println("uploadCSV: row " + i + " -> parts size = " + parts.size() + " values: " + parts);
            if (parts.size() >= 2) {
                String stdin = parts.get(0).replace("\\n", "\n").trim();
                String expectedOutput = parts.get(1).replace("\\n", "\n").trim();
                boolean isSample = false;
                if (parts.size() >= 3) {
                    String sampleStr = parts.get(2).toUpperCase();
                    isSample = sampleStr.equals("TRUE") || sampleStr.equals("1");
                }

                if (!expectedOutput.isEmpty()) {
                    TestCase tc = TestCase.builder().problem(problem).stdin(stdin)
                            .expectedOutput(expectedOutput).isSample(isSample).build();
                    testCaseRepository.save(tc);
                    savedCount++;
                }
            }
        }
        System.out.println("uploadCSV: successfully saved " + savedCount + " test cases");
    }

    private List<String> parseCSVLine(String line, char delimiter) {
        List<String> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        for (int i = 0; i < result.size(); i++) {
            String val = result.get(i);
            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1);
            }
            result.set(i, val.trim());
        }
        return result;
    }

    private TestCaseResponse toResponse(TestCase testCase) {
        return TestCaseResponse.builder()
                .id(testCase.getId())
                .problemId(testCase.getProblem().getId())
                .stdin(testCase.getStdin())
                .expectedOutput(testCase.getExpectedOutput())
                .isSample(testCase.isSample())
                .build();
    }
}
