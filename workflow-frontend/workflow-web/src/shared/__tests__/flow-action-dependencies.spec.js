import assert from 'node:assert/strict'
import { groupFlowActions, predecessorOptions, dependencyProblem, parseActionParams, serializeActionParams } from '../flow-action-dependencies.js'

const a = { id: 'a', processConfigId: 'p', scopeType: 'NODE', elementId: 'n', triggerTiming: 'AFTER', executionMode: 'AFTER_COMMIT', enabled: true, sortOrder: 10 }
const b = { ...a, id: 'b', predecessorActionId: 'a', sortOrder: 0 }
const c = { ...a, id: 'c', predecessorActionId: 'b' }
const before = { ...a, id: 'before', triggerTiming: 'BEFORE', executionMode: 'IN_TRANSACTION', sortOrder: 100 }
const sync = { ...a, id: 'sync', executionMode: 'IN_TRANSACTION' }
const groups = groupFlowActions([b,a,before,sync], [{ value: 'BEFORE' }, { value: 'AFTER' }])
assert.deepEqual(groups.map(g => [g.timing,g.mode]), [['BEFORE','IN_TRANSACTION'],['AFTER','IN_TRANSACTION'],['AFTER','AFTER_COMMIT']])
assert.deepEqual(groups[2].actions.map(a => a.id), ['b','a'])
assert.deepEqual(predecessorOptions([a,b,c,before,sync], b).map(a => a.id), ['a'])
assert.deepEqual(predecessorOptions([a,b,c], a), [])
assert.equal(dependencyProblem([a,b], b), '')
assert.ok(dependencyProblem([a,b], { ...b, triggerTiming: 'OTHER' }))
assert.ok(dependencyProblem([{...a,enabled:false},b], b))
const rows = [{ name: 'contractNo', type: 'predecessor', value: 'contract.no' }, { name: 'count', type: 'number', value: '2' }]
const json = serializeActionParams(rows, 'a')
assert.deepEqual(JSON.parse(json), { contractNo: { $predecessor: 'contract.no' }, count: 2 })
assert.equal(serializeActionParams(parseActionParams(json), 'a'), json)
assert.throws(() => serializeActionParams(rows, null))
assert.throws(() => serializeActionParams([{name:'x',type:'predecessor',value:'getClass()'}], 'a'))
assert.equal(serializeActionParams([{ name:'owner',type:'variable',value:'owner' }], null), '{"owner":"${owner}"}')
console.log('flow action dependency configuration passed')
