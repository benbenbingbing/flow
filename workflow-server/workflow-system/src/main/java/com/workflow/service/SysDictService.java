package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.common.PageResult;
import com.workflow.entity.SysDict;
import com.workflow.entity.SysDictItem;
import com.workflow.mapper.SysDictMapper;
import com.workflow.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 字典类型服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictService {

    private final SysDictMapper dictMapper;
    private final SysDictItemMapper dictItemMapper;
    private final DictCacheService dictCacheService;

    /**
     * 分页查询字典类型
     */
    public PageResult<SysDict> getDictPage(int pageNum, int pageSize, String dictName, String dictCode) {
        Page<SysDict> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysDict> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dictName)) {
            wrapper.like(SysDict::getDictName, dictName);
        }
        if (StringUtils.hasText(dictCode)) {
            wrapper.like(SysDict::getDictCode, dictCode);
        }
        wrapper.orderByAsc(SysDict::getSort);
        Page<SysDict> resultPage = dictMapper.selectPage(page, wrapper);
        return new PageResult<>(resultPage.getRecords(), resultPage.getTotal(),
                resultPage.getCurrent(), resultPage.getSize());
    }

    /**
     * 查询所有启用的字典
     */
    public List<SysDict> getEnabledDictList() {
        return dictMapper.selectList(
                new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getStatus, SysDict.Status.ENABLED.getValue())
                        .orderByAsc(SysDict::getSort)
        );
    }

    /**
     * 根据ID查询字典
     */
    public SysDict getById(String id) {
        return dictMapper.selectById(id);
    }

    /**
     * 根据编码查询字典
     */
    public SysDict getByCode(String dictCode) {
        return dictMapper.selectOne(
                new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getDictCode, dictCode)
        );
    }

    /**
     * 保存字典类型
     */
    @Transactional(rollbackFor = Exception.class)
    public SysDict saveDict(SysDict dict) {
        // 校验字典编码唯一性
        if (StringUtils.hasText(dict.getDictCode())) {
            String excludeId = dict.getId() != null ? dict.getId() : "";
            if (dictMapper.existsDictCode(dict.getDictCode(), excludeId)) {
                throw new RuntimeException("字典编码已存在：" + dict.getDictCode());
            }
        }

        // 设置默认值
        if (!StringUtils.hasText(dict.getStatus())) {
            dict.setStatus(SysDict.Status.ENABLED.getValue());
        }
        if (dict.getSort() == null) {
            dict.setSort(0);
        }

        dict.setUpdateTime(LocalDateTime.now());

        if (!StringUtils.hasText(dict.getId())) {
            dict.setCreateTime(LocalDateTime.now());
            dictMapper.insert(dict);
            log.info("新增字典类型：{} - {}", dict.getDictCode(), dict.getDictName());
        } else {
            SysDict oldDict = dictMapper.selectById(dict.getId());
            if (oldDict == null) {
                throw new RuntimeException("字典类型不存在");
            }
            // 如果编码变更，同步更新字典项中的冗余字段
            if (!oldDict.getDictCode().equals(dict.getDictCode())) {
                updateDictCodeInItems(dict.getId(), dict.getDictCode());
                dictCacheService.reload();
            }
            dictMapper.updateById(dict);
            log.info("更新字典类型：{} - {}", dict.getDictCode(), dict.getDictName());
        }

        return dict;
    }

    @Transactional(rollbackFor = Exception.class)
    public SysDict createWithItems(SysDict dict, List<SysDictItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("代码项不能为空");
        }
        Set<String> itemCodes = new HashSet<>();
        for (SysDictItem item : items) {
            if (!StringUtils.hasText(item.getItemCode())
                    || !StringUtils.hasText(item.getItemLabel())) {
                throw new IllegalArgumentException("代码项编码和名称不能为空");
            }
            if (!itemCodes.add(item.getItemCode())) {
                throw new IllegalArgumentException("代码项编码重复：" + item.getItemCode());
            }
        }
        SysDict saved = saveDict(dict);
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < items.size(); index++) {
            SysDictItem item = items.get(index);
            item.setId(null);
            item.setDictId(saved.getId());
            item.setDictCode(saved.getDictCode());
            item.setItemValue(StringUtils.hasText(item.getItemValue())
                    ? item.getItemValue()
                    : item.getItemCode());
            item.setParentId(StringUtils.hasText(item.getParentId()) ? item.getParentId() : "0");
            item.setStatus(StringUtils.hasText(item.getStatus())
                    ? item.getStatus()
                    : SysDictItem.Status.ENABLED.getValue());
            item.setSort(item.getSort() == null ? (index + 1) * 10 : item.getSort());
            item.setCreateTime(now);
            item.setUpdateTime(now);
            dictItemMapper.insert(item);
        }
        dictCacheService.reload();
        return saved;
    }

    /**
     * 删除字典类型（逻辑删除，级联删除字典项）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDict(String id) {
        SysDict dict = dictMapper.selectById(id);
        if (dict == null) {
            throw new RuntimeException("字典类型不存在");
        }

        // 级联逻辑删除字典项
        dictItemMapper.deleteByDictId(id);

        // 逻辑删除字典类型
        dictMapper.deleteById(id);
        dictCacheService.reload();
        log.info("删除字典类型：{} - {}", dict.getDictCode(), dict.getDictName());
    }

    /**
     * 更新字典类型状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String id, String status) {
        SysDict dict = new SysDict();
        dict.setId(id);
        dict.setStatus(status);
        dict.setUpdateTime(LocalDateTime.now());
        dictMapper.updateById(dict);
    }

    /**
     * 同步更新字典项中的冗余字典编码
     */
    private void updateDictCodeInItems(String dictId, String newDictCode) {
        List<SysDictItem> items = dictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictId, dictId)
        );
        for (SysDictItem item : items) {
            item.setDictCode(newDictCode);
            dictItemMapper.updateById(item);
        }
    }
}
