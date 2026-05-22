package com.jasmine.studioai.kb.split;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple semantic-ish chunker:
 * - split by blank lines first (paragraphs)
 * - then merge paragraphs to fit maxChars window
 */
@Component
public class TextChunker {

    private final int maxChars = 1200;
    private final int minChars = 200;

    public List<String> chunk(String text) {
        if (text == null) return List.of();
        String normalized = text.replace("\r\n", "\n");
        String[] paragraphs = normalized.split("\\n\\s*\\n+");

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String p : paragraphs) {
            String para = p.trim();
            if (para.isEmpty()) continue;

            if (current.length() == 0) {
                current.append(para);
                continue;
            }

            if (current.length() + 2 + para.length() <= maxChars) {
                current.append("\n\n").append(para);
            } else {
                chunks.add(current.toString());
                current.setLength(0);
                current.append(para);
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        // Post-process: split very large chunks hard
        List<String> finalChunks = new ArrayList<>();
        for (String c : chunks) {
            if (c.length() <= maxChars * 2) {
                finalChunks.add(c);
                continue;
            }
            for (int i = 0; i < c.length(); i += maxChars) {
                finalChunks.add(c.substring(i, Math.min(c.length(), i + maxChars)));
            }
        }

        // Remove too small tail chunks by merging if possible
        if (finalChunks.size() >= 2) {
            String last = finalChunks.get(finalChunks.size() - 1);
            if (last.length() < minChars) {
                String prev = finalChunks.get(finalChunks.size() - 2);
                if (prev.length() + 2 + last.length() <= maxChars * 2) {
                    finalChunks.set(finalChunks.size() - 2, prev + "\n\n" + last);
                    finalChunks.remove(finalChunks.size() - 1);
                }
            }
        }
        return finalChunks;
    }
}

