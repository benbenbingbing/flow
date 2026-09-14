package com.workflow.service;

import com.workflow.entity.ui.application.UiExtensionDefinitionService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.ui.api.request.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UI 扩展定义服务测试。
 *
 * <p>被测对象：{@link UiExtensionDefinitionService}，覆盖扩展激活需显式注册版本、
 * 节点类型兼容性、快照版本与注册协议一致性、运行态模式校验、缺失激活清单拒绝等场景。
 */
class UiExtensionDefinitionServiceTest {

    /** 测试激活扩展需显式注册版本：验证版本为 null 时抛出 IllegalArgumentException */
    @Test
    void requiresExplicitRegisteredVersion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service(mock(UiExtensionDefinitionMapper.class))
                        .requireActive("NODE", "risk-matrix", null));
    }

    /** 测试拒绝不支持的节点类型：验证 SECTION 节点对仅支持 FIELD 的扩展抛出异常 */
    @Test
    void rejectsUnsupportedNodeType() {
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setExtensionKey("risk-matrix");
        definition.setSnapshotVersion(2);
        definition.setSupportedNodeTypesDocument("[\"FIELD\"]");

        assertThrows(
                IllegalArgumentException.class,
                () -> service(mock(UiExtensionDefinitionMapper.class))
                        .validateCompatibility(
                                definition,
                                "edit",
                                "SECTION",
                                "NONE",
                                1));
    }

    /** 测试拒绝快照版本新于已注册协议版本：验证快照版本小于注册协议版本时抛出异常 */
    @Test
    void rejectsSnapshotNewerThanRegisteredProtocol() {
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setExtensionKey("risk-matrix");
        definition.setSnapshotVersion(2);

        assertThrows(
                IllegalArgumentException.class,
                () -> service(mock(UiExtensionDefinitionMapper.class))
                        .validateCompatibility(
                                definition,
                                null,
                                "FIELD",
                                "ENTITY_FIELD",
                                3));
    }

    /** 测试注册时拒绝非法运行态模式：验证 supportedModes 含非法值时抛出异常 */
    @Test
    void rejectsInvalidRuntimeModeDuringRegistration() {
        UiExtensionDefinitionSaveRequest request =
                new UiExtensionDefinitionSaveRequest();
        request.setExtensionType("FORM");
        request.setExtensionKey("project-form");
        request.setDisplayName("项目表单");
        request.setVersion(1);
        request.setSupportedModes(List.of("create", "execute-shell"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service(mock(UiExtensionDefinitionMapper.class))
                        .save(request));
    }

    /** 接口扩展写入必须经过专用服务，通用组件目录不得接管或反向委派。 */
    @Test
    void rejectsInterfaceWriteOutsideDedicatedService() {
        UiExtensionDefinitionSaveRequest request =
                new UiExtensionDefinitionSaveRequest();
        request.setExtensionType("INTERFACE");
        request.setExtensionKey("project.query");
        request.setDisplayName("项目查询");
        request.setVersion(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> service(mock(UiExtensionDefinitionMapper.class))
                        .save(request));
    }

    /** 测试缺失激活清单时拒绝：验证查不到激活清单时抛出 IllegalArgumentException */
    @Test
    void rejectsMissingActiveManifest() {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service(mapper).requireActive(
                        "FORM", "project-form", 1));
    }

    /** 指定实体范围必须至少配置一个实体。 */
    @Test
    void rejectsEmptyEntityScope() {
        UiExtensionDefinitionSaveRequest request =
                validFormRequest();
        request.setVisibilityScope("ENTITY");
        request.setEntityCodes(List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> service(mock(UiExtensionDefinitionMapper.class))
                        .save(request));
    }

    /** 发布校验会拒绝把组件用于范围之外的实体。 */
    @Test
    void rejectsEntityOutsideConfiguredScope() {
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setExtensionKey("project-form");
        definition.setVisibilityScope("ENTITY");
        definition.setEntityCodesDocument("[\"project\"]");

        assertThrows(
                IllegalArgumentException.class,
                () -> service(mock(UiExtensionDefinitionMapper.class))
                        .validateEntityScope(definition, "requirement"));
    }

    /** 更新不能把既有 UI 组件转换为接口扩展。 */
    @Test
    void rejectsChangingUiExtensionToInterface() {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        UiExtensionDefinition current = persisted("FORM", "project-form");
        when(mapper.selectById(current.getId())).thenReturn(current);
        UiExtensionDefinitionSaveRequest request = updateRequest(
                current, "INTERFACE", current.getExtensionKey());

        assertThrows(IllegalArgumentException.class,
                () -> service(mapper).save(request));

        verify(mapper, never()).insert(any(UiExtensionDefinition.class));
        verify(mapper, never()).update(isNull(), any());
    }

    /** 更新不能把既有接口扩展伪装成 UI 组件。 */
    @Test
    void rejectsChangingInterfaceExtensionToUi() {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        UiExtensionDefinition current = persisted(
                "INTERFACE", "project.query");
        when(mapper.selectById(current.getId())).thenReturn(current);

        assertThrows(IllegalArgumentException.class,
                () -> service(mapper).save(updateRequest(
                        current, "LIST", current.getExtensionKey())));

        verify(mapper, never()).insert(any(UiExtensionDefinition.class));
        verify(mapper, never()).update(isNull(), any());
    }

    /** 稳定 extensionKey 不能通过普通更新重命名。 */
    @Test
    void rejectsChangingStableExtensionKey() {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        UiExtensionDefinition current = persisted("FORM", "project-form");
        when(mapper.selectById(current.getId())).thenReturn(current);

        assertThrows(IllegalArgumentException.class,
                () -> service(mapper).save(updateRequest(
                        current, "FORM", "renamed-form")));

        verify(mapper, never()).update(isNull(), any());
    }

    /** 带路径 ID 的更新找不到目标时必须失败，不能降级为新增。 */
    @Test
    void rejectsUnknownUpdateIdInsteadOfCreating() {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        UiExtensionDefinition current = persisted("FORM", "project-form");
        UiExtensionDefinitionSaveRequest request = updateRequest(
                current, "FORM", current.getExtensionKey());
        when(mapper.selectById(current.getId())).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service(mapper).save(request));

        verify(mapper, never()).insert(any(UiExtensionDefinition.class));
        verify(mapper, never()).update(isNull(), any());
    }

    /** 身份不变的 UI 扩展仍可正常更新。 */
    @Test
    void acceptsUpdateWithStableIdentity() {
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        UiExtensionDefinition current = persisted("FORM", "project-form");
        when(mapper.selectById(current.getId())).thenReturn(current);
        when(mapper.update(isNull(), any())).thenReturn(1);
        UiExtensionDefinitionSaveRequest request = updateRequest(
                current, "FORM", current.getExtensionKey());
        request.setDisplayName("项目表单（已编辑）");

        UiExtensionDefinition saved = service(mapper).save(request);

        assertEquals("项目表单（已编辑）", saved.getDisplayName());
        verify(mapper).update(isNull(), any());
    }

    private UiExtensionDefinitionSaveRequest validFormRequest() {
        UiExtensionDefinitionSaveRequest request =
                new UiExtensionDefinitionSaveRequest();
        request.setExtensionType("FORM");
        request.setExtensionKey("project-form");
        request.setDisplayName("项目表单");
        request.setVersion(1);
        return request;
    }

    private UiExtensionDefinition persisted(String type, String key) {
        UiExtensionDefinition definition = new UiExtensionDefinition();
        definition.setId("extension-1");
        definition.setExtensionType(type);
        definition.setExtensionKey(key);
        definition.setDisplayName("项目扩展");
        definition.setVersion(1);
        definition.setSnapshotVersion(1);
        definition.setRevision(1);
        definition.setDeleted(0);
        definition.setStatus("ACTIVE");
        return definition;
    }

    private UiExtensionDefinitionSaveRequest updateRequest(
            UiExtensionDefinition current,
            String type,
            String key) {
        UiExtensionDefinitionSaveRequest request = validFormRequest();
        request.setId(current.getId());
        request.setExpectedRevision(current.getRevision());
        request.setExtensionType(type);
        request.setExtensionKey(key);
        return request;
    }

    /** 装配带 Mock Mapper 的被测服务 */
    private UiExtensionDefinitionService service(
            UiExtensionDefinitionMapper mapper) {
        return new UiExtensionDefinitionService(
                mapper,
                mock(EntityDefinitionMapper.class),
                new JsonDocumentCodec(new ObjectMapper()));
    }
}
