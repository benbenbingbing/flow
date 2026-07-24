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
 * <p>
 * 提供字典类型的分页/增删改/状态切换，以及字典项的树形查询、增删改、状态切换接口。
 * </p>
 */
@RestController
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
public class SysDictController {

    /** 字典类型服务 */
    private final SysDictService dictService;
    /** 字典项服务 */
    private final SysDictItemService dictItemService;

    /**
     * 分页查询字典类型
     *
     * @param pageNum  页码，默认 1
     * @param pageSize 每页条数，默认 10
     * @param dictName 字典名称（模糊匹配，可空）
     * @param dictCode 字典编码（模糊匹配，可空）
     * @return 分页结果
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
     *
     * @return 启用状态的字典列表
     */
    @GetMapping("/list")
    public Result<List<SysDict>> list() {
        return Result.success(dictService.getEnabledDictList());
    }

    /**
     * 根据ID查询字典
     *
     * @param id 字典ID
     * @return 字典对象
     */
    @GetMapping("/{id}")
    public Result<SysDict> getById(@PathVariable String id) {
        return Result.success(dictService.getById(id));
    }

    /**
     * 新增字典类型
     *
     * @param dict 字典对象
     * @return 保存后的字典对象
     */
    @PostMapping
    public Result<SysDict> save(@RequestBody SysDict dict) {
        return Result.success(dictService.saveDict(dict));
    }

    /**
     * 创建字典类型并批量写入字典项
     *
     * @param request 包含字典类型和字典项列表的请求体
     * @return 保存后的字典类型对象
     */
    @PostMapping("/with-items")
    public Result<SysDict> createWithItems(@RequestBody DictWithItemsRequest request) {
        return Result.success(dictService.createWithItems(request.dict(), request.items()));
    }

    /**
     * 更新字典类型
     *
     * @param id   字典ID
     * @param dict 字典对象
     * @return 更新后的字典对象
     */
    @PostMapping("/{id}/update")
    public Result<SysDict> update(@PathVariable String id, @RequestBody SysDict dict) {
        dict.setId(id);
        return Result.success(dictService.saveDict(dict));
    }

    /**
     * 删除字典类型
     *
     * @param id 字典ID
     * @return 操作结果
     */
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable String id) {
        dictService.deleteDict(id);
        return Result.success();
    }

    /**
     * 更新字典类型状态
     *
     * @param id     字典ID
     * @param status 状态值（可空，优先取 query 参数）
     * @param body   请求体（status 字段作为兜底）
     * @return 操作结果
     */
    @PostMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id, 
                                     @RequestParam(required = false) String status,
                                     @RequestBody(required = false) java.util.Map<String, String> body) {
        String finalStatus = status != null ? status : (body != null ? body.get("status") : null);
        if (finalStatus == null) {
            throw new RuntimeException("status参数不能为空");
        }
        dictService.updateStatus(id, finalStatus);
        return Result.success();
    }

    // ==================== 字典项接口 ====================

    /**
     * 根据字典ID查询字典项树
     *
     * @param dictId 字典ID
     * @return 树形结构的字典项列表
     */
    @GetMapping("/item/tree/{dictId}")
    public Result<List<SysDictItem>> getItemTreeByDictId(@PathVariable String dictId) {
        return Result.success(dictItemService.getItemTreeByDictId(dictId));
    }

    /**
     * 根据字典编码查询字典项树
     *
     * @param dictCode 字典编码
     * @return 树形结构的字典项列表
     */
    @GetMapping("/item/tree/code/{dictCode}")
    public Result<List<SysDictItem>> getItemTreeByDictCode(@PathVariable String dictCode) {
        return Result.success(dictItemService.getItemTreeByDictCode(dictCode));
    }

    /**
     * 新增字典项
     *
     * @param item 字典项对象
     * @return 保存后的字典项对象
     */
    @PostMapping("/item")
    public Result<SysDictItem> saveItem(@RequestBody SysDictItem item) {
        return Result.success(dictItemService.saveItem(item));
    }

    /**
     * 更新字典项
     *
     * @param id   字典项ID
     * @param item 字典项对象
     * @return 更新后的字典项对象
     */
    @PostMapping("/item/{id}/update")
    public Result<SysDictItem> updateItem(@PathVariable String id, @RequestBody SysDictItem item) {
        item.setId(id);
        return Result.success(dictItemService.saveItem(item));
    }

    /**
     * 删除字典项
     *
     * @param id 字典项ID
     * @return 操作结果
     */
    @PostMapping("/item/{id}/delete")
    public Result<Void> deleteItem(@PathVariable String id) {
        dictItemService.deleteItem(id);
        return Result.success();
    }

    /**
     * 更新字典项状态
     *
     * @param id     字典项ID
     * @param status 状态值（可空，优先取 query 参数）
     * @param body   请求体（status 字段作为兜底）
     * @return 操作结果
     */
    @PostMapping("/item/{id}/status")
    public Result<Void> updateItemStatus(@PathVariable String id, 
                                         @RequestParam(required = false) String status,
                                         @RequestBody(required = false) java.util.Map<String, String> body) {
        String finalStatus = status != null ? status : (body != null ? body.get("status") : null);
        if (finalStatus == null) {
            throw new RuntimeException("status参数不能为空");
        }
        dictItemService.updateStatus(id, finalStatus);
        return Result.success();
    }

    /**
     * 创建字典类型+字典项的请求体
     *
     * @param dict  字典类型
     * @param items 字典项列表
     */
    public record DictWithItemsRequest(SysDict dict, List<SysDictItem> items) {
    }
}
