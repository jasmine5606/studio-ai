package com.jasmine.studioai.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jasmine.studioai.bilibili.BilibiliApiClient;
import com.jasmine.studioai.bilibili.BilibiliQrLoginService;
import com.jasmine.studioai.kb.KnowledgeBaseIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaIngestService {

    @Value("${dashscope.asr.api-key:}")
    private String dashscopeAsrApiKey;

    @Value("${dashscope.asr.model:qwen3-asr-flash-filetrans}")
    private String dashscopeAsrModel;

    private final BilibiliApiClient bilibiliApiClient;
    private final KnowledgeBaseIngestService knowledgeBaseIngestService;
    private final BilibiliQrLoginService bilibiliQrLoginService;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public List<MediaController.FavoriteFolder> listBilibiliFolders(String bindId) {
        String cookie = bilibiliQrLoginService.cookie(bindId);
        if (cookie == null || cookie.isBlank()) {
            throw new IllegalStateException("请先扫码登录 B站，再加载收藏夹列表");
        }
        List<BilibiliApiClient.FavoriteFolder> folders = bilibiliApiClient.listFavoriteFolders(cookie);
        List<MediaController.FavoriteFolder> out = new ArrayList<>();
        for (BilibiliApiClient.FavoriteFolder folder : folders) {
            MediaController.FavoriteFolder f = new MediaController.FavoriteFolder();
            f.setMediaId(folder.mediaId());
            f.setTitle(folder.title());
            f.setMediaCount(folder.mediaCount());
            out.add(f);
        }
        return out;
    }

    public MediaController.SyncResponse syncBilibiliFavorites(MediaController.SyncRequest request) {
        MediaController.SyncResponse resp = new MediaController.SyncResponse();
        if (request == null) {
            resp.setSuccess(false);
            resp.setMessage("请求参数不能为空");
            return resp;
        }

        Set<Long> mediaIds = new LinkedHashSet<>();
        if (request.getSelectedMediaIds() != null) {
            for (Long id : request.getSelectedMediaIds()) {
                if (id != null && id > 0) mediaIds.add(id);
            }
        }
        Long single = request.getMediaId();
        if (single != null && single > 0) mediaIds.add(single);
        if (mediaIds.isEmpty()) {
            resp.setSuccess(false);
            resp.setMessage("至少选择一个收藏夹");
            return resp;
        }

        int max = request.getMax() == null ? 50 : Math.max(1, Math.min(500, request.getMax()));
        String cookie = "";
        if (request.getBindId() != null && !request.getBindId().isBlank()) {
            cookie = bilibiliQrLoginService.cookie(request.getBindId());
        }
        if (cookie == null || cookie.isBlank()) {
            resp.setSuccess(false);
            resp.setMessage("请先扫码登录 B站，再同步收藏夹");
            return resp;
        }
        log.info("Bilibili sync: bindId={}, mediaIds={}, cookieLen={}", request.getBindId(), mediaIds, cookie.length());

        int ingested = 0;
        List<MediaController.SyncedItem> previewItems = new ArrayList<>();
        for (Long mediaId : mediaIds) {
            int page = 1;
            int pageSize = 20;
            while (ingested < max) {
                var medias = bilibiliApiClient.listFavoriteResources(mediaId, page, pageSize, cookie);
                if (medias.isEmpty()) break;
                for (var media : medias) {
                    if (ingested >= max) break;
                    String title = safeTrim(media.title());
                    if (title.isBlank()) continue;

                    String pageUrl = buildVideoUrl(media.bvid());
                    String audioUrl = "";
                    String transcript = "";
                    String pipelineStatus = "metadata";

                    try {
                        if (!media.bvid().isBlank()) {
                            audioUrl = resolveBilibiliAudioUrl(media.bvid(), cookie);
                            if (!audioUrl.isBlank()) {
                                transcript = transcribeByFileUrl(audioUrl);
                                pipelineStatus = transcript.isBlank() ? "audio-only" : "audio+asr";
                            }
                        }
                    } catch (Exception e) {
                        pipelineStatus = "metadata+fallback";
                    }

                    String text = buildIngestText(media, pageUrl, audioUrl, transcript, pipelineStatus, mediaId);
                    knowledgeBaseIngestService.ingestText(
                            "bilibili-" + (media.bvid().isBlank() ? media.avid() : media.bvid()) + ".txt",
                            text,
                            "bilibili:favorites:" + mediaId,
                            "bilibili,favorites,asr"
                    );
                    if (previewItems.size() < 20) {
                        MediaController.SyncedItem item = new MediaController.SyncedItem();
                        item.setTitle(title);
                        item.setUrl(pageUrl);
                        previewItems.add(item);
                    }
                    ingested++;
                }
                page++;
            }
        }

        resp.setSuccess(true);
        resp.setIngested(ingested);
        resp.setItems(previewItems);
        resp.setMessage("已同步并入库 " + ingested + " 条，支持多收藏夹勾选");
        return resp;
    }

    public MediaController.AsrResponse transcribe(MultipartFile file) {
        MediaController.AsrResponse resp = new MediaController.AsrResponse();
        if (file == null || file.isEmpty()) {
            resp.setSuccess(false);
            resp.setMessage("文件不能为空");
            return resp;
        }
        if (dashscopeAsrApiKey == null || dashscopeAsrApiKey.isBlank()) {
            resp.setSuccess(false);
            resp.setMessage("未配置 ASR key，请设置 DASHSCOPE_ASR_API_KEY");
            return resp;
        }
        try {
            String text = file.getContentType() != null && file.getContentType().startsWith("text/")
                    ? new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    : "";
            if (!text.isBlank()) {
                resp.setSuccess(true);
                resp.setText(text);
                resp.setMessage("已识别文本内容");
                return resp;
            }
            resp.setSuccess(false);
            resp.setMessage("音视频直接上传转写暂不支持，请使用 B站同步入口自动抽取音频并转写");
            return resp;
        } catch (Exception e) {
            resp.setSuccess(false);
            resp.setMessage("转写失败：" + e.getMessage());
            return resp;
        }
    }

    private String resolveBilibiliAudioUrl(String bvid, String cookie) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url("https://www.bilibili.com/video/" + bvid)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", "https://www.bilibili.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");
        if (cookie != null && !cookie.isBlank()) builder.header("Cookie", cookie);

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            String html = response.body().string();
            String playInfoJson = extractAssignedJson(html, "window.__playinfo__=");
            if (playInfoJson.isBlank()) return "";
            JsonNode root = objectMapper.readTree(playInfoJson);
            JsonNode audioArray = root.path("data").path("dash").path("audio");
            if (!audioArray.isArray() || audioArray.isEmpty()) return "";
            for (JsonNode audio : audioArray) {
                String u = firstNonBlank(
                        audio.path("baseUrl").asText(""),
                        audio.path("base_url").asText(""),
                        firstArrayValue(audio.path("backupUrl"))
                );
                if (!u.isBlank()) return u;
            }
            return "";
        }
    }

    private String transcribeByFileUrl(String fileUrl) throws IOException, InterruptedException {
        if (dashscopeAsrApiKey == null || dashscopeAsrApiKey.isBlank()) return "";
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", dashscopeAsrModel == null || dashscopeAsrModel.isBlank() ? "qwen3-asr-flash-filetrans" : dashscopeAsrModel);
        requestBody.set("input", objectMapper.createObjectNode().put("file_url", fileUrl));
        requestBody.set("parameters", objectMapper.createObjectNode());

        Request create = new Request.Builder()
                .url("https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcription")
                .header("Authorization", "Bearer " + dashscopeAsrApiKey)
                .header("X-DashScope-Async", "enable")
                .header("Content-Type", "application/json")
                .post(okhttp3.RequestBody.create(requestBody.toString(), okhttp3.MediaType.parse("application/json")))
                .build();

        String taskId;
        try (Response response = httpClient.newCall(create).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            JsonNode root = objectMapper.readTree(response.body().string());
            taskId = root.path("output").path("task_id").asText("");
            if (taskId.isBlank()) return "";
        }

        long deadline = System.currentTimeMillis() + Duration.ofMinutes(3).toMillis();
        while (System.currentTimeMillis() < deadline) {
            JsonNode task = fetchDashScopeTask(taskId);
            String status = task.path("output").path("task_status").asText("");
            if ("SUCCEEDED".equalsIgnoreCase(status)) {
                String transcriptionUrl = task.path("output").path("results").path(0).path("transcription_url").asText("");
                if (transcriptionUrl.isBlank()) transcriptionUrl = task.path("output").path("results").path(0).path("url").asText("");
                return transcriptionUrl.isBlank() ? "" : fetchTranscriptionText(transcriptionUrl);
            }
            if ("FAILED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)) return "";
            Thread.sleep(3000);
        }
        return "";
    }

    private JsonNode fetchDashScopeTask(String taskId) throws IOException {
        Request request = new Request.Builder()
                .url("https://dashscope.aliyuncs.com/api/v1/tasks/" + taskId)
                .header("Authorization", "Bearer " + dashscopeAsrApiKey)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("DashScope task query failed");
            return objectMapper.readTree(response.body().string());
        }
    }

    private String fetchTranscriptionText(String transcriptionUrl) throws IOException {
        Request request = new Request.Builder().url(transcriptionUrl).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            JsonNode root = objectMapper.readTree(response.body().string());
            StringBuilder out = new StringBuilder();
            JsonNode segments = root.path("segments");
            if (segments.isArray()) {
                for (JsonNode segment : segments) {
                    String text = segment.path("text").asText("");
                    if (!text.isBlank()) {
                        if (!out.isEmpty()) out.append('\n');
                        out.append(text.trim());
                    }
                }
            }
            if (out.isEmpty() && root.path("text").isTextual()) return root.path("text").asText("");
            return out.toString();
        }
    }

    private String buildIngestText(BilibiliApiClient.FavoriteMedia media,
                                   String pageUrl,
                                   String audioUrl,
                                   String transcript,
                                   String pipelineStatus,
                                   long mediaId) {
        StringBuilder sb = new StringBuilder();
        sb.append("title: ").append(safeTrim(media.title())).append('\n');
        sb.append("author: ").append(safeTrim(media.author())).append('\n');
        sb.append("url: ").append(pageUrl).append('\n');
        sb.append("favorite_media_id: ").append(mediaId).append('\n');
        sb.append("pipeline: ").append(pipelineStatus).append('\n');
        if (!audioUrl.isBlank()) sb.append("audio_url: ").append(audioUrl).append('\n');
        sb.append("\nintro:\n").append(safeTrim(media.intro())).append('\n');
        if (transcript != null && !transcript.isBlank()) {
            sb.append("\nasr:\n").append(transcript.trim()).append('\n');
        }
        return sb.toString();
    }

    private String extractAssignedJson(String html, String prefix) {
        if (html == null || html.isBlank()) return "";
        int idx = html.indexOf(prefix);
        if (idx < 0) return "";
        int start = html.indexOf('{', idx);
        if (start < 0) return "";
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < html.length(); i++) {
            char c = html.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) return html.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    private static String firstArrayValue(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) return "";
        JsonNode first = node.get(0);
        return first == null ? "" : first.asText("");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static String buildVideoUrl(String bvid) {
        return bvid == null || bvid.isBlank() ? "" : "https://www.bilibili.com/video/" + bvid;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
