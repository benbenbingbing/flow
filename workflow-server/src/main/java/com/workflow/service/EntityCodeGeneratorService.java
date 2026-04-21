package com.workflow.service;

import com.workflow.entity.EntityCodeRule;
import com.workflow.mapper.EntityCodeRuleMapper;
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
    
    /**
     * 生成数据编码
     * 使用数据库乐观锁保证并发安全
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
        
        log.debug("生成编码成功：entityCode={}, code={}", entityCode, code);
        return code;
    }
    
    /**
     * 获取或创建编码规则
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
     */
    private EntityCodeRule createDefaultRule(String entityCode) {
        EntityCodeRule rule = EntityCodeRule.getDefault(entityCode);
        codeRuleMapper.insert(rule);
        log.info("创建默认编码规则：entityCode={}", entityCode);
        return rule;
    }
    
    /**
     * 根据日期格式获取当前日期字符串
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
     */
    public String previewCode(EntityCodeRule rule) {
        String dateStr = getCurrentDateStr(rule.getDateFormat());
        return buildCode(rule, dateStr, 1);
    }
    
    /**
     * 保存或更新编码规则
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRule(EntityCodeRule rule) {
        // 生成示例
        rule.setExample(previewCode(rule));
        
        Optional<EntityCodeRule> existing = codeRuleMapper.findByEntityCode(rule.getEntityCode());
        if (existing.isPresent()) {
            // 更新时保留当前序列号信息
            EntityCodeRule old = existing.get();
            rule.setId(old.getId());
            rule.setCurrentSeq(old.getCurrentSeq());
            rule.setSeqDate(old.getSeqDate());
            codeRuleMapper.updateById(rule);
        } else {
            rule.setCurrentSeq(0);
            rule.setSeqDate("");
            codeRuleMapper.insert(rule);
        }
        
        log.info("保存编码规则：entityCode={}", rule.getEntityCode());
    }
    
    /**
     * 获取实体的编码规则
     */
    public EntityCodeRule getRule(String entityCode) {
        return codeRuleMapper.findByEntityCode(entityCode)
                .orElseGet(() -> EntityCodeRule.getDefault(entityCode));
    }
}
