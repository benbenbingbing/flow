package com.workflow.entity.form.application;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 解析已发布的表单公式。变量值始终作为数据读取，分支按需求值。 */
public final class FormContextFormula {
    private static final Pattern TOKEN = Pattern.compile(
            "\\s*(\\$\\{(?:ctx\\.[A-Za-z][\\w.]*|(?:(?:field|param)\\.)?\\w+)}|#\\{\\w+}|"
                    + "[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+|[A-Za-z_]\\w*|"
                    + "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|==|!=|[<>]=?|[-+*/%(),])");
    private static final Set<String> COMPARISONS = Set.of("==", "!=", "<", "<=", ">", ">=");
    private static final Map<String, Integer> ARITIES = Map.of(
            "TODAY", 0, "IF", 3, "COALESCE", 2, "ADD_DAYS", 2, "DATEDIFF", 2, "WORKDAYDIFF", 2);
    private static final MathContext PRECISION = MathContext.DECIMAL128;

    record Input(Map<String, Object> record, Map<String, Object> parameters, Map<String, Object> variables, LocalDate today) { }

    @FunctionalInterface
    private interface Expression {
        Object evaluate(Input input);
    }

    private final Expression expression;
    private final Set<String> contextKeys;
    private final Set<String> fieldKeys;

    private FormContextFormula(Expression expression, Set<String> contextKeys, Set<String> fieldKeys) {
        this.expression = expression;
        this.contextKeys = Set.copyOf(contextKeys);
        this.fieldKeys = Set.copyOf(fieldKeys);
    }

    /** 编译配置并记录依赖，供发布预检和运行时拓扑求值共用。 */
    public static FormContextFormula compile(String source) {
        Parser parser = new Parser(source);
        Expression expression = parser.expression();
        if (!parser.peek().isEmpty()) throw invalid();
        return new FormContextFormula(expression, parser.contextKeys, parser.fieldKeys);
    }

    public Set<String> contextKeys() { return contextKeys; }
    public Set<String> fieldKeys() { return fieldKeys; }

    Object evaluate(Map<String, Object> record, Map<String, Object> params, Map<String, Object> variables, LocalDate today) {
        return expression.evaluate(new Input(record, params, variables, today));
    }

    private static final class Parser {
        private final List<String> tokens = new ArrayList<>();
        private final Set<String> contextKeys = new LinkedHashSet<>();
        private final Set<String> fieldKeys = new LinkedHashSet<>();
        private int position;

        private Parser(String source) {
            var matcher = TOKEN.matcher(source);
            int offset = 0;
            while (offset < source.length() && matcher.region(offset, source.length()).lookingAt()) {
                tokens.add(matcher.group(1));
                offset = matcher.end();
            }
            if (!source.substring(offset).isBlank() || tokens.isEmpty() || tokens.size() > 512) throw invalid();
            tokens.add("");
        }

        private String peek() { return tokens.get(position); }
        private String take() { return tokens.get(position++); }
        private boolean match(String token) {
            if (!token.equals(peek())) return false;
            take();
            return true;
        }
        private void expect(String token) {
            if (!match(token)) throw invalid();
        }

        private Expression expression() {
            Expression left = sum();
            if (!COMPARISONS.contains(peek())) return left;
            String operator = take();
            Expression right = sum();
            return input -> compare(operator, left.evaluate(input), right.evaluate(input));
        }

        private Expression sum() {
            Expression value = term();
            while ("+".equals(peek()) || "-".equals(peek())) value = operation(take(), value, term());
            return value;
        }

        private Expression term() {
            Expression value = primary();
            while (Set.of("*", "/", "%").contains(peek())) value = operation(take(), value, primary());
            return value;
        }

        private Expression primary() {
            if (match("-")) {
                Expression operand = primary();
                return input -> {
                    Object value = operand.evaluate(input);
                    return empty(value) ? null : number(value).negate(PRECISION);
                };
            }
            if (match("+")) return primary();
            if (match("(")) {
                Expression group = expression();
                expect(")");
                return group;
            }
            if (peek().isEmpty()) throw invalid();
            String token = take();
            if (token.startsWith("${") || token.startsWith("#{")) return reference(token.substring(2, token.length() - 1));
            char first = token.charAt(0);
            if (first == '\'' || first == '"') {
                String value = token.substring(1, token.length() - 1).replaceAll("\\\\(.)", "$1");
                return input -> value;
            }
            if (Character.isDigit(first) || first == '.') {
                BigDecimal value = new BigDecimal(token);
                return input -> value;
            }
            if (!Character.isLetter(first) && first != '_') throw invalid();
            if (match("(")) {
                String name = token.toUpperCase(Locale.ROOT);
                List<Expression> args = new ArrayList<>();
                if (!match(")")) {
                    do {
                        args.add(expression());
                    } while (match(","));
                    expect(")");
                }
                if (!Integer.valueOf(args.size()).equals(ARITIES.get(name))) {
                    throw new IllegalArgumentException("函数或参数个数无效：" + token);
                }
                return input -> function(name, args, input);
            }
            return switch (token) {
                case "true" -> input -> true;
                case "false" -> input -> false;
                case "null" -> input -> null;
                default -> reference(token);
            };
        }

        private Expression reference(String key) {
            if (key.startsWith("ctx.")) {
                String name = key.substring(4);
                contextKeys.add(name);
                return input -> input.variables().get(name);
            }
            if (key.startsWith("param.")) return input -> input.parameters().get(key.substring(6));
            String name = key.startsWith("field.") ? key.substring(6) : key;
            fieldKeys.add(name);
            return input -> input.record().get(name);
        }
    }

    private static Expression operation(String operator, Expression left, Expression right) {
        return input -> {
            Object first = left.evaluate(input);
            Object second = right.evaluate(input);
            if (empty(first) || empty(second)) return null;
            if ("+".equals(operator) && (first instanceof String && !numeric(first) || second instanceof String && !numeric(second))) {
                return first.toString() + second;
            }
            BigDecimal a = number(first);
            BigDecimal b = number(second);
            return switch (operator) {
                case "+" -> a.add(b, PRECISION);
                case "-" -> a.subtract(b, PRECISION);
                case "*" -> a.multiply(b, PRECISION);
                case "/" -> a.divide(b, PRECISION);
                case "%" -> a.remainder(b, PRECISION);
                default -> throw invalid();
            };
        };
    }

    private static Boolean compare(String operator, Object first, Object second) {
        if (empty(first) || empty(second)) return null;
        int compare;
        // 两个字符串直接比较，可区分有前导零的 ID。数值字段与数字常量可直接比较。
        if (first instanceof Number && numeric(second) || second instanceof Number && numeric(first)) {
            compare = number(first).compareTo(number(second));
        } else if (first instanceof String left && second instanceof String right) {
            compare = left.compareTo(right);
        } else {
            if (!Set.of("==", "!=").contains(operator)) return null;
            compare = first.equals(second) ? 0 : 1;
        }
        return switch (operator) {
            case "==" -> compare == 0;
            case "!=" -> compare != 0;
            case "<" -> compare < 0;
            case "<=" -> compare <= 0;
            case ">" -> compare > 0;
            default -> compare >= 0;
        };
    }

    private static Object function(String name, List<Expression> args, Input input) {
        if ("TODAY".equals(name)) return input.today().toString();
        Object first = args.get(0).evaluate(input);
        if ("COALESCE".equals(name)) return empty(first) ? args.get(1).evaluate(input) : first;
        if ("IF".equals(name)) {
            if (empty(first)) return null;
            return args.get(Set.of("true", "1").contains(first.toString()) ? 1 : 2).evaluate(input);
        }
        Object second = args.get(1).evaluate(input);
        if (empty(first) || empty(second)) return null;
        LocalDate date = LocalDate.parse(first.toString());
        if ("ADD_DAYS".equals(name)) return date.plusDays(number(second).longValueExact()).toString();
        LocalDate end = LocalDate.parse(second.toString());
        long distance = ChronoUnit.DAYS.between(date, end);
        if ("DATEDIFF".equals(name)) return distance;
        if (distance < 0) return 0;
        long weeks = (distance + 1) / 7;
        int rest = (int) ((distance + 1) % 7);
        long count = weeks * 5;
        for (int day = 0; day < rest; day++) {
            if (date.plusDays(day).getDayOfWeek().getValue() <= 5) count++;
        }
        return count;
    }

    private static boolean empty(Object value) { return value == null || "".equals(value); }

    private static boolean numeric(Object value) {
        return value instanceof Number || value instanceof String text && text.matches("[+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)");
    }

    private static BigDecimal number(Object value) {
        if (!numeric(value)) throw new IllegalArgumentException("公式运算需要数字");
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("上下文公式格式错误");
    }
}
