package com.workflow.migration.application;

/**
 * 导出文件下载数据。
 */
public record DownloadFile(String fileName, String contentType, byte[] data) {
}
