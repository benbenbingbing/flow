import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import {
  FLOW_EMBED_CAPABILITIES,
  FLOW_EMBED_COMMANDS,
  FLOW_EMBED_EVENTS,
  FLOW_EMBED_PROTOCOL,
  MAX_MESSAGE_BYTES
} from '../packages/flow-embed-sdk/src/index.js'
import { FLOW_EMBED_SELECTION_CONSTRAINTS } from '../packages/flow-embed-sdk/src/protocol.js'

const openApiPath = fileURLToPath(new URL('../../docs/api/embed-v1.yaml', import.meta.url))
const declarationPath = fileURLToPath(new URL('../packages/flow-embed-sdk/types.d.ts', import.meta.url))
const checkOnly = process.argv.includes('--check')

const source = await readFile(openApiPath, 'utf8')
const sourceLines = source.split(/\r?\n/)

/**
 * 解析生成器所需的 OpenAPI YAML 子集。这里故意不实现完整 YAML：只接受当前契约使用的
 * 缩进 Map/Sequence、标量和块文本；遇到不支持的结构立即失败，避免静默生成宽松类型。
 */
function parseYamlSubset(rawLines) {
  const lines = rawLines
    .map(raw => ({
      indent: raw.match(/^ */)?.[0].length || 0,
      text: raw.trim()
    }))
    .filter(line => line.text && !line.text.startsWith('#'))

  function scalar(raw) {
    const value = raw.trim()
    if ((value.startsWith("'") && value.endsWith("'"))
      || (value.startsWith('"') && value.endsWith('"'))) {
      return value.slice(1, -1)
    }
    if (value === 'true') return true
    if (value === 'false') return false
    if (value === 'null') return null
    if (/^-?\d+(?:\.\d+)?$/.test(value)) return Number(value)
    return value
  }

  function splitEntry(text) {
    const colon = text.indexOf(':')
    assert.notEqual(colon, -1, `不支持的 YAML 行: ${text}`)
    return [text.slice(0, colon).trim(), text.slice(colon + 1).trim()]
  }

  function parseBlock(start, indent) {
    assert.equal(lines[start]?.indent, indent, `YAML 缩进异常: ${lines[start]?.text || '<eof>'}`)
    return lines[start].text.startsWith('- ')
      ? parseSequence(start, indent)
      : parseMap(start, indent)
  }

  function parseMap(start, indent) {
    const value = {}
    let index = start
    while (index < lines.length && lines[index].indent === indent
      && !lines[index].text.startsWith('- ')) {
      const [key, rawValue] = splitEntry(lines[index].text)
      index += 1
      if (/^[|>]-?$/.test(rawValue)) {
        const block = []
        while (index < lines.length && lines[index].indent > indent) {
          block.push(lines[index].text)
          index += 1
        }
        value[key] = block.join('\n')
      } else if (!rawValue && index < lines.length && lines[index].indent > indent) {
        const child = parseBlock(index, lines[index].indent)
        value[key] = child.value
        index = child.index
      } else {
        value[key] = rawValue ? scalar(rawValue) : null
      }
    }
    return { value, index }
  }

  function parseSequence(start, indent) {
    const value = []
    let index = start
    while (index < lines.length && lines[index].indent === indent
      && lines[index].text.startsWith('- ')) {
      const rawItem = lines[index].text.slice(2).trim()
      index += 1
      if (!rawItem) {
        assert.ok(index < lines.length && lines[index].indent > indent, '空 YAML 数组项缺少内容')
        const child = parseBlock(index, lines[index].indent)
        value.push(child.value)
        index = child.index
        continue
      }
      if (!rawItem.includes(':')) {
        value.push(scalar(rawItem))
        continue
      }

      const [key, rawValue] = splitEntry(rawItem)
      const item = { [key]: rawValue ? scalar(rawValue) : null }
      if (!rawValue && index < lines.length && lines[index].indent > indent) {
        const child = parseBlock(index, lines[index].indent)
        item[key] = child.value
        index = child.index
      }
      if (index < lines.length && lines[index].indent > indent) {
        const child = parseBlock(index, lines[index].indent)
        assert.equal(Array.isArray(child.value), false, `数组对象必须是 Map: ${rawItem}`)
        Object.assign(item, child.value)
        index = child.index
      }
      value.push(item)
    }
    return { value, index }
  }

  assert.ok(lines.length > 0, 'OpenAPI 子段不能为空')
  const parsed = parseBlock(0, lines[0].indent)
  assert.equal(parsed.index, lines.length, `存在未解析的 YAML: ${lines[parsed.index]?.text}`)
  return parsed.value
}

function extractTopLevelSection(name) {
  const start = sourceLines.findIndex(line => line === `${name}:`)
  assert.notEqual(start, -1, `OpenAPI 缺少 ${name}`)
  let end = start + 1
  while (end < sourceLines.length && (!sourceLines[end].trim() || /^\s/.test(sourceLines[end]))) end += 1
  return parseYamlSubset(sourceLines.slice(start + 1, end))
}

function extractComponentSchema(name) {
  const marker = `    ${name}:`
  const schemasStart = sourceLines.findIndex(line => line === '  schemas:')
  assert.notEqual(schemasStart, -1, 'OpenAPI 缺少 components.schemas')
  const start = sourceLines.findIndex((line, index) => index > schemasStart && line === marker)
  assert.notEqual(start, -1, `OpenAPI 缺少 components.schemas.${name}`)
  let end = start + 1
  while (end < sourceLines.length) {
    const line = sourceLines[end]
    if (/^    \S[^:]*:$/.test(line)) break
    end += 1
  }
  return parseYamlSubset(sourceLines.slice(start + 1, end))
}

const boundary = extractTopLevelSection('x-flow-v1-boundary')
const messageProtocol = extractTopLevelSection('x-flow-embed-message-protocol')
const capabilities = boundary.capabilities
const commands = messageProtocol.hostCommands
const eventDefinitions = messageProtocol.iframeEvents

assert.ok(Array.isArray(capabilities) && capabilities.length > 0, 'V1 capabilities 必须是非空数组')
assert.ok(Array.isArray(commands) && commands.length > 0, 'hostCommands 必须是非空数组')
assert.ok(Array.isArray(eventDefinitions) && eventDefinitions.length > 0, 'iframeEvents 必须是非空数组')
for (const event of eventDefinitions) {
  assert.equal(typeof event.type, 'string', 'iframeEvents.type 必须是字符串')
  assert.match(event.payloadSchema?.$ref || '', /^#\/components\/schemas\/[A-Za-z][A-Za-z0-9]*$/)
}

const runtimeEvents = Object.values(FLOW_EMBED_EVENTS)
assert.deepEqual(Object.values(FLOW_EMBED_COMMANDS), commands, 'SDK 命令常量与 OpenAPI hostCommands 漂移')
assert.equal(new Set(runtimeEvents).size, runtimeEvents.length, 'SDK 事件常量不得重复')
assert.deepEqual(
  [...runtimeEvents].sort(),
  eventDefinitions.map(event => event.type).sort(),
  'SDK 事件常量与 OpenAPI iframeEvents 漂移'
)
assert.deepEqual([...FLOW_EMBED_CAPABILITIES], capabilities, 'SDK 能力常量与 OpenAPI capabilities 漂移')
assert.equal(FLOW_EMBED_PROTOCOL, messageProtocol.protocol, 'SDK 协议版本与 OpenAPI 漂移')
assert.equal(MAX_MESSAGE_BYTES, messageProtocol.maxMessageBytes, 'SDK 消息上限与 OpenAPI 漂移')

const schemaNames = new Set([
  'SurfaceType',
  'V1Capability',
  'RecordId',
  'ScalarValue',
  'ClientValue',
  'ProjectedValues',
  'HostRecordProjection',
  ...eventDefinitions.map(event => event.payloadSchema.$ref.split('/').at(-1))
])
const schemas = Object.fromEntries([...schemaNames].map(name => [name, extractComponentSchema(name)]))
const scalarStringSchema = schemas.ScalarValue.oneOf?.find(item => item.type === 'string')
const clientArraySchema = schemas.ClientValue.oneOf?.find(item => item.type === 'array')
assert.equal(
  scalarStringSchema?.maxLength,
  FLOW_EMBED_SELECTION_CONSTRAINTS.scalarStringMaxLength,
  'SDK ScalarValue 字符串上限与 OpenAPI 漂移'
)
assert.equal(
  clientArraySchema?.maxItems,
  FLOW_EMBED_SELECTION_CONSTRAINTS.clientArrayMaxItems,
  'SDK ClientValue 数组上限与 OpenAPI 漂移'
)
assert.equal(
  schemas.RecordId.maxLength,
  FLOW_EMBED_SELECTION_CONSTRAINTS.recordIdMaxLength,
  'SDK RecordId 长度上限与 OpenAPI 漂移'
)
assert.equal(
  schemas.RecordId.pattern,
  FLOW_EMBED_SELECTION_CONSTRAINTS.recordIdPattern,
  'SDK RecordId pattern 与 OpenAPI 漂移'
)

const publicName = name => ({
  SurfaceType: 'FlowEmbedSurfaceType',
  V1Capability: 'FlowEmbedCapability',
  RecordId: 'FlowEmbedRecordId',
  ScalarValue: 'FlowEmbedScalarValue',
  ClientValue: 'FlowEmbedClientValue',
  ProjectedValues: 'FlowEmbedProjectedValues'
}[name] || name)

const literal = value => typeof value === 'string' ? JSON.stringify(value) : String(value)

function schemaType(schema, depth = 0) {
  assert.ok(schema && typeof schema === 'object' && !Array.isArray(schema), 'Schema 必须是对象')
  if (schema.$ref) return publicName(schema.$ref.split('/').at(-1))
  if (schema.const !== undefined) return literal(schema.const)
  if (schema.enum) return schema.enum.map(literal).join(' | ')
  if (schema.oneOf) return schema.oneOf.map(item => schemaType(item, depth)).join(' | ')
  if (Array.isArray(schema.type)) return schema.type.map(type => schemaType({ ...schema, type }, depth)).join(' | ')
  if (schema.type === 'string') return 'string'
  if (schema.type === 'number' || schema.type === 'integer') return 'number'
  if (schema.type === 'boolean') return 'boolean'
  if (schema.type === 'null') return 'null'
  if (schema.type === 'array') return `ReadonlyArray<${schemaType(schema.items || {}, depth + 1)}>`
  if (schema.type === 'object') {
    if (schema.properties) {
      const required = new Set(schema.required || [])
      const prefix = '  '.repeat(depth + 1)
      const close = '  '.repeat(depth)
      const properties = Object.entries(schema.properties).map(([name, property]) =>
        `${prefix}${JSON.stringify(name)}${required.has(name) ? '' : '?'}: ${schemaType(property, depth + 1)}`)
      return `{\n${properties.join('\n')}\n${close}}`
    }
    if (schema.additionalProperties && typeof schema.additionalProperties === 'object') {
      return `Record<string, ${schemaType(schema.additionalProperties, depth + 1)}>`
    }
    return 'Record<string, unknown>'
  }
  // OpenAPI 对 action.completed.result 只声明安全 JSON 语义，没有放宽为任意宿主对象。
  if (Object.keys(schema).every(key => ['description'].includes(key))) return 'FlowEmbedJsonValue'
  assert.fail(`不支持的 OpenAPI Schema: ${JSON.stringify(schema)}`)
}

function declarationFor(name) {
  const schema = schemas[name]
  const exportedName = publicName(name)
  if (schema.type === 'object' && schema.properties) {
    const required = new Set(schema.required || [])
    const properties = Object.entries(schema.properties).map(([propertyName, property]) =>
      `  ${JSON.stringify(propertyName)}${required.has(propertyName) ? '' : '?'}: ${schemaType(property, 1)}`)
    return `export interface ${exportedName} {\n${properties.join('\n')}\n}`
  }
  return `export type ${exportedName} = ${schemaType(schema)}`
}

const commandKey = value => value.replaceAll('-', '_').replaceAll('.', '_').toUpperCase()
const eventKey = commandKey
const commandPayloads = {
  refresh: 'Record<string, never>',
  'set-theme': `{ theme: 'light' | 'dark' | 'system' }`,
  'set-locale': '{ locale: string }',
  focus: 'Record<string, never>',
  destroy: 'Record<string, never>'
}
for (const command of commands) {
  assert.ok(commandPayloads[command], `未知 hostCommand ${command}：请先定义其封闭 payload`)
}

const eventMap = eventDefinitions.map(event => {
  const schemaName = event.payloadSchema.$ref.split('/').at(-1)
  return `  ${JSON.stringify(event.type)}: ${publicName(schemaName)}`
}).join('\n')

const generated = `// Generated by scripts/generate-embed-sdk-types.mjs from docs/api/embed-v1.yaml.
// Do not edit manually. Run: npm run generate:embed-sdk-types

export const FLOW_EMBED_PROTOCOL: ${literal(messageProtocol.protocol)}
export const MAX_MESSAGE_BYTES: ${messageProtocol.maxMessageBytes}

export const FLOW_EMBED_CAPABILITIES: readonly [
${capabilities.map(value => `  ${literal(value)},`).join('\n')}
]

export const FLOW_EMBED_COMMANDS: Readonly<{
${commands.map(value => `  ${commandKey(value)}: ${literal(value)}`).join('\n')}
}>

export const FLOW_EMBED_EVENTS: Readonly<{
${eventDefinitions.map(event => `  ${eventKey(event.type)}: ${literal(event.type)}`).join('\n')}
}>

export type FlowEmbedCapability = typeof FLOW_EMBED_CAPABILITIES[number]
export type FlowEmbedCommand = typeof FLOW_EMBED_COMMANDS[keyof typeof FLOW_EMBED_COMMANDS]
export type FlowEmbedProtocolEventType = typeof FLOW_EMBED_EVENTS[keyof typeof FLOW_EMBED_EVENTS]
export type FlowEmbedEventType = FlowEmbedProtocolEventType | 'connected'

export type FlowEmbedJsonValue =
  | string
  | number
  | boolean
  | null
  | ReadonlyArray<FlowEmbedJsonValue>
  | { readonly [key: string]: FlowEmbedJsonValue }

${declarationFor('SurfaceType')}
${declarationFor('RecordId')}
${declarationFor('ScalarValue')}
${declarationFor('ClientValue')}
${declarationFor('ProjectedValues')}
${declarationFor('HostRecordProjection')}

${eventDefinitions.map(event => declarationFor(event.payloadSchema.$ref.split('/').at(-1))).join('\n\n')}

export interface FlowEmbedCommandPayloadMap {
${commands.map(command => `  ${JSON.stringify(command)}: ${commandPayloads[command]}`).join('\n')}
}

export interface FlowEmbedEventPayloadMap {
${eventMap}
}

export type FlowEmbedEventPayload = FlowEmbedEventPayloadMap[keyof FlowEmbedEventPayloadMap]

/** 兼容既有泛型事件消费方；新代码优先使用 FlowEmbedEventFor 或 FlowEmbedTypedEvent。 */
export interface FlowEmbedEvent<T = FlowEmbedEventPayload> {
  protocol: ${literal(messageProtocol.protocol)}
  type: FlowEmbedEventType
  launchId: string
  channelId: string
  messageId?: string
  requestId?: string | null
  timestamp?: string
  payload?: T
}

export interface FlowEmbedConnectedEvent {
  protocol: ${literal(messageProtocol.protocol)}
  type: 'connected'
  launchId: string
  channelId: string
}

/** SDK 在握手失败时本地合成；payload 仍严格复用 OpenAPI error Schema。 */
export interface FlowEmbedLocalErrorEvent {
  protocol: ${literal(messageProtocol.protocol)}
  type: 'error'
  launchId: string
  channelId: string
  payload: EmbedBridgeErrorPayload
}

export type FlowEmbedProtocolEvent<K extends FlowEmbedProtocolEventType = FlowEmbedProtocolEventType> = {
  protocol: ${literal(messageProtocol.protocol)}
  type: K
  launchId: string
  channelId: string
  messageId: string
  requestId?: string | null
  timestamp: string
  payload: FlowEmbedEventPayloadMap[K]
}

export type FlowEmbedEventFor<K extends FlowEmbedEventType> =
  K extends 'connected'
    ? FlowEmbedConnectedEvent
    : K extends 'error'
      ? FlowEmbedProtocolEvent<'error'> | FlowEmbedLocalErrorEvent
    : K extends FlowEmbedProtocolEventType
      ? FlowEmbedProtocolEvent<K>
      : never

export type FlowEmbedTypedEvent = {
  [K in FlowEmbedEventType]: FlowEmbedEventFor<K>
}[FlowEmbedEventType]

export interface FlowEmbedHeightOptions {
  mode?: 'auto' | 'fixed'
  min?: number
  max?: number
  initial?: number
}

export interface FlowEmbedMountOptions {
  container: { appendChild(node: HTMLIFrameElement): unknown }
  embedUrl: string
  launchId: string
  launchCode: string
  channelId: string
  targetOrigin: string
  protocol?: ${literal(messageProtocol.protocol)}
  title?: string
  height?: FlowEmbedHeightOptions
  handshakeTimeoutMs?: number
  maxMessageBytes?: number
  maxSeenMessageIds?: number
  onEvent?: (event: FlowEmbedTypedEvent) => void
  onViolation?: (error: FlowEmbedError) => void
}

export class FlowEmbedError extends Error {
  readonly errorCode: string
  constructor(message: string, errorCode?: string, cause?: unknown)
}

export class FlowEmbedWidget {
  readonly launchId: string
  readonly channelId: string
  readonly targetOrigin: string
  readonly embedUrl: string
  refresh(): string
  setTheme(theme: 'light' | 'dark' | 'system'): string
  setLocale(locale: string): string
  focus(): string
  sendCommand<K extends FlowEmbedCommand>(
    type: K,
    ...args: FlowEmbedCommandPayloadMap[K] extends Record<string, never>
      ? [payload?: FlowEmbedCommandPayloadMap[K]]
      : [payload: FlowEmbedCommandPayloadMap[K]]
  ): string
  on<K extends FlowEmbedEventType>(type: K, handler: (event: FlowEmbedEventFor<K>) => void): () => void
  on(type: '*', handler: (event: FlowEmbedTypedEvent) => void): () => void
  off<K extends FlowEmbedEventType>(type: K, handler: (event: FlowEmbedEventFor<K>) => void): void
  off(type: '*', handler: (event: FlowEmbedTypedEvent) => void): void
  destroy(): void
  getState(): 'waiting' | 'acknowledging' | 'connected' | 'failed' | 'destroyed'
}

export function mount(options: FlowEmbedMountOptions): FlowEmbedWidget
export function createSecureNonce(cryptoRef?: Crypto): string
export function normalizeTargetOrigin(value: string): string
export function normalizeEmbedUrl(value: string, targetOrigin: string, launchId: string): string

export const FlowEmbed: Readonly<{ mount: typeof mount }>
export default FlowEmbed
`

if (checkOnly) {
  const current = await readFile(declarationPath, 'utf8')
  assert.equal(
    current,
    generated,
    'Embed SDK 类型声明与 OpenAPI 不一致；请运行 npm run generate:embed-sdk-types 并提交结果'
  )
  console.log('Embed SDK OpenAPI type drift check passed')
} else {
  await writeFile(declarationPath, generated)
  console.log(`Generated ${declarationPath}`)
}
