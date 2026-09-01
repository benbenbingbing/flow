package com.workflow.entity.permission.application;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.data.api.response.EntityDataDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * 查询当前用户是否持有某条记录的未完成待办。
 * 会签时实体 current_task_assignee 只保存其中一人，必须回查 process_task。
 */
@Component
@RequiredArgsConstructor
public class CurrentProcessTaskAssigneeLookup {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 判断用户是否为该记录的当前待办办理人。
     *
     * @param row  业务记录，缺少标识时返回 false
     * @param user 当前用户
     * @return 存在未完成待办时返回 true
     */
    public boolean isCurrentAssignee(EntityDataDTO row, SysUser user) {
        return findActionableTaskId(row, user).isPresent();
    }

    /**
     * 查询当前用户针对该记录可办理的未完成任务 ID。
     *
     * <p>多实例审批会为同一流程节点生成多个兄弟任务，而实体表上的
     * {@code current_task_id} 只能保存其中一个任务。本方法以当前认证用户、
     * 记录身份和流程实例为联合约束回查 {@code process_task}，返回的始终是
     * 当前用户自己的 Flowable taskId，不能使用实体字段中的兄弟任务 ID 代替。</p>
     *
     * @param row  业务记录，缺少标识时返回空
     * @param user 当前认证 Flow 用户
     * @return 当前用户可办理的任务 ID；没有未完成待办时返回空
     */
    public Optional<String> findActionableTaskId(EntityDataDTO row, SysUser user) {
        if (row == null || user == null) {
            return Optional.empty();
        }
        List<String> identities = identities(user);
        if (identities.isEmpty()) {
            return Optional.empty();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT task_id FROM process_task "
                        + "WHERE deleted = 0 AND status = 'todo' "
                        + "AND task_id IS NOT NULL AND task_id <> '' "
                        + "AND assignee_id IN (");
        for (int index = 0; index < identities.size(); index++) {
            if (index > 0) {
                sql.append(',');
            }
            sql.append('?');
            args.add(identities.get(index));
        }
        sql.append(')');
        boolean hasEntityCoordinates =
                StringUtils.hasText(row.getId())
                        && StringUtils.hasText(row.getEntityCode());
        boolean hasProcessCoordinate =
                StringUtils.hasText(row.getProcessInstanceId());
        if (!hasEntityCoordinates && !hasProcessCoordinate) {
            return Optional.empty();
        }
        if (hasEntityCoordinates) {
            sql.append(" AND entity_data_id = ? AND entity_code = ?");
            args.add(row.getId());
            args.add(row.getEntityCode());
        }
        if (hasProcessCoordinate) {
            // 两类坐标同时存在时必须联合约束，不能用 OR 容忍矛盾摘要，
            // 否则可能把另一记录或另一流程实例的同用户任务错误绑定到按钮。
            sql.append(" AND process_instance_id = ?");
            args.add(row.getProcessInstanceId());
        }
        // 同一用户在并行分支也可能同时持有多个任务；按最新本地任务稳定取一个，
        // 后续提交仍由任务服务再次校验办理人与 TODO 状态，避免把查询结果当授权凭据。
        sql.append(" ORDER BY create_time DESC, id DESC LIMIT 1");
        List<String> taskIds = jdbcTemplate.queryForList(
                sql.toString(),
                String.class,
                args.toArray());
        return taskIds == null
                ? Optional.empty()
                : taskIds.stream().filter(StringUtils::hasText).findFirst();
    }

    private List<String> identities(SysUser user) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (StringUtils.hasText(user.getId())) {
            values.add(user.getId());
        }
        if (StringUtils.hasText(user.getUsername())) {
            values.add(user.getUsername());
        }
        return List.copyOf(values);
    }
}
