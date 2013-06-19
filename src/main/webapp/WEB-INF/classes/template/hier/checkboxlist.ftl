<#if parameters.list??>
    <#include "/${parameters.templateDir}/simple/checkboxlist.ftl" />
<#else>
<#assign itemCount = 0/>
<#assign targetList = [] />
<#if stack.findValue(parameters.name)??>
    <#assign targetList = stack.findValue(parameters.name) />
</#if>
<#if parameters.keywordList??>
    <#assign root = stack.findValue(parameters.keywordList) />    
    <@listKeywords root />
<#else>
  &nbsp;
</#if>
<input type="hidden" id="__multiselect_${parameters.id?html}" name="__multiselect_${parameters.name?html}" value=""<#rt/>
<#if parameters.disabled?default(false)>
 disabled="disabled"<#rt/>
</#if>
 /> 
</#if>

<#macro listKeywords node>
    <#local ulid = "${parameters.name?html}_Treeview" />
    <#if parameters.id??>
    <#local ulid = "${parameters.id}" />
    </#if>
    
     <ul<#if itemCount < 1> id="${ulid}" class="tdar-treeview"</#if> >
    <#list node.children?keys as kid>
         <#assign itemCount = itemCount + 1/>
         <#assign kidnode = node.children.get(kid) />
         <#assign itemLabel = kidnode.keyword.label />
        <#assign itemKey = kidnode.keyword.id />
        <#assign itemKeyStr = itemKey.toString() />
        <#assign itemTitle= ""/>    
        <#if kidnode.keyword.definition??>
            <#assign itemTitle=kidnode.keyword.definition?html />
        </#if>
        <li>
        <#if kidnode.keyword.selectable>
<input type="checkbox" name="${parameters.name?html}" value="${itemKeyStr?html}" id="${parameters.name?html}-${itemCount}"<#rt/>
        <#if targetList?seq_contains(itemKey) || targetList?seq_contains(itemKey?string) >
 checked="checked"<#rt/>
        </#if>
        <#if parameters.disabled?default(false)>
 disabled="disabled"<#rt/>
        </#if>
        <#if itemTitle??>
 title="${itemTitle}"<#rt/>
        <#elseif parameters.title??>
 title="${parameters.title?html}"<#rt/>
        </#if>
        <#include "/${parameters.templateDir}/simple/scripting-events.ftl" />
        <#include "/${parameters.templateDir}/simple/common-attributes.ftl" />
/>
<label for="${parameters.name?html}-${itemCount}" class="checkboxLabel" <#if itemTitle??>title="${itemTitle}"</#if> >${itemLabel?html}</label>
        <#else>
        ${itemLabel?html}<#rt/>
        </#if>
        <#if !kidnode.children.empty>
            <@listKeywords kidnode />
        </#if>
        </li>
    </#list>
    </ul>
</#macro>
