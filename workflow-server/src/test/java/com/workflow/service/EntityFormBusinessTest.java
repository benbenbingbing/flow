package com.workflow.service;

import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityFormFieldMapper;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 实体表单服务业务测试
 * 覆盖表单设计全流程：创建 → 字段绑定 → 默认表单互斥 → 复制 → 删除
 */
class EntityFormBusinessTest {

    private EntityFormMapper formMapper;
    private EntityFormFieldMapper formFieldMapper;
    private EntityDefinitionMapper entityMapper;
    private EntityFieldMapper fieldMapper;
    private EntityRelationMapper relationMapper;

    private EntityFormService service;

    @BeforeEach
    void setUp() {
        formMapper = mock(EntityFormMapper.class);
        formFieldMapper = mock(EntityFormFieldMapper.class);
        entityMapper = mock(EntityDefinitionMapper.class);
        fieldMapper = mock(EntityFieldMapper.class);
        relationMapper = mock(EntityRelationMapper.class);

        service = new EntityFormService(formMapper, formFieldMapper, entityMapper, fieldMapper, relationMapper);
    }

    // ==================== 保存表单 ====================

    @Nested
    @DisplayName("保存表单")
    class SaveForm {

        @Test
        @DisplayName("新建表单 - 设置默认值（layoutType/status/isDefault），插入并保存字段")
        void saveForm_new_withDefaults() {
            EntityForm form = new EntityForm();
            form.setEntityId("e1");
            form.setFormName("报销表单");
            form.setFormKey("expense_form");

            when(formMapper.existsFormKey("e1", "expense_form", "")).thenReturn(false);
            when(formMapper.selectByEntityId("e1")).thenReturn(Collections.emptyList());

            EntityForm result = service.saveForm(form);

            assertNotNull(result);
            assertEquals("vertical", form.getLayoutType());
            assertEquals(1, form.getStatus());
            assertEquals(false, form.getIsDefault());
            verify(formMapper).insert(form);
        }

        @Test
        @DisplayName("表单标识重复 - 抛异常")
        void saveForm_duplicateFormKey_throwsException() {
            EntityForm form = new EntityForm();
            form.setEntityId("e1");
            form.setFormKey("expense_form");

            when(formMapper.existsFormKey("e1", "expense_form", "")).thenReturn(true);

            RuntimeException ex = assertThrows(RuntimeException.class, () -> service.saveForm(form));
            assertTrue(ex.getMessage().contains("表单标识已存在"));
        }

        @Test
        @DisplayName("设为默认表单 - 同实体其他表单自动取消默认")
        void saveForm_asDefault_clearOthers() {
            EntityForm form = new EntityForm();
            form.setId("f1");
            form.setEntityId("e1");
            form.setFormKey("form1");
            form.setIsDefault(true);

            EntityForm otherDefault = new EntityForm();
            otherDefault.setId("f2");
            otherDefault.setEntityId("e1");
            otherDefault.setIsDefault(true);

            when(formMapper.existsFormKey("e1", "form1", "f1")).thenReturn(false);
            when(formMapper.selectByEntityId("e1")).thenReturn(List.of(otherDefault));

            service.saveForm(form);

            verify(formMapper).updateById(argThat((com.workflow.entity.EntityField f) -> "f2".equals(f.getId()) && Boolean.FALSE.equals(f.getIsDefault())));
        }

        @Test
        @DisplayName("更新表单 - 走 updateById 而非 insert")
        void saveForm_update() {
            EntityForm form = new EntityForm();
            form.setId("f1");
            form.setEntityId("e1");
            form.setFormKey("form1");

            when(formMapper.existsFormKey("e1", "form1", "f1")).thenReturn(false);
            when(formMapper.selectByEntityId("e1")).thenReturn(Collections.emptyList());

            service.saveForm(form);

            verify(formMapper).updateById(form);
            verify(formMapper, never()).insert(any());
        }
    }

    // ==================== 表单字段绑定 ====================

    @Nested
    @DisplayName("表单字段绑定")
    class FormFieldBinding {

        @Test
        @DisplayName("保存字段 - 先删后插（全量替换），按顺序设置 sortOrder")
        void saveFormFields_fullReplace() {
            EntityFormField f1 = new EntityFormField();
            f1.setFieldId("fld1");
            f1.setFieldCode("amount");

            EntityFormField f2 = new EntityFormField();
            f2.setFieldId("fld2");
            f2.setFieldCode("remark");

            service.saveFormFields("form1", List.of(f1, f2));

            verify(formFieldMapper).deleteByFormId("form1");
            verify(formFieldMapper).insert(argThat((com.workflow.entity.EntityField f) -> "form1".equals(f.getFormId()) && f.getSortOrder() == 0));
            verify(formFieldMapper).insert(argThat((com.workflow.entity.EntityField f) -> "form1".equals(f.getFormId()) && f.getSortOrder() == 1));
        }

        @Test
        @DisplayName("保存字段 - fieldCode 为空时从 entity_field 反查补充")
        void saveFormFields_fillFieldCodeFromEntityField() {
            EntityFormField field = new EntityFormField();
            field.setFieldId("fld1");
            field.setFieldCode(null);

            EntityField entityField = new EntityField();
            entityField.setFieldCode("amount");

            when(fieldMapper.findByIdString("fld1")).thenReturn(entityField);

            service.saveFormFields("form1", List.of(field));

            assertEquals("amount", field.getFieldCode());
        }

        @Test
        @DisplayName("保存字段 - 空列表只清不插")
        void saveFormFields_emptyList() {
            service.saveFormFields("form1", Collections.emptyList());

            verify(formFieldMapper).deleteByFormId("form1");
            verify(formFieldMapper, never()).insert(any());
        }
    }

    // ==================== 默认表单 ====================

    @Nested
    @DisplayName("默认表单管理")
    class DefaultForm {

        @Test
        @DisplayName("设置默认表单 - 当前表单设默认，同实体其他表单取消默认")
        void setDefaultForm_success() {
            EntityForm form = new EntityForm();
            form.setId("f1");
            form.setEntityId("e1");
            form.setFormName("表单1");

            EntityForm other = new EntityForm();
            other.setId("f2");
            other.setEntityId("e1");
            other.setIsDefault(true);

            when(formMapper.selectById("f1")).thenReturn(form);
            when(formMapper.selectByEntityId("e1")).thenReturn(List.of(other));

            service.setDefaultForm("f1");

            assertTrue(form.getIsDefault());
            verify(formMapper).updateById(argThat((com.workflow.entity.EntityField f) -> "f1".equals(f.getId()) && Boolean.TRUE.equals(f.getIsDefault())));
            verify(formMapper).updateById(argThat((com.workflow.entity.EntityField f) -> "f2".equals(f.getId()) && Boolean.FALSE.equals(f.getIsDefault())));
        }

        @Test
        @DisplayName("设置默认表单 - 表单不存在时抛异常")
        void setDefaultForm_notFound_throwsException() {
            when(formMapper.selectById("nonexistent")).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.setDefaultForm("nonexistent"));
            assertTrue(ex.getMessage().contains("表单不存在"));
        }
    }

    // ==================== 复制表单 ====================

    @Nested
    @DisplayName("复制表单")
    class CopyForm {

        @Test
        @DisplayName("复制表单 - 深拷贝，formKey 加 _copy_ 时间戳后缀，字段逐条复制")
        void copyForm_success() {
            EntityForm source = new EntityForm();
            source.setId("f1");
            source.setEntityId("e1");
            source.setFormName("报销表单");
            source.setFormKey("expense_form");
            source.setLayoutType("horizontal");

            EntityFormField sourceField = new EntityFormField();
            sourceField.setFieldId("fld1");
            sourceField.setFieldName("金额");
            sourceField.setFieldType("DECIMAL");

            when(formMapper.selectById("f1")).thenReturn(source);
            when(formFieldMapper.selectByFormId("f1")).thenReturn(List.of(sourceField));

            EntityForm result = service.copyForm("f1");

            assertNotNull(result);
            assertEquals("报销表单 copy", result.getFormName());
            assertTrue(result.getFormKey().startsWith("expense_form_copy_"));
            assertEquals("horizontal", result.getLayoutType());
            assertEquals(1, result.getStatus());
            verify(formMapper).insert(any(EntityForm.class));
            verify(formFieldMapper).insert(argThat((com.workflow.entity.EntityField f) -> "fld1".equals(f.getFieldId()) && f.getSortOrder() == 0));
        }

        @Test
        @DisplayName("复制表单 - 源表单不存在时抛异常")
        void copyForm_notFound_throwsException() {
            when(formMapper.selectById("nonexistent")).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.copyForm("nonexistent"));
            assertTrue(ex.getMessage().contains("表单不存在"));
        }
    }

    // ==================== 删除表单 ====================

    @Nested
    @DisplayName("删除表单")
    class DeleteForm {

        @Test
        @DisplayName("删除表单 - 先删字段再删表单")
        void deleteForm_success() {
            EntityForm form = new EntityForm();
            form.setId("f1");
            form.setFormName("测试表单");
            when(formMapper.selectById("f1")).thenReturn(form);

            service.deleteForm("f1");

            verify(formFieldMapper).deleteByFormId("f1");
            verify(formMapper).deleteById("f1");
        }

        @Test
        @DisplayName("删除表单 - 表单不存在时抛异常")
        void deleteForm_notFound_throwsException() {
            when(formMapper.selectById("nonexistent")).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.deleteForm("nonexistent"));
            assertTrue(ex.getMessage().contains("表单不存在"));
        }
    }

    // ==================== 初始化配置 ====================

    @Nested
    @DisplayName("初始化配置")
    class InitConfig {

        @Test
        @DisplayName("更新初始化配置 - 表单不存在时抛异常")
        void updateInitConfig_notFound_throwsException() {
            when(formMapper.selectById("nonexistent")).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.updateInitConfig("nonexistent", "{}"));
            assertTrue(ex.getMessage().contains("表单不存在"));
        }
    }
}
