package com.workflow.migration.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.core.result.PageResult;
import com.workflow.migration.api.request.ConfigMigrationAssetQuery;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigExportPackageMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigImportPackageMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigExportPackage;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportPackage;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置迁移列表与统计的只读查询服务。
 *
 * <p>将分页、摘要列投影和顶部全局统计集中在读模型中，避免列表查询
 * 读取发布包与导入包的大二进制字段。</p>
 */
@Service
@RequiredArgsConstructor
public class ConfigMigrationReadService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ConfigMigrationAssetMapper assetMapper;
    private final ConfigExportPackageMapper exportPackageMapper;
    private final ConfigImportPackageMapper importPackageMapper;

    /**
     * 按条件分页查询可迁移资产摘要，页码非正数时回退到 1，每页最多 100 条。
     * 快照与依赖 JSON 由资产详情接口按需读取，避免列表重复传输 longtext。
     *
     * @param query 资产过滤与分页条件
     * @return 资产分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<ConfigMigrationAsset> pageAssets(
            ConfigMigrationAssetQuery query) {
        ConfigMigrationAssetQuery safeQuery = query == null
                ? new ConfigMigrationAssetQuery() : query;
        Page<ConfigMigrationAsset> page = assetMapper.selectPage(
                page(safeQuery.getPageNum(), safeQuery.getPageSize()),
                new LambdaQueryWrapper<ConfigMigrationAsset>()
                        .select(
                                ConfigMigrationAsset::getId,
                                ConfigMigrationAsset::getAssetType,
                                ConfigMigrationAsset::getBusinessKey,
                                ConfigMigrationAsset::getAssetName,
                                ConfigMigrationAsset::getSourceVersion,
                                ConfigMigrationAsset::getMigrationTag,
                                ConfigMigrationAsset::getMarkForExport,
                                ConfigMigrationAsset::getSnapshotCompleteness,
                                ConfigMigrationAsset::getDependencyCount,
                                ConfigMigrationAsset::getExportStatus,
                                ConfigMigrationAsset::getPublishedAt,
                                ConfigMigrationAsset::getCreatedAt)
                        .eq(StringUtils.hasText(safeQuery.getAssetType()),
                                ConfigMigrationAsset::getAssetType,
                                safeQuery.getAssetType())
                        .like(StringUtils.hasText(safeQuery.getBusinessKey()),
                                ConfigMigrationAsset::getBusinessKey,
                                safeQuery.getBusinessKey())
                        .eq(StringUtils.hasText(safeQuery.getMigrationTag()),
                                ConfigMigrationAsset::getMigrationTag,
                                safeQuery.getMigrationTag())
                        .eq(safeQuery.getMarkForExport() != null,
                                ConfigMigrationAsset::getMarkForExport,
                                safeQuery.getMarkForExport())
                        .eq(StringUtils.hasText(safeQuery.getExportStatus()),
                                ConfigMigrationAsset::getExportStatus,
                                safeQuery.getExportStatus())
                        .eq(StringUtils.hasText(
                                        safeQuery.getSnapshotCompleteness()),
                                ConfigMigrationAsset::getSnapshotCompleteness,
                                safeQuery.getSnapshotCompleteness())
                        .orderByDesc(ConfigMigrationAsset::getPublishedAt)
                        .orderByDesc(ConfigMigrationAsset::getCreatedAt)
                        .orderByDesc(ConfigMigrationAsset::getId));
        return pageResult(page, page.getRecords());
    }

    /**
     * 分页查询发布包摘要。查询显式排除签名和包体字段，避免列表页
     * 因 longblob 产生额外数据库 I/O 与内存占用。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 发布包摘要分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> pageExports(
            Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<ConfigExportPackage> wrapper =
                new LambdaQueryWrapper<ConfigExportPackage>()
                        .select(
                                ConfigExportPackage::getId,
                                ConfigExportPackage::getPackageNo,
                                ConfigExportPackage::getMigrationTag,
                                ConfigExportPackage::getFileName,
                                ConfigExportPackage::getChecksum,
                                ConfigExportPackage::getStatus,
                                ConfigExportPackage::getAssetCount,
                                ConfigExportPackage::getCreatedBy,
                                ConfigExportPackage::getCreatedAt,
                                ConfigExportPackage::getDownloadCount,
                                ConfigExportPackage::getLastDownloadAt)
                        .orderByDesc(ConfigExportPackage::getCreatedAt)
                        .orderByDesc(ConfigExportPackage::getId);
        Page<ConfigExportPackage> page = exportPackageMapper.selectPage(
                page(pageNum, pageSize), wrapper);
        List<Map<String, Object>> records = page.getRecords().stream()
                .map(this::exportSummary)
                .toList();
        return pageResult(page, records);
    }

    /**
     * 分页查询导入批次摘要。查询不读取校验报告和原始包体，两者均不属于
     * 列表展示所需数据。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 导入批次摘要分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> pageImports(
            Integer pageNum, Integer pageSize) {
        Page<ConfigImportPackage> page = importPackageMapper.selectPage(
                page(pageNum, pageSize), importSummaryQuery());
        List<Map<String, Object>> records = page.getRecords().stream()
                .map(this::importSummary)
                .toList();
        return pageResult(page, records);
    }

    /**
     * 查询影响对比批次选择器的全量轻量选项。
     *
     * <p>选择器需要访问历史任意批次，因此保留全量语义；查询仍使用
     * 摘要列投影，不读取 validation_report_json 与 package_data。</p>
     *
     * @return 按导入时间倒序排列的批次摘要列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listImportOptions() {
        return importPackageMapper.selectList(importSummaryQuery()).stream()
                .map(this::importSummary)
                .toList();
    }

    /**
     * 统计全局待导出资产、已导出资产和阻断导入批次数量。
     *
     * <p>统计不受当前列表页码和页面过滤条件影响，用于顶部概览数据。</p>
     *
     * @return pending/exported/blocked 三项全局统计
     */
    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        long pending = assetMapper.selectCount(
                new LambdaQueryWrapper<ConfigMigrationAsset>()
                        .eq(ConfigMigrationAsset::getMarkForExport, true)
                        .ne(ConfigMigrationAsset::getExportStatus, "EXPORTED"));
        long exported = assetMapper.selectCount(
                new LambdaQueryWrapper<ConfigMigrationAsset>()
                        .eq(ConfigMigrationAsset::getExportStatus, "EXPORTED"));
        long blocked = importPackageMapper.selectCount(
                new LambdaQueryWrapper<ConfigImportPackage>()
                        .eq(ConfigImportPackage::getStatus, "BLOCKED"));
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("pending", pending);
        result.put("exported", exported);
        result.put("blocked", blocked);
        return result;
    }

    /**
     * 统一收敛分页边界，防止非法页码与过大页容量绕过接口默认值。
     */
    private <T> Page<T> page(Integer pageNum, Integer pageSize) {
        int safePageNum = pageNum == null ? 1 : Math.max(1, pageNum);
        int requestedSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        int safePageSize = Math.min(MAX_PAGE_SIZE,
                Math.max(1, requestedSize));
        return new Page<>(safePageNum, safePageSize);
    }

    private <T, R> PageResult<R> pageResult(
            Page<T> page, List<R> records) {
        return new PageResult<>(records, page.getTotal(),
                page.getCurrent(), page.getSize());
    }

    /**
     * 复用导入批次摘要列投影，保证分页列表与对比选择器的字段和排序一致。
     */
    private LambdaQueryWrapper<ConfigImportPackage> importSummaryQuery() {
        return new LambdaQueryWrapper<ConfigImportPackage>()
                .select(
                        ConfigImportPackage::getId,
                        ConfigImportPackage::getPackageNo,
                        ConfigImportPackage::getSourceEnvironment,
                        ConfigImportPackage::getMigrationTag,
                        ConfigImportPackage::getFileName,
                        ConfigImportPackage::getChecksum,
                        ConfigImportPackage::getStatus,
                        ConfigImportPackage::getImportedBy,
                        ConfigImportPackage::getImportedAt,
                        ConfigImportPackage::getPublishedBy,
                        ConfigImportPackage::getPublishedAt,
                        ConfigImportPackage::getErrorMessage)
                .orderByDesc(ConfigImportPackage::getImportedAt)
                .orderByDesc(ConfigImportPackage::getId);
    }

    private Map<String, Object> exportSummary(ConfigExportPackage value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.getId());
        result.put("packageNo", value.getPackageNo());
        result.put("migrationTag", value.getMigrationTag());
        result.put("fileName", value.getFileName());
        result.put("checksum", value.getChecksum());
        result.put("status", value.getStatus());
        result.put("assetCount", value.getAssetCount());
        result.put("createdBy", value.getCreatedBy());
        result.put("createdAt", value.getCreatedAt());
        result.put("downloadCount", value.getDownloadCount());
        result.put("lastDownloadAt", value.getLastDownloadAt());
        return result;
    }

    private Map<String, Object> importSummary(ConfigImportPackage value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.getId());
        result.put("packageNo", value.getPackageNo());
        result.put("sourceEnvironment", value.getSourceEnvironment());
        result.put("migrationTag", value.getMigrationTag());
        result.put("fileName", value.getFileName());
        result.put("checksum", value.getChecksum());
        result.put("status", value.getStatus());
        result.put("importedBy", value.getImportedBy());
        result.put("importedAt", value.getImportedAt());
        result.put("publishedBy", value.getPublishedBy());
        result.put("publishedAt", value.getPublishedAt());
        result.put("errorMessage", value.getErrorMessage());
        return result;
    }
}
