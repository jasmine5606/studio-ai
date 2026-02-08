package com.jasmine.studioai.codereview;

import com.jasmine.studioai.codereview.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/code-review")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CodeReviewController {

    private final CodeReviewService codeReviewService;
    private final AutoPrReviewService autoPrReviewService;
    private final CodeReviewFeedbackService feedbackService;

    @PostMapping("/review")
    public CodeReviewResponse review(@RequestBody CodeReviewRequest request) {
        return codeReviewService.review(request);
    }

    @PostMapping("/unit-test")
    public UnitTestResponse generateUnitTest(@RequestBody UnitTestRequest request) {
        return codeReviewService.generateUnitTest(request);
    }

    @PostMapping("/pr/review")
    public PrReviewResponse reviewPullRequest(@RequestBody PrReviewRequest request) {
        return codeReviewService.reviewPullRequest(request);
    }

    @PostMapping("/pr/auto-review")
    public AutoPrReviewResponse autoReviewPullRequest(@RequestBody AutoPrReviewRequest request) {
        return autoPrReviewService.review(request);
    }

    @PostMapping("/feedback")
    public void feedback(@RequestBody ReviewFeedbackRequest request) {
        feedbackService.record(request);
    }

    @GetMapping("/feedback/stats")
    public CodeReviewFeedbackService.ProfileStats stats(@RequestParam(defaultValue = "default") String profile) {
        return feedbackService.getStats(profile);
    }
}
