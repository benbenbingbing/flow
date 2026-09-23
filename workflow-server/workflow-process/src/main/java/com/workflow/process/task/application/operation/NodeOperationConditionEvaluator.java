package com.workflow.process.task.application.operation;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 节点操作矩阵使用的受限条件表达式引擎。
 *
 * <p>仅支持变量路径、字面量、比较和布尔运算，不支持方法、类型、函数、脚本或反射，
 * 从语法层面阻断任意代码执行。路径只能访问 Map，且根变量必须在发布配置中声明。</p>
 */
@Component
public class NodeOperationConditionEvaluator {

    /** 条件中始终可用的只读上下文根变量。 */
    public static final Set<String> BUILT_IN_ROOTS = Set.of(
            "process", "business", "task", "currentUser", "request");

    /**
     * 编译并校验表达式，发布前和运行时共用同一套语法规则。
     *
     * @param expression 条件表达式
     * @param allowedRoots 允许引用的根变量，不含内置变量也可
     * @return 可重复执行的安全条件
     */
    public CompiledCondition compile(String expression, Set<String> allowedRoots) {
        if (!StringUtils.hasText(expression)) {
            return CompiledCondition.alwaysTrue();
        }
        Parser parser = new Parser(new Lexer(expression).scan());
        Node root = parser.parse();
        if (!root.booleanCompatible()) {
            throw new IllegalArgumentException("节点操作条件必须返回布尔值");
        }
        Set<String> effectiveAllowed = new LinkedHashSet<>(BUILT_IN_ROOTS);
        if (allowedRoots != null) {
            effectiveAllowed.addAll(allowedRoots);
        }
        Set<String> undeclared = new LinkedHashSet<>(parser.referencedRoots());
        undeclared.removeAll(effectiveAllowed);
        if (!undeclared.isEmpty()) {
            throw new IllegalArgumentException("节点操作条件引用了未声明变量: " + String.join(", ", undeclared));
        }
        return new CompiledCondition(root, parser.referencedRoots());
    }

    /** 安全条件的编译结果。 */
    public static final class CompiledCondition {
        private final Node root;
        private final Set<String> referencedRoots;

        /**
         * 初始化{@code compiled}条件，保存构造参数供后续方法使用。
         *
         * @param root 根依赖，保存到当前对象供后续业务方法调用
         * @param referencedRoots 已引用{@code roots}，保存在对象中供后续校验、查询或展示
         */
        private CompiledCondition(Node root, Set<String> referencedRoots) {
            this.root = root;
            this.referencedRoots = Set.copyOf(referencedRoots);
        }

        /**
         * 处理{@code always}{@code true}，并将结果传给后续步骤。
         *
         * @return 处理后的{@code always}{@code true}结果，供调用方继续处理
         */
        private static CompiledCondition alwaysTrue() {
            return new CompiledCondition(new LiteralNode(Boolean.TRUE), Set.of());
        }

        /**
         * 使用只读变量快照执行条件；非布尔结果会被拒绝而不是隐式转换。
         *
         * @param variables 流程变量，后续传给流程引擎或规则求值器使用
         * @return {@code compiled}条件条件成立时为 true，否则为 false
         */
        public boolean evaluate(Map<String, Object> variables) {
            return requireBoolean(root.evaluate(variables == null ? Map.of() : variables));
        }

        /**
         * 整理已引用{@code roots}数据，供调用方遍历或继续处理。
         *
         * @return {@code compiled}条件集合，供调用方遍历或展示
         */
        public Set<String> referencedRoots() {
            return referencedRoots;
        }
    }

    /**
     * 定义节点的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
     */
    private interface Node {
        /**
         * 求值节点，并将结果传给后续步骤。
         *
         * @param variables 流程变量，后续传给流程引擎或规则求值器使用
         * @return 求值后的节点结果，供调用方继续处理
         */
        Object evaluate(Map<String, Object> variables);

        /**
         * 判断布尔值兼容条件是否成立，供调用方选择后续分支。
         *
         * @return 布尔值兼容条件成立时为 true，否则为 false
         */
        boolean booleanCompatible();
    }

    /**
     * 封装字面值节点的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param value 待处理字面值节点的原始输入，结果供调用方继续使用
     */
    private record LiteralNode(Object value) implements Node {
        /**
         * 求值字面值节点，并将结果传给后续步骤。
         *
         * @param variables 流程变量，后续传给流程引擎或规则求值器使用
         * @return 求值后的字面值节点结果，供调用方继续处理
         */
        @Override
        public Object evaluate(Map<String, Object> variables) {
            return value;
        }

        /**
         * 判断布尔值兼容条件是否成立，供调用方选择后续分支。
         *
         * @return 布尔值兼容条件成立时为 true，否则为 false
         */
        @Override
        public boolean booleanCompatible() {
            return value instanceof Boolean;
        }
    }

    /**
     * 封装路径节点的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param path 路径，保存在对象中供后续校验、查询或展示
     */
    private record PathNode(List<String> path) implements Node {
        /**
         * 求值路径节点，并将结果传给后续步骤。
         *
         * @param variables 流程变量，后续传给流程引擎或规则求值器使用
         * @return 求值后的路径节点结果，供调用方继续处理
         */
        @Override
        public Object evaluate(Map<String, Object> variables) {
            Object value = variables.get(path.get(0));
            for (int index = 1; index < path.size(); index++) {
                // 只允许 Map 路径，禁止通过 JavaBean 属性间接触发反射或方法调用。
                if (!(value instanceof Map<?, ?> map)) {
                    return null;
                }
                value = map.get(path.get(index));
            }
            return value;
        }

        /**
         * 判断布尔值兼容条件是否成立，供调用方选择后续分支。
         *
         * @return 布尔值兼容条件成立时为 true，否则为 false
         */
        @Override
        public boolean booleanCompatible() {
            // 路径的运行时类型由测试中心和实际求值共同确认。
            return true;
        }
    }

    /**
     * 封装非节点的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param operand {@code operand}，保存在对象中供后续校验、查询或展示
     */
    private record NotNode(Node operand) implements Node {
        /**
         * 求值非节点，并将结果传给后续步骤。
         *
         * @param variables 流程变量，后续传给流程引擎或规则求值器使用
         * @return 求值后的非节点结果，供调用方继续处理
         */
        @Override
        public Object evaluate(Map<String, Object> variables) {
            return !requireBoolean(operand.evaluate(variables));
        }

        /**
         * 判断布尔值兼容条件是否成立，供调用方选择后续分支。
         *
         * @return 布尔值兼容条件成立时为 true，否则为 false
         */
        @Override
        public boolean booleanCompatible() {
            return true;
        }
    }

    /**
     * 封装{@code logical}节点的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param operator 操作人，保存在对象中供后续校验、查询或展示
     * @param left 左侧，保存在对象中供后续校验、查询或展示
     * @param right 右侧，保存在对象中供后续校验、查询或展示
     */
    private record LogicalNode(TokenType operator, Node left, Node right) implements Node {
        /**
         * 求值{@code logical}节点，并将结果传给后续步骤。
         *
         * @param variables 流程变量，后续传给流程引擎或规则求值器使用
         * @return 求值后的{@code logical}节点结果，供调用方继续处理
         */
        @Override
        public Object evaluate(Map<String, Object> variables) {
            boolean leftValue = requireBoolean(left.evaluate(variables));
            if (operator == TokenType.AND && !leftValue) {
                return false;
            }
            if (operator == TokenType.OR && leftValue) {
                return true;
            }
            boolean rightValue = requireBoolean(right.evaluate(variables));
            return operator == TokenType.AND ? leftValue && rightValue : leftValue || rightValue;
        }

        /**
         * 判断布尔值兼容条件是否成立，供调用方选择后续分支。
         *
         * @return 布尔值兼容条件成立时为 true，否则为 false
         */
        @Override
        public boolean booleanCompatible() {
            return true;
        }
    }

    /**
     * 封装比较节点的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param operator 操作人，保存在对象中供后续校验、查询或展示
     * @param left 左侧，保存在对象中供后续校验、查询或展示
     * @param right 右侧，保存在对象中供后续校验、查询或展示
     */
    private record ComparisonNode(TokenType operator, Node left, Node right) implements Node {
        /**
         * 求值比较节点，并将结果传给后续步骤。
         *
         * @param variables 流程变量，后续传给流程引擎或规则求值器使用
         * @return 求值后的比较节点结果，供调用方继续处理
         * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
         */
        @Override
        public Object evaluate(Map<String, Object> variables) {
            Object leftValue = left.evaluate(variables);
            Object rightValue = right.evaluate(variables);
            return switch (operator) {
                case EQ -> equalValues(leftValue, rightValue);
                case NE -> !equalValues(leftValue, rightValue);
                case GT -> compareValues(leftValue, rightValue) > 0;
                case GTE -> compareValues(leftValue, rightValue) >= 0;
                case LT -> compareValues(leftValue, rightValue) < 0;
                case LTE -> compareValues(leftValue, rightValue) <= 0;
                default -> throw new IllegalStateException("不支持的比较运算符: " + operator);
            };
        }

        /**
         * 判断布尔值兼容条件是否成立，供调用方选择后续分支。
         *
         * @return 布尔值兼容条件成立时为 true，否则为 false
         */
        @Override
        public boolean booleanCompatible() {
            return true;
        }
    }

    /**
     * 校验并获取布尔值；不满足约束时阻止后续处理。
     *
     * @param value 待校验并获取布尔值的原始输入，结果供调用方继续使用
     * @return 布尔值条件成立时为 true，否则为 false
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static boolean requireBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("节点操作条件必须返回布尔值");
    }

    /**
     * 判断{@code equal}值集合条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，供本方法处理{@code equal}值集合时使用
     * @param right 右侧，供本方法处理{@code equal}值集合时使用
     * @return {@code equal}值集合条件成立时为 true，否则为 false
     */
    private static boolean equalValues(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return decimal(leftNumber).compareTo(decimal(rightNumber)) == 0;
        }
        return Objects.equals(left, right);
    }

    /**
     * 比较值集合；结果供调用方的后续步骤使用。
     *
     * @param left 左侧，供本方法比较值集合时使用
     * @param right 右侧，供本方法比较值集合时使用
     * @return 比较后的值集合结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static int compareValues(Object left, Object right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("空值不能参与大小比较");
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return decimal(leftNumber).compareTo(decimal(rightNumber));
        }
        if (left instanceof String leftText && right instanceof String rightText) {
            return leftText.compareTo(rightText);
        }
        throw new IllegalArgumentException("大小比较两侧必须同时为数字或字符串");
    }

    /**
     * 处理{@code decimal}，并将结果传给后续步骤。
     *
     * @param number 数值，作为 {@code BigDecimal} 的输入影响后续处理
     * @return 处理后的{@code decimal}结果，供调用方继续处理
     */
    private static BigDecimal decimal(Number number) {
        return new BigDecimal(number.toString());
    }

    /**
     * 定义令牌类型的可选值；调用方据此选择对应的处理分支。
     */
    private enum TokenType {
        IDENTIFIER, STRING, NUMBER, TRUE, FALSE, NULL,
        EQ, NE, GT, GTE, LT, LTE, AND, OR, NOT, DOT, LEFT_PAREN, RIGHT_PAREN, EOF
    }

    /**
     * 封装令牌的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param type 类型标识，决定后续令牌采用的处理分支
     * @param text 待处理令牌的原始输入，结果供调用方继续使用
     * @param literal 字面值，保存在对象中供后续校验、查询或展示
     * @param position 位置，保存在对象中供后续校验、查询或展示
     */
    private record Token(TokenType type, String text, Object literal, int position) {
    }

    /**
     * 负责词法解析器的业务处理；协调校验、状态变化及后续结果传递。
     */
    private static final class Lexer {
        private final String source;
        private final List<Token> tokens = new ArrayList<>();
        private int current;

        /**
         * 初始化词法解析器，保存构造参数供后续方法使用。
         *
         * @param source 来源依赖，保存到当前对象供后续业务方法调用
         */
        private Lexer(String source) {
            this.source = source;
        }

        /**
         * 整理{@code scan}数据，供调用方遍历或继续处理。
         *
         * @return 令牌集合，供调用方遍历或展示
         */
        private List<Token> scan() {
            while (!atEnd()) {
                int start = current;
                char value = advance();
                if (Character.isWhitespace(value)) {
                    continue;
                }
                switch (value) {
                    case '(' -> add(TokenType.LEFT_PAREN, start, null);
                    case ')' -> add(TokenType.RIGHT_PAREN, start, null);
                    case '.' -> add(TokenType.DOT, start, null);
                    case '!' -> add(match('=') ? TokenType.NE : TokenType.NOT, start, null);
                    case '=' -> {
                        require('=', start, "仅支持 ==，不支持赋值表达式");
                        add(TokenType.EQ, start, null);
                    }
                    case '>' -> add(match('=') ? TokenType.GTE : TokenType.GT, start, null);
                    case '<' -> add(match('=') ? TokenType.LTE : TokenType.LT, start, null);
                    case '&' -> {
                        require('&', start, "布尔与运算必须使用 &&");
                        add(TokenType.AND, start, null);
                    }
                    case '|' -> {
                        require('|', start, "布尔或运算必须使用 ||");
                        add(TokenType.OR, start, null);
                    }
                    case '\'', '"' -> string(value, start);
                    default -> {
                        if (Character.isDigit(value) || (value == '-' && peekIsDigit())) {
                            number(start);
                        } else if (Character.isJavaIdentifierStart(value)) {
                            identifier(start);
                        } else {
                            throw syntax(start, "包含不允许的字符 '" + value + "'");
                        }
                    }
                }
            }
            tokens.add(new Token(TokenType.EOF, "", null, current));
            return List.copyOf(tokens);
        }

        /**
         * 处理字符串，并将结果传给后续步骤。
         *
         * @param quote 引用，供本方法处理字符串时使用
         * @param start 启动，作为 {@code syntax} 的输入影响后续处理
         */
        private void string(char quote, int start) {
            StringBuilder value = new StringBuilder();
            while (!atEnd() && peek() != quote) {
                char currentValue = advance();
                if (currentValue == '\\') {
                    if (atEnd()) {
                        throw syntax(start, "字符串转义不完整");
                    }
                    char escaped = advance();
                    value.append(switch (escaped) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case '\\' -> '\\';
                        case '\'' -> '\'';
                        case '"' -> '"';
                        default -> throw syntax(current - 1, "不支持的字符串转义");
                    });
                } else {
                    value.append(currentValue);
                }
            }
            if (atEnd()) {
                throw syntax(start, "字符串缺少结束引号");
            }
            advance();
            tokens.add(new Token(TokenType.STRING, source.substring(start, current), value.toString(), start));
        }

        /**
         * 处理数值，并将结果传给后续步骤。
         *
         * @param start 启动，作为 {@code source.substring} 的输入影响后续处理
         */
        private void number(int start) {
            while (Character.isDigit(peek())) {
                advance();
            }
            if (peek() == '.' && Character.isDigit(peekNext())) {
                advance();
                while (Character.isDigit(peek())) {
                    advance();
                }
            }
            String text = source.substring(start, current);
            try {
                tokens.add(new Token(TokenType.NUMBER, text, new BigDecimal(text), start));
            } catch (NumberFormatException exception) {
                throw syntax(start, "数字格式无效");
            }
        }

        /**
         * 处理标识符，并将结果传给后续步骤。
         *
         * @param start 启动，作为 {@code source.substring} 的输入影响后续处理
         */
        private void identifier(int start) {
            // NUL 在 Java 标识符规则中可能被视为可忽略字符，必须先判断输入边界。
            while (!atEnd() && Character.isJavaIdentifierPart(peek())) {
                advance();
            }
            String text = source.substring(start, current);
            TokenType type = switch (text) {
                case "true" -> TokenType.TRUE;
                case "false" -> TokenType.FALSE;
                case "null" -> TokenType.NULL;
                default -> TokenType.IDENTIFIER;
            };
            Object literal = switch (type) {
                case TRUE -> Boolean.TRUE;
                case FALSE -> Boolean.FALSE;
                default -> null;
            };
            tokens.add(new Token(type, text, literal, start));
        }

        /**
         * 校验并获取词法解析器；不满足约束时阻止后续处理。
         *
         * @param expected 预期，供本方法校验并获取词法解析器时使用
         * @param start 启动，作为 {@code syntax} 的输入影响后续处理
         * @param message 消息，作为 {@code syntax} 的输入影响后续处理
         */
        private void require(char expected, int start, String message) {
            if (!match(expected)) {
                throw syntax(start, message);
            }
        }

        /**
         * 添加词法解析器；结果供后续流程传递或持久化。
         *
         * @param type 类型标识，决定后续词法解析器采用的处理分支
         * @param start 启动，供本方法添加词法解析器时使用
         * @param literal 字面值，供本方法添加词法解析器时使用
         */
        private void add(TokenType type, int start, Object literal) {
            tokens.add(new Token(type, source.substring(start, current), literal, start));
        }

        /**
         * 匹配词法解析器；判断结果决定调用方的后续分支。
         *
         * @param expected 预期，供本方法匹配词法解析器时使用
         * @return 词法解析器条件成立时为 true，否则为 false
         */
        private boolean match(char expected) {
            if (atEnd() || source.charAt(current) != expected) {
                return false;
            }
            current++;
            return true;
        }

        /**
         * 处理{@code advance}，并将结果传给后续步骤。
         *
         * @return 处理后的{@code advance}结果，供调用方继续处理
         */
        private char advance() {
            return source.charAt(current++);
        }

        /**
         * 处理{@code peek}，并将结果传给后续步骤。
         *
         * @return 处理后的{@code peek}结果，供调用方继续处理
         */
        private char peek() {
            return atEnd() ? '\0' : source.charAt(current);
        }

        /**
         * 处理{@code peek}下一步，并将结果传给后续步骤。
         *
         * @return 处理后的{@code peek}下一步结果，供调用方继续处理
         */
        private char peekNext() {
            return current + 1 >= source.length() ? '\0' : source.charAt(current + 1);
        }

        /**
         * 判断{@code peek}是否{@code digit}条件是否成立，供调用方选择后续分支。
         *
         * @return {@code peek}是否{@code digit}条件成立时为 true，否则为 false
         */
        private boolean peekIsDigit() {
            return Character.isDigit(peek());
        }

        /**
         * 判断时间结束条件是否成立，供调用方选择后续分支。
         *
         * @return 时间结束条件成立时为 true，否则为 false
         */
        private boolean atEnd() {
            return current >= source.length();
        }

        /**
         * 构造{@code syntax}异常，供调用方区分失败原因。
         *
         * @param position 位置，作为 {@code IllegalArgumentException} 的输入影响后续处理
         * @param message 消息，作为 {@code IllegalArgumentException} 的输入影响后续处理
         * @return 处理后的{@code syntax}结果，供调用方继续处理
         */
        private IllegalArgumentException syntax(int position, String message) {
            return new IllegalArgumentException("节点操作条件语法错误（位置 " + position + "）: " + message);
        }
    }

    /**
     * 负责解析器的业务处理；协调校验、状态变化及后续结果传递。
     */
    private static final class Parser {
        private final List<Token> tokens;
        private final Set<String> referencedRoots = new LinkedHashSet<>();
        private int current;

        /**
         * 初始化解析器，保存构造参数供后续方法使用。
         *
         * @param tokens {@code tokens}依赖，保存到当前对象供后续业务方法调用
         */
        private Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * 解析解析器；输出作为后续校验或处理的输入。
         *
         * @return 解析后的解析器结果，供调用方继续处理
         */
        private Node parse() {
            Node result = or();
            consume(TokenType.EOF, "表达式末尾存在多余内容");
            return result;
        }

        /**
         * 处理或，并将结果传给后续步骤。
         *
         * @return 处理后的或结果，供调用方继续处理
         */
        private Node or() {
            Node expression = and();
            while (match(TokenType.OR)) {
                expression = new LogicalNode(TokenType.OR, expression, and());
            }
            return expression;
        }

        /**
         * 处理与，并将结果传给后续步骤。
         *
         * @return 处理后的与结果，供调用方继续处理
         */
        private Node and() {
            Node expression = comparison();
            while (match(TokenType.AND)) {
                expression = new LogicalNode(TokenType.AND, expression, comparison());
            }
            return expression;
        }

        /**
         * 处理比较，并将结果传给后续步骤。
         *
         * @return 处理后的比较结果，供调用方继续处理
         */
        private Node comparison() {
            Node expression = unary();
            if (match(TokenType.EQ, TokenType.NE, TokenType.GT, TokenType.GTE,
                    TokenType.LT, TokenType.LTE)) {
                TokenType operator = previous().type();
                expression = new ComparisonNode(operator, expression, unary());
            }
            return expression;
        }

        /**
         * 处理{@code unary}，并将结果传给后续步骤。
         *
         * @return 处理后的{@code unary}结果，供调用方继续处理
         */
        private Node unary() {
            if (match(TokenType.NOT)) {
                return new NotNode(unary());
            }
            return primary();
        }

        /**
         * 处理主要，并将结果传给后续步骤。
         *
         * @return 处理后的主要结果，供调用方继续处理
         */
        private Node primary() {
            if (match(TokenType.TRUE, TokenType.FALSE)) {
                return new LiteralNode(previous().literal());
            }
            if (match(TokenType.NULL)) {
                return new LiteralNode(null);
            }
            if (match(TokenType.STRING, TokenType.NUMBER)) {
                return new LiteralNode(previous().literal());
            }
            if (match(TokenType.IDENTIFIER)) {
                List<String> path = new ArrayList<>();
                path.add(previous().text());
                referencedRoots.add(previous().text());
                while (match(TokenType.DOT)) {
                    path.add(consume(TokenType.IDENTIFIER, "变量路径的点号后必须是属性名").text());
                }
                return new PathNode(List.copyOf(path));
            }
            if (match(TokenType.LEFT_PAREN)) {
                Node expression = or();
                consume(TokenType.RIGHT_PAREN, "条件分组缺少右括号");
                return expression;
            }
            throw error(peek(), "缺少变量、字面量或条件分组");
        }

        /**
         * 匹配解析器；判断结果决定调用方的后续分支。
         *
         * @param types 类型集合，供本方法匹配解析器时使用
         * @return 解析器条件成立时为 true，否则为 false
         */
        private boolean match(TokenType... types) {
            for (TokenType type : types) {
                if (check(type)) {
                    current++;
                    return true;
                }
            }
            return false;
        }

        /**
         * 处理消费，并将结果传给后续步骤。
         *
         * @param type 类型标识，决定后续消费采用的处理分支
         * @param message 消息，供本方法处理消费时使用
         * @return 处理后的消费结果，供调用方继续处理
         */
        private Token consume(TokenType type, String message) {
            if (check(type)) {
                return tokens.get(current++);
            }
            throw error(peek(), message);
        }

        /**
         * 检查解析器；不满足约束时阻止后续处理。
         *
         * @param type 类型标识，决定后续解析器采用的处理分支
         * @return 解析器条件成立时为 true，否则为 false
         */
        private boolean check(TokenType type) {
            return peek().type() == type;
        }

        /**
         * 处理{@code peek}，并将结果传给后续步骤。
         *
         * @return 处理后的{@code peek}结果，供调用方继续处理
         */
        private Token peek() {
            return tokens.get(current);
        }

        /**
         * 处理上一项，并将结果传给后续步骤。
         *
         * @return 处理后的上一项结果，供调用方继续处理
         */
        private Token previous() {
            return tokens.get(current - 1);
        }

        /**
         * 整理已引用{@code roots}数据，供调用方遍历或继续处理。
         *
         * @return 解析器集合，供调用方遍历或展示
         */
        private Set<String> referencedRoots() {
            return Set.copyOf(referencedRoots);
        }

        /**
         * 构造错误异常，供调用方区分失败原因。
         *
         * @param token 令牌，后续用于授权校验、关联或幂等去重
         * @param message 消息，供本方法处理错误时使用
         * @return 处理后的错误结果，供调用方继续处理
         */
        private IllegalArgumentException error(Token token, String message) {
            return new IllegalArgumentException(
                    "节点操作条件语法错误（位置 " + token.position() + "）: " + message);
        }
    }
}
