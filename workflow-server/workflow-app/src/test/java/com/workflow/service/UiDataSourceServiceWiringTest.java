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

class UiDataSourceServiceWiringTest {

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
