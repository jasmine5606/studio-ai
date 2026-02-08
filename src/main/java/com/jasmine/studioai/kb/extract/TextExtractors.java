package com.jasmine.studioai.kb.extract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TextExtractors {

    private TextExtractors() {
    }

    public static String extract(Path file, String contentType) throws IOException {
        String name = file.getFileName().toString().toLowerCase();

        if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".java") || name.endsWith(".yml") || name.endsWith(".yaml")) {
            return Files.readString(file, StandardCharsets.UTF_8);
        }

        if (name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".ppt") || name.endsWith(".pptx")) {
            return TikaOptionalExtractor.tryExtract(file);
        }

        if (name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".wav")) {
            // Pipeline hook: audio -> ASR -> text
            // Keep the doc ingestable even if ASR isn't wired yet.
            return "【音频文件】已上传：" + file.getFileName() + "\n\n（待转写：请调用 /api/media/asr 或接入 ASR 管线后自动入库转写文本）";
        }

        // Fallback: try plain text
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
