package com.workflow.contracts.entity.code.spi;

import com.workflow.contracts.entity.code.EntityCodeGenerationContext;
import com.workflow.contracts.entity.code.EntityCodePreviewContext;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 实体业务编码扩展。业务模块注册为 Spring Bean，实体配置通过稳定 code 选择实现。
 * 实现只负责返回完整编码，不得写入当前业务记录、递归保存实体或发送业务通知。
 */
public interface EntityCodeGeneratorProvider {
    /** 配置引用的稳定标识，格式为大写字母开头的字母、数字、下划线，最长 64 位。 */
    String getCode();

    /** 配置页面展示名称。 */
    String getDisplayName();

    /** 可选择此生成器的实体编码；空集合表示适用于所有动态实体。 */
    default Set<String> supportedEntityCodes() { return Set.of(); }

    /** 参数的 JSON Schema，供管理页面展示及服务端校验；不应包含密码等凭证。 */
    default Map<String, Object> configurationSchema() { return Map.of(); }

    /** 校验无法由 Schema 表达的业务约束；非法配置抛 IllegalArgumentException，不得取号。 */
    default void validateConfiguration(String entityCode, Map<String, Object> configuration) { }

    /**
     * 在业务事务内、当前记录 INSERT 前生成完整编码，返回非空且不超过 100 字符的值。
     * recordId 已分配但当前记录尚未入库，应读取 context.data 而不是按 ID 回查新记录。
     * 失败应抛异常以中止保存。远程取号需自行设置超时；仅在稳定幂等键可用且远端支持
     * 幂等时重试。外部已分配号码不会随本地事务回滚，允许跳号。
     * @param context 服务端准备的只读业务数据、可信身份和可选父记录快照
     * @param configuration 已校验的只读生成器参数
     * @return 完整业务编码，平台不会追加前缀或流水
     */
    String generate(EntityCodeGenerationContext context, Map<String, Object> configuration);

    /** 展示样例，禁止消耗流水或外部写入；不支持预览时返回空，不能调用 generate 兜底。 */
    default Optional<String> preview(EntityCodePreviewContext context, Map<String, Object> configuration) {
        return Optional.empty();
    }
}
