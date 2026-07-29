package com.workflow.openapi.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateIntegrationProcessContractsRequest(
        @NotNull
        @Size(max = 100)
        List<@Valid IntegrationProcessContractRequest> contracts,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
