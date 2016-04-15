package org.tdar.struts.action.resource.request;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.InterceptorRef;
import org.apache.struts2.convention.annotation.Namespace;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tdar.core.bean.notification.Email;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.configuration.TdarConfiguration;
import org.tdar.core.service.ResourceCollectionService;
import org.tdar.core.service.external.EmailService;
import org.tdar.struts.action.PersistableLoadingAction;
import org.tdar.struts.action.TdarActionException;
import org.tdar.struts.action.TdarActionSupport;
import org.tdar.struts.interceptor.annotation.HttpsOnly;
import org.tdar.struts.interceptor.annotation.PostOnly;
import org.tdar.struts.interceptor.annotation.WriteableSession;
import org.tdar.utils.EmailMessageType;

import com.opensymphony.xwork2.Preparable;

@ParentPackage("secured")
@Namespace("/resource/access")
@Component
@Scope("prototype")
public class ProcessPermissonsAction extends AbstractProcessPermissonsAction implements Preparable, PersistableLoadingAction<Resource> {

    private static final long serialVersionUID = 4719778524052804432L;
    private boolean reject = false;
    private EmailMessageType type;
    private String comment;
    @Autowired
    private transient ResourceCollectionService resourceCollectionService;
    @Autowired
    private transient EmailService emailService;

    @Override
    public void prepare() throws Exception {
        super.prepare();
    }

    @Override
    public void validate() {
        super.validate();
        if (getPermission() == null) {
            addActionError("requestPermissionsController.specify_permission");
        }

        if (reject && StringUtils.isBlank(comment)) {
            addActionError("requestPermissionsController.comment_required");
        }
    }

    @Action(value = "process-access-request",
            interceptorRefs = { @InterceptorRef("editAuthenticatedStack") },
            results = {
                    @Result(name = SUCCESS, type = TdarActionSupport.REDIRECT, location = "/${resource.urlNamespace}/${resource.id}/${resource.slug}"),
                    @Result(name = ERROR, type = TdarActionSupport.FREEMARKERHTTP, location = "/WEB-INF/content/errors/error.ftl",
                            params = { "status", "500" }),
                    @Result(name = INPUT, type = TdarActionSupport.FREEMARKERHTTP, location = "/WEB-INF/content/errors/error.ftl",
                            params = { "status", "500" })
            })
    @PostOnly
    @WriteableSession
    @HttpsOnly
    public String processAccessRequest() throws TdarActionException {
        Email email = new Email();
        email.setSubject(TdarConfiguration.getInstance().getSiteAcronym() + "- " + getResource().getTitle());
        email.setTo(getRequestor().getEmail());
        Map<String, Object> map = new HashMap<>();
        map.put("requestor", getRequestor());
        map.put("resource", getResource());
        map.put("authorizedUser", getAuthenticatedUser());
        if (StringUtils.isNotBlank(comment)) {
            map.put("message", getComment());
        }
        String template = "email-form/access-request-granted.ftl";
        if (reject) {
            template = "email-form/access-request-rejected.ftl";
        } else {
            switch (type) {
                case SAA:
                    template = "email-form/saa-accept.ftl";
                    break;
                default:
                    break;
            }
            resourceCollectionService.addUserToInternalCollection(getResource(), getRequestor(), getPermission());
        }
        emailService.queueWithFreemarkerTemplate(template, map, email);
        email.setUserGenerated(false);
        emailService.send(email);

        return SUCCESS;
    }

    public boolean isReject() {
        return reject;
    }

    public void setReject(boolean reject) {
        this.reject = reject;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public EmailMessageType getType() {
        return type;
    }

    public void setType(EmailMessageType type) {
        this.type = type;
    }

}
