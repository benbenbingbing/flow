package com.workflow.admin.setting.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.admin.setting.infrastructure.persistence.record.GlobalSettingRecord;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 设置存取入口；所有可变操作同时限定归属、稳定键、记录 ID 和版本。 */
@Mapper
public interface GlobalSettingMapper extends BaseMapper<GlobalSettingRecord> {
    /**
     * 读取作用域内单个设置；不存在时返回 null，不自动创建偏好。
     *
     * @param scope 作用域，供本方法查询全局设置时使用
     * @param owner 归属方，供本方法查询全局设置时使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的全局设置结果，供调用方继续处理
     */
    default GlobalSettingRecord find(String scope, String owner, String key) {
        return selectOne(Wrappers.<GlobalSettingRecord>lambdaQuery()
                .eq(GlobalSettingRecord::getScopeType, scope)
                .eq(GlobalSettingRecord::getOwnerId, owner)
                .eq(GlobalSettingRecord::getSettingKey, key));
    }

    /**
     * 批量读取一个归属下的设置，用于系统设置列表。
     *
     * @param scope 作用域，供本方法查询全局设置全部时使用
     * @param owner 归属方，供本方法查询全局设置全部时使用
     * @return 全局设置集合，供调用方遍历或展示
     */
    default List<GlobalSettingRecord> findAll(String scope, String owner) {
        return selectList(Wrappers.<GlobalSettingRecord>lambdaQuery()
                .eq(GlobalSettingRecord::getScopeType, scope)
                .eq(GlobalSettingRecord::getOwnerId, owner));
    }

    /**
     * 原子更新并递增版本，返回 0 表示已被修改或删除重建；不更改记录归属与键。
     *
     * @param row 行，作为 {@code set} 的输入影响后续处理
     * @return 更新后的值结果，供调用方继续处理
     */
    default int updateValue(GlobalSettingRecord row) {
        // 版本检查和递增仍在同一条 UPDATE 内完成；显式 set 允许把可空字段清为 null。
        return update(null, Wrappers.<GlobalSettingRecord>lambdaUpdate()
                .set(GlobalSettingRecord::getSettingValue, row.getSettingValue())
                .set(GlobalSettingRecord::getSettingValueType, row.getSettingValueType())
                .set(GlobalSettingRecord::getName, row.getName())
                .set(GlobalSettingRecord::getRemark, row.getRemark())
                .setIncrBy(GlobalSettingRecord::getVersion, 1)
                .set(GlobalSettingRecord::getUpdatedBy, row.getUpdatedBy())
                .set(GlobalSettingRecord::getUpdateTime, row.getUpdateTime())
                .eq(GlobalSettingRecord::getId, row.getId())
                .eq(GlobalSettingRecord::getVersion, row.getVersion())
                .eq(GlobalSettingRecord::getScopeType, row.getScopeType())
                .eq(GlobalSettingRecord::getOwnerId, row.getOwnerId())
                .eq(GlobalSettingRecord::getSettingKey, row.getSettingKey()));
    }

    /**
     * 删除指定版本恢复继承；旧页面不能删除另一用户或删除重建后的记录。
     *
     * @param scope 作用域，作为 {@code eq} 的输入影响后续处理
     * @param owner 归属方，供本方法删除版本时使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param version 版本，供本方法删除版本时使用
     * @return 删除后的版本结果，供调用方继续处理
     */
    default int deleteVersion(String scope, String owner, String key, String id, long version) {
        // 设置表没有逻辑删除字段；完整保留归属、稳定键和版本保护后物理删除，恢复继承。
        return delete(Wrappers.<GlobalSettingRecord>lambdaQuery()
                .eq(GlobalSettingRecord::getId, id)
                .eq(GlobalSettingRecord::getVersion, version)
                .eq(GlobalSettingRecord::getScopeType, scope)
                .eq(GlobalSettingRecord::getOwnerId, owner)
                .eq(GlobalSettingRecord::getSettingKey, key));
    }
}
