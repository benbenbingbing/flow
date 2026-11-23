package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.port.system.dictionary.model.DictionaryEntry;
import com.workflow.port.system.SysDictPort;
import com.workflow.port.entity.model.EntityPublishedMetadata;
import com.workflow.port.entity.EntityDefinitionPort;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.EntityStatusService;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.ui.api.request.UiBoundExtensionExecuteRequest;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.core.error.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntityMetadataAdapter implements EntityDefinitionPort {
    private final EntityActionCapabilityService access;
    private final EntityPublishedSnapshotService snapshots;
    private final EntityStatusService statuses;
    private final SysDictPort dictionaries;
    private final EntityFormMapper forms;
    private final UiInterfaceExtensionService extensions;

    @Override
    public Optional<EntityPublishedMetadata> findPublished(String entityCode) {
        access.requireEntityMetadataAccess(entityCode);
        var snapshot = snapshots.findLatestByEntityCode(entityCode);
        if (snapshot == null) return Optional.empty();
        // 只投影发布时允许读取的字段，不返回草稿、物理表等实现信息。
        var fields = snapshot.getFields().stream()
                .filter(field -> !Boolean.FALSE.equals(field.getRuntimeReadable()))
                .map(field -> new EntityPublishedMetadata.Field(field.getFieldCode(), field.getFieldName(),
                        field.getFieldType().name(), Boolean.TRUE.equals(field.getIsRequired()), field.getDictType(),
                        field.getOptionsInterfaceId(), field.getRefEntityCode()))
                .toList();
        var relations = snapshot.getRelations().stream()
                .filter(relation -> Boolean.TRUE.equals(relation.getEnabled()))
                .map(relation -> new EntityPublishedMetadata.Relation(relation.getRelationCode(),
                        relation.getRelationName(), relation.getChildEntityCode(), relation.getRelationType().name()))
                .toList();
        var currentStatuses = statuses.findByEntityCode(entityCode).stream()
                .map(status -> new EntityPublishedMetadata.Status(status.getStatusCode(), status.getStatusName(),
                        status.getStatusCategory(), status.getColor()))
                .toList();
        return Optional.of(new EntityPublishedMetadata(entityCode, snapshot.getEntityName(), snapshot.getHistoryId(),
                snapshot.getVersion(), snapshot.getProcessDefinitionId(), fields, relations, currentStatuses));
    }

    @Override
    public List<DictionaryEntry> listDictionaryOptions(String entityCode, String fieldCode) {
        return findPublished(entityCode)
                .flatMap(metadata -> metadata.fields().stream().filter(field -> field.code().equals(fieldCode)).findFirst())
                .map(EntityPublishedMetadata.Field::dictionaryCode)
                .map(dictionaries::listActiveTree)
                .orElseGet(List::of);
    }

    @Override
    public Object loadInterfaceOptions(FieldOptionsRequest request) {
        access.requireEntityMetadataAccess(request.entityCode());
        var snapshot = snapshots.getLatestByEntityCode(request.entityCode());
        var form = forms.selectById(request.formId());
        if (form == null || !snapshot.getEntityId().equals(form.getEntityId())) {
            throw new ForbiddenException("选项表单与实体不匹配");
        }
        var interfaceId = snapshot.getFields().stream()
                .filter(field -> request.fieldCode().equals(field.getFieldCode())
                        && !Boolean.FALSE.equals(field.getRuntimeReadable()))
                .map(field -> field.getOptionsInterfaceId())
                .filter(org.springframework.util.StringUtils::hasText)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("发布字段未配置接口选项"));
        // 仅允许发布字段继承的选项接口，发布绑定与 READ 能力由原生运行时再次核验。
        var invocation = new UiBoundExtensionExecuteRequest();
        invocation.setOwnerType("FORM");
        invocation.setOwnerId(request.formId());
        invocation.setEntityCode(request.entityCode());
        invocation.setConfigHash(request.configHash());
        invocation.setRecordId(request.recordId());
        invocation.setBindingCode(UiDataSourceUsages.FIELD_OPTIONS);
        invocation.setTargetType("FIELD");
        invocation.setTargetKey(request.fieldCode());
        invocation.setExtensionId(interfaceId);
        invocation.setInput(request.input());
        return extensions.executeBoundOperation(invocation);
    }
}
