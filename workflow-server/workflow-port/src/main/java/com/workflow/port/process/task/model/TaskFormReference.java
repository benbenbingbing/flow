package com.workflow.port.process.task.model;

import java.util.*;
import com.workflow.port.process.model.*;

/**
 * 调用方所依据的表单版本；为空时仍须由服务端解析并校验发布表单。
 * @param formId 发布表单 ID
 * @param configHash 发布配置摘要
 */
public record TaskFormReference(
        String formId,
        String configHash) {

}
