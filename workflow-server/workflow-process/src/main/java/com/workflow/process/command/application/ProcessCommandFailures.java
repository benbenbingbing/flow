package com.workflow.process.command.application;

import com.workflow.contracts.process.error.ProcessOperationException;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.error.ForbiddenException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;
import java.util.function.Supplier;

/** 将平台已有的明确业务拒绝转换为公共契约错误；技术故障保留原异常及事务回滚语义。 */
public final class ProcessCommandFailures {
    private ProcessCommandFailures() {}

    /** 在事务代理外转换并发异常，覆盖提交阶段才发现的 Flowable 乐观锁冲突。 */
    public static <T> T translate(String targetId, Supplier<T> work) {
        try { return work.get(); }
        catch (ProcessOperationException error) { throw error; }
        catch (BusinessConflictException error) {
            throw new ProcessOperationException(error.getErrorCode(), error.getMessage(), targetId, error);
        } catch (BusinessForbiddenException error) {
            throw new ProcessOperationException(error.getErrorCode(), error.getMessage(), targetId, error);
        } catch (ForbiddenException error) {
            throw new ProcessOperationException("PROCESS_ACCESS_DENIED", error.getMessage(), targetId, error);
        } catch (FlowableOptimisticLockingException | FlowableTaskAlreadyClaimedException error) {
            throw new ProcessOperationException("PROCESS_STATE_CHANGED", "流程或任务状态已变化，请重新读取后再操作", targetId, error);
        } catch (org.flowable.common.engine.api.FlowableObjectNotFoundException error) {
            if (org.flowable.task.api.Task.class.equals(error.getObjectClass())) {
                throw new ProcessOperationException("TASK_ALREADY_COMPLETED", "任务不存在或已被处理", targetId, error);
            }
            throw error;
        } catch (IllegalArgumentException error) {
            throw new ProcessOperationException("PROCESS_INVALID_ARGUMENT", error.getMessage(), targetId, error);
        }
    }
}
