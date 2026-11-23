package com.workflow.admin.dictionary.infrastructure.adapter;

import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.admin.dictionary.application.SysDictService;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDict;
import com.workflow.contracts.dictionary.model.DictionaryEntry;
import com.workflow.contracts.dictionary.port.DictionaryReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** 仅投影有效字典，供业务选择器和规则复用。 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DictionaryReadAdapter implements DictionaryReadPort {
    private final SysDictService dictionaries;
    private final SysDictItemService items;

    @Override
    public List<DictionaryEntry> listActiveTree(String dictionaryCode) {
        var dictionary = dictionaries.getByCode(dictionaryCode);
        if (dictionary == null || !SysDict.Status.ENABLED.getValue().equals(dictionary.getStatus())) return List.of();
        return activeEntries(items.getItemTreeByDictCode(dictionaryCode));
    }

    @Override
    public Optional<DictionaryEntry> findActiveItem(String dictionaryCode, String itemCode) {
        return listActiveTree(dictionaryCode).stream().flatMap(this::flatten)
                .filter(item -> item.code().equals(itemCode)).findFirst();
    }

    /** 停用父节点时隐藏整条分支，避免调用方得到无法定位的活动子项。 */
    private List<DictionaryEntry> activeEntries(List<SysDictItem> source) {
        if (source == null) return List.of();
        return source.stream().filter(item -> SysDictItem.Status.ENABLED.getValue().equals(item.getStatus()))
                .map(item -> new DictionaryEntry(item.getItemCode(),
                        StringUtils.hasText(item.getItemValue()) ? item.getItemValue() : item.getItemCode(),
                        item.getItemLabel(), activeEntries(item.getChildren()))).toList();
    }

    private Stream<DictionaryEntry> flatten(DictionaryEntry entry) {
        return Stream.concat(Stream.of(entry), entry.children().stream().flatMap(this::flatten));
    }
}
