package com.workflow.process.task.infrastructure.persistence.mapper;

import com.workflow.integration.database.api.query.DatabaseQuerySql;
import com.workflow.process.task.application.TaskInboxQuery;
import org.apache.ibatis.builder.annotation.ProviderContext;
import java.util.Map;

/** 工作台查询只返回摘要列；候选身份用 EXISTS，避免多组命中造成重复行或错误总数。 */
public class TaskInboxSqlProvider {
    private static final String STARTER = """
            CASE WHEN COALESCE(su.username,si.username) IS NULL THEN pt.start_user_id
                 WHEN COALESCE(su.nickname,si.nickname) IS NULL OR TRIM(COALESCE(su.nickname,si.nickname))=''
                   OR %s
                 THEN COALESCE(su.username,si.username)
                 ELSE CONCAT(CONCAT(COALESCE(su.nickname,si.nickname),'('),CONCAT(COALESCE(su.username,si.username),')')) END
            """;
    private static final String ENGINE_JOIN = " LEFT JOIN ACT_RU_TASK ft ON ft.ID_=pt.task_id ";
    private static final String STARTER_JOINS = """
            LEFT JOIN sys_user su ON su.username=pt.start_user_id AND su.deleted=0
            LEFT JOIN sys_user si ON su.id IS NULL AND si.id=pt.start_user_id AND si.deleted=0
            """;
    private static final String LOCAL_CANDIDATES = """
            (EXISTS (SELECT 1 FROM process_task_candidate_user cu
                      WHERE cu.process_task_id=pt.id AND cu.user_id IN (u.id,u.username))
             OR EXISTS (SELECT 1 FROM process_task_candidate_group cg WHERE cg.process_task_id=pt.id AND (
                 EXISTS (SELECT 1 FROM sys_user_group ug JOIN sys_group g ON g.id=ug.group_id
                         WHERE ug.user_id=u.id AND g.deleted=0 AND g.status='0'
                           AND SUBSTR(cg.group_code,1,5) != 'ROLE_' AND cg.group_code IN (g.id,g.group_code))
                 OR EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id
                         WHERE ur.user_id=u.id AND r.deleted=0 AND r.status='0'
                           AND cg.group_code IN (CONCAT('ROLE_',r.id),CONCAT('ROLE_',r.role_code))))))
            """;
    private static final String ENGINE_CANDIDATES = """
            EXISTS (SELECT 1 FROM ACT_RU_IDENTITYLINK c WHERE c.TASK_ID_=ft.ID_ AND c.TYPE_='candidate' AND (
                c.USER_ID_ IN (u.id,u.username)
                OR EXISTS (SELECT 1 FROM sys_user_group ug JOIN sys_group g ON g.id=ug.group_id
                        WHERE ug.user_id=u.id AND g.deleted=0 AND g.status='0' AND SUBSTR(c.GROUP_ID_,1,5) != 'ROLE_'
                          AND c.GROUP_ID_ IN (g.id,g.group_code))
                OR EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id
                        WHERE ur.user_id=u.id AND r.deleted=0 AND r.status='0'
                          AND c.GROUP_ID_ IN (CONCAT('ROLE_',r.id),CONCAT('ROLE_',r.role_code)))))
            """;
    private static final String ADD_SIGN = """
            (pt.node_type='ADD_SIGN' AND ft.ID_ IS NULL AND pt.assignee_type='user'
             AND pt.assignee_id IN (u.id,u.username)
             AND EXISTS (SELECT 1 FROM process_task_add_sign_user child
                 JOIN process_task_add_sign a ON a.id=child.add_sign_id
                 JOIN ACT_RU_TASK source_task ON source_task.ID_=a.source_task_id
                 JOIN process_task source_mirror ON source_mirror.task_id=a.source_task_id
                 WHERE child.generated_task_id=pt.task_id AND child.status='TODO' AND child.user_id IN (u.id,u.username)
                   AND a.status='ACTIVE' AND a.process_instance_id=pt.process_instance_id
                   AND source_task.PROC_INST_ID_=pt.process_instance_id AND source_mirror.process_instance_id=pt.process_instance_id
                   AND source_mirror.deleted=0 AND source_mirror.status IN ('todo','waiting')
                   AND (source_mirror.entity_code=pt.entity_code OR source_mirror.entity_code IS NULL AND pt.entity_code IS NULL)
                   AND (source_mirror.entity_data_id=pt.entity_data_id OR source_mirror.entity_data_id IS NULL AND pt.entity_data_id IS NULL)))
            """;
    private static final String COLUMNS = """
            pt.id,pt.task_id,pt.node_id,pt.node_name,pt.node_type,pt.process_instance_id,pt.process_definition_id,
            pt.process_key,pt.process_name,pt.business_key,pt.entity_code,pt.entity_data_id,pt.form_key,
            CASE WHEN pt.status='todo' AND (pt.node_type IS NULL OR pt.node_type!='ADD_SIGN')
              THEN ft.ASSIGNEE_ ELSE pt.assignee_id END AS assignee_id,
            pt.assignee_name,
            CASE WHEN pt.status='todo' AND (pt.node_type IS NULL OR pt.node_type!='ADD_SIGN')
              THEN CASE WHEN NULLIF(ft.ASSIGNEE_,'') IS NULL THEN 'group' ELSE 'user' END
              ELSE pt.assignee_type END AS assignee_type,
            pt.status,pt.action,pt.comment,pt.start_time,pt.end_time,
            pt.duration,pt.priority,pt.sla_status,pt.response_due_time,pt.due_time,pt.create_time,
            pt.start_user_id,pt.business_name,pt.business_code,pt.business_data_name,pt.business_current_task_name,
            pt.business_status,pt.inbox_summary_ready,pt.inbox_identity_ready
            """;

    /** 分页、总数共用筛选；未完成摘要回填的任务由服务层整体回退到旧列表。 */
    public String selectPage(Map<String, Object> params, ProviderContext context) {
        TaskInboxQuery query = (TaskInboxQuery) params.get("q");
        String order = " ORDER BY pt." + ("done".equals(query.getStatus()) ? "end_time" : "create_time") + " DESC,pt.id DESC";
        // 先在数据库确定本页主键，再读取大文本摘要与展示姓名；历史记录数不会放大展示关联。
        String pageIds = "SELECT pt.id " + from(query, query.getStartUserName() != null) + scope(query)
                + filters(query, context) + order + DatabaseQuerySql.page(context.getDatabaseId(), "q.offset", "q.pageSize");
        return "SELECT " + COLUMNS + "," + starter(context) + " AS start_user_name FROM (" + pageIds
                + ") inbox_page JOIN process_task pt ON pt.id=inbox_page.id " + ENGINE_JOIN + STARTER_JOINS + order;
    }

    public String count(Map<String, Object> params, ProviderContext context) {
        TaskInboxQuery query = (TaskInboxQuery) params.get("q");
        return "SELECT COUNT(*) " + from(query, query.getStartUserName() != null) + scope(query) + filters(query, context);
    }

    /** 就绪检查不能使用尚未回填的业务筛选列，否则会把缺失摘要的匹配任务漏掉。 */
    public String countUnready(Map<String, Object> params) {
        TaskInboxQuery query = (TaskInboxQuery) params.get("q");
        return "SELECT COUNT(*) " + from(query, false) + scope(query)
                + " AND COALESCE(pt.inbox_summary_ready,0)=0";
    }

    /** 统计/选主键只关联筛选必需的表；已办不需要运行任务，未按发起人筛选时不读取用户目录。 */
    private String from(TaskInboxQuery query, boolean filterStarter) {
        return " FROM process_task pt " + ("todo".equals(query.getStatus()) ? ENGINE_JOIN : "")
                + (filterStarter ? STARTER_JOINS : "");
    }

    private String scope(TaskInboxQuery query) {
        if ("done".equals(query.getStatus())) {
            // 与旧已办一致：历史完成者身份匹配不受用户当前启用状态影响。
            // 用户别名是与任务行无关的常量子查询，允许按办理人联合索引做范围扫描。
            return " WHERE pt.status='done' AND pt.deleted=0 AND pt.assignee_id IN (#{q.userId},"
                    + "(SELECT id FROM sys_user WHERE username=#{q.userId} AND deleted=0),"
                    + "(SELECT username FROM sys_user WHERE id=#{q.userId} AND deleted=0))";
        }
        return " WHERE pt.status='todo' AND pt.deleted=0 AND EXISTS (SELECT 1 FROM sys_user u "
                + "WHERE (u.id=#{q.userId} OR u.username=#{q.userId}) AND u.deleted=0 AND u.status='0' AND ("
                + "((pt.node_type IS NULL OR pt.node_type!='ADD_SIGN') AND ft.ID_ IS NOT NULL AND ("
                + "ft.ASSIGNEE_ IN (u.id,u.username) OR (NULLIF(ft.ASSIGNEE_,'') IS NULL AND ("
                + "(pt.inbox_identity_ready=1 AND " + LOCAL_CANDIDATES + ") OR (COALESCE(pt.inbox_identity_ready,0)=0 AND "
                + ENGINE_CANDIDATES + "))))) OR " + ADD_SIGN + "))";
    }

    private String filters(TaskInboxQuery query, ProviderContext context) {
        StringBuilder sql = new StringBuilder();
        if (query.getKeyword() != null) {
            sql.append(" AND (");
            String[] columns = {"process_name", "node_name", "business_current_task_name", "business_name",
                    "business_data_name", "business_code", "business_key"};
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) sql.append(" OR ");
                sql.append(literalLike("pt." + columns[i], "#{q.keyword}", context));
            }
            sql.append(")");
        }
        if (query.getStartUserName() != null) sql.append(" AND ").append(literalLike(starter(context), "#{q.startUserName}", context));
        switch (query.getPriority()) {
            case "URGENT" -> sql.append(" AND COALESCE(pt.priority,0)>=80");
            case "HIGH" -> sql.append(" AND COALESCE(pt.priority,0)>=50 AND COALESCE(pt.priority,0)<80");
            case "NORMAL" -> sql.append(" AND COALESCE(pt.priority,0)<50");
            default -> { }
        }
        if (query.getStartDate() != null) sql.append(" AND pt.start_time>=#{q.startDate}");
        if (query.getEndDate() != null) sql.append(" AND pt.start_time<#{q.endDate}");
        return sql.toString();
    }

    /** 姓名与用户名相等时不重复显示；这里比较原始大小写，不能受 MySQL 不区分大小写的排序规则影响。 */
    private String starter(ProviderContext context) {
        String nickname = "COALESCE(su.nickname,si.nickname)", username = "COALESCE(su.username,si.username)";
        if ("MYSQL".equals(context.getDatabaseId()) || "OCEANBASE_MYSQL".equals(context.getDatabaseId())) {
            nickname = "CAST(" + nickname + " AS BINARY)";
            username = "CAST(" + username + " AS BINARY)";
        }
        return STARTER.formatted(nickname + "=" + username);
    }

    /** MySQL 默认排序规则忽略重音；先统一大小写再按字节匹配，保留原 Java contains 的字面语义。 */
    private String literalLike(String expression, String parameter, ProviderContext context) {
        String normalized = "LOWER(" + expression + ")";
        if ("MYSQL".equals(context.getDatabaseId()) || "OCEANBASE_MYSQL".equals(context.getDatabaseId())) {
            normalized = "CAST(" + normalized + " AS BINARY)";
        }
        return normalized + " LIKE " + parameter + " ESCAPE '!'";
    }
}
