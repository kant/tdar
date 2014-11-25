package org.tdar.struts.action.download;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Actions;
import org.apache.struts2.convention.annotation.Namespace;
import org.apache.struts2.convention.annotation.Namespaces;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tdar.core.bean.Persistable;
import org.tdar.core.bean.resource.VersionType;
import org.tdar.core.configuration.TdarConfiguration;
import org.tdar.core.service.download.DownloadResult;
import org.tdar.core.service.download.DownloadService;
import org.tdar.core.service.external.AuthorizationService;
import org.tdar.struts.interceptor.annotation.HttpsOnly;

import com.opensymphony.xwork2.Preparable;

@ParentPackage("default")
@Namespaces(value = {
        @Namespace("/filestore"),
        @Namespace("/files") })
@Component
@Scope("prototype")
public class UnauthenticatedDownloadController extends AbstractDownloadController implements Preparable {

    private static final long serialVersionUID = 3682702108165100228L;
    @Autowired
    private transient DownloadService downloadService;
    @Autowired
    private transient AuthorizationService authorizationService;

    @Action(value = "download",
            results = {
                    @Result(name = SUCCESS, type = "redirect", location = DOWNLOAD_SINGLE_LANDING),
                    @Result(name = DOWNLOAD_ALL, type = "redirect", location = "/filestore/downloadAllAsZip?informationResourceId=${informationResourceId}"),
                    @Result(name = INPUT, type = "httpheader", params = { "error", "400", "errrorMessage", "no file specified" }),
                    @Result(name = LOGIN, type = FREEMARKER, location = "download-unauthenticated.ftl") })
    @HttpsOnly
    public String download() {
        if (!isAuthenticated()) {
            if (!isAuthenticated() && StringUtils.isNotBlank(TdarConfiguration.getInstance().getRecaptchaPrivateKey())) {
                getH().generateRecapcha(getRecaptchaService());
            }
            return LOGIN;
        }

        if (Persistable.Base.isNotNullOrTransient(getInformationResourceFileVersion())) {
            return SUCCESS;
        } else {
            return DOWNLOAD_ALL;
        }
    }

    /*
     * I believe we'll need something like this for our contract with SRI
     * 
     * public String downloadCustom() {
     * HttpServletRequest request = ServletActionContext.getRequest();
     * String referrer = request.getHeader("referer");
     * if (downloadService.canDownloadUnauthenticated(referrer, getInformationResourceFileVersion())) {
     * downloadService.handleActualDownload(null, this, null, getInformationResourceFileVersion());
     * }
     * return INPUT;
     * }
     */

    @Actions(value = {
            @Action(value = GET),
            @Action(value = "thumbnail/{informationResourceFileVersionId}"),
            @Action(value = "sm/{informationResourceFileVersionId}"),
            @Action(value = "img/sm/{informationResourceFileVersionId}"),
            @Action(value = "{informationResourceFileVersionId}/thumbnail"),
            @Action(value = "{informationResourceFileVersionId}/sm")
    })
    public String thumbnail() {
        getSessionData().clearPassthroughParameters();
        if (Persistable.Base.isNullOrTransient(getInformationResourceFileVersion())) {
            getLogger().warn("thumbnail request: no informationResourceFiles associated with this id [{}]", getInformationResourceFileVersionId());
            return ERROR;
        }

        // image must be thumbnail
        if (getInformationResourceFileVersion().getFileVersionType() != VersionType.WEB_SMALL) {
            getLogger().warn("thumbail request: requested version exists but is not a thumbnail: {}", getInformationResourceFileVersionId());
            return ERROR;
        }

        if (!authorizationService.canDownload(getInformationResourceFileVersion(), getAuthenticatedUser())) {
            getLogger().warn("thumbail request: resource is confidential/embargoed: {}", getInformationResourceFileVersionId());
            return FORBIDDEN;
        }

        setDownloadTransferObject(downloadService.validateFilterAndSetupDownload(getAuthenticatedUser(), getInformationResourceFileVersion(), null,
                isCoverPageIncluded(), this));
        if (getDownloadTransferObject().getResult() != DownloadResult.SUCCESS) {
            return getDownloadTransferObject().getResult().name().toLowerCase();
        }
        return getDownloadTransferObject().getResult().name().toLowerCase();
    }

    @Override
    public void prepare() {
        super.prepare();
    }
}