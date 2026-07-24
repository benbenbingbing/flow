package com.workflow.dto.permission;

import com.workflow.dto.EntityDataDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体列表数据范围模拟结果 DTO。
 * 用于模拟指定用户在某个列表下可见的数据范围与样例数据。
 */
@Data
public class EntityListScopeSimulationDTO {
    /** 实体编码 */
    private String entityCode;
    /** 列表标识 */
    private String listKey;
    /** 模拟的用户 ID */
    private String userId;
    /** 数据范围模式 */
    private String dataScopeMode;
    /** 生效的发布版本号 */
    private Integer releaseVersion;
    /** 权限预览结果（SQL/命中规则等） */
    private PermissionPreviewDTO preview;
    /** 可见数据条数 */
    private long visibleCount;
    /** 可见数据样例 */
    private List<EntityDataDTO> samples = new ArrayList<>();
    /** 模拟过程中的告警信息 */
    private List<String> warnings = new ArrayList<>();
}
