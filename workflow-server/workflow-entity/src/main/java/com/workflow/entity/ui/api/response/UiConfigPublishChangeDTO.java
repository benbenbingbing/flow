package com.workflow.entity.ui.api.response;

import lombok.Builder;
import lombok.Value;

/** 发布确认中的业务变更，保留原始补丁值供界面生成可读的前后对比。 */
@Value
@Builder
public class UiConfigPublishChangeDTO {
    /** 分区和条目 ID 共同标识变更对象，名称不能作为匹配键。 */
    String section;
    String itemId;
    /** 删除时使用旧名称，其他变更使用草稿名称。 */
    String itemLabel;
    /** 仅改名时提供旧名称，方便确认修改的是哪个对象。 */
    String previousItemLabel;
    /** ADDED / UPDATED / REMOVED；根路径 / 表示整项新增或删除。 */
    String changeType;
    String path;
    /** 整项变更保留完整对象；属性变更保留实际类型，不预先字符串化。 */
    Object beforeValue;
    Object afterValue;
    /** 可确认的业务展示值（如同级排列位置、父容器名称）；原始值仍保留供技术追溯。 */
    String beforeDisplayValue;
    String afterDisplayValue;
    /** 继承发布预检风险，不因展示去重或首次发布而降低校验等级。 */
    String riskLevel;
    String reason;
}
