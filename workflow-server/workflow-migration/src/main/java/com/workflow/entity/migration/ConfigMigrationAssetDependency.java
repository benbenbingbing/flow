package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置迁移资产依赖。
 *
 * <p>记录某个迁移资产在导入/导出时依赖的其他资产或资源(实体、流程、表单、用户、字典等)，
 * 用于依赖解析、阻断项分析与导出包完整性校验。</p>
 */
@Data
@TableName("config_migration_asset_dependency")
public class ConfigMigrationAssetDependency {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;                          // 主键
    private String assetId;                     // 所属迁移资产ID
    private String dependencyType;              // 依赖类型(ENTITY/PROCESS/FORM/USER等)
    private String dependencyKey;              // 依赖业务编码
    private Boolean required;                   // 是否为硬依赖(必须满足才能导出/发布)
    private String sourceDescription;           // 依赖来源说明
    private String dependencyDocument;          // 依赖完整描述文档(JSON)

    @TableField("create_time")
    private LocalDateTime createdAt;            // 依赖记录创建时间
}
