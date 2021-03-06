package org.tdar.core.bean;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.hibernate.Cache;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.tdar.AbstractSimpleIntegrationTest;
import org.tdar.TestConstants;
import org.tdar.configuration.TdarConfiguration;
import org.tdar.core.bean.collection.CollectionDisplayProperties;
import org.tdar.core.bean.collection.ResourceCollection;
import org.tdar.core.bean.entity.AuthorizedUser;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.entity.permissions.Permissions;
import org.tdar.core.bean.notification.Email;
import org.tdar.core.bean.resource.Dataset;
import org.tdar.core.bean.resource.Document;
import org.tdar.core.bean.resource.InformationResource;
import org.tdar.core.bean.resource.Project;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.bean.resource.ResourceType;
import org.tdar.core.bean.resource.file.FileAccessRestriction;
import org.tdar.core.bean.resource.file.FileAction;
import org.tdar.core.bean.resource.file.InformationResourceFile;
import org.tdar.core.bean.resource.file.InformationResourceFileVersion;
import org.tdar.core.configuration.TdarAppConfiguration;
import org.tdar.core.dao.entity.AuthorizedUserDao;
import org.tdar.core.exception.TdarValidationException;
import org.tdar.core.service.BookmarkedResourceService;
import org.tdar.core.service.EntityService;
import org.tdar.core.service.ErrorTransferObject;
import org.tdar.core.service.GenericService;
import org.tdar.core.service.PersonalFilestoreService;
import org.tdar.core.service.SerializationService;
import org.tdar.core.service.UrlService;
import org.tdar.core.service.collection.ResourceCollectionService;
import org.tdar.core.service.email.AwsEmailSender;
import org.tdar.core.service.email.MockAwsEmailSenderServiceImpl;
import org.tdar.core.service.external.AuthenticationService;
import org.tdar.core.service.external.AuthorizationService;
import org.tdar.core.service.external.EmailServiceImpl;
import org.tdar.core.service.external.session.SessionData;
import org.tdar.core.service.integration.DataIntegrationService;
import org.tdar.core.service.processes.SendEmailProcess;
import org.tdar.core.service.resource.DataTableService;
import org.tdar.core.service.resource.DatasetService;
import org.tdar.core.service.resource.InformationResourceService;
import org.tdar.core.service.resource.ProjectService;
import org.tdar.core.service.resource.ResourceService;
import org.tdar.exception.TdarRecoverableRuntimeException;
import org.tdar.filestore.FileStoreFile;
import org.tdar.filestore.Filestore;
import org.tdar.filestore.FilestoreObjectType;
import org.tdar.filestore.VersionType;
import org.tdar.utils.EmailStatisticsHelper;
import org.tdar.utils.MessageHelper;
import org.tdar.utils.PersistableUtils;
import org.tdar.utils.StatsChartGenerator;
import org.tdar.utils.TestConfiguration;

// 
@ContextConfiguration(classes = TdarAppConfiguration.class)
@SuppressWarnings("rawtypes")
@ActiveProfiles(profiles = { "test" })
public abstract class AbstractIntegrationTestCase extends AbstractSimpleIntegrationTest implements TestEntityHelper {

    
    @Autowired
    private AwsEmailSender awsEmailService;


    public static final String SPITAL_DB_NAME = TestConstants.SPITAL_DB_NAME;
    protected static final String PATH = TestConstants.TEST_DATA_INTEGRATION_DIR;

    @Autowired
    protected SessionFactory sessionFactory;
    @Autowired
    protected ProjectService projectService;
    @Autowired
    protected DatasetService datasetService;
    @Autowired
    protected DataTableService dataTableService;
    @Autowired
    protected DataIntegrationService dataIntegrationService;
    @Autowired
    protected GenericService genericService;
    @Autowired
    protected UrlService urlService;
    @Autowired
    protected ResourceService resourceService;
    @Autowired
    protected EntityService entityService;
    @Autowired
    protected InformationResourceService informationResourceService;
    @Autowired
    protected BookmarkedResourceService bookmarkedResourceService;
    @Autowired
    protected PersonalFilestoreService filestoreService;
    @Autowired
    protected AuthorizationService authenticationAndAuthorizationService;
    @Autowired
    protected AuthenticationService authenticationService;
    @Autowired
    private SerializationService serializationService;
    @Autowired
    protected ResourceCollectionService resourceCollectionService;
    @Autowired
    private AuthorizedUserDao authorizedUserDao;

    @Autowired
    public SendEmailProcess sendEmailProcess;

    @Autowired
    protected EmailServiceImpl emailService;

    @Autowired
    protected EmailStatisticsHelper emailStatsHelper;

    @Autowired
    protected StatsChartGenerator chartGenerator;

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private SessionData sessionData;

    public AbstractIntegrationTestCase() {
        // making sure all test-index data ends up in target
        System.setProperty("solr.data.dir", "target/junit-solr/");
    }

    @Rule
    public TestName testName = new TestName();

    @Rule
    public TestWatcher failWatcher = new TestWatcher() {

        @Override
        protected void failed(Throwable e, Description description) {
            AbstractIntegrationTestCase.this.onFail(e, description);
        }
    };

    @Before
    public void setupTest() {
        genericService.delete(genericService.findAll(Email.class));
        sendEmailProcess.setAllIds(null);
        if (awsEmailService instanceof MockAwsEmailSenderServiceImpl) {
            ((MockAwsEmailSenderServiceImpl) awsEmailService).getMessages().clear();
        }
        if (TdarConfiguration.getInstance().shouldLogToFilestore()) {
            serializationService.setUseTransactionalEvents(false);
        }

    }

    public String getTestFilePath() {
        return PATH;
    }

    // Called when your test fails. Did I say "when"? I meant "if".
    public void onFail(Throwable e, Description description) {
    }

    @Deprecated
    /*
     * deprecated, use generateInformationResourceWithFileAndUser() or generateInformationResourceWithUser() instead
     */
    public InformationResource generateInformationResourceWithFile() throws InstantiationException, IllegalAccessException, FileNotFoundException {
        Document ir = createAndSaveNewInformationResource(Document.class, true);
        assertTrue(ir.getResourceType() == ResourceType.DOCUMENT);
        File file = TestConstants.getFile(TestConstants.TEST_DOCUMENT_DIR, TestConstants.TEST_DOCUMENT_NAME);
        assertTrue("testing " + TestConstants.TEST_DOCUMENT_NAME + " exists", file.exists());
        ir = (Document) addFileToResource(ir, file);
        return ir;
    }

    public <R extends InformationResource> R generateAndStoreVersion(Class<R> type, String name, File f, Filestore filestore)
            throws InstantiationException,
            IllegalAccessException, IOException {
        R ir = createAndSaveNewInformationResource(type, false);
        InformationResourceFile irFile = new InformationResourceFile();
        irFile.setInformationResource(ir);
        irFile.setLatestVersion(1);
        irFile.setFilename(name);
        @SuppressWarnings("deprecation")
        InformationResourceFileVersion version = new InformationResourceFileVersion();
        version.setVersion(1);
        version.setFilename(name);
        version.setExtension(FilenameUtils.getExtension(name));
        version.setInformationResourceFile(irFile);
        version.setDateCreated(new Date());
        version.setInformationResourceFile(irFile);
        version.setFileVersionType(VersionType.UPLOADED);
        irFile.getInformationResourceFileVersions().add(version);
        ir.getInformationResourceFiles().add(irFile);
        genericService.save(irFile);
        genericService.save(version);
        filestore.store(FilestoreObjectType.RESOURCE, f, version);
        return ir;
    }

    public Document createAndSaveDocumentWithFileAndUseDefaultUser() throws InstantiationException, IllegalAccessException, FileNotFoundException {
        Document ir = createAndSaveNewInformationResource(Document.class, false);
        assertTrue(ir.getResourceType() == ResourceType.DOCUMENT);
        File file = TestConstants.getFile(TestConstants.TEST_DOCUMENT_DIR, TestConstants.TEST_DOCUMENT_NAME);
        assertTrue("testing " + TestConstants.TEST_DOCUMENT_NAME + " exists", file.exists());
        ir = (Document) addFileToResource(ir, file);
        return ir;
    }

    public Document generateDocumentAndUseDefaultUser() throws InstantiationException, IllegalAccessException {
        Document ir = createAndSaveNewInformationResource(Document.class, false);
        return ir;
    }

    public Document generateDocumentWithFileAndUser() throws InstantiationException, IllegalAccessException, FileNotFoundException {
        Document ir = createAndSaveNewInformationResource(Document.class, true);
        assertTrue(ir.getResourceType() == ResourceType.DOCUMENT);
        File file = TestConstants.getFile(TestConstants.TEST_DOCUMENT_DIR, TestConstants.TEST_DOCUMENT_NAME);
        assertTrue("testing " + TestConstants.TEST_DOCUMENT_NAME + " exists", file.exists());
        ir = (Document) addFileToResource(ir, file);
        return ir;
    }

    public Document generateDocumentWithUser() throws InstantiationException, IllegalAccessException {
        Document ir = createAndSaveNewInformationResource(Document.class, false);
        assertTrue(ir.getResourceType() == ResourceType.DOCUMENT);
        return ir;
    }

    @Transactional
    public <R extends InformationResource> R addFileToResource(R ir, File file) {
        return addFileToResource(ir, file, FileAccessRestriction.PUBLIC);
    }

    public <R extends InformationResource> R addFileToResource(R ir, File file, FileAccessRestriction restriction) {
        try {
            FileProxy proxy = new FileProxy(file.getName(), file, VersionType.UPLOADED, FileAction.ADD);
            proxy.setRestriction(restriction);
            // PersonalFilestore filestore, T resource, List<FileProxy> fileProxiesToProcess, Long ticketId
            ErrorTransferObject listener = informationResourceService.importFileProxiesAndProcessThroughWorkflow(ir, null, null, Arrays.asList(proxy));
            if (CollectionUtils.isNotEmpty(listener.getActionErrors())) {
                throw new TdarRecoverableRuntimeException(String.format("errors ocurred while processing file: %s", listener));
            }
            // informationResourceService.addOrReplaceInformationResourceFile(ir, new FileInputStream(file), file.getName(), FileAction.ADD,
            // VersionType.UPLOADED);
            evictCache();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        genericService.refresh(ir);// = genericService.find(ir.getClass(), ir.getId());
        for (InformationResourceFile irf : ir.getInformationResourceFiles()) {
            assertTrue(irf.getId() != null);
            for (InformationResourceFileVersion irfv : irf.getInformationResourceFileVersions()) {
                assertTrue(irfv.getId() != null);
            }
        }
        return ir;
    }

    public <R extends InformationResource> R replaceFileOnResource(R ir, File file, InformationResourceFile oldFile) {
        try {
            FileProxy proxy = new FileProxy(file.getName(), file, VersionType.UPLOADED, FileAction.REPLACE);
            proxy.setFileId(oldFile.getId());
            // PersonalFilestore filestore, T resource, List<FileProxy> fileProxiesToProcess, Long ticketId
            ErrorTransferObject listener = informationResourceService.importFileProxiesAndProcessThroughWorkflow(ir, null, null, Arrays.asList(proxy));
            if (CollectionUtils.isNotEmpty(listener.getActionErrors())) {
                throw new TdarRecoverableRuntimeException(String.format("errors ocurred while processing file: %s", listener));
            }
            // informationResourceService.addOrReplaceInformationResourceFile(ir, new FileInputStream(file), file.getName(), FileAction.ADD,
            // VersionType.UPLOADED);
            evictCache();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        genericService.refresh(ir);// = genericService.find(ir.getClass(), ir.getId());

        return ir;
    }

    public <R extends InformationResource> R createAndSaveNewInformationResource(Class<R> cls) {
        return createAndSaveNewInformationResource(cls, false);
    }

    protected <R extends InformationResource> R createAndSaveNewInformationResource(Class<R> cls, boolean createUser) {
        TdarUser submitter = getUser();
        if (createUser) {
            submitter = createAndSaveNewPerson("test@user.com", "");
        }
        return createAndSaveNewInformationResource(cls, submitter);
    }

    public <R extends InformationResource> R createAndSaveNewInformationResource(Class<R> cls, TdarUser persistentPerson) {
        return createAndSaveNewInformationResource(cls, persistentPerson, "TEST TITLE");
    }

    @Transactional
    public <R extends InformationResource> R createAndSaveNewInformationResource(Class<R> cls, TdarUser persistentPerson, String resourceTitle) {
        return createAndSaveNewInformationResource(cls, null, persistentPerson, resourceTitle);
    }

    public <R extends InformationResource> R createAndSaveNewInformationResource(Class<R> cls, Project project, TdarUser persistentPerson,
            String resourceTitle) {
        R iResource = createAndSaveNewResource(cls, persistentPerson, resourceTitle);
        iResource.setDescription("test description");
        iResource.setProject(project);
        iResource.setDate(2012);
        if (TdarConfiguration.getInstance().getCopyrightMandatory()) {
            iResource.setCopyrightHolder(persistentPerson);
        }
        // iResource.getAuthorizedUsers().add(new AuthorizedUser(persistentPerson, persistentPerson, GeneralPermissions.MODIFY_RECORD));
        genericService.saveOrUpdate(iResource);
        genericService.saveOrUpdate(iResource.getAuthorizedUsers());
        return iResource;
    }

    protected Dataset createAndSaveNewDataset() {
        String title = "Test dataset";
        Dataset dataset = createAndSaveNewInformationResource(Dataset.class, null, getUser(), title);
        dataset.setDescription("Test dataset description");
        dataset.setDate(1999);
        genericService.saveOrUpdate(dataset);
        genericService.saveOrUpdate(dataset.getAuthorizedUsers());
        return dataset;
    }

    protected Project createAndSaveNewProject(String title) {
        return createAndSaveNewResource(Project.class, getUser(), title);
    }

    public <R extends Resource> R createAndSaveNewResource(Class<R> cls, TdarUser persistentPerson, String resourceTitle) {
        R resource = null;
        try {
            resource = cls.newInstance();
            resource.markUpdated(persistentPerson);
            resource.setTitle(resourceTitle);
            resource.setDescription("description for " + resourceTitle);
            if (resource instanceof InformationResource) {
                ((InformationResource) resource).setDate(2012);
            }
            resource.getAuthorizedUsers().add(new AuthorizedUser(persistentPerson, persistentPerson, Permissions.MODIFY_RECORD));
            genericService.save(resource);
            genericService.save(resource.getAuthorizedUsers());
        } catch (Exception e) {
            logger.error("failed: ", e);
            Assert.fail("failed to create/save test" + cls.getSimpleName() + " record: " + e.getMessage() + " \n " + ExceptionUtils.getFullStackTrace(e));
        }

        return resource;
    }

    public <R extends Resource> R createAndSaveNewResource(Class<R> cls) {
        TdarUser persistentPerson = (TdarUser) entityService.findByEmail("test@user.com");
        if (persistentPerson == null) {
            persistentPerson = createAndSaveNewPerson("test@user.com", "");
        }
        String resourceTitle = "Sample " + cls.getSimpleName() + " record";
        return createAndSaveNewResource(cls, persistentPerson, resourceTitle);
    }

    // create new, public, collection with the getUser() as the owner and no resources
    public ResourceCollection createAndSaveNewResourceCollection(String name) {
        return init(new ResourceCollection(), name);
    }

    public ResourceCollection createAndSaveNewWhiteLabelCollection(String name) {
        ResourceCollection wlc = new ResourceCollection();
        wlc.setProperties(new CollectionDisplayProperties(false, false, false, false, false, false, false));
        wlc.getProperties().setWhitelabel(true);
        wlc.getProperties().setSubtitle("This is a fancy whitelabel collection");
        init(wlc, name);
        return wlc;
    }

    protected <C extends ResourceCollection> C init(C resourceCollection, String name) {
        resourceCollection.setName(name);
        resourceCollection.setDescription(name);
        resourceCollection.setViewable(true);
        resourceCollection.setHidden(false);
        resourceCollection.markUpdated(getUser());
        resourceCollection.setOwner(getUser());
        resourceCollection.getAuthorizedUsers().add(new AuthorizedUser(getUser(), getUser(), Permissions.ADMINISTER_COLLECTION));
        genericService.saveOrUpdate(resourceCollection);
        genericService.saveOrUpdate(resourceCollection.getAuthorizedUsers());
        return resourceCollection;
    }

    @Override
    @Autowired
    @Qualifier("tdarMetadataDataSource")
    public void setDataSource(DataSource dataSource) {
        super.setDataSource(dataSource);
    }

    @Autowired
    @Qualifier("genericService")
    public void setGenericService(GenericService genericService) {
        this.genericService = genericService;
    }

    public Logger getLogger() {
        return logger;
    }

    public SessionData getSessionData() {
        if (sessionData == null) {
            this.sessionData = new SessionData();
        }
        return sessionData;
    }

    protected <T> List<T> createListWithSingleNull() {
        ArrayList<T> list = new ArrayList<T>();
        list.add(null);
        return list;
    }

    public TdarUser getUser() {
        return getUser(getUserId());
    }

    protected Long getUserId() {
        return TestConfiguration.getInstance().getUserId();
    }

    protected final Long getBasicUserId() {
        return TestConfiguration.getInstance().getUserId();
    }

    protected final Long getBillingAdminUserId() {
        return TestConfiguration.getInstance().getBillingAdminUserId();
    }

    protected TdarUser getBasicUser() {
        return getUser(getBasicUserId());
    }

    protected TdarUser getEditorUser() {
        return getUser(getEditorUserId());
    }

    protected TdarUser getBillingUser() {
        return getUser(getBillingAdminUserId());
    }

    protected TdarUser getAdminUser() {
        return getUser(getAdminUserId());
    }

    protected TdarUser getUser(Long id) {
        TdarUser p = genericService.find(TdarUser.class, id);
        if (PersistableUtils.isNullOrTransient(p)) {
            fail("failed to load user:" + id);
        }
        genericService.refresh(p);
        Assert.assertNotNull(p.getEmail());
        // genericService.markWritableOnExistingSession(p);
        // logger.info("({}) {}",p.getEmail(),p);
        return p;
    }

    protected void flush() {
        Session session = sessionFactory.getCurrentSession();
        if (session != null) {
            session.flush();
            session.clear();
        }

        evictCache();

        // searchIndexService.flushToIndexes();
        Cache cache = sessionFactory.getCache();
        if (cache != null) {
            cache.evictAllRegions();
        }

    }

    @SuppressWarnings("deprecation")
    public void evictCache() {
        genericService.synchronize();
    }

    protected Long getAdminUserId() {
        return TestConfiguration.getInstance().getAdminUserId();
    }

    protected Long getEditorUserId() {
        return TestConfiguration.getInstance().getEditorUserId();
    }

    public void addAuthorizedUser(Resource resource, TdarUser person, Permissions permission) {
        AuthorizedUser authorizedUser = new AuthorizedUser(person, person, permission);
        // InternalCollection internalResourceCollection = resource.getInternalResourceCollection();
        // if (internalResourceCollection == null) {
        // internalResourceCollection = new InternalCollection();
        // internalResourceCollection.setOwner(person);
        // internalResourceCollection.markUpdated(person);
        // resource.getInternalCollections().add(internalResourceCollection);
        // genericService.save(internalResourceCollection);
        // }
        resource.getAuthorizedUsers().add(authorizedUser);
        // logger.debug("{}", internalResourceCollection);
        // genericService.saveOrUpdate(internalResourceCollection);
        genericService.saveOrUpdate(authorizedUser);
        genericService.saveOrUpdate(resource);
    }

    private TdarUser sessionUser;

    /**
     * @return
     */
    public TdarUser getSessionUser() {
        if (sessionUser != null) {
            return sessionUser;
        }
        return getUser();
    }

    public void setSessionUser(TdarUser user) {
        this.sessionUser = user;
    }

    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    public TdarConfiguration getTdarConfiguration() {
        return TdarConfiguration.getInstance();
    }

    /**
     * @return the dataTableService
     */
    public DataTableService getDataTableService() {
        return dataTableService;
    }

    /**
     * @return the dataIntegrationService
     */
    public DataIntegrationService getDataIntegrationService() {
        return dataIntegrationService;
    }

    public static void assertInvalid(Validatable address, String reason) {
        TdarValidationException tv = null;
        try {
            address.isValid();
        } catch (TdarValidationException ex) {
            tv = ex;
        }
        Assert.assertNotNull(tv);
        if (reason != null) {
            Assert.assertEquals(reason, tv.getLocalizedMessage());
        }
    }

    public static void assertNotEquals(Object obj1, Object obj2) {
        assertNotEquals("", obj1, obj2);
    }

    public static void assertNotEquals(String msg, Object obj1, Object obj2) {
        if (StringUtils.isNotBlank(msg)) {
            assertTrue(msg, ObjectUtils.notEqual(obj1, obj2));
        } else {
            assertTrue(String.format("'%s' == '%s'", obj1, obj2), ObjectUtils.notEqual(obj1, obj2));
        }
    }

    public static void assertNotEmpty(String message, Collection<?> results) {
        assertTrue(message, CollectionUtils.isNotEmpty(results));
    }

    public static void assertEmpty(String message, Collection<?> results) {
        assertTrue(message, CollectionUtils.isEmpty(results));
    }

    public Email checkMailAndGetLatest(String text) {
        sendEmailProcess.execute();
        sendEmailProcess.cleanup();
        List<Email> messages = ((MockAwsEmailSenderServiceImpl) awsEmailService).getMessages();
        logger.debug("{} messages ", messages.size());
        Email toReturn = null;
        for (Email msg : messages) {
            logger.debug("{} from:{} to:{}", msg.getSubject(), msg.getFrom(), msg.getTo());
            if (msg.getMessage().contains(text)) {
                toReturn = msg;
            }
        }
        assertTrue("should have a mail in our 'inbox'", messages.size() > 0);
        if (toReturn != null) {
            messages.remove(toReturn);
        }
        return toReturn;
    }

    public String getText(String msgKey) {
        String msg = MessageHelper.getMessage(msgKey);
        assertThat("key should not be same as getText(key) (did you forget to add it to tdar-messages?)", msgKey, is(not(msg)));
        return msg;
    }

    public AuthorizedUserDao getAuthorizedUserDao() {
        return authorizedUserDao;
    }

    public void setAuthorizedUserDao(AuthorizedUserDao authorizedUserDao) {
        this.authorizedUserDao = authorizedUserDao;
    }

    protected InformationResourceFileVersion makeFileVersion(File name, long id) throws IOException {
        long infoId = (long) (Math.random() * 10000);
        InformationResourceFileVersion version = new InformationResourceFileVersion(VersionType.UPLOADED, name.getName(), 1, infoId, 123L);
        version.setId(id);
        filestore.store(FilestoreObjectType.RESOURCE, name, version);
        version.setTransientFile(name);
        return version;
    }


    protected FileStoreFile makeFileStoreFile(File name, long id) throws IOException {
        long infoId = (long) (Math.random() * 10000);
        FileStoreFile version = new FileStoreFile(FilestoreObjectType.RESOURCE, VersionType.UPLOADED, name.getName(), 1, infoId, 123L, 1L);
        version.setId(id);
        filestore.store(FilestoreObjectType.RESOURCE, name, version);
        version.setTransientFile(name);
        return version;
    }

    @Override
    public GenericService getGenericService() {
        return genericService;
    }

    @Override
    public EntityService getEntityService() {
        return entityService;
    }

    public StatsChartGenerator getChartGenerator() {
        return chartGenerator;
    }

    public void setChartGenerator(StatsChartGenerator chartGenerator) {
        this.chartGenerator = chartGenerator;
    }
}
