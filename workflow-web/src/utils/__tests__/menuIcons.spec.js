import assert from 'node:assert/strict'
import {
  COMMON_MENU_ICON_NAMES,
  listMenuIconNames,
  normalizeMenuIconName,
  resolveMenuIcon
} from '../menuIcons.js'

assert.equal(normalizeMenuIconName('Clock'), 'Clock')
assert.equal(normalizeMenuIconName(' clock '), 'Clock')
assert.equal(normalizeMenuIconName('data-line'), 'DataLine')
assert.equal(normalizeMenuIconName('data_line'), 'DataLine')
assert.equal(normalizeMenuIconName('el-icon-s-home'), 'HomeFilled')
assert.equal(normalizeMenuIconName('missing-menu-icon'), '')

assert.ok(resolveMenuIcon('Briefcase'))
assert.ok(resolveMenuIcon('el-icon-s-tools'))
assert.equal(resolveMenuIcon(''), null)
assert.equal(
  resolveMenuIcon('missing-menu-icon', 'Menu'),
  resolveMenuIcon('Menu')
)

const allNames = listMenuIconNames()
assert.ok(allNames.includes('Clock'))
assert.ok(allNames.includes('Briefcase'))
assert.equal(new Set(allNames).size, allNames.length)
assert.ok(COMMON_MENU_ICON_NAMES.every(name => allNames.includes(name)))

console.log('menu icon tests passed')

