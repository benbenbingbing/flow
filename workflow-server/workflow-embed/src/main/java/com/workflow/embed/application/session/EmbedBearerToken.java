package com.workflow.embed.application.session;

import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import java.util.Base64;
import java.util.regex.Pattern;

/** Strict parser for the memory-only opaque Embed Bearer credential. */
public final class EmbedBearerToken {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43,128}");

    /**
     * 初始化嵌入式{@code bearer}令牌，保存构造参数供后续方法使用。
     */
    private EmbedBearerToken() {
    }

    /**
     * 生成起始授权{@code header}文本，供后续匹配或展示。
     *
     * @param authorization 授权，供本方法处理起始授权{@code header}时使用
     * @return 处理后的起始授权{@code header}文本，供调用方比较或展示
     */
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

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @return 处理后的无效结果，供调用方继续处理
     */
    private static EmbedException invalid() {
        return new EmbedException(
                401,
                EmbedErrorCode.EMBED_SESSION_INVALID,
                "Embed session is invalid");
    }
}
