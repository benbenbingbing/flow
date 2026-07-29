package com.workflow.openapi.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.workflow.openapi.api.error.OpenApiException;
import org.junit.jupiter.api.Test;

class OpenCursorCodecTest {

    private final OpenCursorCodec codec = new OpenCursorCodec();

    @Test
    void roundTripsVersionedCursor() {
        assertEquals(200, codec.decode(codec.encode(200)));
    }

    @Test
    void rejectsMalformedOversizedAndOutOfRangeCursors() {
        assertInvalid("not-base64");
        assertInvalid("a".repeat(513));
        assertInvalid(java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        "v1:100001".getBytes(
                                java.nio.charset.StandardCharsets.US_ASCII)));
    }

    private void assertInvalid(String cursor) {
        OpenApiException exception = assertThrows(
                OpenApiException.class,
                () -> codec.decode(cursor));
        assertEquals("INVALID_REQUEST", exception.getErrorCode());
    }
}
