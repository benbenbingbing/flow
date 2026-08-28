package com.workflow.embed.application.session;

import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import java.util.Base64;
import java.util.regex.Pattern;

/** Strict parser for the memory-only opaque Embed Bearer credential. */
public final class EmbedBearerToken {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43,128}");

    private EmbedBearerToken() {
    }

    public static String fromAuthorizationHeader(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.indexOf(' ', 7) >= 0) {
            throw invalid();
        }
        String token = authorization.substring(7);
        if (!TOKEN_PATTERN.matcher(token).matches()) {
            throw invalid();
        }
        try {
            if (Base64.getUrlDecoder().decode(token).length < 32) {
                throw invalid();
            }
        } catch (IllegalArgumentException error) {
            throw invalid();
        }
        return token;
    }

    private static EmbedException invalid() {
        return new EmbedException(
                401,
                EmbedErrorCode.EMBED_SESSION_INVALID,
                "Embed session is invalid");
    }
}
