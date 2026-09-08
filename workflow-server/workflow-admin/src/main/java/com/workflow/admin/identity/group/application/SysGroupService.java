package com.workflow.admin.identity.group.application;

import com.workflow.core.logging.LogValue;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysUserGroup;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户组管理服务
 * <p>
 * 负责用户组的增删改查、状态切换以及组与用户的关联维护。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysGroupService {

    private static final int GROUP_USER_ID_BATCH_SIZE = 500;
    private static final int GROUP_NAME_MAX_LENGTH = 50;
    private static final int GROUP_CODE_MAX_LENGTH = 50;
    private static final int DESCRIPTION_MAX_LENGTH = 200;
    private static final int SORT_MAX_VALUE = 9999;
    private static final Set<String> VALID_STATUSES = Set.of(
            SysGroup.Status.ENABLED.getValue(),
            SysGroup.Status.DISABLED.getValue());
    
    /** 用户组 Mapper */
    private final SysGroupMapper groupMapper;
    /** 用户组关联 Mapper，维护组与用户的关联关系 */
    private final SysUserGroupMapper userGroupMapper;
    /** 用户 Mapper，用于成员关系写入前验证用户仍然存在 */
    private final SysUserMapper userMapper;
    
    /**
     * 查询组列表
     *
     * @return 按排序升序的用户组列表，每组已填充全部未删除成员 ID
     */
    public List<SysGroup> getGroupList() {
        List<SysGroup> groups = groupMapper.selectList(
            new LambdaQueryWrapper<SysGroup>()
                .orderByAsc(SysGroup::getSort)
        );
        fillGroupUserIds(groups);
        return groups;
    }
    
    /**
     * 查询所有启用的组
     *
     * @return 启用状态的用户组列表，按排序升序
     */
    public List<SysGroup> getEnabledGroups() {
        return groupMapper.selectList(
            new LambdaQueryWrapper<SysGroup>()
                .eq(SysGroup::getStatus, SysGroup.Status.ENABLED.getValue())
                .orderByAsc(SysGroup::getSort)
        );
    }
    
    /**
     * 根据ID查询组
     *
     * @param id 组ID
     * @return 用户组对象（已填充全部未删除成员用户信息），不存在返回 null
     */
    public SysGroup getById(String id) {
        SysGroup group = groupMapper.selectById(id);
        if (group != null) {
            fillGroupMembers(group);
        }
        return group;
    }
    
    /**
     * 新增用户组，并在请求显式携带 userIds 时同步成员关系。
     *
     * <p>新增与更新必须使用独立入口，避免客户端传入 id 后把新增请求变成更新，
     * 同时只复制允许写入的业务字段，阻止 createTime、deleted 等持久化字段越权写入。</p>
     *
     * @param request 用户组输入；id、审计时间和删除标志会被忽略
     * @return 新增后的用户组
     * @throws IllegalArgumentException 输入非法或组编码已被使用时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.UPSERT,
            operation = "新增用户组",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "SYS_GROUP",
            captureArguments = true,
            captureResult = true)
    public SysGroup createGroup(SysGroup request) {
        ValidatedGroupInput input = validateGroupInput(
                request, SysGroup.Status.ENABLED.getValue(), 0);
        if (groupMapper.existsGroupCode(input.groupCode(), "")) {
            throw new IllegalArgumentException("组编码已存在：" + input.groupCode());
        }

        // 成员必须先全部校验成功，避免写入用户组后才发现孤儿用户 ID。
        List<String> memberIds = request.getUserIds() == null
                ? null
                : validateMemberUserIds(request.getUserIds());
        LocalDateTime now = LocalDateTime.now();
        SysGroup group = newGroupRecord(input);
        group.setCreateTime(now);
        group.setUpdateTime(now);
        if (groupMapper.insert(group) != 1) {
            throw new IllegalStateException("新增用户组失败");
        }

        if (memberIds != null) {
            replaceGroupUsers(group.getId(), memberIds);
            group.setUserIds(memberIds);
        }
        log.info("新增用户组：{}", LogValue.safe(group.getGroupName()));
        return group;
    }

    /**
     * 更新用户组可编辑字段，并在请求显式携带 userIds 时同步成员关系。
     *
     * <p>groupCode 是流程配置及外部集成的稳定引用，创建后禁止修改。</p>
     *
     * @param id      待更新用户组 ID
     * @param request 用户组输入
     * @return 更新后的用户组
     * @throws IllegalArgumentException 用户组不存在、输入非法或尝试修改组编码时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.UPDATE,
            operation = "更新用户组",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "SYS_GROUP",
            targetIdArg = 0,
            captureArguments = true,
            captureResult = true)
    public SysGroup updateGroup(String id, SysGroup request) {
        String groupId = requiredText(id, "用户组ID", 64);
        SysGroup existing = requireGroup(groupId);
        ValidatedGroupInput input = validateUpdateGroupInput(request, existing);
        if (!Objects.equals(existing.getGroupCode(), input.groupCode())) {
            throw new IllegalArgumentException("组编码创建后不可修改");
        }

        List<String> memberIds = request.getUserIds() == null
                ? null
                : validateMemberUserIds(request.getUserIds());
        SysGroup group = newGroupRecord(input);
        group.setId(groupId);
        group.setCreateTime(existing.getCreateTime());
        group.setUpdateTime(LocalDateTime.now());
        if (groupMapper.updateById(group) != 1) {
            throw new IllegalArgumentException("用户组不存在");
        }

        if (memberIds != null) {
            replaceGroupUsers(groupId, memberIds);
            group.setUserIds(memberIds);
        }
        log.info("更新用户组：{}", LogValue.safe(group.getGroupName()));
        return group;
    }
    
    /**
     * 删除组（先删除用户关联，再逻辑删除组）
     *
     * @param id 组ID
     * @throws IllegalArgumentException 组不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.DELETE,
            operation = "删除用户组",
            risk = AuditRiskLevel.HIGH,
            targetType = "SYS_GROUP",
            targetIdArg = 0)
    public void deleteGroup(String id) {
        String groupId = requiredText(id, "用户组ID", 64);
        SysGroup group = requireGroup(groupId);
        
        // 删除用户关联
        userGroupMapper.deleteByGroupId(groupId);
        
        // 逻辑删除组
        if (groupMapper.deleteById(groupId) != 1) {
            throw new IllegalArgumentException("用户组不存在");
        }
        log.info("删除用户组：{}", group.getGroupName());
    }
    
    /**
     * 更新组状态
     *
     * @param id     组ID
     * @param status 状态值：0-启用 1-禁用
     * @throws IllegalArgumentException 用户组不存在或状态值非法时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.UPDATE,
            operation = "更新用户组状态",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "SYS_GROUP",
            targetIdArg = 0,
            captureArguments = true)
    public void updateStatus(String id, String status) {
        String groupId = requiredText(id, "用户组ID", 64);
        requireGroup(groupId);
        String normalizedStatus = normalizeStatus(status, null);
        SysGroup group = new SysGroup();
        group.setId(groupId);
        group.setStatus(normalizedStatus);
        group.setUpdateTime(LocalDateTime.now());
        if (groupMapper.updateById(group) != 1) {
            throw new IllegalArgumentException("用户组不存在");
        }
    }
    
    /**
     * 保存组用户关联（先删除原有关联，再批量插入新关联）
     *
     * @param groupId  组ID
     * @param userIds 用户ID列表；空数组表示清空成员，null 属于非法请求
     * @throws IllegalArgumentException 用户组或用户不存在、成员 ID 为空时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.ASSIGN_PERMISSION,
            operation = "维护用户组成员",
            risk = AuditRiskLevel.HIGH,
            targetType = "SYS_GROUP",
            targetIdArg = 0,
            captureArguments = true)
    public void saveGroupUsers(String groupId, List<String> userIds) {
        String normalizedGroupId = requiredText(groupId, "用户组ID", 64);
        requireGroup(normalizedGroupId);
        replaceGroupUsers(normalizedGroupId, validateMemberUserIds(userIds));
    }

    /**
     * 校验并规范化可编辑字段。更新时缺省状态和排序沿用已有值，新增时使用系统默认值。
     */
    private ValidatedGroupInput validateGroupInput(
            SysGroup request,
            String defaultStatus,
            Integer defaultSort) {
        if (request == null) {
            throw new IllegalArgumentException("用户组请求体不能为空");
        }
        String groupName = requiredText(
                request.getGroupName(), "组名称", GROUP_NAME_MAX_LENGTH);
        String groupCode = requiredText(
                request.getGroupCode(), "组编码", GROUP_CODE_MAX_LENGTH);
        String description = request.getDescription() == null
                ? ""
                : request.getDescription().trim();
        if (description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "描述不能超过" + DESCRIPTION_MAX_LENGTH + "个字符");
        }

        Integer sort = request.getSort() == null ? defaultSort : request.getSort();
        if (sort == null) {
            sort = 0;
        }
        if (sort < 0 || sort > SORT_MAX_VALUE) {
            throw new IllegalArgumentException(
                    "排序必须在0到" + SORT_MAX_VALUE + "之间");
        }
        String status = normalizeStatus(request.getStatus(), defaultStatus);
        return new ValidatedGroupInput(
                groupName, groupCode, description, sort, status);
    }

    /**
     * 合并更新请求与现有记录后再统一校验。
     *
     * <p>更新接口兼容旧客户端的部分字段提交：null 表示未提供并沿用原值；
     * 显式空字符串仍按正常输入处理，因此名称/编码为空会被拒绝，描述可被清空。</p>
     */
    private ValidatedGroupInput validateUpdateGroupInput(
            SysGroup request,
            SysGroup existing) {
        if (request == null) {
            throw new IllegalArgumentException("用户组请求体不能为空");
        }
        SysGroup merged = new SysGroup();
        merged.setGroupName(request.getGroupName() == null
                ? existing.getGroupName()
                : request.getGroupName());
        merged.setGroupCode(request.getGroupCode() == null
                ? existing.getGroupCode()
                : request.getGroupCode());
        merged.setDescription(request.getDescription() == null
                ? existing.getDescription()
                : request.getDescription());
        merged.setSort(request.getSort());
        merged.setStatus(request.getStatus());
        return validateGroupInput(
                merged,
                StringUtils.hasText(existing.getStatus())
                        ? existing.getStatus()
                        : SysGroup.Status.ENABLED.getValue(),
                existing.getSort() == null ? 0 : existing.getSort());
    }

    /**
     * 仅构造允许持久化的用户组字段，防止请求实体中的系统字段被透传。
     */
    private SysGroup newGroupRecord(ValidatedGroupInput input) {
        SysGroup group = new SysGroup();
        group.setGroupName(input.groupName());
        group.setGroupCode(input.groupCode());
        group.setDescription(input.description());
        group.setSort(input.sort());
        group.setStatus(input.status());
        return group;
    }

    /**
     * 校验成员 ID、按首次出现顺序去重，并拒绝已删除或不存在的用户。
     */
    private List<String> validateMemberUserIds(List<String> userIds) {
        if (userIds == null) {
            throw new IllegalArgumentException("成员列表不能为空，清空成员请传空数组");
        }
        LinkedHashSet<String> normalizedIds = new LinkedHashSet<>();
        for (String userId : userIds) {
            if (!StringUtils.hasText(userId)) {
                throw new IllegalArgumentException("成员用户ID不能为空");
            }
            normalizedIds.add(userId.trim());
        }
        if (normalizedIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> normalized = new ArrayList<>(normalizedIds);
        Set<String> existingIds = new HashSet<>();
        for (int start = 0; start < normalized.size(); start += GROUP_USER_ID_BATCH_SIZE) {
            int end = Math.min(start + GROUP_USER_ID_BATCH_SIZE, normalized.size());
            List<String> batch = normalized.subList(start, end);
            List<String> existingBatch = userMapper.selectExistingIdsByIds(batch);
            if (existingBatch != null) {
                existingIds.addAll(existingBatch);
            }
        }

        List<String> missingIds = normalized.stream()
                .filter(userId -> !existingIds.contains(userId))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "成员用户不存在或已删除：" + String.join("、", missingIds));
        }
        return normalized;
    }

    /**
     * 在所有校验完成后原子替换成员关系；调用方事务负责失败回滚。
     */
    private void replaceGroupUsers(String groupId, List<String> userIds) {
        userGroupMapper.deleteByGroupId(groupId);
        LocalDateTime now = LocalDateTime.now();
        for (String userId : userIds) {
            SysUserGroup userGroup = new SysUserGroup();
            userGroup.setGroupId(groupId);
            userGroup.setUserId(userId);
            userGroup.setCreateTime(now);
            if (userGroupMapper.insert(userGroup) != 1) {
                throw new IllegalStateException("保存用户组成员失败");
            }
        }
    }

    /**
     * 查询并返回可管理用户组，避免更新零行仍向客户端报告成功。
     */
    private SysGroup requireGroup(String groupId) {
        SysGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("用户组不存在");
        }
        return group;
    }

    /**
     * 规范化用户组状态，仅允许数据库约定的启用/禁用值。
     */
    private String normalizeStatus(String status, String defaultStatus) {
        String normalized = StringUtils.hasText(status)
                ? status.trim()
                : defaultStatus;
        if (normalized == null || !VALID_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("用户组状态只能为0（启用）或1（禁用）");
        }
        return normalized;
    }

    /**
     * 读取必填文本并统一执行去空格和长度校验。
     */
    private String requiredText(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }
    
    /**
     * 填充用户组管理所需的完整成员信息。
     *
     * <p>成员管理必须回显禁用但未删除的用户；运行时解析仍由 Mapper 的
     * selectGroupUsers 查询负责过滤禁用用户。</p>
     *
     * @param group 待填充的用户组对象
     */
    private void fillGroupMembers(SysGroup group) {
        List<SysUser> users = groupMapper.selectGroupMembers(group.getId());
        if (users == null) {
            users = Collections.emptyList();
        }
        group.setUsers(users);
        group.setUserIds(users.stream().map(SysUser::getId).collect(Collectors.toList()));
    }

    /**
     * 批量填充用户组成员 ID。
     * <p>
     * 列表页只需要成员数量和分配成员弹窗的选中 ID，不需要完整用户对象。
     * 这里避免按组逐条查询成员，降低用户组较多时的数据库往返、序列化体积和 JVM 内存压力。
     * </p>
     *
     * @param groups 用户组列表
     */
    private void fillGroupUserIds(List<SysGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }

        Map<String, List<String>> userIdsByGroupId = new HashMap<>();
        List<String> groupIds = groups.stream()
                .map(SysGroup::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        for (int start = 0; start < groupIds.size(); start += GROUP_USER_ID_BATCH_SIZE) {
            int end = Math.min(start + GROUP_USER_ID_BATCH_SIZE, groupIds.size());
            List<String> batch = groupIds.subList(start, end);
            List<SysGroupMapper.GroupUserIdRow> rows =
                    groupMapper.selectGroupUserIdsByGroupIds(batch);
            if (rows == null) {
                continue;
            }
            for (SysGroupMapper.GroupUserIdRow row : rows) {
                if (StringUtils.hasText(row.getGroupId()) && StringUtils.hasText(row.getUserId())) {
                    userIdsByGroupId
                            .computeIfAbsent(row.getGroupId(), ignored -> new ArrayList<>())
                            .add(row.getUserId());
                }
            }
        }

        for (SysGroup group : groups) {
            group.setUsers(null);
            group.setUserIds(userIdsByGroupId.getOrDefault(group.getId(), Collections.emptyList()));
        }
    }

    /** 通过校验且已规范化的用户组可编辑字段。 */
    private record ValidatedGroupInput(
            String groupName,
            String groupCode,
            String description,
            Integer sort,
            String status) {
    }
}
