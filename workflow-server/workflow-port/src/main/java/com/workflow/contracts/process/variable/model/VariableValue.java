package com.workflow.contracts.process.variable.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 保留变量不存在与存在但为 null 的区别。
 * @param present 作用域中是否存在该键
 * @param value 脱离引擎状态的只读值
 */
public record VariableValue(
        boolean present,
        Object value) {

}
