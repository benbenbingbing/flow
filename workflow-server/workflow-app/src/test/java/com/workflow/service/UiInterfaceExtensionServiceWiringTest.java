package com.workflow.service;

import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.ui.application.UiExtensionDefinitionValidator;
import com.workflow.entity.ui.application.UiDataSourceExecutionAccessService;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import com.workflow.entity.ui.application.UiInvocationContextFactory;
import com.workflow.entity.ui.application.UiPublishedDataSourceReferenceGuard;

import com.workflow.admin.dictionary.application.SysDictItemService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * UI 数据源服务装配测试。
 *
 * <p>被测对象：{@link UiInterfaceExtensionService} 的 Spring Bean 装配，验证当容器存在多个 TaskExecutor 时
 * 能正确选择 applicationTaskExecutor 完成服务初始化。
 */
class UiInterfaceExtensionServiceWiringTest {

    /** 测试存在多个 TaskExecutor 时选用 applicationTaskExecutor 完成装配：验证 Bean 可正常获取 */
    @Test
    void selectsApplicationTaskExecutorWhenMultipleExecutorsExist() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(UiExtensionDefinitionMapper.class,
                    () -> mock(UiExtensionDefinitionMapper.class));
            context.registerBean(EntityFormMapper.class,
                    () -> mock(EntityFormMapper.class));
            context.registerBean(EntityListConfigMapper.class,
                    () -> mock(EntityListConfigMapper.class));
            context.registerBean(EntityDefinitionAccessPolicy.class,
                    () -> mock(EntityDefinitionAccessPolicy.class));
            context.registerBean(EntityUiConfigurationPolicy.class,
                    () -> mock(EntityUiConfigurationPolicy.class));
            context.registerBean(EntityDataDynamicService.class,
                    () -> mock(EntityDataDynamicService.class));
            context.registerBean(SysDictItemService.class,
                    () -> mock(SysDictItemService.class));
            context.registerBean(UiDataSourceExecutionAccessService.class,
                    () -> mock(UiDataSourceExecutionAccessService.class));
            context.registerBean(UiInvocationContextFactory.class,
                    () -> mock(UiInvocationContextFactory.class));
            context.registerBean(UiPublishedDataSourceReferenceGuard.class,
                    () -> mock(UiPublishedDataSourceReferenceGuard.class));
            context.registerBean(JsonDocumentCodec.class,
                    () -> new JsonDocumentCodec(new ObjectMapper()));
            context.registerBean(UiExtensionDefinitionValidator.class);
            context.registerBean(
                    "applicationTaskExecutor",
                    TaskExecutor.class,
                    SyncTaskExecutor::new);
            context.registerBean(
                    "taskScheduler",
                    TaskExecutor.class,
                    SyncTaskExecutor::new);
            context.registerBean(UiInterfaceExtensionService.class);

            context.refresh();

            assertNotNull(context.getBean(UiInterfaceExtensionService.class));
        }
    }
}
