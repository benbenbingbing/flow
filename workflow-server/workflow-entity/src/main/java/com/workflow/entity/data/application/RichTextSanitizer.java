package com.workflow.entity.data.application;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Server-side allowlist sanitizer for persisted rich text.
 */
@Component
public class RichTextSanitizer {

    private static final Set<String> SAFE_STYLE_PROPERTIES = Set.of(
            "color",
            "background-color",
            "font-size",
            "text-align");

    private static final Safelist ALLOWLIST = Safelist.relaxed()
            .addTags("div", "span", "font", "hr")
            .addAttributes(":all", "class", "title", "style")
            .addAttributes("font", "color", "face", "size")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https");

    public String sanitize(String html) {
        if (!StringUtils.hasText(html)) {
            return html;
        }
        String cleaned = Jsoup.clean(
                html,
                "",
                ALLOWLIST,
                new Document.OutputSettings().prettyPrint(false));
        Document document = Jsoup.parseBodyFragment(cleaned);
        document.outputSettings().prettyPrint(false);
        for (Element element : document.select("[style]")) {
            String safeStyle = sanitizeStyle(element.attr("style"));
            if (safeStyle.isEmpty()) {
                element.removeAttr("style");
            } else {
                element.attr("style", safeStyle);
            }
        }
        return document.body().html();
    }

    private String sanitizeStyle(String style) {
        List<String> safe = new ArrayList<>();
        for (String declaration : style.split(";")) {
            int separator = declaration.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String property = declaration.substring(0, separator)
                    .trim()
                    .toLowerCase(Locale.ROOT);
            String value = declaration.substring(separator + 1).trim();
            if (SAFE_STYLE_PROPERTIES.contains(property)
                    && isSafeStyleValue(property, value)) {
                safe.add(property + ": " + value);
            }
        }
        return String.join("; ", safe);
    }

    private boolean isSafeStyleValue(String property, String value) {
        if (value.length() > 40
                || value.contains("\\")
                || value.toLowerCase(Locale.ROOT).contains("url")
                || value.toLowerCase(Locale.ROOT).contains("expression")) {
            return false;
        }
        return switch (property) {
            case "text-align" -> value.matches("(?i)left|right|center|justify");
            case "font-size" -> value.matches("(?i)(?:[1-9]\\d?|100)(?:px|%|em|rem)");
            default -> value.matches(
                    "(?i)#[0-9a-f]{3,8}|[a-z]{1,20}|rgba?\\([0-9.,%\\s]+\\)");
        };
    }
}
