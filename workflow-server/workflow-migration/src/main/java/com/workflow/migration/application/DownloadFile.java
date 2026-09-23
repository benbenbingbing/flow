package com.workflow.migration.application;

/**
 * 导出文件下载数据。
 *
 * @param fileName 文件名称，后续用于处理{@code download}文件时匹配或展示
 * @param contentType 内容类型标识，决定后续{@code download}文件采用的处理分支
 * @param data 数据，后续用于处理{@code download}文件并传递处理结果
 */
public record DownloadFile(String fileName, String contentType, byte[] data) {
}
