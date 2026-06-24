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
        if (problem.isDeleted()) {
            throw new NotFoundException("Problem not found: " + problemId);
        }

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
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new NotFoundException("Problem not found: " + problemId));
        if (problem.isDeleted()) {
            throw new NotFoundException("Problem not found: " + problemId);
        }
        return testCaseRepository.findByProblemId(problemId).stream()
                .map(this::toTruncatedResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TestCaseResponse get(Long id) {
        TestCase testCase = testCaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Test case not found: " + id));
        return toResponse(testCase);
    }

    @Transactional
    public void delete(Long testCaseId) {
        if (!testCaseRepository.existsById(testCaseId)) {
            throw new NotFoundException("Test case not found: " + testCaseId);
        }
        testCaseRepository.deleteById(testCaseId);
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

    private TestCaseResponse toTruncatedResponse(TestCase testCase) {
        return TestCaseResponse.builder()
                .id(testCase.getId())
                .problemId(testCase.getProblem().getId())
                .stdin(truncate(testCase.getStdin()))
                .expectedOutput(truncate(testCase.getExpectedOutput()))
                .isSample(testCase.isSample())
                .build();
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= 40) {
            return text;
        }
        return text.substring(0, 40) + "...";
    }

    @Transactional
    public void update(Long id, CreateTestCaseRequest request) {
        TestCase testCase = testCaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Test case not found: " + id));
        if (request.getExpectedOutput() == null || request.getExpectedOutput().isBlank()) {
            throw new BadRequestException("expectedOutput is required");
        }
        testCase.setStdin(request.getStdin());
        testCase.setExpectedOutput(request.getExpectedOutput());
        testCase.setSample(request.isSample());
        testCaseRepository.save(testCase);
    }

    @Transactional
    public List<TestCaseResponse> uploadZip(Long problemId, org.springframework.web.multipart.MultipartFile file) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new NotFoundException("Problem not found: " + problemId));
        if (problem.isDeleted()) {
            throw new NotFoundException("Problem not found: " + problemId);
        }

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Zip file is empty or missing");
        }

        java.util.Map<String, String> inputs = new java.util.HashMap<>();
        java.util.Map<String, String> outputs = new java.util.HashMap<>();

        try (java.util.zip.ZipInputStream zipIn = new java.util.zip.ZipInputStream(file.getInputStream())) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zipIn.closeEntry();
                    continue;
                }

                String name = entry.getName();
                String filename = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
                if (filename.isBlank()) {
                    zipIn.closeEntry();
                    continue;
                }

                if (filename.endsWith(".in")) {
                    String key = filename.substring(0, filename.length() - 3);
                    String content = readEntryContent(zipIn);
                    inputs.put(key, content);
                } else if (filename.endsWith(".out")) {
                    String key = filename.substring(0, filename.length() - 4);
                    String content = readEntryContent(zipIn);
                    outputs.put(key, content);
                }
                zipIn.closeEntry();
            }
        } catch (java.io.IOException e) {
            throw new BadRequestException("Failed to read zip file: " + e.getMessage());
        }

        if (outputs.isEmpty()) {
            throw new BadRequestException("The uploaded zip file does not contain any valid .out files");
        }

        // Delete existing test cases for this problem to avoid duplicates
        List<TestCase> existing = testCaseRepository.findByProblemId(problemId);
        testCaseRepository.deleteAll(existing);

        // List<String> sortedKeys = new java.util.ArrayList<>(outputs.keySet());
        // sortedKeys.sort((k1, k2) -> {
        // try {
        // int i1 = Integer.parseInt(k1);
        // int i2 = Integer.parseInt(k2);
        // return Integer.compare(i1, i2);
        // } catch (NumberFormatException e) {
        // return k1.compareTo(k2);
        // }
        // });
        if (inputs.size() != outputs.size()) {
            throw new BadRequestException("Input count and output count is not same.");
        }

        int n = inputs.size();

        List<TestCase> newTestCases = new java.util.ArrayList<>();
        // for (String key : sortedKeys) {
        // String stdin = inputs.getOrDefault(key, "");
        // String expectedOutput = outputs.get(key);
        // if (expectedOutput == null || expectedOutput.isBlank()) {
        // continue;
        // }

        // boolean isSample = key.toLowerCase().contains("sample");

        // TestCase tc = TestCase.builder()
        // .problem(problem)
        // .stdin(stdin)
        // .expectedOutput(expectedOutput)
        // .isSample(isSample)
        // .build();
        // newTestCases.add(tc);
        // }
        for (int i = 1; i <= n; i++) {
            String idx = String.valueOf(i);
            String stdin = inputs.get(idx);
            if (stdin == null) {
                stdin = inputs.get(String.format("%02d", i));
            }
            if (stdin == null) {
                stdin = inputs.get(String.format("%03d", i));
            }

            String expectedOutput = outputs.get(idx);
            if (expectedOutput == null) {
                expectedOutput = outputs.get(String.format("%02d", i));
            }
            if (expectedOutput == null) {
                expectedOutput = outputs.get(String.format("%03d", i));
            }

            if (stdin == null || expectedOutput == null) {
                throw new BadRequestException("No input or output for testcase number " + i);
            }

            boolean isSample = false;

            if (i <= 3) {
                isSample = true;
            }

            TestCase tc = TestCase.builder()
                    .problem(problem)
                    .stdin(stdin)
                    .expectedOutput(expectedOutput)
                    .isSample(isSample)
                    .build();
            newTestCases.add(tc);
        }

        List<TestCase> saved = testCaseRepository.saveAll(newTestCases);
        return saved.stream().map(this::toTruncatedResponse).toList();
    }

    private String readEntryContent(java.util.zip.ZipInputStream zipIn) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = zipIn.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        return bos.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
