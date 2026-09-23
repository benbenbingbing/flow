package com.workflow.process.cc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程抄送记录 Mapper
 */
@Mapper
public interface ProcessCcRecordMapper extends BaseMapper<ProcessCcRecord> {
    /** unique_key 的历史记录也阻止重复通知；共享当前读避免插入冲突后的锁升级。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
            <script>
            SELECT id FROM process_cc_record WHERE unique_key = #{uniqueKey}
            ${@com.workflow.integration.database.api.DatabaseQuerySql@readGuard(_databaseId)}
            </script>
            """)
    String findIdByUniqueKeyForReplay(@Param("uniqueKey") String uniqueKey);
    
    /**
     * 根据流程实例ID查询抄送记录
     */
    default List<ProcessCcRecord> findByProcessInstanceId(String processInstanceId) {
        return selectList(Wrappers.<ProcessCcRecord>lambdaQuery()
                .eq(ProcessCcRecord::getProcessInstanceId, processInstanceId)
                .eq(ProcessCcRecord::getDeleted, 0)
                .orderByDesc(ProcessCcRecord::getCreateTime));
    }

    /** 用户 ID 和用户名都属于同一身份，合并在一个 OR 分组内查询抄送可见性。 */
    default long existsForUser(String processInstanceId, String userId, String username) {
        return selectCount(Wrappers.<ProcessCcRecord>lambdaQuery()
                .eq(ProcessCcRecord::getProcessInstanceId, processInstanceId)
                .and(user -> user.eq(ProcessCcRecord::getCcUserId, userId)
                        .or().eq(ProcessCcRecord::getCcUserId, username)));
    }

    default ProcessCcRecord findByUniqueKey(String uniqueKey) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<ProcessCcRecord>(1, 1, false), Wrappers.<ProcessCcRecord>lambdaQuery()
                .eq(ProcessCcRecord::getUniqueKey, uniqueKey)).stream().findFirst().orElse(null);
    }
    
    /**
     * 根据抄送人查询抄送记录（分页）
     */
    default List<ProcessCcRecord> findByCcUserId(String userId, int offset, int limit) {
        return selectList(new OffsetPage<>(offset, limit), Wrappers.<ProcessCcRecord>lambdaQuery()
                .eq(ProcessCcRecord::getCcUserId, userId)
                .orderByDesc(ProcessCcRecord::getCreateTime));
    }

    /** 抄送检索复用 Wrapper 条件及框架分页，offset 保留原始行偏移。 */
    default List<ProcessCcRecord> findByCcUserIdFiltered(
            String userId, String keyword, String operatorName,
            LocalDateTime startTime, LocalDateTime endTime, int offset, int limit) {
        return selectList(new OffsetPage<>(offset, limit),
                ccFilter(userId, keyword, operatorName, startTime, endTime)
                        .orderByDesc(ProcessCcRecord::getCreateTime));
    }

    /** 统计与列表共用同一筛选，避免两边的关键字分组或时间边界不一致。 */
    default long countByCcUserIdFiltered(
            String userId, String keyword, String operatorName,
            LocalDateTime startTime, LocalDateTime endTime) {
        return selectCount(ccFilter(userId, keyword, operatorName, startTime, endTime));
    }

    /** 构建抄送条件；关键字保留 SQL 通配符，时间窗口采用左闭右开边界。 */
    private static LambdaQueryWrapper<ProcessCcRecord> ccFilter(
            String userId, String keyword, String operatorName,
            LocalDateTime startTime, LocalDateTime endTime) {
        return Wrappers.<ProcessCcRecord>lambdaQuery()
                .eq(ProcessCcRecord::getCcUserId, userId)
                .and(keyword != null && !keyword.isEmpty(), text -> text
                        .like(ProcessCcRecord::getProcessName, keyword)
                        .or().like(ProcessCcRecord::getDataName, keyword)
                        .or().like(ProcessCcRecord::getNodeName, keyword)
                        .or().like(ProcessCcRecord::getBusinessKey, keyword)
                        .or().like(ProcessCcRecord::getComment, keyword))
                .like(operatorName != null && !operatorName.isEmpty(),
                        ProcessCcRecord::getOperatorName, operatorName)
                .ge(startTime != null, ProcessCcRecord::getCreateTime, startTime)
                .lt(endTime != null, ProcessCcRecord::getCreateTime, endTime);
    }
    
    /**
     * 统计抄送人的抄送记录数
     */
    default long countByCcUserId(String userId) {
        return selectCount(Wrappers.<ProcessCcRecord>lambdaQuery()
                .eq(ProcessCcRecord::getCcUserId, userId)
                .eq(ProcessCcRecord::getDeleted, 0));
    }
    
    /**
     * 统计未读抄送数
     */
    default long countUnreadByUserId(String userId) {
        return selectCount(Wrappers.<ProcessCcRecord>lambdaQuery()
                .eq(ProcessCcRecord::getCcUserId, userId)
                .eq(ProcessCcRecord::getReadStatus, "UNREAD")
                .eq(ProcessCcRecord::getDeleted, 0));
    }
    
    /**
     * 标记为已读
     */
    @Update("""
            <script>
            UPDATE process_cc_record SET read_status = 'READ', read_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@currentNow(_databaseId)}
             WHERE id = #{id}
             AND cc_user_id = #{userId}
             AND deleted = 0
            </script>
            """)
    int markAsRead(@Param("id") String id, @Param("userId") String userId);
    
    /**
     * 批量标记为已读
     */
    @Update("""
            <script>
            UPDATE process_cc_record SET read_status = 'READ', read_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@currentNow(_databaseId)}
             WHERE cc_user_id = #{userId}
             AND read_status = 'UNREAD'
             AND deleted = 0
            </script>
            """)
    int markAllAsRead(@Param("userId") String userId);
}
