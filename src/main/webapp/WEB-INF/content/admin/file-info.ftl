<#escape _untrusted as _untrusted?html >
<#import "/WEB-INF/macros/resource/common.ftl" as common>
<#import "admin-common.ftl" as admin>
<title>Admin Pages - file info</title>

<h1>Files with processing Errors and Warnings</h1>
<table class="table tableFormat">
 <thead>
 <th>file ID</th>
 <th>Resource Id</th>
 <th>created</th>
 <th>type</th>
 <th>number of parts</th>
 <th>status</th>
 <th>error</th>
 <th>part of composite</th>
 <th>latest version</th>
 <th>restrictions</th>
 </thead>
 <tbody>
<#list files as file>
<tr>
<td>${file.id?c}</td>
<td><a href="<@s.url value="/${file.informationResource.resourceType.urlNamespace}/${file.informationResource.id?c}"/>">${file.informationResource.id?c}</td>
<td>${file.fileCreatedDate!""}</td>
<td>${file.informationResourceFileType}</td>
<td>${file.numberOfParts!1}</td>
<td>${file.status}</td>
<td>
<@common.truncate file.errorMessage!"" 80/> <span onClick="$(this).parent().children('.hidden').removeClass('hidden');$(this).hide();">[show]</span>
<span class="hidden">
${file.errorMessage!""}
</span>
</td>
<td>${file.partOfComposite?string}</td>
<td>${file.latestVersion!0}</td>
<td>${file.restriction} <#if file.embargoed>(${file.dateMadePublic!""})</#if></td>
</tr>
</#list>
</tbody>
</table>



<div class="glide">
    <h3># of Files by extension</h3>
    <@common.generatePieJson extensionStats "extensionStats" />
    <@common.pieChart  data="extensionStats" searchKey="extensions" graphHeight=600 graphWidth=400 />
</div>


<div class="glide">
    <h3>Uploaded File Usage by extension</h3>
    <table class="tableFormat table">
     <tr>
      <th>Extension</th>
      <th>Average</th>
      <th>Min</th>
      <th>Max</th>
     </tr>
     <#list fileUploadedAverageStats?keys?sort as stat>
     <tr>
       <td><b>${stat}</b></td>
       <td><@common.convertFileSize fileUploadedAverageStats.get(stat)[0] /></td>
       <td><@common.convertFileSize fileUploadedAverageStats.get(stat)[1] /></td>
       <td><@common.convertFileSize fileUploadedAverageStats.get(stat)[2] /></td>
     </tr>
     </#list>
     </table>
</div>


<div class="glide">
    <h3>All File Usage by extension</h3>
    <table class="tableFormat table">
     <tr>
      <th>Extension</th>
      <th>Average</th>
      <th>Min</th>
      <th>Max</th>
     </tr>
     <#list fileAverageStats?keys?sort as stat>
     <tr>
       <td><b>${stat}</b></td>
       <td><@common.convertFileSize fileAverageStats.get(stat)[0] /></td>
       <td><@common.convertFileSize fileAverageStats.get(stat)[1] /></td>
       <td><@common.convertFileSize fileAverageStats.get(stat)[2] /></td>
     </tr>
     </#list>
     </table>
</div>

<div class="glide">
    <h3>Total File Space Usage by extension</h3>
    <table class="tableFormat table">
     <tr>
      <th>Extension</th>
      <th>size</th>
     </tr>
     <#list fileStats?keys?sort as stat>
     <tr>
       <td><b>${stat}</b></td>
       <td>${fileStats.get(stat)[0]}</td>
     </tr>
     </#list>
     </table>
</div>

<script>
    $(function(){
        TDAR.datatable.extendSorting();
	    $(".table").dataTable({"bFilter": false, "bInfo": false, "bPaginate":false})
    });
</script>
</#escape>
