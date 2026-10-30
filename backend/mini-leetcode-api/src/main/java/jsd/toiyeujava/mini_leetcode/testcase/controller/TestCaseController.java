package jsd.toiyeujava.mini_leetcode.testcase.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jsd.toiyeujava.mini_leetcode.testcase.dto.TestCaseResponse;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.testcase.service.TestCaseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/problems/{problemId}/test-cases")
@Tag(name = "Test Cases", description = "Test Case Upload and Management APIs")
public class TestCaseController {

    private final TestCaseService testCaseService;

    public TestCaseController(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Upload a testcase pair (input and output files)",
            description = "Streams testcase input and expected output directly to MinIO and records metadata. Requires INSTRUCTOR or ADMIN role.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Test case uploaded and registered"),
            @ApiResponse(responseCode = "400", description = "Missing or empty files"),
            @ApiResponse(responseCode = "404", description = "Problem not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<TestCaseResponse> uploadTestCase(
            @PathVariable Long problemId,
            @Parameter(description = "Input data file for the test case")
            @RequestPart("inputFile") MultipartFile inputFile,
            @Parameter(description = "Expected output data file for the test case")
            @RequestPart("outputFile") MultipartFile outputFile,
            @Parameter(description = "Whether this test case is hidden from contestants")
            @RequestParam(value = "isHidden", defaultValue = "true") Boolean isHidden,
            @Parameter(description = "Ordering index of this test case (defaults to next sequential index)")
            @RequestParam(value = "orderIndex", required = false) Integer orderIndex) {
        TestCaseResponse response = testCaseService.addTestCase(problemId, inputFile, outputFile, isHidden, orderIndex);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List test cases for a problem",
            description = "Contestants/Users only receive public test cases (is_hidden = false). Instructors and Admins receive all test cases.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of test cases"),
            @ApiResponse(responseCode = "404", description = "Problem not found")
    })
    public ResponseEntity<List<TestCaseResponse>> getTestCases(
            @PathVariable Long problemId,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean includeHidden = principal != null &&
                (principal.getRole() == Role.INSTRUCTOR || principal.getRole() == Role.ADMIN);

        List<TestCaseResponse> responses = testCaseService.getTestCases(problemId, includeHidden);
        return ResponseEntity.ok(responses);
    }

    @PutMapping(value = "/{testCaseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Update a test case",
            description = "Replaces the input/output files and/or the visibility and order of a test case. "
                    + "Object keys are immutable: new files are written under new keys and the old objects are "
                    + "removed only after the database transaction commits. Test cases of a PUBLISHED problem are frozen.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Test case updated"),
            @ApiResponse(responseCode = "400", description = "Missing or empty files, or test case belongs to another problem"),
            @ApiResponse(responseCode = "404", description = "Problem or test case not found"),
            @ApiResponse(responseCode = "409", description = "Test case is frozen because the problem is published"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<TestCaseResponse> updateTestCase(
            @PathVariable Long problemId,
            @PathVariable Long testCaseId,
            @Parameter(description = "New input data file (optional; keeps the current file when omitted)")
            @RequestPart(value = "inputFile", required = false) MultipartFile inputFile,
            @Parameter(description = "New expected output data file (optional; keeps the current file when omitted)")
            @RequestPart(value = "outputFile", required = false) MultipartFile outputFile,
            @Parameter(description = "Whether this test case is hidden from contestants")
            @RequestParam(value = "isHidden", required = false) Boolean isHidden,
            @Parameter(description = "New ordering index of this test case")
            @RequestParam(value = "orderIndex", required = false) Integer orderIndex) {
        TestCaseResponse response = testCaseService.updateTestCase(
                problemId, testCaseId, inputFile, outputFile, isHidden, orderIndex);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{testCaseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Delete a test case", description = "Removes a test case by ID. Requires INSTRUCTOR or ADMIN role.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Test case deleted"),
            @ApiResponse(responseCode = "404", description = "Test case not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<Void> deleteTestCase(
            @PathVariable Long problemId,
            @PathVariable Long testCaseId) {
        testCaseService.deleteTestCase(problemId, testCaseId);
        return ResponseEntity.noContent().build();
    }
}
