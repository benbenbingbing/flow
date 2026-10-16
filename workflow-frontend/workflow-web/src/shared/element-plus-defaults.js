import { ElDialog, ElDrawer } from 'element-plus'

/**
 * 统一配置应用内弹出面板的交互默认值。
 * 遮罩点击不代表用户明确放弃当前操作，尤其是编辑表单时可能造成内容丢失；
 * 主应用与独立 Embed 入口都必须在挂载前调用本方法。
 */
export function configureElementPlusPopupDefaults() {
  ElDialog.setPropsDefaults({ closeOnClickModal: false })
  ElDrawer.setPropsDefaults({ closeOnClickModal: false })
}
