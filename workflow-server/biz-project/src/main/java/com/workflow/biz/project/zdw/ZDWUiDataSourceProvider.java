package com.workflow.biz.project.zdw;

import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.contracts.entity.ui.spi.UiDataSourceProvider;
import com.workflow.contracts.ui.UiInvocationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * UI数据源扩展，目前这个数据源返回的是 数据回填使用到的数据
 */
@Component
@Slf4j
public class ZDWUiDataSourceProvider implements UiDataSourceProvider {
    @Override
    public String getCode() {
        return "ZDW_UI_DATA_SOURCE_PROVIDER";
    }

    @Override
    public String getDisplayName() {
        return "周大伟自定义数据源";
    }

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
