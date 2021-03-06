<#import "email-macro.ftl" as mail /> 

<@mail.content>
     <table>
        <tr>
        <td>
        <p style="float:left;">
            Dear ${user.firstName},<br />
            We're so glad that you've entrusted your archaeological to tDAR. 
            We'd love to give you an update on what's new, and what's been most popular.<br />
            <br />
    
            <p><b>tDAR update 07/31/2017:</b> This release focuses on three major areas of the 
            repository: (1) visualization, (2) maps and spatial data, and (3) modularization 
            and infrastructure work. 
            In addition, the development team has improved performance and 
            reliability as well as making a series of smaller enhancements. <a href="https://www.tdar.org/news/2017/07/tdar-software-update-prehistoric/">[read more]</a></p>
        </p>
        </td>
        <td>
        <p style="float:right;width:300px;">
            <img src="cid:resources.png" />
        </p>
       </td>
       </tr>  
    </table>
    
    <#list resources>
    <div>
        <span style="font-weight:bold;font-size:14px;text-decoration:underline">
            Your Most Popular Resources
        </span>
        <ul>
        <#items as resource>
            <li> <a href="${resource.detailUrl}">${resource.title}</a></li>
            </#items>
        </ul>
     </div>
     </#list>
        
    <img src="cid:totalviews.png" />   
    <img src="cid:totaldownloads.png" />   
    
    
    <div>
        <span style="font-weight:bold;font-size:14px;text-decoration:underline">
            Your Account Balance:
        </span>
        You currently have space for ${availableFiles} files or up to ${availableSpace} MB of space available in tDAR.  
        <a href="http://core.tdar.org/dashboard/billing">Check your balance now</a> or, 
        <a href="https://core.tdar.org/resource/add">upload something now</a>.
    </div>        
</@mail.content>