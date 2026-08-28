package com.workflow.process.assignment.relative;

import com.workflow.contracts.identity.position.OrganizationUnitSnapshot;
import com.workflow.contracts.identity.position.PositionHolderView;

import java.util.List;

/**
 * 相对组织职务的权威试算结果，由运行时同一解析器生成。
 */
public record RelativeOrgPositionPreview(
        OrganizationUnitSnapshot anchorUnit,
        List<ScannedUnit> scannedUnits,
        MatchedUnit matchedUnit,
        List<PositionHolderView> holders,
        String resultCode,
        String reasonCode,
        String reasonMessage,
        List<String> warnings,
        String directoryRevision) {

    public RelativeOrgPositionPreview {
        scannedUnits = scannedUnits == null ? List.of() : List.copyOf(scannedUnits);
        holders = holders == null ? List.of() : List.copyOf(holders);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record ScannedUnit(
            String id,
            String name,
            String type,
            int depth,
            String result) {
    }

    public record MatchedUnit(
            String id,
            String name,
            String type,
            int depth) {
    }
}
