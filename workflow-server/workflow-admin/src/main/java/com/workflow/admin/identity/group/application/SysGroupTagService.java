package com.workflow.admin.identity.group.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.*;
import com.workflow.admin.identity.group.infrastructure.persistence.record.*;
import com.workflow.port.system.identity.model.GroupMemberFilter;
import com.workflow.port.system.SysUserGroupTagPort;
import com.workflow.port.platform.audit.annotation.SystemAudit;
import com.workflow.port.platform.audit.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 维护组内标签，并作为流程人员解析的身份边界；标签变动不追溯改派已经生成的任务。 */
@Service
@RequiredArgsConstructor
public class SysGroupTagService implements SysUserGroupTagPort {
    private final SysGroupMapper groupMapper;
    private final SysUserGroupMapper memberMapper;
    private final SysGroupTagMapper tagMapper;
    private final SysGroupService groupService;

    public record MemberTags(String userId, List<String> tagIds) { }
    public record MembersRequest(List<String> userIds, List<MemberTags> memberTags) { }
    /** 登录名作为流程身份，昵称只供设计器展示，不参与人员匹配或去重。 */
    public record MemberPreview(String username, String nickname) { }

    /** 返回该组标签目录，管理端和设计器共用；不存在的组拒绝读取。 */
    public List<SysGroupTag> list(String groupId) {
        requireGroup(groupId, false);
        return tagMapper.selectList(new LambdaQueryWrapper<SysGroupTag>()
                .eq(SysGroupTag::getGroupId, groupId).orderByAsc(SysGroupTag::getCreateTime, SysGroupTag::getId));
    }

    /** 新增或改名；用户组锁保证组内重名检查与成员保存不会交错。 */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(module = AuditModule.SYSTEM, action = AuditAction.UPSERT, operation = "维护用户组标签",
            risk = AuditRiskLevel.MEDIUM, targetType = "SYS_GROUP", targetIdArg = 0, captureArguments = true)
    public SysGroupTag save(String groupId, String tagId, String name) {
        lockGroup(groupId);
        if (name == null || name.isBlank() || name.trim().length() > 50) {
            throw new IllegalArgumentException("标签名称不能为空且不能超过50字符");
        }
        String normalized = name.trim();
        if (tagMapper.selectCount(new LambdaQueryWrapper<SysGroupTag>().eq(SysGroupTag::getGroupId, groupId)
                .eq(SysGroupTag::getTagName, normalized).ne(tagId != null, SysGroupTag::getId, tagId)) > 0) {
            throw new IllegalArgumentException("组内标签名称已存在");
        }
        SysGroupTag tag;
        if (tagId == null) {
            if (list(groupId).size() >= 100) throw new IllegalArgumentException("每组最多100个标签");
            tag = new SysGroupTag(); tag.setGroupId(groupId); tag.setCreateTime(LocalDateTime.now());
            tag.setTagName(normalized); tagMapper.insert(tag);
        } else {
            tag = requireTag(groupId, tagId); tag.setTagName(normalized); tagMapper.updateById(tag);
        }
        return tag;
    }

    /** 删除目录及绑定；流程中的稳定 ID 不被清空，后续运行会报告失效条件。 */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(module = AuditModule.SYSTEM, action = AuditAction.DELETE, operation = "删除用户组标签",
            risk = AuditRiskLevel.HIGH, targetType = "SYS_GROUP", targetIdArg = 0, captureArguments = true)
    public void delete(String groupId, String tagId) {
        lockGroup(groupId); requireTag(groupId, tagId);
        tagMapper.clearTag(tagId); tagMapper.deleteById(tagId);
    }

    /** 回显已保存的成员标签，包含停用用户；运行时才过滤不可分配用户。 */
    public List<MemberTags> memberTags(String groupId) {
        requireGroup(groupId, false);
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (var row : tagMapper.memberTags(groupId)) values.computeIfAbsent(row.getUserId(), key -> new ArrayList<>()).add(row.getTagId());
        return values.entrySet().stream().map(e -> new MemberTags(e.getKey(), e.getValue())).toList();
    }

    /** 原子保存成员及显式提交的标签；旧成员接口省略标签时保留既有绑定。任何非法标签使整个请求回滚。 */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(module = AuditModule.SYSTEM, action = AuditAction.ASSIGN_PERMISSION, operation = "维护用户组成员及标签",
            risk = AuditRiskLevel.HIGH, targetType = "SYS_GROUP", targetIdArg = 0, captureArguments = true)
    public void saveMembers(String groupId, MembersRequest request) {
        lockGroup(groupId);
        if (request == null || request.userIds() == null || request.memberTags() == null) {
            throw new IllegalArgumentException("必须显式提供成员和成员标签数组");
        }
        groupService.saveGroupUsers(groupId, request.userIds());
        Map<String, SysUserGroup> members = new HashMap<>();
        for (var member : memberMapper.selectList(new LambdaQueryWrapper<SysUserGroup>().eq(SysUserGroup::getGroupId, groupId))) members.put(member.getUserId(), member);
        Set<String> seen = new HashSet<>();
        Set<String> validTags = new HashSet<>();
        for (var tag : list(groupId)) validTags.add(tag.getId());
        Map<String, Set<String>> previous = new HashMap<>();
        for (var binding : tagMapper.memberTags(groupId)) previous.computeIfAbsent(binding.getUserId(), key -> new HashSet<>()).add(binding.getTagId());
        for (MemberTags row : request.memberTags()) {
            if (row == null || !members.containsKey(row.userId()) || !seen.add(row.userId())) throw new IllegalArgumentException("标签成员不在当前组或重复提交");
            GroupMemberFilter filter = new GroupMemberFilter("validation", row.tagIds());
            if (!validTags.containsAll(filter.tagIds())) throw new IllegalArgumentException("标签不存在或不属于当前用户组");
            // 普通保存和批量编辑只写发生变化的绑定，避免对每个未修改成员反复删插。
            if (previous.getOrDefault(row.userId(), Set.of()).equals(new HashSet<>(filter.tagIds()))) continue;
            String memberId = members.get(row.userId()).getId();
            tagMapper.clearMember(memberId);
            for (String tagId : filter.tagIds()) tagMapper.bind(memberId, tagId);
        }
    }

    @Override
    public void validateTags(List<GroupMemberFilter> filters) {
        if (filters == null || filters.isEmpty() || filters.size() > 100) throw new IllegalArgumentException("必须选择1到100个用户组");
        for (var filter : filters) {
            SysGroup group = groupMapper.selectByGroupCode(filter.groupCode());
            if (group == null || !"0".equals(group.getStatus())) throw new IllegalArgumentException("用户组不存在或已停用：" + filter.groupCode());
            for (String tagId : filter.tagIds()) requireTag(group.getId(), tagId);
        }
    }

    /** 运行时沿用预览的范围规则，只返回流程引擎使用的登录名。 */
    @Override
    @Transactional(readOnly = true)
    public List<String> resolveMembersByTags(List<GroupMemberFilter> filters) {
        return preview(filters).stream().map(MemberPreview::username).toList();
    }

    /** 返回启用成员的登录名与昵称；沿组顺序去重，非法组或标签直接拒绝，避免扩大范围。 */
    @Transactional(readOnly = true)
    public List<MemberPreview> preview(List<GroupMemberFilter> filters) {
        validateTags(filters);
        Map<String, MemberPreview> users = new LinkedHashMap<>();
        for (var filter : filters) {
            SysGroup group = groupMapper.selectByGroupCode(filter.groupCode());
            for (var member : tagMapper.resolveMembers(group.getId(), filter.tagIds())) {
                users.putIfAbsent(member.getUsername(), new MemberPreview(member.getUsername(), member.getNickname()));
            }
        }
        return List.copyOf(users.values());
    }

    private SysGroup requireGroup(String id, boolean enabled) {
        SysGroup group = groupMapper.selectById(id);
        if (group == null || (enabled && !"0".equals(group.getStatus()))) throw new IllegalArgumentException("用户组不存在或已停用");
        return group;
    }

    private void lockGroup(String groupId) {
        if (groupMapper.lockMembers(groupId) == null) throw new IllegalArgumentException("用户组不存在");
        requireGroup(groupId, false);
    }

    private SysGroupTag requireTag(String groupId, String tagId) {
        SysGroupTag tag = tagMapper.selectById(tagId);
        if (tag == null || !Objects.equals(groupId, tag.getGroupId())) throw new IllegalArgumentException("标签不存在或不属于当前用户组：" + tagId);
        return tag;
    }
}
