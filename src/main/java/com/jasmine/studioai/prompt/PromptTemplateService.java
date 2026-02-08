package com.jasmine.studioai.prompt;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Renders a prompt template.
     * <p>
     * If profile is not "default", will first try to load a sibling template:
     * <pre>
     * prompts/x/y.txt  -> prompts/x/y.{profile}.txt
     * </pre>
     */
    public String render(String classpathTemplate, String profile, Map<String, String> vars) {
        String normalizedProfile = normalizeProfile(profile);
        String templatePath = resolveProfilePath(classpathTemplate, normalizedProfile);
        String template = load(templatePath);
        return substitute(template, vars);
    }

    private String resolveProfilePath(String path, String profile) {
        if ("default".equals(profile)) return path;
        int dot = path.lastIndexOf('.');
        if (dot <= 0) return path;
        String candidate = path.substring(0, dot) + "." + profile + path.substring(dot);
        if (exists(candidate)) return candidate;
        return path;
    }

    private boolean exists(String path) {
        return new ClassPathResource(path).exists();
    }

    private String load(String path) {
        return cache.computeIfAbsent(path, p -> {
            try {
                var res = new ClassPathResource(p);
                if (!res.exists()) {
                    throw new IllegalStateException("Prompt template not found on classpath: " + p);
                }
                try (var in = res.getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load prompt template: " + p, e);
            }
        });
    }

    private static String substitute(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String key = "{{" + e.getKey() + "}}";
            result = result.replace(key, e.getValue() == null ? "" : e.getValue());
        }
        return result;
    }

    private static String normalizeProfile(String p) {
        if (p == null || p.isBlank()) return "default";
        return p.trim().toLowerCase();
    }

    public static VarsBuilder vars() {
        return new VarsBuilder();
    }

    public static class VarsBuilder {
        private final Map<String, String> map = new HashMap<>();

        public VarsBuilder put(String key, String value) {
            map.put(key, value);
            return this;
        }

        public Map<String, String> build() {
            return map;
        }
    }
}

