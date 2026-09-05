package com.workflow.contracts.embed.runtime.port;

import com.workflow.contracts.embed.EmbedNativeListDependencyClosure.ListCoordinate;
import java.util.List;

/**
 * 供 Embed Launch 从精确 List Release 读取 open-list 依赖的只读端口。
 */
public interface EmbedNativeListDependencyRuntimePort {

    /**
     * 读取一个精确列表发布版本及其直接 open-list 目标。
     *
     * @param target 服务端已解析的列表坐标；根节点的 listConfigId 可以为空
     * @return 已验证并补全 listConfigId 的节点
     */
    ResolvedList resolveExact(ListCoordinate target);

    /** 已验证、并补全列表配置 ID 的依赖节点。 */
    record ResolvedList(
            ListCoordinate list,
            List<ListCoordinate> openListTargets) {

        public ResolvedList {
            openListTargets = openListTargets == null
                    ? List.of() : List.copyOf(openListTargets);
        }
    }
}
