package com.workflow.embed.application.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class EmbedOriginNormalizerTest {

    @Test
    void normalizesCaseDefaultPortTrailingRootAndUnicodeHost() {
        assertEquals("https://example.com",
                EmbedOriginNormalizer.normalize("HTTPS://Example.COM:443/"));
        assertEquals("https://xn--fsqu00a.xn--0zwm56d:8443",
                EmbedOriginNormalizer.normalize("https://例子.测试:8443"));
        assertEquals("https://[2001:db8::1]",
                EmbedOriginNormalizer.normalize("HTTPS://[2001:DB8::1]:443"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com",
            "https://*.example.com",
            "https://user@example.com",
            "https://example.com/path",
            "https://example.com?next=x",
            "https://example.com#fragment",
            "https://example.com:0",
            "https://example.com:65536",
            "https://example.com:abc",
            "https://example.com:+443",
            "https://2001:db8::1",
            "https://[:]",
            "https://[....]"
    })
    void rejectsNonOriginOrUnsafeValues(String value) {
        EmbedException error = assertThrows(
                EmbedException.class,
                () -> EmbedOriginNormalizer.normalize(value));
        assertEquals(EmbedErrorCode.INVALID_REQUEST, error.getErrorCode());
    }
}
