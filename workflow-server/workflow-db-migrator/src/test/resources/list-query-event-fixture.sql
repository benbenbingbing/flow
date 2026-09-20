-- 仅用于独立 MySQL 测试库，涵盖有效事件优先级与迁移后的草稿状态。

CREATE TABLE entity_list_config (id varchar(64) PRIMARY KEY,entity_id varchar(64),query_interface_extension_id varchar(64),revision int DEFAULT 1,draft_hash varchar(64) DEFAULT 'old',update_time datetime DEFAULT CURRENT_TIMESTAMP,deleted tinyint DEFAULT 0);
CREATE TABLE ui_event_binding (id varchar(64) PRIMARY KEY,owner_type varchar(20),owner_id varchar(64),target_type varchar(20) DEFAULT 'OWNER',target_key varchar(100) DEFAULT '',event_code varchar(50) DEFAULT 'LIST_LOAD',inheritance_mode varchar(20) DEFAULT 'INHERIT',steps_document longtext,revision int DEFAULT 1,enabled tinyint DEFAULT 1,deleted tinyint DEFAULT 0,update_time datetime DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY scope(owner_type,owner_id,target_type,target_key,event_code,deleted));
CREATE TABLE ui_config_release (id varchar(64), snapshot_document longtext, content_hash varchar(64));
INSERT INTO ui_config_release VALUES ('historical','unchanged','unchanged-hash');
INSERT INTO entity_list_config (id,entity_id,query_interface_extension_id) VALUES ('plain','e0','q1'),('before','e0','q2'),('replace','e0','q3'),('inherited','e1','q4'),('disable','e1','q5'),('disabled','e0','q6'),('override','e1','q7'),('append_inherited','e1','q8');
INSERT INTO ui_event_binding (id,owner_type,owner_id,inheritance_mode,steps_document,enabled) VALUES
('b2','LIST','before','INHERIT','[{"strategy":"BEFORE","order":10},{"strategy":"AFTER","order":20}]',1),
('b3','LIST','replace','INHERIT','[{"strategy":"REPLACE","extensionId":"event-query"}]',1),
('entity','ENTITY','e1','INHERIT','[{"strategy":"REPLACE","extensionId":"inherited-query"}]',1),
('b5','LIST','disable','DISABLE','[{"strategy":"AFTER"}]',1),
('b6','LIST','disabled','REPLACE','[{"strategy":"BEFORE"}]',0),
('b7','LIST','override','REPLACE','[{"strategy":"BEFORE"}]',1),
('b8','LIST','append_inherited','INHERIT','[{"strategy":"AFTER"}]',1);
