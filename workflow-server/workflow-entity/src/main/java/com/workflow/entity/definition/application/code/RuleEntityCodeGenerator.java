package com.workflow.entity.definition.application.code;

import com.workflow.core.logging.LogValue;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityCodeRule;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityCodeRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** 内置规则取号器。独立提交序列，业务回滚允许跳号；自定义扩展不继承这个独立事务。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEntityCodeGenerator {
    private final EntityCodeRuleMapper codeRuleMapper;
    /**
     * 生成数据编码
     * 使用数据库乐观锁保证并发安全
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 生成后的编码文本，供调用方比较或展示
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public String generateCode(String entityCode, EntityCodeRule configuration) {
        // 查询或创建编码规则
        EntityCodeRule sequence = getOrCreateRule(entityCode);
        // 格式使用调用入口固定的配置；并发重试只刷新计数，不能中途切换编码模式或格式。
        EntityCodeRule rule = configuration;
        rule.setCurrentSeq(sequence.getCurrentSeq());
        rule.setSeqDate(sequence.getSeqDate());
        
        // 计算当前日期字符串
        String currentDateStr = getCurrentDateStr(rule.getDateFormat());
        String displayDate = currentDateStr;
        
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
                    needReset = (seqDate.length() < 6 || !currentDateStr.substring(0, 6).equals(seqDate.substring(0, 6)));
                    break;
                case YEAR:
                    needReset = (seqDate.length() < 4 || !currentDateStr.substring(0, 4).equals(seqDate.substring(0, 4)));
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
                    code = buildCode(rule, displayDate, 1);
                    break;
                }
            } else {
                // 在同一天内递增
                int currentSeq = rule.getCurrentSeq() != null ? rule.getCurrentSeq() : 0;
                if (currentSeq == Integer.MAX_VALUE) throw new IllegalStateException("编码流水号已耗尽");
                int newSeq = currentSeq + 1;
                int updated = codeRuleMapper.updateSeq(entityCode, seqDate, currentSeq, newSeq);
                if (updated > 0) {
                    code = buildCode(rule, displayDate, newSeq);
                    break;
                }
            }
            
            // 更新失败，重新查询规则并重试
            retryCount++;
            log.warn("编码生成乐观锁冲突，第{}次重试", retryCount);
            
            Optional<EntityCodeRule> refreshed = codeRuleMapper.findByEntityCode(entityCode);
            if (refreshed.isPresent()) {
                rule.setCurrentSeq(refreshed.get().getCurrentSeq());
                rule.setSeqDate(refreshed.get().getSeqDate());
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
                            needReset = (seqDate.length() < 6 || !currentDateStr.substring(0, 6).equals(seqDate.substring(0, 6)));
                            break;
                        case YEAR:
                            needReset = (seqDate.length() < 4 || !currentDateStr.substring(0, 4).equals(seqDate.substring(0, 4)));
                            break;
                        case NEVER:
                            needReset = false;
                            break;
                    }
                }
            } else {
                // 规则被删除，重新创建
                EntityCodeRule created = createDefaultRule(entityCode);
                rule.setCurrentSeq(created.getCurrentSeq());
                rule.setSeqDate(created.getSeqDate());
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
    
}
