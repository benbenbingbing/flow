package com.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.*;
import com.workflow.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 视图引擎服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewEngineService extends ServiceImpl<ViewDefinitionMapper, ViewDefinition> {
    
    private final ViewDefinitionMapper viewDefinitionMapper;
    private final ViewFieldConfigMapper fieldConfigMapper;
    private final ViewQueryConfigMapper queryConfigMapper;
    private final ViewButtonConfigMapper buttonConfigMapper;
    private final EntityDefinitionMapper entityDefinitionMapper;
    private final ObjectMapper objectMapper;
    
    /**
     * 根据ID查询视图详情（含配置）
     */
    public ViewDefinition getViewDetail(String id) {
        ViewDefinition view = viewDefinitionMapper.selectById(id);
        if (view != null) {
            // 加载关联配置
            view.setEntityName(getEntityName(view.getEntityCode()));
        }
        return view;
    }
    
    /**
     * 根据实体编码查询视图列表
     */
    public List<ViewDefinition> getViewsByEntity(String entityCode) {
        return viewDefinitionMapper.findByEntityCode(entityCode);
    }
    
    /**
     * 查询默认视图
     */
    public ViewDefinition getDefaultView(String entityCode) {
        return viewDefinitionMapper.findDefaultByEntityCode(entityCode);
    }
    
    /**
     * 分页查询视图列表
     */
    public Page<ViewDefinition> getViewList(String keyword, String viewType, String entityCode, 
                                             int pageNum, int pageSize) {
        Page<ViewDefinition> page = new Page<>(pageNum, pageSize);
        return viewDefinitionMapper.selectViewList(page, keyword, viewType, entityCode);
    }
    
    /**
     * 保存视图（包含字段、查询条件、按钮配置）
     */
    @Transactional(rollbackFor = Exception.class)
    public ViewDefinition saveView(ViewDefinition view, 
                                   List<ViewFieldConfig> fields,
                                   List<ViewQueryConfig> queries,
                                   List<ViewButtonConfig> buttons) {
        // 处理编码
        if (!StringUtils.hasText(view.getViewCode())) {
            view.setViewCode(generateViewCode(view.getViewName()));
        }
        
        // 保存视图定义
        if (view.getId() == null) {
            viewDefinitionMapper.insert(view);
        } else {
            viewDefinitionMapper.updateById(view);
            // 删除旧配置
            fieldConfigMapper.deleteByViewId(view.getId());
            queryConfigMapper.deleteByViewId(view.getId());
            buttonConfigMapper.deleteByViewId(view.getId());
        }
        
        // 保存字段配置
        if (fields != null && !fields.isEmpty()) {
            for (int i = 0; i < fields.size(); i++) {
                ViewFieldConfig field = fields.get(i);
                field.setViewId(view.getId());
                field.setSortOrder(i);
                fieldConfigMapper.insert(field);
            }
        }
        
        // 保存查询条件配置
        if (queries != null && !queries.isEmpty()) {
            for (int i = 0; i < queries.size(); i++) {
                ViewQueryConfig query = queries.get(i);
                query.setViewId(view.getId());
                query.setSortOrder(i);
                queryConfigMapper.insert(query);
            }
        }
        
        // 保存按钮配置
        if (buttons != null && !buttons.isEmpty()) {
            for (int i = 0; i < buttons.size(); i++) {
                ViewButtonConfig button = buttons.get(i);
                button.setViewId(view.getId());
                button.setSortOrder(i);
                buttonConfigMapper.insert(button);
            }
        }
        
        return view;
    }
    
    /**
     * 删除视图
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteView(String id) {
        fieldConfigMapper.deleteByViewId(id);
        queryConfigMapper.deleteByViewId(id);
        buttonConfigMapper.deleteByViewId(id);
        viewDefinitionMapper.deleteById(id);
    }
    
    /**
     * 获取视图字段配置
     */
    public List<ViewFieldConfig> getViewFields(String viewId) {
        return fieldConfigMapper.findByViewId(viewId);
    }
    
    /**
     * 获取视图查询条件配置
     */
    public List<ViewQueryConfig> getViewQueries(String viewId) {
        return queryConfigMapper.findByViewId(viewId);
    }
    
    /**
     * 获取视图按钮配置
     */
    public List<ViewButtonConfig> getViewButtons(String viewId) {
        return buttonConfigMapper.findByViewId(viewId);
    }
    
    /**
     * 设置默认视图
     */
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultView(String viewId, String entityCode) {
        // 取消该实体下的其他默认视图
        List<ViewDefinition> views = viewDefinitionMapper.findByEntityCode(entityCode);
        for (ViewDefinition v : views) {
            if (v.getIsDefault() != null && v.getIsDefault() == 1) {
                v.setIsDefault(0);
                viewDefinitionMapper.updateById(v);
            }
        }
        
        // 设置新的默认视图
        ViewDefinition view = new ViewDefinition();
        view.setId(viewId);
        view.setIsDefault(1);
        viewDefinitionMapper.updateById(view);
    }
    
    /**
     * 根据实体字段生成默认视图配置
     */
    public ViewDefinition generateDefaultView(String entityCode) {
        EntityDefinition entity = entityDefinitionMapper.findByEntityCode(entityCode).orElse(null);
        if (entity == null) {
            throw new RuntimeException("实体不存在: " + entityCode);
        }
        
        ViewDefinition view = new ViewDefinition();
        view.setViewCode(entityCode + "_default_list");
        view.setViewName(entity.getEntityName() + "-默认列表");
        view.setViewType("LIST");
        view.setEntityCode(entityCode);
        view.setDataSourceType("ENTITY");
        view.setIsDefault(1);
        view.setStatus("ACTIVE");
        
        return view;
    }
    
    private String getEntityName(String entityCode) {
        if (!StringUtils.hasText(entityCode)) {
            return null;
        }
        return entityDefinitionMapper.findByEntityCode(entityCode)
                .map(EntityDefinition::getEntityName)
                .orElse(null);
    }
    
    private String generateViewCode(String viewName) {
        return "VIEW_" + System.currentTimeMillis();
    }
}
