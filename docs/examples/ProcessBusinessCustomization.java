import com.workflow.contracts.process.model.CommandMeta;
import com.workflow.contracts.process.model.ProcessCommandReceipt;
import com.workflow.contracts.process.query.model.ProcessTaskView;
import com.workflow.contracts.process.query.port.ProcessQueryPort;
import com.workflow.contracts.process.task.model.CompleteTaskCommand;
import com.workflow.contracts.process.task.model.TaskCommandResult;
import com.workflow.contracts.process.task.model.TaskSubmission;
import com.workflow.contracts.process.task.port.ProcessTaskCommandPort;
import com.workflow.contracts.process.variable.model.ProcessVariablePatch;
import com.workflow.contracts.process.variable.port.ProcessVariablePort;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * biz 使用示例：只引用 workflow-port 和 JDK；可在业务模块以构造器注入这些端口。
 * 调用线程必须具有平台真实身份。流程动作的实例范围只允许本实例查询/变量操作，
 * 不代替人工审批身份。业务与流程需要一起回滚时，在上层应用服务开启事务。
 */
public final class ProcessBusinessCustomization {
    private final ProcessQueryPort queries;
    private final ProcessVariablePort variables;
    private final ProcessTaskCommandPort tasks;

    public ProcessBusinessCustomization(ProcessQueryPort queries, ProcessVariablePort variables,
                                         ProcessTaskCommandPort tasks) {
        this.queries = queries;
        this.variables = variables;
        this.tasks = tasks;
    }

    /** 返回全部活动任务，业务按明确的 taskId 选择，不能把第一条当作唯一当前任务。 */
    public List<ProcessTaskView> activeTasks(String instanceId) {
        return queries.listActiveTasks(instanceId);
    }

    /** requestId 来自一次业务意图的持久化标识；失败重试复用，新的业务意图使用新键。 */
    public ProcessCommandReceipt changeRisk(String requestId, String instanceId, String risk) {
        return variables.patchProcessVariables(new ProcessVariablePatch(new CommandMeta(requestId),
                instanceId, Map.of("risk", risk), Set.of()));
    }

    /** 不传表单摘要时平台解析已发布绑定；按钮、字段、权限和下一审批人规则仍会重新校验。 */
    public TaskCommandResult approve(String requestId, String taskId, String comment) {
        return tasks.complete(new CompleteTaskCommand(new CommandMeta(requestId), taskId, "approve",
                TaskSubmission.comment(comment)));
    }
}
