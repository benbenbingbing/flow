package com.workflow.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.workflow.common.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置类
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * 设置 MyBatis 使用 SLF4J 日志，避免使用 System.out 导致阻塞
     */
    @PostConstruct
    public void init() {
        System.setProperty("mybatis-plus.configuration.log-impl", "org.apache.ibatis.logging.slf4j.Slf4jImpl");
    }

    /**
     * 分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 自动填充处理器
     * 自动填充创建时间、更新时间、创建人、更新人
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
                
                // 填充创建人和更新人
                String currentUserId = UserContext.getUserId();
                if (currentUserId != null && !currentUserId.isEmpty()) {
                    this.strictInsertFill(metaObject, "createdBy", String.class, currentUserId);
                    this.strictInsertFill(metaObject, "updatedBy", String.class, currentUserId);
                }
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
                
                // 填充更新人
                String currentUserId = UserContext.getUserId();
                if (currentUserId != null && !currentUserId.isEmpty()) {
                    this.strictUpdateFill(metaObject, "updatedBy", String.class, currentUserId);
                }
            }
        };
    }
}
