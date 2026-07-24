package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessCcOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 抄送发件箱 Mapper
 * 提供抄送消息的查询与抢占式领取操作，支撑可靠异步投递
 */
@Mapper
public interface ProcessCcOutboxMapper extends BaseMapper<ProcessCcOutbox> {
    /**
     * 查询待发送的发件箱记录。
     * <p>
     * 筛选状态为 PENDING/FAILED/PROCESSING 且已到重试时间的记录，最多100条。
     *
     * @return 待发送的发件箱记录列表
     */
    @Select("SELECT * FROM process_cc_outbox WHERE status IN ('PENDING','FAILED','PROCESSING') " +
            "AND (next_retry_time IS NULL OR next_retry_time <= NOW()) ORDER BY create_time LIMIT 100")
    List<ProcessCcOutbox> findReady();

    /**
     * 抢占式领取发件箱记录（乐观锁）。
     * <p>
     * 将状态置为 PROCESSING 并将下次重试时间延后5分钟，利用条件更新避免并发重复处理。
     *
     * @param id 发件箱记录ID
     * @return 受影响行数，1表示领取成功，0表示已被其他实例领取
     */
    @Update("UPDATE process_cc_outbox SET status = 'PROCESSING', " +
            "next_retry_time = DATE_ADD(NOW(), INTERVAL 5 MINUTE) WHERE id = #{id} " +
            "AND status IN ('PENDING','FAILED','PROCESSING') " +
            "AND (next_retry_time IS NULL OR next_retry_time <= NOW())")
    int claim(@Param("id") String id);
}
