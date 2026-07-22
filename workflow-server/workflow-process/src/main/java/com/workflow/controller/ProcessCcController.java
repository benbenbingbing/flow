package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.common.UserContext;
import com.workflow.common.ForbiddenException;
import com.workflow.entity.ProcessCcRecord;
import com.workflow.service.ProcessCcService;
import com.workflow.service.CurrentUserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流程抄送控制器
 */
@RestController
@RequestMapping("/api/process-cc")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProcessCcController {
    
    private final ProcessCcService ccService;
    private final CurrentUserRoleService currentUserRoleService;
    
    /**
     * 获取我的抄送列表
     */
    @GetMapping("/my-cc")
    public Result<List<ProcessCcRecord>> getMyCcList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        String userId = UserContext.getUsername();
        userId = requireCurrentUser(userId);
        return Result.success(ccService.getUserCcList(userId, pageNum, pageSize));
    }
    
    /**
     * 获取流程的抄送记录
     */
    @GetMapping("/process/{processInstanceId}")
    public Result<List<ProcessCcRecord>> getProcessCcRecords(@PathVariable String processInstanceId) {
        requireCurrentUser(UserContext.getUsername());
        currentUserRoleService.requireAdministrator("仅管理员可以查看流程全部知会记录");
        return Result.success(ccService.getProcessCcRecords(processInstanceId));
    }
    
    /**
     * 标记抄送为已读
     */
    @PostMapping("/read/{ccId}")
    public Result<Void> markAsRead(@PathVariable String ccId) {
        ccService.markAsRead(ccId, requireCurrentUser(UserContext.getUsername()));
        return Result.success();
    }
    
    /**
     * 批量标记为已读
     */
    @PostMapping("/read-all")
    public Result<Void> markAllAsRead() {
        String userId = UserContext.getUsername();
        userId = requireCurrentUser(userId);
        ccService.markAllAsRead(userId);
        return Result.success();
    }
    
    /**
     * 获取抄送统计
     */
    @GetMapping("/statistics")
    public Result<Map<String, Long>> getCcStatistics() {
        String userId = UserContext.getUsername();
        userId = requireCurrentUser(userId);
        
        long total = ccService.countUserCc(userId);
        long unread = ccService.countUnreadCc(userId);
        
        return Result.success(Map.of(
            "total", total,
            "unread", unread
        ));
    }

    private String requireCurrentUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ForbiddenException("用户未登录");
        }
        return userId;
    }
}
