package com.workflow.port.system;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 查询系统管理中登记的外部系统及对接配置。普通查询由调用方授权。 */
public interface SysExternalSystemPort {
    /**
     * 以名称和编码模糊筛选、以状态精确筛选；筛选参数均可省略。
     * 页码从 1 开始，每页最多 100 条。
     */
    Page pageSystems(int pageNumber, int pageSize, String systemName, String systemCode, String status);

    /** 按 ID 查询基本信息，未找到或已逻辑删除时为空。 */
    Optional<SystemSummary> findById(String id);

    /** 按完整业务编码查询基本信息，未找到或已逻辑删除时为空。 */
    Optional<SystemSummary> findByCode(String systemCode);

    /**
     * 按完整业务编码读取当前对接参数。参数可能含有凭据，调用方负责授权。
     * 未找到或已逻辑删除时为空；调用方可通过状态判断配置是否已启用。
     */
    Optional<Configuration> findConfigurationByCode(String systemCode);

    /**
     * 后台任务按完整业务编码读取已启用系统的配置。服务标识和凭据由部署环境提供，
     * 服务只能读取已授权的系统，授权失败会被拒绝，目标未启用或不存在时为空。
     */
    Optional<Configuration> findConfigurationForService(String systemCode, String serviceCode, String credential);

    /** 列表和单查使用相同的基本信息；status 为 0 时启用，为 1 时禁用。 */
    record SystemSummary(String id, String systemCode, String systemName, String status, String address,
                         String description, int parameterCount, Instant updateTime) { }

    /** total 是应用全部筛选条件后的总数。 */
    record Page(List<SystemSummary> records, long total, long pageNumber, long pageSize) {
        public Page {
            records = List.copyOf(records);
        }
    }

    /** 参数按配置顺序返回，供业务模块组装对接请求。 */
    record Configuration(SystemSummary system, List<Parameter> parameters) {
        public Configuration {
            parameters = List.copyOf(parameters);
        }
    }

    /** nameEn 是程序读取参数时使用的键，value 保留配置原值，可能包含凭据。 */
    record Parameter(String nameEn, String nameZh, String value, int sortOrder) {
        @Override
        public String toString() {
            return "Parameter[nameEn=" + nameEn + ", sortOrder=" + sortOrder + "]";
        }
    }
}
