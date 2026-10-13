package com.workflow.database;

import com.workflow.core.database.jdbc.DatabaseExceptionClassifier;
import com.workflow.core.database.port.SchemaMetadataPort;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.entity.data.application.port.SchemaDdlExecutor;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.schema.dialect.OceanBaseMySqlSchemaDdlDialect;
import com.workflow.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 发布路径在执行任何 DDL 前拒绝不支持的默认值，并通过既有接口错误协议返回 HTTP 400。 */
class OceanBaseSchemaValidationTest {
    @Test
    void schemaPublicationReturnsActionableBadRequestBeforeExecutingAnyDdl() throws Exception {
        var fields = mock(EntityFieldMapper.class);
        var resolver = mock(EntityPhysicalTableResolver.class);
        var metadata = mock(SchemaMetadataPort.class);
        var executor = mock(SchemaDdlExecutor.class);
        var entity = new EntityDefinition();
        entity.setId("ob-defaults"); entity.setEntityCode("ob_defaults");
        var field = new EntityField();
        field.setFieldCode("description"); field.setFieldType(EntityField.FieldType.TEXT);
        field.setDefaultValue("必须保留的默认值"); field.setIsPublished(true);
        when(fields.findByEntityId(entity.getId())).thenReturn(List.of(field));
        when(resolver.resolve(entity)).thenReturn("biz_ob_defaults");
        var service = new DynamicTableService(mock(com.workflow.entity.data.infrastructure.persistence.mapper.EntityUniqueConflictMapper.class), fields, resolver, executor,
                new OceanBaseMySqlSchemaDdlDialect(), metadata,
                DatabaseQueryDialects.forVendor(DatabaseVendor.OCEANBASE_MYSQL));
        var mvc = MockMvcBuilders.standaloneSetup(new PublicationController(service, entity))
                .setControllerAdvice(new GlobalExceptionHandler(new DatabaseExceptionClassifier(
                        DatabaseDialects.errors(DatabaseVendor.OCEANBASE_MYSQL)))).build();
        mvc.perform(post("/test/ob-schema-publish"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("字段「description」")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("请清除字段默认值")));
        // 即使字段已发布且数据库列类型未改变，也先检查默认值，不能绕过校验或提前发出 DDL。
        verifyNoInteractions(executor, metadata);
        assertEquals("必须保留的默认值", field.getDefaultValue());
    }

    @RestController
    static class PublicationController {
        private final DynamicTableService service;
        private final EntityDefinition entity;
        PublicationController(DynamicTableService service, EntityDefinition entity) {
            this.service = service; this.entity = entity;
        }
        @PostMapping("/test/ob-schema-publish")
        void publish() { service.syncEntityTableStructure(entity); }
    }
}
