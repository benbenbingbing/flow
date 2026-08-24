package com.workflow.process.configtest.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.process.configtest.application.ConfigTestModels.ExecutionResult;
import com.workflow.process.configtest.application.ConfigTestModels.GenerateSuiteRequest;
import com.workflow.process.configtest.application.ConfigTestModels.QuickRunRequest;
import com.workflow.process.configtest.application.ConfigTestModels.ResultStatus;
import com.workflow.process.configtest.application.ConfigTestModels.RunReport;
import com.workflow.process.configtest.application.ConfigTestModels.RunStatus;
import com.workflow.process.configtest.application.ConfigTestModels.SaveCaseRequest;
import com.workflow.process.configtest.application.ConfigTestModels.SaveSuiteRequest;
import com.workflow.process.configtest.application.ConfigTestModels.Severity;
import com.workflow.process.configtest.application.ConfigTestModels.SuiteDetail;
import com.workflow.process.configtest.application.ConfigTestModels.TargetType;
import com.workflow.process.configtest.application.ConfigTestModels.TestCase;
import com.workflow.process.configtest.application.ConfigTestModels.TestResult;
import com.workflow.process.configtest.application.ConfigTestModels.TestRun;
import com.workflow.process.configtest.application.ConfigTestModels.TestSuite;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 统一配置测试套件、运行、报告和自动生成服务。 */
@Service
@RequiredArgsConstructor
public class ConfigTestCenterService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ConfigTestCaseExecutor caseExecutor;

    public List<TestSuite> suites(String scopeType, String scopeId) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM config_test_suite WHERE deleted = 0
                """);
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(scopeType)) {
            sql.append(" AND scope_type = ?");
            args.add(scopeType.trim().toUpperCase());
        }
        if (StringUtils.hasText(scopeId)) {
            sql.append(" AND scope_id = ?");
            args.add(scopeId.trim());
        }
        sql.append(" ORDER BY update_time DESC");
        return jdbcTemplate.query(sql.toString(), this::mapSuite, args.toArray());
    }

    public SuiteDetail suite(String suiteId) {
        List<TestSuite> suites = jdbcTemplate.query(
                "SELECT * FROM config_test_suite WHERE id = ? AND deleted = 0",
                this::mapSuite,
                suiteId);
        if (suites.isEmpty()) {
            throw new IllegalArgumentException("测试套件不存在: " + suiteId);
        }
        return new SuiteDetail(suites.get(0), cases(suiteId));
    }

    /** 使用乐观版本保存套件，并原子替换当前用例集合。 */
    @Transactional
    public SuiteDetail save(SaveSuiteRequest request) {
        validateSuite(request);
        String actor = currentActor();
        String suiteId = StringUtils.hasText(request.id()) ? request.id() : id();
        Instant now = Instant.now();
        if (!StringUtils.hasText(request.id())) {
            jdbcTemplate.update("""
                    INSERT INTO config_test_suite
                    (id, name, description, scope_type, scope_id, enabled, release_gate,
                     version, created_by, updated_by, create_time, update_time, deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, 0)
                    """,
                    suiteId, request.name().trim(), request.description(),
                    request.scopeType().trim().toUpperCase(), request.scopeId().trim(),
                    bool(request.enabled(), true), bool(request.releaseGate(), false),
                    actor, actor, Timestamp.from(now), Timestamp.from(now));
        } else {
            long expectedVersion = request.version() == null ? suite(suiteId).suite().version() : request.version();
            int changed = jdbcTemplate.update("""
                    UPDATE config_test_suite
                    SET name = ?, description = ?, scope_type = ?, scope_id = ?, enabled = ?,
                        release_gate = ?, version = version + 1, updated_by = ?, update_time = ?
                    WHERE id = ? AND version = ? AND deleted = 0
                    """,
                    request.name().trim(), request.description(), request.scopeType().trim().toUpperCase(),
                    request.scopeId().trim(), bool(request.enabled(), true),
                    bool(request.releaseGate(), false), actor, Timestamp.from(now), suiteId, expectedVersion);
            if (changed != 1) {
                throw new IllegalStateException("测试套件已被其他用户修改，请刷新后重试");
            }
        }
        jdbcTemplate.update("DELETE FROM config_test_case WHERE suite_id = ?", suiteId);
        int order = 0;
        for (SaveCaseRequest testCase : safeCases(request.cases())) {
            insertCase(suiteId, testCase, order++);
        }
        return suite(suiteId);
    }

    @Transactional
    public void delete(String suiteId) {
        jdbcTemplate.update("""
                UPDATE config_test_suite
                SET deleted = 1, enabled = 0, version = version + 1, updated_by = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """, currentActor(), Timestamp.from(Instant.now()), suiteId);
    }

    /** 为指定配置生成一组可直接编辑和运行的基础回归套件。 */
    @Transactional
    public SuiteDetail generate(GenerateSuiteRequest request) {
        if (!StringUtils.hasText(request.scopeType()) || !StringUtils.hasText(request.scopeId())) {
            throw new IllegalArgumentException("生成测试套件需要 scopeType 和 scopeId");
        }
        TargetType targetType = TargetType.valueOf(request.scopeType().trim().toUpperCase());
        SaveCaseRequest baseline = new SaveCaseRequest(
                null,
                "baseline-" + targetType.name().toLowerCase(),
                targetType + " 基础配置预检",
                targetType.name(),
                request.scopeId(),
                "BASELINE",
                Map.of(),
                Map.of("status", "PASS"),
                true,
                0);
        return save(new SaveSuiteRequest(
                null, null,
                StringUtils.hasText(request.name()) ? request.name() : request.scopeType() + " 配置回归",
                request.description(), request.scopeType(), request.scopeId(), true,
                request.releaseGate(), List.of(baseline)));
    }

    /** 执行已保存套件并持久化不可变运行快照和逐项证据。 */
    @Transactional
    public RunReport run(String suiteId, String triggerType) {
        SuiteDetail detail = suite(suiteId);
        List<TestCase> enabledCases = detail.cases().stream().filter(TestCase::enabled).toList();
        if (enabledCases.isEmpty()) {
            throw new IllegalArgumentException("测试套件没有启用的用例");
        }
        String runId = id();
        Instant startedAt = Instant.now();
        String fingerprint = fingerprint(detail.suite(), enabledCases);
        jdbcTemplate.update("""
                INSERT INTO config_test_run
                (id, suite_id, trigger_type, status, config_fingerprint, operator_id, started_at)
                VALUES (?, ?, ?, 'RUNNING', ?, ?, ?)
                """, runId, suiteId, normalizeTrigger(triggerType), fingerprint,
                currentActor(), Timestamp.from(startedAt));

        List<TestResult> results = executeCases(runId, enabledCases);
        RunStatus status = runStatus(results);
        int passed = count(results, ResultStatus.PASS);
        int warnings = count(results, ResultStatus.WARNING);
        int failed = count(results, ResultStatus.FAIL);
        int blockers = (int) results.stream().filter(item -> item.severity() == Severity.BLOCKER).count();
        Instant finishedAt = Instant.now();
        Map<String, Object> reportSnapshot = Map.of(
                "suite", detail.suite(),
                "cases", enabledCases,
                "results", results);
        jdbcTemplate.update("""
                UPDATE config_test_run
                SET status = ?, total_count = ?, passed_count = ?, warning_count = ?,
                    failed_count = ?, blocker_count = ?, report_json = ?, finished_at = ?
                WHERE id = ?
                """, status.name(), results.size(), passed, warnings, failed, blockers,
                json(reportSnapshot), Timestamp.from(finishedAt), runId);
        return report(runId);
    }

    /** 不持久化的快速运行，供设计器保存前验证。 */
    public RunReport quickRun(QuickRunRequest request) {
        List<TestCase> cases = toCases("preview", request.cases());
        Instant now = Instant.now();
        List<TestResult> results = executeCases(null, cases);
        RunStatus status = runStatus(results);
        TestRun run = new TestRun(
                "preview", null, "PREVIEW", status,
                fingerprint(request, cases), results.size(), count(results, ResultStatus.PASS),
                count(results, ResultStatus.WARNING), count(results, ResultStatus.FAIL),
                (int) results.stream().filter(item -> item.severity() == Severity.BLOCKER).count(),
                currentActor(), now, Instant.now());
        return new RunReport(run, results, passRate(results));
    }

    public List<TestRun> runs(String suiteId) {
        return jdbcTemplate.query("""
                SELECT * FROM config_test_run
                WHERE suite_id = ? ORDER BY started_at DESC LIMIT 100
                """, this::mapRun, suiteId);
    }

    public RunReport report(String runId) {
        List<TestRun> runs = jdbcTemplate.query(
                "SELECT * FROM config_test_run WHERE id = ?",
                this::mapRun,
                runId);
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("测试运行不存在: " + runId);
        }
        List<TestResult> results = jdbcTemplate.query("""
                SELECT * FROM config_test_result
                WHERE run_id = ? ORDER BY create_time, id
                """, this::mapResult, runId);
        return new RunReport(runs.get(0), results, passRate(results));
    }

    private List<TestResult> executeCases(String runId, List<TestCase> cases) {
        List<TestResult> results = new ArrayList<>();
        for (TestCase testCase : cases) {
            long started = System.nanoTime();
            ExecutionResult execution = caseExecutor.execute(testCase);
            long durationMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
            TestResult result = new TestResult(
                    id(), runId, testCase.id(), testCase.name(), testCase.targetType().name(),
                    testCase.targetId(), execution.status(), execution.severity(), execution.code(),
                    execution.message(), execution.evidence(), durationMs);
            results.add(result);
            if (runId != null) {
                insertResult(result);
            }
        }
        return List.copyOf(results);
    }

    private void insertResult(TestResult result) {
        jdbcTemplate.update("""
                INSERT INTO config_test_result
                (id, run_id, case_id, case_name, target_type, target_id, status, severity,
                 result_code, message, evidence_json, duration_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, result.id(), result.runId(), result.caseId(), result.caseName(), result.targetType(),
                result.targetId(), result.status().name(), result.severity().name(), result.code(),
                result.message(), json(result.evidence()), result.durationMs());
    }

    private void insertCase(String suiteId, SaveCaseRequest request, int fallbackOrder) {
        TargetType targetType = TargetType.valueOf(requireText(request.targetType(), "targetType").toUpperCase());
        String caseKey = StringUtils.hasText(request.caseKey()) ? request.caseKey().trim() : id();
        jdbcTemplate.update("""
                INSERT INTO config_test_case
                (id, suite_id, case_key, name, target_type, target_id, scenario_type,
                 input_json, expected_json, enabled, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, StringUtils.hasText(request.id()) ? request.id() : id(), suiteId, caseKey,
                requireText(request.name(), "用例名称"), targetType.name(), request.targetId(),
                StringUtils.hasText(request.scenarioType()) ? request.scenarioType() : "BASELINE",
                json(request.input()), json(request.expected()), bool(request.enabled(), true),
                request.sortOrder() == null ? fallbackOrder : request.sortOrder());
    }

    private List<TestCase> cases(String suiteId) {
        return jdbcTemplate.query("""
                SELECT * FROM config_test_case
                WHERE suite_id = ? ORDER BY sort_order, id
                """, this::mapCase, suiteId);
    }

    private List<TestCase> toCases(String suiteId, List<SaveCaseRequest> requests) {
        int[] index = {0};
        return safeCases(requests).stream().map(request -> new TestCase(
                StringUtils.hasText(request.id()) ? request.id() : id(), suiteId,
                StringUtils.hasText(request.caseKey()) ? request.caseKey() : "preview-" + index[0],
                requireText(request.name(), "用例名称"),
                TargetType.valueOf(requireText(request.targetType(), "targetType").toUpperCase()),
                request.targetId(), StringUtils.hasText(request.scenarioType())
                        ? request.scenarioType() : "PREVIEW",
                safeMap(request.input()), safeMap(request.expected()), bool(request.enabled(), true),
                request.sortOrder() == null ? index[0]++ : request.sortOrder())).filter(TestCase::enabled).toList();
    }

    private TestSuite mapSuite(ResultSet rs, int row) throws SQLException {
        return new TestSuite(
                rs.getString("id"), rs.getString("name"), rs.getString("description"),
                rs.getString("scope_type"), rs.getString("scope_id"), rs.getBoolean("enabled"),
                rs.getBoolean("release_gate"), rs.getLong("version"),
                instant(rs.getTimestamp("create_time")), instant(rs.getTimestamp("update_time")));
    }

    private TestCase mapCase(ResultSet rs, int row) throws SQLException {
        return new TestCase(
                rs.getString("id"), rs.getString("suite_id"), rs.getString("case_key"),
                rs.getString("name"), TargetType.valueOf(rs.getString("target_type")),
                rs.getString("target_id"), rs.getString("scenario_type"),
                map(rs.getString("input_json")), map(rs.getString("expected_json")),
                rs.getBoolean("enabled"), rs.getInt("sort_order"));
    }

    private TestRun mapRun(ResultSet rs, int row) throws SQLException {
        return new TestRun(
                rs.getString("id"), rs.getString("suite_id"), rs.getString("trigger_type"),
                RunStatus.valueOf(rs.getString("status")), rs.getString("config_fingerprint"),
                rs.getInt("total_count"), rs.getInt("passed_count"), rs.getInt("warning_count"),
                rs.getInt("failed_count"), rs.getInt("blocker_count"), rs.getString("operator_id"),
                instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("finished_at")));
    }

    private TestResult mapResult(ResultSet rs, int row) throws SQLException {
        return new TestResult(
                rs.getString("id"), rs.getString("run_id"), rs.getString("case_id"),
                rs.getString("case_name"), rs.getString("target_type"), rs.getString("target_id"),
                ResultStatus.valueOf(rs.getString("status")), Severity.valueOf(rs.getString("severity")),
                rs.getString("result_code"), rs.getString("message"),
                map(rs.getString("evidence_json")), rs.getLong("duration_ms"));
    }

    private void validateSuite(SaveSuiteRequest request) {
        requireText(request.name(), "套件名称");
        requireText(request.scopeType(), "scopeType");
        requireText(request.scopeId(), "scopeId");
        if (safeCases(request.cases()).isEmpty()) {
            throw new IllegalArgumentException("测试套件至少需要一条用例");
        }
    }

    private RunStatus runStatus(List<TestResult> results) {
        if (results.stream().anyMatch(item -> item.status() == ResultStatus.FAIL)) {
            return RunStatus.FAIL;
        }
        if (results.stream().anyMatch(item -> item.status() == ResultStatus.WARNING)) {
            return RunStatus.WARNING;
        }
        return RunStatus.PASS;
    }

    private int count(List<TestResult> results, ResultStatus status) {
        return (int) results.stream().filter(item -> item.status() == status).count();
    }

    private double passRate(List<TestResult> results) {
        return results.isEmpty() ? 0D : Math.round(count(results, ResultStatus.PASS) * 10000D / results.size()) / 100D;
    }

    private String fingerprint(Object suite, List<TestCase> cases) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] payload = objectMapper.writeValueAsString(Map.of("suite", suite, "cases", cases))
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成测试配置指纹", exception);
        }
    }

    private String normalizeTrigger(String triggerType) {
        return StringUtils.hasText(triggerType) ? triggerType.trim().toUpperCase() : "MANUAL";
    }

    private String currentActor() {
        return StringUtils.hasText(UserContext.getUserId()) ? UserContext.getUserId() : UserContext.getUsername();
    }

    private String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("测试配置无法序列化为 JSON", exception);
        }
    }

    private Map<String, Object> map(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("持久化测试配置 JSON 已损坏", exception);
        }
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean bool(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }

    private List<SaveCaseRequest> safeCases(List<SaveCaseRequest> cases) {
        return cases == null ? List.of() : cases;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : new LinkedHashMap<>(value);
    }
}
