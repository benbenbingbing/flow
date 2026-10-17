import { provideExtensionHost } from '@flow/workflow-extensions/host'
import { entityDataApi } from '@/api/entity.js'
import { uiEventBindingApi } from '@/api/uiConfig.js'
import { showRequestError } from '@/shared/request'
import FormFieldRendererLinkage from '@/components/FormFieldRendererLinkage.vue'

/** 管理端与 Embed 共用的能力适配；具体业务只依赖 SDK，不反向导入应用源码。 */
export function installExtensionHost(app) {
  return provideExtensionHost(app, {
    entityDataApi, uiEventBindingApi, showRequestError,
    components: { FormFieldRendererLinkage }
  })
}
