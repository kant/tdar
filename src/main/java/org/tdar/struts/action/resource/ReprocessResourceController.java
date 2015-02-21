package org.tdar.struts.action.resource;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Namespace;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tdar.URLConstants;
import org.tdar.core.bean.entity.permissions.GeneralPermissions;
import org.tdar.core.bean.resource.Dataset;
import org.tdar.core.bean.resource.InformationResource;
import org.tdar.core.bean.resource.datatable.DataTable;
import org.tdar.core.bean.resource.datatable.DataTableColumn;
import org.tdar.core.dao.external.auth.InternalTdarRights;
import org.tdar.core.service.ErrorTransferObject;
import org.tdar.core.service.resource.DatasetService;
import org.tdar.core.service.resource.InformationResourceService;
import org.tdar.core.service.resource.ResourceService;
import org.tdar.struts.action.AbstractPersistableController.RequestType;
import org.tdar.struts.action.AuthenticationAware;
import org.tdar.struts.action.PersistableLoadingAction;
import org.tdar.struts.action.TdarActionException;

import com.opensymphony.xwork2.Preparable;

@Component
@Scope("prototype")
@ParentPackage("secured")
@Namespace("/resource")
public class ReprocessResourceController extends AuthenticationAware.Base implements Preparable, PersistableLoadingAction<InformationResource> {

    private static final long serialVersionUID = 7557491462701114284L;
    public static final String RETRANSLATE = "retranslate";
    public static final String REPROCESS = "reprocess";
    public static final String REIMPORT = "reimport";

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private InformationResourceService informationResourceService;

    private InformationResource resource;
    private Long id;

    @Action(value = REPROCESS, results = { @Result(name = SUCCESS, type = REDIRECT, location = URLConstants.VIEW_RESOURCE_ID_AS_ID) })
    public String reprocess() throws TdarActionException {
        getLogger().info("reprocessing");

        try {
            ErrorTransferObject errors = informationResourceService.reprocessInformationResourceFiles(getResource());
            processErrorObject(errors);
            if (getResource() instanceof Dataset) {
                datasetService.remapAllColumnsAsync(getResource().getId(), getResource().getProject().getId());
            }
        } catch (Exception e) {
            // consider removing the "sorry we were unable to ... just showing error message"
            // addActionErrorWithException(null, e);
            addActionErrorWithException(getText("abstractResourceController.we_were_unable_to_process_the_uploaded_content"), e);
        }
        if (hasActionErrors()) {
            return ERROR;
        }
        return SUCCESS;
    }

    @Action(value = REIMPORT, results = { @Result(name = SUCCESS, type = REDIRECT, location = URLConstants.VIEW_RESOURCE_ID_AS_ID) })
    public String reimport() throws TdarActionException {
        if (getResource() instanceof Dataset) {
            try {
                Dataset dataset = (Dataset) getResource();
                Map<Long, String> colkeys = new HashMap<>();
                Map<Long, String> tabkeys = new HashMap<>();
                extractKeys(dataset, colkeys, tabkeys);
                // note this ignores the quota changes -- it's on us
                datasetService.reprocess(dataset);
                datasetService.retranslate(dataset);
                Map<Long, String> colkeys_ = new HashMap<>();
                Map<Long, String> tabkeys_ = new HashMap<>();
                extractKeys(dataset, colkeys_, tabkeys_);
                logReimportChanges(dataset, colkeys, tabkeys, colkeys_, tabkeys_);
                datasetService.remapAllColumnsAsync(dataset.getId(), getResource().getProject().getId());
            } catch (Exception e) {
                getLogger().error("ex: {}", e);
                throw e;
            }
        } else {
            addActionError(getText("reprocessResourceController.dataset_required"));
            return ERROR;
        }
        return SUCCESS;
    }

    private void logReimportChanges(Dataset dataset, Map<Long, String> colkeys, Map<Long, String> tabkeys, Map<Long, String> colkeys_, Map<Long, String> tabkeys_) {
        Collection<Long> disjunctTab = CollectionUtils.disjunction(tabkeys.keySet(), tabkeys_.keySet());
        Collection<Long> disjunctCol = CollectionUtils.disjunction(colkeys.keySet(), colkeys_.keySet());
        if (CollectionUtils.isNotEmpty(disjunctTab) || CollectionUtils.isNotEmpty(disjunctCol)) {
            getLogger().warn("|======= TABLES/COLS CHANGED FOR {} ===========|", dataset.getId());
            getLogger().warn("| {}", dataset.getTitle());
            if (CollectionUtils.isNotEmpty(disjunctTab)) {
                getLogger().warn("| COLUMNS:");
                for (Long dis : disjunctTab) {
                    getLogger().warn("| id: {} [{}{}]", tabkeys.get(dis), tabkeys_.get(dis));
                }
            }
            if (CollectionUtils.isNotEmpty(disjunctCol)) {
                getLogger().warn("| TABLES:");
                for (Long dis : disjunctCol) {
                    getLogger().warn("| id: {} [{}{}]", colkeys.get(dis), colkeys_.get(dis));
                }
            }
            getLogger().warn("|==============================================|");
        }
    }

    private void extractKeys(Dataset dataset, Map<Long, String> dtkeys, Map<Long, String> dskeys) {
        for (DataTable table : dataset.getDataTables()) {
            dskeys.put(table.getId(), table.getName() + " | " + table.getDisplayName());
            for (DataTableColumn col : table.getDataTableColumns()) {
                dtkeys.put(col.getId(), col.getName() + " | " + col.getDisplayName());
            }
        }
    }

    /**
     * Retranslates the given dataset.
     * XXX: does this need a WritableSession?
     */
    @Action(value = RETRANSLATE, results = { @Result(name = SUCCESS, type = REDIRECT, location = URLConstants.VIEW_RESOURCE_ID_AS_ID) })
    public String retranslate() throws TdarActionException {
        // checkValidRequest(RequestType.MODIFY_EXISTING, this, InternalTdarRights.EDIT_ANYTHING);
        if (getResource() instanceof Dataset) {
            Dataset dataset = (Dataset) getResource();
            datasetService.retranslate(dataset);
            datasetService.createTranslatedFile(dataset);
        } else {
            addActionError(getText("reprocessResourceController.dataset_required"));
            return ERROR;
        }
        return SUCCESS;
    }

    @Override
    public void prepare() throws TdarActionException {
        prepareAndLoad(this, RequestType.EDIT);
        checkValidRequest(this);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public boolean authorize() throws TdarActionException {
        return getAuthorizationService().canEditResource(getAuthenticatedUser(), getResource(), GeneralPermissions.MODIFY_METADATA);
    }

    @Override
    public InformationResource getPersistable() {
        return getResource();
    }

    @Override
    public Class<InformationResource> getPersistableClass() {
        return InformationResource.class;
    }

    @Override
    public void setPersistable(InformationResource persistable) {
        this.setResource(persistable);
    }

    @Override
    public InternalTdarRights getAdminRights() {
        return InternalTdarRights.EDIT_ANYTHING;
    }

    public InformationResource getResource() {
        return resource;
    }

    public void setResource(InformationResource resource) {
        this.resource = resource;
    }
}
