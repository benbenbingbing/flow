package com.workflow.entity.definition.application;

import com.workflow.contracts.entity.code.spi.EntityCodeGeneratorProvider;

import com.workflow.core.logging.LogValue;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityCodeRule;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityCodeRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import com.workflow.contracts.entity.code.*;
import com.workflow.entity.definition.application.code.*;
import com.workflow.entity.data.application.EntityCodeReservationService;
import com.workflow.core.error.BusinessConflictException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 实体编码生成服务
 * 提供并发安全的数据编码生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityCodeGeneratorService {
    
    private final EntityCodeRuleMapper codeRuleMapper;
    private final EntityDefinitionAccessPolicy entityAccessPolicy;
    private final RuleEntityCodeGenerator ruleGenerator;
    private final EntityCodeGeneratorRegistry registry;
    private final EntityCodeReservationService reservations;
    private final EntityCodeContextFactory contexts;

    /** 由主表/子表写入器传入经过字段校验的数据，生成时当前行还未入库。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public String generateCode(EntityCodeGenerationInput input) {
        return generateCode(contexts.create(input), input.suppliedCode());
    }

    /**
     * 当前业务记录 INSERT 前调用。自定义实现加入当前事务；预留最终编码与业务行一起提交。
     * suppliedCode 仅兼容旧规则模式的子行导入，自定义模式始终由服务端生成。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public String generateCode(EntityCodeGenerationContext context, String suppliedCode) {
        if (context == null || context.recordId() == null || context.recordId().isBlank()) {
            throw new IllegalArgumentException("编码生成必须先分配记录ID");
        }
        EntityCodeRule rule = codeRuleMapper.findByEntityCode(context.entityCode())
                .orElseGet(() -> EntityCodeRule.getDefault(context.entityCode()));
        String mode = mode(rule);
        String code;
        if ("CUSTOM".equals(mode)) {
            Map<String, Object> config = EntityCodeSnapshots.copy(rule.getGeneratorConfig());
            EntityCodeGeneratorProvider generator = registry.require(rule.getGeneratorCode(), context.entityCode(), config);
            code = generator.generate(context, config);
        } else if (suppliedCode != null && !suppliedCode.isBlank()) {
            code = suppliedCode;
        } else {
            validateConfiguration(rule);
            code = ruleGenerator.generateCode(context.entityCode(), rule);
        }
        validateCode(code);
        contexts.validateGeneratedCode(context.entityCode(), code);
        reservations.reserve(context.entityCode(), context.recordId(), code);
        return code;
    }

    /** 预览只调用无副作用的样例接口，不创建规则、不占号，也不预留唯一值。 */
    public String previewCode(EntityCodeRule rule) {
        validateConfiguration(rule);
        if ("RULE".equals(mode(rule))) {
            String preview = ruleGenerator.previewCode(rule);
            validateCode(preview);
            return preview;
        }
        Map<String, Object> config = EntityCodeSnapshots.copy(rule.getGeneratorConfig());
        return registry.require(rule.getGeneratorCode(), rule.getEntityCode(), config)
                .preview(new EntityCodePreviewContext(rule.getEntityCode(), LocalDateTime.now()), config)
                .map(code -> { validateCode(code); return code; }).orElse("");
    }

    /** 迁移预检也使用同一校验，不要求目标实体已经创建，不执行取号。 */
    public void validateConfiguration(EntityCodeRule rule) {
        if (rule == null || rule.getEntityCode() == null || rule.getEntityCode().isBlank()) {
            throw new IllegalArgumentException("实体编码不能为空");
        }
        rule.setGenerationMode(mode(rule));
        if ("CUSTOM".equals(rule.getGenerationMode())) {
            registry.require(rule.getGeneratorCode(), rule.getEntityCode(), rule.getGeneratorConfig());
            return;
        }
        if (rule.getPrefix() != null && rule.getPrefix().length() > EntityCodeRule.MAX_PREFIX_LENGTH) {
            throw new IllegalArgumentException("编码前缀长度不能超过20个字符");
        }
        if (rule.getDateFormat() == null || rule.getDateFormat().isEmpty()) rule.setDateFormat("yyyyMMdd");
        // 保留旧接口接受合法日期格式的行为，不把已有配置限制到页面下拉框的几个选项。
        try {
            java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern(
                    rule.getDateFormat().replace("-", "").replace("/", "")));
        } catch (java.time.DateTimeException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的编码日期格式", exception);
        }
        if (rule.getSeqLength() == null) rule.setSeqLength(6);
        if (rule.getSeqLength() < 1 || rule.getSeqLength() > 100) throw new IllegalArgumentException("序号长度必须为1到100位，完整编码不得超过100位");
        if (rule.getSeqType() == null) rule.setSeqType("DAY");
        if (!Set.of("DAY", "MONTH", "YEAR", "NEVER").contains(rule.getSeqType())) throw new IllegalArgumentException("不支持的序号重置周期");
    }

    public List<EntityCodeGeneratorRegistry.Option> generators(String entityCode) {
        entityAccessPolicy.requireDynamicByCode(entityCode);
        return registry.options(entityCode);
    }

    private String mode(EntityCodeRule rule) {
        String mode = rule.getGenerationMode() == null ? "RULE" : rule.getGenerationMode();
        if (!Set.of("RULE", "CUSTOM").contains(mode)) throw new IllegalArgumentException("不支持的编码生成方式");
        return mode;
    }

    private void validateCode(String code) {
        if (code == null || code.isBlank() || code.codePointCount(0, code.length()) > 100
                || !code.equals(code.strip()) || code.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessConflictException("ENTITY_CODE_INVALID", "生成的编码必须非空、无首尾空白或控制字符，且不超过100个字符");
        }
    }

    
    /**
     * 保存或更新编码规则
     *
     * @param rule 规则，作为 {@code rule.setExample} 的输入影响后续处理
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.CONFIGURE,
            operation = "保存实体编码规则",
            risk = AuditRiskLevel.HIGH,
            targetType = "ENTITY_CODE_RULE",
            captureArguments = true)
    public void saveRule(EntityCodeRule rule) {
        if (rule == null || rule.getEntityCode() == null || rule.getEntityCode().isBlank()) {
            throw new RuntimeException("实体编码不能为空");
        }
        String entityCode = rule.getEntityCode().trim();
        rule.setEntityCode(entityCode);
        if (rule.getPrefix() != null && rule.getPrefix().length() > EntityCodeRule.MAX_PREFIX_LENGTH) {
            throw new RuntimeException("编码前缀长度不能超过" + EntityCodeRule.MAX_PREFIX_LENGTH + "个字符");
        }
        entityAccessPolicy.requireDynamicByCodeForUpdate(entityCode);

        validateConfiguration(rule);
        if ("CUSTOM".equals(mode(rule))) reservations.validateExistingCodes(entityCode);
        // 保存配置只生成展示样例，禁止调用正式取号接口。
        rule.setExample(previewCode(rule));
        
        Optional<EntityCodeRule> existing = codeRuleMapper.findByEntityCode(entityCode);
        if (existing.isPresent()) {
            // 更新时保留当前序列号信息
            EntityCodeRule old = existing.get();
            rule.setId(old.getId());
            rule.setCurrentSeq(old.getCurrentSeq());
            rule.setSeqDate(old.getSeqDate());
            rule.setCreatedAt(old.getCreatedAt());
            rule.setUpdatedAt(null);
            // 只更新配置列，不能把读取到的旧序号写回，避免并发取号被回退。
            if ("CUSTOM".equals(mode(rule))) {
                rule.setPrefix(old.getPrefix());
                rule.setDateFormat(old.getDateFormat());
                rule.setSeqLength(old.getSeqLength());
                rule.setSeqType(old.getSeqType());
            }
            codeRuleMapper.updateConfiguration(rule);
        } else {
            // 新规则的主键只能由服务端生成，不能复用客户端或旧实体残留的ID。
            rule.setId(null);
            rule.setCurrentSeq(0);
            rule.setSeqDate("");
            rule.setCreatedAt(null);
            rule.setUpdatedAt(null);
            codeRuleMapper.insert(rule);
        }
        
        log.info("保存编码规则：entityCode={}", LogValue.safe(entityCode));
    }
    
    /**
     * 获取实体的编码规则
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 符合条件的实体编码规则结果，供调用方继续处理
     */
    public EntityCodeRule getRule(String entityCode) {
        if (entityCode == null || entityCode.isBlank()) {
            throw new RuntimeException("实体编码不能为空");
        }
        String normalizedEntityCode = entityCode.trim();
        entityAccessPolicy.requireDynamicByCode(normalizedEntityCode);
        return codeRuleMapper.findByEntityCode(normalizedEntityCode)
                .orElseGet(() -> EntityCodeRule.getDefault(normalizedEntityCode));
    }
}
