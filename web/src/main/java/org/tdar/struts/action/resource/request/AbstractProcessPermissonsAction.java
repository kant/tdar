package org.tdar.struts.action.resource.request;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.entity.permissions.Permissions;
import org.tdar.core.bean.notification.EmailType;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.dao.external.auth.InternalTdarRights;
import org.tdar.core.service.GenericService;
import org.tdar.core.service.external.AuthorizationService;
import org.tdar.struts.action.AbstractAuthenticatableAction;
import org.tdar.struts.action.AbstractPersistableController.RequestType;
import org.tdar.struts_base.action.PersistableLoadingAction;
import org.tdar.struts_base.action.TdarActionException;

import com.opensymphony.xwork2.Preparable;

/**
 * Abstract class for procesisng the permissions request (for users who are
 * processing those requests, as opposed to making them)
 * 
 * @author abrin
 *
 */
public abstract class AbstractProcessPermissonsAction extends AbstractAuthenticatableAction
        implements Preparable, PersistableLoadingAction<Resource> {

    private static final long serialVersionUID = 6775247968159166454L;
    private Long requestorId;
    private Long resourceId;
    private TdarUser requestor;
    private Permissions permission;
    private Resource resource;
    private EmailType type;

    private List<Permissions> availablePermissions = Permissions.resourcePermissions();
    @Autowired
    private transient AuthorizationService authorizationService;
    @Autowired
    private transient GenericService genericService;

    @Override
    public void prepare() throws Exception {
        // make sure we have a valid reqquestor
        requestor = genericService.find(TdarUser.class, requestorId);
        if (requestor == null) {
            addActionError("requestPermissionsController.require_user");
        }
        // setup persistable
        prepareAndLoad(this, RequestType.EDIT);
    }

    public Long getRequestorId() {
        return requestorId;
    }

    public void setRequestorId(Long requestorId) {
        this.requestorId = requestorId;
    }

    public Permissions getPermission() {
        return permission;
    }

    public void setPermission(Permissions permission) {
        this.permission = permission;
    }

    public TdarUser getRequestor() {
        return requestor;
    }

    public void setRequestor(TdarUser requestor) {
        this.requestor = requestor;
    }

    @Override
    public boolean authorize() throws TdarActionException {
        // make sure user has full permissions
        return authorizationService.canEditResource(getAuthenticatedUser(), getResource(),
                Permissions.MODIFY_RECORD);
    }

    @Override
    public Resource getPersistable() {
        return getResource();
    }

    @Override
    public void setPersistable(Resource persistable) {
        this.resource = persistable;
    }

    @Override
    public Class<Resource> getPersistableClass() {
        return Resource.class;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public List<Permissions> getAvailablePermissions() {
        return availablePermissions;
    }

    public void setAvailablePermissions(List<Permissions> availablePermissions) {
        this.availablePermissions = availablePermissions;
    }

    @Override
    public Long getId() {
        return resourceId;
    }

    @Override
    public InternalTdarRights getAdminRights() {
        return InternalTdarRights.EDIT_ANYTHING;
    }

    public EmailType getType() {
        return type;
    }

    public void setType(EmailType type) {
        this.type = type;
    }
}
