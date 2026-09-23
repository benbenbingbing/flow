package com.workflow.core.logging;

/**
 * Formats untrusted values for single-line application logs.
 */
public final class LogValue {

    private static final int MAX_LENGTH = 512;

    /**
     * 初始化日志值，保存构造参数供后续方法使用。
     */
    private LogValue() {
    }

    /**
     * 生成安全文本，供后续匹配或展示。
     *
     * @param value 待处理安全的原始输入，结果供调用方继续使用
     * @return 处理后的安全文本，供调用方比较或展示
     */
    public static String safe(Object value) {
        if (value == null) {
            return "null";
        }
        String text = String.valueOf(value);
        StringBuilder result = new StringBuilder(Math.min(text.length(), MAX_LENGTH));
        int index = 0;
        for (; index < text.length() && result.length() < MAX_LENGTH; index++) {
            char character = text.charAt(index);
            String rendered;
            if (character == '\r') {
                rendered = "\\r";
            } else if (character == '\n') {
                rendered = "\\n";
            } else if (character == '\t') {
                rendered = "\\t";
            } else if (Character.isISOControl(character)) {
                rendered = "?";
            } else {
                rendered = String.valueOf(character);
            }
            if (result.length() + rendered.length() > MAX_LENGTH) {
                break;
            }
            result.append(rendered);
        }
        if (index >= text.length()) {
            return result.toString();
        }
        return result.substring(0, Math.max(0, MAX_LENGTH - 3)) + "...";
    }

    /**
     * 生成失败类型文本，供后续匹配或展示。
     *
     * @param throwable {@code throwable}，供本方法处理失败类型时使用
     * @return 处理后的失败类型文本，供调用方比较或展示
     */
    public static String failureType(Throwable throwable) {
        return throwable == null ? "unknown" : throwable.getClass().getSimpleName();
    }
}
