package com.jasmine.studioai.bilibili;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Bilibili API client (favorites list).
 * <p>
 * Uses public API endpoints when favorites are publicly accessible.
 * For private favorites or stricter requirements, extend with OAuth/open-platform signing.
 */
@Slf4j
@Component
public class BilibiliApiClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${bilibili.api.base-url:https://api.bilibili.com}")
    private String baseUrl;

    public BilibiliApiClient(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public List<FavoriteMedia> listFavoriteResources(long mediaId, int page, int pageSize) {
        return listFavoriteResources(mediaId, page, pageSize, "");
    }

    public List<FavoriteMedia> listFavoriteResources(long mediaId, int page, int pageSize, String cookie) {
        HttpUrl url = HttpUrl.parse(baseUrl + "/x/v3/fav/resource/list").newBuilder()
                .addQueryParameter("media_id", String.valueOf(mediaId))
                .addQueryParameter("pn", String.valueOf(Math.max(1, page)))
                .addQueryParameter("ps", String.valueOf(Math.max(1, Math.min(50, pageSize))))
                .addQueryParameter("order", "mtime")
                .addQueryParameter("type", "0")
                .build();

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://www.bilibili.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");
        if (cookie != null && !cookie.isBlank()) {
            builder.header("Cookie", cookie);
        }
        Request request = builder.build();

        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IllegalStateException("Bilibili API failed: " + resp.code() + " " + resp.message());
            }
            JsonNode root = objectMapper.readTree(resp.body().string());
            int code = root.path("code").asInt(0);
            if (code != 0) {
                throw new IllegalStateException("Bilibili API error, code=" + code + ", message=" + root.path("message").asText(""));
            }
            JsonNode medias = root.path("data").path("medias");
            List<FavoriteMedia> out = new ArrayList<>();
            if (medias.isArray()) {
                for (JsonNode m : medias) {
                    out.add(new FavoriteMedia(
                            m.path("id").asLong(0L),
                            m.path("bvid").asText(""),
                            m.path("title").asText(""),
                            m.path("intro").asText(""),
                            m.path("upper").path("name").asText(""),
                            m.path("cover").asText("")
                    ));
                }
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("Bilibili API request failed: " + e.getMessage(), e);
        }
    }

    public List<FavoriteFolder> listFavoriteFolders(String cookie) {
        log.info("Bilibili listFavoriteFolders: hasCookie={}", cookie != null && !cookie.isBlank());
        HttpUrl url = HttpUrl.parse(baseUrl + "/x/v3/fav/folder/list4nav").newBuilder().build();
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://www.bilibili.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");
        if (cookie != null && !cookie.isBlank()) {
            builder.header("Cookie", cookie);
        }
        Request request = builder.build();

        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IllegalStateException("Bilibili API failed: " + resp.code() + " " + resp.message());
            }
            JsonNode root = objectMapper.readTree(resp.body().string());
            int code = root.path("code").asInt(0);
            if (code != 0) {
                log.warn("Bilibili listFavoriteFolders error: code={}, msg={}", code, root.path("message").asText(""));
                throw new IllegalStateException("Bilibili API error, code=" + code + ", message=" + root.path("message").asText(""));
            }
            List<FavoriteFolder> out = new ArrayList<>();
            JsonNode arr = root.path("data");
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    out.add(new FavoriteFolder(
                            n.path("id").asLong(0L),
                            n.path("title").asText(""),
                            n.path("media_count").asInt(0)
                    ));
                }
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("Bilibili API request failed: " + e.getMessage(), e);
        }
    }

    public record FavoriteMedia(long avid, String bvid, String title, String intro, String author, String coverUrl) {
    }

    public record FavoriteFolder(long mediaId, String title, int mediaCount) {
    }
}
