import assert from 'node:assert/strict'
import { nextTick, reactive, ref, watch } from 'vue'
import {
  areSubFormValuesEqual,
  cloneSubFormValue
} from '../subform-value-sync.js'

const source = reactive([
  {
    id: 'row-1',
    name: '需求条目',
    enabled: true,
    amount: 12.5,
    tags: ['A', 'B'],
    schedule: {
      plannedDate: '2026-08-05'
    }
  }
])
const cloned = cloneSubFormValue(source)

assert.notEqual(cloned, source)
assert.notEqual(cloned[0], source[0])
assert.equal(areSubFormValuesEqual(cloned, source), true)

cloned[0].schedule.plannedDate = '2026-08-06'
assert.equal(areSubFormValuesEqual(cloned, source), false)
assert.equal(source[0].schedule.plannedDate, '2026-08-05')

assert.equal(areSubFormValuesEqual(null, []), false)
assert.equal(areSubFormValuesEqual({ value: undefined }, { value: undefined }), true)
assert.equal(
  areSubFormValuesEqual(
    { reviewedAt: new Date('2026-08-05T00:00:00Z') },
    { reviewedAt: new Date('2026-08-05T00:00:00Z') }
  ),
  true
)

const cyclic = { id: 'cyclic' }
cyclic.self = cyclic
const cyclicClone = cloneSubFormValue(cyclic)
assert.equal(cyclicClone.self, cyclicClone)
assert.equal(areSubFormValuesEqual(cyclic, cyclicClone), true)

const cyclicArray = []
cyclicArray.push(cyclicArray)
const cyclicArrayClone = cloneSubFormValue(cyclicArray)
assert.equal(cyclicArrayClone[0], cyclicArrayClone)
assert.equal(areSubFormValuesEqual(cyclicArray, cyclicArrayClone), true)

const parentValue = ref([{ id: 'row-1', title: '初始标题' }])
const localRows = ref([])
let updateCount = 0

watch(
  parentValue,
  value => {
    if (!areSubFormValuesEqual(localRows.value, value)) {
      localRows.value = cloneSubFormValue(value)
    }
  },
  { deep: true, immediate: true }
)
watch(
  localRows,
  value => {
    const snapshot = cloneSubFormValue(value)
    if (areSubFormValuesEqual(snapshot, parentValue.value)) return
    updateCount += 1
    parentValue.value = snapshot
  },
  { deep: true }
)

await nextTick()
assert.equal(updateCount, 0, '父值首次同步到子表时不应产生回声更新')

localRows.value[0].title = '用户修改'
await nextTick()
await nextTick()
assert.equal(updateCount, 1, '子表编辑应只回写父值一次')
assert.equal(parentValue.value[0].title, '用户修改')

parentValue.value[0].title = '父级外部修改'
await nextTick()
await nextTick()
assert.equal(localRows.value[0].title, '父级外部修改')
assert.equal(updateCount, 1, '父级外部修改同步到子表时不应再次回写')

const emptyParent = ref([])
const initializedRows = ref([])
let initializationUpdates = 0
watch(
  emptyParent,
  value => {
    if (!areSubFormValuesEqual(initializedRows.value, value)) {
      initializedRows.value = cloneSubFormValue(value)
    }
  },
  { deep: true, immediate: true }
)
watch(
  initializedRows,
  value => {
    const snapshot = cloneSubFormValue(value)
    if (areSubFormValuesEqual(snapshot, emptyParent.value)) return
    initializationUpdates += 1
    emptyParent.value = snapshot
  },
  { deep: true }
)
initializedRows.value.push({ title: '' })
await nextTick()
await nextTick()
assert.equal(initializationUpdates, 1, '最小行初始化应只回写一次')
assert.deepEqual(emptyParent.value, [{ title: '' }])

console.log('subform value sync tests passed')
