package org.tdar.struts.action.collection.share;

import org.apache.struts2.convention.annotation.*;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tdar.core.bean.collection.SharedCollection;
import org.tdar.struts_base.action.TdarActionException;

@ParentPackage("secured")
@Namespace("/collection/share")
@Component
@Scope("prototype")
public class CollectionAdhocShareEditAction extends AbstractCollectionAdhocShareAction {

    @Action(value="edit", results={
            @Result(name="success", location="edit.ftl"),
            @Result(name="input", type="redirect", location="/dashboard")
    })
    public  String execute() {
        return "success";
    }

}
