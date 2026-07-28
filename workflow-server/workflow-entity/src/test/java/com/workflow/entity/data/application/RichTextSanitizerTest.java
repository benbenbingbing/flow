package com.workflow.entity.data.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichTextSanitizerTest {

    private final RichTextSanitizer sanitizer = new RichTextSanitizer();

    @Test
    void removesExecutableMarkupAndKeepsFormatting() {
        String result = sanitizer.sanitize("""
                <p style="text-align:center;background-image:url(https://evil)">
                  <strong>safe</strong>
                  <img src="javascript:alert(1)" onerror="alert(1)">
                  <a href="javascript:alert(1)">link</a>
                  <script>alert(1)</script>
                </p>
                """);

        assertTrue(result.contains("<strong>safe</strong>"));
        assertTrue(result.contains("text-align: center"));
        assertFalse(result.contains("background-image"));
        assertFalse(result.contains("javascript:"));
        assertFalse(result.contains("onerror"));
        assertFalse(result.contains("<script"));
    }
}
