import assert from 'node:assert/strict'
import {
  applySelectionReturnMappings,
  SELECTION_RETURN_MAPPING_EXAMPLE
} from '../selectionReturnMappings.ts'

const originalRow = {
  id: 'record-1',
  code: 'CUSTOMER-001',
  name: '顶层名称',
  data: {
    name: '客户甲',
    region: {
      code: 'EAST'
    }
  }
}

assert.equal(
  applySelectionReturnMappings(originalRow, []),
  originalRow,
  '空映射应原样返回选中记录'
)

assert.deepEqual(
  applySelectionReturnMappings(originalRow, SELECTION_RETURN_MAPPING_EXAMPLE),
  {
    ...originalRow,
    selectionData: {
      customerId: 'record-1',
      customerName: '客户甲'
    }
  },
  'sourceField 应支持顶层字段和 data 下的自定义字段'
)

assert.deepEqual(
  applySelectionReturnMappings(originalRow, [
    {
      sourcePath: 'data.region.code',
      targetPath: 'customer.regionCode'
    },
    {
      sourceField: 'code',
      targetField: 'selectionData.customer.code'
    }
  ]).selectionData,
  {
    customer: {
      regionCode: 'EAST',
      code: 'CUSTOMER-001'
    }
  },
  '目标点路径应生成 selectionData 下的嵌套对象'
)

const multipleResults = [
  {
    id: 'record-1',
    data: {
      name: '客户甲'
    }
  },
  {
    id: 'record-2',
    data: {
      name: '客户乙'
    }
  }
].map(row => applySelectionReturnMappings(row, SELECTION_RETURN_MAPPING_EXAMPLE))

assert.deepEqual(
  multipleResults.map(row => row.selectionData),
  [
    {
      customerId: 'record-1',
      customerName: '客户甲'
    },
    {
      customerId: 'record-2',
      customerName: '客户乙'
    }
  ],
  '多选时应对每条选中记录独立应用映射'
)

console.log('selection return mapping tests passed')
