package com.workflow.entity.form.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 表单唯一的线上配置。发布覆盖同一行，历史审计不参与运行时配置选择。 */
@Data
@TableName("entity_form_published")
public class EntityFormPublished {
    /** 稳定表单身份；所有流程和页面通过此键读取当前配置。 */
    @TableId
    private String formId;
    private String entityId;
    /** 完整配置用于一次请求内的渲染、事件执行及提交校验。 */
    private String snapshotDocument;
    /** 内容指纹用于完整性及旧页面检查，不是可固定的业务版本。 */
    private String contentHash;
    private String publishedBy;
    private LocalDateTime publishedAt;
}
