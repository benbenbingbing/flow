package com.workflow.service.listfield;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityListField;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateListFieldDataProviderTest {

    @Test
    void composesFieldsWithoutExecutingScripts() {
        TemplateListFieldDataProvider provider = new TemplateListFieldDataProvider(new ObjectMapper());
        EntityListField field = new EntityListField();
        field.setFieldCode("summary");
        field.setDataSourceConfig("{\"template\":\"${dataNo} / ${owner}\",\"emptyText\":\"-\"}");

        EntityDataDTO row = new EntityDataDTO();
        row.setDataNo("PO-001");
        row.setData(new HashMap<>(Map.of("owner", "张三")));

        provider.enrich(new ArrayList<>(List.of(row)), List.of(field), Map.of());

        assertEquals("PO-001 / 张三", row.getExtData().get("summary"));
    }
}
