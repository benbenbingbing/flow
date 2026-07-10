package com.workflow.service;

import com.workflow.dto.EntityListConfigDTO;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListField;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListFieldMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 实体列表配置服务业务测试
 * 覆盖列表配置+字段的增删改查，验证"全删全插"字段策略
 */
class EntityListConfigBusinessTest {

    private EntityListConfigMapper configMapper;
    private EntityListFieldMapper fieldMapper;
    private EntityListConfigService service;

    @BeforeEach
    void setUp() {
        configMapper = mock(EntityListConfigMapper.class);
        fieldMapper = mock(EntityListFieldMapper.class);
        service = new EntityListConfigService(configMapper, fieldMapper);
    }

    @Nested
    @DisplayName("保存列表配置")
    class SaveConfig {

        @Test
        @DisplayName("新建配置 - 插入配置，插入字段（ID 清空，sortOrder 按索引）")
        void saveConfig_new() {
            EntityListConfigDTO dto = new EntityListConfigDTO();
            dto.setEntityId("e1");
            dto.setListKey("default");
            dto.setListName("默认列表");

            EntityListField f1 = new EntityListField();
            f1.setFieldCode("amount");
            EntityListField f2 = new EntityListField();
            f2.setFieldCode("remark");

            dto.setFields(List.of(f1, f2));

            EntityListConfig savedConfig = new EntityListConfig();
            savedConfig.setId("c1");
            when(configMapper.selectById("c1")).thenReturn(savedConfig);
            when(fieldMapper.findByListConfigId("c1")).thenReturn(List.of(f1, f2));

            service.saveConfig(dto);

            verify(configMapper).insert(any(EntityListConfig.class));
            verify(fieldMapper).insert(argThat((com.workflow.entity.EntityField f) -> f.getId() == null && f.getSortOrder() == 0 && f.getDeleted() == 0));
            verify(fieldMapper).insert(argThat((com.workflow.entity.EntityField f) -> f.getSortOrder() == 1));
        }

        @Test
        @DisplayName("更新配置 - 先删旧字段再插新字段（全量替换）")
        void saveConfig_update_replaceFields() {
            EntityListConfigDTO dto = new EntityListConfigDTO();
            dto.setId("c1");
            dto.setListKey("default");
            dto.setListName("更新列表");

            EntityListField newField = new EntityListField();
            newField.setFieldCode("status");
            dto.setFields(List.of(newField));

            EntityListConfig existingConfig = new EntityListConfig();
            existingConfig.setId("c1");
            when(configMapper.selectById("c1")).thenReturn(existingConfig);
            when(fieldMapper.findByListConfigId("c1")).thenReturn(List.of(newField));

            service.saveConfig(dto);

            verify(configMapper).updateById(any(EntityListConfig.class));
            verify(fieldMapper).deleteByListConfigId("c1");
            verify(fieldMapper).insert(argThat((com.workflow.entity.EntityField f) -> "c1".equals(f.getListConfigId()) && "status".equals(f.getFieldCode())));
        }

        @Test
        @DisplayName("保存配置 - 无字段时只保存配置不插字段")
        void saveConfig_noFields() {
            EntityListConfigDTO dto = new EntityListConfigDTO();
            dto.setEntityId("e1");
            dto.setListKey("empty");

            EntityListConfig savedConfig = new EntityListConfig();
            savedConfig.setId("c1");
            when(configMapper.selectById("c1")).thenReturn(savedConfig);
            when(fieldMapper.findByListConfigId("c1")).thenReturn(Collections.emptyList());

            service.saveConfig(dto);

            verify(fieldMapper, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("删除列表配置")
    class DeleteConfig {

        @Test
        @DisplayName("删除配置 - 配置逻辑删除 + 字段物理删除")
        void deleteConfig_success() {
            service.deleteConfig("c1");

            verify(configMapper).deleteById("c1");
            verify(fieldMapper).deleteByListConfigId("c1");
        }
    }

    @Nested
    @DisplayName("查询配置")
    class QueryConfig {

        @Test
        @DisplayName("按实体ID查询所有列表配置")
        void findByEntityId() {
            EntityListConfig config = new EntityListConfig();
            config.setId("c1");
            config.setEntityId("e1");
            when(configMapper.findByEntityId("e1")).thenReturn(List.of(config));

            var result = service.findByEntityId("e1");

            assertEquals(1, result.size());
            assertEquals("c1", result.get(0).getId());
        }

        @Test
        @DisplayName("按ID查询配置 - 不存在时返回 null")
        void findById_notFound() {
            when(configMapper.selectById("nonexistent")).thenReturn(null);

            EntityListConfigDTO result = service.findById("nonexistent");

            assertNull(result);
        }

        @Test
        @DisplayName("按ID查询配置 - 含字段列表")
        void findById_withFields() {
            EntityListConfig config = new EntityListConfig();
            config.setId("c1");
            config.setEntityId("e1");

            EntityListField field = new EntityListField();
            field.setFieldCode("amount");

            when(configMapper.selectById("c1")).thenReturn(config);
            when(fieldMapper.findByListConfigId("c1")).thenReturn(List.of(field));

            EntityListConfigDTO result = service.findById("c1");

            assertNotNull(result);
            assertEquals(1, result.getFields().size());
            assertEquals("amount", result.getFields().get(0).getFieldCode());
        }
    }
}
