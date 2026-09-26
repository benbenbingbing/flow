import { defineStore } from 'pinia'
import { useUserInterfacePreferenceField } from './userInterfacePreferenceField'

/** 从统一用户界面偏好读取和修改 fieldTypesCollapsed，不再独立保存布尔记录。 */
export const useFieldTypesPreferenceStore = defineStore('fieldTypesPreference', () => useUserInterfacePreferenceField('fieldTypesCollapsed'))
