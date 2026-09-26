package com.workflow.entity.form.application;

import com.workflow.entity.form.application.context.FormCrossFieldRuntimeContext;
import com.workflow.entity.form.application.context.FormSubmissionExecutionContext;
import com.workflow.entity.form.application.error.FormSubmissionPreviewDeferredException;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.form.application.validation.PublishedFormCrossFieldValidator;
import com.workflow.entity.form.application.validation.PublishedFormRequiredValidator;

import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.validation.UiExtensionDefinitionValidator;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;

import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.contracts.entity.ui.port.UiHotfixObservationPort;
import com.workflow.entity.ui.api.request.UiExtensionExecuteRequest;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.application.EntityPublishedRelationService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 已发布表单提交处理服务，在提交前应用表单默认值与 BEFORE_SUBMIT 数据源绑定。
 *
 * <p>解析实体默认表单或指定发布版本，执行字段默认值、计算和前置数据源绑定，
 * 确保提交数据符合发布版本约束，并通过执行上下文保证绑定幂等。</p>
 */
@Service
@RequiredArgsConstructor
public class PublishedFormSubmissionService {

    private final EntityDefinitionMapper entityDefinitionMapper;
    private final EntityFormMapper formMapper;
    private final EntityRelationMapper entityRelationMapper;
    private final UiConfigReleaseService releaseService;
    private final UiInterfaceExtensionService dataSourceService;
    private final JsonDocumentCodec codec;
    private final UiExtensionDefinitionValidator schemaValidator;
    private final PublishedFormRequiredValidator requiredValidator;
    private final PublishedFormCrossFieldValidator crossFieldValidator;
    private EntityPublishedRelationService publishedRelationService;
    private UiHotfixObservationPort hotfixObservationPort;

    /**
     * 可选注入发布关系读取能力；缺失时提交阶段回退到关系 Mapper。
     *
     * @param publishedRelationService 发布关系服务，后续用于关系过滤及 dataKey 解析
     */
    @Autowired(required = false)
    void setPublishedRelationService(
            EntityPublishedRelationService publishedRelationService) {
        this.publishedRelationService = publishedRelationService;
    }

    /**
     * 可选接入热修复观察能力；缺失时跳过观察记录而继续权威提交。
     *
     * @param hotfixObservationPort 观察端口，后续记录实际发布版本的提交结果
     */
    @Autowired(required = false)
    void setHotfixObservationPort(
            UiHotfixObservationPort hotfixObservationPort) {
        this.hotfixObservationPort = hotfixObservationPort;
    }

    /**
     * 应用实体默认表单的默认值与前置数据源（使用独立执行上下文）。
     *
     * @param entityCode 实体编码，用于定位默认表单或根实体校验范围
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @return 处理后的表单数据
     */
    public Map<String, Object> applyDefaultForm(
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData) {
        return applyDefaultForm(
                entityCode,
                recordId,
                mode,
                submittedData,
                FormSubmissionExecutionContext.standalone(
                        "ENTITY_" + normalizeOperation(mode)));
    }

    /**
     * 应用实体默认表单的默认值与前置数据源（使用指定执行上下文）。
     *
     * @param entityCode 实体编码，用于定位默认表单或根实体校验范围
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @param executionContext 单次提交追踪上下文，用于绑定输入和幂等键生成
     * @return 处理后的表单数据
     * @throws IllegalArgumentException 实体不存在时抛出
     */
    public Map<String, Object> applyDefaultForm(
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext) {
        return applyDefaultFormWithRelease(
                entityCode,
                recordId,
                mode,
                submittedData,
                executionContext).data();
    }

    /**
     * 应用实体默认表单，并返回服务端本次实际解析的发布身份。
     *
     * <p>当实体未设置默认表单时，原样返回数据且 formId/release 均为
     * null，调用方不得激活表单作用域规则。存在默认表单时，返回
     * base/effective release，保证前置处理与事务终检读取同一快照。</p>
     *
     * @param entityCode 实体编码，用于定位默认表单或根实体校验范围
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @param executionContext 单次提交追踪上下文，用于绑定输入和幂等键生成
     * @return 处理后的数据与实际基础、有效发布身份；没有默认表单时发布身份为空
     * @throws IllegalArgumentException 实体定义不存在
     */
    public DefaultFormApplication applyDefaultFormWithRelease(
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext) {
        EntityDefinition definition =
                entityDefinitionMapper.findByEntityCode(entityCode)
                        .orElse(null);
        if (definition == null) {
            throw new IllegalArgumentException(
                    "实体不存在: " + entityCode);
        }
        EntityForm form =
                formMapper.selectDefaultByEntityId(definition.getId());
        if (form == null) {
            return new DefaultFormApplication(
                    mutable(submittedData),
                    null,
                    null,
                    null,
                    null);
        }
        AuthorizedFormApplication applied = applyFormWithRelease(
                form.getId(),
                null,
                null,
                entityCode,
                recordId,
                mode,
                submittedData,
                executionContext,
                null);
        return new DefaultFormApplication(
                applied.data(),
                form.getId(),
                applied.releaseId(),
                applied.releaseVersion(),
                applied.effectiveReleaseId(),
                applied.effectiveContentHash(),
                applied.hotfixTargetId());
    }

    /**
     * 默认表单处理结果。调用方用 data 继续实体写入，用 formId/releaseId/
     * releaseVersion 绑定事务终检；effectiveReleaseId、内容哈希与热修复目标
     * 标识本次实际生效配置，供后续一致性检查和观察记录使用。无默认表单时
     * 发布身份均为空，调用方不得启用表单作用域规则。
     *
     * @param data 默认表单处理结果，后续交给实体写入
     * @param formId 实际默认表单 ID，供事务终检定位表单作用域
     * @param releaseId 已解析的基础发布 ID，供事务终检复用
     * @param releaseVersion 已解析的基础版本号，防止终检时版本漂移
     * @param effectiveReleaseId 热修复后实际生效的发布 ID
     * @param effectiveContentHash 实际内容哈希，供一致性检查
     * @param hotfixTargetId 热修复目标，供观察指标关联
     */
    public record DefaultFormApplication(
            Map<String, Object> data,
            String formId,
            String releaseId,
            Integer releaseVersion,
            String effectiveReleaseId,
            String effectiveContentHash,
            String hotfixTargetId) {

        /**
         * 兼容尚未携带热修复观察坐标的调用方，保留基础发布身份。
         *
         * @param data 已处理数据，后续交给实体写入
         * @param formId 实际默认表单 ID，供终检限定表单作用域
         * @param releaseId 已解析的基础发布 ID，供事务终检
         * @param releaseVersion 已解析的基础版本号，防止终检时漂移
         * @param effectiveReleaseId 实际生效发布 ID，供一致性检查
         */
        public DefaultFormApplication(
                Map<String, Object> data,
                String formId,
                String releaseId,
                Integer releaseVersion,
                String effectiveReleaseId) {
            this(
                    data,
                    formId,
                    releaseId,
                    releaseVersion,
                    effectiveReleaseId,
                    null,
                    null);
        }
    }

    /**
     * 应用指定表单的默认值与前置数据源（使用独立执行上下文，取当前激活发布版本）。
     *
     * @param formId 指定表单 ID，用于解析发布配置及节点绑定
     * @param entityCode 实体编码，用于定位默认表单或根实体校验范围
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @return 处理后的表单数据
     */
    public Map<String, Object> applyForm(
            String formId,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData) {
        return applyForm(
                formId,
                entityCode,
                recordId,
                mode,
                submittedData,
                FormSubmissionExecutionContext.standalone(
                        "FORM_" + normalizeOperation(mode)));
    }

    /**
     * 应用指定表单的默认值与前置数据源（使用指定执行上下文，取当前激活发布版本）。
     *
     * @param formId 指定表单 ID，用于解析发布配置及节点绑定
     * @param entityCode 实体编码，用于定位默认表单或根实体校验范围
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @param executionContext 单次提交追踪上下文，用于绑定输入和幂等键生成
     * @return 处理后的表单数据
     */
    public Map<String, Object> applyForm(
            String formId,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext) {
        return applyForm(
                formId,
                null,
                null,
                entityCode,
                recordId,
                mode,
                submittedData,
                executionContext);
    }

    /**
     * 应用指定发布版本表单的默认值与前置数据源（支持版本号一致性校验）。
     * 兼容未显式提供流程解析上下文的调用方，仍按指定发布坐标处理表单。
     *
     * @param formId 指定表单 ID，用于解析发布配置及节点绑定
     * @param releaseId 基础发布记录 ID；为空时读取当前激活版本
     * @param releaseVersion 预期发布版本号；提供时校验实际解析结果
     * @param entityCode 实体编码，用于定位默认表单或根实体校验范围
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @param executionContext 单次提交追踪上下文，用于绑定输入和幂等键生成
     * @return 处理后的表单数据
     * @throws IllegalArgumentException 表单或发布版本不存在时抛出
     */
    public Map<String, Object> applyForm(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext) {
        return applyForm(
                formId,
                releaseId,
                releaseVersion,
                entityCode,
                recordId,
                mode,
                submittedData,
                executionContext,
                null);
    }

    /**
     * 按客户端运行时授权上下文应用精确表单发布版本。
     *
     * @param formId 指定表单 ID，用于解析发布配置及节点绑定
     * @param releaseId 客户端指定的基础发布 ID，交由授权解析服务核对
     * @param releaseVersion 客户端指定的版本号，交由授权解析服务核对
     * @param releaseResolutionToken 客户端运行时授权令牌，用于校验精确发布身份
     * @param entityCode 实体编码，用于定位默认表单或根实体校验范围
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @param executionContext 单次提交追踪上下文，用于绑定输入和幂等键生成
     * @return 按精确授权发布版本处理后的表单数据
     */
    public Map<String, Object> applyAuthorizedForm(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String releaseResolutionToken,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext) {
        return applyAuthorizedFormWithRelease(
                formId,
                releaseId,
                releaseVersion,
                releaseResolutionToken,
                entityCode,
                recordId,
                mode,
                submittedData,
                executionContext).data();
    }

    /**
     * 应用精确发布版并返回同一次授权解析得到的正式发布身份。
     *
     * <p>调用方必须把返回的 releaseId/version 传入统一变更上下文，保证事务终检
     * 与表单默认值、BEFORE_SUBMIT 和必填校验使用同一份不可变发布快照。</p>
     *
     * @param formId 指定表单 ID，用于解析发布配置及节点绑定
     * @param releaseId 客户端指定的基础发布 ID，交由授权解析服务核对
     * @param releaseVersion 客户端指定的版本号，交由授权解析服务核对
     * @param releaseResolutionToken 客户端运行时授权令牌，用于校验精确发布身份
     * @param entityCode 实体编码，用于定位默认表单或根实体校验范围
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @param executionContext 单次提交追踪上下文，用于绑定输入和幂等键生成
     * @return 处理后的数据与本次解析的发布身份，供事务终检复用
     */
    public AuthorizedFormApplication applyAuthorizedFormWithRelease(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String releaseResolutionToken,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext) {
        ResolvedEntityFormRelease resolved =
                releaseService.resolveAuthorizedRuntimeFormRelease(
                        formId,
                        releaseId,
                        releaseVersion,
                        releaseResolutionToken);
        Map<String, Object> data = applyResolvedForm(
                    resolved,
                    entityCode,
                    recordId,
                    mode,
                    submittedData,
                    executionContext,
                    null,
                    PublishedSubFormSubmissionProcessor.Context.root(),
                    0,
                    BindingExecutionMode.AUTHORITATIVE);
        return new AuthorizedFormApplication(
                data,
                resolved.releaseId(),
                resolved.releaseVersion(),
                resolved.effectiveReleaseId(),
                resolved.effectiveContentHash(),
                resolved.hotfixTargetId());
    }

    /**
     * 指定表单的权威处理结果。data 用于落库，基础发布坐标用于后续终检；
     * effectiveReleaseId/内容哈希/热修复目标保留实际生效快照身份，避免
     * 提交前处理与变更审计分别指向不同版本。
     *
     * @param data 权威处理后的表单数据，供实体落库
     * @param releaseId 已解析的基础发布 ID，传给事务终检
     * @param releaseVersion 已解析的基础版本号，传给事务终检
     * @param effectiveReleaseId 实际生效发布 ID，供审计
     * @param effectiveContentHash 实际内容哈希，供一致性检查
     * @param hotfixTargetId 热修复目标，供观察指标关联
     */
    public record AuthorizedFormApplication(
            Map<String, Object> data,
            String releaseId,
            Integer releaseVersion,
            String effectiveReleaseId,
            String effectiveContentHash,
            String hotfixTargetId) {

        /**
         * 兼容无热修复内容哈希的调用方，仍传递基础与有效发布 ID。
         *
         * @param data 已处理数据，后续交给实体落库
         * @param releaseId 已解析的基础发布 ID，供事务终检
         * @param releaseVersion 已解析的基础版本号，防止终检时漂移
         * @param effectiveReleaseId 实际生效发布 ID，供审计关联
         */
        public AuthorizedFormApplication(
                Map<String, Object> data,
                String releaseId,
                Integer releaseVersion,
                String effectiveReleaseId) {
            this(
                    data,
                    releaseId,
                    releaseVersion,
                    effectiveReleaseId,
                    null,
                    null);
        }

        /**
         * 旧调用方只提供基础发布坐标时，以基础发布 ID 作为有效版本。
         *
         * @param data 已处理数据，后续交给实体落库
         * @param releaseId 已解析的基础发布 ID，同时作为有效发布 ID
         * @param releaseVersion 已解析的基础版本号，供事务终检
         */
        public AuthorizedFormApplication(
                Map<String, Object> data,
                String releaseId,
                Integer releaseVersion) {
            this(
                    data,
                    releaseId,
                    releaseVersion,
                    releaseId,
                    null,
                    null);
        }
    }

    /**
     * 按服务端可信流程上下文应用指定发布版本表单的提交处理。
     *
     * @param formId 指定表单 ID，用于解析发布配置及节点绑定
     * @param releaseId 基础发布记录 ID；为空时读取当前激活版本
     * @param releaseVersion 预期发布版本号；提供时校验实际解析结果
     * @param entityCode 实体编码，用于定位默认表单或根实体校验范围
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @param executionContext 单次提交追踪上下文，用于绑定输入和幂等键生成
     * @param resolutionContext 服务端可信流程解析上下文，决定本次采用的有效发布版本
     * @return 按服务端可信上下文处理后的表单数据
     */
    public Map<String, Object> applyForm(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext,
            UiRuntimeResolutionContext resolutionContext) {
        return applyFormWithRelease(
                formId,
                releaseId,
                releaseVersion,
                entityCode,
                recordId,
                mode,
                submittedData,
                executionContext,
                resolutionContext).data();
    }

    /**
     * 按服务端可信流程上下文应用表单，并返回同一次解析实际采用的基础与有效发布身份。
     *
     * @param formId 指定表单 ID，用于解析发布配置及节点绑定
     * @param releaseId 基础发布记录 ID；为空时读取当前激活版本
     * @param releaseVersion 预期发布版本号；提供时校验实际解析结果
     * @param entityCode 实体编码，用于定位默认表单或根实体校验范围
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @param executionContext 单次提交追踪上下文，用于绑定输入和幂等键生成
     * @param resolutionContext 服务端可信流程解析上下文，决定本次采用的有效发布版本
     * @return 处理后的数据及基础、有效发布身份，供事务终检和审计复用
     */
    public AuthorizedFormApplication applyFormWithRelease(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext,
            UiRuntimeResolutionContext resolutionContext) {
        ResolvedEntityFormRelease resolved = resolutionContext == null
                ? releaseService.resolveRuntimeFormRelease(
                        formId,
                        releaseId,
                        releaseVersion)
                : releaseService.resolveRuntimeFormRelease(
                        formId,
                        releaseId,
                        releaseVersion,
                        resolutionContext);
        Map<String, Object> data = applyResolvedForm(
                resolved,
                entityCode,
                recordId,
                mode,
                submittedData,
                executionContext,
                resolutionContext,
                PublishedSubFormSubmissionProcessor.Context.root(),
                0,
                BindingExecutionMode.AUTHORITATIVE);
        return new AuthorizedFormApplication(
                data,
                resolved.releaseId(),
                resolved.releaseVersion(),
                resolved.effectiveReleaseId(),
                resolved.effectiveContentHash(),
                resolved.hotfixTargetId());
    }

    /**
     * 使用与正式提交相同的表单发布版本、映射和校验语义进行无副作用预处理。
     *
     * <p>只有明确声明 {@code sideEffectFree=true} 的 BEFORE_SUBMIT 绑定会被
     * 执行。遇到普通绑定时，在调用数据源之前抛出
     * {@link FormSubmissionPreviewDeferredException}，由流程预览转换为 DEFERRED。
     * 本方法本身不写实体或流程变量。</p>
     *
     * @param formId 指定表单 ID，用于解析发布配置及节点绑定
     * @param releaseId 基础发布记录 ID；为空时读取当前激活版本
     * @param releaseVersion 预期发布版本号；提供时校验实际解析结果
     * @param entityCode 实体编码，用于定位默认表单或根实体校验范围
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @param executionContext 单次提交追踪上下文，用于绑定输入和幂等键生成
     * @param resolutionContext 服务端可信流程解析上下文，决定本次采用的有效发布版本
     * @return 只执行无副作用绑定后的预览数据
     * @throws FormSubmissionPreviewDeferredException 发布配置包含未声明无副作用的前置绑定
     */
    public Map<String, Object> previewSideEffectFreeForm(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext,
            UiRuntimeResolutionContext resolutionContext) {
        ResolvedEntityFormRelease resolved = resolutionContext == null
                ? releaseService.resolveRuntimeFormRelease(
                        formId,
                        releaseId,
                        releaseVersion)
                : releaseService.resolveRuntimeFormRelease(
                        formId,
                        releaseId,
                        releaseVersion,
                        resolutionContext);
        return applyResolvedForm(
                resolved,
                entityCode,
                recordId,
                mode,
                submittedData,
                executionContext,
                resolutionContext,
                PublishedSubFormSubmissionProcessor.Context.root(),
                0,
                BindingExecutionMode.SIDE_EFFECT_FREE_PREVIEW);
    }

    /**
     * 统一包装预览与正式提交。只有正式提交会上报热修复观察指标；预览结果
     * 可能被多次丢弃，不应计入实际提交成功率。子表递归沿用同一执行模式。
     *
     * @param resolved 本次解析出的基础与有效发布快照，后续校验和观察指标共用
     * @param entityCode 实体编码，用于定位默认表单或根实体校验范围
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @param executionContext 单次提交追踪上下文，用于绑定输入和幂等键生成
     * @param resolutionContext 服务端可信流程解析上下文，传入子表递归
     * @param nestedContext 子表行与父记录坐标，参与绑定目标和幂等键
     * @param depth 子表递归深度；根层才执行实体级最终校验
     * @param executionMode 权威提交或无副作用预览，决定绑定执行范围与观察上报
     * @return 处理后的提交数据；正式提交还会上报热修复观察结果
     */
    private Map<String, Object> applyResolvedForm(
            ResolvedEntityFormRelease resolved,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext,
            UiRuntimeResolutionContext resolutionContext,
            PublishedSubFormSubmissionProcessor.Context nestedContext,
            int depth,
            BindingExecutionMode executionMode) {
        if (executionMode != BindingExecutionMode.AUTHORITATIVE) {
            return applyResolvedFormInternal(
                    resolved,
                    entityCode,
                    recordId,
                    mode,
                    submittedData,
                    executionContext,
                    resolutionContext,
                    nestedContext,
                    depth,
                    executionMode);
        }
        try {
            Map<String, Object> result = applyResolvedFormInternal(
                    resolved,
                    entityCode,
                    recordId,
                    mode,
                    submittedData,
                    executionContext,
                    resolutionContext,
                    nestedContext,
                    depth,
                    executionMode);
            observeSubmission(resolved, true, null);
            return result;
        } catch (RuntimeException exception) {
            observeSubmission(resolved, false, exception.getMessage());
            throw exception;
        }
    }

    /**
     * 按发布快照顺序处理表单级、节点或旧版字段级绑定，再执行子表及最终校验。
     * result 是本次提交的可变副本，后续每一步读取前一步映射的输出；depth 和
     * nestedContext 用于限制根实体校验并把子表行坐标传给绑定执行器。
     *
     * @param resolved 本次解析出的基础与有效发布快照，后续校验和观察指标共用
     * @param entityCode 实体编码，用于定位默认表单或根实体校验范围
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @param executionContext 单次提交追踪上下文，用于绑定输入和幂等键生成
     * @param resolutionContext 服务端可信流程解析上下文，传入子表递归
     * @param nestedContext 子表行与父记录坐标，参与绑定目标和幂等键
     * @param depth 子表递归深度；根层才执行实体级最终校验
     * @param executionMode 权威提交或无副作用预览，决定绑定执行范围与观察上报
     * @return 完成绑定映射、子表处理和最终校验后的数据
     */
    private Map<String, Object> applyResolvedFormInternal(
            ResolvedEntityFormRelease resolved,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext,
            UiRuntimeResolutionContext resolutionContext,
            PublishedSubFormSubmissionProcessor.Context nestedContext,
            int depth,
            BindingExecutionMode executionMode) {
        EntityForm form = resolved.form();
        if (form == null) {
            throw new IllegalArgumentException(
                    "已发布表单不存在");
        }
        Map<String, Object> result =
                filterUndeclaredRelationData(
                        form,
                        submittedData);
        Map<String, Object> formBindings =
                StringUtils.hasText(
                        form.getDataSourceBindingsDocument())
                        ? codec.readObject(
                        form.getDataSourceBindingsDocument(),
                        "已发布表单级数据源绑定")
                        : Map.of();
        executeBindings(
                formBindings,
                form,
                "form:" + form.getId(),
                "OWNER",
                null,
                entityCode,
                recordId,
                mode,
                result,
                executionContext,
                resolved,
                nestedContext,
                executionMode);
        // 新版节点配置是绑定归属的权威来源；只有无节点的历史发布表单
        // 才回退字段绑定，避免同一字段在两套配置中被执行两次。
        List<EntityFormNode> nodes =
                form.getNodes() == null
                        ? List.of() : form.getNodes();
        if (!nodes.isEmpty()) {
            for (EntityFormNode node : nodes) {
                Map<String, Object> bindings =
                        StringUtils.hasText(
                                node.getDataSourceBindingsDocument())
                                ? codec.readObject(
                                        node.getDataSourceBindingsDocument(),
                                        "已发布表单节点数据源绑定")
                                : Map.of();
                executeBindings(
                        bindings,
                        form,
                        nodeOwnerKey(node),
                        nodeTargetType(node),
                        nodeTargetKey(node),
                        entityCode,
                        recordId,
                        mode,
                        result,
                        executionContext,
                        resolved,
                        nestedContext,
                        executionMode);
                subFormSubmissionProcessor().apply(
                        node,
                        form,
                        entityCode,
                        recordId,
                        mode,
                        result,
                        executionContext,
                        resolutionContext,
                        nestedContext,
                        depth,
                        executionMode
                                == BindingExecutionMode.AUTHORITATIVE,
                        (childResolved,
                                childEntityCode,
                                childRecordId,
                                childMode,
                                childData,
                                childExecutionContext,
                                childResolutionContext,
                                childContext,
                                childDepth) -> applyResolvedForm(
                                childResolved,
                                childEntityCode,
                                childRecordId,
                                childMode,
                                childData,
                                childExecutionContext,
                                childResolutionContext,
                                childContext,
                                childDepth,
                                executionMode));
            }
        } else {
            for (EntityFormField field :
                    form.getFields() == null
                            ? List.<EntityFormField>of()
                            : form.getFields()) {
                executeBindings(
                        field.getDataSourceBindings(),
                        form,
                        fieldOwnerKey(field),
                        "FIELD",
                        field.getFieldCode(),
                        entityCode,
                        recordId,
                        mode,
                        result,
                        executionContext,
                        resolved,
                        nestedContext,
                        executionMode);
            }
        }
        requiredValidator.validate(
                form,
                entityCode,
                recordId,
                mode,
                result);
        // 扩展处理结束后校验最终补丁；仅处理根实体，子表仍走原有提交链路。
        if (depth == 0 && !FormCrossFieldRuntimeContext.isReadonly(
                executionContext == null ? Map.of() : executionContext.attributes(), form.getId())
                && crossFieldValidator.hasRules(form)) {
            crossFieldValidator.validateRecord(form, mode,
                    crossFieldValidator.finalRecord(entityCode, recordId, result));
        }
        return result;
    }

    /**
     * 移除发布表单未声明的关联数据；实体可能定义了更多关系，客户端提交
     * 不能借当前表单之外的 dataKey 写入这些关系，返回值供后续绑定和落库使用。
     *
     * @param form 已发布表单，声明允许提交的字段和关联节点
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @return 仅保留该发布表单允许的关系数据的可变副本
     */
    private Map<String, Object> filterUndeclaredRelationData(
            EntityForm form,
            Map<String, Object> submittedData) {
        Map<String, Object> result = mutable(submittedData);
        if (form == null
                || !StringUtils.hasText(form.getEntityId())) {
            return result;
        }
        List<EntityRelation> relations = publishedRelationService == null
                ? entityRelationMapper.selectByParentEntityId(
                form.getEntityId())
                : publishedRelationService.listByParentEntityId(
                form.getEntityId());
        if (relations == null || relations.isEmpty()) {
            return result;
        }
        Set<String> declaredRelationFields =
                declaredRelationFields(
                        form,
                        relations);
        for (EntityRelation relation : relations) {
            String dataKey = effectiveDataKey(relation);
            if (StringUtils.hasText(dataKey)
                    && !declaredRelationFields.contains(
                            dataKey)) {
                result.remove(dataKey);
            }
        }
        return result;
    }

    /**
     * 收集发布字段和节点声明的关联 dataKey，供提交前剔除未授权关系值。
     *
     * @param form 已发布表单，从字段与节点中读取声明的关系引用
     * @param relations 实体实际定义的关系，用于匹配节点 bindingRef
     * @return 提交时允许保留的关联数据键集合
     */
    private Set<String> declaredRelationFields(
            EntityForm form,
            List<EntityRelation> relations) {
        Set<String> declared = new LinkedHashSet<>();
        for (EntityFormField field :
                form.getFields() == null
                        ? List.<EntityFormField>of()
                        : form.getFields()) {
            if (StringUtils.hasText(field.getFieldCode())) {
                declared.add(field.getFieldCode());
            }
        }
        for (EntityFormNode node :
                form.getNodes() == null
                        ? List.<EntityFormNode>of()
                        : form.getNodes()) {
            Map<String, Object> props =
                    nodeProperties(node);
            Object fieldCodeValue =
                    props.get("fieldCode");
            String fieldCode =
                    fieldCodeValue == null
                            ? null
                            : String.valueOf(fieldCodeValue);
            if (StringUtils.hasText(fieldCode)) {
                declared.add(fieldCode);
            }
            String bindingRef = node.getBindingRef();
            if (!StringUtils.hasText(bindingRef)) {
                continue;
            }
            for (EntityRelation relation : relations) {
                if (bindingRef.equals(
                                relation.getRelationCode())
                        || bindingRef.equals(
                                relation.getParentFieldCode())
                        || bindingRef.equals(
                                effectiveDataKey(relation))) {
                    declared.add(effectiveDataKey(relation));
                }
            }
        }
        return declared;
    }

    /**
     * 优先读取当前节点属性文档，旧发布快照才回退 legacyPropsDocument。
     *
     * @param node 已发布节点，优先读取当前属性文档，兼容旧属性字段
     * @return 节点属性 Map；无文档时为空 Map
     */
    private Map<String, Object> nodeProperties(
            EntityFormNode node) {
        if (node == null) {
            return Map.of();
        }
        String document =
                StringUtils.hasText(node.getPropsDocument())
                        ? node.getPropsDocument()
                        : node.getLegacyPropsDocument();
        return StringUtils.hasText(document)
                ? codec.readObject(
                        document,
                        "已发布表单节点属性")
                : Map.of();
    }

    /**
     * 与关系运行时共用实际数据键，避免过滤阶段按关系编码误删已发布字段。
     *
     * @param relation 实体关系，解析它在表单提交数据中的实际字段键
     * @return 关系运行时使用的数据键，供过滤和绑定匹配
     */
    private String effectiveDataKey(EntityRelation relation) {
        if (publishedRelationService != null) {
            return publishedRelationService.effectiveDataKey(relation);
        }
        if (StringUtils.hasText(relation.getDataKey())) {
            return relation.getDataKey();
        }
        if (StringUtils.hasText(relation.getParentFieldCode())) {
            return relation.getParentFieldCode();
        }
        return relation.getRelationCode();
    }

    /**
     * 依序执行一个归属对象的 BEFORE_SUBMIT 绑定并合并输出。ownerKey 与
     * bindingIndex 区分同表单的多个步骤，nestedContext 再区分子表行；
     * 发布坐标和输入指纹共同进入幂等键，供接口扩展去重及安全解析使用。
     *
     * @param bindings 当前表单、节点或字段的发布绑定配置
     * @param form 所属发布表单，用于请求上下文和目标校验
     * @param ownerKey 绑定归属键，与序号和子表行组合成幂等范围
     * @param targetType 绑定目标类别，供服务端校验发布配置
     * @param targetKey 绑定目标字段编码或节点键
     * @param entityCode 实体编码，用于定位默认表单或根实体校验范围
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param record 逐步更新的提交数据；前一步输出作为后一步输入
     * @param executionContext 单次提交追踪上下文，用于绑定输入和幂等键生成
     * @param resolved 固定发布快照及版本坐标，保证绑定按同一版本执行
     * @param nestedContext 子表行和父记录坐标，区分递归绑定实例
     * @param executionMode 正式提交或安全预览，决定是否跳过有副作用绑定
     */
    private void executeBindings(
            Map<String, Object> bindings,
            EntityForm form,
            String ownerKey,
            String targetType,
            String targetKey,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> record,
            FormSubmissionExecutionContext executionContext,
            ResolvedEntityFormRelease resolved,
            PublishedSubFormSubmissionProcessor.Context nestedContext,
            BindingExecutionMode executionMode) {
        if (bindings == null) {
            return;
        }
        Object configured = bindings.get(
                UiDataSourceUsages.BEFORE_SUBMIT);
        if (configured == null) {
            return;
        }
        List<?> values = configured instanceof List<?> list
                ? list : List.of(configured);
        int bindingIndex = 0;
        for (Object value : values) {
            String extensionId = interfaceExtensionId(value);
            String operationCode = operationCode(value);
            if (!StringUtils.hasText(extensionId)) {
                throw new IllegalArgumentException(
                        "BEFORE_SUBMIT 接口绑定缺少 extensionId");
            }
            if (!hasUnifiedInterfaceReference(value)
                    && !StringUtils.hasText(operationCode)) {
                // 只有不可变历史快照才允许旧 serviceId，
                // 必须搭配 operationCode 才能解析迁移后的单接口记录。
                throw new IllegalArgumentException(
                        "BEFORE_SUBMIT 历史接口绑定缺少 operationCode");
            }
            if (executionMode
                    == BindingExecutionMode.SIDE_EFFECT_FREE_PREVIEW
                    && !sideEffectFree(value)) {
                throw new FormSubmissionPreviewDeferredException(
                        "表单提交前处理包含未声明无副作用的绑定，"
                                + "需在正式提交后确定下一审批节点: "
                                + effectiveOwnerKey(
                                nestedContext, ownerKey));
            }
            FormSubmissionExecutionContext safeExecutionContext =
                    safeExecutionContext(
                            executionContext,
                            mode);
            String effectiveOwnerKey =
                    nestedContext.ownerKey(ownerKey);
            // 映射前使用当前记录形成指纹：前一步输出会改变下一步输入，
            // 同一业务追踪键下重试相同输入可去重，输入变化则必须重新执行。
            String idempotencyKey =
                    safeExecutionContext.bindingIdempotencyKey(
                            form.getId(),
                            resolved.releaseId(),
                            effectiveOwnerKey,
                            extensionId,
                            bindingIndex,
                            bindingInputFingerprint(
                                    recordId,
                                    mode,
                                    record,
                                    safeExecutionContext,
                                    nestedContext));
            UiExtensionExecuteRequest request =
                    new UiExtensionExecuteRequest();
            request.setUsage(
                    UiDataSourceUsages.BEFORE_SUBMIT);
            request.setOperationCode(operationCode);
            request.setConfigType("FORM");
            request.setConfigId(form.getId());
            request.setTargetType(targetType);
            request.setTargetKey(targetKey);
            request.setReleaseId(resolved.releaseId());
            request.setReleaseVersion(
                    resolved.releaseVersion());
            request.setServerPinnedRelease(
                    resolved.pinned());
            request.setEntityCode(entityCode);
            request.setServerIdempotencyKey(idempotencyKey);
            Map<String, Object> rawInput =
                    new LinkedHashMap<>();
            rawInput.put(
                    "recordId",
                    recordId == null ? "" : recordId);
            rawInput.put(
                    "formData",
                    new LinkedHashMap<>(record));
            rawInput.put("changedField", Map.of());
            rawInput.put(
                    "params",
                    nestedContext.params());
            rawInput.put(
                    "parent",
                    nestedContext.parent());
            rawInput.put(
                    "row",
                    nestedContext.row());
            rawInput.put(
                    "mode",
                    mode == null ? "edit" : mode);
            rawInput.put(
                    "businessTraceKey",
                    safeExecutionContext.businessTraceKey());
            rawInput.put(
                    "idempotencyKey",
                    idempotencyKey);
            Map<String, Object> context =
                    safeExecutionContext.runtimeContext();
            context.put(
                    "mode",
                    mode == null ? "edit" : mode);
            context.put("formId", form.getId());
            context.put("entityId", form.getEntityId());
            context.put(
                    "bindingOwner",
                    effectiveOwnerKey);
            context.put("bindingIndex", bindingIndex);
            context.put("extensionId", extensionId);
            context.put("idempotencyKey", idempotencyKey);
            context.putAll(
                    nestedContext.runtimeValues());
            Map<String, Object> mappingSource =
                    new LinkedHashMap<>();
            mappingSource.put("data", record);
            mappingSource.put("context", context);
            mappingSource.put("input", rawInput);
            mappingSource.put(
                    "parent",
                    nestedContext.parent());
            mappingSource.put(
                    "params",
                    nestedContext.params());
            mappingSource.put(
                    "row",
                    nestedContext.row());
            mappingSource.put(
                    "relation",
                    nestedContext.relation());
            Object mappedInput = applyMapping(
                    mapping(value, "inputMapping"),
                    mappingSource,
                    rawInput);
            if (!(mappedInput instanceof Map<?, ?> inputMap)) {
                throw new IllegalArgumentException(
                        "BEFORE_SUBMIT 输入映射结果必须为对象");
            }
            Map<String, Object> trustedInput =
                    stringMap(inputMap);
            trustedInput.put(
                    "businessTraceKey",
                    safeExecutionContext.businessTraceKey());
            trustedInput.put(
                    "idempotencyKey",
                    idempotencyKey);
            request.setInput(trustedInput);
            Object response = hasUnifiedInterfaceReference(value)
                    ? dataSourceService.execute(extensionId, request)
                    : dataSourceService.executeOperation(
                            extensionId, operationCode, request);
            response = applyMapping(
                    mapping(value, "outputMapping"),
                    Map.of(
                            "data",
                            response == null ? Map.of() : response,
                            "response",
                            response == null ? Map.of() : response),
                    response);
            if (response instanceof Map<?, ?> map) {
                mergeMappedOutput(record, map);
            }
            bindingIndex++;
        }
    }

    /**
     * 预览只允许绑定明确声明无副作用；缺省值按有副作用处理并延期预览。
     *
     * @param binding 发布绑定配置，只有明确写入 sideEffectFree=true 才允许预览执行
     * @return 绑定明确声明无副作用时为 true
     */
    private boolean sideEffectFree(Object binding) {
        return binding instanceof Map<?, ?> map
                && Boolean.TRUE.equals(map.get("sideEffectFree"));
    }

    /**
     * 将记录、模式和子表行上下文规范化为稳定指纹；调用方把它并入幂等键，
     * 使重试保留结果，同时防止预览后修改数据仍读到旧接口响应。
     *
     * @param recordId 已有记录 ID；新增时为空，后续参与绑定输入和最终记录校验
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @param record 当前绑定前的表单值；变化后必须产生不同指纹
     * @param executionContext 单次提交追踪上下文，用于绑定输入和幂等键生成
     * @param nestedContext 子表行和父记录数据，区分递归绑定输入
     * @return 规范化 JSON 指纹，后续并入接口调用幂等键
     */
    private String bindingInputFingerprint(
            String recordId,
            String mode,
            Map<String, Object> record,
            FormSubmissionExecutionContext executionContext,
            PublishedSubFormSubmissionProcessor.Context nestedContext) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("recordId", recordId == null ? "" : recordId);
        material.put("mode", mode == null ? "edit" : mode);
        material.put("formData", new LinkedHashMap<>(record));
        material.put("context", executionContext.runtimeContext());
        if (nestedContext != null) {
            material.put("params", nestedContext.params());
            material.put("parent", nestedContext.parent());
            material.put("row", nestedContext.row());
            material.put("relation", nestedContext.relation());
        }
        String document = codec.write(
                material,
                "BEFORE_SUBMIT 幂等输入");
        return codec.canonicalize(
                document,
                "BEFORE_SUBMIT 幂等输入");
    }

    /**
     * 子表行坐标并入归属键后，相同绑定在不同行上得到独立幂等键。
     *
     * @param nestedContext 子表上下文；存在时将行坐标并入归属键
     * @param ownerKey 表单、节点或字段原始归属键
     * @return 区分子表行的最终绑定归属键
     */
    private String effectiveOwnerKey(
            PublishedSubFormSubmissionProcessor.Context nestedContext,
            String ownerKey) {
        return nestedContext == null
                ? ownerKey : nestedContext.ownerKey(ownerKey);
    }

    /**
     * 独立调用缺少执行上下文时生成新追踪键，已有请求则沿用其重试身份。
     *
     * @param executionContext 单次提交追踪上下文，用于绑定输入和幂等键生成
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @return 可供绑定幂等使用的执行上下文
     */
    private FormSubmissionExecutionContext safeExecutionContext(
            FormSubmissionExecutionContext executionContext,
            String mode) {
        return executionContext == null
                ? FormSubmissionExecutionContext.standalone(
                        "FORM_" + normalizeOperation(mode))
                : executionContext;
    }

    /**
     * 优先用稳定节点 ID 定位绑定，兼容未带 ID 的历史发布节点。
     *
     * @param node 已发布节点，优先使用稳定 ID 作为绑定归属坐标
     * @return 节点绑定的稳定归属键
     */
    private String nodeOwnerKey(EntityFormNode node) {
        if (StringUtils.hasText(node.getId())) {
            return "node:" + node.getId();
        }
        return "node:" + String.valueOf(node.getNodeKey());
    }

    /**
     * 历史字段绑定以字段 ID 或编码形成归属键，供幂等去重区分步骤。
     *
     * @param field 历史发布字段，优先用字段 ID 区分不同绑定
     * @return 字段绑定的稳定归属键
     */
    private String fieldOwnerKey(EntityFormField field) {
        if (StringUtils.hasText(field.getId())) {
            return "field:" + field.getId();
        }
        return "field:" + String.valueOf(field.getFieldCode());
    }

    /**
     * 有字段编码的节点按 FIELD 校验目标，否则按 NODE 校验发布绑定。
     *
     * @param node 已发布节点，字段属性决定服务端目标校验类型
     * @return FIELD 或 NODE 目标类型
     */
    private String nodeTargetType(EntityFormNode node) {
        return StringUtils.hasText(text(
                nodeProperties(node).get("fieldCode")))
                ? "FIELD"
                : "NODE";
    }

    /**
     * 与 targetType 配套生成字段编码或节点键，供服务端定位发布配置。
     *
     * @param node 已发布节点，提取字段编码或节点键供绑定目标校验
     * @return 与目标类型配套的字段编码或节点键
     */
    private String nodeTargetKey(EntityFormNode node) {
        String fieldCode = text(
                nodeProperties(node).get("fieldCode"));
        return StringUtils.hasText(fieldCode)
                ? fieldCode
                : node.getNodeKey();
    }

    /**
     * 空模式按 EDIT 生成提交上下文操作码，保证幂等材料稳定。
     *
     * @param mode 提交模式，决定默认值、校验规则和幂等操作码
     * @return 大写操作码；空模式按 EDIT 处理
     */
    private static String normalizeOperation(String mode) {
        return StringUtils.hasText(mode)
                ? mode.trim().toUpperCase()
                : "EDIT";
    }

    /**
     * 构造子表处理器并复用当前发布解析与校验依赖，供节点递归提交。
     *
     * @return 复用当前发布解析、模式校验和编解码依赖的子表处理器
     */
    private PublishedSubFormSubmissionProcessor
            subFormSubmissionProcessor() {
        return new PublishedSubFormSubmissionProcessor(
                entityDefinitionMapper,
                releaseService,
                schemaValidator,
                codec);
    }

    /**
     * 新快照使用 extensionId，旧 serviceId 仅作不可变发布快照兼容。
     *
     * @param value 新旧发布绑定项；优先 extensionId，兼容旧 serviceId
     * @return 可解析的接口扩展 ID；配置不合法时为 null
     */
    private String interfaceExtensionId(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object extensionId = map.get("extensionId");
            if (extensionId == null) {
                extensionId = map.get("serviceId");
            }
            return extensionId == null
                    ? null : String.valueOf(extensionId);
        }
        return null;
    }

    /**
     * 新版 extensionId 可直接执行；历史 serviceId 还需 operationCode 才能解析接口。
     *
     * @param value 绑定项，用于判断是否有新版 extensionId
     * @return 可直接按统一接口扩展解析时为 true
     */
    private boolean hasUnifiedInterfaceReference(Object value) {
        return value instanceof Map<?, ?> map
                && StringUtils.hasText(text(map.get("extensionId")));
    }

    /**
     * 读取历史接口绑定的操作码，供旧快照路由到迁移后的单接口记录。
     *
     * @param value 历史绑定项，从中提取旧接口操作码
     * @return 历史操作码；未配置时为 null
     */
    private String operationCode(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Object operationCode = map.get("operationCode");
        return operationCode == null
                ? null : String.valueOf(operationCode);
    }

    /**
     * 保留 null 为缺失值，避免把未配置字段误当作字面字符串 "null"。
     *
     * @param value 可为空的配置标量
     * @return 字符串值；空值保留为 null
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 从绑定读取输入或输出映射并规范化键，后续路径映射只处理字符串目标。
     *
     * @param binding 发布绑定项，包含输入或输出映射配置
     * @param key 需要读取的映射类型字段名
     * @return 键已归一化的路径映射；缺失时为空 Map
     */
    private Map<String, Object> mapping(
            Object binding,
            String key) {
        if (!(binding instanceof Map<?, ?> map)
                || !(map.get(key) instanceof Map<?, ?> value)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((childKey, childValue) ->
                result.put(String.valueOf(childKey), childValue));
        return result;
    }

    /**
     * 将接口输入 Map 的键归一为字符串，供扩展请求序列化使用。
     *
     * @param source 接口输入 Map，其键可能不是字符串
     * @return 键统一转换为字符串的可序列化 Map
     */
    private Map<String, Object> stringMap(
            Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) ->
                result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * 递归合并数据源映射补丁，同一对象下的不同叶子字段均需保留。
     *
     * <p>映射步骤仍按顺序执行；后一步只有命中同一叶子路径时才覆盖前值，数组和标量按叶子值
     * 整体替换。每层都会创建可变副本，避免发布快照或 Provider 返回不可变 Map 时写入失败。
     *
     * @param target 当前提交记录，接收合并后的叶子值
     * @param patch 本轮 Provider 输出补丁；同一路径覆盖，其他叶子保留
     */
    static void mergeMappedOutput(
            Map<String, Object> target,
            Map<?, ?> patch) {
        patch.forEach((rawKey, value) -> {
            String key = String.valueOf(rawKey);
            if (value instanceof Map<?, ?> childPatch) {
                Map<String, Object> merged = new LinkedHashMap<>();
                Object existing = target.get(key);
                if (existing instanceof Map<?, ?> existingMap) {
                    existingMap.forEach((existingKey, existingValue) ->
                            merged.put(
                                    String.valueOf(existingKey),
                                    existingValue));
                }
                mergeMappedOutput(merged, childPatch);
                target.put(key, merged);
                return;
            }
            target.put(key, value);
        });
    }

    /**
     * 按目标路径构造映射结果；支持 literal 常量与来源路径，未配置映射时
     * 原样返回 fallback，使旧发布绑定保持原输入/输出协议。
     *
     * @param mapping 目标路径到来源选择器或 literal 常量的映射
     * @param source 用于逐路径读取的当前接口输入或响应
     * @param fallback 无映射配置时保持的旧协议原始值
     * @return 按目标路径构造的映射结果；无配置时返回 fallback
     */
    private Object applyMapping(
            Map<String, Object> mapping,
            Map<String, Object> source,
            Object fallback) {
        if (mapping == null || mapping.isEmpty()) {
            return fallback;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        mapping.forEach((targetPath, selector) -> {
            Object value;
            if (selector instanceof Map<?, ?> literal
                    && literal.containsKey("literal")) {
                value = literal.get("literal");
            } else {
                value = resolvePath(
                        source,
                        selector == null
                                ? "" : String.valueOf(selector));
            }
            setPath(result, targetPath, value);
        });
        return result;
    }

    /**
     * 逐段读取映射来源对象；中途不是 Map 时返回 null 而不推断其他结构。
     *
     * @param source 映射来源对象，逐层查找嵌套字段
     * @param path 点分隔的来源字段路径
     * @return 路径对应的值；中途缺失或非 Map 时为 null
     */
    private Object resolvePath(
            Map<String, Object> source,
            String path) {
        Object current = source;
        for (String part : path.split("\\.")) {
            if (part.isBlank()) {
                continue;
            }
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    /**
     * 为输出映射创建嵌套对象，最终由 mergeMappedOutput 合入提交记录。
     *
     * @param target 正在构造的映射结果 Map
     * @param path 点分隔的输出目标路径，决定嵌套对象位置
     * @param value 写入目标叶子字段的值
     */
    @SuppressWarnings("unchecked")
    private void setPath(
            Map<String, Object> target,
            String path,
            Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int index = 0; index < parts.length - 1; index++) {
            if (parts[index].isBlank()) {
                continue;
            }
            Object child = current.get(parts[index]);
            if (!(child instanceof Map<?, ?>)) {
                child = new LinkedHashMap<String, Object>();
                current.put(parts[index], child);
            }
            current = (Map<String, Object>) child;
        }
        if (parts.length > 0 && !parts[parts.length - 1].isBlank()) {
            current.put(parts[parts.length - 1], value);
        }
    }

    /**
     * 将兼容的 data 包装提交展平成可写副本；顶层字段仅补缺，不覆盖 data 内
     * 同名值，确保后续数据源、校验及落库看到同一份字段值。
     *
     * @param submittedData 客户端表单数据，复制后依次应用绑定映射和校验
     * @return 供绑定和校验修改的平铺副本
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mutable(
            Map<String, Object> submittedData) {
        Map<String, Object> source =
                submittedData == null
                        ? Map.of() : submittedData;
        Object nested = source.get("data");
        if (nested instanceof Map<?, ?> nestedMap) {
            Map<String, Object> result =
                    new LinkedHashMap<>(
                            (Map<String, Object>) nestedMap);
            source.forEach((key, value) -> {
                if (!"data".equals(key)) {
                    result.putIfAbsent(key, value);
                }
            });
            return result;
        }
        return new LinkedHashMap<>(source);
    }

    /**
     * 定义绑定执行模式的可选值；调用方据此选择对应的处理分支。
     */
    private enum BindingExecutionMode {
        AUTHORITATIVE,
        SIDE_EFFECT_FREE_PREVIEW
    }

    /**
     * 热修复观察属于旁路指标，记录失败不能改变权威表单提交结果。
     *
     * @param resolved 本次实际生效的发布身份，作为观察指标坐标
     * @param successful 权威提交是否成功，用于聚合成功率
     * @param errorMessage 失败原因；失败时随指标记录供排障
     */
    private void observeSubmission(
            ResolvedEntityFormRelease resolved,
            boolean successful,
            String errorMessage) {
        if (hotfixObservationPort == null || resolved == null) {
            return;
        }
        try {
            hotfixObservationPort.recordReleaseMetric(
                    resolved.effectiveReleaseId(),
                    "FORM_SUBMIT",
                    successful,
                    errorMessage);
        } catch (RuntimeException ignored) {
            // 观察数据不得改变表单提交结果，告警由治理指标链路自身处理。
        }
    }

}
