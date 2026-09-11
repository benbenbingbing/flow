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

    /** 只包含实体域创建记录所需的服务端可信参数。 */
    record CreateCommand(
            Target target,
            Map<String, Object> data,
            String idempotencyRecordId,
            boolean startProcess) {

        public CreateCommand(
                Target target,
                Map<String, Object> data,
                String idempotencyRecordId) {
            this(target, data, idempotencyRecordId, false);
        }
    }

    /** 创建链使用的最小固定坐标；不包含浏览器字段或组件投影。 */
    record Target(
            String entityCode,
            String formId,
            String formReleaseId,
            int formReleaseVersion) {
    }

    /** 实体写入的最小结果；后续读取仍通过原生表单运行时完成。 */
    record CreatedRecord(
            String recordId,
            Long recordVersion) {
    }
}
