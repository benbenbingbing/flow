package com.workflow.admin.identity.user.infrastructure.persistence.record;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户实体
 * <p>
 * 对应 sys_user 表，存储用户名、昵称、密码、状态及组织/部门归属等，
 * 用户名唯一。非数据库字段（roles、roleIds、orgName、deptName）用于回显。
 * </p>
 */
@Data
@TableName("sys_user")
public class SysUser {
    public static final String LOCAL_PASSWORD = "LOCAL_PASSWORD";
    public static final String EXTERNAL_ONLY = "EXTERNAL_ONLY";
    public static final String EXTERNAL_AND_LOCAL_PASSWORD = "EXTERNAL_AND_LOCAL_PASSWORD";

    /** 同次管理保存提交的本地扩展组，未携带的组保持原值。 */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private java.util.Map<String, com.workflow.port.system.model.identity.IdentityExtensionEdit> extensions;

    /** 扩展字段1：由接入业务约定用途，来源同步维护，供主表条件查询和详情读取。 */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String ext1;

    /** 扩展字段2：由接入业务约定用途，来源同步维护，供主表条件查询和详情读取。 */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String ext2;

    /** 扩展字段3：由接入业务约定用途，来源同步维护，供主表条件查询和详情读取。 */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String ext3;

    /** 扩展字段4：由接入业务约定用途，来源同步维护，供主表条件查询和详情读取。 */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String ext4;

    /** 扩展字段5：由接入业务约定用途，来源同步维护，供主表条件查询和详情读取。 */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String ext5;

    /** 授权后的资料与版本，供通用表单回填；不接受客户端指定读取结果。 */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private java.util.Map<String, com.workflow.port.system.model.identity.IdentityExtensionValue> extensionValues;

    /** 来源维护标识，用于页面只读基础信息和展示独立的本地停用状态。 */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private com.workflow.port.system.model.identity.IdentitySourceInfo sourceInfo;

    /** 认证模式同时记录账号来源和本地密码登录能力，由创建或有权限的密码重置入口维护。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String authenticationMode;

    /** 外部预配账号开通本地密码后，资料维护和来源绑定仍遵循外部账号规则。 */
    public static boolean isExternallyProvisioned(String mode) {
        return EXTERNAL_ONLY.equals(mode) || EXTERNAL_AND_LOCAL_PASSWORD.equals(mode);
    }

    
    /** 主键ID（雪花算法分配） */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 密码
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /**
     * 是否必须在继续使用系统前修改密码
     */
    private Boolean passwordResetRequired;

    /** 撤销该用户全部登录会话时递增的全局令牌版本。 */
    private Long tokenVersion;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 手机号
     */
    private String phone;
    
    /**
     * 头像
     */
    private String avatar;
    
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
     * 组织ID
     */
    private String orgId;
    
    /**
     * 部门ID
     */
    private String deptId;
    
    /**
     * 组织名称（冗余）
     */
    @TableField(exist = false)
    private String orgName;
    
    /**
     * 部门名称（冗余）
     */
    @TableField(exist = false)
    private String deptName;
    
    /**
     * 角色列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<SysRole> roles;
    
    /**
     * 角色ID列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<String> roleIds;

    /** 当前有效任职摘要，仅在用户管理查询中回填。 */
    @TableField(exist = false)
    private List<CurrentPositionAssignment> currentPositionAssignments;

    /**
     * 用户列表所需的最小任职摘要，职务仍不等同于权限角色。
     *
     * @param assignmentId 分配ID，后续用于处理当前位置分配时定位或关联目标
     * @param positionCode 位置编码，后续用于处理当前位置分配时定位或关联目标
     * @param positionName 位置名称，后续用于处理当前位置分配时匹配或展示
     * @param organizationUnitId 组织单元ID，后续用于处理当前位置分配时定位或关联目标
     * @param organizationUnitName 组织单元名称，后续用于处理当前位置分配时匹配或展示
     */
    public record CurrentPositionAssignment(
            String assignmentId,
            String positionCode,
            String positionName,
            String organizationUnitId,
            String organizationUnitName) {
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
}
