package com.workflow.entity.definition.application;

import com.workflow.core.result.PageRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDict;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.core.error.ForbiddenException;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统实体服务
 * 用于查询系统内置实体（用户、部门、角色、用户组）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemEntityService {

    private final SysUserMapper userMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysRoleMapper roleMapper;
    private final SysGroupMapper groupMapper;
    private final SysMenuMapper menuMapper;
    private final SysDictMapper dictMapper;
    private final SysDictItemMapper dictItemMapper;

    /**
     * 查询系统实体列表
     *
     * @param entityType 实体类型（USER/DEPT/ROLE/GROUP）
     * @param keyword    搜索关键词
     * @param pageNum    页码
     * @param pageSize   每页数量
     * @return 分页结果
     */
    public Map<String, Object> selectList(String entityType, String keyword, int pageNum, int pageSize) {
        requirePermission(entityType);
        PageRequest page = PageRequest.normalize(pageNum, pageSize, 10, 100);
        Selection selection = switch (entityType.toUpperCase(Locale.ROOT)) {
            case "USER" -> selectUserList(keyword, page);
            case "DEPT" -> selectDeptList(keyword, page);
            case "ROLE" -> selectRoleList(keyword, page);
            case "GROUP" -> selectGroupList(keyword, page);
            case "MENU" -> selectMenuList(keyword, page);
            case "DICT" -> selectDictList(keyword, page);
            case "DICT_ITEM" -> selectDictItemList(keyword, page);
            default -> throw new IllegalArgumentException("未知的系统实体类型: " + entityType);
        };

        Map<String, Object> result = new HashMap<>();
        result.put("records", selection.records());
        result.put("total", selection.total());
        result.put("pageNum", page.pageNumber());
        result.put("pageSize", page.pageSize());
        return result;
    }

    /**
     * 根据ID查询系统实体
     */
    public Map<String, Object> selectById(String entityType, String id) {
        requirePermission(entityType);
        if (id == null || id.isEmpty()) {
            return null;
        }

        switch (entityType.toUpperCase()) {
            case "USER":
                SysUser user = userMapper.selectById(id);
                return user != null ? convertUser(user) : null;
            case "DEPT":
                SysOrganization dept = organizationMapper.selectById(id);
                return dept != null ? convertDept(dept) : null;
            case "ROLE":
                SysRole role = roleMapper.selectById(id);
                return role != null ? convertRole(role) : null;
            case "GROUP":
                SysGroup group = groupMapper.selectById(id);
                return group != null ? convertGroup(group) : null;
            case "MENU":
                SysMenu menu = menuMapper.selectById(id);
                return menu != null ? convertMenu(menu) : null;
            case "DICT":
                SysDict dict = dictMapper.selectById(id);
                return dict != null ? convertDict(dict) : null;
            case "DICT_ITEM":
                SysDictItem dictItem =
                        dictItemMapper.selectById(id);
                return dictItem != null
                        ? convertDictItem(dictItem) : null;
            default:
                return null;
        }
    }

    /**
     * 批量查询系统实体
     */
    public List<Map<String, Object>> selectBatch(String entityType, List<String> ids) {
        return selectBatch(entityType, ids, "id");
    }

    /**
     * 按 ID 或业务编码批量查询系统实体，并保持调用方的值顺序。
     */
    public List<Map<String, Object>> selectBatch(
            String entityType,
            List<String> values,
            String valueKey) {
        requirePermission(entityType);
        List<String> normalizedValues = normalizeSelectionValues(values);
        if (normalizedValues.isEmpty()) {
            return List.of();
        }

        String normalizedValueKey = normalizeValueKey(valueKey);
        List<Map<String, Object>> records = switch (
                entityType.toUpperCase(Locale.ROOT)) {
            case "USER" -> selectUsersByValues(
                    normalizedValues,
                    normalizedValueKey);
            case "DEPT" -> selectDepartmentsByValues(
                    normalizedValues,
                    normalizedValueKey);
            case "ROLE" -> selectRolesByValues(
                    normalizedValues,
                    normalizedValueKey);
            case "GROUP" -> selectGroupsByValues(
                    normalizedValues,
                    normalizedValueKey);
            case "MENU" -> selectMenusByValues(
                    normalizedValues,
                    normalizedValueKey);
            case "DICT" -> selectDictionariesByValues(
                    normalizedValues,
                    normalizedValueKey);
            case "DICT_ITEM" -> selectDictionaryItemsByValues(
                    normalizedValues,
                    normalizedValueKey);
            default -> throw new IllegalArgumentException(
                    "未知的系统实体类型: " + entityType);
        };

        Map<String, Map<String, Object>> recordsByValue = records.stream()
                .filter(record -> record.get(normalizedValueKey) != null)
                .collect(Collectors.toMap(
                        record -> String.valueOf(
                                record.get(normalizedValueKey)),
                        record -> record,
                        (first, ignored) -> first));

        return normalizedValues.stream()
                .map(recordsByValue::get)
                .filter(Objects::nonNull)
                .toList();
    }

    // ========== 私有方法 ==========

    private List<String> normalizeSelectionValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String normalizeValueKey(String valueKey) {
        String normalized = StringUtils.hasText(valueKey)
                ? valueKey.trim().toLowerCase(Locale.ROOT)
                : "id";
        if (!Set.of("id", "code").contains(normalized)) {
            throw new IllegalArgumentException(
                    "系统实体选择器不支持的取值字段: " + valueKey);
        }
        return normalized;
    }

    private List<Map<String, Object>> selectUsersByValues(
            List<String> values,
            String valueKey) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
        if ("code".equals(valueKey)) {
            query.in(SysUser::getUsername, values);
        } else {
            query.in(SysUser::getId, values);
        }
        return userMapper.selectList(query).stream()
                .map(this::convertUser)
                .toList();
    }

    private List<Map<String, Object>> selectDepartmentsByValues(
            List<String> values,
            String valueKey) {
        LambdaQueryWrapper<SysOrganization> query =
                new LambdaQueryWrapper<>();
        if ("code".equals(valueKey)) {
            query.in(SysOrganization::getOrgCode, values);
        } else {
            query.in(SysOrganization::getId, values);
        }
        return organizationMapper.selectList(query).stream()
                .map(this::convertDept)
                .toList();
    }

    private List<Map<String, Object>> selectRolesByValues(
            List<String> values,
            String valueKey) {
        LambdaQueryWrapper<SysRole> query = new LambdaQueryWrapper<>();
        if ("code".equals(valueKey)) {
            query.in(SysRole::getRoleCode, values);
        } else {
            query.in(SysRole::getId, values);
        }
        return roleMapper.selectList(query).stream()
                .map(this::convertRole)
                .toList();
    }

    private List<Map<String, Object>> selectGroupsByValues(
            List<String> values,
            String valueKey) {
        LambdaQueryWrapper<SysGroup> query = new LambdaQueryWrapper<>();
        if ("code".equals(valueKey)) {
            query.in(SysGroup::getGroupCode, values);
        } else {
            query.in(SysGroup::getId, values);
        }
        return groupMapper.selectList(query).stream()
                .map(this::convertGroup)
                .toList();
    }

    private List<Map<String, Object>> selectMenusByValues(
            List<String> values,
            String valueKey) {
        LambdaQueryWrapper<SysMenu> query =
                new LambdaQueryWrapper<>();
        if ("code".equals(valueKey)) {
            query.in(SysMenu::getPath, values);
        } else {
            query.in(SysMenu::getId, values);
        }
        return menuMapper.selectList(query).stream()
                .map(this::convertMenu)
                .toList();
    }

    private List<Map<String, Object>> selectDictionariesByValues(
            List<String> values,
            String valueKey) {
        LambdaQueryWrapper<SysDict> query =
                new LambdaQueryWrapper<>();
        if ("code".equals(valueKey)) {
            query.in(SysDict::getDictCode, values);
        } else {
            query.in(SysDict::getId, values);
        }
        return dictMapper.selectList(query).stream()
                .map(this::convertDict)
                .toList();
    }

    private List<Map<String, Object>> selectDictionaryItemsByValues(
            List<String> values,
            String valueKey) {
        LambdaQueryWrapper<SysDictItem> query =
                new LambdaQueryWrapper<>();
        if ("code".equals(valueKey)) {
            query.in(SysDictItem::getItemCode, values);
        } else {
            query.in(SysDictItem::getId, values);
        }
        return dictItemMapper.selectList(query).stream()
                .map(this::convertDictItem)
                .toList();
    }

    private Selection selectUserList(String keyword, PageRequest page) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getNickname, keyword));
        }
        Page<SysUser> result = userMapper.selectPage(new Page<>(page.pageNumber(), page.pageSize()), query);
        return new Selection(result.getRecords().stream().map(this::convertUser).toList(), result.getTotal());
    }

    private Selection selectDeptList(String keyword, PageRequest page) {
        LambdaQueryWrapper<SysOrganization> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(SysOrganization::getOrgName, keyword)
                    .or()
                    .like(SysOrganization::getOrgCode, keyword));
        }
        Page<SysOrganization> result = organizationMapper.selectPage(new Page<>(page.pageNumber(), page.pageSize()), query);
        return new Selection(result.getRecords().stream().map(this::convertDept).toList(), result.getTotal());
    }

    private Selection selectRoleList(String keyword, PageRequest page) {
        LambdaQueryWrapper<SysRole> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(SysRole::getRoleName, keyword)
                    .or()
                    .like(SysRole::getRoleCode, keyword));
        }
        Page<SysRole> result = roleMapper.selectPage(new Page<>(page.pageNumber(), page.pageSize()), query);
        return new Selection(result.getRecords().stream().map(this::convertRole).toList(), result.getTotal());
    }

    private Selection selectGroupList(String keyword, PageRequest page) {
        LambdaQueryWrapper<SysGroup> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(SysGroup::getGroupName, keyword)
                    .or()
                    .like(SysGroup::getGroupCode, keyword));
        }
        Page<SysGroup> result = groupMapper.selectPage(new Page<>(page.pageNumber(), page.pageSize()), query);
        return new Selection(result.getRecords().stream().map(this::convertGroup).toList(), result.getTotal());
    }

    private Selection selectMenuList(
            String keyword,
            PageRequest page) {
        LambdaQueryWrapper<SysMenu> query =
                new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(wrapper ->
                    wrapper.like(SysMenu::getMenuName, keyword)
                            .or()
                            .like(SysMenu::getPath, keyword)
                            .or()
                            .like(SysMenu::getPerm, keyword));
        }
        Page<SysMenu> result = menuMapper.selectPage(
                new Page<>(page.pageNumber(), page.pageSize()),
                query);
        return new Selection(
                result.getRecords().stream()
                        .map(this::convertMenu).toList(),
                result.getTotal());
    }

    private Selection selectDictList(
            String keyword,
            PageRequest page) {
        LambdaQueryWrapper<SysDict> query =
                new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(wrapper ->
                    wrapper.like(SysDict::getDictName, keyword)
                            .or()
                            .like(SysDict::getDictCode, keyword));
        }
        Page<SysDict> result = dictMapper.selectPage(
                new Page<>(page.pageNumber(), page.pageSize()),
                query);
        return new Selection(
                result.getRecords().stream()
                        .map(this::convertDict).toList(),
                result.getTotal());
    }

    private Selection selectDictItemList(
            String keyword,
            PageRequest page) {
        LambdaQueryWrapper<SysDictItem> query =
                new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(wrapper ->
                    wrapper.like(
                                    SysDictItem::getItemLabel,
                                    keyword)
                            .or()
                            .like(
                                    SysDictItem::getItemCode,
                                    keyword)
                            .or()
                            .like(
                                    SysDictItem::getItemValue,
                                    keyword));
        }
        Page<SysDictItem> result = dictItemMapper.selectPage(
                new Page<>(page.pageNumber(), page.pageSize()),
                query);
        return new Selection(
                result.getRecords().stream()
                        .map(this::convertDictItem).toList(),
                result.getTotal());
    }

    private record Selection(List<Map<String, Object>> records, long total) {
    }

    // ========== 转换方法 ==========

    private Map<String, Object> convertUser(SysUser user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getNickname() != null ? user.getNickname() : user.getUsername());
        map.put("code", user.getUsername());
        map.put("status", user.getStatus());
        map.put("entityType", "USER");
        return map;
    }

    private Map<String, Object> convertDept(SysOrganization dept) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", dept.getId());
        map.put("name", dept.getOrgName());
        map.put("code", dept.getOrgCode());
        map.put("status", dept.getStatus());
        map.put("entityType", "DEPT");
        return map;
    }

    private Map<String, Object> convertRole(SysRole role) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", role.getId());
        map.put("name", role.getRoleName());
        map.put("code", role.getRoleCode());
        map.put("status", role.getStatus());
        map.put("entityType", "ROLE");
        return map;
    }

    private Map<String, Object> convertGroup(SysGroup group) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", group.getId());
        map.put("name", group.getGroupName());
        map.put("code", group.getGroupCode());
        map.put("entityType", "GROUP");
        return map;
    }

    private Map<String, Object> convertMenu(SysMenu menu) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", menu.getId());
        map.put("name", menu.getMenuName());
        map.put("code", StringUtils.hasText(menu.getPath())
                ? menu.getPath() : menu.getPerm());
        map.put("status", menu.getStatus());
        map.put("entityType", "MENU");
        return map;
    }

    private Map<String, Object> convertDict(SysDict dict) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", dict.getId());
        map.put("name", dict.getDictName());
        map.put("code", dict.getDictCode());
        map.put("status", dict.getStatus());
        map.put("entityType", "DICT");
        return map;
    }

    private Map<String, Object> convertDictItem(
            SysDictItem item) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", item.getId());
        map.put("name", item.getItemLabel());
        map.put("code", item.getItemCode());
        map.put("status", item.getStatus());
        map.put("entityType", "DICT_ITEM");
        return map;
    }

    private void requirePermission(String entityType) {
        String permission = switch (
                entityType.toUpperCase(Locale.ROOT)) {
            case "USER", "GROUP" -> "system:user:view";
            case "DEPT" -> "system:organization:view";
            case "ROLE" -> "system:role:view";
            case "MENU" -> "system:menu:view";
            case "DICT", "DICT_ITEM" ->
                    "system:dictionary:view";
            default -> throw new IllegalArgumentException(
                    "未知的系统实体类型: " + entityType);
        };
        Set<String> permissions =
                PermissionUtil.getCurrentUserPermissions();
        if (!permissions.contains("*")
                && !permissions.contains(permission)) {
            throw new ForbiddenException(
                    "没有权限查看系统引用数据: "
                            + permission);
        }
    }
}
