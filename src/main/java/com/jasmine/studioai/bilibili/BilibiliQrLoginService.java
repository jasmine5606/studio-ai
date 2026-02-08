package com.jasmine.studioai.bilibili;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Slf4j
@Service
public class BilibiliQrLoginService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final BilibiliAuthStore authStore;

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36";
    private static final String REFERER = "https://www.bilibili.com/";
    private static final String ORIGIN = "https://www.bilibili.com";

    public BilibiliQrLoginService(OkHttpClient httpClient, ObjectMapper objectMapper, BilibiliAuthStore authStore) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.authStore = authStore;
    }

    public GenerateResult generate(String bindId) {
        if (bindId == null || bindId.isBlank()) {
            throw new IllegalArgumentException("bindId is required");
        }

        HttpUrl url = HttpUrl.parse("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
                .newBuilder()
                .addQueryParameter("source", "main_web")
                .build();
        JsonNode root = getJson(url.toString());
        if (root.path("code").asInt(0) != 0) {
            throw new IllegalStateException("Bilibili generate failed: " + root.path("message").asText(""));
        }
        JsonNode data = root.path("data");
        String qrcodeKey = data.path("qrcode_key").asText("");
        String qrcodeUrl = data.path("url").asText("");
        if (qrcodeKey.isBlank() || qrcodeUrl.isBlank()) {
            throw new IllegalStateException("Bilibili generate returned empty key/url");
        }

        String qrPng = toQrPngBase64(qrcodeUrl);

        BilibiliAuthStore.AuthSession session = new BilibiliAuthStore.AuthSession();
        session.setBindId(bindId);
        session.setQrcodeKey(qrcodeKey);
        session.setQrcodeUrl(qrcodeUrl);
        session.setBound(false);
        session.setMessage("waiting");
        authStore.put(bindId, session);

        return new GenerateResult(bindId, qrcodeKey, qrcodeUrl, qrPng);
    }

    public PollResult poll(String bindId) {
        if (bindId == null || bindId.isBlank()) {
            throw new IllegalArgumentException("bindId is required");
        }
        BilibiliAuthStore.AuthSession session = authStore.get(bindId);
        if (session == null || session.getQrcodeKey() == null || session.getQrcodeKey().isBlank()) {
            throw new IllegalStateException("No QR session, please generate first");
        }
        if (session.isBound()) {
            return new PollResult(true, "bound", session.getCookie() != null && !session.getCookie().isBlank());
        }

        HttpUrl url = HttpUrl.parse("https://passport.bilibili.com/x/passport-login/web/qrcode/poll")
                .newBuilder()
                .addQueryParameter("qrcode_key", session.getQrcodeKey())
                .addQueryParameter("source", "main_web")
                .build();
        JsonNode root = getJson(url.toString());
        if (root.path("code").asInt(0) != 0) {
            return new PollResult(false, "error:" + root.path("message").asText(""), false);
        }
        JsonNode data = root.path("data");
        int code = data.path("code").asInt(-1);
        String msg = data.path("message").asText("");

        // Common codes:
        // 86101: not scanned
        // 86090: scanned but not confirmed
        // 86038: expired
        // 0: success
        if (code == 0) {
            String redirectUrl = data.path("url").asText("");
            String cookie = fetchCookiesFromRedirect(redirectUrl);
            session.setCookie(cookie);
            session.setBound(cookie != null && !cookie.isBlank());
            session.setMessage("success");
            authStore.put(bindId, session);
            log.info("Bilibili QR login success: bindId={}, cookieLen={}, bound={}", bindId, cookie != null ? cookie.length() : 0, session.isBound());
            return new PollResult(true, "success", session.isBound());
        }

        if (code == 86038) {
            session.setMessage("expired");
            authStore.put(bindId, session);
            return new PollResult(false, "expired", false);
        }
        if (code == 86090) {
            return new PollResult(false, "scanned", false);
        }
        if (code == 86101) {
            return new PollResult(false, "waiting", false);
        }
        return new PollResult(false, "code=" + code + " " + msg, false);
    }

    public boolean hasCookie(String bindId) {
        BilibiliAuthStore.AuthSession s = authStore.get(bindId);
        return s != null && s.getCookie() != null && !s.getCookie().isBlank();
    }

    public String cookie(String bindId) {
        BilibiliAuthStore.AuthSession s = authStore.get(bindId);
        return s == null ? "" : (s.getCookie() == null ? "" : s.getCookie());
    }

    private JsonNode getJson(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Origin", ORIGIN)
                .header("Referer", REFERER)
                .header("User-Agent", UA)
                .build();
        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IllegalStateException("Bilibili request failed: " + resp.code() + " " + resp.message());
            }
            return objectMapper.readTree(resp.body().string());
        } catch (IOException e) {
            throw new IllegalStateException("Bilibili request failed: " + e.getMessage(), e);
        }
    }

    private String fetchCookiesFromRedirect(String url) {
        if (url == null || url.isBlank()) return "";
        OkHttpClient noRedirect = httpClient.newBuilder().followRedirects(false).followSslRedirects(false).build();

        java.util.LinkedHashMap<String, String> cookieMap = new java.util.LinkedHashMap<>();
        String current = url;

        for (int i = 0; i < 6; i++) {
            Request.Builder builder = new Request.Builder()
                    .url(current)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("User-Agent", UA)
                    .header("Referer", REFERER);

            if (!cookieMap.isEmpty()) {
                builder.header("Cookie", cookieMap.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining("; ")));
            }

            try (Response resp = noRedirect.newCall(builder.build()).execute()) {
                var setCookies = resp.headers("Set-Cookie");
                if (setCookies != null) {
                    for (String c : setCookies) {
                        int semi = c.indexOf(';');
                        String kv = (semi > 0 ? c.substring(0, semi) : c).trim();
                        int eq = kv.indexOf('=');
                        if (eq <= 0) continue;
                        String k = kv.substring(0, eq).trim();
                        String v = kv.substring(eq + 1).trim();
                        if (!k.isBlank()) cookieMap.put(k, v);
                    }
                }

                if (resp.isRedirect()) {
                    String location = resp.header("Location", "");
                    if (location == null || location.isBlank()) break;
                    HttpUrl base = HttpUrl.parse(current);
                    HttpUrl next = base == null ? HttpUrl.parse(location) : base.resolve(location);
                    if (next == null) break;
                    current = next.toString();
                    continue;
                }
                break;
            } catch (IOException e) {
                break;
            }
        }

        if (cookieMap.isEmpty()) return "";
        return cookieMap.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining("; "));
    }

    private static String toQrPngBase64(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 280, 280);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("QR generation failed: " + e.getMessage(), e);
        }
    }

    public record GenerateResult(String bindId, String qrcodeKey, String url, String qrPngBase64) {
    }

    public record PollResult(boolean success, String status, boolean cookieReady) {
    }
}
