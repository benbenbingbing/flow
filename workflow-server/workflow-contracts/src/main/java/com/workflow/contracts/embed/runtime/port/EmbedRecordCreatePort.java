package com.workflow.contracts.embed.runtime.port;

import java.util.Map;

/**
 * Embed 新建记录到实体域的强类型防腐层端口。
 *
 * <p>实体域在同一外层业务事务中重新校验权限和精确发布版本。</p>
 */
public interface EmbedRecordCreatePort {

    /**
     * 在调用方已经开启的业务事务中创建记录。
     *
     * @param command 服务端可信创建命令
     * @return 新记录的稳定 ID 与版本信息
     */
    CreatedRecord create(CreateCommand command);

    /**
     * 只包含实体域创建记录所需的服务端可信参数。
     *
     * @param target 目标，保存在对象中供后续校验、查询或展示
     * @param data 数据，后续用于处理创建命令并传递处理结果
     * @param idempotencyRecordId 幂等记录ID，后续用于处理创建命令时定位或关联目标
     * @param startProcess 启动流程，保存在对象中供后续校验、查询或展示
     */
    record CreateCommand(
            Target target,
            Map<String, Object> data,
            String idempotencyRecordId,
            boolean startProcess) {

        /**
         * 初始化创建命令，保存构造参数供后续方法使用。
         *
         * @param target 目标，保存在对象中供后续校验、查询或展示
         * @param data 数据，后续用于初始化创建并传递处理结果
         * @param idempotencyRecordId 幂等记录ID，后续用于初始化创建时定位或关联目标
         */
        public CreateCommand(
                Target target,
                Map<String, Object> data,
                String idempotencyRecordId) {
            this(target, data, idempotencyRecordId, false);
        }
    }

    /**
     * 创建链使用的最小固定坐标；不包含浏览器字段或组件投影。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param formId 表单 ID，后续用于定位已发布表单
     * @param formReleaseId 表单发布版本ID，后续用于处理目标时定位或关联目标
     * @param formReleaseVersion 表单发布版本，保存在对象中供后续校验、查询或展示
     */
    record Target(
            String entityCode,
            String formId,
            String formReleaseId,
            int formReleaseVersion) {
    }

    /**
     * 实体写入的最小结果；后续读取仍通过原生表单运行时完成。
     *
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param recordVersion 记录版本，保存在对象中供后续校验、查询或展示
     */
    record CreatedRecord(
            String recordId,
            Long recordVersion) {
    }
}
