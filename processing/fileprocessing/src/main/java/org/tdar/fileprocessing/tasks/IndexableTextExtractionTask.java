/**
 * 
 */
package org.tdar.fileprocessing.tasks;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.tdar.exception.ExceptionWrapper;
import org.tdar.fileprocessing.workflows.FileArchiveWorkflow;
import org.tdar.filestore.FileStoreFile;
import org.tdar.filestore.VersionType;
import org.tdar.utils.MessageHelper;

/**
 * @author Adam Brin
 *         NOTE: this class should not be used without more testing ... it has issues with including POI 3.8
 *         calls which is in BETA but we're on 3.7. Also, it seems to kill some tests
 */
public class IndexableTextExtractionTask extends AbstractTask {

    private static final String INDEX_TXT = ".index.txt";
    private static final String METADATA2 = ".metadata";
    private static final long serialVersionUID = -5207578211297342261L;
    private static final long ONE_GB = 1073741824;

    @Override
    public void run() throws Exception {
        run(getWorkflowContext().getOriginalFile());
    }

    public void run(FileStoreFile version) throws Exception {
        File file = version.getTransientFile();
        FileOutputStream metadataOutputStream = null;
        InputStream stream = null;
        String filename = file.getName();
        File metadataFile = new File(getWorkflowContext().getWorkingDirectory(), filename + METADATA2);
        try {
            InputStream input = new FileInputStream(file);
            Tika tika = new Tika();
            Metadata metadata = new Metadata();
            String mimeType = tika.detect(input);
            metadata.set(HttpHeaders.CONTENT_TYPE, mimeType);

            Parser parser = new AutoDetectParser();
            ParseContext parseContext = new ParseContext();

            metadataFile = new File(getWorkflowContext().getWorkingDirectory(), filename + METADATA2);
            metadataOutputStream = new FileOutputStream(metadataFile);
            String extension = FilenameUtils.getExtension(filename).toLowerCase();
            if (!FileArchiveWorkflow.ARCHIVE_EXTENSIONS_SUPPORTED.contains(extension)) {
                File indexFile = new File(getWorkflowContext().getWorkingDirectory(), filename + INDEX_TXT);
                FileOutputStream indexOutputStream = new FileOutputStream(indexFile);
                BufferedOutputStream indexedFileOutputStream = new BufferedOutputStream(indexOutputStream);
                BodyContentHandler handler = new BodyContentHandler(indexedFileOutputStream);
                // If we're dealing with a zip, read only the beginning of the file
                stream = new FileInputStream(file);
                try {
                    // if we're a PDF and we're really big... then we should use PDFBox to extract the text to protect memory
                    if ("pdf".equals(extension) && file.length() > ONE_GB) {
                        getLogger().debug("using fallback PDF model");
                        try {
                            fallbackWriteFile(stream, indexedFileOutputStream);
                        } catch (IOException e) {
                            getLogger().debug("NPE from PDF issue", e);
                        }
                    } else {
                        parser.parse(stream, handler, metadata, parseContext);
                    }
                    IOUtils.closeQuietly(indexedFileOutputStream);
                    if (indexFile.length() > 0) {
                        addDerivativeFile(version, indexFile, VersionType.INDEXABLE_TEXT);
                    }
                } catch (NullPointerException npe) {
                    getLogger().debug("NPE from PDF issue", npe);
                }
            }
            Thread.yield();

            List<String> gpsValues = new ArrayList<>();

            for (String name : metadata.names()) {
                StringWriter sw = new StringWriter();
                if (StringUtils.isNotBlank(metadata.get(name))) {
                    sw.append(name).append(":");
                    // http://www.exiv2.org/tags-xmp-exif.html
                    if (name.matches("(?i).*(latitude|longitude|gpsl|gpsd|gpsi).*")) {
                        gpsValues.add(name);
                    }
                    if (metadata.isMultiValued(name)) {
                        sw.append(StringUtils.join(metadata.getValues(name), "|"));
                    } else {
                        sw.append(metadata.get(name));
                    }
                    sw.append("\r\n");
                    IOUtils.write(sw.toString(), metadataOutputStream);
                }
            }
            if (CollectionUtils.isNotEmpty(gpsValues)) {
                ExceptionWrapper wrapper = new ExceptionWrapper(MessageHelper.getMessage("indexableText.gps_message", gpsValues), "");
                wrapper.setFatal(false);
                getWorkflowContext().getExceptions().add(wrapper);
            }
        } catch (Throwable t) {
            // Marking this as a "warn" as it's a derivative
            getLogger().warn("a tika indexing exception happend ", t);
        } finally {
            IOUtils.closeQuietly(stream);
            IOUtils.closeQuietly(metadataOutputStream);
        }

        if ((metadataFile != null) && metadataFile.exists() && (metadataFile.length() > 0)) {
            addDerivativeFile(version, metadataFile, VersionType.METADATA);

        }
    }

    public void fallbackWriteFile(InputStream stream, BufferedOutputStream indexedFileOutputStream) throws IOException {
        PDDocument pdDoc = PDDocument.load(stream, MemoryUsageSetting.setupMixed(Runtime.getRuntime().freeMemory() / 5L));
        PDFTextStripper pdfStripper = new PDFTextStripper();
        OutputStreamWriter writer = new OutputStreamWriter(indexedFileOutputStream);
        pdfStripper.writeText(pdDoc, writer);
        IOUtils.closeQuietly(writer);
    }

    @Override
    public String getName() {
        return "IndexableTextExtractionTask";
    }

}
