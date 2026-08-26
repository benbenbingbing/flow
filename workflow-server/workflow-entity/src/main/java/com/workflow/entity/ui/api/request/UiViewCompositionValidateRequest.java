package com.workflow.entity.ui.api.request;

import lombok.Data;

import java.util.Map;

/**
 * “关联内容”保存前校验请求，不产生持久化副作用。
 */
@Data
public class UiViewCompositionValidateRequest {

    /** FORM/LIST 宿主类型。 */
    private String ownerType;
    /** 表单或列表配置 ID。 */
    private String ownerId;
    /** 可选的真实来源记录；为空时服务端只在当前权限范围内选取一条样本。 */
    private String sourceRecordId;
    /** 四步引导生成的结构化关联内容配置。 */
    private Map<String, Object> config;
}
