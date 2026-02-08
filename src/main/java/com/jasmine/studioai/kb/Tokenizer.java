package com.jasmine.studioai.kb;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight tokenizer with basic CJK support:
 * - Latin: split by non-letter/digit, lowercase
 * - CJK: use bigrams for better precision (e.g., "后端" -> token "后端")
 */
public class Tokenizer {

    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> out = new ArrayList<>();

        StringBuilder latin = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isHan(ch)) {
                flushLatin(latin, out);
                cjk.append(ch);
                continue;
            }
            flushCjk(cjk, out);
            if (Character.isLetterOrDigit(ch)) {
                latin.append(Character.toLowerCase(ch));
            } else {
                flushLatin(latin, out);
            }
        }
        flushCjk(cjk, out);
        flushLatin(latin, out);
        return out;
    }

    private static void flushLatin(StringBuilder sb, List<String> out) {
        if (sb.length() == 0) return;
        out.add(sb.toString());
        sb.setLength(0);
    }

    private static void flushCjk(StringBuilder sb, List<String> out) {
        if (sb.length() == 0) return;
        String s = sb.toString();
        if (s.length() == 1) {
            out.add(s);
        } else {
            for (int i = 0; i < s.length() - 1; i++) {
                out.add(s.substring(i, i + 2));
            }
        }
        sb.setLength(0);
    }

    private static boolean isHan(char ch) {
        return Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN;
    }
}
