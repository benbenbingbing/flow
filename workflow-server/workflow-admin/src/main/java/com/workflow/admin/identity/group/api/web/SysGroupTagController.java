package com.workflow.admin.identity.group.api.web;

import com.workflow.admin.identity.group.application.SysGroupTagService;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroupTag;
import com.workflow.core.security.RequiresPermission;
import com.workflow.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 组内标签管理沿用用户组权限；所有成员及标签写入在服务层事务中完成。 */
@RestController
@RequestMapping("/api/system/group")
@RequiresPermission("system:user:view")
@RequiredArgsConstructor
public class SysGroupTagController {
    private final SysGroupTagService service;
    public record TagInput(String tagName) { }

    /** 读取组内标签目录，供管理端编辑和流程设计器选择。 */
    @GetMapping("/{groupId}/tags")
    public Result<List<SysGroupTag>> tags(@PathVariable String groupId) { return Result.success(service.list(groupId)); }

    /** 回显成员绑定；没有标签的成员由调用方显示为空数组。 */
    @GetMapping("/{groupId}/member-tags")
    public Result<List<SysGroupTagService.MemberTags>> memberTags(@PathVariable String groupId) { return Result.success(service.memberTags(groupId)); }

    @PostMapping("/{groupId}/tags")
    @RequiresPermission("system:user:manage")
    public Result<SysGroupTag> create(@PathVariable String groupId, @RequestBody TagInput input) {
        return Result.success(service.save(groupId, null, input.tagName()));
    }

    @PostMapping("/{groupId}/tags/{tagId}/update")
    @RequiresPermission("system:user:manage")
    public Result<SysGroupTag> update(@PathVariable String groupId, @PathVariable String tagId, @RequestBody TagInput input) {
        return Result.success(service.save(groupId, tagId, input.tagName()));
    }

    @PostMapping("/{groupId}/tags/{tagId}/delete")
    @RequiresPermission("system:user:manage")
    public Result<Void> delete(@PathVariable String groupId, @PathVariable String tagId) { service.delete(groupId, tagId); return Result.success(); }

    /** 设计器实时预览当前目录，带出昵称供辨认人员，不生成任务或修改流程。 */
    @PostMapping("/tag-preview")
    public Result<List<SysGroupTagService.MemberPreview>> preview(@RequestBody List<com.workflow.contracts.identity.model.GroupMemberFilter> filters) {
        return Result.success(service.preview(filters));
    }

    /** 同一次保存提交成员与标签，避免网络中断留下只完成一半的人员范围变更。 */
    @PostMapping("/{groupId}/members")
    @RequiresPermission("system:user:manage")
    public Result<Void> members(@PathVariable String groupId, @RequestBody SysGroupTagService.MembersRequest input) {
        service.saveMembers(groupId, input); return Result.success();
    }
}
