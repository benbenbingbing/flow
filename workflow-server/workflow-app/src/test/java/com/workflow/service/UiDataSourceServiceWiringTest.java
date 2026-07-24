package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.UiDataSourceDefinitionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * UI 数据源服务装配测试。
 *
 * <p>被测对象：{@link UiDataSourceService} 的 Spring Bean 装配，验证当容器存在多个 TaskExecutor 时
 * 能正确选择 applicationTaskExecutor 完成服务初始化。
 */
class UiDataSourceServiceWiringTest {

    /** 测试存在多个 TaskExecutor 时选用 applicationTaskExecutor 完成装配：验证 Bean 可正常获取 */
    @Test
    void selectsApplicationTaskExecutorWhenMultipleExecutorsExist() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(UiDataSourceDefinitionMapper.class,
                    () -> mock(UiDataSourceDefinitionMapper.class));
            context.registerBean(EntityFormMapper.class,
                    () -> mock(EntityFormMapper.class));
            context.registerBean(EntityListConfigMapper.class,
                    () -> mock(EntityListConfigMapper.class));
            context.registerBean(EntityDefinitionAccessPolicy.class,
                    () -> mock(EntityDefinitionAccessPolicy.class));
            context.registerBean(EntityDataDynamicService.class,
                    () -> mock(EntityDataDynamicService.class));
            context.registerBean(SysDictItemService.class,
                    () -> mock(SysDictItemService.class));
            context.registerBean(UiDataSourceExecutionAccessService.class,
                    () -> mock(UiDataSourceExecutionAccessService.class));
            context.registerBean(JsonDocumentCodec.class,
                    () -> new JsonDocumentCodec(new ObjectMapper()));
            context.registerBean(
                    "applicationTaskExecutor",
                    TaskExecutor.class,
                    SyncTaskExecutor::new);
            context.registerBean(
                    "taskScheduler",
                    TaskExecutor.class,
                    SyncTaskExecutor::new);
            context.registerBean(UiDataSourceService.class);

            context.refresh();

            assertNotNull(context.getBean(UiDataSourceService.class));
        }
    }
}
