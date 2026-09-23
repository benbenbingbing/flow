package com.workflow.admin.authorization.menu.infrastructure.persistence.record;

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
    /**
     * 读取ID；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的ID文本，供调用方比较或展示
     */
    public String getId() {
        return id;
    }

    /**
     * 设置ID；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 读取父级ID；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的父级ID文本，供调用方比较或展示
     */
    public String getParentId() {
        return parentId;
    }

    /**
     * 设置父级ID；后续读取或执行将使用更新后的状态。
     *
     * @param parentId 父级ID，后续用于设置父级ID时定位或关联目标
     */
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    /**
     * 读取菜单名称；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的菜单名称文本，供调用方比较或展示
     */
    public String getMenuName() {
        return menuName;
    }
    
    /**
     * 设置菜单名称；后续读取或执行将使用更新后的状态。
     *
     * @param menuName 菜单名称，后续用于设置菜单名称时匹配或展示
     */
    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }
    
    /**
     * 读取菜单类型；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的菜单类型文本，供调用方比较或展示
     */
    public String getMenuType() {
        return menuType;
    }
    
    /**
     * 设置菜单类型；后续读取或执行将使用更新后的状态。
     *
     * @param menuType 菜单类型标识，决定后续菜单类型采用的处理分支
     */
    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }
    
    /**
     * 读取{@code icon}；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的{@code icon}文本，供调用方比较或展示
     */
    public String getIcon() {
        return icon;
    }
    
    /**
     * 设置{@code icon}；后续读取或执行将使用更新后的状态。
     *
     * @param icon {@code icon}，供本方法设置{@code icon}时使用
     */
    public void setIcon(String icon) {
        this.icon = icon;
    }
    
    /**
     * 读取排序；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的系统菜单结果，供调用方继续处理
     */
    public Integer getSort() {
        return sort;
    }
    
    /**
     * 设置排序；后续读取或执行将使用更新后的状态。
     *
     * @param sort 排序，供本方法设置排序时使用
     */
    public void setSort(Integer sort) {
        this.sort = sort;
    }
    
    /**
     * 读取路径；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的路径文本，供调用方比较或展示
     */
    public String getPath() {
        return path;
    }
    
    /**
     * 设置路径；后续读取或执行将使用更新后的状态。
     *
     * @param path 路径，供本方法设置路径时使用
     */
    public void setPath(String path) {
        this.path = path;
    }
    
    /**
     * 读取组件；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的组件文本，供调用方比较或展示
     */
    public String getComponent() {
        return component;
    }
    
    /**
     * 设置组件；后续读取或执行将使用更新后的状态。
     *
     * @param component 组件，供本方法设置组件时使用
     */
    public void setComponent(String component) {
        this.component = component;
    }
    
    /**
     * 读取{@code perm}；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的{@code perm}文本，供调用方比较或展示
     */
    public String getPerm() {
        return perm;
    }
    
    /**
     * 设置{@code perm}；后续读取或执行将使用更新后的状态。
     *
     * @param perm {@code perm}，供本方法设置{@code perm}时使用
     */
    public void setPerm(String perm) {
        this.perm = perm;
    }
    
    /**
     * 读取状态；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的状态文本，供调用方比较或展示
     */
    public String getStatus() {
        return status;
    }
    
    /**
     * 设置状态；后续读取或执行将使用更新后的状态。
     *
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     */
    public void setStatus(String status) {
        this.status = status;
    }
    
    /**
     * 读取可见；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的可见文本，供调用方比较或展示
     */
    public String getVisible() {
        return visible;
    }
    
    /**
     * 设置可见；后续读取或执行将使用更新后的状态。
     *
     * @param visible 可见，供本方法设置可见时使用
     */
    public void setVisible(String visible) {
        this.visible = visible;
    }
    
    /**
     * 读取是否{@code frame}；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的是否{@code frame}文本，供调用方比较或展示
     */
    public String getIsFrame() {
        return isFrame;
    }
    
    /**
     * 设置是否{@code frame}；后续读取或执行将使用更新后的状态。
     *
     * @param isFrame 是否{@code frame}，供本方法设置是否{@code frame}时使用
     */
    public void setIsFrame(String isFrame) {
        this.isFrame = isFrame;
    }
    
    /**
     * 读取是否缓存；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的是否缓存文本，供调用方比较或展示
     */
    public String getIsCache() {
        return isCache;
    }
    
    /**
     * 设置是否缓存；后续读取或执行将使用更新后的状态。
     *
     * @param isCache 是否缓存，供本方法设置是否缓存时使用
     */
    public void setIsCache(String isCache) {
        this.isCache = isCache;
    }
    
    /**
     * 读取查询；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的查询文本，供调用方比较或展示
     */
    public String getQuery() {
        return query;
    }
    
    /**
     * 设置查询；后续读取或执行将使用更新后的状态。
     *
     * @param query 查询，供本方法设置查询时使用
     */
    public void setQuery(String query) {
        this.query = query;
    }
    
    /**
     * 读取实体编码；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的实体编码文本，供调用方比较或展示
     */
    public String getEntityCode() {
        return entityCode;
    }
    
    /**
     * 设置实体编码；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     */
    public void setEntityCode(String entityCode) {
        this.entityCode = entityCode;
    }
    
    /**
     * 读取已删除；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的系统菜单结果，供调用方继续处理
     */
    public Integer getDeleted() {
        return deleted;
    }
    
    /**
     * 设置已删除；后续读取或执行将使用更新后的状态。
     *
     * @param deleted 已删除，供本方法设置已删除时使用
     */
    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
    
    /**
     * 读取创建时间；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的本地日期时间结果，供调用方继续处理
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    /**
     * 设置创建时间；后续读取或执行将使用更新后的状态。
     *
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    /**
     * 读取更新时间；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的本地日期时间结果，供调用方继续处理
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
    
    /**
     * 设置更新时间；后续读取或执行将使用更新后的状态。
     *
     * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
    
    /**
     * 读取子节点；查询结果供调用方展示或继续处理。
     *
     * @return 系统菜单集合，供调用方遍历或展示
     */
    public List<SysMenu> getChildren() {
        return children;
    }
    
    /**
     * 设置子节点；后续读取或执行将使用更新后的状态。
     *
     * @param children 子节点，供本方法设置子节点时使用
     */
    public void setChildren(List<SysMenu> children) {
        this.children = children;
    }
    
    /**
     * 读取父级名称；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的父级名称文本，供调用方比较或展示
     */
    public String getParentName() {
        return parentName;
    }
    
    /**
     * 设置父级名称；后续读取或执行将使用更新后的状态。
     *
     * @param parentName 父级名称，后续用于设置父级名称时匹配或展示
     */
    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    /**
     * 读取{@code has}子节点；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的系统菜单结果，供调用方继续处理
     */
    public Boolean getHasChildren() {
        return hasChildren;
    }

    /**
     * 设置{@code has}子节点；后续读取或执行将使用更新后的状态。
     *
     * @param hasChildren {@code has}子节点，供本方法设置{@code has}子节点时使用
     */
    public void setHasChildren(Boolean hasChildren) {
        this.hasChildren = hasChildren;
    }

    /**
     * 定义菜单类型的可选值；调用方据此选择对应的处理分支。
     */
    public enum MenuType {
        /** 目录 */
        M,
        /** 菜单 */
        C,
        /** 按钮 */
        F
    }
    
    /**
     * 定义状态的可选值；调用方据此选择对应的处理分支。
     */
    public enum Status {
        /** 启用 */
        ENABLED("0"),
        /** 禁用 */
        DISABLED("1");
        
        private final String value;
        
        /**
         * 初始化状态，保存构造参数供后续方法使用。
         *
         * @param value 值依赖，保存到当前对象供后续业务方法调用
         */
        Status(String value) {
            this.value = value;
        }
        
        /**
         * 读取值；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的值文本，供调用方比较或展示
         */
        public String getValue() {
            return value;
        }
    }
    
    /**
     * 定义可见的可选值；调用方据此选择对应的处理分支。
     */
    public enum Visible {
        /** 显示 */
        SHOW("0"),
        /** 隐藏 */
        HIDDEN("1");
        
        private final String value;
        
        /**
         * 初始化可见，保存构造参数供后续方法使用。
         *
         * @param value 值依赖，保存到当前对象供后续业务方法调用
         */
        Visible(String value) {
            this.value = value;
        }
        
        /**
         * 读取值；查询结果供调用方展示或继续处理。
         *
         * @return 读取后的值文本，供调用方比较或展示
         */
        public String getValue() {
            return value;
        }
    }
}
