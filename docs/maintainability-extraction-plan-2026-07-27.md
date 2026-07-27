# Maintainability Extraction Plan

This review freezes the exact file sizes introduced by the full workflow
acceptance work. The budget still fails on any additional line. These
extractions are intentionally separate from the behavioral fixes so they can
be reviewed without mixing runtime changes into the acceptance closure.

## LinkageConfigPanel.vue

- Move linkage expression serialization into `linkageEngine.js`.
- Keep the panel responsible for editing state and validation only.
- Target: below 900 lines without changing generated rule semantics.

## NodeConfigPanel.vue

- Extract assignee and multi-instance person-resolver editing into a focused
  component and composable.
- Extract CC recipient rule editing into a focused component.
- Keep the parent panel responsible for BPMN element coordination.
- Target: below 3,600 lines with node-property regression coverage retained.

## ProcessBpmnPublishSanitizer.java

- Extract receive-task timeout rewriting into a dedicated DOM rewriter.
- Extract explicit CC rewriting and task-specific validation into strategies.
- Keep the sanitizer as the ordered publish pipeline.
- Target: below the 800-line backend default.

## ProcessDefinitionNodeSyncService.java

- Extract DOM status-mapping parsing and node-name lookup into a parser.
- Keep transactional replacement and mapper orchestration in the service.
- Target: below 760 lines while preserving fail-fast behavior.

## ProcessProgressRuntimeService.java

- Extract published node-form resolution and runtime-purpose selection into a
  dedicated form-resolution service.
- Keep progress aggregation and DTO assembly in the runtime service.
- Target: below 1,000 lines with active and historical form tests retained.
