/**
 * 
 */
package org.tdar.core.filestore;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.tdar.TestConstants;
import org.tdar.core.bean.AbstractIntegrationTestCase;
import org.tdar.core.bean.resource.Document;
import org.tdar.core.bean.resource.InformationResourceFile;
import org.tdar.core.bean.resource.InformationResourceFile.FileStatus;
import org.tdar.core.bean.resource.InformationResourceFile.FileType;
import org.tdar.core.bean.resource.InformationResourceFileVersion;
import org.tdar.core.service.workflow.GenericDocumentWorkflow;
import org.tdar.core.service.workflow.MessageService;
import org.tdar.core.service.workflow.PDFWorkflow;
import org.tdar.core.service.workflow.Workflow;
import org.tdar.filestore.FileAnalyzer;
import org.tdar.filestore.PairtreeFilestore;

import static org.junit.Assert.*;

/**
 * @author Adam Brin
 * 
 */
public class DocumentFileITCase extends AbstractIntegrationTestCase {

    @Autowired
    private FileAnalyzer fileAnalyzer;

    @Autowired
    private MessageService messageService;

    @Test
    public void testBrokenImageStatus() throws Exception {
        String filename = "volume1-encrypted-test.pdf";
        File f = new File(TestConstants.TEST_DOCUMENT_DIR + "/sample_pdf_formats/", filename);
        PairtreeFilestore store = new PairtreeFilestore(TestConstants.FILESTORE_PATH);

        InformationResourceFileVersion originalVersion = generateAndStoreVersion(Document.class, filename, f, store);
        FileType fileType = fileAnalyzer.analyzeFile(originalVersion);
        assertEquals(FileType.DOCUMENT, fileType);
        Workflow workflow = fileAnalyzer.getWorkflow(originalVersion);
        assertEquals(PDFWorkflow.class, workflow.getClass());
        boolean result = messageService.sendFileProcessingRequest(originalVersion, workflow);
        InformationResourceFile informationResourceFile = originalVersion.getInformationResourceFile();
        informationResourceFile = genericService.find(InformationResourceFile.class, informationResourceFile.getId());
        assertFalse(result);
        assertEquals(FileStatus.PROCESSING_ERROR, informationResourceFile.getStatus());
    }


    @Test
    public void testRTFTextExtraction() throws Exception {
        String filename = "test-file.rtf";
        File f = new File(TestConstants.TEST_DOCUMENT_DIR, filename);
        PairtreeFilestore store = new PairtreeFilestore(TestConstants.FILESTORE_PATH);

        InformationResourceFileVersion originalVersion = generateAndStoreVersion(Document.class, filename, f, store);
        FileType fileType = fileAnalyzer.analyzeFile(originalVersion);
        assertEquals(FileType.DOCUMENT, fileType);
        Workflow workflow = fileAnalyzer.getWorkflow(originalVersion);
        assertEquals(GenericDocumentWorkflow.class, workflow.getClass());
        boolean result = messageService.sendFileProcessingRequest(originalVersion, workflow);
        InformationResourceFile informationResourceFile = originalVersion.getInformationResourceFile();
        informationResourceFile = genericService.find(InformationResourceFile.class, informationResourceFile.getId());
        assertTrue(result);
        assertEquals(null, informationResourceFile.getStatus());
        InformationResourceFileVersion indexableVersion = informationResourceFile.getIndexableVersion();
        String text = FileUtils.readFileToString(indexableVersion.getFile());
        logger.info(text);
        assertTrue(text.toLowerCase().contains("have fun digging"));
    }
}
