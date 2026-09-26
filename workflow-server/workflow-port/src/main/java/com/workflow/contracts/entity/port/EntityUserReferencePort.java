package com.workflow.contracts.entity.port;

import java.util.List;

/**
 * 流程人员解析读取实体用户关系字段时使用的稳定查询能力。
 *
 * <p>字段元数据和记录值均以实体定义及持久化记录为权威来源。</p>
 */
public interface EntityUserReferencePort {

    /**
     * 校验并返回可作为流程人员来源的实体字段元数据。
     *
     * @param entityCode 实体编码
     * @param fieldCode  字段编码
     * @return 已校验的字段元数据
     * @throws IllegalArgumentException 实体或字段不可作为用户关系时抛出
     */
    UserReferenceField requireUserReferenceField(String entityCode, String fieldCode);

    /**
     * 从实体持久化记录读取并归一为已验证用户名。
     *
     * @param entityCode 实体编码
     * @param recordId   实体记录 ID
     * @param fieldCode  字段编码
     * @return 已验证用户名列表；字段未填写时返回空列表
     */
    List<String> readUserKeys(
            String entityCode,
            String recordId,
            String fieldCode);

    /**
     * 实体元数据或记录值导致的可预期人员解析失败。
     *
     * <p>该类型只表达调用方可交给空办理人策略处理的业务结果；基础设施异常保持原类型向上抛出。</p>
     */
    class EntityUserReferenceException extends IllegalArgumentException {

        private final String reasonCode;

        /**
         * 初始化实体用户引用异常，保存构造参数供后续方法使用。
         *
         * @param reasonCode 原因编码依赖，保存到当前对象供后续业务方法调用
         * @param message 消息，保存在对象中供后续校验、查询或展示
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        public EntityUserReferenceException(
                String reasonCode,
                String message) {
            super(message);
            if (reasonCode == null || reasonCode.isBlank()) {
                throw new IllegalArgumentException("实体用户关系失败码不能为空");
            }
            this.reasonCode = reasonCode.trim();
        }

        /**
         * 生成原因编码文本，供后续匹配或展示。
         *
         * @return 处理后的原因编码文本，供调用方比较或展示
         */
        public String reasonCode() {
            return reasonCode;
        }
    }

    /**
     * 已验证的用户关系字段。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param fieldCode 字段编码，后续用于处理用户引用字段时定位或关联目标
     * @param multiple {@code multiple}，保存在对象中供后续校验、查询或展示
     */
    record UserReferenceField(
            String entityCode,
            String fieldCode,
            boolean multiple) {
    }
}
