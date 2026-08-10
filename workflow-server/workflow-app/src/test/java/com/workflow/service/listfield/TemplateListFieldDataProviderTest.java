package com.workflow.service.listfield;

import com.workflow.entity.list.extension.TemplateListFieldDataProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 模板列表字段数据提供者测试。
 *
 * <p>被测对象：{@link TemplateListFieldDataProvider}，覆盖纯模板字段组合而不执行脚本的场景。
 */
class TemplateListFieldDataProviderTest {

    /** 数据源只配置组合逻辑，空值文本统一归单元格显示配置。 */
    @Test
    void exposesOnlyTemplateDataSourceConfig() {
        TemplateListFieldDataProvider provider =
                new TemplateListFieldDataProvider(new ObjectMapper());

        assertEquals(1, provider.getConfigSchema().size());
        assertEquals(
                "template",
                provider.getConfigSchema().get(0).get("key"));
    }

    /** 测试纯模板字段组合不执行脚本：验证按模板渲染出 summary 扩展字段值 */
    @Test
    void composesFieldsWithoutExecutingScripts() {
        TemplateListFieldDataProvider provider = new TemplateListFieldDataProvider(new ObjectMapper());
        EntityListField field = new EntityListField();
        field.setFieldCode("summary");
        field.setDataSourceConfig("{\"template\":\"${dataNo} / ${owner}\"}");
        field.setRenderConfig("{\"emptyText\":\"-\"}");

        EntityDataDTO row = new EntityDataDTO();
        row.setDataNo("PO-001");
        row.setData(new HashMap<>(Map.of("owner", "张三")));

        provider.enrich(new ArrayList<>(List.of(row)), List.of(field), Map.of());

        assertEquals("PO-001 / 张三", row.getExtData().get("summary"));
    }

    /** 空字段统一使用单元格渲染配置中的空值文本。 */
    @Test
    void usesCellRenderEmptyTextForMissingTemplateValues() {
        TemplateListFieldDataProvider provider =
                new TemplateListFieldDataProvider(new ObjectMapper());
        EntityListField field = new EntityListField();
        field.setFieldCode("summary");
        field.setDataSourceConfig(
                "{\"template\":\"${dataNo} / ${owner}\"}");
        field.setRenderConfig("{\"emptyText\":\"未填写\"}");

        EntityDataDTO row = new EntityDataDTO();
        row.setDataNo("PO-001");
        row.setData(new HashMap<>());

        provider.enrich(
                new ArrayList<>(List.of(row)),
                List.of(field),
                Map.of());

        assertEquals(
                "PO-001 / 未填写",
                row.getExtData().get("summary"));
    }
}
