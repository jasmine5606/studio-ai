package com.jasmine.studioai.kb.extract;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Optional Apache Tika-based extractor.
 * <p>
 * Uses reflection to avoid hard dependency: if Tika is not on classpath, throws a helpful error.
 */
public class TikaOptionalExtractor {

    private TikaOptionalExtractor() {
    }

    public static String tryExtract(Path file) {
        try {
            Class<?> tikaClass = Class.forName("org.apache.tika.Tika");
            Object tika = tikaClass.getConstructor().newInstance();
            var parseToString = tikaClass.getMethod("parseToString", InputStream.class);
            try (InputStream in = Files.newInputStream(file)) {
                Object text = parseToString.invoke(tika, in);
                return text == null ? "" : String.valueOf(text);
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("PDF/DOC extraction requires Apache Tika dependency. Please add tika-parsers to pom.xml.");
        } catch (Exception e) {
            throw new IllegalStateException("Tika extraction failed: " + e.getMessage(), e);
        }
    }
}

