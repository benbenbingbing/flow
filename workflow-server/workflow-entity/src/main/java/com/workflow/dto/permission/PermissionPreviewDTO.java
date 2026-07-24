package com.workflow.dto.permission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据权限 SQL 预览结果
 */
@Data
public class PermissionPreviewDTO {

    /** 最终生效的 SQL 条件 */
    private String sql;

    /** 是否拥有权限（false = 1=0） */
    private boolean hasPermission = true;

    /** 是否需要过滤（false = 1=1 全放行） */
    private boolean needFilter = false;

    /** 命中的规则明细 */
    private List<MatchedRuleDTO> matchedRules = new ArrayList<>();

    /** 预览提示/说明（如当前用户缺少某属性时给出提示） */
    private String remark;

    /** 数据范围模式 */
    private String dataScopeMode;

    /** 生效的发布版本号 */
    private Integer releaseVersion;

    /**
     * 命中规则明细
     */
    @Data
    public static class MatchedRuleDTO {
        /** 规则名称 */
        private String ruleName;
        /** 规则效果（INCLUDE/EXCLUDE） */
        private String ruleEffect;
        /** 列表标识 */
        private String listKey;
        /** 该规则生成的 SQL 条件 */
        private String sql;
    }
}
