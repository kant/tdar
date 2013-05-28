<#if dataset??>
    <h1 class="view-page-title">Row number ${rowId} of ${dataset.name}</h1>
    <#-- At the moment no sidebar: we await further refinement of the users needs. -->
    <#if authenticatedUser??>
      <#if dataTableRowAsMap??>
        <p><strong>Dataset:</strong> ${dataset.name}</p>
        <p><strong>Description:</strong> ${dataset.description}</p>
        <table class="table table-striped">
            <thead>
              <tr>
                <th>Field</th><th>Value</th>  
              </tr>
            </thead>
            <tbody>
        <#list dataTableRowAsMap.entrySet() as entry>
            <#if entry.key.visible>
              <tr>
                <td>${entry.key.displayName}</td><td>${entry.value}</td>
              </tr>
            </#if>
        </#list>
            </tbody>
        </table>
      </#if>
    </#if>
<#else>
    <#-- text of following is used for testing DatasetWebITCase#viewRowPageRender-->
    <p class="alert">The view dataset row feature does not appear to be enabled.</p>
</#if>