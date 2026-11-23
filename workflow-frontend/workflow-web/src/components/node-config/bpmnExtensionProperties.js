/**
 * 通过命令栈替换扩展属性，保留其他扩展及属性。禁止原地修改 businessObject
 * 内的数组或 Property：命令栈需要旧对象快照才能在撤销时恢复原值。
 * element 必须是原始 BPMN element（不能传 Vue Proxy）。缺少模型服务时返回 false。
 */
export function updateBpmnExtensionProperty(element, moddle, modeling, name, value, replaceCompatible = false) {
  const businessObject = element?.businessObject
  if (!businessObject || !moddle || !modeling) return false
  const previousExtensions = businessObject.extensionElements
  const previousValues = previousExtensions?.get('values') || []
  const previousProperties = previousValues.find(item => item.$type === 'flowable:Properties')
  const previousPropertyValues = previousProperties?.get('values') || []
  const keepValue = value !== null && value !== undefined && value !== ''
  const propertyValues = []
  let found = false
  for (const property of previousPropertyValues) {
    if (property.name !== name || (found && !replaceCompatible)) {
      propertyValues.push(property)
      continue
    }
    if (found) continue
    found = true
    if (keepValue) {
      propertyValues.push(moddle.create('flowable:Property', {
        ...attributesOf(property), name, value: String(value)
      }))
    }
  }
  if (!found && keepValue) {
    propertyValues.push(moddle.create('flowable:Property', { name, value: String(value) }))
  }
  const properties = moddle.create('flowable:Properties', {
    ...attributesOf(previousProperties), values: propertyValues
  })
  const values = previousValues.map(item => {
    if (item === previousProperties) return properties
    // 回退设置覆盖旧容器里的同名值，否则转换成 Flowable 后可能读回旧设置。
    if (replaceCompatible && ['camunda:Properties', 'flowable:Properties'].includes(item.$type)) {
      const entries = item.get('values') || []
      const remaining = entries.filter(property => property.name !== name)
      if (remaining.length !== entries.length) {
        return moddle.create(item.$type, { ...attributesOf(item), values: remaining })
      }
    }
    return item
  })
  if (!previousProperties) values.push(properties)
  const extensionElements = moddle.create('bpmn:ExtensionElements', {
    ...attributesOf(previousExtensions), values
  })
  modeling.updateProperties(element, { extensionElements })
  return true
}

// 保留 moddle 上的非结构属性（包括未知扩展属性），父对象关系由新模型维护。
function attributesOf(element) {
  return Object.fromEntries(Object.entries(element || {}).filter(([key]) =>
    !['$type', '$parent', 'values'].includes(key)))
}
