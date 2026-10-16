import { defineStore } from 'pinia'
import { useUserInterfacePreferenceField } from './userInterfacePreferenceField'

/** 从统一用户界面偏好读取和修改 tabsEnabled，不再独立保存布尔记录。 */
export const useTabsPreferenceStore = defineStore('tabsPreference', () => useUserInterfacePreferenceField('tabsEnabled'))
