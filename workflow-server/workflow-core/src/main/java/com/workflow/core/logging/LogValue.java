package com.workflow.core.logging;

/**
 * Formats untrusted values for single-line application logs.
 */
public final class LogValue {

    private static final int MAX_LENGTH = 512;

    private LogValue() {
    }

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

    public static String failureType(Throwable throwable) {
        return throwable == null ? "unknown" : throwable.getClass().getSimpleName();
    }
}
