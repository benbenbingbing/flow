package com.workflow.process.condition.application;

import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 只比较本次流程变量，不访问候选 Provider 或读取实时外部数据。 */
@Component("_wfSelectionCondition")
public class WorkflowSelectionCondition {
    private static final String CODE = "[A-Za-z0-9][A-Za-z0-9._:/@-]{0,127}";
    private static final Pattern EXPRESSION_TOKENS = Pattern.compile(
            "(?<![\\w.])_wfSelectionCondition\\.matches\\(execution, '([A-Za-z_]\\w{0,99})', "
                    + "'(selectionEquals|selectionAny|selectionAll)', '(" + CODE + "(?:," + CODE + "){0,49})'\\)"
                    + "|'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"");

    /** 供 Flowable 顺序流调用；缺失、空值或类型不匹配均不命中，由默认连线兜底。 */
    public boolean matches(DelegateExecution execution, String field, String operator, String codes) {
        return execution != null && compare(execution.getVariable(field), operator, codes);
    }

    /**
     * 仅识别平台生成的精确调用。带引号的字面量作为整体跳过，防止把普通文本误当作可执行调用。
     * 发布校验用布尔常量代换后继续检查其余表达式；预览用同一套比较规则代入本地变量。
     */
    public static String substitute(String expression, Function<String, Object> variables) {
        Matcher tokens = EXPRESSION_TOKENS.matcher(expression);
        return tokens.replaceAll(token -> token.group(1) == null
                ? Matcher.quoteReplacement(token.group())
                : Boolean.toString(variables == null
                        || compare(variables.apply(token.group(1)), token.group(2), token.group(3))));
    }

    private static boolean compare(Object actual, String operator, String codes) {
        if (codes == null || codes.isEmpty()) return false;
        if ("selectionEquals".equals(operator)) return codes.equals(actual);
        if (!(actual instanceof Collection<?> values)) return false;
        var expected = Arrays.stream(codes.split(",", -1)).filter(code -> !code.isEmpty()).toList();
        if (expected.isEmpty()) return false;
        return switch (operator) {
            case "selectionAny" -> expected.stream().anyMatch(values::contains);
            case "selectionAll" -> values.containsAll(expected);
            default -> false;
        };
    }
}
