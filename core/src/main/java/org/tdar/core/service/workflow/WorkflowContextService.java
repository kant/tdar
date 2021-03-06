package org.tdar.core.service.workflow;

import org.tdar.core.bean.resource.file.InformationResourceFileVersion;
import org.tdar.fileprocessing.workflows.Workflow;
import org.tdar.fileprocessing.workflows.WorkflowContext;

public interface WorkflowContextService {

    /**
     * This method takes a workflow context once it's been generated and persists it back into tDAR. It will remove all existing derivatives, then
     * rehydrate all of the objects associated with the context, and then save them back into the database
     * 
     * @param ctx
     */
    void processContext(WorkflowContext ctx);

    /**
     * given any InformationResourceFileVersion (for an uploaded file) this will create a workflow context
     */
    WorkflowContext initializeWorkflowContext(Workflow w, InformationResourceFileVersion... versions);

}