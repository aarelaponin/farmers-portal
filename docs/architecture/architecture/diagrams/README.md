# Architecture Diagrams

Five diagrams covering the Lesotho Farmers Portal at the levels architects
typically need: context (the system in its world), container (what's inside
the system), sequence (how key flows execute over time), entity-relationship
(what data is in scope), and deployment (where it physically runs).

All diagrams are authored in **Mermaid** (text-based, version-controllable,
renders natively on GitHub, GitLab, and most Markdown previewers). To view:

* On GitHub / GitLab: open the `.mermaid` file — the diagram renders inline.
* Locally: paste the source into [https://mermaid.live](https://mermaid.live)
  for an interactive editor.
* To embed in a Joget HtmlPage: load Mermaid from CDN
  (`<script src="https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js"></script>`)
  and paste the diagram source inside `<div class="mermaid">…</div>`.
* To export PNG / SVG: VS Code's Markdown Preview Mermaid Support extension
  has a one-click export, or use the Mermaid CLI
  (`npm install -g @mermaid-js/mermaid-cli` → `mmdc -i diagram.mermaid -o diagram.svg`).

## The five diagrams

| # | File | Level | Purpose | When to read |
|---|---|---|---|---|
| 1 | `01-c4-context.mermaid` | C4 Level 1 | The system in its world — who interacts with it, who pays for it, where physical inputs come from. | Starting point for any new stakeholder. |
| 2 | `02-c4-container.mermaid` | C4 Level 2 | What's *inside* the Joget instance — userview, forms, plugins, data stores. | When you need to know "where does the X logic live?" |
| 3 | `03-sequence-voucher-lifecycle.mermaid` | Sequence | The full voucher lifecycle: application submit → eligibility → voucher → redemption → receipt, with side branches for cancel and expire. | When debugging or designing a change to any step. |
| 4 | `04-er-domain.mermaid` | ER | The IM-module domain entities and their relationships, including the Joget master-data tables they reference. | When writing new SQL queries, reports, or forensic queries. |
| 5 | `05-deployment.mermaid` | Deployment | The physical / network topology — VM, Postgres, file store, deployment flow. | When planning ops, scaling, or DR. |

## What's intentionally NOT here

* **Component-level diagrams within the reg-bb-engine bundle.** That's
  inside-the-plugin design and lives in
  `docs/architecture/architecture/components/reg-bb-framework.md` as prose with code
  references. A separate UML class diagram would add maintenance burden
  without much insight beyond what `Activator.java` and the package layout
  already convey.
* **C4 Level 3 (Component) per container.** Same reason — the prose
  per-component SADs cover this level adequately.
* **State machine diagrams** for individual entities (e.g. voucher status
  transitions). The voucher's state machine is documented in
  `docs/architecture/decision-log.md` (D37, D38) as a table — Mermaid would
  duplicate that without adding clarity.

If you need any of these, raise an issue and we'll author them as needed —
they're cheap to add but expensive to keep in sync if no-one's reading them.

## Maintenance discipline

When the system changes meaningfully:

* **New external actor** (e.g. an integration with a new ministry) → update
  diagram 1.
* **New plugin bundle** added to the JAR roster → update diagram 2.
* **New voucher state** (or a state-transition change) → update diagram 3.
* **New IM-domain entity / form** → update diagram 4.
* **Topology change** (e.g. moving Postgres to a separate VM, adding a
  cache layer) → update diagram 5.

The text-based diagrams are intended to track lightly with code changes —
they're not a separate documentation effort but part of the change.

---

*Authored: 2026-05-06 (closes pending task #86). Revision 1.*
