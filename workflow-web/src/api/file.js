import request from '@/utils/request'

/**
 * 文件上传API
 */
export const fileApi = {
  /**
   * 上传文件
   * @param file 文件对象
   * @returns 文件URL
   */
  upload(file) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/file/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  /**
   * 上传图片（带压缩）
   * @param file 图片文件
   * @param maxWidth 最大宽度（默认1920）
   * @param quality 压缩质量（默认0.8）
   */
  uploadImage(file, maxWidth = 1920, quality = 0.8) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('maxWidth', maxWidth)
    formData.append('quality', quality)
    return request.post('/file/upload-image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  /**
   * 删除文件
   * @param fileUrl 文件URL
   */
  delete(fileUrl) {
    return request.delete('/file', {
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
