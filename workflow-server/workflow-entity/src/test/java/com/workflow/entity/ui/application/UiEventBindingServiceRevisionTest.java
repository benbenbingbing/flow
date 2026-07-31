package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.api.request.UiEventBindingSaveRequest;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiEventBindingServiceRevisionTest {

    private static final Pattern REVISION_PARAMETER = Pattern.compile(
            "WHERE.*revision\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}",
            Pattern.CASE_INSENSITIVE);

    @Test
    void updateMatchesPersistedRevisionBeforeIncrementingIt() {
        UiEventBindingMapper mapper =
                mock(UiEventBindingMapper.class);
        UiEventBinding current = binding();
        when(mapper.selectById(current.getId()))
                .thenReturn(current);
        when(mapper.update(isNull(), any()))
                .thenReturn(1);

        UiEventBinding saved =
                service(mapper).save(updateRequest(current));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<UiEventBinding>> captor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        UpdateWrapper<UiEventBinding> update = captor.getValue();
        Matcher matcher = REVISION_PARAMETER.matcher(
                update.getCustomSqlSegment());
        assertTrue(matcher.find());
        assertEquals(
                1,
                update.getParamNameValuePairs().get(matcher.group(1)),
                () -> update.getCustomSqlSegment()
                        + " "
                        + update.getParamNameValuePairs());
        assertEquals(2, saved.getRevision());
        assertEquals("REPLACE", saved.getInheritanceMode());
    }

    private UiEventBindingService service(
            UiEventBindingMapper mapper) {
        return new UiEventBindingService(
                mapper,
                mock(UiConfigReleaseMapper.class),
                mock(EntityDefinitionMapper.class),
                mock(EntityFormMapper.class),
                mock(EntityListConfigMapper.class),
                mock(EntityDefinitionAccessPolicy.class),
                mock(UiConfigurationAccessService.class),
                mock(UiDataSourceService.class),
                mock(UiConfigReleaseService.class),
                new JsonDocumentCodec(new ObjectMapper()),
                new ObjectMapper());
    }

    private UiEventBinding binding() {
        UiEventBinding binding = new UiEventBinding();
        binding.setId("binding-1");
        binding.setOwnerType("ENTITY");
        binding.setOwnerId("entity-1");
        binding.setTargetType("OWNER");
        binding.setTargetKey("");
        binding.setEventCode("DETAIL_LOAD");
        binding.setInheritanceMode("INHERIT");
        binding.setRevision(1);
        binding.setEnabled(true);
        binding.setDeleted(0);
        return binding;
    }

    private UiEventBindingSaveRequest updateRequest(
            UiEventBinding current) {
        UiEventBindingSaveRequest request =
                new UiEventBindingSaveRequest();
        request.setId(current.getId());
        request.setExpectedRevision(current.getRevision());
        request.setOwnerType(current.getOwnerType());
        request.setOwnerId(current.getOwnerId());
        request.setTargetType(current.getTargetType());
        request.setTargetKey(current.getTargetKey());
        request.setEventCode(current.getEventCode());
        request.setInheritanceMode("REPLACE");
        request.setSteps(List.of());
        request.setEnabled(true);
        return request;
    }
}
