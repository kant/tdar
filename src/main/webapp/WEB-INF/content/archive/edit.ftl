<#escape _untrusted as _untrusted?html>
	<#import "/WEB-INF/macros/resource/common.ftl" as common>
	<#import "/${themeDir}/local-helptext.ftl" as  helptext>
	<#global multipleUpload=multipleFileUploadEnabled!false />
    <#global itemPrefix="archive"/>
    <#global inheritanceEnabled=true />
    <#macro afterUpload>
        <h2>Import Action</h2>
        <#if !resource.isImportDone()>
            <@helptext.unpackArchiveTip />
            <div class="" id="unpackArchiveSection" data-tiplabel="Unpack the archive?" data-tooltipcontent="#divUnpackArchiveTip" >
                <@common.boolfield label='Unpack the uploaded archive (.bz2) into the repository?' name="resource.doImportContent" id="do_import_content" value=resource.doImportContent!false  />
            </div>
        <#else>
            <p>This archive <strong>has already been</strong> unpacked into the repository.
            </p>
         </#if>
    </#macro>
</#escape>