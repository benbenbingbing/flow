package com.workflow.process.workbench.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 工作台控制器
 */
@AuthenticatedApi
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    /**
     * 获取工作台数据
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @return 符合条件的API{@code response<map<string,}{@code object>>}结果，供调用方继续处理
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

    /**
     * 创建{@code shortcut}；结果供后续流程传递或持久化。
     *
     * @param name 名称，后续用于创建{@code shortcut}时匹配或展示
     * @param icon {@code icon}，作为 {@code shortcut.put} 的输入影响后续处理
     * @param url URL，作为 {@code shortcut.put} 的输入影响后续处理
     * @param color {@code color}，作为 {@code shortcut.put} 的输入影响后续处理
     * @return {@code shortcut}键值结果，供调用方继续处理
     */
    private Map<String, Object> createShortcut(String name, String icon, String url, String color) {
        Map<String, Object> shortcut = new HashMap<>();
        shortcut.put("name", name);
        shortcut.put("icon", icon);
        shortcut.put("url", url);
        shortcut.put("color", color);
        return shortcut;
    }
}
