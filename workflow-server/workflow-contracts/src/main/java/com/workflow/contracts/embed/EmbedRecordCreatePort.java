package com.workflow.contracts.embed;

import java.util.Map;

/**
 * Embed 新建记录到实体域的强类型防腐层端口。
 *
 * <p>调用方只能传入由服务端 Session 和不可变 Embed Release 恢复的目标，以及已经完成
 * External Field Policy 求交和 Context 强制覆盖的数据。实现必须在同一外层业务事务中
 * 重查当前 Flow 用户 CREATE 权限、精确固定 Form Release，并通过统一实体变更管道写入。</p>
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
            EmbedRuntimeFormPort.Target target,
            Map<String, Object> data,
            String idempotencyRecordId) {
    }

    /** 实体写入的最小结果，字段值由 Embed 当前 Output Policy 重新读取和投影。 */
    record CreatedRecord(
            String recordId,
            Long recordVersion) {
    }
}
