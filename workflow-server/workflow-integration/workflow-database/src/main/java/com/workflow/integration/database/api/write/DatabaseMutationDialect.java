package com.workflow.integration.database.api.write;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.query.DatabaseSort;

import java.util.List;

/** 单表批量写入的语法标准；只生成 SQL，不持有连接或管理事务。 */
public interface DatabaseMutationDialect {
    /**
     * 处理供应商，并将结果传给后续步骤。
     *
     * @return 处理后的供应商结果，供调用方继续处理
     */
    DatabaseVendor vendor();

    /**
     * 按稳定排序最多更新 limit 行。primaryKey 必须是完整的非空主键，orderBy 必须包含其所有列；
     * assignments 不得修改主键。条件、赋值是服务端固定 SQL，值只能用命名/MyBatis 参数绑定。
     * 调用方负责参数范围、事务和死锁重试；并发改变候选状态时可少于 limit 行，零行不证明队列为空。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param assignments 分配集合，供本方法更新受限时使用
     * @param predicate 判断条件，供本方法更新受限时使用
     * @param orderBy 顺序，供本方法更新受限时使用
     * @param primaryKey 主要键，后续用于授权校验、关联或幂等去重
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 更新后的受限文本，供调用方比较或展示
     */
    String updateLimited(String table, String assignments, String predicate,
                         List<DatabaseSort> orderBy, List<String> primaryKey, String limit);

    /**
     * 按稳定排序最多删除 limit 行；键、排序和绑定限制同 updateLimited。
     * 关联存在性条件保留在最终 DELETE 中，外键仍由数据库作为最终约束。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param predicate 判断条件，供本方法删除受限时使用
     * @param orderBy 顺序，供本方法删除受限时使用
     * @param primaryKey 主要键，后续用于授权校验、关联或幂等去重
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 删除后的受限文本，供调用方比较或展示
     */
    String deleteLimited(String table, String predicate, List<DatabaseSort> orderBy,
                         List<String> primaryKey, String limit);

    /**
     * 放在 UPDATE 表名后的主键访问提示。仅用于完整主键等值更新；不支持此提示的产品返回空串。
     * 语义正确性必须由主键和状态条件保证，不能依赖优化器提示提供额外互斥。
     *
     * @return 处理后的主要键更新{@code hint}文本，供调用方比较或展示
     */
    String primaryKeyUpdateHint();
}
