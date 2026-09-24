package com.workflow.process.instance.api.response;

import java.util.Date;

/** 单个业务记录的一轮流程；所有状态及时间均属于该实例，不能用实体最新投影覆盖。 */
public record ProcessRoundDTO(String processInstanceId, int generation, String status,
                              String endType, Date startTime, Date endTime) {}
