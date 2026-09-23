package com.workflow.admin.identity.group.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户组实体
 * <p>
 * 对应 sys_group 表，存储用户组编码、名称、状态等，组编码唯一。
 * 非数据库字段（users、userIds）用于回显组内成员。
 * </p>
 */
@Data
@TableName("sys_group")
public class SysGroup {
    
    /** 主键ID（雪花算法分配） */
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
    @TableField("sort_order")
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
}
