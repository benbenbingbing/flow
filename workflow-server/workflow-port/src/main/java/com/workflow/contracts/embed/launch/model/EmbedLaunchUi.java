package com.workflow.contracts.embed.launch.model;

/**
 * Optional presentation preferences for an Embed launch.
 *
 * @param locale {@code locale}，保存在对象中供后续校验、查询或展示
 * @param theme {@code theme}，保存在对象中供后续校验、查询或展示
 * @param formPresentation 表单展示，保存在对象中供后续校验、查询或展示
 */
public record EmbedLaunchUi(
        String locale,
        String theme,
        String formPresentation) {

    /**
     * Preserves source compatibility for callers that do not select a form presentation.
     * The launch service applies the canonical seamless default at the security boundary.
     *
     * @param locale {@code locale}，保存在对象中供后续校验、查询或展示
     * @param theme {@code theme}，保存在对象中供后续校验、查询或展示
     */
    public EmbedLaunchUi(String locale, String theme) {
        this(locale, theme, null);
    }
}
