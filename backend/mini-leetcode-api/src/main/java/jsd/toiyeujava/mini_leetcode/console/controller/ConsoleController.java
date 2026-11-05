package jsd.toiyeujava.mini_leetcode.console.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleExecuteRequest;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleExecuteResponse;
import jsd.toiyeujava.mini_leetcode.console.dto.ConsoleTranscriptResponse;
import jsd.toiyeujava.mini_leetcode.console.service.ConsoleService;
import jsd.toiyeujava.mini_leetcode.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API console tương tác cho các bài OOP.
 *
 * <p>Mọi lệnh console đều chạy trong Docker sandbox của judge worker — không bao giờ
 * chạy trong JVM của API server.
 */
@RestController
@RequestMapping("/api/v1/problems/{problemId}/console")
@Tag(name = "OOP Console", description = "Interactive stdin/stdout console for OOP problems")
@SecurityRequirement(name = "bearerAuth")
public class ConsoleController {

    private final ConsoleService consoleService;

    public ConsoleController(ConsoleService consoleService) {
        this.consoleService = consoleService;
    }

    @PostMapping("/execute")
    @Operation(summary = "Run a console command for an OOP problem",
            description = "Compiles the current editor sources and executes them inside an isolated Docker sandbox. "
                    + "Returns stdout/stderr, exit code, runtime and peak memory.")
    public ResponseEntity<ApiResponse<ConsoleExecuteResponse>> execute(
            @Parameter(description = "Problem ID") @PathVariable Long problemId,
            @Valid @RequestBody ConsoleExecuteRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ConsoleExecuteResponse response = consoleService.execute(currentUser.getId(), problemId, request);
        return ResponseEntity.ok(ApiResponse.success("Console command executed", response));
    }

    @GetMapping("/transcript")
    @Operation(summary = "Get the console transcript for the current session")
    public ResponseEntity<ApiResponse<ConsoleTranscriptResponse>> getTranscript(
            @Parameter(description = "Problem ID") @PathVariable Long problemId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ConsoleTranscriptResponse transcript = consoleService.getTranscript(currentUser.getId(), problemId);
        return ResponseEntity.ok(ApiResponse.success(transcript));
    }

    @DeleteMapping("/transcript")
    @Operation(summary = "Clear the console transcript for the current session")
    public ResponseEntity<Void> clearTranscript(
            @Parameter(description = "Problem ID") @PathVariable Long problemId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        consoleService.clearTranscript(currentUser.getId(), problemId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
