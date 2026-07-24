package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单权限实体
 * <p>
 * 对应 sys_menu 表，存储菜单/目录/按钮及权限标识。菜单类型分为 M-目录、C-菜单、F-按钮。
 * 通过 entity_code/list_key/resource_type 字段支持动态实体数据列表菜单。
 * 非数据库字段（children、parentName、hasChildren）用于树形回显。
 * </p>
 */
@Data
@TableName("sys_menu")
public class SysMenu {
    
    /** 主键ID（雪花算法分配） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 父菜单ID
     */
    private String parentId;
    
    /**
     * 菜单名称
     */
    private String menuName;
    
    /**
     * 菜单类型：M-目录 C-菜单 F-按钮
     */
    private String menuType;
    
    /**
     * 菜单图标
     */
    private String icon;
    
    /**
     * 显示排序
     */
    private Integer sort;
    
    /**
     * 路由地址
     */
    private String path;
    
    /**
     * 组件路径
     */
    private String component;
    
    /**
     * 权限标识
     */
    private String perm;
    
    /**
     * 状态：0-启用 1-禁用
     */
    private String status;
    
    /**
     * 显示状态：0-显示 1-隐藏
     */
    private String visible;
    
    /**
     * 是否外链：0-否 1-是
     */
    @TableField("is_frame")
    private String isFrame;
    
    /**
     * 是否缓存：0-缓存 1-不缓存
     */
    @TableField("is_cache")
    private String isCache;
    
    /**
     * 路由参数
     */
    private String query;
    
    /**
     * 关联实体编码（用于实体数据菜单）
     * 当菜单类型为C且配置了此字段时，点击菜单将跳转到对应实体的数据列表
     */
    @TableField("entity_code")
    private String entityCode;

    /**
     * 动态资源类型，ENTITY_LIST 表示通用实体列表。
     */
    @TableField("resource_type")
    private String resourceType;

    /**
     * 动态实体列表编码。
     */
    @TableField("list_key")
    private String listKey;
    
    /**
     * 是否删除：0-未删除 1-已删除
     */
    @TableLogic
    private Integer deleted;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 子菜单（非数据库字段）
     */
    @TableField(exist = false)
    private List<SysMenu> children;
    
    /**
     * 父菜单名称（非数据库字段）
     */
    @TableField(exist = false)
    private String parentName;

    /**
     * 是否有子菜单（非数据库字段）
     */
    @TableField(exist = false)
    private Boolean hasChildren;

    // Getter 和 Setter 方法
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getMenuName() {
        return menuName;
    }
    
    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }
    
    public String getMenuType() {
        return menuType;
    }
    
    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }
    
    public String getIcon() {
        return icon;
    }
    
    public void setIcon(String icon) {
        this.icon = icon;
    }
    
    public Integer getSort() {
        return sort;
    }
    
    public void setSort(Integer sort) {
        this.sort = sort;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getComponent() {
        return component;
    }
    
    public void setComponent(String component) {
        this.component = component;
    }
    
    public String getPerm() {
        return perm;
    }
    
    public void setPerm(String perm) {
        this.perm = perm;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getVisible() {
        return visible;
    }
    
    public void setVisible(String visible) {
        this.visible = visible;
    }
    
    public String getIsFrame() {
        return isFrame;
    }
    
    public void setIsFrame(String isFrame) {
        this.isFrame = isFrame;
    }
    
    public String getIsCache() {
        return isCache;
    }
    
    public void setIsCache(String isCache) {
        this.isCache = isCache;
    }
    
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    public String getEntityCode() {
        return entityCode;
    }
    
    public void setEntityCode(String entityCode) {
        this.entityCode = entityCode;
    }
    
    public Integer getDeleted() {
        return deleted;
    }
    
    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
    
    public List<SysMenu> getChildren() {
        return children;
    }
    
    public void setChildren(List<SysMenu> children) {
        this.children = children;
    }
    
    public String getParentName() {
        return parentName;
    }
    
    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public Boolean getHasChildren() {
        return hasChildren;
    }

    public void setHasChildren(Boolean hasChildren) {
        this.hasChildren = hasChildren;
    }

    public enum MenuType {
        /** 目录 */
        M,
        /** 菜单 */
        C,
        /** 按钮 */
        F
    }
    
    public enum Status {
        /** 启用 */
        ENABLED("0"),
        /** 禁用 */
        DISABLED("1");
        
        private final String value;
        
        Status(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    public enum Visible {
        /** 显示 */
        SHOW("0"),
        /** 隐藏 */
        HIDDEN("1");
        
        private final String value;
        
        Visible(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
}
