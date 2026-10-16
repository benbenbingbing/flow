import { layoutProcess } from 'bpmn-auto-layout'
import BpmnModdle from 'bpmn-moddle'

const collectMatches = (xml, pattern) => {
  const values = []
  for (const match of xml.matchAll(pattern)) {
    values.push(match[1])
  }
  return values
}

const collectEdgeWaypointCounts = (xml = '') => {
  const counts = {}
  const collect = (attributes = '', body = '') => {
    const elementMatch = attributes.match(/\bbpmnElement="([^"]+)"/i)
    if (!elementMatch) return
    const waypointCount = (
      body.match(/<(?:[A-Za-z_][\w.-]*:)?waypoint\b/gi) || []
    ).length
    counts[elementMatch[1]] = Math.max(counts[elementMatch[1]] || 0, waypointCount)
  }

  for (const match of xml.matchAll(
    /<(?:[A-Za-z_][\w.-]*:)?BPMNEdge\b([^>]*)>([\s\S]*?)<\/(?:[A-Za-z_][\w.-]*:)?BPMNEdge>/gi
  )) {
    collect(match[1], match[2])
  }
  for (const match of xml.matchAll(
    /<(?:[A-Za-z_][\w.-]*:)?BPMNEdge\b([^>]*)\/>/gi
  )) {
    collect(match[1])
  }
  return counts
}

export const getBpmnLayoutStats = (xml = '') => {
  const sequenceFlowIds = collectMatches(
    xml,
    /<(?:[A-Za-z_][\w.-]*:)?sequenceFlow\b[^>]*\bid="([^"]+)"/gi
  )
  const edgeRefs = collectMatches(
    xml,
    /<(?:[A-Za-z_][\w.-]*:)?BPMNEdge\b[^>]*\bbpmnElement="([^"]+)"/gi
  )
  const shapeRefs = collectMatches(
    xml,
    /<(?:[A-Za-z_][\w.-]*:)?BPMNShape\b[^>]*\bbpmnElement="([^"]+)"/gi
  )
  const edgeWaypointCounts = collectEdgeWaypointCounts(xml)

  return {
    sequenceFlowIds,
    edgeRefs,
    shapeRefs,
    edgeWaypointCounts
  }
}

export const hasCompleteBpmnDi = (xml = '') => {
  const {
    sequenceFlowIds,
    edgeRefs,
    shapeRefs,
    edgeWaypointCounts
  } = getBpmnLayoutStats(xml)
  if (shapeRefs.length === 0) {
    return false
  }
  if (sequenceFlowIds.length === 0) {
    return xml.includes('BPMNDiagram')
  }

  const edgeRefSet = new Set(edgeRefs)
  return sequenceFlowIds.every(flowId =>
    edgeRefSet.has(flowId) && (edgeWaypointCounts[flowId] || 0) >= 2
  )
}

const linkSequenceFlows = container => {
  const flowElements = container?.flowElements || []
  for (const element of flowElements) {
    if (element.$type === 'bpmn:SequenceFlow') {
      if (element.sourceRef && !element.sourceRef.outgoing?.includes(element)) {
        element.sourceRef.outgoing = [...(element.sourceRef.outgoing || []), element]
      }
      if (element.targetRef && !element.targetRef.incoming?.includes(element)) {
        element.targetRef.incoming = [...(element.targetRef.incoming || []), element]
      }
    }
    if (element.$type === 'bpmn:SubProcess') {
      linkSequenceFlows(element)
    }
  }
}

export const ensureBpmnLayout = async xml => {
  if (hasCompleteBpmnDi(xml)) {
    return xml
  }

  const moddle = new BpmnModdle()
  const { rootElement } = await moddle.fromXML(xml)
  const processes = (rootElement.rootElements || [])
    .filter(element => element.$type === 'bpmn:Process')

  if (processes.length === 0) {
    throw new Error('BPMN XML 中未找到可布局的流程定义')
  }

  processes.forEach(linkSequenceFlows)
  rootElement.diagrams = []

  const { xml: normalizedXml } = await moddle.toXML(rootElement, { format: true })
  const layoutedXml = await layoutProcess(normalizedXml)
  if (!hasCompleteBpmnDi(layoutedXml)) {
    const stats = getBpmnLayoutStats(layoutedXml)
    const drawableEdgeCount = stats.sequenceFlowIds.filter(
      flowId => (stats.edgeWaypointCounts[flowId] || 0) >= 2
    ).length
    throw new Error(
      `BPMN 自动布局未生成完整连线：sequenceFlow=${stats.sequenceFlowIds.length}, BPMNEdge=${stats.edgeRefs.length}, 可绘制连线=${drawableEdgeCount}`
    )
  }
  return layoutedXml
}
