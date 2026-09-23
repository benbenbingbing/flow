package com.workflow.embed.domain;

/**
 * Versioned encrypted launch/session context plus its equality-safe HMAC digest.
 *
 * @param ciphertext {@code ciphertext}，保存在对象中供后续校验、查询或展示
 * @param cipherKeyVersion {@code cipher}键版本，保存在对象中供后续校验、查询或展示
 * @param digest 摘要，保存在对象中供后续校验、查询或展示
 * @param digestKeyVersion 摘要键版本，保存在对象中供后续校验、查询或展示
 */
public record ProtectedContext(
        String ciphertext,
        String cipherKeyVersion,
        String digest,
        String digestKeyVersion) {

    /**
     * 生成当前对象的文本表示，供日志和排障使用。
     *
     * @return 转换为后的字符串文本，供调用方比较或展示
     */
    @Override
    public String toString() {
        return "ProtectedContext[ciphertext=<redacted>, cipherKeyVersion="
                + cipherKeyVersion + ", digest=<redacted>, digestKeyVersion="
                + digestKeyVersion + "]";
    }
}
