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
        if (row == null || user == null) {
            return false;
        }
        List<String> identities = identities(user);
        if (identities.isEmpty()) {
            return false;
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM process_task "
                        + "WHERE deleted = 0 AND status = 'todo' "
                        + "AND assignee_id IN (");
        for (int index = 0; index < identities.size(); index++) {
            if (index > 0) {
                sql.append(',');
            }
            sql.append('?');
            args.add(identities.get(index));
        }
        sql.append(") AND (");
        boolean hasPredicate = false;
        if (StringUtils.hasText(row.getId()) && StringUtils.hasText(row.getEntityCode())) {
            sql.append("(entity_data_id = ? AND entity_code = ?)");
            args.add(row.getId());
            args.add(row.getEntityCode());
            hasPredicate = true;
        }
        if (StringUtils.hasText(row.getProcessInstanceId())) {
            if (hasPredicate) {
                sql.append(" OR ");
            }
            sql.append("process_instance_id = ?");
            args.add(row.getProcessInstanceId());
            hasPredicate = true;
        }
        if (!hasPredicate) {
            return false;
        }
        sql.append(')');
        Integer count = jdbcTemplate.queryForObject(
                sql.toString(),
                Integer.class,
                args.toArray());
        return count != null && count > 0;
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
