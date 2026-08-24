const ownerEventGroups = {
  ENTITY: [
    {
      label: '列表生命周期',
      events: ['LIST_LOAD', 'LIST_EXPORT']
    },
    {
      label: '数据操作',
      events: [
        'DETAIL_LOAD',
        'DATA_CREATE',
        'DATA_UPDATE',
        'DATA_DELETE',
        'DATA_BATCH_DELETE'
      ]
    },
    {
      label: '表单生命周期',
      events: ['FORM_OPEN', 'FORM_SAVE', 'FORM_RESET']
    },
    {
      label: '字段默认事件',
      events: ['FIELD_CHANGE', 'ENTITY_SELECTED', 'FIELD_BUTTON_CLICK']
    },
    {
      label: '子表单默认事件',
      events: ['SUBFORM_LOAD', 'SUBFORM_SAVE']
    },
    {
      label: '列表按钮默认事件',
      events: ['TOOLBAR_BUTTON_CLICK', 'ROW_BUTTON_CLICK']
    },
    {
      label: '表单按钮默认事件',
      events: ['FORM_BUTTON_CLICK']
    }
  ],
  FORM: [
    {
      label: '表单生命周期',
      events: ['FORM_OPEN', 'FORM_SAVE', 'FORM_RESET']
    },
    {
      label: '表单数据',
      events: ['DETAIL_LOAD', 'DATA_CREATE', 'DATA_UPDATE']
    },
    {
      label: '字段默认事件',
      events: ['FIELD_CHANGE', 'ENTITY_SELECTED', 'FIELD_BUTTON_CLICK']
    },
    {
      label: '子表单默认事件',
      events: ['SUBFORM_LOAD', 'SUBFORM_SAVE']
    },
    {
      label: '表单按钮',
      events: ['FORM_BUTTON_CLICK']
    }
  ],
  LIST: [
    {
      label: '列表生命周期',
      events: ['LIST_LOAD', 'LIST_EXPORT']
    },
    {
      label: '列表数据操作',
      events: [
        'DETAIL_LOAD',
        'DATA_CREATE',
        'DATA_UPDATE',
        'DATA_DELETE',
        'DATA_BATCH_DELETE'
      ]
    },
    {
      label: '列表按钮默认事件',
      events: ['TOOLBAR_BUTTON_CLICK', 'ROW_BUTTON_CLICK']
    }
  ]
}

const targetEventGroups = {
  FIELD: [
    {
      label: '字段事件',
      events: ['FIELD_CHANGE', 'ENTITY_SELECTED', 'FIELD_BUTTON_CLICK']
    },
    {
      label: '子表字段事件',
      events: ['SUBFORM_LOAD', 'SUBFORM_SAVE']
    }
  ],
  FORM_BUTTON: [
    {
      label: '表单按钮事件',
      events: ['FORM_BUTTON_CLICK']
    }
  ],
  LIST_BUTTON: [
    {
      label: '列表按钮事件',
      events: ['TOOLBAR_BUTTON_CLICK', 'ROW_BUTTON_CLICK']
    }
  ]
}

function normalize(value) {
  return String(value || '').toUpperCase()
}

/**
 * 返回当前配置对象允许的事件分组。
 *
 * 目标级事件优先于 owner 级事件，防止表单、列表、字段和按钮共用目录时串入其他范围。
 */
export function eventGroupsForScope(ownerType, targetType = 'OWNER') {
  const owner = normalize(ownerType)
  const target = normalize(targetType || 'OWNER')
  if (target === 'FIELD') {
    return owner === 'FORM' ? targetEventGroups.FIELD : []
  }
  if (target === 'BUTTON') {
    if (owner === 'LIST') return targetEventGroups.LIST_BUTTON
    if (owner === 'FORM') return targetEventGroups.FORM_BUTTON
    return []
  }
  return ownerEventGroups[owner] || []
}

/** 展平当前范围的事件编码，用于列表检查和保存前校验。 */
export function eventsForScope(ownerType, targetType = 'OWNER') {
  return eventGroupsForScope(ownerType, targetType)
    .flatMap(group => group.events)
}
