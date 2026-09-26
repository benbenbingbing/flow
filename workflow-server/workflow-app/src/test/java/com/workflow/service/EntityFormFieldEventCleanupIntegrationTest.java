package com.workflow.service;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.core.database.jdbc.JdbcWriteAttempt;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.form.application.EntityFormNodeService;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** 使用真实 Mapper 和事务验证级联清理范围、重复删除重建及失败回滚。 */
class EntityFormFieldEventCleanupIntegrationTest {
    private JdbcTemplate jdbc;
    private EntityFormNodeMapper nodes;
    private UiEventBindingMapper bindings;
    private EntityFormMapper forms;
    private EntityFormNodeService service;

    @BeforeEach
    void setUp() throws Exception {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:field_events_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE entity_form_node (
                  id VARCHAR(64) PRIMARY KEY, form_id VARCHAR(64), parent_id VARCHAR(64),
                  node_key VARCHAR(100), node_type VARCHAR(30), binding_type VARCHAR(30), binding_ref VARCHAR(200),
                  component_name VARCHAR(100), component_version INT, snapshot_version INT,
                  props_document CLOB, rules_document CLOB, data_source_bindings_document CLOB,
                  legacy_props_document CLOB, order_key BIGINT, revision INT, template_id VARCHAR(64),
                  template_version INT, local_overrides_document CLOB, create_time TIMESTAMP,
                  update_time TIMESTAMP, deleted INT DEFAULT 0
                )
                """);
        jdbc.execute("""
                CREATE TABLE ui_event_binding (
                  id VARCHAR(64) PRIMARY KEY, owner_type VARCHAR(20), owner_id VARCHAR(64),
                  target_type VARCHAR(20), target_key VARCHAR(100), event_code VARCHAR(50),
                  inheritance_mode VARCHAR(20), steps_document CLOB, revision INT, enabled INT,
                  create_time TIMESTAMP, update_time TIMESTAMP, deleted INT DEFAULT 0,
                  UNIQUE (owner_type, owner_id, target_type, target_key, event_code, deleted)
                )
                """);
        var configuration = new MybatisConfiguration();
        configuration.setDatabaseId("MYSQL");
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(EntityFormNodeMapper.class);
        configuration.addMapper(UiEventBindingMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        nodes = session.getMapper(EntityFormNodeMapper.class);
        bindings = session.getMapper(UiEventBindingMapper.class);
        forms = mock(EntityFormMapper.class);
        var target = new EntityFormNodeService(forms, nodes,
                mock(EntityRelationMapper.class), mock(UiConfigReleaseMapper.class),
                mock(EntityUiConfigurationPolicy.class), mock(EntityDefinitionMapper.class),
                mock(EntityFieldMapper.class), mock(SystemEntityFieldPolicy.class), bindings,
                new JsonDocumentCodec(new ObjectMapper()), new JdbcWriteAttempt(new JdbcTemplate(dataSource),
                        DatabaseDialects.insert(DatabaseVendor.MYSQL)));
        var transaction = new TransactionInterceptor();
        transaction.setTransactionManager(new DataSourceTransactionManager(dataSource));
        transaction.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(transaction);
        service = (EntityFormNodeService) proxy.getProxy();
    }

    @Test
    void repeatedDeleteAndReaddDoesNotReviveBindingsOrHitDeletedUniqueKey() {
        insertNode("node-1");
        insertBinding("current", "FORM", "form-1", "FIELD", "myUser1", 0);
        insertBinding("old-tombstone", "FORM", "form-1", "FIELD", "myUser1", 1);
        insertBinding("other-form", "FORM", "form-2", "FIELD", "myUser1", 0);
        insertBinding("other-field", "FORM", "form-1", "FIELD", "name", 0);
        insertBinding("entity-default", "ENTITY", "entity-1", "OWNER", "", 0);
        insertBinding("form-default", "FORM", "form-1", "OWNER", "", 0);
        insertBinding("button", "FORM", "form-1", "BUTTON", "myUser1", 0);

        service.delete("form-1", "node-1", 1);
        assertNull(nodes.selectById("node-1"));
        assertEquals(0, localFieldBindingCount());
        assertEquals(List.of("button", "entity-default", "form-default", "other-field", "other-form"),
                jdbc.queryForList("SELECT id FROM ui_event_binding ORDER BY id", String.class));

        insertNode("node-2");
        assertEquals(0, localFieldBindingCount(), "重新添加同编码字段不得恢复旧事件");
        insertBinding("new-event", "FORM", "form-1", "FIELD", "myUser1", 0);
        service.delete("form-1", "node-2", 1);
        assertEquals(0, localFieldBindingCount());
    }

    @Test
    void clearsBindingOnlyAfterLastSameCodeNodeIsDeleted() {
        insertNode("node-1");
        insertNode("node-2");
        insertBinding("current", "FORM", "form-1", "FIELD", "myUser1", 0);

        service.delete("form-1", "node-1", 1);
        assertEquals(1, localFieldBindingCount());
        service.delete("form-1", "node-2", 1);
        assertEquals(0, localFieldBindingCount());
    }

    @Test
    void laterFailureRollsBackBothNodeDeletionAndEventCleanup() {
        insertNode("node-1");
        insertBinding("current", "FORM", "form-1", "FIELD", "myUser1", 0);
        when(forms.update(isNull(), any())).thenThrow(new IllegalStateException("模拟表单修订更新失败"));

        assertThrows(IllegalStateException.class, () -> service.delete("form-1", "node-1", 1));

        assertNotNull(nodes.selectById("node-1"));
        assertEquals(1, nodes.selectById("node-1").getRevision());
        assertEquals(1, localFieldBindingCount());
    }

    private int localFieldBindingCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_binding WHERE owner_type='FORM'"
                + " AND owner_id='form-1' AND target_type='FIELD' AND target_key='myUser1'", Integer.class);
    }

    private void insertNode(String id) {
        EntityFormNode node = new EntityFormNode();
        node.setId(id);
        node.setFormId("form-1");
        node.setNodeKey(id);
        node.setNodeType("FIELD");
        node.setPropsDocument("{\"fieldCode\":\"myUser1\",\"hidden\":true}");
        node.setRevision(1);
        node.setDeleted(0);
        nodes.insert(node);
    }

    private void insertBinding(String id, String owner, String ownerId, String type, String key, int deleted) {
        UiEventBinding binding = new UiEventBinding();
        binding.setId(id);
        binding.setOwnerType(owner);
        binding.setOwnerId(ownerId);
        binding.setTargetType(type);
        binding.setTargetKey(key);
        binding.setEventCode("ENTITY_SELECTED");
        binding.setRevision(1);
        binding.setEnabled(true);
        binding.setDeleted(deleted);
        bindings.insert(binding);
    }
}
