package org.tdar.core.service.download;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.resource.InformationResource;
import org.tdar.core.bean.resource.InformationResourceFileVersion;
import org.tdar.core.bean.statistics.FileDownloadStatistic;

/**
 * Represents a transfer object for a download. The DownloadService will build this and pass it back to the DownloadController, the controller will then
 * ask this for the inputStream and various info it needs. This means that the streaming of the result happens through the calls to this
 * 
 * @author abrin
 *
 */
public class DownloadTransferObject implements Serializable {

    private final transient Logger logger = LoggerFactory.getLogger(getClass());

    private static final long serialVersionUID = 7856219475924463528L;

    private List<FileDownloadStatistic> statistics = new ArrayList<>();
    private List<DownloadFile> downloads = new ArrayList<>();

    private String mimeType;
    private Long contentLength;
    private InformationResource informationResource;
    private List<InformationResourceFileVersion> versionsToDownload;
    private String fileName;
    private InputStream stream;
    private DownloadResult result;
    private TdarUser authenticatedUser;

    private String dispositionPrefix;

    public DownloadTransferObject(InformationResource resourceToDownload, List<InformationResourceFileVersion> versionsToDownload, TdarUser user) {
        this.informationResource = resourceToDownload;
        this.versionsToDownload = versionsToDownload;
        this.setAuthenticatedUser(user);
    }

    public DownloadTransferObject() {
    }

    public InputStream getStream() {
        return stream;
    }

    public void setStream(InputStream stream) {
        this.stream = stream;
    }

    public List<FileDownloadStatistic> getStatistics() {
        return statistics;
    }

    public void setStatistics(List<FileDownloadStatistic> statistics) {
        this.statistics = statistics;
    }

    public List<DownloadFile> getDownloads() {
        return downloads;
    }

    public void setDownloads(List<DownloadFile> downloads) {
        this.downloads = downloads;
    }

    public String getMimeType() {
        if (CollectionUtils.size(downloads) > 1) {
            mimeType = "application/zip";
        }
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getFileName() {
        if (CollectionUtils.size(downloads) > 1) {
            return String.format("files-%s.zip", getInformationResource().getId());
        }
        return fileName;
    }

    public InputStream getInputStream() throws Exception {
        logger.debug("calling getInputStream");
        if (CollectionUtils.size(downloads) > 1) {
            return getZipInputStream();
        }
        logger.debug("{}", downloads.get(0));
        return downloads.get(0).getInputStream();
    }

    private InputStream getZipInputStream() throws Exception {
        PipedInputStream is = new PipedInputStream();
        ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(new PipedOutputStream(is)));
        for (DownloadFile df : downloads) {
            String filename = df.getFileName();

            ZipEntry zentry = new ZipEntry(filename);
            zout.putNextEntry(zentry);
            InputStream fin = df.getInputStream();
            logger.debug("adding to archive: {}", df.getFileName());
            IOUtils.copy(fin, zout);
            IOUtils.closeQuietly(fin);
        }
        IOUtils.closeQuietly(zout);
        return is;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public InformationResource getInformationResource() {
        return informationResource;
    }

    public void setInformationResource(InformationResource informationResource) {
        this.informationResource = informationResource;
    }

    public DownloadResult getResult() {
        return result;
    }

    public void setResult(DownloadResult result) {
        this.result = result;
    }

    public List<InformationResourceFileVersion> getVersionsToDownload() {
        return versionsToDownload;
    }

    public void setVersionsToDownload(List<InformationResourceFileVersion> versionsToDownload) {
        this.versionsToDownload = versionsToDownload;
    }

    public TdarUser getAuthenticatedUser() {
        return authenticatedUser;
    }

    public void setAuthenticatedUser(TdarUser authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
    }

    public String getDispositionPrefix() {
        return dispositionPrefix;
    }

    public void setDispositionPrefix(String string) {
        this.dispositionPrefix = string;

    }

    public Long getContentLength() {
        return contentLength;
    }

    public void setContentLength(Long contentLength) {
        this.contentLength = contentLength;
    }

    @Override
    public String toString() {
        return StringUtils.join(downloads.toArray());
    }

}
