package com.jasmine.studioai.lab;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ExperimentSimilarity {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    private ExperimentSimilarity() {
    }

    static double jaccardOnConditions(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() && tb.isEmpty()) return 1.0;
        if (ta.isEmpty() || tb.isEmpty()) return 0.0;
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return union.isEmpty() ? 0.0 : (inter.size() * 1.0 / union.size());
    }

    private static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) return out;
        Matcher m = TOKEN.matcher(text.toLowerCase());
        while (m.find()) {
            String t = m.group();
            if (t.length() >= 2) out.add(t);
        }
        return out;
    }
}
