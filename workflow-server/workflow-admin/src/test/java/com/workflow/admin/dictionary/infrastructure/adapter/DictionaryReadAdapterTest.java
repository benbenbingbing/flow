package com.workflow.admin.dictionary.infrastructure.adapter;

import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.admin.dictionary.application.SysDictService;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDict;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DictionaryReadAdapterTest {
    private final SysDictService dictionaries = mock(SysDictService.class);
    private final SysDictItemService items = mock(SysDictItemService.class);
    private final DictionaryReadAdapter port = new DictionaryReadAdapter(dictionaries, items);

    @Test
    void resolvesEnabledItemsAndHidesDisabledBranches() {
        var dictionary = new SysDict();
        dictionary.setStatus(SysDict.Status.ENABLED.getValue());
        when(dictionaries.getByCode("kind")).thenReturn(dictionary);
        var parent = item("parent", true, List.of(item("leaf", true, List.of())));
        var hidden = item("hidden", false, List.of(item("hidden-leaf", true, List.of())));
        when(items.getItemTreeByDictCode("kind")).thenReturn(List.of(parent, hidden));

        assertEquals("leaf label", port.findActiveItem("kind", "leaf").orElseThrow().label());
        assertTrue(port.findActiveItem("kind", "hidden-leaf").isEmpty());
        assertEquals(List.of("parent"), port.listActiveTree("kind").stream().map(node -> node.code()).toList());
    }

    @Test
    void skipsItemsOfMissingOrDisabledDictionary() {
        var dictionary = new SysDict();
        dictionary.setStatus(SysDict.Status.DISABLED.getValue());
        when(dictionaries.getByCode("disabled")).thenReturn(dictionary);

        assertTrue(port.listActiveTree("disabled").isEmpty());
        assertTrue(port.listActiveTree("missing").isEmpty());
        verifyNoInteractions(items);
    }

    private SysDictItem item(String code, boolean enabled, List<SysDictItem> children) {
        var item = new SysDictItem();
        item.setItemCode(code);
        item.setItemLabel(code + " label");
        item.setStatus((enabled ? SysDictItem.Status.ENABLED : SysDictItem.Status.DISABLED).getValue());
        item.setChildren(children);
        return item;
    }
}
