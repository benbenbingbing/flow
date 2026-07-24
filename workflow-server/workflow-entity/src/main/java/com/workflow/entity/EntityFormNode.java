package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体表单节点实体，对应 entity_form_node 表。
 * 描述表单的递归节点树结构（容器、字段、子表单等），承载组件绑定、属性与校验配置。
 */
@Data
@TableName("entity_form_node")
public class EntityFormNode {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 所属表单ID */
    private String formId;
    /** 父节点ID（用于构建节点树） */
    private String parentId;
    /** 节点唯一标识（表单内唯一） */
    private String nodeKey;
    /** 节点类型（如 container/field/subform 等） */
    private String nodeType;
    /** 绑定类型（如 field/relation/dataSource 等） */
    private String bindingType;
    /** 绑定引用（如字段编码） */
    private String bindingRef;
    /** 前端渲染组件注册名 */
    private String componentName;
    /** 组件版本号 */
    private Integer componentVersion;
    /** 当前激活的组件发布快照版本号 */
    private Integer snapshotVersion;
    /** 节点属性配置（JSON） */
    private String propsDocument;
    /** 节点校验规则配置（JSON） */
    private String rulesDocument;
    /** 节点数据源绑定配置（JSON） */
    private String dataSourceBindingsDocument;
    /** 旧版属性配置（JSON，兼容历史数据） */
    private String legacyPropsDocument;
    /** 稀疏排序键，用于节点排序 */
    private Long orderKey;
    /** 草稿元数据修订号 */
    private Integer revision;
    /** 节点引用的组件模板ID */
    private String templateId;
    /** 节点引用的组件模板版本号 */
    private Integer templateVersion;
    /** 模板本地覆盖配置（JSON） */
    private String localOverridesDocument;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updatedAt;

    /** 逻辑删除标志（0-未删除 1-已删除） */
    @TableLogic
    private Integer deleted;
}
