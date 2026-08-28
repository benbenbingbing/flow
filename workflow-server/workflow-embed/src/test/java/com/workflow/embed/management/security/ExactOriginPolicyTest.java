package com.workflow.embed.management.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExactOriginPolicyTest {

    private final ExactOriginPolicy policy = new ExactOriginPolicy();

    @Test
    void normalizesCaseDefaultPortAndDuplicates() {
        assertEquals(List.of("https://portal.example.com"), policy.normalizeAll(List.of(
                "HTTPS://Portal.Example.COM:443/",
                "https://portal.example.com")));
    }

    @Test
    void normalizesUnicodeHostToAsciiOrigin() {
        assertEquals("https://xn--fsqu00a.xn--0zwm56d",
                policy.normalize("https://例子.测试"));
    }

    @Test
    void rejectsNonOriginInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.normalize("http://portal.example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.normalize("https://portal.example.com/path"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.normalize("https://portal.example.com?tenant=1"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.normalize("https://*.example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.normalize("https://portal.example.com:0"));
    }
}
