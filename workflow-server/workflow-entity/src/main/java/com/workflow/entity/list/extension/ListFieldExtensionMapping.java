package com.workflow.entity.list.extension;

import com.workflow.contracts.entity.list.model.ListFieldDataConfig;
import com.workflow.contracts.entity.list.model.ListFieldDataRecord;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import java.util.LinkedHashMap;

/** 将宿主对象投影为扩展输入，避免把 API 令牌、权限结果或 ORM 对象传给 Provider。 */
public final class ListFieldExtensionMapping {
    private ListFieldExtensionMapping() { }

    /** 复制当前行；只有 data/extData 作为可变展示结果交回宿主。 */
    public static ListFieldDataRecord record(EntityDataDTO source) {
        ListFieldDataRecord target = new ListFieldDataRecord();
        target.setId(source.getId());
        target.setEntityCode(source.getEntityCode());
        target.setEntityName(source.getEntityName());
        target.setName(source.getName());
        target.setCode(source.getCode());
        target.setStatus(source.getStatus());
        target.setProcessStatus(source.getProcessStatus());
        target.setProcessInstanceId(source.getProcessInstanceId());
        target.setProcessStartTime(source.getProcessStartTime());
        target.setProcessEndTime(source.getProcessEndTime());
        target.setCurrentTaskId(source.getCurrentTaskId());
        target.setCurrentTaskName(source.getCurrentTaskName());
        target.setCurrentTaskAssignee(source.getCurrentTaskAssignee());
        target.setData(source.getData() == null ? null : new LinkedHashMap<>(source.getData()));
        target.setSubmitterId(source.getSubmitterId());
        target.setSubmitterName(source.getSubmitterName());
        target.setDeptId(source.getDeptId());
        target.setDeptName(source.getDeptName());
        target.setSubmitTime(source.getSubmitTime());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        target.setCreateBy(source.getCreateBy());
        target.setUpdateBy(source.getUpdateBy());
        target.setDeleted(source.getDeleted());
        target.setExtData(source.getExtData() == null ? null : new LinkedHashMap<>(source.getExtData()));
        return target;
    }

    /** 配置传递值快照；Provider 校验或补数不能修改持久化配置对象。 */
    public static ListFieldDataConfig field(EntityListField source) {
        ListFieldDataConfig target = new ListFieldDataConfig();
        target.setId(source.getId());
        target.setListConfigId(source.getListConfigId());
        target.setFieldId(source.getFieldId());
        target.setFieldCode(source.getFieldCode());
        target.setFieldName(source.getFieldName());
        target.setSortOrder(source.getSortOrder());
        target.setOrderKey(source.getOrderKey());
        target.setRevision(source.getRevision());
        target.setWidth(source.getWidth());
        target.setShowInList(source.getShowInList());
        target.setIsQuery(source.getIsQuery());
        target.setQueryType(source.getQueryType());
        target.setAlign(source.getAlign());
        target.setDataSourceType(source.getDataSourceType());
        target.setDataSourceConfig(source.getDataSourceConfig());
        target.setInterfaceExtensionId(source.getInterfaceExtensionId());
        target.setRenderComponent(source.getRenderComponent());
        target.setFormatter(source.getFormatter());
        target.setColumnConfig(source.getColumnConfig());
        target.setQueryConfig(source.getQueryConfig());
        target.setRenderConfig(source.getRenderConfig());
        target.setTemplateId(source.getTemplateId());
        target.setTemplateVersion(source.getTemplateVersion());
        target.setLocalOverridesDocument(source.getLocalOverridesDocument());
        target.setDeleted(source.getDeleted());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    /** 仅合并展示数据，保留宿主计算的行身份、权限、状态和发布上下文。 */
    public static void apply(ListFieldDataRecord source, EntityDataDTO target) {
        target.setData(source.getData());
        target.setExtData(source.getExtData());
    }
}
