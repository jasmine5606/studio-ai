package com.jasmine.studioai.mask;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Slf4j
@Service
public class DataMaskingService {

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
    private static final Pattern API_KEY = Pattern.compile("(sk|api[_-]?key|token|secret)[=:]\\s*[a-zA-Z0-9_-]{20,}", Pattern.CASE_INSENSITIVE);

    public String mask(String text) {
        if (text == null || text.isBlank()) return text;

        String result = text;
        result = PHONE.matcher(result).replaceAll(m -> maskMiddle(m.group(), 3, 4));
        result = EMAIL.matcher(result).replaceAll(m -> maskEmail(m.group()));
        result = ID_CARD.matcher(result).replaceAll(m -> maskMiddle(m.group(), 4, 4));
        result = API_KEY.matcher(result).replaceAll(m -> maskApiKey(m.group()));
        return result;
    }

    public boolean containsSensitiveData(String text) {
        if (text == null || text.isBlank()) return false;
        return PHONE.matcher(text).find()
                || EMAIL.matcher(text).find()
                || ID_CARD.matcher(text).find()
                || API_KEY.matcher(text).find();
    }

    private static String maskMiddle(String s, int keepStart, int keepEnd) {
        if (s.length() <= keepStart + keepEnd) return s;
        String masked = s.substring(0, keepStart) + "*".repeat(s.length() - keepStart - keepEnd) + s.substring(s.length() - keepEnd);
        return masked;
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String maskApiKey(String s) {
        int eq = s.indexOf('=');
        if (eq < 0) eq = s.indexOf(':');
        if (eq < 0) return s;
        return s.substring(0, eq + 1) + " ***MASKED***";
    }
}
