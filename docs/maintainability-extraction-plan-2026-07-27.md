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

## 2026-09-24 frontend extraction update

The current round extracted form-node conversion, entity permissions, publication,
list-column models, SLA editing, related-content target selection, inbox loading
and statistics, event-step validation, and record-version capabilities. The
standalone progress page now delegates BPMN rendering to the shared viewer.
See `output/frontend-refactor-progress-2026-09-24.md` for exact files, sizes,
verification, and the remaining boundaries in each page.

The original strict budget is unchanged and still reports historical excess.
`npm run check:frontend-budget` adds a Git-base comparison for PC, mobile and
shared packages: new files are limited to 900 lines and existing oversized files
cannot grow. This freezes further growth without treating legacy debt as resolved.
The NodeConfigPanel assignee, multi-instance and CC extractions above remain future
work; this round extracted controlled SLA UI and copy-on-write extension updates
validated with real BPMN command-stack undo/redo.
