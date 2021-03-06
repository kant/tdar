package org.tdar.search.service.query;

import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.queryparser.classic.QueryParser.Operator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tdar.core.bean.collection.ResourceCollection;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.entity.permissions.Permissions;
import org.tdar.core.bean.resource.Status;
import org.tdar.core.dao.external.auth.InternalTdarRights;
import org.tdar.core.service.external.AuthorizationService;
import org.tdar.search.bean.CollectionSearchQueryObject;
import org.tdar.search.exception.SearchException;
import org.tdar.search.query.LuceneSearchResultHandler;
import org.tdar.search.query.QueryFieldNames;
import org.tdar.search.query.builder.ResourceCollectionQueryBuilder;
import org.tdar.search.query.part.CollectionAccessQueryPart;
import org.tdar.search.query.part.FieldQueryPart;
import org.tdar.search.query.part.GeneralSearchQueryPart;
import org.tdar.search.query.part.QueryPartGroup;
import org.tdar.search.query.part.resource.AutocompleteTitleQueryPart;
import org.tdar.utils.PersistableUtils;

import com.opensymphony.xwork2.TextProvider;

@Service
@Transactional
public class CollectionSearchServiceImpl extends AbstractSearchService implements CollectionSearchService {

    @Autowired
    private transient AuthorizationService authorizationService;

    @Autowired
    private transient SearchService<ResourceCollection> searchService;

    /*
     * (non-Javadoc)
     * 
     * @see org.tdar.search.service.query.CollectionSearchService#buildResourceCollectionQuery(org.tdar.core.bean.entity.TdarUser,
     * org.tdar.search.bean.CollectionSearchQueryObject, org.tdar.search.query.LuceneSearchResultHandler, com.opensymphony.xwork2.TextProvider)
     */
    @Override
    public LuceneSearchResultHandler<ResourceCollection> buildResourceCollectionQuery(TdarUser authenticatedUser, CollectionSearchQueryObject query,
            LuceneSearchResultHandler<ResourceCollection> result, TextProvider provider) throws SearchException, IOException {
        ResourceCollectionQueryBuilder queryBuilder = new ResourceCollectionQueryBuilder();
        queryBuilder.setCreatorCreatedEmphasized(true);
        queryBuilder.setOperator(Operator.AND);

        if (CollectionUtils.isNotEmpty(query.getAllFields())) {
            GeneralSearchQueryPart q = new GeneralSearchQueryPart(query.getAllFields());
            q.setOperator(query.getOperator());
            queryBuilder.append(q);
        }

        if (CollectionUtils.isNotEmpty(query.getTitles())) {
            for (String title : query.getTitles()) {
                queryBuilder.append(new AutocompleteTitleQueryPart(title));
            }
        }

        // queryBuilder.append(new FieldQueryPart<String>(QueryFieldNames.COLLECTION_TYPE, CollectionType.SHARED.name()));
        if (query.isLimitToTopLevel()) {
            queryBuilder.append(new FieldQueryPart<Boolean>(QueryFieldNames.TOP_LEVEL, true));
        }

        if (PersistableUtils.isNotNullOrTransient(query.getId())) {
            queryBuilder.append(new FieldQueryPart<Long>(QueryFieldNames.ID, query.getId()));
        }

        // either it's not hidden and you can see it, or it is hidden but you have rights to it.

        QueryPartGroup rightsPart = buildCollectionRightsPart(authenticatedUser, query);
        queryBuilder.append(rightsPart);
        searchService.handleSearch(queryBuilder, result, provider);
        return result;

    }

    private QueryPartGroup buildCollectionRightsPart(TdarUser authenticatedUser, CollectionSearchQueryObject query) {
        QueryPartGroup rightsPart = new QueryPartGroup(Operator.OR);
        FieldQueryPart<Boolean> effectivePart = new FieldQueryPart<Boolean>(QueryFieldNames.EFFECTIVELY_PUBLIC, Boolean.TRUE);
        rightsPart.append(effectivePart);
        if (PersistableUtils.isNotNullOrTransient(authenticatedUser)) {
            boolean viewAnything = authorizationService.can(InternalTdarRights.VIEW_ANYTHING, authenticatedUser);
            Permissions permission = query.getPermission();
            if (permission == null) {
                permission = Permissions.NONE;
            }

            if (permission.ordinal() <= Permissions.VIEW_ALL.ordinal()) {
                // if view anything and empty or view all
                if (viewAnything) {
                    rightsPart.clear();
                } else {
                    rightsPart.append(new FieldQueryPart<Long>(QueryFieldNames.COLLECTION_USERS_WHO_CAN_VIEW, authenticatedUser.getId()));

                }

            } else {
                rightsPart.clear();
            }
            CollectionAccessQueryPart queryPart = getPermissionsPart(authenticatedUser, query);
            rightsPart.append(queryPart);
        }

        QueryPartGroup parentPart = new QueryPartGroup(Operator.AND);
        parentPart.append(new FieldQueryPart<Status>(QueryFieldNames.STATUS, Operator.OR, Arrays.asList(Status.ACTIVE, Status.DRAFT)));
        parentPart.append(rightsPart);
        return parentPart;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tdar.search.service.query.CollectionSearchService#lookupCollection(org.tdar.core.bean.entity.TdarUser,
     * org.tdar.search.bean.CollectionSearchQueryObject, org.tdar.search.query.LuceneSearchResultHandler, com.opensymphony.xwork2.TextProvider)
     */
    @Override
    public LuceneSearchResultHandler<ResourceCollection> lookupCollection(TdarUser authenticatedUser, CollectionSearchQueryObject csqo,
            LuceneSearchResultHandler<ResourceCollection> result, TextProvider provider) throws SearchException, IOException {
        ResourceCollectionQueryBuilder q = new ResourceCollectionQueryBuilder();
        q.setCreatorCreatedEmphasized(true);
        q.setOperator(Operator.AND);
        q.append(new AutocompleteTitleQueryPart(csqo.getTitles().get(0)));

        QueryPartGroup rightsPart = buildCollectionRightsPart(authenticatedUser, csqo);
        q.append(rightsPart);
        if (csqo.getType() != null) {
            q.append(new FieldQueryPart<>(QueryFieldNames.COLLECTION_TYPE, csqo.getType()));
        }
        searchService.handleSearch(q, result, provider);
        return result;

    }

    private CollectionAccessQueryPart getPermissionsPart(TdarUser authenticatedUser, CollectionSearchQueryObject csqo) {
        boolean admin = false;
        if (authorizationService.can(InternalTdarRights.VIEW_ANYTHING, authenticatedUser)) {
            admin = true;
        }
        CollectionAccessQueryPart queryPart = new CollectionAccessQueryPart(authenticatedUser, admin, csqo.getPermission());
        return queryPart;
    }

}