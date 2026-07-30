<template>
  <el-dialog
    v-model="visible"
    :title="targetType === 'FIELD' ? '字段事件绑定' : `${ownerLabel}事件绑定`"
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
    />
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import EventBindingEditor from './EventBindingEditor.vue'

const props = defineProps({
  ownerType: { type: String, required: true },
  ownerId: { type: [String, Number], default: '' },
  ownerLabel: { type: String, default: '配置' },
  fieldOptions: { type: Array, default: () => [] },
  ownerEvents: { type: Array, default: () => [] }
})

const visible = ref(false)
const targetType = ref('OWNER')
const targetKey = ref('')
const targetName = ref('')

const allowedEvents = computed(() =>
  targetType.value === 'FIELD'
    ? ['FIELD_CHANGE', 'ENTITY_SELECTED', 'FIELD_BUTTON_CLICK']
    : props.ownerEvents.length
      ? props.ownerEvents
      : String(props.ownerType).toUpperCase() === 'LIST'
        ? [
            'LIST_LOAD', 'LIST_EXPORT', 'DETAIL_LOAD',
            'DATA_CREATE', 'DATA_UPDATE', 'DATA_DELETE', 'DATA_BATCH_DELETE'
          ]
        : [
            'DETAIL_LOAD', 'FORM_OPEN', 'FORM_SAVE', 'FORM_RESET',
            'DATA_CREATE', 'DATA_UPDATE', 'SUBFORM_LOAD', 'SUBFORM_SAVE',
            'FORM_BUTTON_CLICK'
          ])

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

defineExpose({ openOwner, openField })
</script>
