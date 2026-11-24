package com.workflow.port.process.model.query;

/** ALL 由调用方授权，其余范围描述当前用户的查询视角。 */
public enum ProcessInstanceScope { MY_STARTED, PARTICIPATED, READABLE, ALL }
