package com.workflow.entity.permission.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.core.result.PageResult;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.permission.api.dto.EntityListScopeInventoryBatchResult;
import com.workflow.entity.permission.api.dto.EntityListScopeInventoryDTO;
import com.workflow.entity.permission.api.request.EntityListScopeInventoryBatchConfirmRequest;
import com.workflow.entity.permission.api.request.EntityListScopeInventoryConfirmItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 管理存量列表的数据范围安全策略盘点。
 * 批量确认先完成全部参数、权限和数据库对象校验，再进入写入阶段；任一失败由外层事务整体回滚。
 */
@Service
@RequiredArgsConstructor
public class EntityListScopeInventoryService {

    private static final int MAX_BATCH_SIZE = 200;
    private static final Set<String> ALLOWED_POLICIES =
            Set.of("DENY_ALL", "PERSONAL", "EXPLICIT_ALL");
    private static final Set<String> ALLOWED_STATUSES =
            Set.of("UNASSIGNED", "PENDING", "CONFIRMED", "EXCEPTION");

    private final JdbcTemplate jdbcTemplate;
    private final EntityListConfigMapper listConfigMapper;
    private final EntityListScopeService scopeService;

    /**
     * 补登记尚未确认且仍处于 OBSERVE 阶段的存量列表；重复执行不会覆盖责任人和处理结果。
     *
     * @return 新增的盘点记录数
     */
    @Transactional
    public int refreshInventory() {
        return jdbcTemplate.update("""
                INSERT INTO entity_list_scope_inventory (
                  id, list_id, entity_id, entity_code, list_key, list_name,
                  detected_policy, detected_enforcement, processing_status,
                  create_time, update_time
                )
                SELECT config.id, config.id, config.entity_id, config.entity_code,
                       config.list_key, config.list_name,
                       COALESCE(config.unbound_scope_policy, 'EXPLICIT_ALL'),
                       COALESCE(config.scope_enforcement_mode, 'OBSERVE'),
                       'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM entity_list_config config
                WHERE config.deleted = 0
                  AND COALESCE(config.scope_default_confirmed, 0) = 0
                  AND COALESCE(config.scope_enforcement_mode, 'OBSERVE') = 'OBSERVE'
                ON DUPLICATE KEY UPDATE
                  entity_code = VALUES(entity_code),
                  list_key = VALUES(list_key),
                  list_name = VALUES(list_name),
                  detected_policy = IF(processing_status = 'CONFIRMED', detected_policy, VALUES(detected_policy)),
                  detected_enforcement = IF(processing_status = 'CONFIRMED', detected_enforcement, VALUES(detected_enforcement)),
                  update_time = CURRENT_TIMESTAMP
                """);
    }

    /** 查询盘点清单，清单始终包含责任人、处理状态和确认信息。 */
    public PageResult<EntityListScopeInventoryDTO> findPage(
            String processingStatus,
            String entityKeyword,
            int requestedPageNum,
            int requestedPageSize) {
        String status = trimToNull(processingStatus);
        if (status != null && !ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException("不支持的处理状态：" + status);
        }
        int pageNum = Math.max(1, requestedPageNum);
        int pageSize = Math.max(1, Math.min(100, requestedPageSize));
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            where.append(" AND processing_status = ?");
            args.add(status);
        }
        String keyword = trimToNull(entityKeyword);
        if (keyword != null) {
            where.append(" AND (entity_code LIKE ? OR list_key LIKE ? OR list_name LIKE ?)");
            String pattern = "%" + keyword + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM entity_list_scope_inventory" + where,
                Long.class,
                args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<EntityListScopeInventoryDTO> records = jdbcTemplate.query(
                """
                SELECT id, list_id, entity_id, entity_code, list_key, list_name,
                       detected_policy, detected_enforcement, owner_id, owner_name,
                       processing_status, selected_policy, confirmation_reason,
                       confirmed_by, confirmed_at, exception_note, create_time, update_time
                FROM entity_list_scope_inventory
                """ + where + " ORDER BY update_time DESC, entity_code, list_key LIMIT ? OFFSET ?",
                EntityListScopeInventoryService::mapInventory,
                pageArgs.toArray());
        return new PageResult<>(records, total == null ? 0 : total, pageNum, pageSize);
    }

    /**
     * 原子批量确认。所有项目先完成校验并加锁，随后才更新列表策略和盘点状态。
     * 相同内容的重复提交直接跳过，避免产生重复发布快照。
     */
    @Transactional
    public EntityListScopeInventoryBatchResult batchConfirm(
            EntityListScopeInventoryBatchConfirmRequest request) {
        List<EntityListScopeInventoryConfirmItem> items = validateRequest(request);
        if (items.stream().anyMatch(item -> "EXPLICIT_ALL".equals(item.getSelectedPolicy()))) {
            scopeService.requireExplicitAllPermission();
        }

        // 先锁定并核对整批数据库对象，确保写入阶段不会因常规校验产生部分结果。
        List<PreparedConfirmation> prepared = new ArrayList<>(items.size());
        for (EntityListScopeInventoryConfirmItem item : items) {
            LockedInventory inventory = lockInventory(item.getListId());
            EntityListConfig listConfig = lockListConfig(item.getListId());
            boolean idempotent = isSameConfirmation(inventory, item);
            if ("CONFIRMED".equals(inventory.processingStatus()) && !idempotent) {
                throw new IllegalStateException(
                        "列表 " + item.getListId() + " 已确认，不能用不同内容覆盖；请先走异常处理流程");
            }
            prepared.add(new PreparedConfirmation(item, listConfig, idempotent));
        }

        int processed = 0;
        int skipped = 0;
        String actor = Objects.requireNonNullElse(UserContext.getUserId(), "system");
        for (PreparedConfirmation confirmation : prepared) {
            if (confirmation.idempotent()) {
                skipped++;
                continue;
            }
            EntityListScopeInventoryConfirmItem item = confirmation.item();
            scopeService.confirmInventoryPolicy(
                    confirmation.listConfig(), item.getSelectedPolicy(), trimToNull(item.getReason()));
            jdbcTemplate.update("""
                            UPDATE entity_list_scope_inventory
                            SET owner_id = ?, owner_name = ?, processing_status = 'CONFIRMED',
                                selected_policy = ?, confirmation_reason = ?,
                                confirmed_by = ?, confirmed_at = CURRENT_TIMESTAMP,
                                exception_note = NULL, update_time = CURRENT_TIMESTAMP
                            WHERE list_id = ?
                            """,
                    item.getOwnerId().trim(), item.getOwnerName().trim(),
                    item.getSelectedPolicy(), trimToNull(item.getReason()), actor, item.getListId());
            processed++;
        }
        return new EntityListScopeInventoryBatchResult(items.size(), processed, skipped);
    }

    private List<EntityListScopeInventoryConfirmItem> validateRequest(
            EntityListScopeInventoryBatchConfirmRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("至少选择一个盘点对象");
        }
        if (request.getItems().size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("单次最多确认 " + MAX_BATCH_SIZE + " 个盘点对象");
        }
        Set<String> listIds = new HashSet<>();
        for (EntityListScopeInventoryConfirmItem item : request.getItems()) {
            if (item == null || trimToNull(item.getListId()) == null) {
                throw new IllegalArgumentException("列表 ID 不能为空");
            }
            item.setListId(item.getListId().trim());
            if (!listIds.add(item.getListId())) {
                throw new IllegalArgumentException("同一批次不能重复选择列表：" + item.getListId());
            }
            if (trimToNull(item.getOwnerId()) == null || trimToNull(item.getOwnerName()) == null) {
                throw new IllegalArgumentException("列表 " + item.getListId() + " 必须指定责任人");
            }
            String policy = trimToNull(item.getSelectedPolicy());
            if (policy == null || !ALLOWED_POLICIES.contains(policy)) {
                throw new IllegalArgumentException("列表 " + item.getListId() + " 必须选择有效处理模式");
            }
            item.setSelectedPolicy(policy);
            if ("EXPLICIT_ALL".equals(policy)
                    && (trimToNull(item.getReason()) == null || item.getReason().trim().length() < 5)) {
                throw new IllegalArgumentException(
                        "列表 " + item.getListId() + " 选择全量可见时，确认原因至少 5 个字符");
            }
        }
        return request.getItems();
    }

    private LockedInventory lockInventory(String listId) {
        List<LockedInventory> rows = jdbcTemplate.query("""
                        SELECT list_id, processing_status, owner_id, owner_name,
                               selected_policy, confirmation_reason
                        FROM entity_list_scope_inventory
                        WHERE list_id = ?
                        FOR UPDATE
                        """,
                (rs, rowNum) -> new LockedInventory(
                        rs.getString("list_id"), rs.getString("processing_status"),
                        rs.getString("owner_id"), rs.getString("owner_name"),
                        rs.getString("selected_policy"), rs.getString("confirmation_reason")),
                listId);
        if (rows.size() != 1) {
            throw new IllegalArgumentException("盘点记录不存在：" + listId);
        }
        return rows.get(0);
    }

    private EntityListConfig lockListConfig(String listId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT id FROM entity_list_config WHERE id = ? AND deleted = 0 FOR UPDATE",
                (rs, rowNum) -> rs.getString(1), listId);
        if (rows.size() != 1) {
            throw new IllegalArgumentException("列表配置不存在或已删除：" + listId);
        }
        EntityListConfig config = listConfigMapper.selectById(listId);
        if (config == null) {
            throw new IllegalArgumentException("列表配置不存在：" + listId);
        }
        return config;
    }

    private boolean isSameConfirmation(
            LockedInventory inventory,
            EntityListScopeInventoryConfirmItem item) {
        if (!"CONFIRMED".equals(inventory.processingStatus())) {
            return false;
        }
        return Objects.equals(inventory.ownerId(), item.getOwnerId().trim())
                && Objects.equals(inventory.ownerName(), item.getOwnerName().trim())
                && Objects.equals(inventory.selectedPolicy(), item.getSelectedPolicy())
                && (!"EXPLICIT_ALL".equals(item.getSelectedPolicy())
                || Objects.equals(trimToNull(inventory.confirmationReason()), trimToNull(item.getReason())));
    }

    private static EntityListScopeInventoryDTO mapInventory(ResultSet rs, int rowNum)
            throws SQLException {
        return new EntityListScopeInventoryDTO(
                rs.getString("id"), rs.getString("list_id"), rs.getString("entity_id"),
                rs.getString("entity_code"), rs.getString("list_key"), rs.getString("list_name"),
                rs.getString("detected_policy"), rs.getString("detected_enforcement"),
                rs.getString("owner_id"), rs.getString("owner_name"),
                rs.getString("processing_status"), rs.getString("selected_policy"),
                rs.getString("confirmation_reason"), rs.getString("confirmed_by"),
                timestamp(rs, "confirmed_at"), rs.getString("exception_note"),
                timestamp(rs, "create_time"), timestamp(rs, "update_time"));
    }

    private static LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private record LockedInventory(
            String listId, String processingStatus, String ownerId, String ownerName,
            String selectedPolicy, String confirmationReason) {
    }

    private record PreparedConfirmation(
            EntityListScopeInventoryConfirmItem item,
            EntityListConfig listConfig,
            boolean idempotent) {
    }
}
