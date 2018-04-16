package org.tdar.struts.action.api.files;

import java.io.IOException;

import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.InterceptorRef;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tdar.struts_base.interceptor.annotation.PostOnly;

@Component
@Scope("prototype")
@ParentPackage("secured")
public class UploadAction extends AbstractUploadController {

    private static final long serialVersionUID = 4637923657007222358L;

    @Action(value = "upload",
            interceptorRefs = { @InterceptorRef("editAuthenticatedStack") },
            results = { @Result(name = SUCCESS, type = JSONRESULT, params = { "stream", "jsonInputStream" }),
                    @Result(name = ERROR, type = JSONRESULT, params = { "stream", "jsonInputStream", "statusCode", "400" })
            })
    @PostOnly
    public String upload() throws IOException {
        return super.upload();
    }

}