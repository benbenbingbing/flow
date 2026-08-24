package com.workflow.entity.list.experience.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.RequiresPermission;
import com.workflow.entity.list.experience.application.EntityIndexAdvisorService;
import com.workflow.entity.list.experience.application.EntityListExperienceModels.IndexAnalyzeRequest;
import com.workflow.entity.list.experience.application.EntityListExperienceModels.IndexApplyRequest;
import com.workflow.entity.list.experience.application.EntityListExperienceModels.IndexCandidate;
import com.workflow.entity.list.experience.application.EntityListExperienceModels.IndexRejectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 基于已发布列表字段生成和执行受控索引建议。 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/list-experience")
@RequiredArgsConstructor
public class EntityListExperienceController {

    private final EntityIndexAdvisorService indexAdvisorService;

    @PostMapping("/{entityCode}/{listKey}/index-advice/analyze")
    @RequiresPermission("index-advisor:analyze")
    public ApiResponse<List<IndexCandidate>> analyzeIndexes(
            @PathVariable String entityCode,
            @PathVariable String listKey,
            @RequestBody(required = false) IndexAnalyzeRequest request) {
        return ApiResponse.success(indexAdvisorService.analyze(entityCode, listKey, request));
    }

    @GetMapping("/{entityCode}/{listKey}/index-advice")
    @RequiresPermission("index-advisor:analyze")
    public ApiResponse<List<IndexCandidate>> indexes(
            @PathVariable String entityCode,
            @PathVariable String listKey) {
        return ApiResponse.success(indexAdvisorService.list(entityCode, listKey));
    }

    @PostMapping("/index-advice/{id}/apply")
    @RequiresPermission("index-advisor:execute")
    public ApiResponse<IndexCandidate> applyIndex(
            @PathVariable String id,
            @RequestBody IndexApplyRequest request) {
        int expected = request == null || request.expectedRevision() == null
                ? -1 : request.expectedRevision();
        return ApiResponse.success(indexAdvisorService.apply(
                id, expected, request != null && Boolean.TRUE.equals(request.confirmed())));
    }

    @PostMapping("/index-advice/{id}/reject")
    @RequiresPermission("index-advisor:execute")
    public ApiResponse<IndexCandidate> rejectIndex(
            @PathVariable String id,
            @RequestBody IndexRejectRequest request) {
        int expected = request == null || request.expectedRevision() == null
                ? -1 : request.expectedRevision();
        return ApiResponse.success(indexAdvisorService.reject(
                id, expected, request == null ? null : request.reason()));
    }
}
