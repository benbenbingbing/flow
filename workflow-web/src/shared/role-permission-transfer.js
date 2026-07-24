const MENU_TYPE_LABELS = {
  M: '目录',
  C: '菜单',
  F: '按钮'
}

const normalizeKey = (value) => String(value ?? '')

export function flattenPermissionMenuTree(menuTree = []) {
  const options = []

  const walk = (menus, ancestorIds = [], ancestorNames = []) => {
    const branchIds = []

    for (const menu of Array.isArray(menus) ? menus : []) {
      const id = normalizeKey(menu.id)
      if (!id) continue

      const menuName = menu.menuName || menu.title || '未命名权限'
      const pathNames = [...ancestorNames, menuName]
      const { children, ...menuData } = menu
      const option = {
        ...menuData,
        id,
        menuName,
        fullPath: pathNames.join(' / '),
        menuTypeLabel: MENU_TYPE_LABELS[menu.menuType] || '权限',
        ancestorIds: [...ancestorIds],
        descendantIds: [],
        searchText: [...pathNames, menu.perm].filter(Boolean).join(' ').toLowerCase()
      }

      options.push(option)
      const descendantIds = walk(children, [...ancestorIds, id], pathNames)
      option.descendantIds = descendantIds
      branchIds.push(id, ...descendantIds)
    }

    return branchIds
  }

  walk(menuTree)
  return options
}

export function sanitizePermissionKeys(keys = [], options = []) {
  const selectedKeys = new Set(keys.map(normalizeKey))
  return options
    .map(option => normalizeKey(option.id))
    .filter(id => selectedKeys.has(id))
}

export function applyPermissionTransferChange(keys, direction, movedKeys, options) {
  const optionMap = new Map(options.map(option => [normalizeKey(option.id), option]))
  const selectedKeys = new Set(sanitizePermissionKeys(keys, options))

  for (const movedKey of movedKeys || []) {
    const id = normalizeKey(movedKey)
    const option = optionMap.get(id)
    if (!option) continue

    if (direction === 'right') {
      selectedKeys.add(id)
      option.ancestorIds.forEach(parentId => selectedKeys.add(normalizeKey(parentId)))
      option.descendantIds.forEach(childId => selectedKeys.add(normalizeKey(childId)))
    } else {
      selectedKeys.delete(id)
      option.descendantIds.forEach(childId => selectedKeys.delete(normalizeKey(childId)))
    }
  }

  return options
    .map(option => normalizeKey(option.id))
    .filter(id => selectedKeys.has(id))
}
