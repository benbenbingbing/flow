package com.workflow.entity.ui.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.request.UiEventBindingSaveRequest;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 用真实 Mapper 和唯一约束验证按钮事件可以反复删建，并保留删除时的权限及并发保护。
 */
class UiEventBindingDeleteDatabaseTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private UiEventBindingMapper mapper;
    private UiConfigurationAccessService access;
    private UiEventBindingService service;

    @BeforeEach
    void setUp() throws Exception {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
        jdbc = new JdbcTemplate(database);
        // 保持生产唯一键的 deleted 维度，普通 mock 无法发现第二轮删除撞旧墓碑的问题。
        jdbc.execute("""
                CREATE TABLE ui_event_binding (
                    id VARCHAR(64) PRIMARY KEY,
                    owner_type VARCHAR(20) NOT NULL,
                    owner_id VARCHAR(64) NOT NULL,
                    target_type VARCHAR(20) NOT NULL,
                    target_key VARCHAR(100) NOT NULL,
                    event_code VARCHAR(50) NOT NULL,
                    inheritance_mode VARCHAR(20),
                    steps_document CLOB,
                    revision INT NOT NULL,
                    enabled BOOLEAN NOT NULL,
                    deleted INT NOT NULL,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    CONSTRAINT uk_ui_event_binding_scope UNIQUE
                        (owner_type, owner_id, target_type, target_key, event_code, deleted)
                )
                """);
        var configuration = new MybatisConfiguration();
        configuration.addMapper(UiEventBindingMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(database);
        factory.setConfiguration(configuration);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(UiEventBindingMapper.class);
        access = mock(UiConfigurationAccessService.class);
        service = service(mapper);
    }

    @AfterEach
    void tearDown() {
        if (database != null) database.shutdown();
    }

    @Test
    void sameButtonCanBeCreatedAndDeletedRepeatedlyWithoutOldTombstoneCollision() {
        for (int round = 0; round < 3; round++) {
            UiEventBinding saved = service.save(request());
            service.delete(saved.getId(), saved.getRevision());

            assertNull(mapper.selectById(saved.getId()));
            assertEquals(0, countAll());
        }
        UiEventBinding recreated = service.save(request());
        assertEquals(1, countAll());
        assertEquals("excel_download", recreated.getTargetKey());
    }

    @Test
    void oldLogicalDeleteAndOtherButtonsSurviveRepeatedRecreation() {
        UiEventBinding legacy = service.save(request());
        jdbc.update("UPDATE ui_event_binding SET deleted = 1 WHERE id = ?", legacy.getId());
        UiEventBindingSaveRequest otherRequest = request();
        otherRequest.setTargetKey("other_button");
        UiEventBinding other = service.save(otherRequest);

        for (int round = 0; round < 3; round++) {
            UiEventBinding saved = service.save(request());
            service.delete(saved.getId(), saved.getRevision());
        }

        assertEquals(2, countAll());
        assertEquals(1, jdbc.queryForObject(
                "SELECT deleted FROM ui_event_binding WHERE id = ?", Integer.class, legacy.getId()));
        assertEquals(other.getId(), mapper.selectById(other.getId()).getId());
        // 显式 Mapper 删除也只允许活动草稿，旧墓碑不应被顺带清理。
        assertEquals(0, mapper.deleteDraftByIdAndRevision(legacy.getId(), legacy.getRevision()));
    }

    @Test
    void staleRevisionCannotDeleteCurrentDraft() {
        UiEventBinding saved = service.save(request());

        assertThrows(RevisionConflictException.class,
                () -> service.delete(saved.getId(), saved.getRevision() + 1));

        assertEquals(1, countAll());
        assertEquals(saved.getRevision(), mapper.selectById(saved.getId()).getRevision());
    }

    @Test
    void revisionChangeBetweenReadAndDeleteFailsCompareAndSet() {
        UiEventBinding saved = service.save(request());
        UiEventBindingMapper racingMapper = mock(UiEventBindingMapper.class, delegatesTo(mapper));
        // 在读取校验完成之后、真实 DELETE 之前提交另一编辑，验证 SQL 而非仅 Java 检查。
        doAnswer(invocation -> {
            jdbc.update("UPDATE ui_event_binding SET revision = revision + 1 WHERE id = ?", saved.getId());
            return mapper.deleteDraftByIdAndRevision(saved.getId(), saved.getRevision());
        }).when(racingMapper).deleteDraftByIdAndRevision(saved.getId(), saved.getRevision());

        assertThrows(RevisionConflictException.class,
                () -> service(racingMapper).delete(saved.getId(), saved.getRevision()));

        assertEquals(1, countAll());
        assertEquals(saved.getRevision() + 1, mapper.selectById(saved.getId()).getRevision());
    }

    @Test
    void ownerAccessIsStillRequiredBeforeDeletingDraft() {
        UiEventBinding saved = service.save(request());
        doThrow(new IllegalArgumentException("无列表配置权限"))
                .when(access).requireListAccess("list-1");

        assertThrows(IllegalArgumentException.class,
                () -> service.delete(saved.getId(), saved.getRevision()));

        assertEquals(1, countAll());
    }

    private int countAll() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_binding", Integer.class);
    }

    private UiEventBindingSaveRequest request() {
        var request = new UiEventBindingSaveRequest();
        request.setOwnerType("LIST");
        request.setOwnerId("list-1");
        request.setTargetType("BUTTON");
        request.setTargetKey("excel_download");
        request.setEventCode("TOOLBAR_BUTTON_CLICK");
        request.setInheritanceMode("REPLACE");
        request.setEnabled(true);
        request.setSteps(List.of(Map.of(
                "stepCode", "excel_action", "strategy", "REPLACE", "order", 10,
                "outputMapping", Map.of())));
        return request;
    }

    private UiEventBindingService service(UiEventBindingMapper bindingMapper) {
        EntityListConfig list = new EntityListConfig();
        list.setId("list-1");
        list.setEntityId("entity-1");
        list.setEntityCode("employee");
        list.setListKey("list001");
        EntityListConfigMapper listMapper = mock(EntityListConfigMapper.class);
        when(listMapper.selectById("list-1")).thenReturn(list);
        ObjectMapper objectMapper = new ObjectMapper();
        return new UiEventBindingService(bindingMapper,
                mock(UiConfigReleaseMapper.class), mock(EntityDefinitionMapper.class),
                mock(EntityFormMapper.class), listMapper,
                mock(EntityDefinitionAccessPolicy.class), access,
                mock(UiInterfaceExtensionService.class), mock(UiEventBindingSnapshotService.class),
                mock(UiConfigReleaseService.class), new JsonDocumentCodec(objectMapper), objectMapper);
    }
}
