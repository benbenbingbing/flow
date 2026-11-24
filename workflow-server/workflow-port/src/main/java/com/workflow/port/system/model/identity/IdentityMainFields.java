package com.workflow.port.system.model.identity;

import lombok.Builder;

/**
 * 用户、组织主表的五个可查询文本槽位，每项最多 500 字符，用途和格式由接入业务约定。
 * 保存模型省略本组时保留现值；携带时整体替换五列，组内 null 显式清空对应列。
 * 不从原始 JSON 自动提取，避免来源字段变化时静默改变业务查询结果。
 */
@Builder
public record IdentityMainFields(String ext1, String ext2, String ext3, String ext4, String ext5) {
}
