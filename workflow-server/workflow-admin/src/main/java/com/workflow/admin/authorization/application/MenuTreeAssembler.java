package com.workflow.admin.authorization.application;

import com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 菜单查询与角色授权共用的树组装，不扩展调用方已经筛选好的菜单范围。 */
public final class MenuTreeAssembler {
    private MenuTreeAssembler() {}

    /**
     * 按原有行为就地追加子菜单并排序根节点；孤儿节点不提升为根节点。
     * menuMap 由调用方按其重复 ID 策略建立，menus 须为本次查询的新对象。
     */
    public static List<SysMenu> assemble(List<SysMenu> menus, Map<String, SysMenu> menuMap) {
        List<SysMenu> tree = new ArrayList<>();
        
        for (SysMenu menu : menus) {
            if ("0".equals(menu.getParentId()) || menu.getParentId() == null) {
                tree.add(menu);
            } else {
                SysMenu parent = menuMap.get(menu.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(menu);
                }
            }
        }
        
        tree.sort(java.util.Comparator.comparingInt(SysMenu::getSort));
        return tree;
    }
}
