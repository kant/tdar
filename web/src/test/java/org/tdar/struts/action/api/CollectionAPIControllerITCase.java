package org.tdar.struts.action.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.tdar.core.bean.collection.ResourceCollection;
import org.tdar.core.exception.StatusCode;
import org.tdar.core.service.SerializationService;
import org.tdar.struts.action.AbstractAdminControllerITCase;
import org.tdar.struts.action.api.collection.CollectionAPIAction;

import com.opensymphony.xwork2.Action;

public class CollectionAPIControllerITCase extends AbstractAdminControllerITCase {

    @Autowired
    SerializationService serializationService;

    @Test
    @Rollback
    public void testAPIController() throws Exception {
        ResourceCollection rc;
        String uploadStatus;
        CollectionAPIAction controller = setupParent();
        rc = genericService.find(ResourceCollection.class, controller.getId());
        // rc.setOrientation(DisplayOrientation.LIST);
        rc.setName("another name");
        String childXml = serializationService.convertToXML(rc);
        rc = null;
        controller = generateNewInitializedController(CollectionAPIAction.class);
        controller.setRecord(childXml);
        uploadStatus = controller.upload();
        logger.debug(controller.getErrorMessage());
        assertTrue(controller.getErrorMessage().contains("updated"));
        assertEquals(Action.SUCCESS, uploadStatus);
        assertEquals(StatusCode.UPDATED, controller.getStatus());
        logger.debug("{}", controller.getImportedRecord());
        childXml = serializationService.convertToXML(controller.getImportedRecord());
        logger.info(childXml);
        assertEquals("another name", controller.getImportedRecord().getName());
    }

    @Test
    @Rollback
    public void testAPIControllerChange() throws Exception {
        ResourceCollection rc;
        String uploadStatus;
        CollectionAPIAction controller = setupParent();

        rc = new ResourceCollection("child", "child description", getBasicUser());
        rc.setParent(genericService.find(ResourceCollection.class, controller.getId()));
        // rc.setOrientation(DisplayOrientation.GRID);
        String childXml = serializationService.convertToXML(rc);
        rc = null;
        controller = generateNewInitializedController(CollectionAPIAction.class);
        controller.setRecord(childXml);
        uploadStatus = controller.upload();
        logger.debug(controller.getErrorMessage());
        assertTrue(controller.getErrorMessage().contains("created"));
        assertEquals(Action.SUCCESS, uploadStatus);
        assertEquals(StatusCode.CREATED, controller.getStatus());
        logger.debug("{}", controller.getImportedRecord());
        childXml = serializationService.convertToXML(controller.getImportedRecord());
        logger.info(childXml);
    }

    private CollectionAPIAction setupParent() throws Exception {
        ResourceCollection rc = new ResourceCollection("parent", "parent description", getBasicUser());
        // rc.setOrientation(DisplayOrientation.GRID);
        String docXml = serializationService.convertToXML(rc);
        logger.info(docXml);
        rc = null;

        CollectionAPIAction controller = generateNewInitializedController(CollectionAPIAction.class);
        controller.setRecord(docXml);
        String uploadStatus = controller.upload();
        logger.debug(controller.getErrorMessage());
        assertTrue(controller.getErrorMessage().contains("created"));
        assertEquals(Action.SUCCESS, uploadStatus);
        assertEquals(StatusCode.CREATED, controller.getStatus());
        logger.debug("{}", controller.getImportedRecord());
        docXml = serializationService.convertToXML(controller.getImportedRecord());
        logger.info(docXml);
        return controller;
    }

    @Test
    @Ignore
    @Rollback
    public void testAPIControllerJSON() throws Exception {
        ResourceCollection rc = new ResourceCollection("parent", "parent description", getBasicUser());
        // rc.setOrientation(DisplayOrientation.GRID);
        String docXml = serializationService.convertToJson(rc);
        logger.info(docXml);
        rc = null;

        CollectionAPIAction controller = generateNewInitializedController(CollectionAPIAction.class);
        controller.setRecord(docXml);
        String uploadStatus = controller.upload();
        logger.debug(controller.getErrorMessage());
        assertTrue(controller.getErrorMessage().contains("created"));
        assertEquals(Action.SUCCESS, uploadStatus);
        assertEquals(StatusCode.CREATED, controller.getStatus());
        logger.debug("{}", controller.getImportedRecord());
        docXml = serializationService.convertToJson(controller.getImportedRecord());
        logger.info(docXml);

        rc = new ResourceCollection("child", "child description", getBasicUser());
        rc.setParent(genericService.find(ResourceCollection.class, controller.getId()));
        // rc.setOrientation(DisplayOrientation.GRID);
        String childXml = serializationService.convertToJson(rc);
        rc = null;
        controller = generateNewInitializedController(CollectionAPIAction.class);
        controller.setRecord(childXml);
        uploadStatus = controller.upload();
        logger.debug(controller.getErrorMessage());
        assertTrue(controller.getErrorMessage().contains("created"));
        assertEquals(Action.SUCCESS, uploadStatus);
        assertEquals(StatusCode.CREATED, controller.getStatus());
        logger.debug("{}", controller.getImportedRecord());
        childXml = serializationService.convertToJson(controller.getImportedRecord());
        logger.info(childXml);

    }

}
