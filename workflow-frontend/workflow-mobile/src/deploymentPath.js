const buildBase = import.meta.env?.BASE_URL || '/m/'

// 移动端基址位于管理端的 m/ 子目录，两端通过同一个 API 路径刷新会话。
export const mobileBase = buildBase === './'
  ? globalThis.document?.querySelector('base[href]')?.getAttribute('href') || '/m/'
  : buildBase
export const webBase = mobileBase.replace(/\/m\/$/, '/')
export const apiBase = import.meta.env?.VITE_API_BASE_URL || `${webBase}api`
