<#escape _untrusted as _untrusted?html>
<#import "/WEB-INF/macros/resource/view-macros.ftl" as view>
<#import "/WEB-INF/macros/resource/navigation-macros.ftl" as nav>

<#macro toolbarAdditions>
	<#if editable>
	<!-- disabled if coding sheet has errors, ontology is not mapped -->
		<#assign disabled = !okToMapOntology />
		<@nav.makeLink "coding-sheet" "mapping" "map ontology" "mapping"   current true disabled "mappingLink"/>
	</#if>
</#macro>

<#macro afterBasicInfo>
	<@view.codingRules>
		<#if codingSheet.defaultOntology?has_content>
			<p><b>Ontology:</b> <a href='<@s.url value="/${codingSheet.defaultOntology.absoluteUrl}"/>'>${codingSheet.defaultOntology.title}</a></p>
		</#if>
		<@view.categoryVariables />
	</@view.codingRules>
</#macro>

<#macro localJavascript>
    <@view.datatableChildJavascript />
</#macro>
</#escape>