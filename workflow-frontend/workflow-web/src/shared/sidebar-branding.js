import { normalizeMenuIconName } from '../utils/menuIcons.js'

export const SIDEBAR_BRANDING_SETTING_KEY = 'ui.layout.sidebar_branding'
export const SIDEBAR_BRAND_IMAGE_MAX_BYTES = 32 * 1024
export const SIDEBAR_BRANDING_MAX_VALUE_BYTES = 48 * 1024
export const SIDEBAR_BRAND_IMAGE_TYPES = Object.freeze(['image/png', 'image/jpeg', 'image/gif', 'image/webp'])
export const DEFAULT_SIDEBAR_BRANDING = Object.freeze({ title: '流程配置系统', icon: 'Connection', imageBase64: '' })

/** 校验完整的图片 Data URL 及文件头，禁止把外链或其他内容作为系统图片保存。 */
export function validateSidebarBrandImage(value) {
  if (value === '') return ''
  if (typeof value !== 'string') throw new Error('图片 Base64 须为字符串')
  const imageBase64 = value.trim()
  if (!imageBase64) return ''
  if (imageBase64.length > 4 * Math.ceil(SIDEBAR_BRAND_IMAGE_MAX_BYTES / 3) + 32) {
    throw new Error('图片不能超过 32 KiB')
  }
  const match = /^data:image\/(png|jpeg|gif|webp);base64,([A-Za-z0-9+/]+={0,2})$/.exec(imageBase64)
  if (!match) throw new Error('请选择 PNG、JPG、GIF 或 WebP 图片')
  let bytes
  try { bytes = atob(match[2]) } catch { throw new Error('图片 Base64 格式不合法') }
  if (btoa(bytes) !== match[2]) throw new Error('图片 Base64 格式不合法')
  if (bytes.length > SIDEBAR_BRAND_IMAGE_MAX_BYTES) throw new Error('图片不能超过 32 KiB')
  const signatures = {
    png: bytes.startsWith('\x89PNG\r\n\x1a\n'),
    jpeg: bytes.startsWith('\xff\xd8\xff'),
    gif: bytes.startsWith('GIF87a') || bytes.startsWith('GIF89a'),
    webp: bytes.startsWith('RIFF') && bytes.slice(8, 12) === 'WEBP'
  }
  if (!signatures[match[1]]) throw new Error('图片内容与文件类型不一致')
  return imageBase64
}

/** 本地读取并确认浏览器能解码图片；只返回待保存的 Data URL，不上传至文件服务。 */
export async function readSidebarBrandImageFile(file) {
  if (!SIDEBAR_BRAND_IMAGE_TYPES.includes(file.type)) throw new Error('请选择 PNG、JPG、GIF 或 WebP 图片')
  if (!file.size || file.size > SIDEBAR_BRAND_IMAGE_MAX_BYTES) throw new Error('请选择不超过 32 KiB 的图片')
  const value = await new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('图片读取失败，请重新选择'))
    reader.onabort = () => reject(new Error('图片读取已取消，请重新选择'))
    reader.readAsDataURL(file)
  })
  const imageBase64 = validateSidebarBrandImage(value)
  await new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => image.naturalWidth && image.naturalHeight ? resolve() : reject(new Error('图片内容无效'))
    image.onerror = () => reject(new Error('图片无法显示，请重新选择'))
    image.src = imageBase64
  })
  return imageBase64
}

/** 读取时逐项兜底，避免旧服务或不可用图标导致导航标识消失；名称始终作为纯文本展示。 */
export function normalizeSidebarBranding(value) {
  const title = typeof value?.title === 'string' ? value.title.trim() : ''
  let imageBase64 = ''
  // 历史配置没有图片字段；非法手工配置也不影响名称和备用图标的正常展示。
  try { imageBase64 = validateSidebarBrandImage(value?.imageBase64 ?? '') } catch { /* 沿用内置图标。 */ }
  return {
    title: title && title.length <= 40 ? title : DEFAULT_SIDEBAR_BRANDING.title,
    icon: normalizeMenuIconName(value?.icon) || DEFAULT_SIDEBAR_BRANDING.icon,
    imageBase64
  }
}
