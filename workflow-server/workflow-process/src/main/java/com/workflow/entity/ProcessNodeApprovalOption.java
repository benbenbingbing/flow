package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点审批选项实体
 * 定义节点审批操作的选项列表（如同意、驳回、退回等），供前端按钮展示及操作校验
 */
@Data
@TableName("process_node_approval_option")
public class ProcessNodeApprovalOption {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 关联的审批配置ID */
    private String approvalConfigId;
    /** 选项值（如 approve/reject/return） */
    private String optionValue;
    /** 选项显示名称 */
    private String optionLabel;
    /** 按钮样式类型（如 primary/success/danger） */
    private String styleType;
    /** 是否显示审批意见输入框 */
    private Boolean showComment;
    /** 是否强制要求填写备注 */
    private Boolean remarkRequired;
    /** 排序号 */
    private Integer sortOrder;
    /** 选项说明文档 */
    private String optionDocument;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updatedAt;
}
