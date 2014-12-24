package org.tdar.struts.action.api.integration;

import java.io.IOException;
import java.util.List;

import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Namespace;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tdar.core.bean.resource.Ontology;
import org.tdar.core.bean.resource.OntologyNode;
import org.tdar.core.dao.integration.IntegrationColumnProxy;
import org.tdar.core.service.DataIntegrationService;
import org.tdar.core.service.integration.IntegrationColumn;
import org.tdar.utils.json.JsonNodeParticipationFilter;

@Namespace("/api/integration")
@ParentPackage("secured")
@Component
@Scope("prototype")
public class IntegrationColumnDetailsAction extends AbstractIntegrationAction {

    private static final long serialVersionUID = 6114433438074971363L;

    private IntegrationColumn integrationColumn;

    @Autowired
    private transient DataIntegrationService integrationService;

    @Action(value = "integration-column-details")
    public String integrationColumnDetails() throws IOException {
        integrationService.getColumnDetails(getIntegrationColumn());
        Ontology sharedOntology = getIntegrationColumn().getSharedOntology();
        IntegrationColumnProxy proxy = new IntegrationColumnProxy();
        proxy.setSharedOntology(sharedOntology);
        List<OntologyNode> flattenedOntologyNodeList = getIntegrationColumn().getFlattenedOntologyNodeList();
        if (flattenedOntologyNodeList != null) {
            proxy.getFlattenedNodes().addAll(flattenedOntologyNodeList);
        }

        setJsonObject(proxy, JsonNodeParticipationFilter.class);
        return SUCCESS;
    }

    public IntegrationColumn getIntegrationColumn() {
        return integrationColumn;
    }

    public void setIntegrationColumn(IntegrationColumn integrationColumn) {
        this.integrationColumn = integrationColumn;
    }

}
