    /**
     * 从 BPMN XML 解析节点配置并保存到 node_config 表
     * 支持所有节点类型：开始事件、结束事件、用户任务、服务任务、网关等
     */
    private void parseAndSaveNodeConfigs(String processConfigId, String bpmnXml) {
        try {
            // 先删除该流程的旧节点配置
            nodeMapper.deleteByProcessConfigId(processConfigId);
            
            int savedCount = 0;
            
            // 解析开始事件
            savedCount += parseNodesByType(processConfigId, bpmnXml, "startEvent", NodeConfig.NodeType.START);
            
            // 解析结束事件
            savedCount += parseNodesByType(processConfigId, bpmnXml, "endEvent", NodeConfig.NodeType.END);
            
            // 解析用户任务（特殊处理，包含执行人和表单）
            savedCount += parseUserTasks(processConfigId, bpmnXml);
            
            // 解析服务任务
            savedCount += parseNodesByType(processConfigId, bpmnXml, "serviceTask", NodeConfig.NodeType.SERVICE_TASK);
            
            // 解析脚本任务
            savedCount += parseNodesByType(processConfigId, bpmnXml, "scriptTask", NodeConfig.NodeType.SCRIPT_TASK);
            
            // 解析发送任务
            savedCount += parseNodesByType(processConfigId, bpmnXml, "sendTask", NodeConfig.NodeType.SEND_TASK);
            
            // 解析接收任务
            savedCount += parseNodesByType(processConfigId, bpmnXml, "receiveTask", NodeConfig.NodeType.RECEIVE_TASK);
            
            // 解析手动任务
            savedCount += parseNodesByType(processConfigId, bpmnXml, "manualTask", NodeConfig.NodeType.MANUAL_TASK);
            
            // 解析业务规则任务
            savedCount += parseNodesByType(processConfigId, bpmnXml, "businessRuleTask", NodeConfig.NodeType.BUSINESS_RULE_TASK);
            
            // 解析排他网关
            savedCount += parseNodesByType(processConfigId, bpmnXml, "exclusiveGateway", NodeConfig.NodeType.EXCLUSIVE_GATEWAY);
            
            // 解析并行网关
            savedCount += parseNodesByType(processConfigId, bpmnXml, "parallelGateway", NodeConfig.NodeType.PARALLEL_GATEWAY);
            
            // 解析包容网关
            savedCount += parseNodesByType(processConfigId, bpmnXml, "inclusiveGateway", NodeConfig.NodeType.INCLUSIVE_GATEWAY);
            
            // 解析事件网关
            savedCount += parseNodesByType(processConfigId, bpmnXml, "eventBasedGateway", NodeConfig.NodeType.EVENT_BASED_GATEWAY);
            
            // 解析调用活动（子流程）
            savedCount += parseNodesByType(processConfigId, bpmnXml, "callActivity", NodeConfig.NodeType.CALL_ACTIVITY);
            
            // 解析子流程
            savedCount += parseNodesByType(processConfigId, bpmnXml, "subProcess", NodeConfig.NodeType.SUB_PROCESS);
            
            log.info("从 BPMN XML 解析并保存了 {} 个节点配置: processConfigId={}", savedCount, processConfigId);
        } catch (Exception e) {
            log.error("解析 BPMN XML 保存节点配置失败: processConfigId={}", processConfigId, e);
        }
    }
    
    /**
     * 解析指定类型的节点
     */
    private int parseNodesByType(String processConfigId, String bpmnXml, String tagName, NodeConfig.NodeType nodeType) {
        int count = 0;
        
        // 解析自闭合标签 <tagName ... />
        Pattern selfClosingPattern = Pattern.compile(
            "<(bpmn:)?" + tagName + "([^>]*)/>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher selfClosingMatcher = selfClosingPattern.matcher(bpmnXml);
        while (selfClosingMatcher.find()) {
            String attrs = selfClosingMatcher.group(2);
            if (saveNode(processConfigId, attrs, "", nodeType)) {
                count++;
            }
        }
        
        // 解析有内容的标签 <tagName ...>...</tagName>
        Pattern pattern = Pattern.compile(
            "<(bpmn:)?" + tagName + "([^>]*)>(.*?)</(bpmn:)?" + tagName + ">",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(bpmnXml);
        while (matcher.find()) {
            String attrs = matcher.group(2);
            String content = matcher.group(3);
            if (saveNode(processConfigId, attrs, content, nodeType)) {
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * 专门解析用户任务（包含执行人和表单）
     */
    private int parseUserTasks(String processConfigId, String bpmnXml) {
        int count = 0;
        String tagName = "userTask";
        NodeConfig.NodeType nodeType = NodeConfig.NodeType.USER_TASK;
        
        // 解析自闭合标签
        Pattern selfClosingPattern = Pattern.compile(
            "<(bpmn:)?" + tagName + "([^>]*)/>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher selfClosingMatcher = selfClosingPattern.matcher(bpmnXml);
        while (selfClosingMatcher.find()) {
            String attrs = selfClosingMatcher.group(2);
            String nodeConfigId = saveNodeAndGetId(processConfigId, attrs, "", nodeType);
            if (nodeConfigId != null) {
                parseAndSaveAssigneeConfig(nodeConfigId, attrs, "");
                parseAndSaveFormConfig(nodeConfigId, "", processConfigId);
                count++;
            }
        }
        
        // 解析有内容的标签
        Pattern pattern = Pattern.compile(
            "<(bpmn:)?" + tagName + "([^>]*)>(.*?)</(bpmn:)?" + tagName + ">",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(bpmnXml);
        while (matcher.find()) {
            String attrs = matcher.group(2);
            String content = matcher.group(3);
            String nodeConfigId = saveNodeAndGetId(processConfigId, attrs, content, nodeType);
            if (nodeConfigId != null) {
                parseAndSaveAssigneeConfig(nodeConfigId, attrs, content);
                parseAndSaveFormConfig(nodeConfigId, content, processConfigId);
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * 保存节点并返回ID
     */
    private String saveNodeAndGetId(String processConfigId, String attrs, String content, NodeConfig.NodeType nodeType) {
        Matcher idMatcher = Pattern.compile("id=\"([^\"]+)\"").matcher(attrs);
        Matcher nameMatcher = Pattern.compile("name=\"([^\"]*)\"").matcher(attrs);
        
        if (!idMatcher.find()) {
            return null;
        }
        
        String nodeId = idMatcher.group(1);
        String nodeName = nameMatcher.find() ? nameMatcher.group(1) : "";
        
        // 检查是否有 skipNode 配置（仅用户任务）
        boolean skipNode = false;
        if (nodeType == NodeConfig.NodeType.USER_TASK) {
            skipNode = content.contains("name=\"skipNode\" value=\"true\"") ||
                      attrs.contains("flowable:skipExpression=\"${skipNodeEnabled}\"") ||
                      content.contains("flowable:skipExpression=\"${skipNodeEnabled}\"");
        }
        
        try {
            NodeConfig node = new NodeConfig();
            node.setProcessConfigId(processConfigId);
            node.setNodeId(nodeId);
            node.setNodeName(nodeName);
            node.setNodeType(nodeType);
            node.setSkipNode(skipNode);
            
            nodeMapper.insert(node);
            
            // 查询获取生成的ID
            List<NodeConfig> nodes = nodeMapper.findByProcessConfigId(processConfigId);
            for (NodeConfig n : nodes) {
                if (n.getNodeId().equals(nodeId)) {
                    return n.getId();
                }
            }
        } catch (Exception e) {
            log.error("保存节点失败: nodeId={}, nodeType={}", nodeId, nodeType, e);
        }
        
        return null;
    }
    
    /**
     * 保存节点（简化版）
     */
    private boolean saveNode(String processConfigId, String attrs, String content, NodeConfig.NodeType nodeType) {
        return saveNodeAndGetId(processConfigId, attrs, content, nodeType) != null;
    }
