<template>
  <el-dialog
    v-model="visible"
    :title="targetType === 'FIELD'
      ? '字段事件绑定'
      : targetType === 'BUTTON'
        ? `${targetName || '按钮'}事件绑定`
        : `${ownerLabel}事件绑定`"
    width="1040px"
    append-to-body
    destroy-on-close
  >
    <EventBindingEditor
      :owner-type="ownerType"
      :owner-id="String(ownerId || '')"
      :target-type="targetType"
      :target-key="targetKey"
      :target-name="targetName"
      :allowed-events="allowedEvents"
      :field-options="fieldOptions"
      :title="`${ownerLabel}执行链`"
      @changed="emit('changed')"
    />
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import EventBindingEditor from './EventBindingEditor.vue'
import { eventsForScope } from './uiEventScope'

const props = defineProps({
  ownerType: { type: String, required: true },
  ownerId: { type: [String, Number], default: '' },
  ownerLabel: { type: String, default: '配置' },
  fieldOptions: { type: Array, default: () => [] },
  ownerEvents: { type: Array, default: () => [] }
})
const emit = defineEmits(['changed'])

const visible = ref(false)
const targetType = ref('OWNER')
const targetKey = ref('')
const targetName = ref('')

const allowedEvents = computed(() =>
  props.ownerEvents.length
    ? props.ownerEvents
    : eventsForScope(props.ownerType, targetType.value))

function openOwner(name = '') {
  targetType.value = 'OWNER'
  targetKey.value = ''
  targetName.value = name
  visible.value = true
}

function openField(field) {
  targetType.value = 'FIELD'
  targetKey.value = field?.fieldCode || ''
  targetName.value = field?.fieldLabel || field?.fieldName || field?.fieldCode || ''
  visible.value = true
}

function openButton(button) {
  targetType.value = 'BUTTON'
  targetKey.value = button?.key || ''
  targetName.value = button?.label || button?.key || ''
  visible.value = true
}

defineExpose({ openOwner, openField, openButton })
</script>
