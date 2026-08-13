import assert from 'node:assert/strict'
import {
  FORM_RENDERER_MODE_CUSTOM,
  FORM_RENDERER_MODE_DEFAULT,
  changeFormRendererMode,
  resolveFormRendererMode,
  shouldPersistFormNodes
} from '../form-renderer-mode.js'

assert.equal(resolveFormRendererMode(''), FORM_RENDERER_MODE_DEFAULT)
assert.equal(resolveFormRendererMode('ProjectCustomForm'), FORM_RENDERER_MODE_CUSTOM)

const customModeWithoutSelection = changeFormRendererMode({
  mode: FORM_RENDERER_MODE_CUSTOM
})
assert.equal(customModeWithoutSelection.mode, FORM_RENDERER_MODE_CUSTOM)
assert.equal(customModeWithoutSelection.customComponent, '')

const defaultMode = changeFormRendererMode({
  mode: FORM_RENDERER_MODE_DEFAULT,
  customComponent: 'ProjectCustomForm'
})
assert.equal(defaultMode.customComponent, '')
assert.equal(defaultMode.lastCustomComponent, 'ProjectCustomForm')

const restoredCustomMode = changeFormRendererMode({
  mode: FORM_RENDERER_MODE_CUSTOM,
  lastCustomComponent: defaultMode.lastCustomComponent
})
assert.equal(restoredCustomMode.customComponent, 'ProjectCustomForm')

assert.equal(shouldPersistFormNodes(FORM_RENDERER_MODE_DEFAULT), true)
assert.equal(shouldPersistFormNodes(FORM_RENDERER_MODE_CUSTOM), false)

console.log('form-renderer-mode tests passed')
