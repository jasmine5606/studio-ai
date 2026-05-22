package com.jasmine.studioai.lab;

import com.jasmine.studioai.lab.dto.CompareExperimentsResponse;
import com.jasmine.studioai.lab.dto.CreateExperimentRequest;
import com.jasmine.studioai.lab.dto.ExperimentRecordResponse;
import com.jasmine.studioai.lab.dto.ProjectSummaryResponse;
import com.jasmine.studioai.lab.internal.StoredExperimentRecord;
import com.jasmine.studioai.lab.internal.StoredProjectMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExperimentRecordService {

    private final ExperimentFileStore fileStore;

    public ExperimentRecordResponse create(String owner, CreateExperimentRequest req) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("owner required");
        if (req == null || req.getProjectId() == null || req.getProjectId().isBlank()) {
            throw new IllegalArgumentException("projectId is required");
        }

        String recordId = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        StoredExperimentRecord s = new StoredExperimentRecord();
        s.setRecordId(recordId);
        s.setProjectId(req.getProjectId().trim());
        s.setProjectName(req.getProjectName() == null ? "" : req.getProjectName().trim());
        s.setOwner(owner);
        s.setCreatedAt(Instant.now().toString());
        s.setConditions(req.getConditions() == null ? "" : req.getConditions());
        s.setConditionsStructured(req.getConditionsStructured() == null ? new java.util.LinkedHashMap<>() : req.getConditionsStructured());
        s.setDataPath(req.getDataPath() == null ? "" : req.getDataPath());
        s.setConclusion(req.getConclusion() == null ? "" : req.getConclusion());
        s.setFailureReason(req.getFailureReason() == null ? "" : req.getFailureReason());
        s.setTags(req.getTags() == null ? new ArrayList<>() : new ArrayList<>(req.getTags()));

        try {
            if (!s.getProjectName().isBlank()) {
                fileStore.upsertProjectMeta(owner, s.getProjectId(), s.getProjectName());
            }
            fileStore.saveRecord(s);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save experiment: " + e.getMessage(), e);
        }
        return toResponse(s);
    }

    public List<ProjectSummaryResponse> listProjects(String owner) {
        try {
            List<ProjectSummaryResponse> out = new ArrayList<>();
            for (String pid : fileStore.listProjectIds(owner)) {
                List<StoredExperimentRecord> recs = fileStore.listProjectRecords(owner, pid);
                Optional<StoredProjectMeta> meta = fileStore.loadProjectMeta(owner, pid);
                ProjectSummaryResponse p = new ProjectSummaryResponse();
                p.setProjectId(pid);
                p.setProjectName(meta.map(StoredProjectMeta::getProjectName).orElse(""));
                p.setExperimentCount(recs.size());
                p.setLastExperimentAt(recs.isEmpty() ? "" : recs.get(0).getCreatedAt());
                out.add(p);
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list projects: " + e.getMessage(), e);
        }
    }

    public List<ExperimentRecordResponse> listExperiments(String owner, String projectId) {
        try {
            return fileStore.listProjectRecords(owner, projectId).stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list experiments: " + e.getMessage(), e);
        }
    }

    public ExperimentRecordResponse get(String owner, String projectId, String recordId) {
        try {
            return fileStore.loadRecord(owner, projectId, recordId)
                    .map(this::toResponse)
                    .orElseThrow(() -> new IllegalArgumentException("record not found"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load record: " + e.getMessage(), e);
        }
    }

    public List<ExperimentRecordResponse> search(String owner, String projectId, String q) {
        if (q == null || q.isBlank()) return listExperiments(owner, projectId);
        String needle = q.trim().toLowerCase();
        return listExperiments(owner, projectId).stream()
                .filter(r -> contains(r, needle))
                .collect(Collectors.toList());
    }

    public CompareExperimentsResponse compareSimilar(String owner, String projectId, String recordId, int topK) {
        ExperimentRecordResponse baselineResp = get(owner, projectId, recordId);
        List<ExperimentRecordResponse> all = listExperiments(owner, projectId);
        int k = Math.max(1, Math.min(20, topK));

        CompareExperimentsResponse out = new CompareExperimentsResponse();
        out.setBaseline(baselineResp);

        String baseCond = baselineResp.getConditions() == null ? "" : baselineResp.getConditions();
        List<CompareExperimentsResponse.SimilarMatch> matches = new ArrayList<>();
        for (ExperimentRecordResponse other : all) {
            if (other.getRecordId().equals(recordId)) continue;
            double sim = ExperimentSimilarity.jaccardOnConditions(baseCond, other.getConditions() == null ? "" : other.getConditions());
            CompareExperimentsResponse.SimilarMatch m = new CompareExperimentsResponse.SimilarMatch();
            m.setRecord(other);
            m.setConditionsSimilarity(sim);
            m.setNote(buildDiffNote(baselineResp, other));
            matches.add(m);
        }
        matches.sort(Comparator.comparingDouble(CompareExperimentsResponse.SimilarMatch::getConditionsSimilarity).reversed());
        out.setSimilar(matches.stream().limit(k).collect(Collectors.toList()));
        return out;
    }

    private static String buildDiffNote(ExperimentRecordResponse a, ExperimentRecordResponse b) {
        StringBuilder sb = new StringBuilder();
        if (!nullEq(a.getDataPath(), b.getDataPath())) {
            sb.append("dataPath 不同; ");
        }
        if (!nullEq(a.getConclusion(), b.getConclusion())) {
            sb.append("conclusion 不同; ");
        }
        if (!nullEq(a.getFailureReason(), b.getFailureReason())) {
            sb.append("failureReason 不同; ");
        }
        if (sb.isEmpty()) return "结构化字段接近，请重点对比结论文本与原始记录";
        return sb.toString().trim();
    }

    private static boolean nullEq(String x, String y) {
        String a = x == null ? "" : x.trim();
        String b = y == null ? "" : y.trim();
        return a.equals(b);
    }

    private static boolean contains(ExperimentRecordResponse r, String needle) {
        return (r.getConditions() != null && r.getConditions().toLowerCase().contains(needle))
                || (r.getConclusion() != null && r.getConclusion().toLowerCase().contains(needle))
                || (r.getFailureReason() != null && r.getFailureReason().toLowerCase().contains(needle))
                || (r.getDataPath() != null && r.getDataPath().toLowerCase().contains(needle))
                || (r.getTags() != null && r.getTags().stream().anyMatch(t -> t != null && t.toLowerCase().contains(needle)));
    }

    private ExperimentRecordResponse toResponse(StoredExperimentRecord s) {
        ExperimentRecordResponse r = new ExperimentRecordResponse();
        r.setRecordId(s.getRecordId());
        r.setProjectId(s.getProjectId());
        r.setProjectName(s.getProjectName());
        r.setOwner(s.getOwner());
        r.setCreatedAt(s.getCreatedAt());
        r.setConditions(s.getConditions());
        r.setConditionsStructured(s.getConditionsStructured() == null ? new java.util.LinkedHashMap<>() : s.getConditionsStructured());
        r.setDataPath(s.getDataPath());
        r.setConclusion(s.getConclusion());
        r.setFailureReason(s.getFailureReason());
        r.setTags(s.getTags() == null ? List.of() : s.getTags());
        return r;
    }
}
