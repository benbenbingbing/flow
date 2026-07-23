package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.entity.SysDictItem;
import com.workflow.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 字典明细服务
 * <p>
 * 提供字典项的树形查询、增删改、状态切换及递归级联删除等能力。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictItemService {

    /** 字典项 Mapper */
    private final SysDictItemMapper dictItemMapper;
    /** 字典缓存服务，字典项变更后触发缓存重载 */
    private final DictCacheService dictCacheService;

    /**
     * 根据字典ID查询字典项树
     *
     * @param dictId 字典ID
     * @return 树形结构的字典项列表
     */
    public List<SysDictItem> getItemTreeByDictId(String dictId) {
        List<SysDictItem> allItems = dictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictId, dictId)
                        .orderByAsc(SysDictItem::getSort)
        );
        return buildItemTree(allItems);
    }

    /**
     * 根据字典编码查询字典项树
     *
     * @param dictCode 字典编码
     * @return 树形结构的字典项列表
     */
    public List<SysDictItem> getItemTreeByDictCode(String dictCode) {
        List<SysDictItem> allItems = dictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictCode, dictCode)
                        .orderByAsc(SysDictItem::getSort)
        );
        return buildItemTree(allItems);
    }

    /**
     * 根据ID查询字典项
     *
     * @param id 字典项ID
     * @return 字典项对象，不存在返回 null
     */
    public SysDictItem getById(String id) {
        return dictItemMapper.selectById(id);
    }

    /**
     * 保存字典项（新增或更新），保存后触发字典缓存重载
     *
     * @param item 字典项对象
     * @return 保存后的字典项对象
     */
    @Transactional(rollbackFor = Exception.class)
    public SysDictItem saveItem(SysDictItem item) {
        if (!StringUtils.hasText(item.getStatus())) {
            item.setStatus(SysDictItem.Status.ENABLED.getValue());
        }
        if (item.getSort() == null) {
            item.setSort(0);
        }
        if (!StringUtils.hasText(item.getParentId())) {
            item.setParentId("0");
        }

        item.setUpdateTime(LocalDateTime.now());

        if (!StringUtils.hasText(item.getId())) {
            item.setCreateTime(LocalDateTime.now());
            dictItemMapper.insert(item);
            log.info("新增字典项：{} - {}" , item.getItemCode(), item.getItemLabel());
        } else {
            dictItemMapper.updateById(item);
            log.info("更新字典项：{} - {}", item.getItemCode(), item.getItemLabel());
        }

        dictCacheService.reload();
        return item;
    }

    /**
     * 删除字典项（逻辑删除，如有子项一并处理）
     *
     * @param id 字典项ID
     * @throws RuntimeException 字典项不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(String id) {
        SysDictItem item = dictItemMapper.selectById(id);
        if (item == null) {
            throw new RuntimeException("字典项不存在");
        }

        // 递归删除所有子项
        deleteChildren(id);

        // 逻辑删除自身
        dictItemMapper.deleteById(id);
        dictCacheService.reload();
        log.info("删除字典项：{} - {}", item.getItemCode(), item.getItemLabel());
    }

    /**
     * 递归删除子项
     *
     * @param parentId 父项ID
     */
    private void deleteChildren(String parentId) {
        List<SysDictItem> children = dictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getParentId, parentId)
        );
        for (SysDictItem child : children) {
            deleteChildren(child.getId());
            dictItemMapper.deleteById(child.getId());
        }
    }

    /**
     * 更新字典项状态
     *
     * @param id     字典项ID
     * @param status 状态值：0-启用 1-禁用
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String id, String status) {
        SysDictItem item = new SysDictItem();
        item.setId(id);
        item.setStatus(status);
        item.setUpdateTime(LocalDateTime.now());
        dictItemMapper.updateById(item);
    }

    /**
     * 构建字典项树
     *
     * @param items 平铺的字典项列表
     * @return 树形结构的字典项列表
     */
    private List<SysDictItem> buildItemTree(List<SysDictItem> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        java.util.Map<String, SysDictItem> itemMap = items.stream()
                .collect(Collectors.toMap(SysDictItem::getId, item -> item));

        List<SysDictItem> tree = new ArrayList<>();

        for (SysDictItem item : items) {
            if ("0".equals(item.getParentId()) || item.getParentId() == null) {
                tree.add(item);
            } else {
                SysDictItem parent = itemMap.get(item.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(item);
                } else {
                    // 父节点不存在（可能被删除），作为顶级节点处理
                    tree.add(item);
                }
            }
        }

        tree.sort(java.util.Comparator.comparingInt(SysDictItem::getSort));
        return tree;
    }
}
