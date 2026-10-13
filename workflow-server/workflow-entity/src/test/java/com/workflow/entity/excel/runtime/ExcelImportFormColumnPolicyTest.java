package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExcelImportFormColumnPolicyTest {
    @Test
    void legacyDocumentsDefaultToDirectWithoutChangingUnmappedFileHeadersPolicy() throws Exception {
        var document = new ObjectMapper().readValue("""
                {"schemaVersion":1,"schemeCode":"test","name":"test",
                 "file":{"sheetName":"数据","headRowNumber":1,"unknownColumnPolicy":"REJECT"},
                 "importConfig":{"enabled":true,"writePolicy":"CREATE_ONLY","transactionPolicy":"ATOMIC_ALL",
                   "writeFormKey":"create","startProcess":false,"maxRows":1000}}
                """, ExcelSchemeDocument.class);
        assertEquals("DIRECT", document.importConfig().unknownColumnPolicy());
        assertEquals("REJECT", document.file().unknownColumnPolicy());
        assertEquals("DIRECT", new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "ATOMIC_ALL", "create", false, 1000)
                .unknownColumnPolicy());
    }

    @Test
    void rootFieldsAndEntityBindingsCountButChildFieldsWithSameCodeDoNot() {
        var form = new EntityForm(); form.setId("main");
        var legacy = field("legacy", "legacy");
        var childProjection = field("child-name", "childName");
        var childMatchingMain = field("child-shared", "shared");
        form.setFields(List.of(legacy, childProjection, childMatchingMain));
        var group = node("group", "CONTAINER", null, null);
        var visible = node("visible", "FIELD", "group", "visible");
        visible.setPropsDocument("{\"hidden\":true,\"readonly\":true}");
        var shared = node("shared", "FIELD", "group", "shared");
        var subform = node("detail", "SUB_FORM", null, "detail");
        subform.setPropsDocument("{\"fields\":[{\"fieldCode\":\"embeddedOnly\"}]}");
        var child = node("child-name", "FIELD", "detail", "childName");
        var sameName = node("child-shared", "FIELD", "detail", "shared");
        var sublist = node("list", "SUB_LIST", null, "list");
        var listField = node("list-field", "FIELD", "list", "listOnly");
        form.setNodes(List.of(group, visible, shared, subform, child, sameName, sublist, listField));
        assertEquals(Set.of("legacy", "visible", "shared"), ExcelImportFormColumnPolicy.formFields(form));
    }

    @Test
    void legacyPropsOnlyFieldAndParentKeyWorkWithoutReadingOtherFormFields() {
        var form = new EntityForm(); form.setId("main");
        var otherForm = field("other", "other"); otherForm.setFormId("child");
        form.setFields(List.of(otherForm));
        var parent = node("parent-id", "REPEATER", null, null); parent.setNodeKey("parent-key");
        var child = node("child-id", "FIELD", "parent-key", "child");
        var root = node("root", "FIELD", null, null);
        root.setBindingType(null); root.setPropsDocument("{\"fieldCode\":\"root\"}");
        form.setNodes(List.of(parent, child, root));
        assertEquals(Set.of("root"), ExcelImportFormColumnPolicy.formFields(form));
    }

    private EntityFormField field(String id, String code) {
        var field = new EntityFormField(); field.setId(id); field.setFieldCode(code); return field;
    }

    private EntityFormNode node(String id, String type, String parent, String code) {
        var node = new EntityFormNode(); node.setId(id); node.setNodeType(type); node.setParentId(parent);
        node.setBindingType("ENTITY_FIELD"); node.setBindingRef(code); return node;
    }
}
