package com.workflow.entity.definition.application;

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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    
    /**
     * 生成数据编码
     * 使用数据库乐观锁保证并发安全
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 生成后的编码文本，供调用方比较或展示
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public String generateCode(String entityCode) {
        // 查询或创建编码规则
        EntityCodeRule rule = getOrCreateRule(entityCode);
        
        // 计算当前日期字符串
        String currentDateStr = getCurrentDateStr(rule.getDateFormat());
        
        // 判断是否需要重置序列号
        String seqDate = rule.getSeqDate();
        boolean needReset = false;
        
        if (seqDate == null || seqDate.isEmpty()) {
            needReset = true;
        } else {
            switch (EntityCodeRule.SeqType.valueOf(rule.getSeqType())) {
                case DAY:
                    needReset = !currentDateStr.equals(seqDate);
                    break;
                case MONTH:
                    needReset = !currentDateStr.substring(0, 6).equals(seqDate.substring(0, 6));
                    break;
                case YEAR:
                    needReset = !currentDateStr.substring(0, 4).equals(seqDate.substring(0, 4));
                    break;
                case NEVER:
                    needReset = false;
                    break;
            }
        }
        
        // 使用乐观锁更新序列号
        int retryCount = 0;
        int maxRetry = 10;
        String code = null;
        
        while (retryCount < maxRetry) {
            if (needReset) {
                // 需要重置序列号，从1开始
                int updated = codeRuleMapper.updateSeqWithDate(entityCode, seqDate, currentDateStr, 1);
                if (updated > 0) {
                    code = buildCode(rule, currentDateStr, 1);
                    break;
                }
            } else {
                // 在同一天内递增
                int currentSeq = rule.getCurrentSeq() != null ? rule.getCurrentSeq() : 0;
                int newSeq = currentSeq + 1;
                int updated = codeRuleMapper.updateSeq(entityCode, seqDate, currentSeq, newSeq);
                if (updated > 0) {
                    code = buildCode(rule, currentDateStr, newSeq);
                    break;
                }
            }
            
            // 更新失败，重新查询规则并重试
            retryCount++;
            log.warn("编码生成乐观锁冲突，第{}次重试", retryCount);
            
            Optional<EntityCodeRule> refreshed = codeRuleMapper.findByEntityCode(entityCode);
            if (refreshed.isPresent()) {
                rule = refreshed.get();
                seqDate = rule.getSeqDate();
                // 重新判断是否需要重置
                if (seqDate == null || seqDate.isEmpty()) {
                    needReset = true;
                } else {
                    switch (EntityCodeRule.SeqType.valueOf(rule.getSeqType())) {
                        case DAY:
                            needReset = !currentDateStr.equals(seqDate);
                            break;
                        case MONTH:
                            needReset = !currentDateStr.substring(0, 6).equals(seqDate.substring(0, 6));
                            break;
                        case YEAR:
                            needReset = !currentDateStr.substring(0, 4).equals(seqDate.substring(0, 4));
                            break;
                        case NEVER:
                            needReset = false;
                            break;
                    }
                }
            } else {
                // 规则被删除，重新创建
                rule = createDefaultRule(entityCode);
                needReset = true;
            }
        }
        
        if (code == null) {
            throw new RuntimeException("生成编码失败，重试次数耗尽");
        }
        
        log.debug("生成编码成功：entityCode={}, code={}", LogValue.safe(entityCode), LogValue.safe(code));
        return code;
    }
    
    /**
     * 获取或创建编码规则
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 符合条件的实体编码规则结果，供调用方继续处理
     */
    private EntityCodeRule getOrCreateRule(String entityCode) {
        Optional<EntityCodeRule> optional = codeRuleMapper.findByEntityCode(entityCode);
        if (optional.isPresent()) {
            return optional.get();
        }
        
        // 创建默认规则
        return createDefaultRule(entityCode);
    }
    
    /**
     * 创建默认编码规则
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 创建后的默认规则结果，供调用方继续处理
     */
    private EntityCodeRule createDefaultRule(String entityCode) {
        EntityCodeRule rule = EntityCodeRule.getDefault(entityCode);
        codeRuleMapper.insert(rule);
        log.info("创建默认编码规则：entityCode={}", LogValue.safe(entityCode));
        return rule;
    }
    
    /**
     * 根据日期格式获取当前日期字符串
     *
     * @param dateFormat 日期{@code format}，后续用于判断有效期或展示该事件的发生时间
     * @return 读取后的当前日期{@code str}文本，供调用方比较或展示
     */
    private String getCurrentDateStr(String dateFormat) {
        if (dateFormat == null || dateFormat.isEmpty()) {
            dateFormat = "yyyyMMdd";
        }
        // 处理Java日期格式，移除多余的字符
        String javaFormat = dateFormat
                .replace("yyyy", "yyyy")
                .replace("MM", "MM")
                .replace("dd", "dd")
                .replace("-", "")
                .replace("/", "");
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(javaFormat);
        return LocalDate.now().format(formatter);
    }
    
    /**
     * 构建最终编码
     *
     * @param rule 规则，作为 {@code code.append} 的输入影响后续处理
     * @param dateStr 日期{@code str}，作为 {@code code.append} 的输入影响后续处理
     * @param seq {@code seq}，供本方法构建编码时使用
     * @return 构建后的编码文本，供调用方比较或展示
     */
    private String buildCode(EntityCodeRule rule, String dateStr, int seq) {
        StringBuilder code = new StringBuilder();
        
        // 前缀
        if (rule.getPrefix() != null && !rule.getPrefix().isEmpty()) {
            code.append(rule.getPrefix());
        }
        
        // 日期
        code.append(dateStr);
        
        // 序列号（补零）
        int seqLength = rule.getSeqLength() != null ? rule.getSeqLength() : 6;
        String seqStr = String.format("%0" + seqLength + "d", seq);
        code.append(seqStr);
        
        return code.toString();
    }
    
    /**
     * 预览编码（不实际生成）
     *
     * @param rule 规则，作为 {@code getCurrentDateStr} 的输入影响后续处理
     * @return 处理后的预览编码文本，供调用方比较或展示
     */
    public String previewCode(EntityCodeRule rule) {
        String dateStr = getCurrentDateStr(rule.getDateFormat());
        return buildCode(rule, dateStr, 1);
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

        // 生成示例
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
            codeRuleMapper.updateById(rule);
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
