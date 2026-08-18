import assert from 'node:assert/strict'
import {
  applyPermissionTransferChange,
  collectNewlyAssignedScopeBypass,
  flattenPermissionMenuTree
} from '../role-permission-transfer.js'

const options = flattenPermissionMenuTree([
  {
    id: 'entity-root',
    menuName: '实体数据权限',
    menuType: 'M',
    children: [
      {
        id: 'zdwreq-perms',
        menuName: 'zdw-需求管理权限',
        menuType: 'C',
        children: [
          { id: 'zdwreq-list', menuName: '查询列表', menuType: 'F', perm: 'entity:zdwreq:list' },
          { id: 'zdwreq-bypass', menuName: '绕过数据范围', menuType: 'F', perm: 'entity:zdwreq:scope:bypass' }
        ]
      }
    ]
  }
])

const assignedFromParent = applyPermissionTransferChange([], 'right', ['zdwreq-perms'], options)
assert.deepEqual(assignedFromParent, [
  'entity-root',
  'zdwreq-perms',
  'zdwreq-list',
  'zdwreq-bypass'
])

const newlyAssigned = collectNewlyAssignedScopeBypass([], assignedFromParent, options)
assert.equal(newlyAssigned.length, 1)
assert.equal(newlyAssigned[0].id, 'zdwreq-bypass')

const alreadyAssigned = collectNewlyAssignedScopeBypass(
  assignedFromParent,
  assignedFromParent,
  options
)
assert.equal(alreadyAssigned.length, 0)
