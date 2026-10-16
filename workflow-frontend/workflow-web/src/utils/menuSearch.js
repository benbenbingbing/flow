/**
 * 将当前用户已授权的侧栏菜单树转换为可跳转的搜索项。
 * 只能传入运行态侧栏数据，不能使用管理端完整菜单树或路由表替代权限来源。
 * 隐藏、禁用节点的整棵子树均不参与搜索；含子菜单的目录仅用于展示所属层级。
 * @param {Array} menus 后端按当前用户授权裁剪后的菜单树
 * @returns {Array} 包含菜单名称、所属层级及原始跳转路径的搜索项
 */
export const buildMenuSearchOptions = menus => {
  const options = []
  const paths = new Set()
  const visibleMenus = nodes => Array.isArray(nodes)
    ? nodes.filter(menu => menu && menu.status !== '1' && menu.visible !== '1' && menu.menuType !== 'F')
    : []

  const visit = (nodes, parents = []) => {
    for (const menu of visibleMenus(nodes)) {
      const menuName = String(menu.menuName || '').trim()
      const names = menuName ? [...parents, menuName] : parents
      const children = visibleMenus(menu.children)
      if (children.length) {
        visit(children, names)
        continue
      }

      const path = String(menu.path || '').trim()
      // 历史首页可能配置为目录类型，沿用侧栏“有路径的末级项可跳转”的规则。
      // 占位路径和外部地址不能交给站内路由，按钮已在遍历前过滤。
      if (!menuName || !/^\/(?!\/)/.test(path) || paths.has(path)) continue
      paths.add(path)
      options.push({
        path,
        menuName,
        parentLabel: parents.join(' / '),
        label: names.join(' / ')
      })
    }
  }

  visit(menus)
  return options
}

/**
 * 按菜单名称和所属层级匹配关键字，忽略大小写及多余空白。
 * 空格分隔的词须同时命中，便于用“用户手册 流程”等组合定位同名菜单。
 */
export const filterMenuSearchOptions = (options, keyword) => {
  const words = String(keyword || '').trim().toLocaleLowerCase().split(/\s+/).filter(Boolean)
  return options.filter(option => {
    const label = option.label.toLocaleLowerCase()
    return words.every(word => label.includes(word))
  })
}
