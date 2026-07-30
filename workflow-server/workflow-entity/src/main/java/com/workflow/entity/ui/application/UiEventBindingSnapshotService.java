package com.workflow.entity.ui.application;

import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the published snapshot representation of UI event bindings.
 */
@Service
@RequiredArgsConstructor
public class UiEventBindingSnapshotService {

    private final UiEventBindingMapper bindingMapper;
    private final JsonDocumentCodec codec;

    public List<Map<String, Object>> snapshot(
            String configType,
            String configId,
            String entityId) {
        return bindingMapper.findForSnapshot(
                        configType,
                        configId,
                        entityId)
                .stream()
                .map(this::snapshotValue)
                .toList();
    }

    private Map<String, Object> snapshotValue(UiEventBinding binding) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", binding.getId());
        value.put("ownerType", binding.getOwnerType());
        value.put("ownerId", binding.getOwnerId());
        value.put("targetType", binding.getTargetType());
        value.put("targetKey", binding.getTargetKey());
        value.put("eventCode", binding.getEventCode());
        value.put("inheritanceMode", binding.getInheritanceMode());
        value.put(
                "steps",
                StringUtils.hasText(binding.getStepsDocument())
                        ? codec.readArray(
                                binding.getStepsDocument(),
                                "UI事件绑定步骤")
                        : List.of());
        value.put("revision", binding.getRevision());
        return value;
    }
}
