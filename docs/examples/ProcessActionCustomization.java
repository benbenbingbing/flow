import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.spi.FlowActionProvider;

/**
 * 仅依赖 port/spi 的流程动作示例；宿主负责注册 Bean、签发当前实例范围并清理执行上下文。
 * 发布前按新契约重新编译外部扩展，不能沿用旧版 Flowable 返回类型的二进制文件。
 */
public final class ProcessActionCustomization implements FlowActionProvider {
    @Override
    public void execute(FlowActionContext context) {
        // getVariables 保留触发快照；需读取执行此刻的变量时显式使用 getLiveVariables。
        Object currentRisk = context.getLiveVariables().get("risk");
        if (currentRisk != null) {
            // 固定调用顺序的快捷写入由宿主生成幂等键，不允许覆盖 approved 等编排字段。
            context.setProcessVariable("riskReviewed", true);
        }
    }
}
