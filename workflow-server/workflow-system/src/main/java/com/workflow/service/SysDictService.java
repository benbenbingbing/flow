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
 * <p>
 * 提供字典类型的分页查询、增删改、状态切换，以及字典编码变更时同步更新字典项冗余字段等能力。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictService {

    /** 字典类型 Mapper */
    private final SysDictMapper dictMapper;
    /** 字典项 Mapper，用于级联删除字典项及同步冗余编码 */
    private final SysDictItemMapper dictItemMapper;
    /** 字典缓存服务，字典变更后触发缓存重载 */
    private final DictCacheService dictCacheService;

    /**
     * 分页查询字典类型
     *
     * @param pageNum   页码
     * @param pageSize  每页条数
     * @param dictName  字典名称（模糊匹配，可空）
     * @param dictCode  字典编码（模糊匹配，可空）
     * @return 分页结果
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
     *
     * @return 启用状态的字典列表，按排序升序
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
     *
     * @param id 字典ID
     * @return 字典对象，不存在返回 null
     */
    public SysDict getById(String id) {
        return dictMapper.selectById(id);
    }

    /**
     * 根据编码查询字典
     *
     * @param dictCode 字典编码
     * @return 字典对象，不存在返回 null
     */
    public SysDict getByCode(String dictCode) {
        return dictMapper.selectOne(
                new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getDictCode, dictCode)
        );
    }

    /**
     * 保存字典类型（新增或更新）
     *
     * @param dict 字典对象
     * @return 保存后的字典对象
     * @throws RuntimeException 字典编码已存在或字典类型不存在时抛出
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

    /**
     * 创建字典类型并批量写入字典项
     * <p>
     * 校验代码项编码/名称非空且不重复，字典项缺少 itemValue 时以 itemCode 充当，
     * 缺少 parentId 时默认 "0"，缺少 status 时默认启用，sort 为空时按 (index+1)*10 计算。
     * </p>
     *
     * @param dict  字典类型对象
     * @param items 字典项列表
     * @return 保存后的字典类型对象
     * @throws IllegalArgumentException 代码项为空、编码/名称为空或编码重复时抛出
     */
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
     *
     * @param id 字典ID
     * @throws RuntimeException 字典类型不存在时抛出
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
     *
     * @param id     字典ID
     * @param status 状态值：0-启用 1-禁用
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
     *
     * @param dictId      字典ID
     * @param newDictCode 新字典编码
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
