import request from '@/utils/request'
import { getFileUploadIdempotencyKey } from '@/shared/file-upload-idempotency'

/**
 * 文件上传API
 */
export const fileApi = {
  /**
   * 上传文件
   * @param file 文件对象
   * @returns 文件URL
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
   * @param fileUrl 文件URL
   */
  delete(fileUrl) {
    return request.post('/file', {
      params: { url: fileUrl }
    })
  },

  /**
   * 获取文件预览URL
   * @param fileUrl 文件URL
   */
  getPreviewUrl(fileUrl) {
    return `${request.defaults.baseURL}/file/preview?url=${encodeURIComponent(fileUrl)}`
  }
}
