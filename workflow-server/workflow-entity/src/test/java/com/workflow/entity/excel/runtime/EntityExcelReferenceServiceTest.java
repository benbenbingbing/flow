package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.application.*;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.excel.config.EntityExcelReferenceMetadata;
import com.workflow.entity.excel.config.EntityExcelReferenceMetadata.*;
import com.workflow.entity.excel.model.*;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.excel.ExcelUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntityExcelReferenceServiceTest {
    private final EntityExcelReferenceMetadata metadata = mock(EntityExcelReferenceMetadata.class);
    private final EntityDataDynamicService data = mock(EntityDataDynamicService.class);
    private final SystemEntityReadService system = mock(SystemEntityReadService.class);
    private final EntityActionCapabilityService permissions = mock(EntityActionCapabilityService.class);
    private final EntityExcelReferenceService service = new EntityExcelReferenceService(metadata, provider(data), provider(system), provider(permissions));
    private final List<EntityDataDTO> rows = new ArrayList<>();
    private final EntityField single = field("customer", EntityField.FieldType.REFERENCE);
    private final EntityField multi = field("partners", EntityField.FieldType.MULTI_REFERENCE);
    private final Map<String, Property> properties = new LinkedHashMap<>();

    @BeforeEach
    void setup() {
        for (String code : List.of("id", "code", "name", "email")) properties.put(code, new Property(code, code, EntityField.FieldType.STRING, code));
        properties.put("score", new Property("score", "积分", EntityField.FieldType.DECIMAL, "score"));
        properties.put("birthday", new Property("birthday", "生日", EntityField.FieldType.DATE, "birthday"));
        when(metadata.resolve(any())).thenAnswer(call -> new Target("customer", "客户", false,
                call.getArgument(0, EntityField.class).getFieldType() == EntityField.FieldType.MULTI_REFERENCE, properties));
        when(data.findPage(eq("customer"), isNull(), anyMap(), anyLong(), eq(200L), eq("id"), eq("ASC"))).thenAnswer(call -> {
            Map<String, Object> filter = call.getArgument(2);
            String field = filter.keySet().stream().filter(key -> !key.endsWith("_op")).findFirst().orElseThrow();
            assertEquals("IN", filter.get(field + "_op"));
            Collection<?> values = (Collection<?>) filter.get(field);
            var found = rows.stream().filter(row -> values.stream().anyMatch(value -> Objects.equals(value.toString(), row.getData().get(field).toString()))).toList();
            int start = ((int)(long)call.getArgument(3) - 1) * 200;
            return new PageResult<>(found.subList(Math.min(start, found.size()), Math.min(start + 200, found.size())), found.size(), (long)call.getArgument(3), 200);
        });
        rows.add(row("1", "C001", "客户甲", "a@example.com", "1"));
        rows.add(row("2", "C002", "客户乙", "b@example.com", "2"));
    }

    @Test void defaultsMatchCodeOrNameButNeverChooseFirstAmbiguousRecord() {
        var session = service.compile(single, Map.of(), true);
        session.prepareImport(List.of("C001", "客户乙", "missing"));
        assertEquals("1", session.read("C001")); assertEquals("2", session.read("客户乙"));
        assertThrows(IllegalArgumentException.class, () -> session.read("missing"));
        // 编码和名称分别命中不同记录时也必须报错，不能隐含“编码优先”。
        rows.add(row("3", "other", "C001", "other@example.com", "3"));
        session.prepareImport(List.of("C001"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> session.read("C001")).getMessage().contains("多条"));
        verify(permissions, atLeastOnce()).requireStandardPermission("customer", EntityPermissionAction.VIEW);
    }

    @Test void sameRecordMatchingTwoPropertiesIsUnambiguousAndCustomPropertiesWork() {
        rows.set(0, row("1", "C001", "C001", "a@example.com", "1"));
        var session = service.compile(single, Map.of(), true); session.prepareImport(List.of("C001"));
        assertEquals("1", session.read("C001"));
        var custom = service.compile(single, Map.of("matchFields", List.of("email", "score", "birthday")), true);
        custom.prepareImport(List.of("a@example.com", "2.00", "2020-01-01"));
        assertEquals("1", custom.read("a@example.com")); assertEquals("2", custom.read("2.00"));
        assertThrows(IllegalArgumentException.class, () -> custom.read("2020-01-01")); // 两条同生日记录
    }

    @Test void multipleValuesDeduplicateIdsAndRoundTripEscapedNames() {
        rows.set(0, row("1", "C001", "甲;乙\\公司", "a@example.com", "1"));
        var export = service.compile(multi, Map.of(), false);
        export.prepareIds(List.of(List.of("1", "2")));
        String text = export.write(List.of("1", "2"));
        assertEquals("甲\\;乙\\\\公司;客户乙", text);
        var input = service.compile(multi, Map.of(), true);
        input.prepareImport(List.of(text, "C001;C001;客户乙"));
        assertEquals(List.of("1", "2"), input.read(text));
        assertEquals(List.of("1", "2"), input.read("C001;C001;客户乙"));
        assertEquals(List.of(), input.read(""));
        assertThrows(IllegalArgumentException.class, () -> input.read("C001;"));
        assertThrows(IllegalArgumentException.class, () -> input.read("C001\\"));
    }

    @Test void newlineSeparatorAndCustomExportProperty() {
        var input = service.compile(multi, Map.of("separator", "\n"), true);
        input.prepareImport(List.of("C001\r\nC002"));
        assertEquals(List.of("1", "2"), input.read("C001\r\nC002"));
        var export = service.compile(multi, Map.of("separator", "\n", "exportField", "email"), false);
        export.prepareIds(List.of(List.of("2", "1")));
        assertEquals("b@example.com\na@example.com", export.write(List.of("2", "1")));
    }

    @Test void processedIdsAndExportsRejectDeletedOrInaccessibleRecordsAndMalformedStorage() {
        var session = service.compile(multi, Map.of(), true);
        session.prepareIds(List.of(List.of("1", "hidden")));
        assertEquals(List.of("1"), session.validateIds(List.of("1")));
        assertThrows(IllegalArgumentException.class, () -> session.validateIds(List.of("hidden")));
        assertThrows(IllegalArgumentException.class, () -> session.validateIds("1,2"));
        rows.clear(); session.prepareIds(List.of(List.of("1")));
        assertThrows(IllegalArgumentException.class, () -> session.validateIds(List.of("1")));
        var export = service.compile(single, Map.of(), false); export.prepareIds(List.of("1"));
        assertThrows(IllegalArgumentException.class, () -> export.write("1"));
    }

    @Test void deniedTargetPermissionPreventsDataLookup() {
        doThrow(new IllegalArgumentException("无权读取目标实体")).when(permissions).requireStandardPermission("customer", EntityPermissionAction.VIEW);
        var session = service.compile(single, Map.of(), true);
        assertThrows(IllegalArgumentException.class, () -> session.prepareImport(List.of("C001")));
        verifyNoInteractions(data);
    }

    @Test void systemReferencesQueryPhysicalAttributesThroughSystemReadPolicy() {
        var systemProperties = Map.of("id", properties.get("id"),
                "code", new Property("code", "编码", EntityField.FieldType.STRING, "username"),
                "name", new Property("name", "名称", EntityField.FieldType.STRING, "nickname"));
        when(metadata.resolve(single)).thenReturn(new Target("sys_user", "用户", true, false, systemProperties));
        var user = new EntityDataDTO(); user.setId("u1"); user.setData(Map.of("username", "alice", "nickname", "张三"));
        when(system.findPage(eq("sys_user"), anyMap(), eq(1L), eq(200L))).thenReturn(new PageResult<>(List.of(user),1,1,200));
        var input = service.compile(single, Map.of(), true); input.prepareImport(List.of("alice"));
        assertEquals("u1", input.read("alice"));
        verify(system).findPage("sys_user", Map.of("username",List.of("alice"),"username_op","IN"),1,200);
        var export = service.compile(single, Map.of(), false); export.prepareIds(List.of("u1"));
        assertEquals("张三", export.write("u1"));
        verifyNoInteractions(data, permissions);
    }

    @Test void matchingTraversesPagesAndCapsExcessiveNonUniqueResults() {
        rows.clear(); for (int i=0;i<201;i++) rows.add(row("id"+i, "C"+i, "同名", "a"+i, "1"));
        var session = service.compile(single, Map.of("matchFields", List.of("name")), true);
        session.prepareImport(List.of("同名"));
        assertThrows(IllegalArgumentException.class, () -> session.read("同名"));
        verify(data).findPage(eq("customer"), isNull(), anyMap(), eq(2L), eq(200L), eq("id"), eq("ASC"));
        when(data.findPage(anyString(), isNull(), anyMap(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(new PageResult<>(List.of(), 10001, 1, 200));
        assertThrows(IllegalArgumentException.class, () -> session.prepareImport(List.of("同名")));
    }

    @Test void realWorkbookParsesIdsReportsCoordinatesAndExportsChosenValues() throws Exception {
        var codec = new EntityExcelCodecService(mock(SysDictItemService.class), new EntityFieldValidationRuleService(new ObjectMapper()),
                mock(EntityStatusService.class), service);
        var plan = plan(List.of(single, multi));
        var out = new ByteArrayOutputStream();
        ExcelUtils.writeRows(out, List.of(List.of("customer"), List.of("partners")),
                List.of(List.of("C001", "C001;客户乙"), List.of("missing", "客户乙")), "数据");
        var parsed = codec.parse(plan, new ByteArrayInputStream(out.toByteArray()));
        assertEquals("1", parsed.rows().get(0).data().get("customer"));
        assertEquals(List.of("1", "2"), parsed.rows().get(0).data().get("partners"));
        assertEquals(1, parsed.errors().size()); assertEquals(3, parsed.errors().get(0).rowNumber());
        assertEquals("A", parsed.errors().get(0).columnLetter());
        var record = new EntityDataDTO(); record.setData(parsed.rows().get(0).data());
        assertEquals(List.of("客户甲", "客户甲;客户乙"), codec.formatRow(plan, record));
        assertTrue(codec.validateProcessedRows(plan, parsed.subset(List.of(parsed.rows().get(0))), List.of(record.getData())).isEmpty());
        assertFalse(codec.validateProcessedRows(plan, parsed.subset(List.of(parsed.rows().get(0))),
                List.of(Map.of("customer", "not-authorized", "partners", List.of("1")))).isEmpty());
    }

    @Test void requiredMultiCannotPassWithEmptyList() throws Exception {
        multi.setIsRequired(true);
        var codec = new EntityExcelCodecService(mock(SysDictItemService.class), new EntityFieldValidationRuleService(new ObjectMapper()), mock(EntityStatusService.class), service);
        var plan = plan(List.of(single, multi));
        var row = new EntityExcelCodecService.Row(0, "数据", 2, Map.of(), Map.of());
        var parsed = new EntityExcelCodecService.Parsed(List.of(row), List.of(), 1,0,1,0,false,Map.of(0,"customer",1,"partners"));
        var errors = codec.validateProcessedRows(plan, parsed, List.of(Map.of("customer", "1", "partners", List.of())));
        assertEquals("partners", errors.get(0).fieldCode());
        assertTrue(errors.get(0).message().contains("不能为空"));
    }

    private EntityExcelRuntimeResolver.Plan plan(List<EntityField> fields) {
        var columns = fields.stream().map(f -> new ExcelSchemeDocument.Column(f.getFieldCode(), f.getFieldCode(), List.of(), f.getFieldCode(),
                true,true,true,20,Map.<String,Object>of("type","ENTITY_REFERENCE"),Map.<String,Object>of())).toList();
        var doc = new ExcelSchemeDocument(1,"refs","关联","",new ExcelSchemeDocument.FileConfig("数据",1,"REJECT"),
                new ExcelSchemeDocument.ImportConfig(true,"CREATE_ONLY","ATOMIC_ALL","create",false,1000),
                new ExcelSchemeDocument.ExportConfig(true,"数据","数据"), columns);
        var snapshot = new EntityPublishedSnapshot(); snapshot.setFields(fields);
        return new EntityExcelRuntimeResolver.Plan(null,new ExcelResolvedScheme("e","source","refs",doc,"hash","","f"),null,
                new EntityPublishedSnapshotService.PinnedEntitySnapshot(snapshot,"schema"),null);
    }
    private static EntityField field(String code, EntityField.FieldType type) { var field = new EntityField(); field.setFieldCode(code); field.setFieldName(code); field.setFieldType(type); return field; }
    private static EntityDataDTO row(String id, String code, String name, String email, String score) {
        var row = new EntityDataDTO(); row.setId(id); row.setCode(code); row.setName(name);
        row.setData(Map.of("id",id,"code",code,"name",name,"email",email,"score",new BigDecimal(score),"birthday","2020-01-01")); return row;
    }
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T value) { var provider = mock(ObjectProvider.class); when(provider.getObject()).thenReturn(value); return provider; }
}
