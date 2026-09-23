package com.workflow.config;

import com.workflow.core.database.DatabaseExceptionClassifier;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** MVC 切片不装配数据源，显式提供 MySQL 错误规则以验证与生产相同的异常响应。 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlErrorTestConfiguration {
    @Bean
    DatabaseExceptionClassifier databaseExceptionClassifier() {
        return new DatabaseExceptionClassifier(DatabaseDialects.errors(DatabaseVendor.MYSQL));
    }
}
