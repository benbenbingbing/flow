package com.workflow.service;

import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveResult;
import com.workflow.contracts.identity.resolver.PersonResolverDescriptor;
import com.workflow.contracts.process.action.spi.FlowActionHandler;
import com.workflow.contracts.process.assignment.spi.PersonResolver;
import com.workflow.notification.SendNotificationHandler;
import com.workflow.process.assignment.extension.ProcessInitiatorPersonResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 扩展 SPI 实现归属的默认值和平台显式声明测试。 */
class ExtensionImplementationOriginTest {

    @Test
    void treatsThirdPartySpiImplementationsAsCustomByDefault() {
        FlowActionHandler actionHandler = context -> { };
        PersonResolver personResolver = new PersonResolver() {
            @Override
            public PersonResolverDescriptor descriptor() {
                return null;
            }

            @Override
            public PersonResolveResult resolve(PersonResolveRequest request) {
                return null;
            }
        };

        assertEquals(ExtensionImplementationOrigin.CUSTOM,
                actionHandler.implementationOrigin());
        assertEquals(ExtensionImplementationOrigin.CUSTOM,
                personResolver.implementationOrigin());
    }

    @Test
    void platformImplementationsDeclareTheirOrigin() {
        assertEquals(ExtensionImplementationOrigin.PLATFORM,
                new ProcessInitiatorPersonResolver().implementationOrigin());
        assertEquals(ExtensionImplementationOrigin.PLATFORM,
                new SendNotificationHandler().implementationOrigin());
    }
}
