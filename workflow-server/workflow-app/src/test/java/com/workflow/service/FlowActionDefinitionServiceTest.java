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

/**
 * 流程动作定义服务测试。
 *
 * <p>被测对象：{@link FlowActionDefinitionService}，覆盖动作定义的可见性筛选与可选性校验，
 * 验证全局动作与绑定当前实体的动作可见、跨实体绑定动作被拒绝。
 */
class FlowActionDefinitionServiceTest {

    private FlowActionDefinitionMapper definitionMapper;
    private ApplicationContext applicationContext;
    private EntityCodeCatalogPort entityCodeCatalogPort;
    /** 被测服务 */
    private FlowActionDefinitionService service;
    /** 测试用空动作处理器 */
    private FlowActionHandler handler;

    /** 装配被测服务与各 Mock 依赖 */
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

    /**
     * 测试仅返回全局与当前实体绑定的动作定义：
     * 验证按流程实例筛选后，仅 GLOBAL 与绑定 order 实体的动作可见，customer 动作被排除。
     */
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

    /**
     * 测试拒绝选择绑定在流程未绑定实体上的动作：
     * 验证当前流程绑定 order 时选择 customer 绑定动作会抛出 RuntimeException。
     */
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

    /** 构造一个启用、未删除的动作定义，含可见范围与实体编码 JSON */
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
