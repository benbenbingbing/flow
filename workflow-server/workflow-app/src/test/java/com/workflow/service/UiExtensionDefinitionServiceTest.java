package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.UiExtensionDefinition;
import com.workflow.mapper.UiExtensionDefinitionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

    /** 装配带 Mock Mapper 的被测服务 */
    private UiExtensionDefinitionService service(
            UiExtensionDefinitionMapper mapper) {
        return new UiExtensionDefinitionService(
                mapper,
                new JsonDocumentCodec(new ObjectMapper()));
    }
}
