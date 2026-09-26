package com.workflow.contracts.process.action.port;

import com.workflow.contracts.entity.model.EntityRecordData;
import java.util.Map;

/**
 * 流程动作执行期间可用的运行时数据访问能力。
 *
 * <p>引擎对象以 {@link Object} 暴露，避免 contracts 模块依赖 Flowable 或实体实现类。</p>
 */
public interface FlowActionRuntimePort {

    /**
     * 读取流程变量；查询结果供调用方展示或继续处理。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 流程变量键值结果，供调用方继续处理
     */
    Map<String, Object> getVariables(String processInstanceId);

    /**
     * 读取变量；查询结果供调用方展示或继续处理。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param name 名称，后续用于读取变量时匹配或展示
     * @return 符合条件的流程动作运行时访问结果，供调用方继续处理
     */
    Object getVariable(String processInstanceId, String name);

    /**
     * 设置变量；后续读取或执行将使用更新后的状态。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param name 名称，后续用于设置变量时匹配或展示
     * @param value 待设置变量的原始输入，结果供调用方继续使用
     */
    void setVariable(
            String processInstanceId,
            String name,
            Object value);

    /**
     * 设置流程变量；后续读取或执行将使用更新后的状态。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     */
    void setVariables(
            String processInstanceId,
            Map<String, Object> variables);

    /**
     * 读取流程实例；查询结果供调用方展示或继续处理。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 符合条件的流程动作运行时访问结果，供调用方继续处理
     */
    Object getProcessInstance(String processInstanceId);

    /**
     * 读取历史流程实例；查询结果供调用方展示或继续处理。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 符合条件的流程动作运行时访问结果，供调用方继续处理
     */
    Object getHistoricProcessInstance(String processInstanceId);

    /**
     * 读取当前任务；查询结果供调用方展示或继续处理。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 符合条件的流程动作运行时访问结果，供调用方继续处理
     */
    Object getCurrentTask(String processInstanceId);

    /**
     * 读取任务；查询结果供调用方展示或继续处理。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 符合条件的流程动作运行时访问结果，供调用方继续处理
     */
    Object getTask(String taskId);

    /**
     * 读取实体数据；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 符合条件的流程动作运行时访问结果，供调用方继续处理
     */
    EntityRecordData getEntityData(String entityCode, String entityDataId);

    /**
     * 转换参数；输出作为后续校验或处理的输入。
     *
     * @param params 参数，供本方法转换参数时使用
     * @param targetType 目标类型标识，决定后续参数采用的处理分支
     * @return 转换后的参数结果，供调用方继续处理
     */
    <T> T convertParams(Map<String, Object> params, Class<T> targetType);
}
