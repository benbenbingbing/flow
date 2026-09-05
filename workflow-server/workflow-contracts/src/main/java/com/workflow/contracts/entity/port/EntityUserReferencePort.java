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

        public EntityUserReferenceException(
                String reasonCode,
                String message) {
            super(message);
            if (reasonCode == null || reasonCode.isBlank()) {
                throw new IllegalArgumentException("实体用户关系失败码不能为空");
            }
            this.reasonCode = reasonCode.trim();
        }

        public String reasonCode() {
            return reasonCode;
        }
    }

    /** 已验证的用户关系字段。 */
    record UserReferenceField(
            String entityCode,
            String fieldCode,
            boolean multiple) {
    }
}
