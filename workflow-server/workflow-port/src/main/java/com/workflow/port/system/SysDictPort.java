package com.workflow.port.system;

import com.workflow.port.system.dictionary.model.DictionaryEntry;

import java.util.List;
import java.util.Optional;

/** 运行时只读字典目录，适用于表单选项和业务规则。 */
public interface SysDictPort {
    /** 查询有效字典的有效项树；字典不存在或停用时返回空集合。 */
    List<DictionaryEntry> listActiveTree(String dictionaryCode);

    /** 按稳定项编码查找有效字典项，可用于匹配业务输入并获取展示标签。 */
    Optional<DictionaryEntry> findActiveItem(String dictionaryCode, String itemCode);
}
