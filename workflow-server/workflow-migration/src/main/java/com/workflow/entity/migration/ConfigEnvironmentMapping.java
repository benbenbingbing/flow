package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置环境映射。
 *
 * <p>记录源环境与目标环境之间的资源编码映射关系(如用户名、角色、部门、数据源等)，
 * 在导入分析阶段用于将源环境的依赖键解析为目标环境对应的键。</p>
 */
@Data
@TableName("config_environment_mapping")
public class ConfigEnvironmentMapping {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;                          // 主键
    private String sourceType;                  // 资源类型(USER/ROLE/DEPT/ENTITY等)
    private String sourceKey;                   // 源环境编码
    private String targetKey;                   // 目标环境对应编码
    private String description;                 // 映射说明
    private Boolean enabled;                    // 是否启用
    private LocalDateTime createdAt;             // 创建时间
    private LocalDateTime updatedAt;            // 更新时间
}
