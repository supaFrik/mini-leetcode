package jsd.toiyeujava.mini_leetcode.problem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jsd.toiyeujava.mini_leetcode.problem.authoring.AuthoringDtos.AuthoringResponse;
import jsd.toiyeujava.mini_leetcode.problem.authoring.AuthoringDtos.GradingValidationResponse;
import jsd.toiyeujava.mini_leetcode.problem.authoring.AuthoringDtos.ReferenceRunRequest;
import jsd.toiyeujava.mini_leetcode.problem.authoring.AuthoringDtos.ReferenceRunResponse;
import jsd.toiyeujava.mini_leetcode.problem.authoring.AuthoringDtos.UpdateGradingConfigRequest;
import jsd.toiyeujava.mini_leetcode.problem.authoring.AuthoringDtos.UpdateReferenceSolutionRequest;
import jsd.toiyeujava.mini_leetcode.problem.authoring.ProblemAuthoringService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Soạn bài console/stdin: cấu hình chấm, lời giải mẫu, sinh output kỳ vọng từ lời giải mẫu.
 * Nằm dưới {@code /api/v1/admin/**} vì {@code GET /api/v1/problems/**} là công khai — các dữ liệu này thì không.
 */
@RestController
@RequestMapping("/api/v1/admin/problems/{problemId}")
@PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
@Tag(name = "Problem authoring", description = "Grading config, reference solution and expected-output generation (INSTRUCTOR/ADMIN)")
public class ProblemAuthoringController {

    private final ProblemAuthoringService service;

    public ProblemAuthoringController(ProblemAuthoringService service) {
        this.service = service;
    }

    @GetMapping("/authoring")
    @Operation(summary = "Grading config and reference solution of a problem")
    public AuthoringResponse get(@PathVariable Long problemId) {
        return service.get(problemId);
    }

    @PostMapping("/grading-config/validate")
    @Operation(summary = "Validate a grading config against the judge's rule schema without saving it")
    public GradingValidationResponse validate(@PathVariable Long problemId,
                                              @RequestBody UpdateGradingConfigRequest request) {
        return service.validateGradingConfig(request.gradingConfig());
    }

    @PutMapping("/grading-config")
    @Operation(summary = "Save (or clear, when empty) the grading config. 400 with 'errors' when invalid; 409 when PUBLISHED")
    public AuthoringResponse updateGradingConfig(@PathVariable Long problemId,
                                                 @RequestBody UpdateGradingConfigRequest request) {
        return service.updateGradingConfig(problemId, request.gradingConfig());
    }

    @PutMapping("/reference-solution")
    @Operation(summary = "Save (or clear, when empty) the reference solution files")
    public AuthoringResponse updateReferenceSolution(@PathVariable Long problemId,
                                                     @RequestBody UpdateReferenceSolutionRequest request) {
        return service.updateReferenceSolution(problemId, request.files());
    }

    @PostMapping("/reference-run")
    @Operation(summary = "Run the saved reference solution on test cases (or ad-hoc inputs) in the judge sandbox",
            description = "With 'apply': true, test cases whose expected output differs are overwritten with the reference output.")
    public ReferenceRunResponse run(@PathVariable Long problemId, @RequestBody(required = false) ReferenceRunRequest request) {
        return service.runReference(problemId, request);
    }
}
