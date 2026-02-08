package com.jasmine.studioai.i18n;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

@Service
public class I18nService {

    public String getMessage(String key, Locale locale) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale != null ? locale : Locale.SIMPLIFIED_CHINESE);
            return bundle.getString(key);
        } catch (Exception e) {
            return key;
        }
    }

    public String getMessage(String key) {
        return getMessage(key, Locale.SIMPLIFIED_CHINESE);
    }

    public String getMessage(String key, String localeStr) {
        Locale locale = switch (localeStr != null ? localeStr.toLowerCase() : "") {
            case "en", "en-us" -> Locale.US;
            case "zh-cn", "zh" -> Locale.SIMPLIFIED_CHINESE;
            case "zh-tw" -> Locale.TRADITIONAL_CHINESE;
            case "ja", "ja-jp" -> Locale.JAPANESE;
            default -> Locale.SIMPLIFIED_CHINESE;
        };
        return getMessage(key, locale);
    }
}
