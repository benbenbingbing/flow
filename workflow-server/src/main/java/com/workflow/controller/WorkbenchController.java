package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.service.ProcessCenterService;
import com.workflow.vo.ProcessStatisticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 工作台控制器
 */
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WorkbenchController {
    
    private final ProcessCenterService processCenterService;
    
    /**
     * 获取工作台数据
     */
    @GetMapping("/data")
    public ApiResponse<Map<String, Object>> getWorkbenchData(@RequestAttribute("userId") String userId) {
        Map<String, Object> data = new HashMap<>();
        
        // 流程统计
        ProcessStatisticsVO statistics = processCenterService.getStatistics(userId);
        data.put("statistics", statistics);
        
        // 待办任务（前5条）
        data.put("todoList", processCenterService.getTodoList(userId, null, null, null, 1, 5).getRecords());
        
        // 快捷入口（模拟数据）
        List<Map<String, Object>> shortcuts = Arrays.asList(
            createShortcut("流程中心", "process-center", "/process-center", "#409EFF"),
            createShortcut("实体管理", "entity", "/entity", "#67C23A"),
            createShortcut("视图引擎", "view", "/view-engine", "#E6A23C"),
            createShortcut("报表引擎", "report", "/report-engine", "#F56C6C")
        );
        data.put("shortcuts", shortcuts);
        
        // 系统公告（模拟数据）
        List<Map<String, Object>> notices = Arrays.asList(
            createNotice("系统升级通知", "系统将于今晚进行升级维护", "2026-03-20"),
            createNotice("新功能上线", "流程中心功能已全面上线", "2026-03-19"),
            createNotice("使用指南", "查看低代码平台使用手册", "2026-03-18")
        );
        data.put("notices", notices);
        
        return ApiResponse.success(data);
    }
    
    private Map<String, Object> createShortcut(String name, String icon, String url, String color) {
        Map<String, Object> shortcut = new HashMap<>();
        shortcut.put("name", name);
        shortcut.put("icon", icon);
        shortcut.put("url", url);
        shortcut.put("color", color);
        return shortcut;
    }
    
    private Map<String, Object> createNotice(String title, String content, String date) {
        Map<String, Object> notice = new HashMap<>();
        notice.put("title", title);
        notice.put("content", content);
        notice.put("date", date);
        return notice;
    }
}
