package com.jasmine.studioai.lab;

import com.jasmine.studioai.auth.UserContext;
import com.jasmine.studioai.lab.dto.CompareExperimentsResponse;
import com.jasmine.studioai.lab.dto.CreateExperimentRequest;
import com.jasmine.studioai.lab.dto.ExperimentRecordResponse;
import com.jasmine.studioai.lab.dto.ProjectSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Lab experiment log & project dashboard API (file-backed, per logged-in user).
 */
@RestController
@RequestMapping("/api/lab")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class LabExperimentController {

    private final ExperimentRecordService experimentRecordService;

    @PostMapping("/experiments")
    public ExperimentRecordResponse create(@RequestBody CreateExperimentRequest request) {
        return experimentRecordService.create(UserContext.username(), request);
    }

    /**
     * Alias for clients that want an explicit "quick log" path.
     */
    @PostMapping("/experiments/quick")
    public ExperimentRecordResponse quick(@RequestBody CreateExperimentRequest request) {
        return experimentRecordService.create(UserContext.username(), request);
    }

    @GetMapping("/projects")
    public List<ProjectSummaryResponse> projects() {
        return experimentRecordService.listProjects(UserContext.username());
    }

    @GetMapping("/projects/{projectId}/experiments")
    public List<ExperimentRecordResponse> experiments(@PathVariable String projectId) {
        return experimentRecordService.listExperiments(UserContext.username(), projectId);
    }

    @GetMapping("/projects/{projectId}/experiments/{recordId}")
    public ExperimentRecordResponse one(@PathVariable String projectId, @PathVariable String recordId) {
        return experimentRecordService.get(UserContext.username(), projectId, recordId);
    }

    @GetMapping("/projects/{projectId}/search")
    public List<ExperimentRecordResponse> search(@PathVariable String projectId, @RequestParam("q") String q) {
        return experimentRecordService.search(UserContext.username(), projectId, q);
    }

    @GetMapping("/projects/{projectId}/experiments/{recordId}/compare")
    public CompareExperimentsResponse compare(
            @PathVariable String projectId,
            @PathVariable String recordId,
            @RequestParam(defaultValue = "5") int topK) {
        return experimentRecordService.compareSimilar(UserContext.username(), projectId, recordId, topK);
    }
}
