package com.jasmine.studioai.media;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/media")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class MediaController {

    private final MediaIngestService mediaIngestService;

    @GetMapping("/bilibili/folders")
    public List<FavoriteFolder> bilibiliFolders(@RequestParam String bindId) {
        return mediaIngestService.listBilibiliFolders(bindId);
    }

    @PostMapping("/bilibili/sync")
    public SyncResponse syncBilibiliFavorites(@RequestBody SyncRequest request) {
        return mediaIngestService.syncBilibiliFavorites(request);
    }

    @PostMapping(value = "/asr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AsrResponse asr(@RequestPart("file") MultipartFile file) {
        return mediaIngestService.transcribe(file);
    }

    @Data
    public static class SyncRequest {
        private Long mediaId;
        private List<Long> selectedMediaIds;
        private Integer max;
        private String bindId;
        private String target;

        public Long getMediaId() {
            if (mediaId != null && mediaId > 0) return mediaId;
            if (target == null) return null;
            String digits = target.replaceAll("\\D+", "");
            if (digits.isBlank()) return null;
            try {
                return Long.parseLong(digits);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    @Data
    public static class FavoriteFolder {
        private long mediaId;
        private String title;
        private int mediaCount;
    }

    @Data
    public static class SyncResponse {
        private boolean success;
        private String message;
        private int ingested;
        private List<SyncedItem> items;
    }

    @Data
    public static class SyncedItem {
        private String title;
        private String url;
    }

    @Data
    public static class AsrResponse {
        private boolean success;
        private String text;
        private String message;
    }
}
