<select id="${name!?replace('.', '_')?replace('-', '_')}_sel" multiple size="${size!}">
<#list options as opt>
    <option value="${opt.value!?html}"<#if opt.selected?has_content> selected</#if>>${opt.label!?html}</option>
</#list>
</select>
<input type="hidden" id="${name!?replace('.', '_')?replace('-', '_')}" name="${name!}" value="${value!?html}"/>
<#if hint?has_content>
<div style="font-size:10px;color:#9ca3af;margin-top:2px;">${hint!}</div>
</#if>
<script>
(function(){
    var selEl=document.getElementById('${name!?replace('.', '_')?replace('-', '_')}_sel');
    var hidEl=document.getElementById('${name!?replace('.', '_')?replace('-', '_')}');
    if(!selEl||!hidEl) return;
    function sync(){
        var v=[];
        for(var i=0;i<selEl.options.length;i++){
            if(selEl.options[i].selected) v.push(selEl.options[i].value);
        }
        hidEl.value=v.join(';');
    }
    selEl.addEventListener('change',sync);
    var f=selEl.closest('form');
    if(f) f.addEventListener('submit',function(){sync();});
})();
</script>
