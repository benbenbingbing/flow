package com.workflow.contracts.entity.permission.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 自定义权限候选项；宿主按 code 去重并映射为 HTTP 响应，候选展示本身不授予权限。 */
@Data
@AllArgsConstructor
public class EntityPermissionOption {
    /** 对应按钮操作，供配置界面分类和选择。 */
    private String action;
    /** 稳定权限码，保存到配置后由宿主校验归属和授权。 */
    private String code;
    private String label;
    private String description;
    private String category;
}
