package com.workflow.admin.dictionary.application;

import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictItemMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SysDictItemValuesTest {
    @Test
    void exposesImmutableFlatValuesWithLegacyCodeFallbackAndDisabledHistory() {
        var mapper = mock(SysDictItemMapper.class);
        SysDictItem parent = new SysDictItem(); parent.setId("parent"); parent.setParentId("0");
        parent.setItemValue("01"); parent.setItemLabel("一级"); parent.setStatus("0");
        SysDictItem child = new SysDictItem(); child.setId("child"); child.setParentId("parent");
        child.setItemCode("legacy"); child.setItemLabel("历史选项"); child.setStatus("1");
        when(mapper.selectList(any())).thenReturn(List.of(parent, child));
        var service = new SysDictItemService(mapper, mock(DictCacheService.class));
        var values = service.listDictionaryValues("categories");
        assertThat(values).containsExactly(new SysDictItemService.DictionaryValue("01", "一级", true),
                new SysDictItemService.DictionaryValue("legacy", "历史选项", false));
        assertThatThrownBy(() -> values.clear()).isInstanceOf(UnsupportedOperationException.class);
        parent.setItemValue("changed");
        assertThat(values.get(0).value()).isEqualTo("01");
        verify(mapper, times(1)).selectList(any());
    }
}
