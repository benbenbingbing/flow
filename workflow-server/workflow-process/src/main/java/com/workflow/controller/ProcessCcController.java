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
 * 流程抄送/知会控制器
 *
 * 对外提供抄送记录的查询、已读标记与统计接口：
 * <ul>
 *   <li>GET  /my-cc                我的抄送列表（分页）</li>
 *   <li>GET  /process/{id}        指定流程的抄送记录（仅管理员）</li>
 *   <li>POST /read/{ccId}         标记单条抄送为已读</li>
 *   <li>POST /read-all            标记当前用户全部抄送为已读</li>
 *   <li>GET  /statistics          抄送数量统计（总数 + 未读数）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/process-cc")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProcessCcController {
    
    private final ProcessCcService ccService;
    private final CurrentUserRoleService currentUserRoleService;
    
    /**
     * 获取我的抄送列表（分页）。
     *
     * <p>返回当前登录用户收到的抄送/知会记录，用于"抄送我的"列表展示。
     *
     * @param pageNum  页码，默认 1
     * @param pageSize 每页条数，默认 10
     * @return 抄送记录列表
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
     * 获取指定流程的抄送记录。
     *
     * <p>仅管理员可调用，用于查看某流程实例向哪些用户发送过知会。
     *
     * @param processInstanceId 流程实例ID
     * @return 该流程的抄送记录列表
     */
    @GetMapping("/process/{processInstanceId}")
    public Result<List<ProcessCcRecord>> getProcessCcRecords(@PathVariable String processInstanceId) {
        requireCurrentUser(UserContext.getUsername());
        currentUserRoleService.requireAdministrator("仅管理员可以查看流程全部知会记录");
        return Result.success(ccService.getProcessCcRecords(processInstanceId));
    }
    
    /**
     * 标记单条抄送记录为已读。
     *
     * <p>标记后该记录不再计入"抄送我的"页签的未读数量。
     *
     * @param ccId 抄送记录ID
     */
    @PostMapping("/read/{ccId}")
    public Result<Void> markAsRead(@PathVariable String ccId) {
        ccService.markAsRead(ccId, requireCurrentUser(UserContext.getUsername()));
        return Result.success();
    }
    
    /**
     * 将当前用户的所有抄送记录标记为已读。
     *
     * <p>用于"全部已读"操作，执行后该用户的未读抄送数归零。
     */
    @PostMapping("/read-all")
    public Result<Void> markAllAsRead() {
        String userId = UserContext.getUsername();
        userId = requireCurrentUser(userId);
        ccService.markAllAsRead(userId);
        return Result.success();
    }
    
    /**
     * 获取抄送数量统计。
     *
     * <p>返回当前用户的抄送总数与未读数，用于工作台/首页未读抄送徽标展示。
     *
     * @return 包含 total（总数）与 unread（未读数）的统计信息
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

    /**
     * 校验并返回当前登录用户ID。
     *
     * @param userId 从上下文获取的用户ID
     * @return 非空的用户ID
     * @throws ForbiddenException 用户未登录时抛出
     */
    private String requireCurrentUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ForbiddenException("用户未登录");
        }
        return userId;
    }
}
