package com.workflow.dto;

import lombok.Data;

import java.util.Map;

/**
 * UI 组件模板升级请求。
 * 用于将基于旧版本模板的配置升级到新版本，并合并本地覆盖项。
 */
@Data
public class UiComponentTemplateUpgradeRequest {

    /** 原版本号 */
    private Integer fromVersion;
    /** 目标版本号 */
    private Integer toVersion;
    /** 当前快照内容 */
    private Map<String, Object> currentSnapshot;
    /** 本地覆盖配置 */
    private Map<String, Object> localOverrides;
}
