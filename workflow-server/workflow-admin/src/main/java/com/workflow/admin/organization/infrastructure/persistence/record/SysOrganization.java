package com.workflow.admin.organization.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
    /** 同次管理保存提交的本地扩展组，未携带的组保持原值。 */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private java.util.Map<String, com.workflow.contracts.identity.model.IdentityExtensionEdit> extensions;

    /** 授权后的资料与版本，供通用表单回填；不接受客户端指定读取结果。 */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private java.util.Map<String, com.workflow.contracts.identity.model.IdentityExtensionValue> extensionValues;

    /** 来源维护标识，用于页面只读基础信息和展示独立的本地停用状态。 */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private com.workflow.contracts.identity.model.IdentitySourceInfo sourceInfo;

    
    /** 主键ID（雪花算法分配） */
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
     * 稳定业务层级编码；与物理树深度 level 相互独立。
     * ALWAYS 允许管理员显式清空旧层级；负责人字段仍保持默认 NOT_NULL，
     * 不会被普通组织保存误清空。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String businessLevelCode;
    
    /**
     * 父级ID，顶级为 0，来源父级尚未到达时为空，后续由来源关联处理补齐。
     */
    private String parentId;

    /**
     * 父链已接到真实根节点时才为 true，供组织树过滤和流程组织链校验使用。
     * 仅由来源关联 SQL 维护，普通局部更新不得把 DTO 的默认值写回待关联组织。
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private boolean hierarchyReady = true;
    
    /**
     * 层级（0为顶级）
     * 冗余字段，避免递归计算层级，父链未完整时为空，补齐后连同下级重新计算。
     */
    private Integer level;
    
    /**
     * 完整路径，如：/0/1/5/10/
     * 冗余字段，避免递归查询所有父级，父链未完整时为空，避免使用不完整路径查询下级。
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

    /** 创建者用户主键，供流程沿部门关系寻找创建人；历史未知值保持为空，更新时不可改写。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String createBy;
    
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

    /** 管理页按需展开标记，由当前页节点批量查询得出，不写入数据库。 */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private boolean hasChildren;

    /** 管理页完整名称路径，用于搜索结果区分同名部门及选择上级时回显。 */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String displayPath;
    
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
    
    /**
     * 定义类型的可选值；调用方据此选择对应的处理分支。
     */
    public enum Type {
        /** 组织 */
        ORG("org"),
        /** 部门 */
        DEPT("dept");
        
        private final String value;
        
        /**
         * 初始化类型，保存构造参数供后续方法使用。
         *
         * @param value 值依赖，保存到当前对象供后续业务方法调用
         */
        Type(String value) {
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
