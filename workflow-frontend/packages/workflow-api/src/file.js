import { getFileUploadIdempotencyKey } from '@flow/workflow-core/file-upload-idempotency'
export { createBrowserFileAccess } from './fileAccess.js'

/** 为指定 transport 创建接口集合，不在包内持有应用单例。 */
export function createFileApi(request) {

/** 只请求应用自己的文件接口；文件 ID 作为参数传递，令牌由宿主请求层注入。 */
async function readFile(fileId, download) {
  const response = await request.get(download ? '/file/download' : '/file/preview', {
    params: { fileId }, responseType: 'blob', returnBlobResponse: true, silentError: true
  })
  const disposition = response.headers?.['content-disposition'] || ''
  // 优先 RFC 5987 文件名，保留中文和空格；旧响应没有扩展文件名时兼容普通 filename。
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  const plain = disposition.match(/filename="((?:\\.|[^"\\])*)"|filename=([^;]+)/i)
  let filename = plain ? (plain[1] ?? plain[2]).replace(/\\(.)/g, '$1').trim() : ''
  if (encoded) {
    try { filename = decodeURIComponent(encoded[1].trim()) } catch { /* 保留普通文件名兜底。 */ }
  }
  // XHR 生成的 Blob 可能丢失 charset；重新附上响应类型，避免中文纯文本预览乱码。
  const contentType = response.headers?.['content-type']
  const blob = contentType && response.data.type !== contentType.toLowerCase()
    ? response.data.slice(0, response.data.size, contentType) : response.data
  return { blob, filename, inline: /^inline(?:;|$)/i.test(disposition) }
}

/**
 * 文件上传API
 */
const fileApi = {
  /** 通过鉴权接口获取可预览文件流和服务端原始文件名，文件 ID 由后端解析为存储位置。 */
  preview: fileId => readFile(fileId, false),
  /** 通过同一归属校验获取附件流；与预览使用不同的 Content-Disposition。 */
  download: fileId => readFile(fileId, true),
  /**
   * 上传文件
   * @param file 文件对象
   * @returns 文件信息（fileId、originalName、size）
   */
  upload(file, options = {}) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/file/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
        'Idempotency-Key': options.idempotencyKey || getFileUploadIdempotencyKey(file)
      },
      onUploadProgress: options.onUploadProgress,
      silentError: options.silentError === true
    })
  },

  /**
   * 以实体表单动作权限上传文件。
   * @param file 文件对象
   * @param context 实体编码、动作和文件字段编码
   * @param options 上传进度、幂等键等请求选项
   * @returns 文件信息
   */
  uploadForEntity(file, context, options = {}) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('action', context.action)
    formData.append('fieldCode', context.fieldCode)
    const entityCode = encodeURIComponent(context.entityCode)
    return request.post(`/file/entity/${entityCode}/upload`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
        'Idempotency-Key': options.idempotencyKey || getFileUploadIdempotencyKey(file)
      },
      onUploadProgress: options.onUploadProgress,
      silentError: options.silentError === true
    })
  },

  /**
   * 上传图片（带压缩）
   * @param file 图片文件
   * @param maxWidth 最大宽度（默认1920）
   * @param quality 压缩质量（默认0.8）
   */
  uploadImage(file, maxWidth = 1920, quality = 0.8, options = {}) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('maxWidth', maxWidth)
    formData.append('quality', quality)
    return request.post('/file/upload-image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
        'Idempotency-Key': options.idempotencyKey || getFileUploadIdempotencyKey(file)
      },
      onUploadProgress: options.onUploadProgress,
      silentError: options.silentError === true
    })
  },

  /**
   * 删除文件
   * @param fileId 业务文件 ID
   */
  delete(fileId) {
    return request.post('/file', null, { params: { fileId } })
  }
}

return { fileApi }
}
