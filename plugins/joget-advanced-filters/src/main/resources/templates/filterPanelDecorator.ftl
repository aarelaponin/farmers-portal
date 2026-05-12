<span id="roFilterPanelMarker" style="display:none"></span>
<style>
/* ═══════════════════════════════════════════════════════════
   Filter Panel Decorator — targets Joget DataList structure:
     form.filter_form > div.filters > span.filter-cell
   ═══════════════════════════════════════════════════════════ */

/* ── Panel chrome ── */
.ro-filters-panel{background:#fff;border:1px solid #d1d5db;border-radius:6px;margin-bottom:16px;overflow:hidden}
.ro-filters-header{display:flex;justify-content:space-between;align-items:center;padding:10px 20px;cursor:pointer;user-select:none;border-bottom:1px solid transparent;transition:border-color .2s}
.ro-filters-header:hover{background:#fafbfc}
.ro-filters-panel.ro-open .ro-filters-header{border-bottom-color:#d1d5db}
.ro-fh-title{font-size:12px;text-transform:uppercase;letter-spacing:.05em;color:#6b7280;font-weight:600;display:flex;align-items:center;gap:8px}
.ro-active-count{font-size:11px;background:#2563eb;color:#fff;padding:1px 7px;border-radius:10px;font-weight:600}
.ro-chevron{width:18px;height:18px;color:#6b7280;transition:transform .25s ease}
.ro-filters-panel.ro-open .ro-chevron{transform:rotate(180deg)}
.ro-filters-body{max-height:0;overflow:hidden;transition:max-height .3s ease}
.ro-filters-panel.ro-open .ro-filters-body{max-height:800px}

/* ── Grid layout on div.filters ── */
.ro-filters-panel .filter_form{margin:0 !important;border:none !important;box-shadow:none !important}
.ro-filters-panel .filter_form .mobile_search_trigger{display:none !important}
.ro-filters-panel .filters{
    display:grid !important;
    grid-template-columns:repeat(${columns!}, 1fr) !important;
    gap:14px !important;
    padding:16px 20px !important;
    text-align:left !important;
    margin:0 !important;
}

/* ── Each filter cell ── */
.ro-filters-panel .filter-cell{
    display:flex !important;
    flex-direction:column !important;
    gap:4px !important;
    margin:0 !important;
    padding:0 !important;
}

/* ── Show labels on desktop (Joget hides mobile_label) ── */
.ro-filters-panel .filter-cell label.mobile_label{
    display:block !important;
    font-size:11px !important;
    font-weight:600 !important;
    color:#6b7280 !important;
    text-transform:uppercase !important;
    letter-spacing:.03em !important;
    margin-bottom:2px !important;
}

/* ── Type badges (added by JS) ── */
.ro-type-badge{
    display:inline-block;font-size:9px;font-weight:600;
    padding:1px 6px;border-radius:3px;margin-left:6px;
    vertical-align:middle;text-transform:capitalize;letter-spacing:.02em;
}
.ro-type-badge.ro-badge-multi{background:#dbeafe;color:#2563eb}
.ro-type-badge.ro-badge-text{background:#dcfce7;color:#16a34a}
.ro-type-badge.ro-badge-date{background:#dbeafe;color:#2563eb}

/* ── Inputs ── */
.ro-filters-panel input[type=text]{
    border:1px solid #d1d5db !important;border-radius:4px !important;
    padding:0 8px !important;font-size:13px !important;color:#1f2937 !important;
    background:#fff !important;height:34px !important;box-sizing:border-box !important;
    width:100% !important;
}
.ro-filters-panel select{
    border:1px solid #d1d5db !important;border-radius:4px !important;
    padding:0 8px !important;font-size:13px !important;color:#1f2937 !important;
    background:#fff !important;width:100% !important;
}
.ro-filters-panel select[multiple]{height:auto !important;padding:4px !important}
.ro-filters-panel select[multiple] option{padding:3px 6px;border-radius:2px;margin-bottom:1px}
.ro-filters-panel select[multiple] option:checked{background:#dbeafe;color:#2563eb}

/* ── Hint text ── */
.ro-filters-panel .filter-cell > div[style*="font-size:10px"]{
    font-size:10px;color:#9ca3af;margin-top:2px;
}

/* ── Buttons row ── */
.ro-filter-buttons{
    grid-column:1/-1 !important;display:flex !important;
    gap:8px !important;align-items:center !important;padding-top:4px !important;
}
.ro-btn-apply{
    height:34px;padding:0 22px;background:#2563eb;color:#fff;border:none;
    border-radius:4px;font-size:12px;font-weight:600;cursor:pointer;
}
.ro-btn-apply:hover{background:#1d4ed8}
.ro-btn-reset{
    height:34px;padding:0 14px;background:#fff;color:#6b7280;
    border:1px solid #d1d5db;border-radius:4px;font-size:12px;
    cursor:pointer;text-decoration:none;display:inline-flex;align-items:center;
}
.ro-btn-reset:hover{background:#f0f3f8}

/* ── Hide: original Show button, decorator cell, page-reset hidden input cell ── */
.ro-filters-panel .filter-cell > input.form-button,
.ro-filters-panel .filter-cell > input[type=submit]{display:none !important}
.ro-filters-panel .ro-decorator-cell{display:none !important}
.ro-filters-panel .filter-cell > input[type=hidden]:only-child{display:none}
/* Hide the cell that ONLY contains the submit button */
.ro-filters-panel .ro-submit-cell{display:none !important}
</style>

<script>
(function(){
    /* ── Run once DOM is ready (jQuery if available, else vanilla) ── */
    function run(){
        var marker = document.getElementById('roFilterPanelMarker');
        if(!marker) return;

        /* ── Find form.filter_form ── */
        var form = marker.closest('form.filter_form') || marker.closest('form');
        if(!form) return;
        var filtersDiv = form.querySelector('div.filters');
        if(!filtersDiv) filtersDiv = form; /* fallback */
        var formParent = form.parentNode;

        /* ── Hide decorator's own cell ── */
        var markerCell = marker.closest('span.filter-cell');
        if(markerCell) markerCell.classList.add('ro-decorator-cell');

        /* ── Hide the submit-button cell ── */
        var submitBtn = form.querySelector('input.form-button[type=submit], input[type=submit]');
        if(submitBtn){
            var submitCell = submitBtn.closest('span.filter-cell');
            if(submitCell) submitCell.classList.add('ro-submit-cell');
        }

        /* ── Count active filters ── */
        var activeCount = 0;
        var cells = filtersDiv.querySelectorAll('span.filter-cell');
        cells.forEach(function(cell){
            if(cell.classList.contains('ro-decorator-cell') ||
               cell.classList.contains('ro-submit-cell')) return;
            /* check hidden inputs with fn_ in name */
            cell.querySelectorAll('input[type=hidden]').forEach(function(inp){
                if(inp.name && inp.name.indexOf('fn_') !== -1
                   && inp.value && inp.value.trim() !== '') activeCount++;
            });
            /* check text inputs */
            cell.querySelectorAll('input[type=text]').forEach(function(inp){
                if(inp.name && inp.name.indexOf('fn_') !== -1
                   && inp.value && inp.value.trim() !== '') activeCount++;
            });
        });

        /* ── Build panel wrapper ── */
        var panel = document.createElement('div');
        panel.className = 'ro-filters-panel ro-open';
        panel.id = 'roFiltersPanel';

        /* Header */
        var header = document.createElement('div');
        header.className = 'ro-filters-header';
        var h = '<div class="ro-fh-title">'
            + '<svg class="ro-chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="6 9 12 15 18 9"/></svg>'
            + '${panelTitle!?js_string}';
        if(activeCount > 0){
            h += ' <span class="ro-active-count">' + activeCount + ' active</span>';
        }
        h += '</div>';
        <#if hierarchyHint?has_content>
        h += '<div style="font-size:11px;color:#9ca3af;">${hierarchyHint!?js_string}</div>';
        </#if>
        header.innerHTML = h;
        header.addEventListener('click', function(e){
            if(e.target.closest('.ro-filters-body')) return;
            panel.classList.toggle('ro-open');
        });

        /* Body */
        var body = document.createElement('div');
        body.className = 'ro-filters-body';

        /* Assemble: panel wraps the form */
        formParent.insertBefore(panel, form);
        body.appendChild(form);
        panel.appendChild(header);
        panel.appendChild(body);

        /* ── Add type badges to labels ── */
        cells.forEach(function(cell){
            if(cell.classList.contains('ro-decorator-cell') ||
               cell.classList.contains('ro-submit-cell')) return;
            var lbl = cell.querySelector('label.mobile_label');
            if(!lbl || !lbl.textContent.trim()) return;
            var badge = '';
            if(cell.querySelector('select[multiple]')){
                badge = '<span class="ro-type-badge ro-badge-multi">Multi-select</span>';
            } else if(cell.querySelector('input[type=date]')){
                badge = '<span class="ro-type-badge ro-badge-date">Date Picker</span>';
            } else if(cell.querySelector('input[type=text]')){
                badge = '<span class="ro-type-badge ro-badge-text">Text</span>';
            }
            if(badge) lbl.insertAdjacentHTML('beforeend', ' ' + badge);
        });

        /* ── Add Apply + Reset buttons ── */
        var btnDiv = document.createElement('span');
        btnDiv.className = 'filter-cell ro-filter-buttons';

        var applyBtn = document.createElement('button');
        applyBtn.type = 'submit';
        applyBtn.className = 'ro-btn-apply';
        applyBtn.textContent = 'Apply Filters';
        btnDiv.appendChild(applyBtn);

        var resetLink = document.createElement('a');
        resetLink.href = window.location.pathname;
        resetLink.className = 'ro-btn-reset';
        resetLink.textContent = 'Reset';
        btnDiv.appendChild(resetLink);

        filtersDiv.appendChild(btnDiv);
    }

    /* Schedule: jQuery ready if available, else DOMContentLoaded + fallback */
    if(typeof jQuery !== 'undefined'){
        jQuery(document).ready(run);
    } else if(document.readyState === 'loading'){
        document.addEventListener('DOMContentLoaded', run);
    } else {
        run();
    }
})();
</script>
