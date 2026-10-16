import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const LEGACY_ICON_ALIASES = Object.freeze({
  's-home': 'HomeFilled',
  's-tools': 'Tools',
  's-custom': 'UserFilled',
  's-management': 'Management',
  's-grid': 'Grid',
  's-data': 'DataAnalysis',
  's-open': 'FolderOpened',
  's-cooperation': 'Connection'
})

const iconComponents = Object.fromEntries(
  Object.entries(ElementPlusIconsVue)
    .filter(([name, component]) => name !== 'default' && component)
)

const canonicalNames = new Map(
  Object.keys(iconComponents)
    .map(name => [name.toLowerCase(), name])
)

const toPascalCase = value => value
  .split(/[-_\s]+/)
  .filter(Boolean)
  .map(part => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
  .join('')

export const COMMON_MENU_ICON_NAMES = Object.freeze([
  'HomeFilled', 'Home', 'User', 'UserFilled', 'Setting', 'Tools',
  'Document', 'Memo', 'Notebook', 'Tickets',
  'Folder', 'FolderOpened', 'FolderChecked',
  'Menu', 'Grid', 'List', 'Management',
  'DataAnalysis', 'DataLine', 'TrendCharts', 'PieChart', 'Histogram',
  'Connection', 'Link', 'Share', 'Briefcase', 'OfficeBuilding',
  'Clock', 'Timer', 'Calendar', 'Bell', 'Message',
  'Box', 'Collection', 'CollectionTag', 'Monitor', 'Service'
].filter(name => iconComponents[name]))

export const normalizeMenuIconName = iconName => {
  if (typeof iconName !== 'string' || !iconName.trim()) {
    return ''
  }

  const rawName = iconName.trim()
  const withoutPrefix = rawName
    .replace(/^el-icon-/i, '')
    .replace(/^icon-/i, '')
  const alias = LEGACY_ICON_ALIASES[withoutPrefix.toLowerCase()]
  const candidates = [
    rawName,
    withoutPrefix,
    alias,
    toPascalCase(withoutPrefix)
  ].filter(Boolean)

  for (const candidate of candidates) {
    if (iconComponents[candidate]) {
      return candidate
    }
    const canonicalName = canonicalNames.get(candidate.toLowerCase())
    if (canonicalName) {
      return canonicalName
    }
  }
  return ''
}

export const resolveMenuIcon = (iconName, fallbackName = '') => {
  const normalizedName = normalizeMenuIconName(iconName)
  if (normalizedName) {
    return iconComponents[normalizedName]
  }
  if (typeof iconName === 'string' && iconName.trim() && fallbackName) {
    return iconComponents[normalizeMenuIconName(fallbackName)] || null
  }
  return null
}

export const listMenuIconNames = () => Object.keys(iconComponents)
  .sort((left, right) => left.localeCompare(right))

