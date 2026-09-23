package com.workflow.entity.data.infrastructure.persistence.provider;

/**
 * 唯一值候选的保守文本预筛；只证明 ASCII 子集，不取代 FormUniqueRulePolicy 的最终比较。
 * 正则仅由固定结构和逐字符转义构成，用户不能提供量词或正则表达式。
 */
final class FormUniqueTextPrefilter {
    // 显式枚举字符，不用受 locale 影响的 [ -~] 范围。] 必须在首位，- 必须在末位；
    // 反斜杠和左方括号转义兼容 ICU/POSIX。其他产品把类内反斜杠当字面量也不会漏掉 ASCII。
    static final String NON_PRINTABLE_ASCII = nonPrintableAscii();
    private static final int PREFIX_LENGTH = 64;

    private FormUniqueTextPrefilter() { }

    /**
     * 非 ASCII 目标不可能由纯 ASCII 字段归一化得到，只需保留非 ASCII/控制字符分支。
     * 长目标最多预筛 64 字符前缀，控制模式在 Oracle 的 512 字节以内；截短只增加候选，
     * 不截断数据库值，也不能把前缀相同直接认定为唯一冲突。
     */
    static String asciiPattern(String normalizedValue) {
        String value = normalizedValue == null ? "" : normalizedValue;
        if (value.chars().anyMatch(character -> character < 32 || character > 126)) return null;
        var pattern = new StringBuilder("^ *");
        int length = Math.min(value.length(), PREFIX_LENGTH);
        for (int index = 0; index < length; index++) {
            char character = value.charAt(index);
            if (character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z') {
                char lower = Character.toLowerCase(character);
                pattern.append('[').append(lower).append(Character.toUpperCase(lower)).append(']');
            } else {
                if ("\\.^$|?*+()[]{}".indexOf(character) >= 0) pattern.append('\\');
                pattern.append(character);
            }
        }
        if (length == value.length()) pattern.append(" *$");
        return pattern.toString();
    }

    private static String nonPrintableAscii() {
        var pattern = new StringBuilder("[^]");
        for (char character = 32; character <= 126; character++) {
            if (character == ']' || character == '-') continue;
            if (character == '\\' || character == '[') pattern.append('\\');
            pattern.append(character);
        }
        return pattern.append("-]").toString();
    }
}
