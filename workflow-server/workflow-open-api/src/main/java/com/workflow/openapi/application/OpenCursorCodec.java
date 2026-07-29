package com.workflow.openapi.application;

import com.workflow.openapi.api.error.OpenApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class OpenCursorCodec {

    private static final int MAX_OFFSET = 100_000;

    public int decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        if (cursor.length() > 512) {
            throw invalidCursor();
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.US_ASCII);
            if (!decoded.startsWith("v1:")) {
                throw new IllegalArgumentException();
            }
            int offset = Integer.parseInt(decoded.substring(3));
            if (offset < 0 || offset > MAX_OFFSET) {
                throw new IllegalArgumentException();
            }
            return offset;
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    public String encode(int offset) {
        if (offset < 0 || offset > MAX_OFFSET) {
            throw new IllegalArgumentException(
                    "Cursor offset is out of range");
        }
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        ("v1:" + offset)
                                .getBytes(StandardCharsets.US_ASCII));
    }

    private OpenApiException invalidCursor() {
        return new OpenApiException(
                400,
                "INVALID_REQUEST",
                "Cursor is invalid");
    }
}
