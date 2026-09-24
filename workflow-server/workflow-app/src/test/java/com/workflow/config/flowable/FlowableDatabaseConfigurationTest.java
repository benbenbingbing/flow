package com.workflow.config.flowable;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.idm.spring.SpringIdmEngineConfiguration;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlowableDatabaseConfigurationTest {
    @Test
    void configuresMysqlAcrossProcessAndIdmEnginesBeforeTheyInitialize() {
        var environment = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:mysql://localhost/workflow");
        var configurer = FlowableDatabaseConfiguration.flowableDatabaseTypeConfigurer(environment);
        var process = new SpringProcessEngineConfiguration();
        var idm = new SpringIdmEngineConfiguration();
        assertSame(process, configurer.postProcessBeforeInitialization(process, "process"));
        configurer.postProcessBeforeInitialization(idm, "idm");
        assertEquals("mysql", process.getDatabaseType());
        assertEquals("mysql", idm.getDatabaseType());
    }
    @Test
    void leavesH2TestEnginesOnTheirOwnDatabaseType() {
        var environment = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:h2:mem:test;MODE=MySQL");
        var process = new SpringProcessEngineConfiguration();
        FlowableDatabaseConfiguration.flowableDatabaseTypeConfigurer(environment).postProcessBeforeInitialization(process, "process");
        assertEquals("h2", process.getDatabaseType());
    }
}
