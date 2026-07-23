package com.workflow.entity.migration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置导入条目。
 *
 * <p>记录导入批次中单个资产的导入全过程状态，包括源/目标版本对照、
 * 比较结果(NEW/CONSISTENT/CONFLICT等)、依赖映射状态、发布状态及异常信息。</p>
 */
@Data
@TableName("config_import_item")
public class ConfigImportItem {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;                          // 主键
    private String importPackageId;            // 所属导入批次ID
    private String assetType;                   // 资产类型(ENTITY/PROCESS)
    private String businessKey;                 // 资产业务编码
    private String assetName;                   // 资产名称
    private Integer sourceVersion;              // 源版本号
    private String sourceHash;                   // 源内容哈希
    private Integer targetBeforeVersion;       // 发布前目标环境版本
    private String targetBeforeHash;            // 发布前目标环境内容哈希
    private Integer targetAfterVersion;         // 发布后目标环境版本
    private String targetAfterHash;             // 发布后目标环境内容哈希
    private String comparisonStatus;            // 比较状态(NEW/CONSISTENT/CONFLICT/LOCAL_CHANGED/SOURCE_NEWER)
    private String mappingStatus;               // 依赖映射状态(RESOLVED/UNRESOLVED)
    private String publishStatus;              // 发布状态(PENDING/PUBLISHING/SUCCESS/ROLLED_BACK)
    private String snapshotJson;                // 快照内容(JSON)
    private String dependenciesJson;            // 依赖清单(JSON)
    private String errorMessage;                // 异常或阻断原因
    private LocalDateTime createdAt;            // 创建时间
    private LocalDateTime updatedAt;            // 更新时间
}
