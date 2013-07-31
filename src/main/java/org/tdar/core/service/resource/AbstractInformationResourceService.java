package org.tdar.core.service.resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.tdar.core.bean.PersonalFilestoreTicket;
import org.tdar.core.bean.entity.Person;
import org.tdar.core.bean.resource.Dataset;
import org.tdar.core.bean.resource.InformationResource;
import org.tdar.core.bean.resource.InformationResourceFile;
import org.tdar.core.bean.resource.InformationResourceFile.FileAccessRestriction;
import org.tdar.core.bean.resource.InformationResourceFile.FileAction;
import org.tdar.core.bean.resource.datatable.DataTable;
import org.tdar.core.bean.resource.datatable.DataTableColumn;
import org.tdar.core.bean.resource.InformationResourceFileVersion;
import org.tdar.core.bean.resource.Language;
import org.tdar.core.configuration.TdarConfiguration;
import org.tdar.core.dao.resource.DatasetDao;
import org.tdar.core.dao.resource.InformationResourceFileDao;
import org.tdar.core.dao.resource.ResourceDao;
import org.tdar.core.exception.TdarRecoverableRuntimeException;
import org.tdar.core.service.PersonalFilestoreService;
import org.tdar.core.service.ServiceInterface;
import org.tdar.core.service.workflow.ActionMessageErrorSupport;
import org.tdar.core.service.workflow.WorkflowResult;
import org.tdar.filestore.FileAnalyzer;
import org.tdar.filestore.Filestore;
import org.tdar.filestore.Filestore.BaseFilestore;
import org.tdar.filestore.personal.PersonalFilestore;
import org.tdar.struts.data.FileProxy;

/**
 * $Id: AbstractInformationResourceService.java 1466 2011-01-18 20:32:38Z abrin$
 * 
 * Provides basic InformationResource services including file management (via FileProxyS).
 * 
 * @author <a href='mailto:Allen.Lee@asu.edu'>Allen Lee</a>
 * @version $Rev$
 */

public abstract class AbstractInformationResourceService<T extends InformationResource, R extends ResourceDao<T>> extends ServiceInterface.TypedDaoBase<T, R> {

    // FIXME: this should be injected
    private static final Filestore filestore = TdarConfiguration.getInstance().getFilestore();

    @Autowired
    private InformationResourceFileDao informationResourceFileDao;
    @Autowired
    private DatasetDao datasetDao;

    @Autowired
    private PersonalFilestoreService personalFilestoreService;

    protected FileAnalyzer analyzer;

    // private MessageService messageService;

    // Martin: according to the Spring documentation all these private methods marked as @Transactional totally ignored.
    // They must be public to have the annotation recognised?
    // See: http://static.springsource.org/spring/docs/3.2.x/spring-framework-reference/html/transaction.html#transaction-declarative-annotations
    @Transactional(readOnly = false)
    private void addInformationResourceFile(InformationResource resource, InformationResourceFile irFile, FileProxy proxy) throws IOException {
        // always set the download/version info and persist the relationships between the InformationResource and its IRFile.
        incrementVersionNumber(irFile);
        // genericDao.saveOrUpdate(resource);
        getDao().saveOrUpdate(resource);
        irFile.setInformationResource(resource);
        proxy.setInformationResourceFileVersion(createVersionMetadataAndStore(irFile, proxy));
        setInformationResourceFileMetadata(irFile, proxy);
        for (FileProxy additionalVersion : proxy.getAdditionalVersions()) {
            logger.debug("Creating new version {}", additionalVersion);
            createVersionMetadataAndStore(irFile, additionalVersion);
        }
        getDao().saveOrUpdate(irFile);
        resource.add(irFile);
        logger.debug("all versions for {}", irFile);
    }

    @Transactional(readOnly = true)
    private InformationResourceFile findInformationResourceFile(FileProxy proxy) {
        InformationResourceFile irFile = getDao().find(InformationResourceFile.class, proxy.getFileId());
        if (irFile == null) {
            logger.error("{} had no findable InformationResourceFile.id set on it", proxy);
            // FIXME: throw an exception?
        }
        return irFile;
    }

    @Transactional
    public WorkflowResult importFileProxiesAndProcessThroughWorkflow(T resource, Person user, Long ticketId, ActionMessageErrorSupport listener,
            List<FileProxy> fileProxiesToProcess) throws Exception {
        if (CollectionUtils.isEmpty(fileProxiesToProcess)) {
            logger.debug("Nothing to process, returning.");
            return new WorkflowResult(fileProxiesToProcess);
        }

        processMetadataForFileProxies(resource, fileProxiesToProcess.toArray(new FileProxy[0]));
        List<InformationResourceFileVersion> filesToProcess = new ArrayList<>();

        for (FileProxy proxy : fileProxiesToProcess) {
            if (proxy.getAction().requiresWorkflowProcessing()) {
                InformationResourceFile irFile = proxy.getInformationResourceFile();
                InformationResourceFileVersion version = proxy.getInformationResourceFileVersion();
                logger.trace("version: {} proxy: {} ", version, proxy);
                getDao().saveOrUpdate(irFile);
                switch (version.getFileVersionType()) {
                    case UPLOADED:
                    case UPLOADED_ARCHIVAL:
                        irFile.setInformationResourceFileType(analyzer.analyzeFile(version));
                        filesToProcess.add(version);
                        break;
                    default:
                        logger.debug("Not setting file type on irFile {} for VersionType {}", irFile, proxy.getVersionType());
                }
            }
        }
        processFiles(resource, filesToProcess);
        WorkflowResult result = new WorkflowResult(fileProxiesToProcess);
        result.addActionErrorsAndMessages(listener);

        // getDao().refreshAll(resource.getInformationResourceFiles());
        /*
         * FIXME: Should I purge regardless of errors??? Really???
         */
        if (ticketId != null) {
            PersonalFilestore personalFilestore = personalFilestoreService.getPersonalFilestore(user);
            personalFilestore.purge(getDao().find(PersonalFilestoreTicket.class, ticketId));
        }
        return result;
    }

    private void processFiles(T resource, List<InformationResourceFileVersion> filesToProcess) {
        if (!CollectionUtils.isEmpty(filesToProcess)) {
            if (resource.getResourceType().isCompositeFilesEnabled()) {
                try {
                    analyzer.processFile(filesToProcess.toArray(new InformationResourceFileVersion[0]));
                } catch (Exception e) {
                    logger.warn("caught exception {} while analyzing file {}\n {}", e, filesToProcess, ExceptionUtils.getStackTrace(e));
                }
            } else {
                for (InformationResourceFileVersion version : filesToProcess) {
                    try {
                        analyzer.processFile(version);
                    } catch (Exception e) {
                        logger.warn("caught exception {} while analyzing file {}", e, version);
                    }

                }
            }
        }
    }

    @Transactional
    private void processFileProxyMetadata(InformationResource informationResource, FileProxy proxy) throws IOException {
        logger.debug("applying {} to {}", proxy, informationResource);
        // will be reassigned in a REPLACE or ADD_DERIVATIVE
        InformationResourceFile irFile = new InformationResourceFile();
        if (proxy.getAction().requiresExistingIrFile()) {
            irFile = findInformationResourceFile(proxy);
            if (irFile == null) {
                logger.error("FileProxy {} {} had no InformationResourceFile.id ({}) set on it", proxy.getFilename(), proxy.getAction(), proxy.getFileId());
                return;
            }
        }

        switch (proxy.getAction()) {
            case MODIFY_METADATA:
                // set sequence number and confidentiality
                setInformationResourceFileMetadata(irFile, proxy);
                getDao().update(irFile);
                break;
            case REPLACE:
                // explicit fall through to ADD after loading the existing irFile to be replaced.
            case ADD:
                addInformationResourceFile(informationResource, irFile, proxy);
                break;
            case ADD_DERIVATIVE:
                createVersionMetadataAndStore(irFile, proxy);
                break;
            case DELETE:
                irFile.markAsDeleted();
                getDao().update(irFile);
                if (informationResource instanceof Dataset) {
                    unmapDataTablesForFile((Dataset) informationResource, irFile);
                }
                break;
            case NONE:
                logger.debug("Taking no action on {} with proxy {}", informationResource, proxy);
                break;
            default:
                break;
        }
        proxy.setInformationResourceFile(irFile);
    }

    @Transactional(readOnly = true)
    private void unmapDataTablesForFile(Dataset dataset, InformationResourceFile irFile) {
        String fileName = irFile.getFileName();
        switch (FilenameUtils.getExtension(fileName).toLowerCase()) {
            case "tab":
            case "csv":
            case "txt":
                String name = FilenameUtils.getBaseName(fileName);
                name = datasetDao.normalizeTableName(name);
                DataTable dt = dataset.getDataTableByGenericName(name);
                logger.info("removing {}", dt);
                cleanupUnusedTablesAndColumns(dataset, Arrays.asList(dt));
                // dataset.getDataTableByGenericName(name)
                break;
            default:
                cleanupUnusedTablesAndColumns(dataset, dataset.getDataTables());
        }
    }

    @Transactional
    public void processMetadataForFileProxies(InformationResource informationResource, FileProxy... proxies) throws IOException {
        for (FileProxy proxy : proxies) {
            processFileProxyMetadata(informationResource, proxy);
        }
    }

    @Transactional(readOnly = true)
    public void cleanupUnusedTablesAndColumns(Dataset dataset, Collection<DataTable> tablesToRemove) {
        logger.info("deleting unmerged tables: {}", tablesToRemove);
        ArrayList<DataTableColumn> columnsToUnmap = new ArrayList<DataTableColumn>();
        for (DataTable table : tablesToRemove) {
            columnsToUnmap.addAll(table.getDataTableColumns());
        }
        // first unmap all columns from the removed tables
        datasetDao.unmapAllColumnsInProject(dataset.getProject(), columnsToUnmap);
        dataset.getDataTables().removeAll(tablesToRemove);
    }

    private void setInformationResourceFileMetadata(InformationResourceFile irFile, FileProxy fileProxy) {
        irFile.setRestriction(fileProxy.getRestriction());
        Integer sequenceNumber = fileProxy.getSequenceNumber();
        if (fileProxy.getRestriction() == FileAccessRestriction.EMBARGOED) {
            if (irFile.getDateMadePublic() == null) {
                Calendar calendar = Calendar.getInstance();
                // set date made public to 5 years now.
                calendar.add(Calendar.YEAR, TdarConfiguration.getInstance().getEmbargoPeriod());
                irFile.setDateMadePublic(calendar.getTime());
            }
        } else {
            irFile.setDateMadePublic(null);
        }

        if (fileProxy.getAction() == FileAction.MODIFY_METADATA || fileProxy.getAction() == FileAction.ADD) {
            irFile.setDescription(fileProxy.getDescription());
            irFile.setFileCreatedDate(fileProxy.getFileCreatedDate());
        }

        if (sequenceNumber == null) {
            logger.warn("No sequence number set on file proxy {}, existing sequence number was {}", fileProxy, irFile.getSequenceNumber());
        }
        else {
            irFile.setSequenceNumber(sequenceNumber);
        }
    }

    private void incrementVersionNumber(InformationResourceFile irFile) {
        irFile.incrementVersionNumber();
        irFile.clearStatus();
        logger.info("incremented version number and reset download and status for irfile: {}", irFile, irFile.getLatestVersion());
    }

    @Transactional(readOnly = false)
    public void reprocessInformationResourceFiles(Collection<InformationResourceFile> informationResourceFiles, ActionMessageErrorSupport listener) {
        Iterator<InformationResourceFile> fileIterator = informationResourceFiles.iterator();
        while (fileIterator.hasNext()) {
            InformationResourceFile irFile = fileIterator.next();
            InformationResourceFileVersion original = irFile.getLatestUploadedVersion();
            Iterator<InformationResourceFileVersion> iterator = irFile.getInformationResourceFileVersions().iterator();
            while (iterator.hasNext()) {
                InformationResourceFileVersion version = iterator.next();
                if (!version.equals(original) && !version.isUploaded() && !version.isArchival()) {
                    iterator.remove();
                    informationResourceFileDao.delete(version);
                }
            }
            // this is a known case where we need to purge the session
            getDao().synchronize();
            try {
                original.setTransientFile(filestore.retrieveFile(original));
                if (!analyzer.processFile(original)) {
                    logger.error("could not reprocess file: " + original.getFilename());
                    // fixme: add listener...
                }
                // messageService.sendFileProcessingRequest(workflow, original);
            } catch (Exception e) {
                logger.warn("caught exception {} while analyzing file {}", e, original.getFilename());
                // fixme: add listener...
            }
        }

    }

    @Transactional(readOnly = false)
    private InformationResourceFileVersion createVersionMetadataAndStore(InformationResourceFile irFile, FileProxy fileProxy) throws IOException {
        String filename = BaseFilestore.sanitizeFilename(fileProxy.getFilename());
        if (fileProxy.getFile() == null || !fileProxy.getFile().exists()) {
            throw new TdarRecoverableRuntimeException("something went wrong, file " + fileProxy.getFilename() + " does not exist");
        }
        InformationResourceFileVersion version = new InformationResourceFileVersion(fileProxy.getVersionType(), filename, irFile);
        if (irFile.isTransient()) {
            getDao().saveOrUpdate(irFile);
        }

        irFile.addFileVersion(version);
        filestore.store(fileProxy.getFile(), version);
        version.setTransientFile(fileProxy.getFile());
        getDao().save(version);
        getDao().saveOrUpdate(irFile);
        return version;
    }

    public List<Language> findAllLanguages() {
        return Arrays.asList(Language.values());
    }

    /**
     * @param analyzer
     *            the analyzer to set
     */
    @Autowired
    public void setAnalyzer(FileAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

}
