import { entityDataApi } from '@/api/entity.js'
import { createMemberChangeApi } from '@flow/workflow-api/memberChange'
export const { loadMemberChangeContext } = createMemberChangeApi(entityDataApi)
