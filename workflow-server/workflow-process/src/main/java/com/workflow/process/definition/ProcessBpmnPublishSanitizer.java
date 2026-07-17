package com.workflow.process.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 发布前 BPMN 归一化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessBpmnPublishSanitizer {

    private final ObjectMapper objectMapper;

    public String sanitize(String bpmnXml, String processKey) {
        String result = bpmnXml;

        result = removeDuplicateCamundaAssignments(result);
        result = convertCamundaAssignments(result);
        result = result.replaceAll("(?i)\\s+xmlns:camunda=\"[^\"]*\"", "");
        result = convertCamundaProperties(result);
        result = removeCamundaElements(result);
        result = result.replaceAll("(?i)\\s+camunda:[^=\\s]*=\"[^\"]*\"", "");
        result = result.replaceAll("(?i)\\s+resultVariable=\"[^\"]*\"", "");
        result = result.replaceAll("\\s+extensionProperties=\"[^\"]*\"", "");
        result = convertBareFlowableAttributes(result);
        result = convertMultiInstanceAttributes(result);
        result = processSkipNodeTasks(result);
        result = migrateApprovedExpressions(result);
        result = ensureFlowableNamespace(result);
        result = resolveBpmnIdConflicts(result, processKey);
        result = useProcessKey(result, processKey);
        result = removeInvalidMultiInstanceConfig(result);
        result = fixMultiInstanceAssignee(result);
        result = fixScriptTasks(result);

        return result;
    }

    private String removeDuplicateCamundaAssignments(String bpmnXml) {
        String result = bpmnXml;
        result = result.replaceAll(
                "(<userTask[^>]*?flowable:assignee=\"[^\"]*\"[^>]*?)\\s+camunda:assignee=\"[^\"]*\"",
                "$1");
        result = result.replaceAll(
                "(<userTask[^>]*?)\\s+camunda:assignee=\"[^\"]*\"([^>]*?flowable:assignee=\"[^\"]*\"[^>]*)",
                "$1$2");
        result = result.replaceAll(
                "(<userTask[^>]*?flowable:candidateGroups=\"[^\"]*\"[^>]*?)\\s+camunda:candidateGroups=\"[^\"]*\"",
                "$1");
        result = result.replaceAll(
                "(<userTask[^>]*?)\\s+camunda:candidateGroups=\"[^\"]*\"([^>]*?flowable:candidateGroups=\"[^\"]*\"[^>]*)",
                "$1$2");
        result = result.replaceAll(
                "(<userTask[^>]*?flowable:candidateUsers=\"[^\"]*\"[^>]*?)\\s+camunda:candidateUsers=\"[^\"]*\"",
                "$1");
        result = result.replaceAll(
                "(<userTask[^>]*?)\\s+camunda:candidateUsers=\"[^\"]*\"([^>]*?flowable:candidateUsers=\"[^\"]*\"[^>]*)",
                "$1$2");
        return result;
    }

    private String convertCamundaAssignments(String bpmnXml) {
        String result = bpmnXml;
        result = result.replaceAll("camunda:candidateGroups=\"([^\"]*)\"", "flowable:candidateGroups=\"$1\"");
        result = result.replaceAll("camunda:candidateUsers=\"([^\"]*)\"", "flowable:candidateUsers=\"$1\"");
        result = result.replaceAll("camunda:assignee=\"([^\"]*)\"", "flowable:assignee=\"$1\"");
        return result;
    }

    private String convertCamundaProperties(String bpmnXml) {
        String result = bpmnXml;
        result = result.replaceAll("(?i)<camunda:Properties", "<flowable:Properties");
        result = result.replaceAll("(?i)</camunda:Properties>", "</flowable:Properties>");
        result = result.replaceAll("(?i)<camunda:Property", "<flowable:Property");
        result = result.replaceAll("(?i)</camunda:Property>", "</flowable:Property>");
        return result;
    }

    private String removeCamundaElements(String bpmnXml) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)<camunda:(?!properties|property)[^>]*>[\\s\\S]*?</camunda:[^>]*>",
                java.util.regex.Pattern.DOTALL);
        String result = bpmnXml;
        for (int i = 0; i < 10; i++) {
            java.util.regex.Matcher matcher = pattern.matcher(result);
            if (!matcher.find()) {
                break;
            }
            result = matcher.replaceAll("");
        }
        return result;
    }

    private String convertBareFlowableAttributes(String bpmnXml) {
        String result = bpmnXml;
        result = result.replaceAll("(?<!flowable:)candidateGroups=\"([^\"]*)\"", "flowable:candidateGroups=\"$1\"");
        result = result.replaceAll("(?<!flowable:)candidateUsers=\"([^\"]*)\"", "flowable:candidateUsers=\"$1\"");
        result = result.replaceAll("(?<!flowable:)\\sassignee=\"([^\"]*)\"", " flowable:assignee=\"$1\"");
        return result;
    }

    private String convertMultiInstanceAttributes(String bpmnXml) {
        String result = bpmnXml;
        result = result.replaceAll(
                "(?i)(<multiInstanceLoopCharacteristics[^>]*?)(?<!flowable:)collection=\"([^\"]*)\"",
                "$1flowable:collection=\"$2\"");
        result = result.replaceAll(
                "(?i)(<multiInstanceLoopCharacteristics[^>]*?)(?<!flowable:)elementVariable=\"([^\"]*)\"",
                "$1flowable:elementVariable=\"$2\"");
        return result;
    }

    private String ensureFlowableNamespace(String bpmnXml) {
        if (bpmnXml.contains("xmlns:flowable")) {
            return bpmnXml;
        }
        return bpmnXml.replace(
                "xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
                "xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\"");
    }

    private String useProcessKey(String bpmnXml, String processKey) {
        String result = bpmnXml.replaceAll(
                "<((?:[A-Za-z_][\\w.-]*:)?process)\\s+id=\"[^\"]+\"",
                "<$1 id=\"" + processKey + "\"");
        return result.replaceAll(
                "(<bpmndi:BPMNPlane[^>]*\\s)bpmnElement=\"[^\"]+\"",
                "$1bpmnElement=\"" + processKey + "\"");
    }

    private String removeInvalidMultiInstanceConfig(String bpmnXml) {
        String result = bpmnXml;
        result = result.replaceAll(
                "(?i)<bpmn:multiInstanceLoopCharacteristics\\s+isSequential=\"(?:true|false)\"\\s*/>",
                "");
        result = result.replaceAll("(?i)<bpmn:multiInstanceLoopCharacteristics\\s*/>", "");

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)<bpmn:multiInstanceLoopCharacteristics[^>]*?>[\\s\\S]*?</bpmn:multiInstanceLoopCharacteristics>",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String tag = matcher.group();
            boolean valid = tag.toLowerCase().contains("collection=")
                    || tag.toLowerCase().contains("flowable:collection=")
                    || tag.toLowerCase().contains("<bpmn:loopcardinality")
                    || tag.toLowerCase().contains("<bpmn:loopdatainputref");
            if (!valid) {
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String fixMultiInstanceAssignee(String bpmnXml) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)<(bpmn:)?userTask\\b([^>]*)>([\\s\\S]*?)</\\1userTask>",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(bpmnXml);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String fullTag = matcher.group(0);
            String startTag = fullTag.substring(0, fullTag.indexOf('>') + 1);
            String content = matcher.group(3);
            if (!content.toLowerCase().contains("multiinstanceloopcharacteristics")) {
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(fullTag));
                continue;
            }

            java.util.regex.Matcher evMatcher = java.util.regex.Pattern
                    .compile("(?i)(?:flowable:)?elementVariable=\"([^\"]*)\"")
                    .matcher(content);
            String elementVar = evMatcher.find() ? evMatcher.group(1) : "assignee";
            String newStartTag = startTag;
            if (!newStartTag.toLowerCase().contains("flowable:assignee=")) {
                newStartTag = newStartTag.replace(">", " flowable:assignee=\"${" + elementVar + "}\">");
            }

            String prefix = matcher.group(1) != null ? matcher.group(1) : "";
            String newFullTag = newStartTag + content + "</" + prefix + "userTask>";
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(newFullTag));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String fixScriptTasks(String bpmnXml) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)<(bpmn:)?scriptTask\\b([^>]*)>([\\s\\S]*?)</\\1scriptTask>",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(bpmnXml);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String fullTag = matcher.group(0);
            String startTag = fullTag.substring(0, fullTag.indexOf('>') + 1);
            String content = matcher.group(3);
            String prefix = matcher.group(1) != null ? matcher.group(1) : "";
            String endTag = "</" + prefix + "scriptTask>";

            java.util.regex.Matcher configMatcher = java.util.regex.Pattern
                    .compile("(?i)<flowable:property\\s+name=\\\"scriptConfig\\\"\\s+value=\\\"([^\\\"]*)\\\"")
                    .matcher(content);
            if (!configMatcher.find()) {
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(fullTag));
                continue;
            }

            String configJson = decodeXml(configMatcher.group(1));
            try {
                com.fasterxml.jackson.databind.JsonNode config = objectMapper.readTree(configJson);
                String script = config.has("script") ? config.get("script").asText() : "";
                script = script.replaceAll("(?i)<script[^>]*>", "").replaceAll("(?i)</script>", "").trim();
                if (script.isEmpty()) {
                    matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(fullTag));
                    continue;
                }

                String newStartTag = applyScriptAttributes(startTag, config);
                String newContent = content.replaceAll("(?i)<" + prefix + "script>[^<]*</" + prefix + "script>", "")
                        .replaceAll("(?i)<script>[^<]*</script>", "");
                String scriptElement = "<" + prefix + "script>" + script + "</" + prefix + "script>";
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(newStartTag + newContent + scriptElement + endTag));
            } catch (Exception e) {
                log.warn("解析 scriptTask 配置失败: {}", e.getMessage());
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(fullTag));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String applyScriptAttributes(String startTag, com.fasterxml.jackson.databind.JsonNode config) {
        String result = startTag;
        String scriptFormat = config.has("scriptFormat") ? config.get("scriptFormat").asText() : "javascript";
        if (!result.toLowerCase().contains("scriptformat=")) {
            result = result.replace(">", " scriptFormat=\"" + scriptFormat + "\">");
        } else {
            result = result.replaceAll("(?i)scriptFormat=\\\"[^\\\"]*\\\"", "scriptFormat=\"" + scriptFormat + "\"");
        }

        String resultVariable = config.has("resultVariable") ? config.get("resultVariable").asText() : "";
        if (!resultVariable.isEmpty()) {
            if (!result.toLowerCase().contains("flowable:resultvariable=")) {
                result = result.replace(">", " flowable:resultVariable=\"" + resultVariable + "\">");
            } else {
                result = result.replaceAll("(?i)flowable:resultVariable=\\\"[^\\\"]*\\\"", "flowable:resultVariable=\"" + resultVariable + "\"");
            }
        }

        boolean autoStore = config.has("autoStoreVariables") && config.get("autoStoreVariables").asBoolean();
        if (autoStore) {
            if (!result.toLowerCase().contains("flowable:autostorevariables=")) {
                result = result.replace(">", " flowable:autoStoreVariables=\"true\">");
            } else {
                result = result.replaceAll("(?i)flowable:autoStoreVariables=\\\"[^\\\"]*\\\"", "flowable:autoStoreVariables=\"true\"");
            }
        }
        return result;
    }

    private String processSkipNodeTasks(String bpmnXml) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<bpmn:userTask([^>]*)>(.*?)</bpmn:userTask>",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(bpmnXml);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String attrs = matcher.group(1);
            String content = matcher.group(2);
            if (content.contains("name=\"skipNode\" value=\"true\"") && !attrs.contains("flowable:skipExpression")) {
                attrs += " flowable:skipExpression=\"${skipNodeEnabled}\"";
            }
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(
                    "<bpmn:userTask" + attrs + ">" + content + "</bpmn:userTask>"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 迁移旧的 approved 布尔条件表达式为字符串比较。
     *
     * <p>历史流程的网关条件写的是 {@code ${approved == true}} / {@code ${approved == false}}，
     * 但 approved 变量已统一为字符串 "approve"/"reject"，布尔比较会导致条件永远不成立。
     * 发布时把 {@code approved == true} 改为 {@code approved == 'approve'}，
     * {@code approved == false} 改为 {@code approved == 'reject'}（兼容 == 与 !=）。</p>
     */
    private String migrateApprovedExpressions(String bpmnXml) {
        String result = bpmnXml;
        // approved == true  →  approved == 'approve'
        result = result.replaceAll("approved\\s*==\\s*true\\b", "approved == 'approve'");
        // true == approved  →  'approve' == approved
        result = result.replaceAll("\\btrue\\s*==\\s*approved", "'approve' == approved");
        // approved == false  →  approved == 'reject'
        result = result.replaceAll("approved\\s*==\\s*false\\b", "approved == 'reject'");
        // false == approved  →  'reject' == approved
        result = result.replaceAll("\\bfalse\\s*==\\s*approved", "'reject' == approved");
        // approved != true  →  approved != 'approve'
        result = result.replaceAll("approved\\s*!=\\s*true\\b", "approved != 'approve'");
        // approved != false  →  approved != 'reject'
        result = result.replaceAll("approved\\s*!=\\s*false\\b", "approved != 'reject'");
        return result;
    }

    private String resolveBpmnIdConflicts(String bpmnXml, String processKey) {
        java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("id=\"([^\"]+)\"");
        java.util.regex.Matcher idMatcher = idPattern.matcher(bpmnXml);
        java.util.Set<String> allIds = new java.util.HashSet<>();
        while (idMatcher.find()) {
            allIds.add(idMatcher.group(1));
        }

        java.util.regex.Matcher processIdMatcher = java.util.regex.Pattern
                .compile("<(?:(?:[A-Za-z_][\\w.-]*):)?process\\s+id=\"([^\"]+)\"")
                .matcher(bpmnXml);
        String currentProcessId = processIdMatcher.find() ? processIdMatcher.group(1) : null;
        boolean hasConflict = allIds.stream().anyMatch(id -> id.equals(processKey) && !id.equals(currentProcessId));
        if (!hasConflict) {
            return bpmnXml;
        }

        String newId = processKey + "_" + System.currentTimeMillis();
        while (allIds.contains(newId)) {
            newId = processKey + "_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
        }

        String result = bpmnXml;
        result = result.replaceAll("(id=\")" + java.util.regex.Pattern.quote(processKey) + "(\")", "$1" + newId + "$2");
        result = result.replaceAll("(sourceRef=\")" + java.util.regex.Pattern.quote(processKey) + "(\")", "$1" + newId + "$2");
        result = result.replaceAll("(targetRef=\")" + java.util.regex.Pattern.quote(processKey) + "(\")", "$1" + newId + "$2");
        result = result.replaceAll("(bpmnElement=\")" + java.util.regex.Pattern.quote(processKey) + "(\")", "$1" + newId + "$2");
        result = result.replaceAll("(default=\")" + java.util.regex.Pattern.quote(processKey) + "(\")", "$1" + newId + "$2");
        return result;
    }

    private String decodeXml(String value) {
        return value.replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&lt;", "<")
                .replace("&#60;", "<")
                .replace("&gt;", ">")
                .replace("&#62;", ">")
                .replace("&amp;", "&")
                .replace("&#38;", "&");
    }
}
