# ADR-011 — No XPDL workflow generation by RegBB

| | |
|---|---|
| Status | Proposed |
| Date | 2026-05-02 |
| Decider | Aare Laponin |
| Related | Solution-level SAD §2.1 (constraint C8); RegBB framework SAD §4.5; component SADs across the board. |

## Context

An earlier draft of the migration plan called for RegBB to generate Joget XPDL workflow processes per service — a workflow-authoring layer on top of the metamodel. Phase 2 of the migration plan dedicated several person-weeks to "XPDL design + authoring" and a "submission backbone" (`wf-activator → DocSubmitter → ProcessingServer`).

In May 2026 the project lead reviewed the actual operational need and reframed: **workflow integration is via Joget's native XPDL process designer**, authored by sysadmins. RegBB doesn't generate XPDL because the platform already has a perfectly good designer for that. Reinventing it inside RegBB would add complexity, fork from the platform's evolution, and eat capacity that should go to subsidy/IM convergence work.

The framework already has a clean dispatch boundary: `mm_action` rows declare *which Joget process to start on which lifecycle event*. `WorkflowDispatcher` resolves the process by id and invokes `WorkflowManager.processStart`. That's the integration; the XPDL is on the other side of the boundary.

## Decision

The RegBB framework **never generates XPDL**. Workflow processes are sysadmin-authored in Joget's native process designer. RegBB's contribution to workflow integration is exactly:

- The `mm_action.kind=status_change` row that declares which Joget process to dispatch.
- `WorkflowDispatcher` that resolves the process id and starts it.
- `RegBbWorkflowEchoTool` — a diagnostic tool plugin that drops into any workflow tool step and writes a `WORKFLOW_ECHO:<processDefId>` audit row showing the engine→workflow handoff context.

The framework is unaware of XPDL syntax, the activity graph, deadlines, escalation, or any other workflow internals.

## Consequences

**Positive:**

- The framework stays small. Workflow concerns don't accrete inside it.
- Joget's process designer evolves; RegBB inherits the evolution at no cost.
- Sysadmins use the tool they already know.
- The boundary is testable: `RegBbWorkflowEchoTool` proves the handoff works without depending on any specific XPDL.

**Negative:**

- Sysadmins must author XPDL. For projects without a sysadmin, this is a real burden.
- "Programme Builder" cannot offer a one-button "publish + workflow" — workflow stays a separate authoring step.
- Cross-domain workflows (subsidy approval → IM entitlement issuance) require coordinated XPDL across two domains; the framework doesn't help.

**Trade-off named:** Convention over Invention won here against "build the missing piece." The principle that pulled the other way was DRY (don't make sysadmins author XPDL by hand for every programme); rejected because the per-programme XPDL is small (typically 3–5 activities) and pattern-reusable, and the cost of a generator outweighs the savings.

**Documents updated:** Solution-level SAD §2.1 C8; framework SAD §4.5; subsidy and IM module SADs name workflow as out of their scope; migration-plan.md scope cuts (per item 4 of the next-actions list).
