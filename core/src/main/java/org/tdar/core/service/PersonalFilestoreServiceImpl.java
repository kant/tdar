package org.tdar.core.service;

import java.io.File;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tdar.core.bean.ImportFileStatus;
import org.tdar.core.bean.PersonalFilestoreTicket;
import org.tdar.core.bean.billing.BillingAccount;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.file.AbstractFile;
import org.tdar.core.bean.file.CurationState;
import org.tdar.core.bean.file.FileComment;
import org.tdar.core.bean.file.Mark;
import org.tdar.core.bean.file.TdarDir;
import org.tdar.core.bean.file.TdarFile;
import org.tdar.core.bean.resource.ResourceType;
import org.tdar.core.dao.DirSummary;
import org.tdar.core.dao.DirSummaryPart;
import org.tdar.core.dao.FileOrder;
import org.tdar.core.dao.FileProcessingDao;
import org.tdar.core.dao.RecentFileSummary;
import org.tdar.core.dao.base.GenericDao;
import org.tdar.core.exception.FileUploadException;
import org.tdar.filestore.FileAnalyzer;
import org.tdar.filestore.personal.BagitPersonalFilestore;
import org.tdar.filestore.personal.PersonalFileType;
import org.tdar.filestore.personal.PersonalFilestore;
import org.tdar.filestore.personal.PersonalFilestoreFile;
import org.tdar.utils.PersistableUtils;

/**
 * Manages adding and saving files in the @link PersonalFilestore
 * 
 * @author <a href='jim.devos@asu.edu'>Jim Devos</a>, <a href='mailto:allen.lee@asu.edu'>Allen Lee</a>
 * @version $Rev$
 */
@Service
public class PersonalFilestoreServiceImpl implements PersonalFilestoreService {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    @Autowired
    private GenericDao genericDao;

    @Autowired
    private FileProcessingDao fileProcessingDao;

    @Autowired
    private FileAnalyzer analyzer;

    // FIXME: double check that won't leak memory
    private Map<TdarUser, PersonalFilestore> personalFilestoreCache = new WeakHashMap<TdarUser, PersonalFilestore>();

    /*
     * (non-Javadoc)
     * 
     * @see org.tdar.core.service.PersonalFilestoreService#createPersonalFilestoreTicket(org.tdar.core.bean.entity.TdarUser)
     */
    @Override
    @Transactional
    public PersonalFilestoreTicket createPersonalFilestoreTicket(TdarUser person) {
        return createPersonalFilestoreTicket(person, PersonalFileType.UPLOAD);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tdar.core.service.PersonalFilestoreService#createPersonalFilestoreTicket(org.tdar.core.bean.entity.TdarUser,
     * org.tdar.filestore.personal.PersonalFileType)
     */
    @Override
    @Transactional
    public PersonalFilestoreTicket createPersonalFilestoreTicket(TdarUser person, PersonalFileType fileType) {
        PersonalFilestoreTicket tfg = new PersonalFilestoreTicket();
        tfg.setSubmitter(person);
        tfg.setPersonalFileType(fileType);
        genericDao.save(tfg);

        // FIXME: it uses the ID as the ticket, but needs to check whether the ticket actually exists
        return tfg;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tdar.core.service.PersonalFilestoreService#getPersonalFilestore(org.tdar.core.bean.entity.TdarUser)
     */
    @Override
    public synchronized PersonalFilestore getPersonalFilestore(TdarUser submitter) {
        PersonalFilestore personalFilestore = personalFilestoreCache.get(submitter);
        if (personalFilestore == null) {
            personalFilestore = new BagitPersonalFilestore();
            personalFilestoreCache.put(submitter, personalFilestore);
        }
        return personalFilestore;
    }

    @Transactional(readOnly = false)
    @Override
    public TdarFile store(PersonalFilestoreTicket ticket, File file, String fileName, BillingAccount account, TdarUser user, TdarDir dir)
            throws FileUploadException {
        PersonalFilestore filestore = getPersonalFilestore(ticket);
        try {
            // if we're not unfiled then require uniqueness
            if (dir == null || !StringUtils.equals(dir.getName(), TdarDir.UNFILED)) {
                List<AbstractFile> listFiles = listFiles(dir, account, null,  null, user);
                for (AbstractFile f : listFiles) {
                    if (StringUtils.equalsIgnoreCase(f.getName(), fileName)) {
                        throw new FileAlreadyExistsException(fileName);
                    }
                }

            }
            PersonalFilestoreFile store = filestore.store(ticket, file, fileName);
            TdarFile tdarFile = new TdarFile();
            tdarFile.setInternalName(store.getFile().getName());
            tdarFile.setLocalPath(store.getFile().getPath());
            tdarFile.setFilename(fileName);
            tdarFile.setExtension(FilenameUtils.getExtension(fileName));
            tdarFile.setSize(file.length());
            tdarFile.setDateCreated(new Date());
            if (account != null) {
                tdarFile.setAccount(account);
            }
            tdarFile.setUploader(user);
            if (dir != null) {
                tdarFile.setParent(dir);
            }
            tdarFile.setMd5(store.getMd5());
            tdarFile.setStatus(ImportFileStatus.UPLOADED);
            genericDao.saveOrUpdate(tdarFile);
            return tdarFile;
        } catch (Exception e) {
            throw new FileUploadException("uploadController.could_not_store", e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tdar.core.service.PersonalFilestoreService#findPersonalFilestoreTicket(java.lang.Long)
     */
    @Override
    @Transactional(readOnly = true)
    public PersonalFilestoreTicket findPersonalFilestoreTicket(Long ticketId) {
        return genericDao.find(PersonalFilestoreTicket.class, ticketId);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tdar.core.service.PersonalFilestoreService#retrieveAllPersonalFilestoreFiles(java.lang.Long)
     */
    @Override
    @Transactional(readOnly = true)
    public List<PersonalFilestoreFile> retrieveAllPersonalFilestoreFiles(Long ticketId) {
        PersonalFilestoreTicket ticket = findPersonalFilestoreTicket(ticketId);
        if (ticket == null) {
            return Collections.emptyList();
        }
        return getPersonalFilestore(ticket.getSubmitter()).retrieveAll(ticket);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tdar.core.service.PersonalFilestoreService#getPersonalFilestore(org.tdar.core.bean.PersonalFilestoreTicket)
     */
    @Override
    public synchronized PersonalFilestore getPersonalFilestore(PersonalFilestoreTicket ticket) {
        return getPersonalFilestore(ticket.getSubmitter());
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tdar.core.service.PersonalFilestoreService#getPersonalFilestore(java.lang.Long)
     */
    @Override
    @Transactional(readOnly = true)
    public synchronized PersonalFilestore getPersonalFilestore(Long ticketId) {
        PersonalFilestoreTicket ticket = findPersonalFilestoreTicket(ticketId);
        return getPersonalFilestore(ticket);
    }

    @Override
    @Transactional(readOnly = false)
    public TdarDir createDirectory(TdarDir parent, String name, BillingAccount account, TdarUser authenticatedUser) throws FileAlreadyExistsException {
        List<AbstractFile> listFiles = listFiles(parent, account, null, null, authenticatedUser);
        for (AbstractFile f : listFiles) {
            if (f instanceof TdarDir && StringUtils.equalsIgnoreCase(f.getName(), name)) {
                throw new FileAlreadyExistsException(name);
            }
        }
        TdarDir dir = new TdarDir();
        dir.setAccount(account);
        dir.setFilename(name);
        dir.setInternalName(name);
        dir.setParent(parent);
        dir.setDateCreated(new Date());
        dir.setUploader(authenticatedUser);
        genericDao.saveOrUpdate(dir);
        return dir;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AbstractFile> listFiles(TdarDir parent, BillingAccount account, String term, FileOrder sort, TdarUser authenticatedUser) {
        return fileProcessingDao.listFilesFor(parent, account, term, sort, authenticatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TdarDir> listDirectories(TdarDir parent, BillingAccount account, TdarUser authenticatedUser) {
        return fileProcessingDao.listDirectoriesFor(parent, account, authenticatedUser);
    }

    @Override
    @Transactional(readOnly = false)
    public void deleteFile(AbstractFile file, TdarUser authenticatedUser) throws FileUploadException {
        if (file instanceof TdarFile) {
            fileProcessingDao.delete(((TdarFile) file).getParts());
        }
        if (file instanceof TdarDir) {
            if (CollectionUtils.isNotEmpty(listFiles((TdarDir)file, file.getAccount(), null, null, authenticatedUser))) {
                throw new FileUploadException("personalFilestoreService.directory.not_empty");
            }
        }
        fileProcessingDao.delete(file);
    }

    @Override
    @Transactional(readOnly = false)
    public void moveFiles(List<AbstractFile> files, TdarDir dir, TdarUser authenticatedUser) {
        for (AbstractFile f : files) {
            f.setParent(dir);
            if (f instanceof TdarFile) {
                for (TdarFile part : ((TdarFile) f).getParts()) {
                    part.setParent(dir);
                    genericDao.saveOrUpdate(part);
                }
            }
            genericDao.saveOrUpdate(f);
        }
    }

    @Override
    @Transactional(readOnly = false)
    public TdarDir findUnfileDir(TdarUser authenticatedUser) {
        return fileProcessingDao.findUnfiledDirByName(authenticatedUser);

    }

    @Override
    @Transactional(readOnly = false)
    public void editMetadata(TdarFile file, String note, boolean needsOcr, CurationState curate, TdarUser user) {
        file.setNote(note);
        file.setCuration(curate);
        file.setRequiresOcr(needsOcr);
        genericDao.saveOrUpdate(file);
    }

    @Override
    @Transactional(readOnly = false)
    public void mark(List<TdarFile> files, Mark action, TdarUser user) {
        for (TdarFile file : files) {
            switch (action) {
                case CURATED:
                    file.setCuratedBy(user);
                    file.setDateCurated(new Date());
                    break;
                case EXTERNAL_REVIEWED:
                    file.setExternalReviewedBy(user);
                    file.setDateExternalReviewed(new Date());
                    break;
                case REVIEWED:
                    file.setReviewedBy(user);
                    file.setDateReviewed(new Date());
                    break;
                case INITIAL_REVIEWED:
                    file.setInitialReviewedBy(user);
                    file.setDateInitialReviewed(new Date());
                    break;
            }
            genericDao.saveOrUpdate(file);
        }
    }

    @Override
    @Transactional(readOnly = false)
    public void unMark(List<TdarFile> files, Mark action, TdarUser user) {
        for (TdarFile file : files) {
            switch (action) {
                case CURATED:
                    file.setCuratedBy(null);
                    file.setDateCurated(null);
                    break;
                case EXTERNAL_REVIEWED:
                    file.setExternalReviewedBy(null);
                    file.setDateExternalReviewed(null);
                    break;
                case REVIEWED:
                    file.setReviewedBy(null);
                    file.setDateReviewed(null);
                    break;
                case INITIAL_REVIEWED:
                    file.setInitialReviewedBy(null);
                    file.setDateInitialReviewed(null);
                    break;
            }
            genericDao.saveOrUpdate(file);
        }
    }

    @Override
    @Transactional(readOnly = false)
    public FileComment addComment(AbstractFile file, String comment, TdarUser authenticatedUser) {
        FileComment comm = new FileComment(authenticatedUser, comment);
        file.getComments().add(comm);
        genericDao.saveOrUpdate(file);
        genericDao.saveOrUpdate(comm);
        return comm;
    }

    @Override
    @Transactional(readOnly = false)
    public FileComment resolveComment(AbstractFile file, FileComment comment, TdarUser authenticatedUser) {
        comment.setResolved(true);
        comment.setDateResolved(new Date());
        comment.setResolver(authenticatedUser);
        genericDao.saveOrUpdate(comment);
        return comment;
    }

    @Override
    @Transactional(readOnly = true)
    public ResourceType getResourceTypeForFiles(TdarFile files) {
        // THIS IS A TEMPORARY FIX UNTIL WE HAVE BETTER LOGIC FOR DETERMINING TYPE
        List<ResourceType> types = new ArrayList<>(ResourceType.activeValues());
        types.remove(ResourceType.CODING_SHEET);
        // types.remove(ResourceType.GEOSPATIAL);
        ResourceType resourceType = analyzer.suggestTypeForFileName(files.getName(), types.toArray(new ResourceType[0]));
        if (resourceType == ResourceType.GEOSPATIAL &&
                (files.getExtension().equalsIgnoreCase("jpg") || files.getExtension().equalsIgnoreCase("tif"))) {
            // FIXME: need better logic here
            resourceType = ResourceType.IMAGE;
        }
        return resourceType;
    }

    @Override
    @Transactional(readOnly = false)
    public List<AbstractFile> moveFilesBetweenAccounts(List<AbstractFile> files, BillingAccount account, TdarUser authenticatedUser) {
        
        List<AbstractFile> allFiles = new ArrayList<>();
        List<AbstractFile> toProcess = new ArrayList<>(files);
        // for each of the initial files, set the parent to NULL because we're not moving the "dir"
        files.forEach(f -> {
            f.setParent(null);
        });
        while (CollectionUtils.isNotEmpty(toProcess)) {
            AbstractFile file = toProcess.remove(0);
            logger.debug("moving {} to {}", file, account);
            if (file instanceof TdarDir) {
                List<AbstractFile> listFiles = listFiles((TdarDir)file, file.getAccount(), null, null, authenticatedUser);
                logger.debug("subdir {} ", listFiles);
                toProcess.addAll(listFiles);
            }
            
            if (file instanceof TdarFile) {
                for (TdarFile part : ((TdarFile) file).getParts()) {
                    part.setAccount(account);
                    allFiles.add(part);
                }
            }
            file.setAccount(account);
            allFiles.add(file);
        }
        genericDao.saveOrUpdate(allFiles);
        return allFiles;
    }
    
    @Override
    @Transactional(readOnly=false)
    public void renameDirectory(TdarDir file, BillingAccount account, String name, TdarUser authenticatedUser) throws FileAlreadyExistsException {
        List<AbstractFile> listFiles = listFiles(file.getParent(), account, null, null, authenticatedUser);
        for (AbstractFile f : listFiles) {
            if (f instanceof TdarDir && StringUtils.equalsIgnoreCase(f.getName(), name)) {
                throw new FileAlreadyExistsException(name);
            }
        }
        file.setInternalName(name);
        file.setFilename(name);
        genericDao.saveOrUpdate(file);
        
    }
    
    @Override
    @Transactional(readOnly=true)
    public DirSummary summarizeAccountBy(BillingAccount account, Date date, TdarUser authenticatedUser) {
        List<Object[]> resultList = fileProcessingDao.summerizeByAccount(account, date, authenticatedUser);

        List<TdarDir> dirs = fileProcessingDao.listDirectoriesFor(null, account, authenticatedUser);
        Map<Long, TdarDir> dirIdMap = PersistableUtils.createIdMap(dirs);
        Map<Long, Set<Long>> dirChildMap = new HashMap<>();
        Set<Long> topLevel = new HashSet<>();

        // get the structure of the hierarchy tree
        buildChildMapAndTopLevelNodes(dirs, dirChildMap, topLevel);
        DirSummary summary = new DirSummary();

        // setup each dirSummaryPart
        Map<Long, DirSummaryPart> partMap = new HashMap<>();
        for (Object[] row : resultList) {
            DirSummaryPart part = summary.addPart(row);
            setupPart(dirIdMap, partMap, part);
            logger.debug("{}, {}", part.getDirPath(),  row);
        }
        // there may be parts that are mid-level directories that are empty, we don't add them, but they should be in the parts array 

        summarizeChildren(dirChildMap, topLevel, partMap, dirIdMap);

        summary.getParts().addAll(partMap.values());
        return summary;
    }
    
    @Override
    @Transactional(readOnly=true)
    public RecentFileSummary recentByAccount(BillingAccount account, Date dateStart, Date dateEnd, TdarDir dir, TdarUser authenticatedUser) {
        return fileProcessingDao.recentByAccount(account, dateStart, dateEnd, dir, authenticatedUser);
        
    }
    

    private void setupPart(Map<Long, TdarDir> dirIdMap, Map<Long, DirSummaryPart> partMap, DirSummaryPart part) {
        part.setDir(dirIdMap.get(part.getId()));
        part.setDirPath(buildDirTree(part));
        partMap.put(part.getId(), part);
    }

    /**
     * Find all of the children of a given node, also find "top" level nodes
     * @param dirs
     * @param dirChildMap
     * @param topLevel
     */
    private void buildChildMapAndTopLevelNodes(List<TdarDir> dirs, Map<Long, Set<Long>> dirChildMap, Set<Long> topLevel) {
        for (TdarDir dir : dirs) {
            if (dir.getParentId() == null) {
                topLevel.add(dir.getId());
                continue;
            }
            TdarDir dir_ = dir;
            while (dir_ != null) {
                Long parentId = dir_.getParentId();
                Set<Long> children = dirChildMap.getOrDefault(parentId, new HashSet<>());
                children.add(dir_.getId());
                dirChildMap.put(parentId, children);
                dir_ = dir_.getParent();
            }
        }
    }

    /**
     * Recursively summarize all children from top down so we don't double count
     * 
     * @param dirChildMap
     * @param topLevel
     * @param partMap
     */
    private void summarizeChildren(Map<Long, Set<Long>> dirChildMap, Set<Long> topLevel, Map<Long, DirSummaryPart> partMap, Map<Long,TdarDir> dirMap) {
        for (Long id : topLevel) {
            Set<Long> working = new HashSet<>(dirChildMap.getOrDefault((id), new HashSet<>()));
            Set<Long> allChildren = new HashSet<>(working);
            while (!working.isEmpty()) {
                Iterator<Long> iterator = working.iterator();
                Long next = iterator.next();
                iterator.remove();

                Set<Long> nextChild = dirChildMap.get(next);
                if (CollectionUtils.isNotEmpty(nextChild)) {
                    working.addAll(nextChild);
                    allChildren.addAll(nextChild);
                }
            }
            DirSummaryPart part = partMap.get(id);
            if (part == null) {
                logger.debug("creating part {}",  id);
                logger.debug(" chilren      {}",  dirChildMap.get(id));
                
                part = new DirSummaryPart(null);
                part.setId(id);
                setupPart(dirMap, partMap, part);
            }
            
            part.addAll(allChildren, partMap);
            if (CollectionUtils.isNotEmpty(dirChildMap.get(id))) {
                summarizeChildren(dirChildMap, dirChildMap.get(id), partMap, dirMap);
            }
        }
    }

    private String buildDirTree(DirSummaryPart part) {
        TdarDir parent = part.getDir();
        if (parent == null) {
            return "/";
        }
        StringBuilder path = new StringBuilder();
        TdarDir d = parent;
        while (d != null) {
            path.insert(0, "/" + d.getName());
            d = d.getParent();
        }
        return path.toString();
    }

}
