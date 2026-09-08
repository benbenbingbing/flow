package com.workflow.embed.management.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workflow.embed.management.domain.EmbedManagementModel.ApplicationOption;
import com.workflow.embed.management.domain.EmbedManagementModel.OptionsFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import com.workflow.embed.management.support.InMemoryEmbedManagementRepository;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class EmbedOptionsQueryServiceTest {

    @Test
    void normalizesKeywordAndPaginatesFilteredApplicationsWithStableTotal() {
        InMemoryEmbedManagementRepository repository = new InMemoryEmbedManagementRepository();
        repository.applicationOptions.put("app-b", new ApplicationOption(
                "app-b", "Partner B", "client-b", SecurityStatus.ACTIVE, null, true));
        repository.applicationOptions.put("app-a", new ApplicationOption(
                "app-a", "Partner A", "client-a", SecurityStatus.ACTIVE, null, false));
        repository.applicationOptions.put("app-c", new ApplicationOption(
                "app-c", "Partner C", "client-c", SecurityStatus.DISABLED, null, false));
        repository.applicationOptions.put("app-shadow", new ApplicationOption(
                "app-shadow", "A alias containing app-a", "client-shadow",
                SecurityStatus.ACTIVE, null, true));
        EmbedOptionsQueryService service = new EmbedOptionsQueryService(repository);

        var page = service.applications(new OptionsFilter(" Partner ", SecurityStatus.ACTIVE, 2, 1));

        assertEquals(2, page.total());
        assertEquals(2, page.pageNum());
        assertEquals(1, page.pageSize());
        assertEquals("app-b", page.records().get(0).id());
        assertTrue(page.records().get(0).embedLaunchReady());
        assertEquals("app-a", service.applications(new OptionsFilter(
                "app-a", null, 1, 20)).records().get(0).id());
        assertEquals("app-c", service.applications(new OptionsFilter(
                "client-c", null, 1, 20)).records().get(0).id());
        assertEquals(4, service.applications(new OptionsFilter("  ", null, 1, 20)).total());
        assertEquals(0, service.applications(new OptionsFilter(null, null, 5, 1)).records().size());
    }

    @Test
    void optionPagesUseOneReadOnlyTransactionForRowsAndTotal() throws Exception {
        for (String methodName : new String[]{"applications", "identityProviders"}) {
            Method method = EmbedOptionsQueryService.class.getMethod(
                    methodName, OptionsFilter.class);
            Transactional transaction = method.getAnnotation(Transactional.class);

            assertNotNull(transaction, methodName);
            assertTrue(transaction.readOnly(), methodName);
        }
    }

    @Test
    void invalidPageKeywordAndOverflowAreRejectedBeforeQueryingEitherDirectory() {
        EmbedOptionsQueryService service = new EmbedOptionsQueryService(
                new InMemoryEmbedManagementRepository());
        for (OptionsFilter filter : new OptionsFilter[]{
                new OptionsFilter(null, null, 0, 20),
                new OptionsFilter(null, null, 1, 0),
                new OptionsFilter(null, null, 1, 101),
                new OptionsFilter(null, null, Integer.MAX_VALUE, 100),
                new OptionsFilter("x".repeat(129), null, 1, 20)}) {
            assertThrows(IllegalArgumentException.class, () -> service.applications(filter));
            assertThrows(IllegalArgumentException.class, () -> service.identityProviders(filter));
        }
    }

    @Test
    void acceptsSearchableColumnLengthBoundariesAfterTrimming() {
        EmbedOptionsQueryService service = new EmbedOptionsQueryService(
                new InMemoryEmbedManagementRepository());

        assertDoesNotThrow(() -> service.applications(
                new OptionsFilter("x".repeat(101), null, 1, 20)));
        assertDoesNotThrow(() -> service.identityProviders(
                new OptionsFilter("x".repeat(128), null, 1, 20)));
        assertDoesNotThrow(() -> service.applications(
                new OptionsFilter("  " + "x".repeat(128) + "  ", null, 1, 20)));
    }
}
