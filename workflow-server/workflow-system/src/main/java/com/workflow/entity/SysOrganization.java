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
 * 组织部门实体
 * 组织和部门使用同一张表，通过type字段区分
 * 树形结构使用path字段避免递归查询
 */
@Data
@TableName("sys_organization")
public class SysOrganization {
    
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 组织编码（唯一）
     */
    private String orgCode;
    
    /**
     * 组织名称
     */
    private String orgName;
    
    /**
     * 类型：org-组织，dept-部门
     */
    private String type;
    
    /**
     * 父级ID（顶级为0）
     */
    private String parentId;
    
    /**
     * 层级（0为顶级）
     * 冗余字段，避免递归计算层级
     */
    private Integer level;
    
    /**
     * 完整路径，如：/0/1/5/10/
     * 冗余字段，避免递归查询所有父级
     */
    private String path;
    
    /**
     * 排序号
     */
    private Integer sortOrder;
    
    /**
     * 负责人ID
     */
    private String leaderId;
    
    /**
     * 负责人名称（冗余）
     */
    private String leaderName;
    
    /**
     * 联系电话
     */
    private String phone;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 地址
     */
    private String address;
    
    /**
     * 状态：0-启用，1-禁用
     */
    private String status;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 删除标志
     */
    @TableLogic
    private Integer deleted;
    
    // ========== 非数据库字段 ==========
    
    /**
     * 子节点列表
     */
    @TableField(exist = false)
    private List<SysOrganization> children;
    
    /**
     * 父级名称
     */
    @TableField(exist = false)
    private String parentName;
    
    /**
     * 用户数量（冗余统计）
     */
    @TableField(exist = false)
    private Integer userCount;
    
    public enum Type {
        /** 组织 */
        ORG("org"),
        /** 部门 */
        DEPT("dept");
        
        private final String value;
        
        Type(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
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
}
