import { provideExtensionHost } from '@flow/workflow-extensions/host'
import { entities } from './services.js'

/** 只提供通用实体客户端；成员变更等业务服务由选中的业务包自行组装。 */
export function installExtensionHost(app) {
  return provideExtensionHost(app, { entityDataApi: entities.entityDataApi })
}
