import assert from 'node:assert/strict'
import { projectGroupTagAssignment, restoreGroupTagAssignment, hasGroupTagFilter } from '../group-member-tags.js'
import { buildAssigneeConfig, normalizeDesignerAssigneeConfig } from '../process-config/index.js'
const form = { assigneeType:'group', candidateGroupIds:['TECH','FIN'], candidateGroups:'TECH,FIN', groupTags:{TECH:['backend','security'],FIN:[]}, isMultiInstance:false }
const projected = projectGroupTagAssignment(form)
const persisted = buildAssigneeConfig(projected)
assert.equal(persisted.assigneeType,'interface')
assert.equal(persisted.assignmentMode,'CANDIDATE')
assert.equal(persisted.assigneeValue,'')
assert.equal(persisted.resolverCode,'groupMembersByTag')
assert.deepEqual(persisted.extraParams.groups,[{groupCode:'TECH',tagIds:['backend','security']},{groupCode:'FIN',tagIds:[]}])
const restored = normalizeDesignerAssigneeConfig(persisted,{},false)
assert.equal(restored.assigneeType,'group')
assert.equal(restored.assigneeValue,'TECH,FIN')
assert.deepEqual(restored.groupTags,form.groupTags)
// 删除目录中的标签后也保留原 ID，不能在 load/save 时悄悄变成全组。
assert.deepEqual(restoreGroupTagAssignment(persisted).groupTags.TECH,['backend','security'])
assert.equal(hasGroupTagFilter({...form,groupTags:{}}),false)
const cleared = projectGroupTagAssignment({...form,...restored,candidateGroupIds:['TECH','FIN'],groupTags:{}})
assert.equal(cleared.assigneeType,'group')
assert.equal(cleared.resolverCode,'')
assert.deepEqual(cleared.extraParams,{})
assert.equal(buildAssigneeConfig(projectGroupTagAssignment({...form,isMultiInstance:true})).assignmentMode,'CANDIDATE')
assert.throws(()=>restoreGroupTagAssignment({resolverCode:'groupMembersByTag',extraParams:{groups:[{groupCode:'TECH'}]}}))
console.log('group member tag configuration tests passed')
