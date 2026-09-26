package com.workflow.service.listfield;

import com.workflow.entity.list.extension.TemplateListFieldDataProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.list.model.ListFieldDataRecord;
import com.workflow.contracts.entity.list.model.ListFieldDataConfig;
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
        ListFieldDataConfig field = new ListFieldDataConfig();
        field.setFieldCode("summary");
        field.setDataSourceConfig("{\"template\":\"${code} / ${owner}\"}");
        field.setRenderConfig("{\"emptyText\":\"-\"}");

        ListFieldDataRecord row = new ListFieldDataRecord();
        row.setCode("PO-001");
        row.setData(new HashMap<>(Map.of("owner", "张三")));

        provider.enrich(new ArrayList<>(List.of(row)), List.of(field), Map.of());

        assertEquals("PO-001 / 张三", row.getExtData().get("summary"));
    }

    /** 空字段统一使用单元格渲染配置中的空值文本。 */
    @Test
    void usesCellRenderEmptyTextForMissingTemplateValues() {
        TemplateListFieldDataProvider provider =
                new TemplateListFieldDataProvider(new ObjectMapper());
        ListFieldDataConfig field = new ListFieldDataConfig();
        field.setFieldCode("summary");
        field.setDataSourceConfig(
                "{\"template\":\"${code} / ${owner}\"}");
        field.setRenderConfig("{\"emptyText\":\"未填写\"}");

        ListFieldDataRecord row = new ListFieldDataRecord();
        row.setCode("PO-001");
        row.setData(new HashMap<>());

        provider.enrich(
                new ArrayList<>(List.of(row)),
                List.of(field),
                Map.of());

        assertEquals(
                "PO-001 / 未填写",
                row.getExtData().get("summary"));
    }
    /** 模板占位符与实体字段编码保持一致，审计时间和人员可直接展示。 */
    @Test
    void rendersAuditFieldsByDatabaseColumnNames() {
        TemplateListFieldDataProvider provider = new TemplateListFieldDataProvider(new ObjectMapper());
        ListFieldDataConfig field = new ListFieldDataConfig();
        field.setFieldCode("summary");
        field.setDataSourceConfig("{\"template\":\"${create_time} / ${update_time} / ${create_by} / ${update_by}\"}");
        ListFieldDataRecord row = new ListFieldDataRecord();
        var created = java.time.LocalDateTime.of(2026, 9, 20, 10, 0);
        row.setCreateTime(created);
        row.setUpdateTime(created.plusHours(1));
        row.setCreateBy("creator");
        row.setUpdateBy("updater");
        provider.enrich(new ArrayList<>(List.of(row)), List.of(field), Map.of());
        assertEquals("2026-09-20T10:00 / 2026-09-20T11:00 / creator / updater", row.getExtData().get("summary"));
    }
}
