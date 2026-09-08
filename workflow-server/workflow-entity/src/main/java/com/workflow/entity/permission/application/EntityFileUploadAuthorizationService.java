package com.workflow.entity.permission.application;

import com.workflow.contracts.entity.port.EntityFileUploadAuthorizationPort;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.Set;

/**
 * 将实体表单中的文件上传绑定到精确的实体动作和文件字段。
 */
@Service
@RequiredArgsConstructor
public class EntityFileUploadAuthorizationService
        implements EntityFileUploadAuthorizationPort {

    private static final Set<EntityPermissionAction> UPLOAD_ACTIONS =
            EnumSet.of(
                    EntityPermissionAction.CREATE,
                    EntityPermissionAction.UPDATE,
                    EntityPermissionAction.APPROVE);
    private static final Set<EntityField.FieldType> FILE_FIELD_TYPES =
            EnumSet.of(
                    EntityField.FieldType.FILE,
                    EntityField.FieldType.IMAGE);

    private final EntityActionCapabilityService capabilityService;
    private final EntityDefinitionMapper entityDefinitionMapper;
    private final EntityFieldMapper entityFieldMapper;

    /**
     * 校验实体动作权限，并确认字段属于该实体且为文件类字段。
     *
     * <p>权限检查先于实体元数据查询，避免无权用户利用上传接口探测实体字段。</p>
     *
     * @param entityCode 实体编码
     * @param action     create、update 或 approve
     * @param fieldCode  文件字段编码
     * @throws ForbiddenException 上下文不合法、权限不足或字段不是文件字段时抛出
     */
    @Override
    public void requireUpload(
            String entityCode,
            String action,
            String fieldCode) {
        String normalizedEntityCode = normalizeEntityCode(entityCode);
        String requestedEntityCode = entityCode.trim();
        EntityPermissionAction permissionAction =
                EntityPermissionAction.fromCode(action);
        if (!UPLOAD_ACTIONS.contains(permissionAction)
                || !StringUtils.hasText(fieldCode)) {
            throw invalidContext();
        }

        capabilityService.requireStandardPermission(
                normalizedEntityCode,
                permissionAction);

        // 权限码统一小写，但实体定义保留建模时的原始大小写（例如 ZDWREQ）。
        EntityDefinition entity = entityDefinitionMapper
                .findByEntityCode(requestedEntityCode)
                .orElseThrow(EntityFileUploadAuthorizationService::invalidContext);
        if (entity.getStatus() != EntityDefinition.Status.PUBLISHED) {
            throw invalidContext();
        }
        EntityField field = entityFieldMapper.findByEntityIdAndFieldCode(
                entity.getId(),
                fieldCode.trim());
        if (field == null
                || !Boolean.TRUE.equals(field.getIsPublished())
                || !FILE_FIELD_TYPES.contains(field.getFieldType())) {
            throw invalidContext();
        }
    }

    private String normalizeEntityCode(String entityCode) {
        try {
            return EntityPermissionAction.normalizeEntityCode(entityCode);
        } catch (IllegalArgumentException exception) {
            throw invalidContext();
        }
    }

    private static ForbiddenException invalidContext() {
        return new ForbiddenException("实体文件上传上下文无效");
    }
}
