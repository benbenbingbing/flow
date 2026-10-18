package com.workflow.process.archive;

import com.workflow.contracts.history.ProcessArchivePort;
import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.AuthenticatedApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 归档只提供读取接口；对象权限在应用服务按关联实体范围再次验证。 */
@RestController
@RequestMapping("/api/process-archives")
@AuthenticatedApi(objectAuthorization = true)
@RequiredArgsConstructor
public class ProcessArchiveController {
    private final ProcessArchivePort archives;
    @GetMapping
    public ApiResponse<?> list(@RequestParam String entityCode, @RequestParam String recordId) { return ApiResponse.success(archives.list(entityCode, recordId)); }
    @GetMapping("/{id}")
    public ApiResponse<?> detail(@PathVariable String id) { return ApiResponse.success(archives.detail(id)); }
}
