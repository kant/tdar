package org.tdar.core.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.exceptions.COSVisitorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the actual merging of the PDFs through a piped-output-stream.
 * 
 * @author abrin
 *
 */
public class PDFMergeTask implements Runnable {

    private static final String PIPE_CLOSED = "Pipe closed";
    private final transient Logger logger = LoggerFactory.getLogger(getClass());
    private PDFMergeWrapper wrapper;
    private PipedOutputStream pipedOutputStream;

    public PDFMergeTask(PDFMergeWrapper wrapper, PipedOutputStream pipedOutputStream) {
        this.wrapper = wrapper;
        this.pipedOutputStream = pipedOutputStream;
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        try {
            wrapper.getMerger().mergeDocuments();
            wrapper.setSuccessful(true);
        } catch (IOException | COSVisitorException ex ) {
            // downgrade broken pipe exceptions
            if (isBrokenPipeException(ex)) {
                logger.warn("broken pipe", ex);
            } else {
                logger.error("PDF Converter Exception:", ex);
                // if IO exception was due to encrypted document, try again without the cover page
                attemptTransferWithoutMerge(wrapper.getDocument(), pipedOutputStream);
            }
            wrapper.setFailureReason(ex.getMessage());
        } catch (Exception e) {
            logger.error("exception when processing PDF cover page: {}", e.getMessage(), e);
            wrapper.setFailureReason(e.getMessage());
            // if some other kind of error occured during the merge, try to send without cover page.
            attemptTransferWithoutMerge(wrapper.getDocument(), pipedOutputStream);
        } finally {
            IOUtils.closeQuietly(pipedOutputStream);
        }
    }

    /**
     * Java has no built-in broken pipe exception, however, it's sometimes convenient to treat them differently from other types
     * of IO Exception (e.g. log them at different lower level, because they are inevetible in a web-serving environment).
     * 
     * @param exception
     * @return
     */
    private boolean isBrokenPipeException(Exception exception) {
        if (exception.getClass().getSimpleName().contains("ClientAbortException")) {
            return true;
        }
        if (StringUtils.contains(exception.getMessage(), PIPE_CLOSED)) {
            return true;
        }
        
        if (exception.getCause() != null && exception.getCause().getMessage().contains(PIPE_CLOSED)) {
            return true;
        }
        
        return false;

    }

    private void attemptTransferWithoutMerge(File document, OutputStream os) {
        try {
            logger.warn("attempting to send pdf without cover page: {}", document);
            IOUtils.copyLarge(new FileInputStream(document), os);
        } catch (Exception ex) {
            logger.error("cannot attach PDF, even w/o cover page", ex);
        }
    }
}
