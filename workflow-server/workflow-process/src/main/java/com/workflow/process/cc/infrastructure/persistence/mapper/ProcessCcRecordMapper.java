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
    /**
     * unique_key 的历史记录也阻止重复通知；共享当前读避免插入冲突后的锁升级。
     *
     * @param uniqueKey 唯一键，后续用于授权校验、关联或幂等去重
     * @return 查询后的ID唯一键重放文本，供调用方比较或展示
     */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
            <script>
            SELECT id FROM process_cc_record WHERE unique_key = #{uniqueKey}
            ${@com.workflow.integration.database.api.query.DatabaseQuerySql@readGuard(_databaseId)}
            </script>
            """)
    String findIdByUniqueKeyForReplay(@Param("uniqueKey") String uniqueKey);
    
    /**
     * 根据流程实例ID查询抄送记录
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 流程抄送集合，供调用方遍历或展示
     */
    default List<ProcessCcRecord> findByProcessInstanceId(String processInstanceId) {
        return selectList(Wrappers.<ProcessCcRecord>lambdaQuery()
                .eq(ProcessCcRecord::getProcessInstanceId, processInstanceId)
                .eq(ProcessCcRecord::getDeleted, 0)
                .orderByDesc(ProcessCcRecord::getCreateTime));
    }

    /**
     * 用户 ID 和用户名都属于同一身份，合并在一个 OR 分组内查询抄送可见性。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @return 判断是否存在后的用户结果，供调用方继续处理
     */
    default long existsForUser(String processInstanceId, String userId, String username) {
        return selectCount(Wrappers.<ProcessCcRecord>lambdaQuery()
                .eq(ProcessCcRecord::getProcessInstanceId, processInstanceId)
                .and(user -> user.eq(ProcessCcRecord::getCcUserId, userId)
                        .or().eq(ProcessCcRecord::getCcUserId, username)));
    }

    /**
     * 按唯一键查询流程抄送；结果供后续展示或处理。
     *
     * @param uniqueKey 唯一键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的流程抄送结果，供调用方继续处理
     */
    default ProcessCcRecord findByUniqueKey(String uniqueKey) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<ProcessCcRecord>(1, 1, false), Wrappers.<ProcessCcRecord>lambdaQuery()
                .eq(ProcessCcRecord::getUniqueKey, uniqueKey)).stream().findFirst().orElse(null);
    }
    
    /**
     * 根据抄送人查询抄送记录（分页）
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 流程抄送集合，供调用方遍历或展示
     */
    default List<ProcessCcRecord> findByCcUserId(String userId, int offset, int limit) {
        return selectList(new OffsetPage<>(offset, limit), Wrappers.<ProcessCcRecord>lambdaQuery()
                .eq(ProcessCcRecord::getCcUserId, userId)
                .orderByDesc(ProcessCcRecord::getCreateTime));
    }

    /**
     * 抄送检索复用 Wrapper 条件及框架分页，offset 保留原始行偏移。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param keyword 关键字，供本方法查询抄送用户ID{@code filtered}时使用
     * @param operatorName 用户名称，后续用于身份匹配或操作展示
     * @param startTime 启动时间，后续用于判断有效期或展示该事件的发生时间
     * @param endTime 结束时间，后续用于判断有效期或展示该事件的发生时间
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 流程抄送集合，供调用方遍历或展示
     */
    default List<ProcessCcRecord> findByCcUserIdFiltered(
            String userId, String keyword, String operatorName,
            LocalDateTime startTime, LocalDateTime endTime, int offset, int limit) {
        return selectList(new OffsetPage<>(offset, limit),
                ccFilter(userId, keyword, operatorName, startTime, endTime)
                        .orderByDesc(ProcessCcRecord::getCreateTime));
    }

    /**
     * 统计与列表共用同一筛选，避免两边的关键字分组或时间边界不一致。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param keyword 关键字，作为 {@code selectCount} 的输入影响后续处理
     * @param operatorName 用户名称，后续用于身份匹配或操作展示
     * @param startTime 启动时间，后续用于判断有效期或展示该事件的发生时间
     * @param endTime 结束时间，后续用于判断有效期或展示该事件的发生时间
     * @return 符合条件的抄送用户ID{@code filtered}数量
     */
    default long countByCcUserIdFiltered(
            String userId, String keyword, String operatorName,
            LocalDateTime startTime, LocalDateTime endTime) {
        return selectCount(ccFilter(userId, keyword, operatorName, startTime, endTime));
    }

    /**
     * 构建抄送条件；关键字保留 SQL 通配符，时间窗口采用左闭右开边界。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param keyword 关键字，供本方法处理抄送过滤时使用
     * @param operatorName 用户名称，后续用于身份匹配或操作展示
     * @param startTime 启动时间，后续用于判断有效期或展示该事件的发生时间
     * @param endTime 结束时间，后续用于判断有效期或展示该事件的发生时间
     * @return 处理后的抄送过滤结果，供调用方继续处理
     */
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
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 符合条件的抄送用户ID数量
     */
    default long countByCcUserId(String userId) {
        return selectCount(Wrappers.<ProcessCcRecord>lambdaQuery()
                .eq(ProcessCcRecord::getCcUserId, userId)
                .eq(ProcessCcRecord::getDeleted, 0));
    }
    
    /**
     * 统计未读抄送数
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 符合条件的{@code unread}用户ID数量
     */
    default long countUnreadByUserId(String userId) {
        return selectCount(Wrappers.<ProcessCcRecord>lambdaQuery()
                .eq(ProcessCcRecord::getCcUserId, userId)
                .eq(ProcessCcRecord::getReadStatus, "UNREAD")
                .eq(ProcessCcRecord::getDeleted, 0));
    }
    
    /**
     * 标记为已读
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 标记后的{@code as}读取结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_cc_record SET read_status = 'READ', read_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@currentNow(_databaseId)}
             WHERE id = #{id}
             AND cc_user_id = #{userId}
             AND deleted = 0
            </script>
            """)
    int markAsRead(@Param("id") String id, @Param("userId") String userId);
    
    /**
     * 批量标记为已读
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 标记后的全部{@code as}读取结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_cc_record SET read_status = 'READ', read_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@currentNow(_databaseId)}
             WHERE cc_user_id = #{userId}
             AND read_status = 'UNREAD'
             AND deleted = 0
            </script>
            """)
    int markAllAsRead(@Param("userId") String userId);
}
