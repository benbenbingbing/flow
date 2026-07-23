package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置资产迁移基线。
 *
 * <p>记录某次成功导入发布后，源环境与目标环境的资产版本/内容哈希对照关系，
 * 用于后续导入时判断生产环境是否相对基线发生了本地修改，从而识别冲突或增量更新。</p>
 */
@Data
@TableName("config_asset_baseline")
public class ConfigAssetBaseline {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;                              // 主键
    private String assetType;                       // 资产类型(ENTITY/PROCESS)
    private String businessKey;                     // 资产业务编码(实体编码或流程Key)
    private Integer sourceVersion;                 // 基线对应的源环境资产版本
    private String sourceHash;                      // 基线对应的源环境资产内容哈希
    private Integer targetVersion;                 // 基线对应的目标环境资产版本
    private String targetHash;                     // 基线对应的目标环境资产内容哈希
    private String importPackageId;               // 生成该基线的导入批次ID
    private LocalDateTime updatedAt;              // 基线最后更新时间
}
