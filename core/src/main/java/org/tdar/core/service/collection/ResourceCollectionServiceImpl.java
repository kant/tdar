/**
 * $Id$
 * 
 * @author $Author$
 * @version $Revision$
 */
package org.tdar.core.service.collection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.atlas.test.Gen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tdar.core.bean.collection.CollectionRevisionLog;
import org.tdar.core.bean.collection.HierarchicalCollection;
import org.tdar.core.bean.collection.ListCollection;
import org.tdar.core.bean.collection.ResourceCollection;
import org.tdar.core.bean.collection.SharedCollection;
import org.tdar.core.bean.entity.AuthorizedUser;
import org.tdar.core.bean.entity.Person;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.entity.UserInvite;
import org.tdar.core.bean.entity.permissions.GeneralPermissions;
import org.tdar.core.bean.resource.HasAuthorizedUsers;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.bean.resource.RevisionLogType;
import org.tdar.core.bean.resource.Status;
import org.tdar.core.bean.resource.UserRightsProxy;
import org.tdar.core.dao.SimpleFileProcessingDao;
import org.tdar.core.dao.external.auth.InternalTdarRights;
import org.tdar.core.dao.resource.ResourceCollectionDao;
import org.tdar.core.event.EventType;
import org.tdar.core.event.TdarEvent;
import org.tdar.core.exception.TdarAuthorizationException;
import org.tdar.core.exception.TdarRecoverableRuntimeException;
import org.tdar.core.service.CollectionSaveObject;
import org.tdar.core.service.DeleteIssue;
import org.tdar.core.service.RightsResolver;
import org.tdar.core.service.SerializationService;
import org.tdar.core.service.ServiceInterface;
import org.tdar.core.service.UserRightsProxyService;
import org.tdar.core.service.external.AuthorizationService;
import org.tdar.core.service.resource.ErrorHandling;
import org.tdar.transform.jsonld.SchemaOrgCollectionTransformer;
import org.tdar.utils.PersistableUtils;
import org.tdar.utils.TitleSortComparator;

import com.opensymphony.xwork2.TextProvider;

import ucar.nc2.ft.point.standard.plug.GempakCdm;

/**
 * @author Adam Brin
 * 
 */

@Service
public class ResourceCollectionServiceImpl  extends ServiceInterface.TypedDaoBase<ResourceCollection, ResourceCollectionDao> implements ResourceCollectionService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private transient AuthorizationService authorizationService;
    @Autowired
    private transient SimpleFileProcessingDao simpleFileProcessingDao;
    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private SerializationService serializationService;
    @Autowired
    private UserRightsProxyService userRightsProxyService;

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#saveAuthorizedUsersForResource(org.tdar.core.bean.resource.Resource, java.util.List, boolean, org.tdar.core.bean.entity.TdarUser)
     */
    @Override
    @Transactional
    public void saveAuthorizedUsersForResource(Resource resource, List<AuthorizedUser> authorizedUsers, boolean shouldSave, TdarUser actor) {
        logger.info("saving authorized users...");

        logger.trace("------------------------------------------------------");
        logger.debug("current users (start): {}", resource.getAuthorizedUsers());
        logger.debug("incoming authorized users (start): {}", authorizedUsers);

        CollectionRightsComparator comparator = new CollectionRightsComparator(resource.getAuthorizedUsers(), authorizedUsers);
        if (comparator.rightsDifferent()) {
            RightsResolver rco = authorizationService.getRightsResolverFor(resource, actor, InternalTdarRights.EDIT_ANYTHING);
            comparator.makeChanges(rco, resource, actor);
        }
        comparator = null;

        logger.debug("users after save: {}", resource.getAuthorizedUsers());
        if (shouldSave) {
            getDao().saveOrUpdate(resource);
        }
        logger.trace("------------------------------------------------------");

    }

//    private void handleDifferences(HasAuthorizedUsers resource, TdarUser actor, CollectionRightsComparator comparator, RightsResolver rco) {
//        if (CollectionUtils.isNotEmpty(comparator.getChanges())) {
//            Map<Long, AuthorizedUser> idMap2 = null;
//
//            Map<Long, AuthorizedUser> idMap = PersistableUtils.createIdMap(resource.getAuthorizedUsers());
//            for (AuthorizedUser user : comparator.getChanges()) {
//                AuthorizedUser actual = idMap.get(user.getId());
//                if (actual == null) {
//                    // it's possible that the authorizedUserId was not passed back from the client
//                    // if so, build a secondary map using the TdarUser (authorizedUser.user) id.
//                    if (idMap2 == null) {
//                        idMap2 = new HashMap<>();
//                        for (AuthorizedUser au : resource.getAuthorizedUsers()) {
//                            idMap2.put(au.getUser().getId(), au);
//                        }
//                    }
//
//                    actual = idMap2.get(user.getUser().getId());
//                    logger.debug("actual was null, now: {}", actual);
//                }
//                checkEscalation(actor, user, rco);
//                actual.setGeneralPermission(user.getGeneralPermission());
//                actual.setDateExpires(user.getDateExpires());
//            }
//        }
//    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#getAuthorizedUsersForResource(org.tdar.core.bean.resource.Resource, org.tdar.core.bean.entity.TdarUser)
     */
    @Override
    @Transactional(readOnly = true)
    public List<AuthorizedUser> getAuthorizedUsersForResource(Resource resource, TdarUser authenticatedUser) {
        List<AuthorizedUser> authorizedUsers = new ArrayList<>(resource.getAuthorizedUsers());
        boolean canModify = authorizationService.canUploadFiles(authenticatedUser, resource);
        applyTransientEnabledPermission(authenticatedUser, authorizedUsers, canModify);
        return authorizedUsers;
    }

    /**
     * Set the transient @link enabled boolean on a @link AuthorizedUser
     * 
     * Generally speaking, we use the enabled property to indicate to the UI whether removing the authorizedUser from the authorizedUser is "safe". An operation
     * is "safe" if it doesnt remove the permissions that enabled the user to modify the resource collection in the first place.
     * 
     * @param authenticatedUser
     * @param resourceCollection
     * @param canModify
     */
    private void applyTransientEnabledPermission(Person authenticatedUser, List<AuthorizedUser> authorizedUsers, boolean canModify) {
        for (AuthorizedUser au : authorizedUsers) {
            // enable if: permission is irrelevant (authuser is owner)
            // or if: user has modify permission but is not same as authuser
            au.setEnabled((canModify && !au.getUser().equals(authenticatedUser)));
        }
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findAllTopLevelCollections()
     */
    @Override
    @Transactional(readOnly = true)
    public <C extends ResourceCollection> List<C> findAllTopLevelCollections() {
        Set<C> resultSet = new HashSet<>();
        resultSet.addAll((List<C>) getDao().findCollectionsOfParent(null, false, SharedCollection.class));
        resultSet.addAll((List<C>) getDao().findCollectionsOfParent(null, false, ListCollection.class));
        List<C> toReturn = new ArrayList<>(resultSet);
        Collections.sort(toReturn, new Comparator<C>() {
            @Override
            public int compare(C o1, C o2) {
                return o1.getTitle().compareTo(o2.getTitle());
            }
        });
        return toReturn;
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findDirectChildCollections(java.lang.Long, java.lang.Boolean, java.lang.Class)
     */
    @Override
    @Transactional(readOnly = true)
    public <C extends HierarchicalCollection> List<C> findDirectChildCollections(Long id, Boolean hidden, Class<C> cls) {
        return getDao().findCollectionsOfParent(id, hidden, cls);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#delete(org.tdar.core.bean.collection.ResourceCollection)
     */
    @Override
    @Transactional
    public void delete(ResourceCollection resourceCollection) {
        getDao().delete(resourceCollection.getAuthorizedUsers());
        if (resourceCollection instanceof SharedCollection) {
            for (Resource resource : ((SharedCollection)resourceCollection).getResources()) {
                publisher.publishEvent(new TdarEvent(resource, EventType.CREATE_OR_UPDATE));
            }
        }
        getDao().delete(resourceCollection);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#saveAuthorizedUsersForResourceCollection(org.tdar.core.bean.resource.HasAuthorizedUsers, org.tdar.core.bean.collection.ResourceCollection, java.util.Collection, boolean, org.tdar.core.bean.entity.TdarUser, org.tdar.core.bean.resource.RevisionLogType)
     */
    @Override
    @Transactional(readOnly = false)
    public void saveAuthorizedUsersForResourceCollection(HasAuthorizedUsers source, ResourceCollection resourceCollection,
            Collection<AuthorizedUser> incomingUsers,
            boolean shouldSaveResource, TdarUser actor, RevisionLogType type) {
        if (resourceCollection == null) {
            throw new TdarRecoverableRuntimeException("resourceCollectionService.could_not_save");
        }
        logger.trace("------------------------------------------------------");
        logger.debug("current users (start): {}", resourceCollection.getAuthorizedUsers());
        logger.debug("incoming authorized users (start): {}", incomingUsers);

        RightsResolver rightsResolver = authorizationService.getRightsResolverFor(resourceCollection, actor, InternalTdarRights.EDIT_ANYTHING);
        CollectionRightsComparator comparator = new CollectionRightsComparator(getDao().getUsersFromDb(resourceCollection), incomingUsers);
        if (comparator.rightsDifferent()) {
            logger.debug("{}", actor);

            if (!authorizationService.canAdminiserUsersOn( actor, source)) {
                throw new TdarAuthorizationException("resourceCollectionService.insufficient_rights");
            }

            for (AuthorizedUser user : comparator.getAdditions()) {
                addUserToCollection(shouldSaveResource, resourceCollection.getAuthorizedUsers(), user, actor, resourceCollection, source, type, rightsResolver);
            }

            resourceCollection.getAuthorizedUsers().removeAll(comparator.getDeletions());

            comparator.handleDifferences(resourceCollection, actor, rightsResolver);
        }
        comparator = null;

        logger.debug("users after save: {}", resourceCollection.getAuthorizedUsers());
        if (shouldSaveResource) {
            getDao().saveOrUpdate(resourceCollection);
        }
        logger.trace("------------------------------------------------------");
    }


    /**
     * Add a @link AuthorizedUser to the @link ResourceCollection if it's valid
     * 
     * @param shouldSaveResource
     * @param currentUsers
     * @param incomingUser
     * @param resourceCollection
     */
    private void addUserToCollection(boolean shouldSaveResource, Set<AuthorizedUser> currentUsers, AuthorizedUser incomingUser, TdarUser actor,
            ResourceCollection resourceCollection, HasAuthorizedUsers source, RevisionLogType type, RightsResolver rightsResolver) {
        TdarUser transientUser = incomingUser.getUser();
        if (PersistableUtils.isNotNullOrTransient(transientUser)) {
            TdarUser user = null;
            Long tranientUserId = transientUser.getId();
            try {
                user = getDao().find(TdarUser.class, tranientUserId);
            } catch (Exception e) {
                throw new TdarRecoverableRuntimeException("resourceCollectionService.user_does_not_exists", e, Arrays.asList(transientUser));
            }
            if (user == null) {
                throw new TdarRecoverableRuntimeException("resourceCollectionService.user_does_not_exists", Arrays.asList(transientUser));
            }

            // it's important to ensure that we replace the proxy user w/ the persistent user prior to calling isValid(), because isValid()
            // may evaluate fields that aren't set in the proxy object.
            incomingUser.setUser(user);
            if (!incomingUser.isValid()) {
                return;
            }
            if (PersistableUtils.isNotNullOrTransient(source) && RevisionLogType.EDIT == type) {
                rightsResolver.checkEscalation(actor, incomingUser);
            }
            currentUsers.add(incomingUser);
            if (shouldSaveResource) {
                incomingUser.setCreatedBy(actor);
                getDao().saveOrUpdate(incomingUser);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findParentOwnerCollections(org.tdar.core.bean.entity.Person, java.lang.Class)
     */
    @Override
    @Transactional(readOnly = true)
    public <C extends HierarchicalCollection> List<C> findParentOwnerCollections(Person person, Class<C> cls) {
        return getDao().findParentOwnerCollections(person, cls);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findPotentialParentCollections(org.tdar.core.bean.entity.Person, C, java.lang.Class)
     */
    @Override
    @Transactional(readOnly = true)
    public <C extends HierarchicalCollection> List<C> findPotentialParentCollections(Person person, C collection, Class<C> cls) {
        List<C> potentialCollections = getDao().findParentOwnerCollections(person, cls);
        if (collection == null) {
            return potentialCollections;
        }
        Iterator<C> iterator = potentialCollections.iterator();
        while (iterator.hasNext()) {
            ResourceCollection parent = iterator.next();
            if (parent instanceof SharedCollection) {
                while (parent != null) {
                    if (parent.equals(collection)) {
                        logger.trace("removing {} from parent list to prevent infinite loops", collection);
                        iterator.remove();
                        break;
                    }
                    parent = ((SharedCollection) parent).getParent();
                }
            }
        }
        return potentialCollections;
    }

    @Override
    @Transactional(readOnly = false)
    public <C extends ResourceCollection> void saveResourceCollections(Resource resource, Collection<C> incoming, Set<C> current,
            TdarUser authenticatedUser, boolean shouldSave, ErrorHandling errorHandling, Class<C> cls) {

        logger.debug("incoming {}: {} ({})", cls.getSimpleName(), incoming, incoming.size());
        logger.debug(" current {}: {} ({})", cls.getSimpleName(), current, current.size());

        ResourceCollectionSaveHelper<C> helper = new ResourceCollectionSaveHelper<C>(incoming, current, cls);
        logger.info("collections to remove: {}", helper.getToDelete());
        for (C collection : helper.getToDelete()) {
            removeResourceCollectionFromResource(resource, current, authenticatedUser, collection);
        }

        for (C collection : helper.getToAdd()) {
            if (collection.isValidForController()) {
                logger.debug("adding: {} ", collection);
                addResourceCollectionToResource(resource, current, authenticatedUser, shouldSave, errorHandling, collection, cls);
            } else {
                logger.warn("skipping invalid collection: {}", collection);
            }
        }
        logger.debug("after save: {} ({})", current, current.size());

    }

    private <C extends ResourceCollection> void removeResourceCollectionFromResource(Resource resource, Set<C> current, TdarUser authenticatedUser,
            C collection) {
        if (!authorizationService.canRemoveFromCollection(authenticatedUser, collection)) {
            String name = "Collection";
            if (collection instanceof ResourceCollection) {
                name = ((ResourceCollection) collection).getName();
            }
            throw new TdarAuthorizationException("resourceCollectionSerice.resource_collection_rights_remmove_error", Arrays.asList(name));

        }
        current.remove(collection);
        if (collection instanceof SharedCollection) {
            ((SharedCollection)collection).getResources().remove(resource);
            if (collection instanceof SharedCollection) {
                resource.getSharedCollections().remove(collection);
            }
        } else {
            ((ListCollection) collection).getUnmanagedResources().remove(resource);
            resource.getUnmanagedResourceCollections().remove((ListCollection) collection);
        }
    }

    /**
     * Add a @Link ResourceCollection to a @link Resource, create as needed.
     * 
     * @param resource
     * @param current
     * @param authenticatedUser
     * @param shouldSave
     * @param errorHandling
     * @param collection
     */
    @Override
    @Transactional(readOnly = false)
    public <C extends ResourceCollection> void addResourceCollectionToResource(Resource resource, Set<C> current, TdarUser authenticatedUser,
            boolean shouldSave,
            ErrorHandling errorHandling, C collection, Class<C> cls) {
        C collectionToAdd = null;
        logger.trace("addResourceCollectionToResource({}) {} - {}", cls, collection, resource);
        if (!collection.isValidForController()) {
            logger.debug("skipping invalid: {}", collection);
            return;
        }

        if (collection.isTransient()) {
            if (collection instanceof SharedCollection) {
                collectionToAdd = (C) findOrCreateCollection(resource, authenticatedUser, (SharedCollection) collection, SharedCollection.class);
            }
            if (collection instanceof ListCollection) {
                collectionToAdd = (C) findOrCreateCollection(resource, authenticatedUser, (ListCollection) collection, ListCollection.class);
            }
        } else {
            collectionToAdd = getDao().find(cls, collection.getId());
        }
        // }
        logger.trace("{}, {}", collectionToAdd, collectionToAdd.isValid());
        String name = getName(collectionToAdd);
        if (collectionToAdd != null && collectionToAdd.isValid()) {
            if (PersistableUtils.isNotNullOrTransient(collectionToAdd) && !current.contains(collectionToAdd)
                    && !authorizationService.canAddToCollection(authenticatedUser, collectionToAdd)) {
                throw new TdarAuthorizationException("resourceCollectionSerice.resource_collection_rights_error",
                        Arrays.asList(name));
            }
            collectionToAdd.markUpdated(authenticatedUser);
            if (collectionToAdd.isTransient()) {
                collectionToAdd.setChangesNeedToBeLogged(true);
                collectionToAdd.getAuthorizedUsers().add(new AuthorizedUser(authenticatedUser, authenticatedUser, GeneralPermissions.ADMINISTER_SHARE));
            }

            // jtd the following line changes collectionToAdd's hashcode. all sets it belongs to are now corrupt.
            if (collectionToAdd instanceof SharedCollection) {
                addToCollection(resource, (SharedCollection) collectionToAdd);
            } else {
                ((ListCollection) collectionToAdd).getUnmanagedResources().add(resource);
                resource.getUnmanagedResourceCollections().add((ListCollection) collectionToAdd);
            }
        } else {
            logger.debug("collection is not valid: {}", collection);
            if (errorHandling == ErrorHandling.VALIDATE_WITH_EXCEPTION) {
                String collectionName = "null collection";
                if (collectionToAdd != null && StringUtils.isNotBlank(name)) {
                    collectionName = name;
                }
                throw new TdarRecoverableRuntimeException("resourceCollectionService.invalid", Arrays.asList(collectionName));
            }
        }
    }

    private <C extends ResourceCollection> String getName(C collectionToAdd) {
        String name = "Internal";
        if (collectionToAdd instanceof ResourceCollection) {
            name = ((ResourceCollection) collectionToAdd).getName();
        }
        return name;
    }

    private void addToCollection(Resource resource, SharedCollection collectionToAdd) {

        if (collectionToAdd instanceof SharedCollection) {
            resource.getSharedCollections().add((SharedCollection) collectionToAdd);
        }
        ((SharedCollection) collectionToAdd).getResources().add(resource);
    }

    private <C extends ResourceCollection> C findOrCreateCollection(Resource resource, TdarUser authenticatedUser, C collection, Class<C> cls) {
        boolean isAdmin = authorizationService.can(InternalTdarRights.EDIT_RESOURCE_COLLECTIONS, authenticatedUser);
        C potential = getDao().findCollectionWithName(authenticatedUser, isAdmin, collection.getName(), cls);
        if (potential != null) {
            return potential;
        } else {
            collection.setOwner(authenticatedUser);
            collection.markUpdated(resource.getSubmitter());
            if (collection instanceof ResourceCollection && ((ResourceCollection) collection).getSortBy() == null) {
                ((ResourceCollection) collection).setSortBy(ResourceCollection.DEFAULT_SORT_OPTION);
            }
            publisher.publishEvent(new TdarEvent(collection, EventType.CREATE_OR_UPDATE));
            return collection;
        }
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findAllResourceCollections()
     */
    @Override
    @Transactional(readOnly = true)
    public List<SharedCollection> findAllResourceCollections() {
        return getDao().findAllSharedResourceCollections();
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#buildCollectionTreeForController(C, org.tdar.core.bean.entity.TdarUser, java.lang.Class)
     */
    @Override
    @Transactional(readOnly = true)
    public <C extends HierarchicalCollection<C>> TreeSet<C> buildCollectionTreeForController(C collection, TdarUser authenticatedUser, Class<C> cls) {
        TreeSet<C> allChildren = new TreeSet<C>(new TitleSortComparator());
        allChildren.addAll(getAllChildCollections(collection, cls));
        allChildren.addAll(addAlternateChildrenTrees(allChildren, collection, cls));
        // FIXME: iterate over all children to reconcile tree
        Iterator<C> iter = allChildren.iterator();
        while (iter.hasNext()) {
            C child = iter.next();
            authorizationService.applyTransientViewableFlag(child, authenticatedUser);
            C parent = child.getParent();
            if (parent != null) {
                parent.getTransientChildren().add(child);
                iter.remove();
            }
            if (child.getAlternateParent() != null) {
                child.getAlternateParent().getTransientChildren().add(child);
            }
        }
        ;

        // second pass - sort all children lists (we add root into "allchildren" so we can sort the top level)
        allChildren.add(collection);
        return allChildren;
    }

    private <C extends HierarchicalCollection<C>> Collection<C> addAlternateChildrenTrees(Collection<C> allChildren, C child, Class<C> cls) {
        Set<C> toReturn = new HashSet<>(getDao().getAlternateChildrenTrees(allChildren, child, cls));
        return toReturn;
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findAllChildCollectionsOnly(E, java.lang.Class)
     */
    @Override
    @Transactional(readOnly = true)
    public <E extends HierarchicalCollection<E>> List<E> findAllChildCollectionsOnly(E collection, Class<E> cls) {
        return getDao().findAllChildCollectionsOnly(collection, cls);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findFlattenedCollections(org.tdar.core.bean.entity.Person, org.tdar.core.bean.entity.permissions.GeneralPermissions, java.lang.Class)
     */
    @Override
    @Transactional(readOnly = true)
    public <C extends HierarchicalCollection<?>> Set<C> findFlattenedCollections(Person user, GeneralPermissions generalPermissions, Class<C> cls) {
        return getDao().findFlattendCollections(user, generalPermissions, cls);
    }

    /**
     * Find the root @link ResourceCollection of the specified collection.
     * 
     * @param node
     * @return
     */
    private SharedCollection getRootResourceCollection(SharedCollection node) {
        return node.getHierarchicalResourceCollections().get(0);
    };

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#getFullyInitializedRootResourceCollection(org.tdar.core.bean.collection.SharedCollection, org.tdar.core.bean.entity.TdarUser)
     */
    @Override
    @Transactional(readOnly = true)
    public SharedCollection getFullyInitializedRootResourceCollection(SharedCollection anyNode, TdarUser authenticatedUser) {
        SharedCollection root = getRootResourceCollection(anyNode);
        buildCollectionTreeForController(getRootResourceCollection(anyNode), authenticatedUser, SharedCollection.class);
        return root;
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findAllPublicActiveCollectionIds()
     */
    @Override
    @Transactional(readOnly = true)
    public List<Long> findAllPublicActiveCollectionIds() {
        return getDao().findAllPublicActiveCollectionIds();
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findAllResourcesWithStatus(org.tdar.core.bean.collection.ResourceCollection, org.tdar.core.bean.resource.Status)
     */
    @Override
    @Transactional(readOnly = true)
    public List<Resource> findAllResourcesWithStatus(ResourceCollection persistable, Status... statuses) {
        return getDao().findAllResourcesWithStatus(persistable, statuses);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#getAuthorizedUsersForCollection(org.tdar.core.bean.collection.ResourceCollection, org.tdar.core.bean.entity.TdarUser)
     */
    @Override
    @Transactional(readOnly = true)
    public List<AuthorizedUser> getAuthorizedUsersForCollection(ResourceCollection persistable, TdarUser authenticatedUser) {
        List<AuthorizedUser> users = new ArrayList<>(persistable.getAuthorizedUsers());
        applyTransientEnabledPermission(authenticatedUser, users,
                authorizationService.canEditCollection(authenticatedUser, persistable));
        return users;
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#reconcileCollectionTree(java.util.Collection, org.tdar.core.bean.entity.TdarUser, java.util.List, java.lang.Class)
     */
    @Override
    @Transactional(readOnly = true)
    public <C extends HierarchicalCollection<C>> void reconcileCollectionTree(Collection<C> collection, TdarUser authenticatedUser, List<Long> collectionIds,
            Class<C> cls) {
        Iterator<C> iter = collection.iterator();
        while (iter.hasNext()) {
            C rc = iter.next();
            List<Long> list = new ArrayList<>(rc.getParentIds());
            list.remove(rc.getId());
            if (CollectionUtils.containsAny(collectionIds, list)) {
                iter.remove();
            }
            buildCollectionTreeForController(rc, authenticatedUser, cls);
        }
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findCollectionSparseResources(java.lang.Long)
     */
    @Override
    @Transactional(readOnly = true)
    public List<Resource> findCollectionSparseResources(Long collectionId) {
        return getDao().findCollectionSparseResources(collectionId);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#getCollectionViewCount(org.tdar.core.bean.collection.ResourceCollection)
     */
    @Override
    @Transactional(readOnly = true)
    public Long getCollectionViewCount(ResourceCollection persistable) {
        if (PersistableUtils.isNullOrTransient(persistable))
            return 0L;
        return getDao().getCollectionViewCount(persistable);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#updateCollectionParentTo(org.tdar.core.bean.entity.TdarUser, C, C, java.lang.Class)
     */
    @Override
    @Transactional(readOnly = false)
    public <C extends HierarchicalCollection<C>> void updateCollectionParentTo(TdarUser authorizedUser, C persistable, C parent, Class<C> cls) {
        // find all children with me as a parent
        if (!authorizationService.canEditCollection(authorizedUser, persistable) ||
                parent != null && !authorizationService.canEditCollection(authorizedUser, parent)) {
            throw new TdarAuthorizationException("resourceCollectionService.user_does_not_have_permisssions");
        }

        List<C> children = getAllChildCollections(persistable, cls);
        List<Long> oldParentIds = new ArrayList<>(persistable.getParentIds());
        logger.debug("updating parent for {} from {} to {}", persistable.getId(), persistable.getParent(), parent);
        persistable.setParent(parent);
        List<Long> parentIds = new ArrayList<>();
        if (PersistableUtils.isNotNullOrTransient(parent)) {
            if (CollectionUtils.isNotEmpty(parent.getParentIds())) {
                parentIds.addAll(parent.getParentIds());
            }
            parentIds.add(parent.getId());
        }
        persistable.getParentIds().clear();
        persistable.getParentIds().addAll(parentIds);
        for (C child : children) {
            child.getParentIds().removeAll(oldParentIds);
            child.getParentIds().addAll(parentIds);
            saveOrUpdate(child);
        }
        saveOrUpdate(persistable);

    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#updateAlternateCollectionParentTo(org.tdar.core.bean.entity.TdarUser, C, org.tdar.core.bean.collection.HierarchicalCollection, java.lang.Class)
     */
    @Override
    @Transactional(readOnly = false)
    public <C extends HierarchicalCollection> void updateAlternateCollectionParentTo(TdarUser authorizedUser, C persistable,
            HierarchicalCollection hierarchicalCollection, Class<C> cls) {

        List<C> children = getAllChildCollections(persistable, cls);
        List<Long> oldParentIds = new ArrayList<>(persistable.getAlternateParentIds());
        logger.debug("updating parent for {} from {} to {}", persistable.getId(), persistable.getAlternateParent(), hierarchicalCollection);
        persistable.setAlternateParent(hierarchicalCollection);
        List<Long> parentIds = new ArrayList<>();
        if (PersistableUtils.isNotNullOrTransient(hierarchicalCollection)) {
            if (CollectionUtils.isNotEmpty(hierarchicalCollection.getAlternateParentIds())) {
                parentIds.addAll(hierarchicalCollection.getAlternateParentIds());
                parentIds.addAll(hierarchicalCollection.getParentIds());
            }
            parentIds.add(hierarchicalCollection.getId());
        }
        persistable.getAlternateParentIds().clear();
        persistable.getAlternateParentIds().addAll(parentIds);
        for (C child : children) {
            child.getAlternateParentIds().removeAll(oldParentIds);
            child.getAlternateParentIds().addAll(parentIds);
            saveOrUpdate(child);
        }
        saveOrUpdate(persistable);

    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#getAllChildCollections(C, java.lang.Class)
     */
    @Override
    @Transactional(readOnly = true)
    public <C extends HierarchicalCollection> List<C> getAllChildCollections(C persistable, Class<C> cls) {
        return getDao().getAllChildCollections(persistable, cls);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#addUserToInternalCollection(org.tdar.core.bean.resource.Resource, org.tdar.core.bean.entity.TdarUser, org.tdar.core.bean.entity.TdarUser, org.tdar.core.bean.entity.permissions.GeneralPermissions)
     */
    @Override
    @Transactional
    public void addUserToInternalCollection(Resource resource, TdarUser authenticatedUser, TdarUser user, GeneralPermissions permission) {
        resource.getAuthorizedUsers().add(new AuthorizedUser(authenticatedUser, user, permission));
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#getEffectiveSharesForResource(org.tdar.core.bean.resource.Resource)
     */
    @Override
    @Transactional(readOnly = true)
    public Set<SharedCollection> getEffectiveSharesForResource(Resource resource) {
        Set<SharedCollection> tempSet = new HashSet<>();
        for (SharedCollection collection : resource.getSharedResourceCollections()) {
            if (collection != null) {
                tempSet.addAll(collection.getHierarchicalResourceCollections());
            }
        }

        return tempSet;
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#getEffectiveResourceCollectionsForResource(org.tdar.core.bean.resource.Resource)
     */
    @Override
    @Transactional(readOnly = true)
    public Set<ListCollection> getEffectiveResourceCollectionsForResource(Resource resource) {
        Set<ListCollection> tempSet = new HashSet<>();
        for (ListCollection collection : resource.getUnmanagedResourceCollections()) {
            if (collection != null) {
                tempSet.addAll(collection.getHierarchicalResourceCollections());
            }
        }

        Iterator<ListCollection> iter = tempSet.iterator();
        while (iter.hasNext()) {
            ListCollection next = iter.next();
            if (CollectionUtils.isEmpty(((ListCollection) next).getAuthorizedUsers())) {
                iter.remove();
            }
        }

        return tempSet;
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#reconcileIncomingResourcesForCollection(org.tdar.core.bean.collection.RightsBasedResourceCollection, org.tdar.core.bean.entity.TdarUser, java.util.List, java.util.List)
     */
    @Override
    @Transactional(readOnly = false)
    public void reconcileIncomingResourcesForCollection(SharedCollection persistable, TdarUser authenticatedUser, List<Resource> resourcesToAdd,
            List<Resource> resourcesToRemove) {
        Set<Resource> resources = persistable.getResources();
        List<Resource> ineligibleToAdd = new ArrayList<Resource>(); // existing resources the user doesn't have the rights to add
        List<Resource> ineligibleToRemove = new ArrayList<Resource>(); // existing resources the user doesn't have the rights to add

        if (CollectionUtils.isNotEmpty(resourcesToAdd)) {
            if (!authorizationService.canAddToCollection(authenticatedUser, (ResourceCollection) persistable)) {
                throw new TdarAuthorizationException("resourceCollectionSerice.resource_collection_rights_error",
                        Arrays.asList(getName((ResourceCollection) persistable)));
            }
        }

        if (CollectionUtils.isNotEmpty(resourcesToRemove)) {
            if (!authorizationService.canRemoveFromCollection(authenticatedUser, (ResourceCollection) persistable)) {
                throw new TdarAuthorizationException("resourceCollectionSerice.resource_collection_rights_remmove_error",
                        Arrays.asList(getName((ResourceCollection) persistable)));
            }
        }

        for (Resource resource : resourcesToAdd) {
            if (!authorizationService.canEditResource(authenticatedUser, resource, GeneralPermissions.MODIFY_RECORD)) {
                ineligibleToAdd.add(resource);
            } else {
                addToCollection(resource, persistable);
                publisher.publishEvent(new TdarEvent(resource, EventType.CREATE_OR_UPDATE));
            }
        }

        for (Resource resource : resourcesToRemove) {
            if (!authorizationService.canEditResource(authenticatedUser, resource, GeneralPermissions.MODIFY_RECORD)) {
                ineligibleToAdd.add(resource);
            } else {
                removeFromCollection(resource, (ResourceCollection) persistable);
                publisher.publishEvent(new TdarEvent(resource, EventType.CREATE_OR_UPDATE));
                resources.remove(resource);
            }
        }
        getDao().saveOrUpdate(persistable);
        getDao().saveOrUpdate(resourcesToAdd);
        getDao().saveOrUpdate(resourcesToRemove);
        if (ineligibleToAdd.size() > 0) {
            throw new TdarAuthorizationException("resourceCollectionService.could_not_add", ineligibleToAdd);
        }

        if (ineligibleToRemove.size() > 0) {
            throw new TdarAuthorizationException("resourceCollectionService.could_not_remove", ineligibleToRemove);
        }
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#removeResourceFromCollection(org.tdar.core.bean.resource.Resource, org.tdar.core.bean.collection.VisibleCollection, org.tdar.core.bean.entity.TdarUser)
     */
    @Override
    @Transactional(readOnly = false)
    public void removeResourceFromCollection(Resource resource, ResourceCollection collection, TdarUser authenticatedUser) {
        if (!authorizationService.canEditResource(authenticatedUser, resource, GeneralPermissions.MODIFY_RECORD) ||
                authorizationService.canRemoveFromCollection(authenticatedUser, collection)) {
            throw new TdarAuthorizationException("resourceCollectionService.could_not_remove");
        } else {
            removeFromCollection(resource, collection);
            publisher.publishEvent(new TdarEvent(resource, EventType.CREATE_OR_UPDATE));
        }

    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#reconcileIncomingResourcesForCollectionWithoutRights(org.tdar.core.bean.collection.ListCollection, org.tdar.core.bean.entity.TdarUser, java.util.List, java.util.List)
     */
    @Override
    @Transactional(readOnly = false)
    public void reconcileIncomingResourcesForCollectionWithoutRights(ListCollection persistable, TdarUser authenticatedUser, List<Resource> resourcesToAdd,
            List<Resource> resourcesToRemove) {
        Set<Resource> resources = persistable.getUnmanagedResources();

        // FIXME: check that there's no overlap with existing resources
        for (Resource resource : resourcesToAdd) {
            resource.getUnmanagedResourceCollections().add(persistable);
            resources.add(resource);
            publisher.publishEvent(new TdarEvent(resource, EventType.CREATE_OR_UPDATE));
        }

        for (Resource resource : resourcesToRemove) {
            resource.getUnmanagedResourceCollections().remove(persistable);
            resources.remove(resource);
            publisher.publishEvent(new TdarEvent(resource, EventType.CREATE_OR_UPDATE));
        }
        saveOrUpdate(persistable);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#deleteForController(org.tdar.core.bean.collection.VisibleCollection, java.lang.String, org.tdar.core.bean.entity.TdarUser)
     */
    @Override
    @Transactional(readOnly = false)
    public void deleteForController(ResourceCollection persistable, String deletionReason, TdarUser authenticatedUser) {
        // should I do something special?
        if (persistable instanceof SharedCollection) {
            for (Resource resource : ((SharedCollection) persistable).getResources()) {
                removeFromCollection(resource, persistable);
                getDao().saveOrUpdate(resource);
                publisher.publishEvent(new TdarEvent(resource, EventType.CREATE_OR_UPDATE));
            }
        }
        if (persistable instanceof ListCollection) {
            ListCollection listCollection = (ListCollection) persistable;
            for (Resource resource : listCollection.getUnmanagedResources()) {
                removeFromCollection(resource, listCollection);
                getDao().saveOrUpdate(resource);
                publisher.publishEvent(new TdarEvent(resource, EventType.CREATE_OR_UPDATE));
            }
        }

        getDao().delete(persistable.getAuthorizedUsers());
        getDao().deleteDownloadAuthorizations(persistable);
        // FIXME: need to handle parents and children
        String msg = String.format("%s deleted %s (%s);\n%s ", authenticatedUser.getProperName(), persistable.getTitle(), persistable.getId(), deletionReason);
        CollectionRevisionLog revision = new CollectionRevisionLog(msg, persistable, authenticatedUser, RevisionLogType.DELETE);
        getDao().saveOrUpdate(revision);

        getDao().delete(persistable);
        publisher.publishEvent(new TdarEvent(persistable, EventType.DELETE));
        // getSearchIndexService().index(persistable.getResources().toArray(new Resource[0]));

    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#getDeletionIssues(com.opensymphony.xwork2.TextProvider, org.tdar.core.bean.collection.ResourceCollection)
     */
    @Override
    @Transactional(readOnly = true)
    public DeleteIssue getDeletionIssues(TextProvider provider, ResourceCollection persistable) {
        List<SharedCollection> findAllChildCollections = findDirectChildCollections(persistable.getId(), null, SharedCollection.class);
        if (CollectionUtils.isNotEmpty(findAllChildCollections)) {
            getLogger().info("we still have children: {}", findAllChildCollections);
            DeleteIssue issue = new DeleteIssue();
            issue.getRelatedItems().addAll(findAllChildCollections);
            issue.setIssue(provider.getText("resourceCollectionService.cannot_delete_collection"));
            return issue;
        }
        return null;
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#saveCollectionForController(org.tdar.core.service.CollectionSaveObject)
     */
    @Override
    @Transactional(readOnly = false)
    public <C extends HierarchicalCollection> void saveCollectionForController(CollectionSaveObject cso) {
        C persistable = (C) cso.getCollection();
        Class<C> cls = cso.getPersistableClass();
        TdarUser authenticatedUser = cso.getUser();
        if (persistable == null) {
            throw new TdarRecoverableRuntimeException();
        }
        logger.debug("{} - {}", persistable, persistable.getId());
        if (PersistableUtils.isTransient(persistable)) {
            GeneralPermissions perm = GeneralPermissions.ADMINISTER_SHARE;
            if (persistable instanceof ListCollection) {
                cls = (Class<C>) ListCollection.class;
                perm = GeneralPermissions.ADMINISTER_GROUP;
            }
            persistable.getAuthorizedUsers().add(new AuthorizedUser(authenticatedUser, authenticatedUser, perm));
        }
        RevisionLogType type = RevisionLogType.CREATE;
        if (PersistableUtils.isNotTransient(persistable)) {
            type = RevisionLogType.EDIT;
        }
        saveOrUpdate(persistable);
        List<Resource> resourcesToRemove = getDao().findAll(Resource.class, cso.getToRemove());
        List<Resource> resourcesToAdd = getDao().findAll(Resource.class, cso.getToAdd());
        getLogger().debug("toAdd: {}", resourcesToAdd);
        getLogger().debug("toRemove: {}", resourcesToRemove);

        if (persistable instanceof SharedCollection) {
            SharedCollection shared = (SharedCollection) persistable;
            reconcileIncomingResourcesForCollection(shared, authenticatedUser, resourcesToAdd, resourcesToRemove);
        }

        if (persistable instanceof ListCollection) {
            ListCollection list = (ListCollection) persistable;
            reconcileIncomingResourcesForCollectionWithoutRights(list, authenticatedUser, resourcesToAdd, resourcesToRemove);
        }
        // saveAuthorizedUsersForResourceCollection(persistable, persistable, cso.getAuthorizedUsers(), cso.isShouldSave(), authenticatedUser,type);
        simpleFileProcessingDao.processFileProxyForCreatorOrCollection(persistable.getProperties(),
                cso.getFileProxy());

        if (!Objects.equals(cso.getParentId(), persistable.getParentId())) {
            updateCollectionParentTo(authenticatedUser, persistable, (C) cso.getParent(), cls);
        }

        if (!Objects.equals(cso.getAlternateParentId(), persistable.getAlternateParentId())) {
            logger.debug("updating alternate parent for {} from {} to {}", persistable.getId(), persistable.getAlternateParent(), cso.getAlternateParent());
            updateAlternateCollectionParentTo(authenticatedUser, persistable, cso.getAlternateParent(), cls);
        }

        String msg = String.format("%s modified %s", authenticatedUser, persistable.getTitle());
        CollectionRevisionLog revision = new CollectionRevisionLog(msg, persistable, authenticatedUser, type);
        revision.setTimeBasedOnStart(cso.getStartTime());
        getDao().saveOrUpdate(revision);
        publisher.publishEvent(new TdarEvent(persistable, EventType.CREATE_OR_UPDATE));
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#makeResourcesInCollectionActive(org.tdar.core.bean.collection.ResourceCollection, org.tdar.core.bean.entity.TdarUser)
     */
    @Override
    @Transactional(readOnly = false)
    public void makeResourcesInCollectionActive(ResourceCollection col, TdarUser person) {
        if (!authorizationService.canEditCollection(person, col)) {
            throw new TdarAuthorizationException("resourceCollectionService.make_active_permissions");
        }
        getDao().makeResourceInCollectionActive(col, person);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#getRandomFeaturedCollection()
     */
    @Override
    @Transactional(readOnly = true)
    public ResourceCollection getRandomFeaturedCollection() {
        return getDao().findRandomFeaturedCollection();
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#getWhiteLabelCollectionForResource(org.tdar.core.bean.resource.Resource)
     */
    @Override
    @Transactional(readOnly = true)
    public ResourceCollection getWhiteLabelCollectionForResource(Resource resource) {
        return getDao().getWhiteLabelCollectionForResource(resource);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findCollectionsWithName(org.tdar.core.bean.entity.TdarUser, java.lang.String, java.lang.Class)
     */
    @Override
    public <C extends HierarchicalCollection> C findCollectionsWithName(TdarUser user, String name, Class<C> cls) {
        boolean isAdmin = authorizationService.isEditor(user);
        return getDao().findCollectionWithName(user, isAdmin, name, cls);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#convertToWhitelabelCollection(org.tdar.core.bean.collection.CustomizableCollection)
     */
    @Override
    @Transactional
    /**
     * Convert a resource collection into a persisted white-label collection with all default values.
     * Note that this has the effect of detaching the input collection from the session.
     * 
     * @param rc
     * @return
     */
    public ResourceCollection convertToWhitelabelCollection(ResourceCollection rc) {
        if (rc.getProperties() != null && rc.getProperties().getWhitelabel()) {
            return rc;
        }
        return getDao().convertToWhitelabelCollection(rc);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#convertToResourceCollection(org.tdar.core.bean.collection.CustomizableCollection)
     */
    @Override
    @Transactional
    /**
     * Detach the provided white-label collection and return a persisted resource collection object.
     *
     * @param wlc
     * @return
     */
    public ResourceCollection convertToResourceCollection(ResourceCollection wlc) {
        return getDao().convertToResourceCollection(wlc);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#changeSubmitter(org.tdar.core.bean.collection.ResourceCollection, org.tdar.core.bean.entity.TdarUser, org.tdar.core.bean.entity.TdarUser)
     */
    @Override
    @Transactional(readOnly = false)
    public void changeSubmitter(ResourceCollection collection, TdarUser submitter, TdarUser authenticatedUser) {
        getDao().changeSubmitter(collection, submitter, authenticatedUser);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#moveResource(org.tdar.core.bean.resource.Resource, C, C, org.tdar.core.bean.entity.TdarUser)
     */
    @Override
    @Transactional(readOnly = false)
    public <C extends ResourceCollection> void moveResource(Resource resource, C fromCollection, C toCollection, TdarUser tdarUser) {
        if (!authorizationService.canEdit(tdarUser, resource) || !authorizationService.canEdit(tdarUser, fromCollection)
                || !authorizationService.canEdit(tdarUser, toCollection)) {
            throw new TdarAuthorizationException("resourceCollectionService.insufficient_rights");
        }
        if (fromCollection instanceof SharedCollection) {
            resource.getSharedCollections().remove(fromCollection);
            ((SharedCollection) fromCollection).getResources().remove(resource);
            resource.getSharedCollections().add((SharedCollection) toCollection);
            ((SharedCollection) toCollection).getResources().add(resource);
        }
        if (fromCollection instanceof ListCollection) {
            resource.getUnmanagedResourceCollections().remove(fromCollection);
            ((ListCollection) fromCollection).getUnmanagedResources().remove(resource);
            resource.getUnmanagedResourceCollections().add((ListCollection) toCollection);
            ((ListCollection) toCollection).getUnmanagedResources().add(resource);
        }

        getDao().saveOrUpdate(resource);
        saveOrUpdate(fromCollection);
        saveOrUpdate(toCollection);

    }

    private void removeFromCollection(Resource resource, ResourceCollection collection) {
        if (collection instanceof SharedCollection) {
            resource.getSharedCollections().remove(collection);
            // ((SharedCollection)collection).getResources().remove(resource);
        }
        if (collection instanceof ListCollection) {
            resource.getUnmanagedResourceCollections().remove(collection);
            // ((ListCollection)collection).getUnmanagedResources().remove(resource);
        }

    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#getSchemaOrgJsonLD(org.tdar.core.bean.collection.VisibleCollection)
     */
    @Override
    @Transactional(readOnly = true)
    public String getSchemaOrgJsonLD(ResourceCollection resource) throws IOException {
        SchemaOrgCollectionTransformer transformer = new SchemaOrgCollectionTransformer();
        return transformer.convert(serializationService, resource);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#saveCollectionForRightsController(C, org.tdar.core.bean.entity.TdarUser, java.util.List, java.lang.Class, java.lang.Long)
     */
    @Override
    @Transactional(readOnly = false)
    public <C extends ResourceCollection> void saveCollectionForRightsController(C c, TdarUser authenticatedUser,
            List<UserRightsProxy> proxies,
            Class<C> class1, Long startTime) {
        List<AuthorizedUser> authorizedUsers = new ArrayList<>();
        List<UserInvite> invites = new ArrayList<>();
        userRightsProxyService.convertProxyToItems(proxies, authenticatedUser, authorizedUsers, invites);
        RevisionLogType edit = RevisionLogType.EDIT;
        saveAuthorizedUsersForResourceCollection(c, c, authorizedUsers, true, authenticatedUser, edit);

        if (c instanceof ResourceCollection) {
            String msg = String.format("%s modified rights on %s", authenticatedUser, ((ResourceCollection) c).getTitle());
            CollectionRevisionLog revision = new CollectionRevisionLog(msg, c, authenticatedUser, edit);
            revision.setTimeBasedOnStart(startTime);
            getDao().saveOrUpdate(revision);
        }
        c.markUpdated(authenticatedUser);
        userRightsProxyService.handleInvites(authenticatedUser, invites, c);
        saveOrUpdate(c);
        publisher.publishEvent(new TdarEvent(c, EventType.CREATE_OR_UPDATE));

    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findResourcesSharedWith(org.tdar.core.bean.entity.TdarUser, org.tdar.core.bean.entity.TdarUser)
     */
    @Override
    @Transactional(readOnly = true)
    public List<Resource> findResourcesSharedWith(TdarUser authenticatedUser, TdarUser user) {
        boolean admin = false;
        if (authorizationService.isEditor(authenticatedUser)) {
            admin = true;
        }
        return getDao().findResourcesSharedWith(authenticatedUser, user, admin);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findCollectionsSharedWith(org.tdar.core.bean.entity.TdarUser, org.tdar.core.bean.entity.TdarUser, java.lang.Class)
     */
    @Override
    @Transactional(readOnly = true)
    public <C extends ResourceCollection> List<SharedCollection> findCollectionsSharedWith(TdarUser authenticatedUser, TdarUser user, Class<C> cls) {
        boolean admin = false;
        if (authorizationService.isEditor(authenticatedUser)) {
            admin = true;
        }
        return getDao().findCollectionsSharedWith(authenticatedUser, user, cls, GeneralPermissions.MODIFY_RECORD, admin);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findUsersSharedWith(org.tdar.core.bean.entity.TdarUser)
     */
    @Override
    @Transactional(readOnly = true)
    public List<TdarUser> findUsersSharedWith(TdarUser authenticatedUser) {
        return getDao().findUsersSharedWith(authenticatedUser);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#saveResourceRights(java.util.List, org.tdar.core.bean.entity.TdarUser, org.tdar.core.bean.resource.Resource)
     */
    @Override
    @Transactional(readOnly = false)
    public void saveResourceRights(List<UserRightsProxy> proxies, TdarUser authenticatedUser, Resource resource) {
        List<UserInvite> invites = new ArrayList<>();
        List<AuthorizedUser> authorizedUsers = new ArrayList<>();
        userRightsProxyService.convertProxyToItems(proxies, authenticatedUser, authorizedUsers, invites);
        saveAuthorizedUsersForResource(resource, authorizedUsers, true, authenticatedUser);

        userRightsProxyService.handleInvites(authenticatedUser, invites, resource);
    }

    /* (non-Javadoc)
     * @see org.tdar.core.service.collection.ResourceCollectionService#findAlternateChildren(java.util.List, org.tdar.core.bean.entity.TdarUser, java.lang.Class)
     */
    @Override
    @Transactional(readOnly = true)
    public <C extends HierarchicalCollection> List<C> findAlternateChildren(List<Long> ids, TdarUser authenticatedUser, Class<C> cls) {
        List<C> findAlternateChildren = getDao().findAlternateChildren(ids, cls);
        if (CollectionUtils.isNotEmpty(findAlternateChildren)) {
            findAlternateChildren.forEach(c -> {
                authorizationService.applyTransientViewableFlag(c, authenticatedUser);
            });
        }
        return findAlternateChildren;
    }

    @Override
    public ResourceCollection find(long l) {
        return super.find(l);
    }

}
