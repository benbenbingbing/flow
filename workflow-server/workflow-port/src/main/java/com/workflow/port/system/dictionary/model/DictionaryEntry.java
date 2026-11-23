package com.workflow.port.system.dictionary.model;

import java.util.List;

/** 字典运行时项；code 用于稳定引用，value 用于业务输入，children 保留可用层级。 */
public record DictionaryEntry(String code, String value, String label, List<DictionaryEntry> children) {
    public DictionaryEntry {
        children = List.copyOf(children);
    }
}
