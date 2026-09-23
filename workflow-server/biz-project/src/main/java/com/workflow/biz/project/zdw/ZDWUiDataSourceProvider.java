package com.workflow.biz.project.zdw;

import com.workflow.contracts.entity.list.model.DataScopePlan;
import com.workflow.contracts.entity.ui.spi.UiDataSourceProvider;
import com.workflow.contracts.entity.ui.context.UiInvocationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * UI数据源扩展，目前这个数据源返回的是 数据回填使用到的数据
 */
@Component
@Slf4j
public class ZDWUiDataSourceProvider implements UiDataSourceProvider {
    /**
     * 读取编码；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的编码文本，供调用方比较或展示
     */
    @Override
    public String getCode() {
        return "ZDW_UI_DATA_SOURCE_PROVIDER";
    }

    /**
     * 读取用户可见名称，供页面和操作日志展示。
     *
     * @return 读取后的展示名称文本，供调用方比较或展示
     */
    @Override
    public String getDisplayName() {
        return "周大伟自定义数据源";
    }

    /**
     * 执行{@code zdw}界面数据来源提供者，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续{@code zdw}界面数据来源提供者步骤传递身份、配置或状态
     * @param dataScopePlan 数据作用域方案，供本方法执行{@code zdw}界面数据来源提供者时使用
     * @param configuration 配置内容，决定后续{@code zdw}界面数据来源提供者的处理规则
     * @param input 待执行{@code zdw}界面数据来源提供者的原始输入，结果供调用方继续使用
     * @return 执行后的{@code zdw}界面数据来源提供者结果，供调用方继续处理
     */
    @Override
    public Object execute(UiInvocationContext context,
                          DataScopePlan dataScopePlan, Map<String, Object> configuration, Map<String, Object> input) {

        log.info("Executing ZDWUiDataSourceProvider with context: {}, dataScopePlan: {}, configuration: {}, input: {}",
                  context, dataScopePlan, configuration, input);

        Map<String,Object> data = Map.of(
                "message", "Hello from ZDWUiDataSourceProvider!",
                "userName", "周大伟 userName",
                "userCode", "周大伟 userCode",
                "input", input,
                "configuration", configuration
        );

        return data;
    }
}
