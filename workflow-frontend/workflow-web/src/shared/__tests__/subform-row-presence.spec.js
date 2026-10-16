import assert from 'node:assert/strict'
import { hasOneToOneSubFormContent } from '../subform-row-presence.js'

const fields = [{ fieldKey: 'line_name' }]

assert.equal(hasOneToOneSubFormContent(null, fields), false)
assert.equal(hasOneToOneSubFormContent({ line_name: '' }, fields), false)
assert.equal(hasOneToOneSubFormContent({ line_name: '  ' }, fields), false)
assert.equal(hasOneToOneSubFormContent({ line_name: '明细' }, fields), true)
assert.equal(hasOneToOneSubFormContent({ id: 'existing', line_name: '' }, fields), true)
assert.equal(hasOneToOneSubFormContent({ flag: false }, [{ fieldKey: 'flag' }]), true)
assert.equal(hasOneToOneSubFormContent({ amount: 0 }, [{ fieldKey: 'amount' }]), true)
assert.equal(hasOneToOneSubFormContent({ tags: [] }, [{ fieldKey: 'tags' }]), false)
assert.equal(hasOneToOneSubFormContent({ tags: ['x'] }, [{ fieldKey: 'tags' }]), true)
