package com.workflow.entity.ui.api.request;

import lombok.Data;

/**
 * 撤销表单或列表未发布修改的并发前置条件。
 *
 * <p>三项期望值均来自用户确认撤销前读取到的草稿差异；服务端会在配置行锁内
 * 重新计算草稿哈希，避免覆盖确认之后保存的表单、列表或事件绑定修改。</p>
 */
@Data
public class UiConfigDraftDiscardRequest {

    /** 用户确认时看到的配置修订号。 */
    private Integer expectedRevision;
    /** 用户确认时看到的 canonical 草稿哈希。 */
    private String expectedDraftHash;
    /** 用户确认时看到的当前激活发布 ID。 */
    private String expectedActiveReleaseId;
    /** 可选审计说明。 */
    private String reason;
}
