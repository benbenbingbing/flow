package com.workflow.controller;

import com.workflow.dto.ApiResponse;
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

    /**
     * 获取工作台数据
     */
    @GetMapping("/data")
    public ApiResponse<Map<String, Object>> getWorkbenchData(@RequestAttribute("userId") String userId) {
        Map<String, Object> data = new HashMap<>();

        // 流程统计（模拟数据）
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("todoCount", 0);
        statistics.put("doneTodayCount", 0);
        statistics.put("unreadCcCount", 0);
        data.put("statistics", statistics);

        // 待办任务（空列表）
        data.put("todoList", Collections.emptyList());

        // 快捷入口
        List<Map<String, Object>> shortcuts = Collections.singletonList(
            createShortcut("实体管理", "entity", "/entity", "#67C23A")
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
