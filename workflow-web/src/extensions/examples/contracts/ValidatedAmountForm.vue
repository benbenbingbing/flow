<!--
  独立页面示例：显式把 JS 校验器用于 Element Plus 表单，不注册也不修改系统表单。
  复制到业务页面后替换金额字段/额度；接口异常会作为校验错误显示，提交被阻止。
-->
<template>
  <el-form ref="formRef" :model="model" :rules="rules" label-width="90px">
    <el-form-item label="金额" prop="amount">
      <el-input-number v-model="model.amount" />
    </el-form-item>
    <el-button type="primary" :loading="pending" @click="submit">校验</el-button>
  </el-form>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createElementPlusValidator } from '@/extensions/contracts/validation.js'
import { AmountValidator, requiredValidator } from '@/extensions/examples/contracts/AmountValidator.js'

const model = reactive({ amount: null })
const formRef = ref(null)
const pending = ref(false)
const rules = {
  amount: [
    { validator: createElementPlusValidator(requiredValidator, getContext), trigger: 'change' },
    { validator: createElementPlusValidator(new AmountValidator(), getContext), trigger: 'change' }
  ]
}

/** 每次校验读取最新表单快照，避免规则声明时捕获旧值；整表提交也复用这些规则。 */
function getContext() {
  return { fieldCode: 'amount', field: { fieldName: '金额' }, formData: { ...model }, params: { maxAmount: 1000 } }
}

/** 明确 await 整表验证；当前示例只报告结果，实际保存逻辑应放在验证成功之后。 */
async function submit() {
  if (pending.value || !formRef.value) return
  pending.value = true
  try {
    await formRef.value.validate()
    ElMessage.success('校验通过')
  } catch {
    // 字段错误已由 el-form 展示，不将失败转为保存操作。
  } finally {
    pending.value = false
  }
}
</script>
