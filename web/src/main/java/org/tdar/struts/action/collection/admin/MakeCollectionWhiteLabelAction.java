package org.tdar.struts.action.collection.admin;

import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Namespace;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tdar.core.bean.TdarGroup;
import org.tdar.core.bean.collection.ResourceCollection;
import org.tdar.core.bean.collection.WhiteLabelCollection;
import org.tdar.core.service.ResourceCollectionService;
import org.tdar.struts.action.AuthenticationAware.Base;
import org.tdar.struts.interceptor.annotation.PostOnly;
import org.tdar.struts.interceptor.annotation.RequiresTdarUserGroup;
import org.tdar.struts.interceptor.annotation.WriteableSession;

import com.opensymphony.xwork2.Preparable;

@Component
@Scope("prototype")
@ParentPackage("secured")
@RequiresTdarUserGroup(TdarGroup.TDAR_EDITOR)
@Namespace("/collection/admin")
public class MakeCollectionWhiteLabelAction extends Base implements Preparable {

    private static final long serialVersionUID = 4671830242931274023L;

    @Autowired
    private ResourceCollectionService resourceCollectionService;
    
    private Long id;
    private ResourceCollection collection;

    @Override
    public void prepare() throws Exception {
        collection = resourceCollectionService.find(id);
    }

    @Override
    @PostOnly
    @WriteableSession
    @Action(value = "makeWhitelabel/{id}", results={
            @Result(name = SUCCESS, type = TDAR_REDIRECT, location = "${collection.detailUrl}"),
    })
    public String execute() throws Exception {
        if (collection instanceof WhiteLabelCollection) {
            return SUCCESS;
        }
        resourceCollectionService.makeCollectionWhiteLabel(collection);
        return SUCCESS;
    }
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

}
