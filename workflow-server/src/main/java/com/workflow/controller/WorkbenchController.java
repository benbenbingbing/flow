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

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("todoCount", 0);
        statistics.put("doneTodayCount", 0);
        statistics.put("unreadCcCount", 0);
        data.put("statistics", statistics);

        data.put("todoList", Collections.emptyList());

        List<Map<String, Object>> shortcuts = Collections.singletonList(
            createShortcut("实体管理", "entity", "/entity", "#67C23A")
        );
        data.put("shortcuts", shortcuts);

        data.put("notices", Collections.emptyList());

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
}
