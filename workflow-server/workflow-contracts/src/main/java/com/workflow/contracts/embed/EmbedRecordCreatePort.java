package com.workflow.contracts.embed;

import java.util.Map;

/**
 * Embed 新建记录到实体域的强类型防腐层端口。
 *
 * <p>调用方只能传入由服务端 Session 和不可变 Embed Release 恢复的目标，以及
 * 已完成通用 JSON 边界检查和 Context 强制覆盖的原生表单数据。字段、组件、
 * 默认值、联动和 BEFORE_SUBMIT 语义只由固定 Published Form 的标准提交链解析。
 * 实现必须在同一外层业务事务中重查当前 Flow 用户 CREATE 权限、精确固定
 * Form Release，并通过统一实体变更管道写入。</p>
 */
public interface EmbedRecordCreatePort {

    /**
     * 在调用方已经开启的业务事务中创建记录。
     *
     * @param command 服务端可信创建命令；不得包含浏览器可控实体、表单或发布坐标
     * @return 新记录的稳定 ID；通用 record_version 上线前版本保持 null
     */
    CreatedRecord create(CreateCommand command);

    /** 只包含实体域执行所需的可信参数。 */
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

    /** 创建链使用的最小固定坐标；不包含任何字段或组件投影。 */
    record Target(
            String entityCode,
            String formId,
            String formReleaseId,
            int formReleaseVersion) {
    }

    /**
     * 实体写入的最小结果；不携带字段或组件投影。外层只据此生成幂等回执，
     * 原生页面后续读取仍走 Flow 标准 Published Form 运行时。
     */
    record CreatedRecord(
            String recordId,
            Long recordVersion) {
    }
}
