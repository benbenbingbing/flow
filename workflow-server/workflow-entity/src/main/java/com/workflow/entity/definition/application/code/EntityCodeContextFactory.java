package com.workflow.entity.definition.application.code;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.code.EntityCodeGenerationContext;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 在平台边界将存储列转换为逻辑字段，并从可信变更作用域构造扩展上下文。 */
@Component
@RequiredArgsConstructor
public class EntityCodeContextFactory {
    private final EntityPublishedSnapshotService snapshots;
    private final EntityRuntimeRecordMapper records;
    private final EntityDataDynamicMapper mapper;
    private final DynamicTableService tables;
    private final SysUserService users;
    private final com.workflow.entity.data.application.EntityDataMutationValidator validator;
    private final com.workflow.entity.definition.application.EntityFieldValidationRuleService fieldRules;

    /** 当前行尚未 INSERT；父记录在本事务中读取，避免扩展或远程服务回查未提交的新记录。 */
    public EntityCodeGenerationContext create(EntityCodeGenerationInput input) {
        if (input.parentEntityCode() != null) validator.validateGenerationInput(input.entityCode(), input.storageData());
        var mutation = EntityCodeGenerationScope.current();
        String actor = mutation != null && mutation.operatorId() != null ? mutation.operatorId() : UserContext.getUserId();
        var user = actor == null ? null : users.getById(actor);
        Map<String, Object> parent = Map.of();
        if (input.parentEntityCode() != null && input.parentRecordId() != null) {
            Map<String, Object> stored = mapper.selectById(tables.getTableName(input.parentEntityCode()), input.parentRecordId());
            if (stored != null) {
                parent = logical(input.parentEntityCode(), stored);
                parent.put("id", input.parentRecordId());
                parent.put("code", stored.get("code"));
            }
        }
        // 同一个根请求的子行按稳定提交路径区分；上层未提供稳定键时，不伪造跨请求幂等承诺。
        String key = mutation == null ? null : mutation.idempotencyKey() + ":code:"
                + input.entityCode() + ":" + input.submissionPath();
        return new EntityCodeGenerationContext(input.entityCode(), input.recordId(), logical(input.entityCode(), input.storageData()),
                actor, user == null ? null : user.getDeptId(), input.parentEntityCode(), input.parentRecordId(), parent,
                LocalDateTime.now(), key);
    }

    private Map<String, Object> logical(String entityCode, Map<String, Object> stored) {
        var snapshot = snapshots.getLatestByEntityCode(entityCode);
        Map<String, Object> data = new LinkedHashMap<>(records.toDto(stored, entityCode, snapshot.getFields()).getData());
        data.put("name", stored.get("name"));
        return data;
    }

    /** 对服务器生成的最终编号应用实体字段规则，不能拿客户端提供的 code 替代校验。 */
    public void validateGeneratedCode(String entityCode, String code) {
        var snapshot = snapshots.getLatestByEntityCode(entityCode);
        if (snapshot.getFields() == null) return;
        snapshot.getFields().stream().filter(field -> "code".equals(field.getFieldCode()))
                .forEach(field -> fieldRules.validateValue(field, code));
    }
}
