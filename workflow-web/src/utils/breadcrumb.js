const normalizePath = value => {
  const path = String(value || '').split(/[?#]/, 1)[0]
  if (!path) return ''
  return path.length > 1 ? path.replace(/\/+$/, '') : path
}

const menuPath = menu => normalizePath(menu?.path)

export const findDeepestMenuChain = (menus, targetPath, parents = []) => {
  const target = normalizePath(targetPath)
  if (!target || !Array.isArray(menus)) return null

  let matchedChain = null
  for (const menu of menus) {
    const chain = [...parents, menu]
    const childMatch = findDeepestMenuChain(menu.children, target, chain)
    if (childMatch) {
      matchedChain = childMatch
      continue
    }
    if (menuPath(menu) === target) {
      matchedChain = chain
    }
  }
  return matchedChain
}

const findLongestPrefixMenuChain = (menus, targetPath, parents = []) => {
  const target = normalizePath(targetPath)
  if (!target || !Array.isArray(menus)) return null

  let bestMatch = null
  for (const menu of menus) {
    const chain = [...parents, menu]
    const currentPath = menuPath(menu)
    if (currentPath && target.startsWith(`${currentPath}/`)) {
      bestMatch = chain
    }
    const childMatch = findLongestPrefixMenuChain(menu.children, target, chain)
    if (childMatch) {
      const childPathLength = menuPath(childMatch.at(-1)).length
      const bestPathLength = menuPath(bestMatch?.at(-1)).length
      if (
        !bestMatch
        || childPathLength > bestPathLength
        || (childPathLength === bestPathLength && childMatch.length > bestMatch.length)
      ) {
        bestMatch = childMatch
      }
    }
  }
  return bestMatch
}

export const getActiveMenuPath = route =>
  normalizePath(route?.meta?.activeMenu || route?.path)

export const buildBreadcrumb = (menus, route) => {
  const currentPath = normalizePath(route?.path)
  if (!currentPath) return []

  const exactChain = findDeepestMenuChain(menus, currentPath)
  if (exactChain) return exactChain

  const activeMenuPath = getActiveMenuPath(route)
  const baseChain = findDeepestMenuChain(menus, activeMenuPath)
    || findLongestPrefixMenuChain(menus, currentPath)
    || []
  const title = String(route?.meta?.title || '').trim()

  if (!title) return baseChain
  const lastItem = baseChain.at(-1)
  if (lastItem?.menuName === title) return baseChain

  return [
    ...baseChain,
    {
      id: `route:${String(route?.name || currentPath)}`,
      menuName: title,
      path: ''
    }
  ]
}
