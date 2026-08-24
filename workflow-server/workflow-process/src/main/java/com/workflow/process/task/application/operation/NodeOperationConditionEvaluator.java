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

        private CompiledCondition(Node root, Set<String> referencedRoots) {
            this.root = root;
            this.referencedRoots = Set.copyOf(referencedRoots);
        }

        private static CompiledCondition alwaysTrue() {
            return new CompiledCondition(new LiteralNode(Boolean.TRUE), Set.of());
        }

        /**
         * 使用只读变量快照执行条件；非布尔结果会被拒绝而不是隐式转换。
         */
        public boolean evaluate(Map<String, Object> variables) {
            return requireBoolean(root.evaluate(variables == null ? Map.of() : variables));
        }

        public Set<String> referencedRoots() {
            return referencedRoots;
        }
    }

    private interface Node {
        Object evaluate(Map<String, Object> variables);

        boolean booleanCompatible();
    }

    private record LiteralNode(Object value) implements Node {
        @Override
        public Object evaluate(Map<String, Object> variables) {
            return value;
        }

        @Override
        public boolean booleanCompatible() {
            return value instanceof Boolean;
        }
    }

    private record PathNode(List<String> path) implements Node {
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

        @Override
        public boolean booleanCompatible() {
            // 路径的运行时类型由测试中心和实际求值共同确认。
            return true;
        }
    }

    private record NotNode(Node operand) implements Node {
        @Override
        public Object evaluate(Map<String, Object> variables) {
            return !requireBoolean(operand.evaluate(variables));
        }

        @Override
        public boolean booleanCompatible() {
            return true;
        }
    }

    private record LogicalNode(TokenType operator, Node left, Node right) implements Node {
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

        @Override
        public boolean booleanCompatible() {
            return true;
        }
    }

    private record ComparisonNode(TokenType operator, Node left, Node right) implements Node {
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

        @Override
        public boolean booleanCompatible() {
            return true;
        }
    }

    private static boolean requireBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("节点操作条件必须返回布尔值");
    }

    private static boolean equalValues(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return decimal(leftNumber).compareTo(decimal(rightNumber)) == 0;
        }
        return Objects.equals(left, right);
    }

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

    private static BigDecimal decimal(Number number) {
        return new BigDecimal(number.toString());
    }

    private enum TokenType {
        IDENTIFIER, STRING, NUMBER, TRUE, FALSE, NULL,
        EQ, NE, GT, GTE, LT, LTE, AND, OR, NOT, DOT, LEFT_PAREN, RIGHT_PAREN, EOF
    }

    private record Token(TokenType type, String text, Object literal, int position) {
    }

    private static final class Lexer {
        private final String source;
        private final List<Token> tokens = new ArrayList<>();
        private int current;

        private Lexer(String source) {
            this.source = source;
        }

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

        private void require(char expected, int start, String message) {
            if (!match(expected)) {
                throw syntax(start, message);
            }
        }

        private void add(TokenType type, int start, Object literal) {
            tokens.add(new Token(type, source.substring(start, current), literal, start));
        }

        private boolean match(char expected) {
            if (atEnd() || source.charAt(current) != expected) {
                return false;
            }
            current++;
            return true;
        }

        private char advance() {
            return source.charAt(current++);
        }

        private char peek() {
            return atEnd() ? '\0' : source.charAt(current);
        }

        private char peekNext() {
            return current + 1 >= source.length() ? '\0' : source.charAt(current + 1);
        }

        private boolean peekIsDigit() {
            return Character.isDigit(peek());
        }

        private boolean atEnd() {
            return current >= source.length();
        }

        private IllegalArgumentException syntax(int position, String message) {
            return new IllegalArgumentException("节点操作条件语法错误（位置 " + position + "）: " + message);
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final Set<String> referencedRoots = new LinkedHashSet<>();
        private int current;

        private Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        private Node parse() {
            Node result = or();
            consume(TokenType.EOF, "表达式末尾存在多余内容");
            return result;
        }

        private Node or() {
            Node expression = and();
            while (match(TokenType.OR)) {
                expression = new LogicalNode(TokenType.OR, expression, and());
            }
            return expression;
        }

        private Node and() {
            Node expression = comparison();
            while (match(TokenType.AND)) {
                expression = new LogicalNode(TokenType.AND, expression, comparison());
            }
            return expression;
        }

        private Node comparison() {
            Node expression = unary();
            if (match(TokenType.EQ, TokenType.NE, TokenType.GT, TokenType.GTE,
                    TokenType.LT, TokenType.LTE)) {
                TokenType operator = previous().type();
                expression = new ComparisonNode(operator, expression, unary());
            }
            return expression;
        }

        private Node unary() {
            if (match(TokenType.NOT)) {
                return new NotNode(unary());
            }
            return primary();
        }

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

        private boolean match(TokenType... types) {
            for (TokenType type : types) {
                if (check(type)) {
                    current++;
                    return true;
                }
            }
            return false;
        }

        private Token consume(TokenType type, String message) {
            if (check(type)) {
                return tokens.get(current++);
            }
            throw error(peek(), message);
        }

        private boolean check(TokenType type) {
            return peek().type() == type;
        }

        private Token peek() {
            return tokens.get(current);
        }

        private Token previous() {
            return tokens.get(current - 1);
        }

        private Set<String> referencedRoots() {
            return Set.copyOf(referencedRoots);
        }

        private IllegalArgumentException error(Token token, String message) {
            return new IllegalArgumentException(
                    "节点操作条件语法错误（位置 " + token.position() + "）: " + message);
        }
    }
}
