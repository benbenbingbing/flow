package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.EntityCodeCatalogPort;
import com.workflow.dto.FlowActionHandlerOptionDTO;
import com.workflow.entity.FlowActionDefinition;
import com.workflow.mapper.FlowActionDefinitionMapper;
import com.workflow.mapper.FlowActionDefinitionEntityMapper;
import com.workflow.process.action.FlowActionContext;
import com.workflow.process.action.FlowActionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowActionDefinitionServiceTest {

    private FlowActionDefinitionMapper definitionMapper;
    private ApplicationContext applicationContext;
    private EntityCodeCatalogPort entityCodeCatalogPort;
    private FlowActionDefinitionService service;
    private FlowActionHandler handler;

    @BeforeEach
    void setUp() {
        definitionMapper = mock(FlowActionDefinitionMapper.class);
        applicationContext = mock(ApplicationContext.class);
        entityCodeCatalogPort = mock(EntityCodeCatalogPort.class);
        handler = context -> {};
        service = new FlowActionDefinitionService(
                definitionMapper,
                mock(FlowActionDefinitionEntityMapper.class),
                applicationContext,
                new ObjectMapper(),
                entityCodeCatalogPort,
                mock(CurrentUserRoleService.class));
    }

    @Test
    void shouldReturnOnlyGlobalAndCurrentEntityDefinitions() {
        FlowActionDefinition global = definition("global", "globalHandler", "GLOBAL", null);
        FlowActionDefinition order = definition("order", "orderHandler", "ENTITY", "[\"order\"]");
        FlowActionDefinition customer = definition("customer", "customerHandler", "ENTITY", "[\"customer\"]");
        when(definitionMapper.findAllActive()).thenReturn(List.of(global, order, customer));
        when(applicationContext.getBeansOfType(FlowActionHandler.class)).thenReturn(Map.of(
                "globalHandler", handler,
                "orderHandler", handler,
                "customerHandler", handler));
        when(entityCodeCatalogPort.findEntityCodeByProcessDefinitionId("process-1")).thenReturn("order");

        List<FlowActionHandlerOptionDTO> result = service.listVisible("process-1");

        assertEquals(
                Set.of("globalHandler", "orderHandler"),
                result.stream().map(FlowActionHandlerOptionDTO::getBeanName).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void shouldRejectDefinitionOutsideBoundEntity() {
        FlowActionDefinition customer = definition("customer", "customerHandler", "ENTITY", "[\"customer\"]");
        when(definitionMapper.findActiveById("customer")).thenReturn(Optional.of(customer));
        when(applicationContext.containsBean("customerHandler")).thenReturn(true);
        when(applicationContext.getBean("customerHandler", FlowActionHandler.class)).thenReturn(handler);
        when(entityCodeCatalogPort.findEntityCodeByProcessDefinitionId("process-1")).thenReturn("order");

        assertThrows(
                RuntimeException.class,
                () -> service.requireSelectable("process-1", "customer", "customerHandler"));
    }

    private FlowActionDefinition definition(
            String id,
            String handlerName,
            String visibilityScope,
            String entityCodesJson) {
        FlowActionDefinition definition = new FlowActionDefinition();
        definition.setId(id);
        definition.setActionCode(handlerName);
        definition.setDisplayName(handlerName);
        definition.setHandlerName(handlerName);
        definition.setVisibilityScope(visibilityScope);
        definition.setEntityCodesJson(entityCodesJson);
        definition.setEnabled(true);
        definition.setDeleted(0);
        return definition;
    }
}
