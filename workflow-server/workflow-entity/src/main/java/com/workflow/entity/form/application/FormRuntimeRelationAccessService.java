package com.workflow.entity.form.application;

import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 校验子表单现有行属于当前父记录；引用路径本身不能授权任意子记录。 */
@Service
@RequiredArgsConstructor
public class FormRuntimeRelationAccessService {
    private final ObjectProvider<UiConfigReleaseService> releases;
    private final ObjectProvider<EntityDataDynamicService> data;
    private final EntityDefinitionMapper definitions;
    private final JsonDocumentCodec codec;

    /**
     * 根记录已通过任务/实例或普通实体权限校验。每跳使用发布配置的外键读取实际子记录，
     * 新建行没有 ID，不读取其他记录；深层现有行必须提供每一跳行 ID。
     */
    public void requireRecords(String rootFormId, String rootRecordId, Object rawPath, String targetRecordId,
            boolean processAuthorized) {
        if (!(rawPath instanceof List<?> path) || path.isEmpty() || !StringUtils.hasText(targetRecordId)) return;
        if (!StringUtils.hasText(rootRecordId)) throw denied();
        String formId = rootFormId;
        String parentId = rootRecordId;
        var rootForm = releases.getObject().resolveRuntimeFormRelease(formId).form();
        var rootEntity = definitions.selectById(rootForm.getEntityId());
        if (rootEntity == null) throw denied();
        if (!processAuthorized) data.getObject().findAccessibleById(rootEntity.getEntityCode(), parentId, null);
        for (int index = 0; index < path.size(); index++) {
            Object entry = path.get(index);
            String key = entry instanceof Map<?, ?> map ? text(map.get("nodeKey")) : text(entry);
            String childId = entry instanceof Map<?, ?> map ? text(map.get("recordId")) : null;
            if (index == path.size() - 1) {
                if (StringUtils.hasText(childId) && !Objects.equals(childId, targetRecordId)) throw denied();
                childId = targetRecordId;
            }
            if (!StringUtils.hasText(childId)) throw denied();
            var form = releases.getObject().resolveRuntimeFormRelease(formId).form();
            EntityFormNode node = form.getNodes().stream()
                    .filter(item -> key.equals(item.getNodeKey()) || key.equals(item.getBindingRef()))
                    .findFirst().orElseThrow(FormRuntimeRelationAccessService::denied);
            var relation = SubFormParameterContractPolicy.relationConfig(node, codec);
            if (!StringUtils.hasText(relation.childFormId()) || !StringUtils.hasText(relation.childRefFieldCode())) throw denied();
            var childForm = releases.getObject().resolveRuntimeFormRelease(relation.childFormId()).form();
            var childEntity = definitions.selectById(childForm.getEntityId());
            if (childEntity == null) throw denied();
            var row = processAuthorized ? data.getObject().findById(childEntity.getEntityCode(), childId)
                    : data.getObject().findAccessibleById(childEntity.getEntityCode(), childId, null);
            Object parent = row == null || row.getData() == null ? null : row.getData().get(relation.childRefFieldCode());
            if (parent instanceof Map<?, ?> map) parent = map.get("id");
            if (!Objects.equals(parentId, text(parent))) throw denied();
            parentId = childId;
            formId = relation.childFormId();
        }
    }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
    private static BusinessForbiddenException denied() {
        return new BusinessForbiddenException("CHILD_FORM_RECORD_MISMATCH", "子表单记录不属于当前父记录");
    }
}
