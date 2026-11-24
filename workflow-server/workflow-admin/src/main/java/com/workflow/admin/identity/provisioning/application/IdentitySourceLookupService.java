package com.workflow.admin.identity.provisioning.application;

import com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper.IdentityPersistenceMapper;
import com.workflow.port.system.model.identity.ExternalIdentityKey;
import com.workflow.port.system.model.identity.IdentityObjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按来源命名空间回查未删除的本地身份标识。 */
@Service
@RequiredArgsConstructor
public class IdentitySourceLookupService {
    private final IdentityPersistenceMapper persistence;

    /** 校验外部键格式并批量回查，来源和目标记录的访问范围由 Port 调用方授权。 */
    @Transactional(readOnly = true)
    public Map<String, String> findIds(IdentityObjectType type, String sourceCode, List<String> externalIds) {
        if (externalIds == null || externalIds.size() > 100) {
            throw new IllegalArgumentException("单次最多查询 100 个来源 ID");
        }
        if (externalIds.isEmpty()) return Map.of();

        var keys = new LinkedHashMap<String, String>();
        for (String externalId : externalIds) {
            var key = new ExternalIdentityKey(sourceCode, externalId);
            IdentityAccessService.validateKey(key);
            keys.putIfAbsent(IdentitySourceStore.hash(type, key), externalId);
        }
        var rows = new HashMap<String, String>();
        persistence.findSourceIds(type, List.copyOf(keys.keySet()))
                .forEach(row -> rows.put(row.sourceKey(), row.localId()));
        var result = new LinkedHashMap<String, String>();
        keys.forEach((key, externalId) -> {
            String id = rows.get(key);
            if (id != null) result.put(externalId, id);
        });
        return Collections.unmodifiableMap(result);
    }
}
