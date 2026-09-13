/**
 * 自定义组件注册中心
 *
 * 二次开发者可通过 registerCustomListComponent / registerCustomFormComponent
 * 注册自定义列表/表单组件，替代默认渲染。
 *
 * 自定义列表组件运行时契约 v2：
 *   - entityCode: 实体编码
 *   - entityDefinition: 实体定义对象
 *   - entityName: 实体名称
 *   - listConfig: 列表配置对象
 *   - listConfigFields: 列表字段配置数组
 *   - listFields: 实际显示的列表字段
 *   - queryFields: 查询字段数组
 *   - queryForm: 当前查询条件对象
 *   - dataList: 数据列表
 *   - loading: 页面加载状态
 *   - tableLoading: 表格加载状态
 *   - total: 总记录数
 *   - pageNum: 当前页码
 *   - pageSize: 每页大小
 *   - config: viewConfig.customComponentProps
 *   - runtime: reload/search/reset/create/view/edit/delete/approve/exportData、
 *              canAction/getActionReason/viewConfig 聚合对象
 *   - 兼容事件: search/reset/sizeChange/pageChange/create/view/edit/delete/approve
 *   - getStatusType: 获取状态样式 (status) => string
 *   - getStatusText: 获取状态文本 (status) => string
 *   - formatDate: 格式化日期 (dateStr) => string
 *
 * 自定义表单组件接收的 props：
 *   - form: 表单配置对象
 *   - modelValue: 表单数据对象
 *   - readonly: 是否只读
 *   - fields: 字段数组
 *   - linkageState: 联动状态对象 { visibility, disabled, required, options, values }
 *   - entityCode: 实体编码（数据录入场景）
 *   - entityDefinition: 实体定义对象（数据录入场景）
 *   - entityFields: 实体字段数组（数据录入场景）
 *   - mode: 'create' | 'edit' | 'approve' | 'view'
 *   - config: viewConfig.customComponentProps
 *   - context: 当前模式、实体、表单和记录等场景上下文
 *   - formActionSlots: 受控内嵌动作契约
 *     - version: 当前为 1
 *     - slots: { [slotKey]: FormAction[] }，只包含当前可见的 ACTION_SLOT 动作
 *     - trigger(actionKey): 仅触发宿主已解析且当前启用的动作，返回是否受理
 *   - 兼容事件 form-action: 可提交 actionKey；宿主仍会按上述白名单重新解析
 *     - context.formUniqueErrors: 当前字段唯一错误映射（兼容已有组件）
 *     - context.formUniqueness.errors: 当前字段唯一错误映射
 *     - context.formUniqueness.onFieldBlur(fieldOrCode): 输入失焦时按发布规则预检
 *     - context.formUniqueness.checkField(fieldOrCode, reason): 复合控件显式预检；
 *       reason 可为 CHANGE/BLUR/SUBMIT，实际执行仍受字段发布规则约束
 * 组件通过 update:modelValue 更新业务字段对象，并通过 defineExpose({ validate })
 * 暴露异步提交校验。
 */

import { normalizeExtensionDescriptor } from '@/shared/config-runtime'

const listRegistry = new Map()
const formRegistry = new Map()

const SHA256_CONSTANTS = [
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
  0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
  0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
  0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
  0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
  0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
  0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
  0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
  0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
]

function rotateRight(value, amount) {
  return value >>> amount | value << (32 - amount)
}

/**
 * 同步计算 SHA-256，注册过程因此无需等待 Web Crypto 异步完成。
 * 摘要用于可信的一方组件制品身份与发布漂移检测，不是脚本沙箱。
 */
function sha256(value) {
  const bytes = new TextEncoder().encode(value)
  const bitLength = bytes.length * 8
  const paddedLength = Math.ceil((bytes.length + 9) / 64) * 64
  const padded = new Uint8Array(paddedLength)
  padded.set(bytes)
  padded[bytes.length] = 0x80
  const view = new DataView(padded.buffer)
  view.setUint32(paddedLength - 8, Math.floor(bitLength / 0x100000000))
  view.setUint32(paddedLength - 4, bitLength >>> 0)

  const state = [
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
    0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
  ]
  const words = new Uint32Array(64)
  for (let offset = 0; offset < paddedLength; offset += 64) {
    for (let index = 0; index < 16; index += 1) {
      words[index] = view.getUint32(offset + index * 4)
    }
    for (let index = 16; index < 64; index += 1) {
      const previous15 = words[index - 15]
      const previous2 = words[index - 2]
      const sigma0 = rotateRight(previous15, 7)
        ^ rotateRight(previous15, 18) ^ previous15 >>> 3
      const sigma1 = rotateRight(previous2, 17)
        ^ rotateRight(previous2, 19) ^ previous2 >>> 10
      words[index] = (words[index - 16] + sigma0
        + words[index - 7] + sigma1) >>> 0
    }

    let [a, b, c, d, e, f, g, h] = state
    for (let index = 0; index < 64; index += 1) {
      const sum1 = rotateRight(e, 6) ^ rotateRight(e, 11) ^ rotateRight(e, 25)
      const choice = e & f ^ ~e & g
      const temporary1 = (h + sum1 + choice
        + SHA256_CONSTANTS[index] + words[index]) >>> 0
      const sum0 = rotateRight(a, 2) ^ rotateRight(a, 13) ^ rotateRight(a, 22)
      const majority = a & b ^ a & c ^ b & c
      const temporary2 = (sum0 + majority) >>> 0
      h = g
      g = f
      f = e
      e = (d + temporary1) >>> 0
      d = c
      c = b
      b = a
      a = (temporary1 + temporary2) >>> 0
    }
    ;[a, b, c, d, e, f, g, h].forEach((value, index) => {
      state[index] = (state[index] + value) >>> 0
    })
  }
  return state.map(value => value.toString(16).padStart(8, '0')).join('')
}

function stableArtifactMaterial(value, seen = new WeakSet()) {
  if (value === null) return 'null'
  if (typeof value === 'function') {
    return `function:${Function.prototype.toString.call(value)}`
  }
  if (typeof value !== 'object') return `${typeof value}:${String(value)}`
  if (seen.has(value)) return '[circular]'
  seen.add(value)
  const material = Array.isArray(value)
    ? `[${value.map(item => stableArtifactMaterial(item, seen)).join(',')}]`
    : `{${Object.keys(value).sort().map(key => {
        let item
        try {
          item = value[key]
        } catch {
          item = '[unreadable]'
        }
        return `${JSON.stringify(key)}:${stableArtifactMaterial(item, seen)}`
      }).join(',')}}`
  seen.delete(value)
  return material
}

function normalizeRegisteredArtifactDigest(value) {
  if (value === undefined || value === null || value === '') return ''
  const digest = String(value).trim().toLowerCase()
  if (!/^[a-f0-9]{64}$/.test(digest)) {
    throw new Error('自定义组件 artifactDigest 必须为 64 位十六进制字符串')
  }
  return digest
}

function componentArtifactDigest(descriptor, configuredDigest) {
  const explicit = normalizeRegisteredArtifactDigest(configuredDigest)
  if (explicit) return explicit
  return sha256(stableArtifactMaterial({
    name: descriptor.name,
    version: descriptor.version,
    snapshotVersion: descriptor.snapshotVersion,
    component: descriptor.component
  }))
}

function normalizeVersion(version) {
  const normalized = Number(version)
  return Number.isFinite(normalized) && normalized > 0 ? normalized : 1
}

function parseRequestedVersion(version) {
  const normalized = Number(version)
  return Number.isFinite(normalized) && normalized > 0 ? normalized : undefined
}

function registerVersionedComponent(registry, descriptor, configuredDigest) {
  descriptor.version = normalizeVersion(descriptor.version)
  descriptor.artifactDigest = componentArtifactDigest(
    descriptor,
    configuredDigest
  )
  const versions = registry.get(descriptor.name) || new Map()
  const registered = versions.get(descriptor.version)
  if (registered) {
    if (registered.artifactDigest !== descriptor.artifactDigest) {
      throw new Error(
        `自定义组件 ${descriptor.name} v${descriptor.version} 已注册不同制品摘要，请提升版本后再注册`
      )
    }
    return registered
  }
  versions.set(descriptor.version, descriptor)
  registry.set(descriptor.name, versions)
  return descriptor
}

function getVersionedDescriptor(registry, name, version, artifactDigest) {
  const versions = registry.get(name)
  if (!versions) return undefined
  let descriptor
  if (version !== undefined && version !== null && version !== '') {
    const requestedVersion = parseRequestedVersion(version)
    descriptor = requestedVersion === undefined
      ? undefined : versions.get(requestedVersion)
  } else {
    descriptor = Array.from(versions.values())
      .sort((left, right) => right.version - left.version)[0]
  }
  if (!descriptor || artifactDigest === undefined
    || artifactDigest === null || artifactDigest === '') return descriptor
  const expected = String(artifactDigest).trim().toLowerCase()
  return /^[a-f0-9]{64}$/.test(expected)
    && descriptor.artifactDigest === expected
    ? descriptor
    : undefined
}

function getVersionedOptions(registry) {
  return Array.from(registry.values())
    .map(versions => Array.from(versions.values())
      .sort((left, right) => right.version - left.version)[0])
    .sort((left, right) => left.name.localeCompare(right.name))
}

function getAllVersionedOptions(registry, name) {
  const entries = name ? [registry.get(name)].filter(Boolean) : Array.from(registry.values())
  return entries
    .flatMap(versions => Array.from(versions.values()))
    .sort((left, right) => left.name.localeCompare(right.name)
      || right.version - left.version)
    .map(({ component, ...descriptor }) => descriptor)
}

// ========== 自定义列表组件 ==========

/**
 * 注册自定义列表组件
 * @param {string} name 组件标识名
 * @param {Component} component Vue 组件
 */
export function registerCustomListComponent(name, component, metadata = {}) {
  const descriptor = normalizeExtensionDescriptor(name, component, metadata)
  return registerVersionedComponent(
    listRegistry,
    descriptor,
    metadata.artifactDigest || name?.artifactDigest
  )
}

/**
 * 获取自定义列表组件
 * @param {string} name 组件标识名
 * @returns {Component|undefined}
 */
export function getCustomListComponent(name, version, artifactDigest) {
  return getCustomListDescriptor(name, version, artifactDigest)?.component
}

/**
 * 判断自定义列表组件是否已注册
 * @param {string} name 组件标识名
 * @returns {boolean}
 */
export function hasCustomListComponent(name, version, artifactDigest) {
  return Boolean(getCustomListDescriptor(name, version, artifactDigest))
}

/**
 * 获取所有已注册的自定义列表组件名
 * @returns {string[]}
 */
export function getRegisteredCustomListNames() {
  return Array.from(listRegistry.keys())
}

export function getCustomListDescriptor(name, version, artifactDigest) {
  return getVersionedDescriptor(
    listRegistry,
    name,
    version,
    artifactDigest
  )
}

export function getCustomListComponentOptions() {
  return getVersionedOptions(listRegistry)
    .map(({ component, ...descriptor }) => descriptor)
}

/** 返回指定列表组件所有已注册版本，供发布配置显式选版。 */
export function getCustomListComponentVersionOptions(name) {
  return getAllVersionedOptions(listRegistry, name)
}

// ========== 自定义表单组件 ==========

/**
 * 注册自定义表单组件
 * @param {string} name 组件标识名
 * @param {Component} component Vue 组件
 */
export function registerCustomFormComponent(name, component, metadata = {}) {
  const descriptor = normalizeExtensionDescriptor(name, component, metadata)
  return registerVersionedComponent(
    formRegistry,
    descriptor,
    metadata.artifactDigest || name?.artifactDigest
  )
}

/**
 * 获取自定义表单组件
 * @param {string} name 组件标识名
 * @returns {Component|undefined}
 */
export function getCustomFormComponent(name, version, artifactDigest) {
  return getCustomFormDescriptor(name, version, artifactDigest)?.component
}

/**
 * 判断自定义表单组件是否已注册
 * @param {string} name 组件标识名
 * @returns {boolean}
 */
export function hasCustomFormComponent(name, version, artifactDigest) {
  return Boolean(getCustomFormDescriptor(name, version, artifactDigest))
}

/**
 * 获取所有已注册的自定义表单组件名
 * @returns {string[]}
 */
export function getRegisteredCustomFormNames() {
  return Array.from(formRegistry.keys())
}

export function getCustomFormDescriptor(name, version, artifactDigest) {
  return getVersionedDescriptor(
    formRegistry,
    name,
    version,
    artifactDigest
  )
}

export function getCustomFormComponentOptions() {
  return getVersionedOptions(formRegistry)
    .map(({ component, ...descriptor }) => descriptor)
}

/** 返回指定表单组件所有已注册版本，供发布配置显式选版。 */
export function getCustomFormComponentVersionOptions(name) {
  return getAllVersionedOptions(formRegistry, name)
}
