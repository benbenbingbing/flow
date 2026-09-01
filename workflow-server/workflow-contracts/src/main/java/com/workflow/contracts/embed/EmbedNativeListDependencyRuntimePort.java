package com.workflow.contracts.embed;

import com.workflow.contracts.embed.EmbedNativeListDependencyClosure.ListCoordinate;
import java.util.List;

/**
 * 供 Embed Launch 从精确 List Release 读取 open-list 依赖的只读端口。
 *
 * <p>实现位于 Entity 边界，必须校验 Release 哈希、配置归属和按钮内目标坐标。
 * 应用层只负责遍历、去环和把结果写入不可变 Runtime Snapshot。</p>
 */
public interface EmbedNativeListDependencyRuntimePort {

    /**
     * 读取一个精确列表发布版本及其直接 open-list 目标。
     *
     * @param target 服务端已解析的列表坐标；根节点的 listConfigId 可以为空
     * @return 已验证并补全 listConfigId 的节点
     */
    ResolvedList resolveExact(ListCoordinate target);

    record ResolvedList(
            ListCoordinate list,
            List<ListCoordinate> openListTargets) {

        public ResolvedList {
            openListTargets = openListTargets == null
                    ? List.of() : List.copyOf(openListTargets);
        }
    }
}
