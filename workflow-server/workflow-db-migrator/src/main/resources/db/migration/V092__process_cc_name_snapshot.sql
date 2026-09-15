-- 新知会保存创建时的流程数据名称；历史记录保持原样，不关联业务表补写。
ALTER TABLE process_cc_record
    ADD COLUMN data_name varchar(500) DEFAULT NULL COMMENT '知会创建时的流程数据名称快照'
        AFTER process_name;
