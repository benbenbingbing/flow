const buildBase = import.meta.env?.BASE_URL || '/'

// 网关构建使用相对资源路径，部署后的 HTML 基址同时供路由、整页跳转和 API 使用。
export const webBase = buildBase === './'
  ? globalThis.document?.querySelector('base[href]')?.getAttribute('href') || '/'
  : buildBase
export const webApiBase = import.meta.env?.VITE_API_BASE_URL || `${webBase}api`
