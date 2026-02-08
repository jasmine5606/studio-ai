package com.jasmine.studioai.literature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches paper text (title + abstract) from arXiv Atom API or Crossref.
 */
@Component
@RequiredArgsConstructor
public class ArxivAndCrossrefFetcher {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final Pattern ARXIV_ID = Pattern.compile("(?i)(?:arxiv[.:]\\s*|abs/|pdf/)?(\\d{4}\\.\\d{4,5})(?:v\\d+)?");

    public FetchResult fetchByIdentifier(String identifier) throws IOException {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier is required");
        }
        String raw = identifier.trim();
        String arxiv = extractArxivId(raw);
        if (arxiv != null) {
            return fetchArxiv(arxiv);
        }
        String doi = extractDoi(raw);
        if (doi != null) {
            return fetchCrossref(doi);
        }
        throw new IllegalArgumentException("无法识别 identifier：请提供 arXiv 编号或 DOI");
    }

    private static String extractArxivId(String raw) {
        Matcher m = ARXIV_ID.matcher(raw);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String extractDoi(String raw) {
        String s = raw;
        int idx = s.toLowerCase().indexOf("doi.org/");
        if (idx >= 0) {
            s = s.substring(idx + "doi.org/".length());
        }
        s = s.trim();
        if (s.startsWith("10.") && s.length() > 6) return s;
        return null;
    }

    public FetchResult fetchArxiv(String id) throws IOException {
        String url = "https://export.arxiv.org/api/query?id_list=" + URLEncoder.encode(id, StandardCharsets.UTF_8);
        String xml = httpGet(url);
        String entry = firstEntry(xml);
        if (entry.isBlank()) throw new IllegalStateException("arXiv 未返回条目: " + id);
        String title = firstTag(entry, "title");
        String summary = firstTag(entry, "summary");
        String authors = extractAuthors(entry);
        StringBuilder text = new StringBuilder();
        text.append("Title: ").append(title.trim()).append("\n\n");
        if (!authors.isBlank()) text.append("Authors: ").append(authors).append("\n\n");
        text.append("Abstract:\n").append(summary.trim()).append("\n");
        return new FetchResult("arxiv", id, title.trim(), text.toString());
    }

    public FetchResult fetchCrossref(String doi) throws IOException {
        String enc = URLEncoder.encode(doi, StandardCharsets.UTF_8);
        String url = "https://api.crossref.org/works/" + enc;
        String json = httpGet(url);
        JsonNode root = objectMapper.readTree(json).path("message");
        String title = "";
        if (root.path("title").isArray() && root.path("title").size() > 0) {
            title = root.path("title").get(0).asText("");
        }
        String abs = "";
        if (root.path("abstract").isTextual()) {
            abs = stripHtml(root.path("abstract").asText(""));
        }
        StringBuilder text = new StringBuilder();
        text.append("Title: ").append(title.trim()).append("\n\n");
        if (!abs.isBlank()) {
            text.append("Abstract:\n").append(abs.trim()).append("\n");
        } else {
            text.append("Abstract:\n( Crossref 未提供摘要，请改用 PDF 上传 )\n");
        }
        return new FetchResult("doi", doi, title.trim(), text.toString());
    }

    private String httpGet(String url) throws IOException {
        Request req = new Request.Builder().url(url).header("Accept", "*/*").build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IllegalStateException("HTTP " + resp.code() + " for " + url);
            }
            return resp.body().string();
        }
    }

    private static String firstEntry(String xml) {
        int i = xml.indexOf("<entry>");
        if (i < 0) return "";
        int j = xml.indexOf("</entry>", i);
        if (j < 0) return "";
        return xml.substring(i, j + "</entry>".length());
    }

    private static String firstTag(String xml, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int i = xml.indexOf(open);
        if (i < 0) return "";
        int j = xml.indexOf(close, i);
        if (j < 0) return "";
        return xml.substring(i + open.length(), j).replaceAll("\\s+", " ").trim();
    }

    private static String extractAuthors(String entryXml) {
        StringBuilder sb = new StringBuilder();
        int from = 0;
        while (true) {
            int a = entryXml.indexOf("<name>", from);
            if (a < 0) break;
            int b = entryXml.indexOf("</name>", a);
            if (b < 0) break;
            String name = entryXml.substring(a + "<name>".length(), b).trim();
            if (!name.isBlank()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(name);
            }
            from = b + 1;
        }
        return sb.toString();
    }

    private static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    public record FetchResult(String source, String identifier, String title, String fullText) {
    }
}
