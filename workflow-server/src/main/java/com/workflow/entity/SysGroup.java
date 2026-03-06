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
 * 用户组实体
 */
@Data
@TableName("sys_group")
public class SysGroup {
    
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 组名称
     */
    private String groupName;
    
    /**
     * 组编码
     */
    private String groupCode;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 排序
     */
    private Integer sort;
    
    /**
     * 状态：0-启用 1-禁用
     */
    private String status;
    
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
    
    /**
     * 用户列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<SysUser> users;
    
    /**
     * 用户ID列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<String> userIds;
    
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
