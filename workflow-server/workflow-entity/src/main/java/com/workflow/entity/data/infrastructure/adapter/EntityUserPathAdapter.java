package com.workflow.entity.data.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.port.EntityUserPathPort;
import com.workflow.contracts.entity.port.EntityUserReferencePort.EntityUserReferenceException;
import com.workflow.core.database.mybatis.ParameterizedSql;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityUserPathMapper;
import com.workflow.entity.definition.application.EntityUserPathMetadata;
import com.workflow.entity.definition.application.EntityUserPathMetadata.*;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.*;

/** 有限步人员路径求值；以流程记录为根，不依赖当前登录用户或表单字段副本。 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntityUserPathAdapter implements EntityUserPathPort {
    private final EntityUserPathMetadata metadata;
    private final EntityUserPathMapper mapper;
    private final EntityPhysicalTableResolver tables;
    private final DynamicTableService dynamicTables;
    private final DatabaseQueryDialect dialect;
    private final ObjectMapper json;

    @Override
    public PathDescription describe(String entityCode, List<Step> path, List<Version> versions) {
        Plan plan = metadata.compile(entityCode, path, versions);
        if (path.isEmpty() || !"sys_user".equals(plan.terminal().code())) throw EntityUserPathMetadata.invalid("请选择最终指向用户的关系路径");
        return plan.describe();
    }

    @Override
    public List<Option> options(String entityCode, List<Step> prefix) {
        return metadata.options(entityCode, prefix == null ? List.of() : prefix);
    }

    /** 固定最多八跳、每跳二百条；允许同一记录出现在不同步骤，禁止分页或截断人员结果。 */
    @Override
    public List<String> readUserKeys(String entityCode, String recordId, List<Step> path, List<Version> versions) {
        if (!StringUtils.hasText(recordId)) throw failure("ENTITY_RECORD_MISSING", "流程缺少实体记录 ID");
        Plan plan = metadata.compile(entityCode, path, versions);
        if (path.isEmpty() || !"sys_user".equals(plan.terminal().code())) throw EntityUserPathMetadata.invalid("关系路径必须以用户结束");
        List<String> ids = List.of(recordId);
        for (int i = 0; i < plan.hops().size(); i++) {
            Hop hop = plan.hops().get(i);
            if (ids.isEmpty()) break;
            ids = hop.reverse() ? reverse(hop, ids) : forward(hop, ids);
            ids = bounded(ids);
            // 每层验证目标记录存在；用户删除按既有人员语义过滤，其他悬空引用明确失败。
            if ("sys_user".equals(hop.target().code())) {
                ids = userIds(ids, hop.legacyUser());
            } else if (!ids.isEmpty()) {
                requireRows(hop.target(), ids, null);
            }
        }
        if (ids.isEmpty()) return List.of();
        List<Map<String, Object>> users = query("SELECT id, username FROM " + q("sys_user")
                + " WHERE deleted = 0 AND status = ? AND id IN (" + placeholders(ids) + ") ORDER BY id", with("0", ids));
        return users.stream().map(row -> text(row.get("username"))).filter(StringUtils::hasText).distinct().toList();
    }

    private List<String> forward(Hop hop, List<String> ids) {
        EntityField field = hop.field();
        boolean multiTable = field.getFieldType() == EntityField.FieldType.MULTI_REFERENCE && StringUtils.hasText(field.getRefEntityId());
        List<Map<String, Object>> rows = requireRows(hop.source(), ids, multiTable ? null : column(field));
        List<String> result = new ArrayList<>();
        if (multiTable) {
            List<Object> parameters = new ArrayList<>(ids);
            parameters.add(field.getFieldCode()); parameters.add(field.getRefEntityId());
            rows = query("SELECT target_record_id AS link_value FROM " + q(dynamicTables.getMultiValueTableName(hop.source().code()))
                    + " WHERE record_id IN (" + placeholders(ids) + ") AND field_code = ? AND target_entity_id = ? AND deleted = 0"
                    + " ORDER BY sort_order, id", parameters);
            for (var row : rows) result.addAll(values(row.get("link_value")));
        } else {
            for (var row : rows) {
                List<String> values = values(row.get("link_value"));
                if (!hop.multiple() && values.size() > 1) throw failure("ENTITY_USER_PATH_CARDINALITY", "单选关系包含多个记录");
                result.addAll(values);
            }
        }
        if (hop.source().system() && "parent_id".equals(field.getFieldCode())) result.removeIf("0"::equals);
        return bounded(result);
    }

    /** 显式一对多/反向关系按目标外键批量查找；一对一按来源记录逐个验证基数。 */
    private List<String> reverse(Hop hop, List<String> ids) {
        requireRows(hop.source(), ids, null);
        String table = q(table(hop.target()));
        List<Map<String, Object>> rows;
        if (hop.field().getFieldType() == EntityField.FieldType.MULTI_REFERENCE) {
            List<Object> params = new ArrayList<>(); params.add(hop.field().getFieldCode());
            params.add(hop.source().definition().getId()); params.addAll(ids);
            rows = query("SELECT DISTINCT t.id FROM " + table + " t JOIN "
                    + q(dynamicTables.getMultiValueTableName(hop.target().code()))
                    + " m ON m.record_id = t.id WHERE t.deleted = 0 AND m.deleted = 0 AND m.field_code = ?"
                    + " AND m.target_entity_id = ? AND m.target_record_id IN (" + placeholders(ids) + ") ORDER BY t.id", params);
        } else {
            rows = query("SELECT id, " + q(column(hop.field())) + " AS link_value FROM " + table
                    + " WHERE deleted = 0 AND " + q(column(hop.field())) + " IN (" + placeholders(ids) + ") ORDER BY id", ids);
            if (!hop.multiple()) {
                Set<String> seen = new HashSet<>();
                for (var row : rows) if (!seen.add(text(row.get("link_value"))))
                    throw failure("ENTITY_USER_PATH_CARDINALITY", "一对一关系匹配到多条记录");
            }
        }
        return rows.stream().map(row -> text(row.get("id"))).toList();
    }

    private List<Map<String, Object>> requireRows(Entity entity, List<String> ids, String column) {
        List<Map<String, Object>> rows = query("SELECT id" + (column == null ? "" : ", " + q(column) + " AS link_value")
                + " FROM " + q(table(entity)) + " WHERE deleted = 0 AND id IN ("
                + placeholders(ids) + ") ORDER BY id", ids);
        if (rows.size() != ids.size()) throw failure("ENTITY_USER_PATH_RECORD_MISSING", "关系记录不存在或已删除: " + entity.code());
        return rows;
    }

    /** 保留已删除 ID 的身份占位，避免旧用户名兜底把该 ID 误解释成另一位用户。 */
    private List<String> userIds(List<String> values, boolean legacy) {
        if (values.isEmpty()) return values;
        List<Map<String, Object>> rows = query("SELECT id, username, deleted FROM " + q("sys_user")
                + " WHERE id IN (" + placeholders(values) + ") ORDER BY id", values);
        Set<String> matched = new HashSet<>();
        List<String> ids = new ArrayList<>();
        for (var row : rows) {
            String id = text(row.get("id"));
            matched.add(id.toLowerCase(Locale.ROOT));
            if ("0".equals(text(row.get("deleted"))) || Boolean.FALSE.equals(row.get("deleted"))) ids.add(id);
        }
        List<String> unresolved = values.stream().filter(v -> !matched.contains(v.toLowerCase(Locale.ROOT))).toList();
        if (legacy && !unresolved.isEmpty()) {
            for (var row : query("SELECT id FROM " + q("sys_user") + " WHERE deleted = 0 AND username IN ("
                    + placeholders(unresolved) + ") ORDER BY id", unresolved)) ids.add(text(row.get("id")));
        }
        return bounded(ids);
    }

    /** SQL 只能由本类基于服务端已验证元数据构造；值绑定且多取一条用于拒绝超限。 */
    private List<Map<String, Object>> query(String sql, Collection<?> parameters) {
        List<Map<String, Object>> rows = mapper.select(ParameterizedSql.of(sql
                + dialect.paginationClause("0", Integer.toString(MAX_USERS + 1)), parameters.toArray()));
        if (rows.size() > MAX_USERS) throw failure("ENTITY_USER_PATH_LIMIT", "关系路径单层记录超过 200 条");
        return rows;
    }
    private List<String> bounded(Collection<String> values) {
        List<String> result = values.stream().filter(StringUtils::hasText).distinct().toList();
        if (result.size() > MAX_USERS) throw failure("ENTITY_USER_PATH_LIMIT", "关系路径人数或单层记录超过 200 条");
        return result;
    }
    private List<String> values(Object value) {
        if (value == null) return List.of();
        if (value instanceof Collection<?> list) return list.stream().map(this::text).filter(StringUtils::hasText).toList();
        String text = text(value);
        if (text.isEmpty()) return List.of();
        if (text.startsWith("[")) {
            try { return values(json.readValue(text, List.class)); }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw failure("ENTITY_USER_PATH_VALUE_INVALID", "关系值不是合法数组"); }
        }
        return Arrays.stream(text.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
    }
    private String column(EntityField field) {
        return StringUtils.hasText(field.getDbColumnName()) ? field.getDbColumnName()
                : field.getFieldCode().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
    /** 系统实体走已白名单验证的固定表；不能放宽通用动态实体表解析器的访问限制。 */
    private String table(Entity entity) { return entity.system() ? entity.code() : tables.resolve(entity.definition()); }
    private String q(String value) { return dialect.quoteIdentifier(value); }
    private String text(Object value) { return value == null ? "" : value.toString().trim(); }
    private String placeholders(Collection<?> values) { return String.join(",", Collections.nCopies(values.size(), "?")); }
    private List<Object> with(Object first, Collection<?> rest) { List<Object> values = new ArrayList<>(); values.add(first); values.addAll(rest); return values; }
    private EntityUserReferenceException failure(String code, String message) { return new EntityUserReferenceException(code, message); }
}
