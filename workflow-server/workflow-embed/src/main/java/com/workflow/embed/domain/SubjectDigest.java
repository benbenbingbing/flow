package com.workflow.embed.domain;

/**
 * Versioned HMAC digest used to query an external identity binding.
 *
 * @param value 待处理主体摘要的原始输入，结果供调用方继续使用
 * @param keyVersion 键版本，保存在对象中供后续校验、查询或展示
 */
public record SubjectDigest(String value, String keyVersion) {
}
