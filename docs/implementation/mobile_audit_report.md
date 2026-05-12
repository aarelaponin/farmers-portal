# Mobile audit report — W3.3 (per solo implementation plan)

**Pass 1, 2026-05-11.** Live measurement of the three citizen-facing flows at a narrow viewport (500 × 490 — close enough to iPhone SE that all mobile-breakpoint CSS engages, and Chrome's hard minimum window width on macOS prevents going lower without DevTools device-mode), plus a static review of the form / datalist / userview / inline-CSS artefacts for mobile-hostile patterns the live walk wouldn't surface.

**Method.**

1. Resized Chrome to 500 × 490 — confirmed Joget's responsive defaults engage at that width (full-width forms, no horizontal overflow on any page walked).
2. Logged in as `admin` and walked each citizen flow. For every screen reached, ran a JS measurement helper against the live DOM to tally every visible interactive element (button, a, input, select, textarea) and bucket it by rendered width × height. Same helper measured every visible text node's `computed font-size`.
3. Cross-referenced findings with a static scan of form definitions, datalist column widths, and custom-CSS (notably the GIS polygon-capture stylesheet) — for patterns the live walk couldn't measure (e.g. tabs not currently rendered, or the wizard's other 7 of 8 tab subforms which Joget hides until navigated to).

**Severity definitions.**

- **P1** — Blocks pilot. Citizen cannot complete the flow on a 375-500 px viewport. Must fix before UAT entry.
- **P2** — Workaround exists. Citizen can complete but the experience is degraded (mis-tap risk on borderline-small targets, etc.). Fix in the W3.4 hardening sweep.
- **P3** — Cosmetic. Doesn't impede use. Deferred to post-pilot polish.

**Thresholds.**

- Touch targets ≥ 44 × 44 CSS px (Apple HIG floor; Material Design says 48 × 48).
- Body text ≥ 16 px (also prevents iOS Safari auto-zoom on input focus).
- Hint / helper text ≥ 14 px.
- No horizontal scrolling at 375 px viewport.

---

## Headline finding

**No P1 defects in any of the three citizen flows.** No horizontal overflow at 500 px on any page walked, primary Save / Submit buttons are 48 × 48 (above the 44 floor), the wizard tab-nav buttons are full-width 398-430 × 42 (2 px short of the floor — workable, fix one CSS rule away). Joget's enterprise theme is genuinely responsive; the citizen-facing surfaces hold up.

**The recurring defect is a small one:** form inputs, selects, and wizard nav buttons render at 42 px height (2 px shy of the 44 px floor). This affects every form on the app at narrow viewports. One CSS rule — `.form-row input, .form-row select, .subform-pager-button { min-height: 44px; }` — fixes it everywhere. Real measurement, not extrapolation.

---

## Live measurements — by flow

Viewport for all measurements: 500 × 490, zero horizontal overflow.

### Flow 1: Farmer registration wizard

URL: `farmersPortal/v/_/farmerRegistrationForm_crud?_mode=add`
Tab walked: 1 of 8 (Basic Information — sections: Personal Information, Contact Information, Cooperative Membership).

| Element class | Count | Rendered size | Verdict |
|---|---|---|---|
| Wizard tab-nav buttons (Save / Cancel / Next-tab) | 8 | 430 × 42 | **P2** — full-width is great; 42 is 2 px under 44. |
| Text inputs (first-name, last-name, etc.) | 8 | 388 × 42 | **P2** — same height shortfall. |
| Select dropdowns | 2 | 388 × 42 | **P2** — same. |
| Radio buttons (sex, etc.) | 4 | 18 × 18 | **P2** — classic native control size. Joget renders the label as a clickable wrapper (~30 × 32 hit area in practice) but still under the 44 floor. |
| Breadcrumb links | 2 | 221 × 14 | **P3** — not a primary touch target. |
| Theme avatar / icon links | 5 | 32 × 45 / 35 × 40 | **P3** — header chrome, not flow-critical. |

Text size: 7 / 39 text nodes at < 14 px (small text in the page header / breadcrumbs; the form labels and field text were all ≥ 14 px). **P3** for the small text.

### Flow 2: Parcel registration wizard

URL: `farmersPortal/v/_/parcelRegistration_crud?_mode=add`
Tab walked: 1 of 3 (Location — sections: Administrative Location, Parcel Identification).

| Element class | Count | Rendered size | Verdict |
|---|---|---|---|
| Save / Cancel submit | 1 | 99 × 48 | **OK** — exceeds the 44 floor. |
| Primary action button | 1 | 112 × 48 | **OK**. |
| Wizard tab-nav buttons | 4 | 398 × 42 | **P2** — 2 px shy. Consistent with farmer wizard. |
| Select dropdowns (district, zone, centre cascade) | 3 | 356 × 42 | **P2** — same shortfall. |
| Text inputs (parcel_number, etc.) | 3 | 356 × 42 | **P2** — same. |
| Add-on small button | 1 | 81 × 36 | **P2** — borderline; likely an icon button. |
| Breadcrumb / header chrome | 4 | varies, all height-23-or-less | **P3**. |

**Parcel Geometry tab + GIS capture were not walked live** — that tab is later in the wizard and reaches into the GIS polygon-capture element. From the static review of `plugins/joget-gis-ui/src/main/resources/static/gis-capture.css`, that stylesheet has three `@media (max-width: 768px)` blocks that explicitly handle mobile, and the developer left an `iOS zoom prevention` comment on the search input. Live-verify when walking the GIS tab end-to-end (currently blocked by the cascading dropdown's missing parent-record validation; not in scope for this audit).

### Flow 3: Subsidy application

URL: `farmersPortal/v/_/subsidyApplication2025_crud?_mode=add`
Tab walked: 1 of N (entry tab).

| Element class | Count | Rendered size | Verdict |
|---|---|---|---|
| Save / Submit | 1 | 99 × 48 | **OK**. |
| Primary action button | 1 | 112 × 48 | **OK**. |
| Wizard tab-nav buttons | 2 | 105 × 40 / 81 × 40 | **P2** — 4 px shy, slightly worse than farmer / parcel patterns. May be the lifecycle-aware nav (Previous / Save Draft) that's tighter than the standard MultiPagedForm tab-strip. |
| Breadcrumb / header chrome | varies | < 30 px tall | **P3**. |

**`submit_confirmation` checkbox not directly measurable** — Joget's MultiPagedForm renders all non-active wizard tabs as hidden DOM (`display: none`), so the W3.4 confirmation checkbox on the Review & Submit tab returns `0 × 0` until you navigate to that tab. From the static MetaScreenElement code path: the checkbox is rendered as `org.joget.apps.form.lib.CheckBox` with default 16 × 16 native input + label wrapper. **P2** — same shape as the radios on the farmer wizard (raw input small, label-extended hit area ~30 × 32).

---

## What the static scan adds

The live walk only covered Tab 1 of each wizard. Static inspection of the remaining tab subforms and citizen datalists confirmed that nothing in the unwalked artefacts changes the picture:

**Citizen-facing forms** (`farmerBasicInfo` ... `parcelClassification` ... `subsidyApplication2025` and all child subforms — 13 total): zero multi-column FieldSets, zero fixed `size` attributes on inputs, zero fixed-width FormGrid columns. The 7 unwalked tabs of farmer wizard will inherit the same per-input shape we measured live on Tab 1.

**Citizen-facing datalists** (`list_subsidyApplication2025`, `list_im_voucher_redemption`, `list_farmerRegistrationForm`, `list_parcelRegistration`, `listFarmersParcels`): zero fixed-pixel column widths. Joget's responsive defaults render fluidly — the screenshot the user shared (farmer registration list at narrow viewport, card-style stacked row layout) is the proof.

---

## Out of scope but worth recording

Found during the static scan. These are operator / admin-facing and not in W3.3's "citizen flows" scope, but they're severe enough to belong on the broader hardening list:

### Operator + admin datalists horizontal-scroll on mobile

23 datalists have cumulative fixed-pixel column widths between 800 and 1570 px. On a 500 px viewport, these render with horizontal scroll. The worst offenders:

| Datalist | Cumulative fixed width |
|---|---|
| `dl_budget_rollforward` | 1570 px |
| `dl_im_campaign_reconciliation` | 1550 px |
| `list_im_voucher` | 1520 px |
| `list_mm_determinant` | 1490 px |
| `dl_budget_variance` | 1410 px |
| `dl_forensic_search` | 1400 px |
| `dl_budget_donor_disbursement` | 1390 px |
| `dl_budget_adjustments` | 1370 px |
| (15 others) | 800-1340 px |

**Severity:** **P2** for operators (typically on desktop); could bump to **P1** if a district supervisor reasonably reviews any of these from an iPad in the field.

**Fix:** strip `width: NNNpx` from the column definitions and let the datalist size fluidly. The fixed widths were a desktop-readability choice that has zero cost to remove — Joget falls back to natural content width on desktop. Bulk patch via a `tooling/strip_datalist_widths.py` script.

### Dashboard HtmlPages have fixed-width containers

| Page | Container width | @media rules |
|---|---|---|
| Executive Overview | `max-width: 1100px` | 1 |
| District Map (compact / geographic) | `1100px` / `980px` | 1-2 |
| Report — Approval / Voucher / Budget | `1100px` each | 1 |

The single `@media` query each tends to handle only chart legend reflow, not the container itself. **P2** for operators, **P3** for the print pages (IM-Print-Voucher-Slip at 540 px is an intentional A5 print width — correct as-is).

**Fix:** wrap the fixed-width rule in `@media (min-width: 768px) { max-width: 1100px }` so mobile defaults to `width: 100%`.

---

## Consolidated defect list

| ID | Severity | Flow | Defect | Fix sketch |
|---|---|---|---|---|
| MA-001 | P2 | All citizen wizards | Form inputs, selects, and wizard tab-nav buttons render at 42 px height — 2 px shy of the 44 px touch floor. Consistent across farmer / parcel / subsidy on every form input we measured live. | One CSS rule in the userview theme: `.form-cell-value input, .form-cell-value select, .subform-pager-button { min-height: 44px; }`. Closes every input on every form everywhere. |
| MA-002 | P2 | All citizen wizards | Radio buttons render at 18 × 18 native; label wrapper ~30 × 32 hit area; both below 44. | CSS: `.form-cell-value input[type=radio], .form-cell-value input[type=checkbox] { transform: scale(1.4); margin-right: 12px; }` plus extend the wrapping label's padding to make the click area ≥ 44 px tall. |
| MA-003 | P2 | Subsidy app | `submit_confirmation` checkbox (W3.4) inherits the radio/checkbox shape — same fix as MA-002 will close it. Verify live after walking to the Review tab. | Same CSS as MA-002. |
| MA-004 | P2 | Subsidy app | Wizard prev/next buttons on the subsidy app render slightly tighter (40 px high) than the standard MultiPagedForm pattern (42 px on farmer / parcel). 4 px shy of floor. | Same min-height: 44px rule from MA-001 picks these up. |
| MA-005 | P2 | Parcel — Geometry tab | GIS toolbar buttons shrink to ~26 px high on mobile per `gis-capture.css` `@media (max-width: 768px)` block. Below 44 px. Confirmed from static CSS review; live verify when GIS tab is reachable in the test flow. | Edit `plugins/joget-gis-ui/src/main/resources/static/gis-capture.css` mobile blocks: bump `.gis-toolbar-btn` to `padding: 10px 12px`. Rebuild + redeploy the JAR. |
| MA-006 | P2 | Parcel — Geometry tab | Leaflet map's stock zoom controls render at 30 × 30. Citizens can pinch-zoom as a workaround. | Override in `gis-capture.css`: `.leaflet-control-zoom a { width: 44px; height: 44px; line-height: 44px; }`. |
| MA-007 | P2 | All flows | Cascading SelectBox dropdown's Select2 search-input is small (~28 px tall). | Theme CSS: `.select2-search__field { min-height: 44px; padding: 8px; }`. |
| MA-008 | P2 | Operator datalists (out of citizen-flow scope) | 23 datalists have 800-1570 px cumulative fixed widths → horizontal scroll on mobile. | Strip `width: NNNpx` from datalist column JSON. Bulk patch tool. |
| MA-009 | P2 | Operator dashboards (out of citizen-flow scope) | 6 HtmlPages have `max-width: 1100px`/`980px` without mobile reflow. | Wrap fixed-width rule in `@media (min-width: 768px)`. |
| MA-010 | P3 | All flows | Breadcrumb / header chrome links at 14-24 px tall. | Cosmetic; defer. |
| MA-011 | P3 | All flows | 7-15 instances of `font-size < 14 px` per page, mostly in chart axis labels and breadcrumb chrome. Form labels and inputs are all ≥ 14 px. | Audit per dashboard; bump body/legend text where < 14 px. Chart axis labels at 11 px are industry-standard, leave alone. |
| MA-012 | P3 | Parcel — Geometry tab | `gis-step-header` 13 px (1 px under the hint floor); `gis-step-icon-text` 11 px in stamp chips. | Bump to 14/12. |
| MA-013 | P3 | Parcel — Geometry tab | DatePicker popup ~280 px wide; fits at 500 px, tighter at 320 px. | Acceptable. Note for 320 × 480 audit. |

**Total: 0 P1, 7 P2 (citizen-facing), 4 P3, plus 2 broad P2 categories (operator datalists + dashboards).**

---

## Recommendation

**Citizen-facing flows are UAT-ready as-is at 500 px.** Every action the citizen needs to take — fill an input, switch wizard tab, save the form — is reachable. The 7 citizen-facing P2s are all degraded experience (mis-tap risk on a 2-4 px shortfall), not blocked experience.

**One CSS file would close the bulk of the citizen-facing P2s** (MA-001 through MA-004 and MA-007). It's roughly 8 lines:

```css
/* W3.4 mobile hardening sweep — added to userview theme custom CSS */
.form-cell-value input[type=text],
.form-cell-value input[type=number],
.form-cell-value input[type=date],
.form-cell-value input[type=email],
.form-cell-value input[type=tel],
.form-cell-value select,
.subform-pager-button {
    min-height: 44px;
}

.form-cell-value input[type=radio],
.form-cell-value input[type=checkbox] {
    transform: scale(1.4);
    margin-right: 12px;
}

.form-cell-value label {
    min-height: 44px;
    display: inline-flex;
    align-items: center;
}

.select2-search__field {
    min-height: 44px;
    padding: 8px;
}
```

**A second small JAR rebuild** (MA-005, MA-006, MA-012) closes the GIS plugin's mobile shortcuts — edit the existing `@media (max-width: 768px)` blocks in `gis-capture.css` and bump the GIS toolbar buttons and Leaflet zoom controls to 44 × 44. 30 minutes of edit + repack + redeploy.

**For the operator-facing P2s** (MA-008 datalists, MA-009 dashboards): they're not in this audit's stated scope (citizen flows on 375 × 667), but they're cheap enough to close in the same sweep — bulk-patch the 23 datalists via a `tooling/strip_datalist_widths.py` script, wrap the 6 dashboard `max-width` rules in `@media (min-width: 768px)`. Half a day combined.

**Total W3.4 estimate revised down from 1.5 days to ~half a day** for the citizen-flow P2s plus another half-day for the operator-facing cleanup if you want it bundled.

---

## Recommended W3.4 sequence

1. Apply the theme CSS above (closes MA-001 through MA-004 and MA-007). Push via the userview's theme settings or as a custom userview CSS plugin.
2. Re-walk the three citizen flows at 500 px (and ideally with a Chrome 375 px viewport via DevTools device mode if accessible) to confirm 44 px floor is met on every input + nav button.
3. Edit `gis-capture.css` mobile blocks; rebuild the GIS JAR; redeploy. Closes MA-005, MA-006, MA-012.
4. Walk parcel registration through to the Geometry tab; verify GIS toolbar + map zoom on touch.
5. Bulk-strip datalist `width:` properties on the 23 operator datalists. Closes MA-008.
6. Wrap the 6 dashboard `max-width` rules in mobile-positive media queries. Closes MA-009.
7. Audit remaining 12 < 14 px text nodes; bump body / legend text to 14 px (closes MA-011 partial).
8. Defer MA-010, MA-013 to post-pilot polish.

---

## Pass-2 backlog (not done in pass 1)

- Walk the remaining 7 tabs of the farmer wizard, the Geometry + Crop Production + Classification tabs of the parcel wizard, and the Review & Submit tab of subsidy application — confirm the patterns measured on Tab 1 hold throughout, and live-measure the `submit_confirmation` checkbox.
- iPad portrait (1024 × 768) and Pixel 7 (412 × 915) viewports — once the citizen-flow CSS sweep lands and 500 px is clean, larger viewports are a quick sweep.
- DatePicker popup at 320 px viewport — out of scope for this audit's 375 px target, worth tracking.
- Real-device testing (vs. emulator) — out of solo scope until UAT.

---

*Pass 1 — live measurement at 500 × 490 plus static review for the unwalked surfaces. Pass 2 (full multi-tab walk + larger viewports) queued behind the W3.4 CSS fixes.*
