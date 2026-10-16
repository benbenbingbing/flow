import assert from 'node:assert/strict'

import {
  ensureBpmnLayout,
  getBpmnLayoutStats,
  hasCompleteBpmnDi
} from '../bpmnLayout.js'

const sourceXml = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://workflow.com/test">
  <process id="layout_test" isExecutable="true">
    <startEvent id="start"/>
    <sequenceFlow id="flow_start_task" sourceRef="start" targetRef="review"/>
    <userTask id="review" name="审核" flowable:assignee="admin" flowable:formKey="test-form"/>
    <sequenceFlow id="flow_task_end" sourceRef="review" targetRef="end"/>
    <endEvent id="end"/>
  </process>
</definitions>`

assert.equal(hasCompleteBpmnDi(sourceXml), false)

const layoutedXml = await ensureBpmnLayout(sourceXml)
const layoutedStats = getBpmnLayoutStats(layoutedXml)
assert.equal(layoutedStats.sequenceFlowIds.length, 2)
assert.equal(layoutedStats.edgeRefs.length, 2)
assert.equal(layoutedStats.shapeRefs.length, 3)
assert.equal(hasCompleteBpmnDi(layoutedXml), true)
assert.match(layoutedXml, /flowable:formKey="test-form"/)

const partialXml = layoutedXml.replace(
  /<(?:[A-Za-z_][\w.-]*:)?BPMNEdge\b[\s\S]*?<\/(?:[A-Za-z_][\w.-]*:)?BPMNEdge>/gi,
  ''
)
assert.equal(hasCompleteBpmnDi(partialXml), false)

const repairedXml = await ensureBpmnLayout(partialXml)
assert.equal(getBpmnLayoutStats(repairedXml).edgeRefs.length, 2)
assert.equal(hasCompleteBpmnDi(repairedXml), true)
assert.equal(await ensureBpmnLayout(repairedXml), repairedXml)

const missingWaypointXml = layoutedXml.replace(
  /(<bpmndi:BPMNEdge\b[^>]*\bbpmnElement="flow_start_task"[^>]*>)[\s\S]*?(<\/bpmndi:BPMNEdge>)/i,
  '$1$2'
)
assert.equal(hasCompleteBpmnDi(missingWaypointXml), false)
assert.equal(
  getBpmnLayoutStats(missingWaypointXml).edgeWaypointCounts.flow_start_task,
  0
)

const repairedWaypointXml = await ensureBpmnLayout(missingWaypointXml)
assert.equal(hasCompleteBpmnDi(repairedWaypointXml), true)
assert.ok(
  getBpmnLayoutStats(repairedWaypointXml).edgeWaypointCounts.flow_start_task >= 2
)

console.log('bpmn layout tests passed')
