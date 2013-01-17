<#import "/WEB-INF/macros/resource/view-macros.ftl" as view>
<@view.htmlHeader resourceType="coding sheet">
    <meta name="lastModifiedDate" content="$Date$"/>
    <@view.googleScholar />
    <@view.datatableChildJavascript />
</@view.htmlHeader>

<@view.toolbar resource.urlNamespace "view">
<#if editable>
<#assign disabled = !resource.defaultOntology?? />
<@view.makeLink "coding-sheet" "mapping" "map ontology" "mapping"   current true disabled />
</#if>
</@view.toolbar>

<@view.datatableChild />

<@view.projectAssociation resourceType="coding sheet" />

<@view.infoResourceBasicInformation />

<@view.codingRules>
<#if codingSheet.defaultOntology?has_content>
<p><b>Ontology:</b> <a href='<@s.url value="/${codingSheet.defaultOntology.absoluteUrl}"/>'>${codingSheet.defaultOntology.title}</a></p>
</#if>
<@view.categoryVariables />
</@view.codingRules>

<@view.sharedViewComponents resource />
