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

    /**
     * 清洗{@code rich}文本{@code sanitizer}；结果供调用方的后续步骤使用。
     *
     * @param html {@code html}，作为 {@code Jsoup.clean} 的输入影响后续处理
     * @return 清洗后的{@code rich}文本{@code sanitizer}文本，供调用方比较或展示
     */
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

    /**
     * 清洗{@code style}；结果供调用方的后续步骤使用。
     *
     * @param style {@code style}，供本方法清洗{@code style}时使用
     * @return 清洗后的{@code style}文本，供调用方比较或展示
     */
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

    /**
     * 判断是否安全{@code style}值；判断结果决定调用方的后续分支。
     *
     * @param property 属性，供本方法判断是否安全{@code style}值时使用
     * @param value 待判断是否安全{@code style}值的原始输入，结果供调用方继续使用
     * @return 安全{@code style}值条件成立时为 true，否则为 false
     */
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
