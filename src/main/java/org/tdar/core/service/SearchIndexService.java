package org.tdar.core.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.hibernate.CacheMode;
import org.hibernate.FlushMode;
import org.hibernate.ScrollableResults;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.SearchFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tdar.core.bean.AsyncUpdateReceiver;
import org.tdar.core.bean.Indexable;
import org.tdar.core.bean.collection.ResourceCollection;
import org.tdar.core.bean.collection.ResourceCollection.CollectionType;
import org.tdar.core.bean.entity.Person;
import org.tdar.core.bean.resource.InformationResource;
import org.tdar.core.bean.resource.Project;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.bean.resource.Status;
import org.tdar.core.configuration.TdarConfiguration;
import org.tdar.core.dao.GenericDao;
import org.tdar.core.dao.HibernateSearchDao;
import org.tdar.core.dao.resource.DatasetDao;
import org.tdar.core.dao.resource.ProjectDao;
import org.tdar.search.index.LookupSource;
import org.tdar.utils.activity.Activity;

@Service
@Transactional(readOnly = true)
public class SearchIndexService {

    private final Logger logger = LoggerFactory.getLogger(SearchIndexService.class);

    @Autowired
    private HibernateSearchDao hibernateSearchDao;

    @Autowired
    private GenericDao genericDao;

    @Autowired
    private DatasetDao datasetDao;

    @Autowired
    private ResourceCollectionService resourceCollectionService;

    @Autowired
    private ProjectDao projectDao;

    private static final int FLUSH_EVERY = TdarConfiguration.getInstance().getIndexerFlushSize();

    private static final int INDEXER_BATCH_SIZE_TO_LOAD_OBJECTS = 50;
    private static final int INDEXER_THREADS_FOR_SUBSEQUENT_FETCHING = 5;
    private static final int INDEXER_THREADS_TO_LOAD_OBJECTS = 5;

    public static final String BUILD_LUCENE_INDEX_ACTIVITY_NAME = "Build Lucene Search Index";

    public void indexAll(AsyncUpdateReceiver updateReceiver, Person person) {
        indexAll(updateReceiver, getDefaultClassesToIndex(), person);
    }

    /**
     * The default classes to reindex
     * 
     * @return
     */
    public List<Class<? extends Indexable>> getDefaultClassesToIndex() {
        return getClassesToReindex(Arrays.asList(LookupSource.values()));
    }

    public List<Class<? extends Indexable>> getClassesToReindex(List<LookupSource> values) {
        List<Class<? extends Indexable>> toReindex = new ArrayList<>();
        for (LookupSource source : values) {
            switch (source) {
                case RESOURCE:
                    toReindex.add(Resource.class);
                    break;
                case PERSON:
                    toReindex.add(Person.class);
                    break;
                default:
                    toReindex.addAll(Arrays.asList(source.getClasses()));
            }
        }
        return toReindex;
    }

    /**
     * Index all of the @link Indexable items. Uses a ScrollableResult to manage memory and object complexity
     * 
     * @param updateReceiver
     * @param classesToIndex
     * @param person
     */
    @SuppressWarnings("deprecation")
    public void indexAll(AsyncUpdateReceiver updateReceiver, List<Class<? extends Indexable>> classesToIndex, Person person) {
        if (updateReceiver == null) {
            updateReceiver = getDefaultUpdateReceiver();
        }
        Activity activity = new Activity();
        activity.setName(BUILD_LUCENE_INDEX_ACTIVITY_NAME);
        activity.setIndexingActivity(true);
        activity.setUser(person);
        activity.setMessage(String.format("reindexing %s", StringUtils.join(classesToIndex, ", ")));
        activity.start();
        ActivityManager.getInstance().addActivityToQueue(activity);

        try {
            genericDao.synchronize();
            FullTextSession fullTextSession = getFullTextSession();
            FlushMode previousFlushMode = fullTextSession.getFlushMode();
            fullTextSession.setFlushMode(FlushMode.MANUAL);
            fullTextSession.setCacheMode(CacheMode.IGNORE);
            SearchFactory sf = fullTextSession.getSearchFactory();
            float percent = 0f;
            updateAllStatuses(updateReceiver, activity, "initializing...", 0f);
            float maxPer = (1f / classesToIndex.size()) * 100f;
            for (Class<?> toIndex : classesToIndex) {
                fullTextSession.purgeAll(toIndex);
                sf.optimize(toIndex);
                Number total = genericDao.count(toIndex);
                ScrollableResults scrollableResults = genericDao.findAllScrollable(toIndex);
                String message = total + " " + toIndex.getSimpleName() + "(s) to be indexed";
                updateAllStatuses(updateReceiver, activity, message, 0f);
                int divisor = getDivisor(total);
                float currentProgress = 0f;
                int numProcessed = 0;
                String MIDDLE = " of " + total.intValue() + " " + toIndex.getSimpleName() + "(s) ";

                while (scrollableResults.next()) {
                    Object item = scrollableResults.get(0);
                    currentProgress = numProcessed / total.floatValue();
                    index(fullTextSession, item);
                    numProcessed++;
                    float totalProgress = ((currentProgress * maxPer) + percent);
                    if ((numProcessed % divisor) == 0) {
                        message = "indexed " + numProcessed + MIDDLE + totalProgress + "%";
                        updateAllStatuses(updateReceiver, activity, message, totalProgress);
                    }
                    if ((numProcessed % FLUSH_EVERY) == 0) {
                        message = "indexed " + numProcessed + MIDDLE + totalProgress + "% ... (flushing)";
                        updateAllStatuses(updateReceiver, activity, message, totalProgress);
                        logger.trace("flushing search index");
                        fullTextSession.flushToIndexes();
                        fullTextSession.clear();
                        logger.trace("flushed search index");
                    }
                }
                scrollableResults.close();
                fullTextSession.flushToIndexes();
                fullTextSession.clear();
                percent += maxPer;
                message = "finished indexing all " + toIndex.getSimpleName() + "(s).";
                updateAllStatuses(updateReceiver, activity, message, percent);
            }

            fullTextSession.flushToIndexes();
            fullTextSession.clear();
            updateAllStatuses(updateReceiver, activity, "index all complete", 100f);
            fullTextSession.setFlushMode(previousFlushMode);
            activity.end();
        } catch (Throwable ex) {
            logger.warn("exception: {}", ex);
            if (updateReceiver != null) {
                updateReceiver.addError(ex);
            }
        }
        activity.end();
    }

    private void updateAllStatuses(AsyncUpdateReceiver updateReceiver, Activity activity, String status, float complete) {
        if (updateReceiver != null) {
            updateReceiver.setPercentComplete(complete);
            updateReceiver.setStatus(status);
        }
        activity.setMessage(status);
        activity.setPercentDone(complete);
        logger.debug("status: {} [{}%]", status, complete);
    }

    /**
     * Index an item of some sort.
     * 
     * @param fullTextSession
     * @param item
     */
    private void index(FullTextSession fullTextSession, Object item) {
        if (item instanceof InformationResource) {
            datasetDao.assignMappedDataForInformationResource(((InformationResource) item));
        }

        if (item instanceof Project) {
            Project project = (Project) item;
            if (CollectionUtils.isEmpty(project.getCachedInformationResources())) {
                projectDao.findAllResourcesInProject(project, Status.ACTIVE, Status.DRAFT);
            }
        }
        fullTextSession.index(item);
    }

    /**
     * Reindex a set of @link ResourceCollection Entries and their subtrees to update rights and permissions
     * 
     * @param collectionToReindex
     */
    public void indexAllResourcesInCollectionSubTree(ResourceCollection collectionToReindex) {
        logger.info("indexing collection async");
        List<ResourceCollection> collections = resourceCollectionService.buildCollectionTreeForController(collectionToReindex, null, CollectionType.SHARED);
        collections.add(collectionToReindex);
        Set<Resource> resources = new HashSet<Resource>();
        for (ResourceCollection collection : collections) {
            resources.addAll(collection.getResources());
        }

        indexCollection(resources);
    }

    /**
     * Reindex a set of @link ResourceCollection Entries and their subtrees to update rights and permissions
     * 
     * @param collectionToReindex
     */
    @Async
    public void indexAllResourcesInCollectionSubTreeAsync(final ResourceCollection collectionToReindex) {
        indexAllResourcesInCollectionSubTree(collectionToReindex);
    }

    /**
     * @see #indexCollection(Collection)
     * @param collectionToReindex
     */
    @Async
    public <C extends Indexable> void indexCollectionAsync(final Collection<C> collectionToReindex) {
        indexCollection(collectionToReindex);
    }

    /**
     * help's calcualate the percentage complete
     * 
     * @param total
     * @return
     */
    public int getDivisor(Number total) {
        int divisor = 5;
        if (total.intValue() < 50) {
            divisor = 2;
        } else if (total.intValue() < 100) {
            divisor = 20;
        } else if (total.intValue() < 1000) {
            divisor = 50;
        } else if (total.intValue() < 10000) {
            divisor = 500;
        } else {
            divisor = 5000;
        }
        return divisor;
    }

    /**
     * @see #indexCollection(Collection)
     * @param indexable
     */
    @SuppressWarnings("unchecked")
    public <C extends Indexable> void index(C... indexable) {
        indexCollection(Arrays.asList(indexable));
    }

    /**
     * Index a collection of @link Indexable entities
     * 
     * @param indexable
     */
    public <C extends Indexable> boolean indexCollection(Collection<C> indexable) {
        boolean exceptions = false;
        if (indexable != null) {
            logger.debug("manual indexing ... {}", indexable.size());
            FullTextSession fullTextSession = getFullTextSession();

            for (C toIndex : indexable) {
                logger.debug("indexing: {}", toIndex);
                try {
                    // if we were called via async, the objects will belong to managed by the current hib session.
                    // purge them from the session and merge w/ transient object to get it back on the session before indexing.
                    fullTextSession.purge(toIndex.getClass(), toIndex.getId());
                    index(fullTextSession, genericDao.merge(toIndex));
                } catch (Exception e) {
                    logger.error("exception in indexing, {} [{}]", toIndex, e);
                    logger.error(String.format("%s %s", ExceptionUtils.getRootCauseMessage(e), Arrays.asList(ExceptionUtils.getRootCauseStackTrace(e))),
                            ExceptionUtils.getRootCause(e));
                    exceptions = true;
                }
            }
            fullTextSession.flushToIndexes();
        }
        return exceptions;
    }

    /**
     * Similar to @link GenericService.synchronize() forces all pending indexing actions to be written.
     * 
     * Should only be used in tests...
     * 
     */
    @Deprecated
    public void flushToIndexes() {
        getFullTextSession().flushToIndexes();
    }

    /**
     * Index/Reindex everything. Requested by the @link Person
     * 
     * @param person
     */
    public void indexAll(Person person) {
        indexAll(getDefaultUpdateReceiver(), getDefaultClassesToIndex(), person);
    }

    /**
     * Index all items of the Specified Class; person is the person requesting the index
     * 
     * @param person
     * @param classes
     */
    @SuppressWarnings("unchecked")
    public void indexAll(Person person, Class<? extends Indexable>... classes) {
        indexAll(getDefaultUpdateReceiver(), Arrays.asList(classes), person);
    }

    /**
     * The AsyncUpdateReciever allows us to pass data about the indexing back to the requester. The default one does nothing.
     * 
     * @return
     */
    private AsyncUpdateReceiver getDefaultUpdateReceiver() {
        return AsyncUpdateReceiver.DEFAULT_RECEIVER;
    }

    /**
     * Maintained for reference, we have not used this since Azmiuth as it has issues with Lazy references
     * 
     * Warning, this type of indexing does not use lazy fetching, which as of the current build is causing exceptions
     * 
     * @param classes
     */
    public void massIndex(Class<?>... classes) {
        try {
            getFullTextSession().createIndexer(classes).purgeAllOnStart(true).batchSizeToLoadObjects(INDEXER_BATCH_SIZE_TO_LOAD_OBJECTS)
                    .cacheMode(CacheMode.IGNORE).threadsToLoadObjects(INDEXER_THREADS_TO_LOAD_OBJECTS)
                    .threadsForSubsequentFetching(INDEXER_THREADS_FOR_SUBSEQUENT_FETCHING).startAndWait();
        } catch (InterruptedException e) {
            logger.error("index failed", e);
        }
    }

    /**
     * Exposes the FullTextSession (HibernateSearch's interface to Lucene)
     * 
     * @return
     */
    private FullTextSession getFullTextSession() {
        return Search.getFullTextSession(hibernateSearchDao.getFullTextSession());
    }

    /**
     * Wipe everything from the index
     * 
     */
    public void purgeAll() {
        purgeAll(getDefaultClassesToIndex());
    }

    /**
     * Purge all objects of the specified Class frmo the index
     * 
     * @param classes
     */
    public void purgeAll(List<Class<? extends Indexable>> classes) {
        FullTextSession fullTextSession = getFullTextSession();
        for (Class<?> clss : classes) {
            fullTextSession.purgeAll(clss);
        }
    }

    /**
     * Optimizes all lucene indexes
     * 
     */
    public void optimizeAll() {
        FullTextSession fullTextSession = getFullTextSession();
        SearchFactory sf = fullTextSession.getSearchFactory();
        for (Class<?> toIndex : getDefaultClassesToIndex()) {
            sf.optimize(toIndex);
            logger.info("optimizing {}", toIndex.getSimpleName());
        }
    }

    /**
     * Indexes a @link Project and it's contents. It loads the project's child @link Resource entries before indexing
     * 
     * @param project
     */
    public boolean indexProject(Project project) {
        project.setCachedInformationResources(new HashSet<InformationResource>(projectDao.findAllResourcesInProject(project, Status.ACTIVE, Status.DRAFT)));
        project.setReadyToIndex(true);
        index(project);
        logger.debug("reindexing project contents");
        boolean exceptions = indexCollection(project.getCachedInformationResources());
        logger.debug("completed reindexing project contents");
        return exceptions;
    }

    /**
     * @see #indexProject(Project)
     * @param project
     */
    @Async
    public void indexProjectAsync(final Project project) {
        indexProject(project);
    }

    @Transactional(readOnly = true)
    public boolean indexProject(Long id) {
        return indexProject(genericDao.find(Project.class, id));
    }

    @Async
    public void indexAllAsync(final AsyncUpdateReceiver reciever, final List<Class<? extends Indexable>> toReindex, final Person person) {
        logger.info("reindexing indexall");
        indexAll(reciever, toReindex, person);
    }
}
