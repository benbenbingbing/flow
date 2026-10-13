package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.excel.config.EntityExcelConfigurationService;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.application.EntityDataListConfigService;
import com.workflow.entity.list.application.EntityListPublishedRuntimeService;
import com.workflow.entity.list.extension.ListFieldDataProviderRegistry;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityListActionConfigService;
import com.workflow.entity.ui.application.UiDataSourceExecutionAccessService;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import com.workflow.entity.ui.application.UiInvocationContextFactory;
import com.workflow.entity.ui.application.UiPublishedDataSourceReferenceGuard;
import com.workflow.entity.ui.application.validation.UiExtensionDefinitionValidator;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 用真实 Spring 构造注入复现列表→接口运行器→Excel Provider→解析器→列表的启动链。
 * 仅隔离数据库等外围服务；环上的四个生产 Bean 均由容器创建，禁止循环引用。
 */
class EntityExcelStartupContextTest {
    @ParameterizedTest(name = "listsFirst={0}")
    @ValueSource(booleans = {true, false})
    void excelProviderAndListRuntimeStartWithoutCircularReferences(boolean listsFirst) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setAllowCircularReferences(false);
            registerDependencies(context);
            // 两种入口分别覆盖控制器先拉起列表服务、Excel 入口先拉起解析器的初始化顺序。
            context.register(listsFirst ? EntityDataListConfigService.class : EntityExcelRuntimeResolver.class);
            context.register(EntityDataListConfigService.class, UiInterfaceExtensionService.class,
                    EntityExcelActionProvider.class, EntityExcelRuntimeResolver.class);
            context.refresh();

            assertThat(context.getBean(EntityDataListConfigService.class)).isNotNull();
            var catalog = context.getBean(UiInterfaceExtensionService.class).catalog();
            assertThat((List<Map<String, Object>>) catalog.get("providers"))
                    .anySatisfy(provider -> assertThat(provider.get("code")).isEqualTo("entity_excel_action"));
            assertThat(context.getBean(EntityExcelRuntimeResolver.class)).isNotNull();
            assertThat(context.getBean(EntityExcelActionProvider.class).getCode()).isEqualTo("entity_excel_action");
        }
    }

    private static void registerDependencies(AnnotationConfigApplicationContext context) {
        // 外围 mock 直接登记为已创建单例，避免其被 Spring 当作真实服务继续注入无关依赖。
        for (Class<?> type : new Class<?>[] {
                EntityDataDynamicService.class, EntityListConfigMapper.class, EntityListFieldMapper.class,
                EntityDefinitionMapper.class, ListFieldDataProviderRegistry.class, EntityActionCapabilityService.class,
                EntityListPublishedRuntimeService.class, UiExtensionDefinitionMapper.class, EntityFormMapper.class,
                EntityDefinitionAccessPolicy.class, EntityUiConfigurationPolicy.class, SysDictItemService.class,
                UiDataSourceExecutionAccessService.class, UiInvocationContextFactory.class,
                UiExtensionDefinitionValidator.class, UiPublishedDataSourceReferenceGuard.class,
                EntityExcelConfigurationService.class, EntityListActionConfigService.class, EntityPublishedSnapshotService.class
        }) context.getBeanFactory().registerSingleton(type.getSimpleName(), mock(type));
        ObjectMapper mapper = new ObjectMapper();
        context.getBeanFactory().registerSingleton("objectMapper", mapper);
        context.getBeanFactory().registerSingleton("jsonDocumentCodec", new JsonDocumentCodec(mapper));
        context.getBeanFactory().registerSingleton("excelActionTokenService", new ExcelActionTokenService(mapper, "startup-test-secret"));
        context.getBeanFactory().registerSingleton("uiExtensionTaskExecutor", new SyncTaskExecutor());
    }
}
