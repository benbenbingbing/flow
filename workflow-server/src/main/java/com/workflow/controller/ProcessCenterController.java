package com.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.dto.ApiResponse;
import com.workflow.entity.ProcessCommonOpinion;
import com.workflow.entity.ProcessDraft;
import com.workflow.entity.ProcessTask;
import com.workflow.service.ProcessCenterService;
import com.workflow.vo.ProcessStatisticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程中心控制器
 */
@RestController
@RequestMapping("/api/process-center")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProcessCenterController {
    
    private final ProcessCenterService processCenterService;
    
    // ==================== 待办任务 ====================
    
    /**
     * 获取待办任务列表
     */
    @GetMapping("/todo/list")
    public ApiResponse<Page<ProcessTask>> getTodoList(
            @RequestParam(required = false) String processKey,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer priority,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestAttribute("userId") String userId) {
        return ApiResponse.success(processCenterService.getTodoList(userId, processKey, keyword, priority, pageNum, pageSize));
    }
    
    /**
     * 标记任务为已读
     */
    @PostMapping("/todo/read/{taskId}")
    public ApiResponse<Void> markTaskAsRead(@PathVariable String taskId, 
                                            @RequestAttribute("userId") String userId) {
        processCenterService.markTaskAsRead(taskId, userId);
        return ApiResponse.success();
    }
    
    // ==================== 已办任务 ====================
    
    /**
     * 获取已办任务列表
     */
    @GetMapping("/done/list")
    public ApiResponse<Page<ProcessTask>> getDoneList(
            @RequestParam(required = false) String processKey,
            @RequestParam(required = false) String actionType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestAttribute("userId") String userId) {
        return ApiResponse.success(processCenterService.getDoneList(userId, processKey, actionType, pageNum, pageSize));
    }
    
    // ==================== 抄送/知会 ====================
    
    /**
     * 获取抄送列表
     */
    @GetMapping("/cc/list")
    public ApiResponse<Page<ProcessTask>> getCcList(
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestAttribute("userId") String userId) {
        return ApiResponse.success(processCenterService.getCcList(userId, isRead, pageNum, pageSize));
    }
    
    /**
     * 标记抄送为已读
     */
    @PostMapping("/cc/read/{id}")
    public ApiResponse<Void> markCcAsRead(@PathVariable String id) {
        processCenterService.markCcAsRead(id);
        return ApiResponse.success();
    }
    
    // ==================== 常用意见 ====================
    
    /**
     * 获取常用意见列表
     */
    @GetMapping("/common-opinions")
    public ApiResponse<List<ProcessCommonOpinion>> getCommonOpinions(
            @RequestParam(required = false) String opinionType,
            @RequestAttribute("userId") String userId) {
        return ApiResponse.success(processCenterService.getCommonOpinions(userId, opinionType));
    }
    
    /**
     * 保存常用意见
     */
    @PostMapping("/common-opinions")
    public ApiResponse<Void> saveCommonOpinion(@RequestBody ProcessCommonOpinion opinion,
                                                @RequestAttribute("userId") String userId) {
        opinion.setUserId(userId);
        processCenterService.saveCommonOpinion(opinion);
        return ApiResponse.success();
    }
    
    /**
     * 删除常用意见
     */
    @DeleteMapping("/common-opinions/{id}")
    public ApiResponse<Void> deleteCommonOpinion(@PathVariable String id) {
        processCenterService.deleteCommonOpinion(id);
        return ApiResponse.success();
    }
    
    /**
     * 使用意见（增加使用次数）
     */
    @PostMapping("/common-opinions/use/{id}")
    public ApiResponse<Void> useCommonOpinion(@PathVariable String id) {
        processCenterService.useCommonOpinion(id);
        return ApiResponse.success();
    }
    
    // ==================== 草稿箱 ====================
    
    /**
     * 获取草稿列表
     */
    @GetMapping("/draft/list")
    public ApiResponse<Page<ProcessDraft>> getDraftList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestAttribute("userId") String userId) {
        return ApiResponse.success(processCenterService.getDraftList(userId, status, pageNum, pageSize));
    }
    
    /**
     * 保存草稿
     */
    @PostMapping("/draft/save")
    public ApiResponse<ProcessDraft> saveDraft(@RequestBody ProcessDraft draft,
                                               @RequestAttribute("userId") String userId,
                                               @RequestAttribute("userName") String userName) {
        draft.setUserId(userId);
        draft.setUserName(userName);
        return ApiResponse.success(processCenterService.saveDraft(draft));
    }
    
    /**
     * 删除草稿
     */
    @DeleteMapping("/draft/{id}")
    public ApiResponse<Void> deleteDraft(@PathVariable String id) {
        processCenterService.deleteDraft(id);
        return ApiResponse.success();
    }
    
    /**
     * 提交草稿
     */
    @PostMapping("/draft/submit/{id}")
    public ApiResponse<Void> submitDraft(@PathVariable String id) {
        processCenterService.submitDraft(id);
        return ApiResponse.success();
    }
    
    // ==================== 统计 ====================
    
    /**
     * 获取流程中心统计
     */
    @GetMapping("/statistics")
    public ApiResponse<ProcessStatisticsVO> getStatistics(@RequestAttribute("userId") String userId) {
        return ApiResponse.success(processCenterService.getStatistics(userId));
    }
}
