package com.workflow.process.command.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.command.infrastructure.persistence.record.ProcessCommandRecord;
import org.apache.ibatis.annotations.*;

/** 公共流程命令的回执存取，重放读取必须绕过 MyBatis 缓存及旧事务快照。 */
@Mapper
public interface ProcessCommandRecordMapper extends BaseMapper<ProcessCommandRecord> {
    /** 唯一冲突后的当前读只重放，不升级排他锁，避免并发重复插入者相互死锁。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
            <script>
            SELECT * FROM process_command_receipt WHERE id = #{id}
            ${@com.workflow.integration.database.api.query.DatabaseQuerySql@readGuard(_databaseId)}
            </script>
            """)
    ProcessCommandRecord findForReplay(@Param("id") String id);

    /** 只允许当前事务创建的 PENDING 回执完成；重复完成不覆盖原结果。 */
    default int complete(ProcessCommandRecord record) {
        return update(null, Wrappers.<ProcessCommandRecord>lambdaUpdate()
                .eq(ProcessCommandRecord::getId, record.getId())
                .eq(ProcessCommandRecord::getStatus, "PENDING")
                .set(ProcessCommandRecord::getStatus, "SUCCESS")
                .set(ProcessCommandRecord::getProcessInstanceId, record.getProcessInstanceId())
                .set(ProcessCommandRecord::getTaskId, record.getTaskId())
                .set(ProcessCommandRecord::getChanged, record.getChanged())
                .set(ProcessCommandRecord::getResultDocument, record.getResultDocument())
                .set(ProcessCommandRecord::getUpdateTime, record.getUpdateTime()));
    }
}
