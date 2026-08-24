package com.workflow.process.configtest.api.web;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.Result;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.process.configtest.application.ConfigTestCenterService;
import com.workflow.process.configtest.application.ConfigTestModels.GenerateSuiteRequest;
import com.workflow.process.configtest.application.ConfigTestModels.GateStatus;
import com.workflow.process.configtest.application.ConfigTestModels.QuickRunRequest;
import com.workflow.process.configtest.application.ConfigTestModels.RunReport;
import com.workflow.process.configtest.application.ConfigTestModels.SaveSuiteRequest;
import com.workflow.process.configtest.application.ConfigTestModels.SuiteDetail;
import com.workflow.process.configtest.application.ConfigTestModels.TestRun;
import com.workflow.process.configtest.application.ConfigTestModels.TestSuite;
import com.workflow.process.configtest.application.ConfigTestReleaseGateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 统一配置测试中心 API。 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/config-test-center")
@RequiredArgsConstructor
public class ConfigTestCenterController {

    private final ConfigTestCenterService centerService;
    private final ConfigTestReleaseGateService releaseGateService;

    @GetMapping("/suites")
    public Result<List<TestSuite>> suites(
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeId) {
        require("config:test:list");
        return Result.success(centerService.suites(scopeType, scopeId));
    }

    @GetMapping("/suites/{suiteId}")
    public Result<SuiteDetail> suite(@PathVariable String suiteId) {
        require("config:test:list");
        return Result.success(centerService.suite(suiteId));
    }

    @PostMapping("/suites")
    public Result<SuiteDetail> save(@RequestBody SaveSuiteRequest request) {
        require("config:test:manage");
        return Result.success(centerService.save(request));
    }

    @PostMapping("/suites/generate")
    public Result<SuiteDetail> generate(@RequestBody GenerateSuiteRequest request) {
        require("config:test:manage");
        return Result.success(centerService.generate(request));
    }

    @PostMapping("/suites/{suiteId}/delete")
    public Result<Void> delete(@PathVariable String suiteId) {
        require("config:test:manage");
        centerService.delete(suiteId);
        return Result.success();
    }

    @PostMapping("/suites/{suiteId}/run")
    public Result<RunReport> run(
            @PathVariable String suiteId,
            @RequestParam(required = false, defaultValue = "MANUAL") String triggerType) {
        require("config:test:run");
        return Result.success(centerService.run(suiteId, triggerType));
    }

    @PostMapping("/quick-run")
    public Result<RunReport> quickRun(@RequestBody QuickRunRequest request) {
        require("config:test:run");
        return Result.success(centerService.quickRun(request));
    }

    @GetMapping("/suites/{suiteId}/runs")
    public Result<List<TestRun>> runs(@PathVariable String suiteId) {
        require("config:test:list");
        return Result.success(centerService.runs(suiteId));
    }

    @GetMapping("/runs/{runId}")
    public Result<RunReport> report(@PathVariable String runId) {
        require("config:test:list");
        return Result.success(centerService.report(runId));
    }

    @GetMapping("/gates/{scopeType}/{scopeId}")
    public Result<GateStatus> gate(
            @PathVariable String scopeType,
            @PathVariable String scopeId) {
        require("config:test:list");
        return Result.success(releaseGateService.status(scopeType, scopeId));
    }

    private void require(String permission) {
        if (!PermissionUtil.hasPermission(permission)) {
            throw new ForbiddenException("缺少权限: " + permission);
        }
    }
}
