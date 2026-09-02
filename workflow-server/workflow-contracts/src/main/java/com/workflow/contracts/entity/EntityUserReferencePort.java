package com.workflow.contracts.entity;

import java.util.List;

/**
 * 流程人员解析器读取实体用户关系字段时使用的跨模块端口。
 *
 * <p>端口始终以实体定义和持久化记录为权威来源，避免流程模块依赖某个
 * 节点表单的字段快照，从而允许字段由当前表单或其他表单维护。</p>
 */
public interface EntityUserReferencePort {

    /**
     * 实体元数据或记录值导致的可预期人员解析失败。
     *
     * <p>端口只用该类型报告调用方可以交给空办理人策略处置的业务结果；
     * 数据库不可用等基础设施异常保持原类型向上抛出。</p>
     */
    class EntityUserReferenceException extends IllegalArgumentException {

        private final String reasonCode;

        public EntityUserReferenceException(
                String reasonCode,
                String message) {
            super(message);
            if (reasonCode == null || reasonCode.isBlank()) {
                throw new IllegalArgumentException(
                        "实体用户关系失败码不能为空");
            }
            this.reasonCode = reasonCode.trim();
        }

        public String reasonCode() {
            return reasonCode;
        }
    }

    /**
     * 校验并返回可作为流程人员来源的实体字段元数据。
     *
     * @param entityCode 实体编码
     * @param fieldCode  字段编码
     * @return 已校验的字段元数据
     * @throws IllegalArgumentException 实体或字段不存在、未发布，或字段不是用户关系时抛出
     */
    UserReferenceField requireUserReferenceField(
            String entityCode,
            String fieldCode);

    /**
     * 从实体持久化记录读取并归一为已验证用户名，结果保持多选字段的配置顺序并去重。
     *
     * @param entityCode 实体编码
     * @param recordId   实体记录 ID
     * @param fieldCode  字段编码
     * @return 已验证的用户名列表；字段未填写时返回空列表
     */
    List<String> readUserKeys(
            String entityCode,
            String recordId,
            String fieldCode);

    /** 已验证的用户关系字段。 */
    record UserReferenceField(
            String entityCode,
            String fieldCode,
            boolean multiple) {
    }
}
