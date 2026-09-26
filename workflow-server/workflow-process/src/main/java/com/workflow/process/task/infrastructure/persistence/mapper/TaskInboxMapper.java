package com.workflow.process.task.infrastructure.persistence.mapper;

import com.workflow.process.task.infrastructure.persistence.provider.TaskInboxSqlProvider;

import com.workflow.process.task.application.model.TaskInboxQuery;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 专用于工作台的受控分页读取，不通过 selectList 读取全量表单数据。 */
@Mapper
public interface TaskInboxMapper {
    @SelectProvider(type = TaskInboxSqlProvider.class, method = "selectPage")
    List<ProcessTask> selectPage(@Param("q") TaskInboxQuery query);

    @SelectProvider(type = TaskInboxSqlProvider.class, method = "count")
    long count(@Param("q") TaskInboxQuery query);

    @SelectProvider(type = TaskInboxSqlProvider.class, method = "countUnready")
    long countUnready(@Param("q") TaskInboxQuery query);
}
