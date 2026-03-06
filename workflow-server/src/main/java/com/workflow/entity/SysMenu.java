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
 */
@Data
@TableName("sys_menu")
public class SysMenu {
    
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
    private String isFrame;
    
    /**
     * 是否缓存：0-缓存 1-不缓存
     */
    private String isCache;
    
    /**
     * 路由参数
     */
    private String query;
    
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
