package com.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.dto.ApiResponse;
import com.workflow.entity.*;
import com.workflow.service.ViewEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 视图引擎控制器
 */
@RestController
@RequestMapping("/api/view-engine")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ViewEngineController {
    
    private final ViewEngineService viewEngineService;
    
    /**
     * 分页查询视图列表
     */
    @GetMapping("/list")
    public ApiResponse<Page<ViewDefinition>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String viewType,
            @RequestParam(required = false) String entityCode,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(viewEngineService.getViewList(keyword, viewType, entityCode, pageNum, pageSize));
    }
    
    /**
     * 根据实体查询视图列表
     */
    @GetMapping("/entity/{entityCode}")
    public ApiResponse<List<ViewDefinition>> getByEntity(@PathVariable String entityCode) {
        return ApiResponse.success(viewEngineService.getViewsByEntity(entityCode));
    }
    
    /**
     * 查询默认视图
     */
    @GetMapping("/entity/{entityCode}/default")
    public ApiResponse<ViewDefinition> getDefaultView(@PathVariable String entityCode) {
        return ApiResponse.success(viewEngineService.getDefaultView(entityCode));
    }
    
    /**
     * 根据ID查询视图详情
     */
    @GetMapping("/{id}")
    public ApiResponse<ViewDefinition> getById(@PathVariable String id) {
        ViewDefinition view = viewEngineService.getViewDetail(id);
        if (view == null) {
            return ApiResponse.error(404, "视图不存在");
        }
        return ApiResponse.success(view);
    }
    
    /**
     * 查询视图完整配置（含字段、查询条件、按钮）
     */
    @GetMapping("/{id}/config")
    public ApiResponse<ViewConfigVO> getViewConfig(@PathVariable String id) {
        ViewDefinition view = viewEngineService.getViewDetail(id);
        if (view == null) {
            return ApiResponse.error(404, "视图不存在");
        }
        
        ViewConfigVO config = new ViewConfigVO();
        config.setView(view);
        config.setFields(viewEngineService.getViewFields(id));
        config.setQueries(viewEngineService.getViewQueries(id));
        config.setButtons(viewEngineService.getViewButtons(id));
        
        return ApiResponse.success(config);
    }
    
    /**
     * 保存视图
     */
    @PostMapping("/save")
    public ApiResponse<ViewDefinition> save(@RequestBody ViewConfigDTO dto) {
        ViewDefinition view = viewEngineService.saveView(
                dto.getView(),
                dto.getFields(),
                dto.getQueries(),
                dto.getButtons()
        );
        return ApiResponse.success(view);
    }
    
    /**
     * 删除视图
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        viewEngineService.deleteView(id);
        return ApiResponse.success();
    }
    
    /**
     * 设置默认视图
     */
    @PostMapping("/{id}/set-default")
    public ApiResponse<Void> setDefault(@PathVariable String id,
                                        @RequestParam String entityCode) {
        viewEngineService.setDefaultView(id, entityCode);
        return ApiResponse.success();
    }
    
    /**
     * 生成默认视图
     */
    @PostMapping("/generate-default/{entityCode}")
    public ApiResponse<ViewDefinition> generateDefault(@PathVariable String entityCode) {
        ViewDefinition view = viewEngineService.generateDefaultView(entityCode);
        return ApiResponse.success(view);
    }
    
    // ==================== DTO和VO ====================
    
    @lombok.Data
    public static class ViewConfigDTO {
        private ViewDefinition view;
        private List<ViewFieldConfig> fields;
        private List<ViewQueryConfig> queries;
        private List<ViewButtonConfig> buttons;
    }
    
    @lombok.Data
    public static class ViewConfigVO {
        private ViewDefinition view;
        private List<ViewFieldConfig> fields;
        private List<ViewQueryConfig> queries;
        private List<ViewButtonConfig> buttons;
    }
}
