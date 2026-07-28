package com.workflow.project.action;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionHandler;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Creates the governed system asset after a system application is approved.
 *
 * <p>This is intentionally the only project-specific backend extension. Entity CRUD, forms,
 * lists, permissions, process routing, approval options, and status synchronization are all
 * supplied by platform configuration.</p>
 */
@Component("createSystemAssetHandler")
public class CreateSystemAssetHandler implements FlowActionHandler {

    private static final String SOURCE_ENTITY = "system_application";
    private static final String TARGET_ENTITY = "system_asset";

    private final EntityDataDynamicService entityDataService;

    public CreateSystemAssetHandler(EntityDataDynamicService entityDataService) {
        this.entityDataService = entityDataService;
    }

    @Override
    public Set<String> supportedTriggerTimings() {
        return Set.of("PROCESS_COMPLETED");
    }

    @Override
    public Set<String> supportedExecutionModes() {
        return Set.of("AFTER_COMMIT");
    }

    @Override
    public String recommendedExecutionMode() {
        return "AFTER_COMMIT";
    }

    @Override
    public void execute(FlowActionContext context) {
        if (!SOURCE_ENTITY.equals(context.getEntityCode())
                || !"approve".equals(String.valueOf(context.getVariable("approved")))) {
            context.addExecutionTrace("SKIPPED", "The process did not finish with approval.");
            return;
        }

        Object entityData = context.getEntityData();
        if (!(entityData instanceof EntityDataDTO application)) {
            throw new IllegalStateException("System application data is unavailable.");
        }
        Map<String, Object> source = application.getData() == null
                ? Map.of()
                : application.getData();
        Object existingAssetId = read(source, "approved_system_id");
        if (existingAssetId != null && !String.valueOf(existingAssetId).isBlank()) {
            context.setExecutionResult(Map.of("systemAssetId", existingAssetId, "reused", true));
            return;
        }

        EntityDataDTO asset = new EntityDataDTO();
        asset.setEntityCode(TARGET_ENTITY);
        asset.setName(text(read(source, "proposed_system_name")));
        asset.setSubmitterId(application.getSubmitterId());
        asset.setSubmitterName(application.getSubmitterName());
        asset.setData(buildAssetData(application, source));
        EntityDataDTO saved = entityDataService.save(asset);

        Map<String, Object> update = new LinkedHashMap<>();
        Map<String, Object> applicationData = new LinkedHashMap<>();
        applicationData.put("approved_system_id", saved.getId());
        applicationData.put("approved_at", LocalDateTime.now());
        update.put("data", applicationData);
        entityDataService.update(SOURCE_ENTITY, application.getId(), update);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemAssetId", saved.getId());
        result.put("systemAssetCode", saved.getCode());
        result.put("reused", false);
        context.setExecutionResult(result);
        context.addExecutionTrace("CREATED", "Created system asset and linked it to the application.", result);
    }

    private Map<String, Object> buildAssetData(
            EntityDataDTO application,
            Map<String, Object> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        copy(source, target, "proposed_abbreviation", "system_abbreviation");
        copy(source, target, "system_type", "system_type");
        copy(source, target, "business_domain", "business_domain");
        copy(source, target, "owner_dept_id", "owner_dept_id");
        copy(source, target, "proposed_system_owner_id", "system_owner_id");
        copy(source, target, "proposed_technical_owner_id", "technical_owner_id");
        copy(source, target, "proposed_ops_owner_id", "ops_owner_id");
        copy(source, target, "criticality_level", "criticality_level");
        copy(source, target, "data_classification", "data_classification");
        copy(source, target, "security_level", "security_level");
        copy(source, target, "deployment_mode", "deployment_mode");
        copy(source, target, "availability_target", "availability_target");
        copy(source, target, "rto_minutes", "rto_minutes");
        copy(source, target, "rpo_minutes", "rpo_minutes");
        copy(source, target, "expected_go_live_date", "planned_go_live_date");
        target.put("source_application_id", application.getId());
        target.put("asset_status", "PROPOSED");
        return target;
    }

    private void copy(
            Map<String, Object> source,
            Map<String, Object> target,
            String sourceKey,
            String targetKey) {
        Object value = read(source, sourceKey);
        if (value != null) {
            target.put(targetKey, value);
        }
    }

    private Object read(Map<String, Object> source, String snakeCaseKey) {
        if (source.containsKey(snakeCaseKey)) {
            return source.get(snakeCaseKey);
        }
        StringBuilder camelCaseKey = new StringBuilder();
        boolean capitalizeNext = false;
        for (char character : snakeCaseKey.toCharArray()) {
            if (character == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                camelCaseKey.append(Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                camelCaseKey.append(character);
            }
        }
        return source.get(camelCaseKey.toString());
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
