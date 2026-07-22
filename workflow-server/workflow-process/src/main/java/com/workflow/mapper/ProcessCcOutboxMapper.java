package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessCcOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProcessCcOutboxMapper extends BaseMapper<ProcessCcOutbox> {
    @Select("SELECT * FROM process_cc_outbox WHERE status IN ('PENDING','FAILED','PROCESSING') " +
            "AND (next_retry_time IS NULL OR next_retry_time <= NOW()) ORDER BY create_time LIMIT 100")
    List<ProcessCcOutbox> findReady();

    @Update("UPDATE process_cc_outbox SET status = 'PROCESSING', " +
            "next_retry_time = DATE_ADD(NOW(), INTERVAL 5 MINUTE) WHERE id = #{id} " +
            "AND status IN ('PENDING','FAILED','PROCESSING') " +
            "AND (next_retry_time IS NULL OR next_retry_time <= NOW())")
    int claim(@Param("id") String id);
}
