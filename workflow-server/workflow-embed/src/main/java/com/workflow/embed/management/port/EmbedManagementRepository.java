package com.workflow.embed.management.port;

import com.workflow.embed.management.domain.EmbedManagementModel.BindingFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.BindingState;
import com.workflow.embed.management.domain.EmbedManagementModel.GrantState;
import com.workflow.embed.management.domain.EmbedManagementModel.Page;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderState;
import com.workflow.embed.management.domain.EmbedManagementModel.ReleaseState;
import com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewState;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

/** Embed 管理用例访问持久化和已发布资源目录的端口。 */
public interface EmbedManagementRepository {

    Page<ViewState> findViews(ViewFilter filter);

    ViewState findView(String viewId);

    ViewState lockView(String viewId);

    ViewState findViewByKey(String viewKey);

    void insertView(ViewState view);

    int updateDraft(String viewId, long expectedVersion, String draftJson,
                    String actorId, LocalDateTime now);

    long nextReleaseRevision(String viewId);

    void insertRelease(ReleaseState release);

    int markPublished(String viewId, long expectedVersion, String releaseId,
                      String actorId, LocalDateTime now);

    int updateViewStatus(String viewId, long expectedVersion, String status,
                         String actorId, LocalDateTime now);

    List<ReleaseState> findReleases(String viewId);

    ReleaseState findRelease(String viewId, long revision);

    ResolvedResource resolvePublishedResource(
            SurfaceType surfaceType, JsonNode target, JsonNode releasePolicy);

    GrantState findGrant(String viewId, String applicationId);

    GrantState lockGrant(String viewId, String applicationId);

    List<GrantState> findGrants(String viewId);

    void insertGrant(GrantState grant);

    int updateGrant(GrantState grant, long expectedVersion,
                    String actorId, LocalDateTime now);

    void replaceOrigins(String grantId, List<String> origins);

    int changeGrantStatus(String grantId, long expectedVersion, String status,
                          String actorId, LocalDateTime now, boolean revoked);

    boolean applicationExistsAndEnabled(String applicationId);

    Page<ProviderState> findProviders(ProviderFilter filter);

    ProviderState findProvider(String providerId);

    ProviderState lockProvider(String providerId);

    ProviderState findProviderByIssuerAndNamespace(String issuer, String namespace);

    ProviderState lockProviderByIssuerAndNamespace(String issuer, String namespace);

    void insertProvider(ProviderState provider);

    int updateProvider(ProviderState provider, long expectedVersion,
                       String actorId, LocalDateTime now);

    int changeProviderStatus(String providerId, long expectedVersion, String status,
                             String actorId, LocalDateTime now, boolean revoked);

    int rotateProviderKey(String providerId, long expectedVersion, String jwksJson,
                          String actorId, LocalDateTime now);

    Page<BindingState> findBindings(BindingFilter filter);

    BindingState findBinding(String bindingId);

    BindingState lockBinding(String bindingId);

    BindingState findBindingByDigests(
            String applicationId, String providerId, List<String> digests);

    void insertBinding(BindingState binding);

    int changeBindingStatus(String bindingId, long expectedVersion, String status,
                            String actorId, LocalDateTime now, boolean revoked);

    boolean flowUserExistsAndEnabled(String flowUserId);

    /** View 安全版本变化后受影响的活动会话数，仅用于管理响应与审计。 */
    long countActiveSessionsByView(String viewId);
}
