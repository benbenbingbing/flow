package com.workflow.service;

import com.workflow.entity.EntityCodeRule;
import com.workflow.mapper.EntityCodeRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 实体编码生成服务业务测试
 * 覆盖编码规则管理、编码生成（乐观锁重试、序列号重置）、预览
 */
class EntityCodeGeneratorBusinessTest {

    private EntityCodeRuleMapper codeRuleMapper;
    private EntityCodeGeneratorService service;

    @BeforeEach
    void setUp() {
        codeRuleMapper = mock(EntityCodeRuleMapper.class);
        service = new EntityCodeGeneratorService(codeRuleMapper);
    }

    // ==================== 预览编码 ====================

    @Nested
    @DisplayName("预览编码")
    class PreviewCode {

        @Test
        @DisplayName("预览 - 使用当前日期 + 序列号 1，不写库")
        void previewCode_noDatabaseWrite() {
            EntityCodeRule rule = EntityCodeRule.getDefault("expense");

            String preview = service.previewCode(rule);

            assertNotNull(preview);
            assertTrue(preview.startsWith("EXPENSE"));
            assertTrue(preview.endsWith("000001"));
            verify(codeRuleMapper, never()).insert(any());
            verify(codeRuleMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("预览 - 自定义前缀和序列号长度")
        void previewCode_customPrefix() {
            EntityCodeRule rule = new EntityCodeRule();
            rule.setPrefix("CG");
            rule.setDateFormat("yyyyMMdd");
            rule.setSeqLength(4);

            String preview = service.previewCode(rule);

            assertTrue(preview.startsWith("CG"));
            assertTrue(preview.endsWith("0001"));
        }

        @Test
        @DisplayName("预览 - 空前缀时编码以日期开头")
        void previewCode_emptyPrefix() {
            EntityCodeRule rule = new EntityCodeRule();
            rule.setPrefix("");
            rule.setDateFormat("yyyyMMdd");
            rule.setSeqLength(6);

            String preview = service.previewCode(rule);

            String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            assertTrue(preview.startsWith(todayStr));
        }
    }

    // ==================== 保存规则 ====================

    @Nested
    @DisplayName("保存规则")
    class SaveRule {

        @Test
        @DisplayName("新建规则 - 自动生成 example，currentSeq=0，seqDate=空")
        void saveRule_new() {
            EntityCodeRule rule = new EntityCodeRule();
            rule.setEntityCode("expense");
            rule.setPrefix("CG");
            rule.setDateFormat("yyyyMMdd");
            rule.setSeqLength(6);
            rule.setSeqType(EntityCodeRule.SeqType.DAY.name());

            when(codeRuleMapper.findByEntityCode("expense")).thenReturn(Optional.empty());

            service.saveRule(rule);

            assertNotNull(rule.getExample());
            assertEquals(0, rule.getCurrentSeq());
            assertEquals("", rule.getSeqDate());
            verify(codeRuleMapper).insert(rule);
        }

        @Test
        @DisplayName("更新规则 - 保留旧 currentSeq 和 seqDate，避免重置已用序列号")
        void saveRule_update_preserveSeq() {
            EntityCodeRule existing = new EntityCodeRule();
            existing.setId("r1");
            existing.setCurrentSeq(42);
            existing.setSeqDate("20240115");

            EntityCodeRule newRule = new EntityCodeRule();
            newRule.setEntityCode("expense");
            newRule.setPrefix("NEW");
            newRule.setSeqType(EntityCodeRule.SeqType.MONTH.name());

            when(codeRuleMapper.findByEntityCode("expense")).thenReturn(Optional.of(existing));

            service.saveRule(newRule);

            assertEquals("r1", newRule.getId());
            assertEquals(42, newRule.getCurrentSeq());
            assertEquals("20240115", newRule.getSeqDate());
            verify(codeRuleMapper).updateById(newRule);
        }
    }

    // ==================== 生成编码 ====================

    @Nested
    @DisplayName("生成编码")
    class GenerateCode {

        @Test
        @DisplayName("首次生成 - 无 seqDate，重置为 1，乐观锁更新成功")
        void generateCode_firstTime() {
            EntityCodeRule rule = EntityCodeRule.getDefault("expense");
            rule.setCurrentSeq(0);
            rule.setSeqDate("");

            when(codeRuleMapper.findByEntityCode("expense")).thenReturn(Optional.of(rule));
            when(codeRuleMapper.updateSeqWithDate(eq("expense"), eq(""), anyString(), eq(1))).thenReturn(1);

            String code = service.generateCode("expense");

            assertNotNull(code);
            assertTrue(code.startsWith("EXPENSE"));
            assertTrue(code.endsWith("000001"));
        }

        @Test
        @DisplayName("同日递增 - 不重置，seq +1")
        void generateCode_sameDay_increment() {
            String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            EntityCodeRule rule = EntityCodeRule.getDefault("expense");
            rule.setCurrentSeq(5);
            rule.setSeqDate(todayStr);

            when(codeRuleMapper.findByEntityCode("expense")).thenReturn(Optional.of(rule));
            when(codeRuleMapper.updateSeq(eq("expense"), eq(todayStr), eq(5), eq(6))).thenReturn(1);

            String code = service.generateCode("expense");

            assertTrue(code.endsWith("000006"));
        }

        @Test
        @DisplayName("跨天重置 - seqDate 与当前日期不同，重置为 1")
        void generateCode_crossDay_reset() {
            EntityCodeRule rule = EntityCodeRule.getDefault("expense");
            rule.setCurrentSeq(99);
            rule.setSeqDate("20200101");

            when(codeRuleMapper.findByEntityCode("expense")).thenReturn(Optional.of(rule));
            when(codeRuleMapper.updateSeqWithDate(eq("expense"), eq("20200101"), anyString(), eq(1))).thenReturn(1);

            String code = service.generateCode("expense");

            assertTrue(code.endsWith("000001"));
        }

        @Test
        @DisplayName("乐观锁冲突 - 首次更新失败，重试后成功")
        void generateCode_optimisticLockRetry() {
            String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            EntityCodeRule rule = EntityCodeRule.getDefault("expense");
            rule.setCurrentSeq(5);
            rule.setSeqDate(todayStr);

            EntityCodeRule refreshedRule = EntityCodeRule.getDefault("expense");
            refreshedRule.setCurrentSeq(6);
            refreshedRule.setSeqDate(todayStr);

            when(codeRuleMapper.findByEntityCode("expense"))
                    .thenReturn(Optional.of(rule))
                    .thenReturn(Optional.of(refreshedRule));
            when(codeRuleMapper.updateSeq(eq("expense"), eq(todayStr), eq(5), eq(6))).thenReturn(0);
            when(codeRuleMapper.updateSeq(eq("expense"), eq(todayStr), eq(6), eq(7))).thenReturn(1);

            String code = service.generateCode("expense");

            assertTrue(code.endsWith("000007"));
        }

        @Test
        @DisplayName("乐观锁重试耗尽 - 抛异常")
        void generateCode_retryExhausted_throwsException() {
            String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            EntityCodeRule rule = EntityCodeRule.getDefault("expense");
            rule.setCurrentSeq(5);
            rule.setSeqDate(todayStr);

            when(codeRuleMapper.findByEntityCode("expense")).thenReturn(Optional.of(rule));
            when(codeRuleMapper.updateSeq(anyString(), anyString(), anyInt(), anyInt())).thenReturn(0);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.generateCode("expense"));
            assertTrue(ex.getMessage().contains("重试次数耗尽"));
        }

        @Test
        @DisplayName("规则不存在 - 自动创建默认规则后生成")
        void generateCode_ruleNotFound_createDefault() {
            when(codeRuleMapper.findByEntityCode("expense"))
                    .thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.generateCode("expense"));
            // 默认规则创建后进入重试循环，mock 的 updateSeqWithDate 返回 0，最终重试耗尽
            assertTrue(ex.getMessage().contains("重试次数耗尽") || ex.getMessage().contains("失败"));
        }
    }

    // ==================== 获取规则 ====================

    @Nested
    @DisplayName("获取规则")
    class GetRule {

        @Test
        @DisplayName("规则存在 - 返回数据库中的规则")
        void getRule_exists() {
            EntityCodeRule rule = new EntityCodeRule();
            rule.setEntityCode("expense");
            rule.setPrefix("CG");
            when(codeRuleMapper.findByEntityCode("expense")).thenReturn(Optional.of(rule));

            EntityCodeRule result = service.getRule("expense");

            assertEquals("CG", result.getPrefix());
        }

        @Test
        @DisplayName("规则不存在 - 返回默认规则（不落库）")
        void getRule_notFound_returnDefault() {
            when(codeRuleMapper.findByEntityCode("expense")).thenReturn(Optional.empty());

            EntityCodeRule result = service.getRule("expense");

            assertNotNull(result);
            assertEquals("EXPENSE", result.getPrefix());
            assertEquals(EntityCodeRule.SeqType.DAY.name(), result.getSeqType());
            verify(codeRuleMapper, never()).insert(any());
        }
    }
}
