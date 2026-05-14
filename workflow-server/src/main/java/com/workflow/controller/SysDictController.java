package com.workflow.controller;

import com.workflow.common.PageResult;
import com.workflow.common.Result;
import com.workflow.entity.SysDict;
import com.workflow.entity.SysDictItem;
import com.workflow.service.SysDictItemService;
import com.workflow.service.SysDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典管理控制器
 */
@RestController
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictService dictService;
    private final SysDictItemService dictItemService;

    /**
     * 分页查询字典类型
     */
    @GetMapping("/page")
    public Result<PageResult<SysDict>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String dictName,
            @RequestParam(required = false) String dictCode) {
        return Result.success(dictService.getDictPage(pageNum, pageSize, dictName, dictCode));
    }

    /**
     * 查询所有启用的字典
     */
    @GetMapping("/list")
    public Result<List<SysDict>> list() {
        return Result.success(dictService.getEnabledDictList());
    }

    /**
     * 根据ID查询字典
     */
    @GetMapping("/{id}")
    public Result<SysDict> getById(@PathVariable String id) {
        return Result.success(dictService.getById(id));
    }

    /**
     * 新增字典类型
     */
    @PostMapping
    public Result<SysDict> save(@RequestBody SysDict dict) {
        return Result.success(dictService.saveDict(dict));
    }

    /**
     * 更新字典类型
     */
    @PutMapping("/{id}")
    public Result<SysDict> update(@PathVariable String id, @RequestBody SysDict dict) {
        dict.setId(id);
        return Result.success(dictService.saveDict(dict));
    }

    /**
     * 删除字典类型
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        dictService.deleteDict(id);
        return Result.success();
    }

    /**
     * 更新字典类型状态
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id, @RequestParam String status) {
        dictService.updateStatus(id, status);
        return Result.success();
    }

    // ==================== 字典项接口 ====================

    /**
     * 根据字典ID查询字典项树
     */
    @GetMapping("/item/tree/{dictId}")
    public Result<List<SysDictItem>> getItemTreeByDictId(@PathVariable String dictId) {
        return Result.success(dictItemService.getItemTreeByDictId(dictId));
    }

    /**
     * 根据字典编码查询字典项树
     */
    @GetMapping("/item/tree/code/{dictCode}")
    public Result<List<SysDictItem>> getItemTreeByDictCode(@PathVariable String dictCode) {
        return Result.success(dictItemService.getItemTreeByDictCode(dictCode));
    }

    /**
     * 新增字典项
     */
    @PostMapping("/item")
    public Result<SysDictItem> saveItem(@RequestBody SysDictItem item) {
        return Result.success(dictItemService.saveItem(item));
    }

    /**
     * 更新字典项
     */
    @PutMapping("/item/{id}")
    public Result<SysDictItem> updateItem(@PathVariable String id, @RequestBody SysDictItem item) {
        item.setId(id);
        return Result.success(dictItemService.saveItem(item));
    }

    /**
     * 删除字典项
     */
    @DeleteMapping("/item/{id}")
    public Result<Void> deleteItem(@PathVariable String id) {
        dictItemService.deleteItem(id);
        return Result.success();
    }

    /**
     * 更新字典项状态
     */
    @PutMapping("/item/{id}/status")
    public Result<Void> updateItemStatus(@PathVariable String id, @RequestParam String status) {
        dictItemService.updateStatus(id, status);
        return Result.success();
    }
}
