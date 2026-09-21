import request from '@/utils/request'
import { createAuthApi } from '@flow/workflow-api/auth'

export const { login, getCurrentUser, logout, changePassword, getPermissions } = createAuthApi(request)
