package com.workflow.service;

import com.workflow.dto.EntityListActionSaveRequest;
import com.workflow.entity.EntityListAction;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListField;
import com.workflow.mapper.EntityListActionMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListSceneMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 实体列表增量配置测试。
 *
 * <p>被测对象：{@link EntityListConfigService} 与 {@link EntityListRelationalConfigService}，
 * 覆盖字段补丁显式清空可选绑定、动作创建持久化显式排序等增量配置场景。
 */
class EntityListIncrementalConfigurationTest {

    /**
     * 测试字段补丁可显式清空可选绑定：
     * 验证通过 copyMutableFieldProperties 将源字段为空的属性复制到目标后，相关绑定被清空为 null。
     */
    @Test
    void fieldPatchCanExplicitlyClearOptionalBindings() {
        EntityListConfigService service = new EntityListConfigService(
                null, null, null, null, null,
                null, null, null, null, null);
        EntityListField source = new EntityListField();
        EntityListField target = new EntityListField();
        target.setDataSourceId("source-1");
        target.setTemplateId("template-1");
        target.setTemplateVersion(3);
        target.setLocalOverridesDocument("{}");

        ReflectionTestUtils.invokeMethod(
                service,
                "copyMutableFieldProperties",
                source,
                target,
                Set.of(
                        "dataSourceId",
                        "templateId",
                        "templateVersion",
                        "localOverridesDocument"));

        assertNull(target.getDataSourceId());
        assertNull(target.getTemplateId());
        assertNull(target.getTemplateVersion());
        assertNull(target.getLocalOverridesDocument());
    }

    /**
     * 测试动作创建持久化显式排序值：
     * 验证保存动作时 sortOrder 与 orderKey 按请求显式值落库，不被自动覆盖。
     */
    @Test
    void actionCreatePersistsExplicitSortOrder() {
        EntityListActionMapper actionMapper = mock(EntityListActionMapper.class);
        EntityListSceneMapper sceneMapper = mock(EntityListSceneMapper.class);
        EntityListConfigMapper configMapper = mock(EntityListConfigMapper.class);
        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        when(configMapper.selectById("list-1")).thenReturn(config);
        when(actionMapper.findByListAndPosition("list-1", "TOOLBAR"))
                .thenReturn(List.of());
        when(actionMapper.insert(any(EntityListAction.class))).thenReturn(1);

        EntityListRelationalConfigService service =
                new EntityListRelationalConfigService(
                        actionMapper,
                        sceneMapper,
                        configMapper,
                        null);
        EntityListActionSaveRequest request = new EntityListActionSaveRequest();
        request.setPosition("TOOLBAR");
        request.setButtonKey("custom_review");
        request.setButtonLabel("复核");
        request.setSortOrder(9);
        request.setOrderKey(10_000_000L);

        EntityListAction saved = service.createAction("list-1", request);

        assertEquals(9, saved.getSortOrder());
        assertEquals(10_000_000L, saved.getOrderKey());
    }
}
