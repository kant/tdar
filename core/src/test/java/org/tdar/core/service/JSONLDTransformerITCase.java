package org.tdar.core.service;

import java.io.IOException;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.tdar.core.bean.AbstractIntegrationTestCase;
import org.tdar.core.bean.RelationType;
import org.tdar.core.bean.entity.Creator;
import org.tdar.core.bean.keyword.ExternalKeywordMapping;
import org.tdar.core.bean.keyword.GeographicKeyword;
import org.tdar.core.bean.resource.Resource;
import org.tdar.transform.SchemaOrgMetadataTransformer;

public class JSONLDTransformerITCase extends AbstractIntegrationTestCase {

    protected transient Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    SerializationService serializationService;
    @Autowired
    GenericService genericService;

    @Test
    public void testResources() throws IOException, ClassNotFoundException {
        SchemaOrgMetadataTransformer transformer = new SchemaOrgMetadataTransformer();

        for (Resource r : genericService.findAll(Resource.class)) {
            logger.debug("//  {} - {}", r.getId(), r.getResourceType());
            logger.debug(transformer.convert(serializationService, r));
        }
    }

    @Test
    public void testJsonLDExtensions() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        SchemaOrgMetadataTransformer transformer = new SchemaOrgMetadataTransformer();
        Resource r = generateDocumentWithUser();
        GeographicKeyword gk = new GeographicKeyword();
        gk.setLabel("Petra");
        ExternalKeywordMapping map = new ExternalKeywordMapping();
        map.setRelation("http://www.petra.com");
        map.setRelationType(RelationType.DCTERMS_RELATION);
        gk.getAssertions().add(map);
        r.getGeographicKeywords().add(gk);
        genericService.saveOrUpdate(map);
        genericService.saveOrUpdate(gk);
        genericService.saveOrUpdate(r);
        logger.debug(transformer.convert(serializationService, r));
    }

    @Test
    public void testCreators() throws IOException, ClassNotFoundException {
        SchemaOrgMetadataTransformer transformer = new SchemaOrgMetadataTransformer();

        for (Creator r : genericService.findAll(Creator.class)) {
            logger.debug("//  {} - {}", r.getId(), r.getCreatorType());
            logger.debug(transformer.convert(serializationService, r, null));
        }
    }
}
