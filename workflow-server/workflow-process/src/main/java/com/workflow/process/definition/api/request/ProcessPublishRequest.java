package com.workflow.process.definition.api.request;

import com.workflow.contracts.migration.ConfigMigrationPublishRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程发布请求。
 *
 * <p>除配置迁移标记外，正式发布必须携带最近一次预检绑定的草稿修订号、内容哈希和预检令牌，
 * 防止预检后草稿发生变化仍被静默发布。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessPublishRequest extends ConfigMigrationPublishRequest {

    /** 预检时的流程草稿修订号 */
    private Long expectedRevision;

    /** 预检时的流程草稿 SHA-256 */
    private String expectedDraftHash;

    /** 由发布预检接口生成的内容绑定令牌 */
    private String previewToken;
}
