package com.workflow.entity.data.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFieldFileItemMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFieldFileItem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityFieldFileItemServiceTest {

    private final EntityFieldFileItemMapper mapper =
            mock(EntityFieldFileItemMapper.class);
    private final EntityFieldFileItemService service =
            new EntityFieldFileItemService(
                    mapper,
                    new ObjectMapper());

    @Test
    void preservesStableKeyAndAddsAliasWhenItemIsRenamed() {
        EntityFieldFileItem existing = item(
                "row-1",
                "afi_contract",
                "合同初稿",
                "[\"合同\"]");
        when(mapper.findByFieldId("field-1"))
                .thenReturn(List.of(existing));
        EntityFieldFileItem submitted = item(
                "row-1",
                "afi_attempted_rotation",
                "合同终稿",
                null);

        service.saveFileItems(
                "field-1",
                List.of(submitted));

        ArgumentCaptor<EntityFieldFileItem> captor =
                ArgumentCaptor.forClass(EntityFieldFileItem.class);
        verify(mapper).insert(captor.capture());
        EntityFieldFileItem saved = captor.getValue();
        assertEquals("afi_contract", saved.getItemKey());
        assertEquals("合同终稿", saved.getItemName());
        assertEquals(
                List.of("合同初稿", "合同"),
                parseAliases(saved.getNameAliases()));
    }

    @Test
    void matchesImportedItemByHistoricalAliasWithoutRotatingTargetKey() {
        EntityFieldFileItem target = item(
                "row-target",
                "afi_target",
                "合同初稿",
                null);
        when(mapper.findByFieldId("field-1"))
                .thenReturn(List.of(target));
        EntityFieldFileItem imported = item(
                null,
                "afi_source",
                "合同终稿",
                "[\"合同初稿\"]");

        service.saveFileItems(
                "field-1",
                List.of(imported));

        ArgumentCaptor<EntityFieldFileItem> captor =
                ArgumentCaptor.forClass(EntityFieldFileItem.class);
        verify(mapper).insert(captor.capture());
        EntityFieldFileItem saved = captor.getValue();
        assertEquals("row-target", saved.getId());
        assertEquals("afi_target", saved.getItemKey());
        assertEquals(
                List.of("合同初稿"),
                parseAliases(saved.getNameAliases()));
    }

    @Test
    void keepsPortableKeyForNewCrossEnvironmentItem() {
        when(mapper.findByFieldId("field-1"))
                .thenReturn(List.of());
        EntityFieldFileItem imported = item(
                null,
                "afi_portable",
                "项目章程",
                null);

        service.saveFileItems(
                "field-1",
                List.of(imported));

        ArgumentCaptor<EntityFieldFileItem> captor =
                ArgumentCaptor.forClass(EntityFieldFileItem.class);
        verify(mapper).insert(captor.capture());
        assertEquals(
                "afi_portable",
                captor.getValue().getItemKey());
    }

    @Test
    void generatesKeyWhenOlderClientDoesNotProvideOne() {
        when(mapper.findByFieldId("field-1"))
                .thenReturn(List.of());
        EntityFieldFileItem submitted = item(
                null,
                null,
                "项目章程",
                null);

        service.saveFileItems(
                "field-1",
                List.of(submitted));

        ArgumentCaptor<EntityFieldFileItem> captor =
                ArgumentCaptor.forClass(EntityFieldFileItem.class);
        verify(mapper).insert(captor.capture());
        assertTrue(captor.getValue().getItemKey()
                .matches("afi_[a-f0-9]{32}"));
    }

    @Test
    void rejectsCurrentNameThatConflictsWithAnotherItemsAlias() {
        when(mapper.findByFieldId("field-1"))
                .thenReturn(List.of());
        EntityFieldFileItem first = item(
                null,
                "afi_first",
                "合同终稿",
                "[\"合同\"]");
        EntityFieldFileItem second = item(
                null,
                "afi_second",
                "合同",
                null);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveFileItems(
                        "field-1",
                        List.of(first, second)));

        assertTrue(failure.getMessage().contains("合同"));
    }

    private EntityFieldFileItem item(
            String id,
            String itemKey,
            String itemName,
            String aliases) {
        EntityFieldFileItem item = new EntityFieldFileItem();
        item.setId(id);
        item.setItemKey(itemKey);
        item.setItemName(itemName);
        item.setNameAliases(aliases);
        item.setRequired(false);
        return item;
    }

    private List<String> parseAliases(String aliases) {
        try {
            return new ObjectMapper().readValue(
                    aliases,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
