import assert from 'node:assert/strict'
import {
  normalizeExternalSystemParameters,
  validateExternalSystemParameters
} from '../external-system-model.js'

assert.equal(validateExternalSystemParameters([]), '')
assert.equal(validateExternalSystemParameters([
  { nameZh: '客户端标识', nameEn: 'client.id', value: 'client-a' },
  { nameZh: '接口版本', nameEn: 'api-version', value: 'v1' },
  { nameZh: '租户编码', nameEn: 'tenant_code', value: 'tenant-a' }
]), '')

assert.match(
  validateExternalSystemParameters([
    { nameZh: '客户端标识', nameEn: 'ClientId', value: 'client-a' },
    { nameZh: '重复标识', nameEn: 'clientid', value: 'client-b' }
  ]),
  /忽略大小写/
)
assert.match(
  validateExternalSystemParameters([{ nameZh: '错误参数', nameEn: '1client', value: 'client-a' }]),
  /英文名不合法/
)
assert.match(
  validateExternalSystemParameters([{ nameZh: '', nameEn: 'clientId', value: 'client-a' }]),
  /缺少中文名/
)
assert.match(
  validateExternalSystemParameters([{ nameZh: '空参数', nameEn: 'emptyValue', value: '  ' }]),
  /缺少参数值/
)
assert.match(
  validateExternalSystemParameters([{
    nameZh: '超长参数',
    nameEn: 'oversizedValue',
    value: 'a'.repeat(65536)
  }]),
  /65535/
)
assert.match(
  validateExternalSystemParameters(Array.from({ length: 201 }, (_, index) => ({
    nameZh: `参数${index}`,
    nameEn: `parameter${index}`,
    value: 'value'
  }))),
  /最多配置 200 个参数/
)

assert.deepEqual(normalizeExternalSystemParameters([
  {
    id: 'parameter-1',
    nameZh: ' 客户端标识 ',
    nameEn: ' client.id ',
    value: ' value with spaces ',
    rowKey: 'local-only'
  },
  { nameZh: '租户', nameEn: 'tenant', value: null }
]), [
  {
    id: 'parameter-1',
    nameZh: '客户端标识',
    nameEn: 'client.id',
    value: ' value with spaces ',
    sortOrder: 0
  },
  {
    nameZh: '租户',
    nameEn: 'tenant',
    value: '',
    sortOrder: 1
  }
])

console.log('external system model tests passed')
