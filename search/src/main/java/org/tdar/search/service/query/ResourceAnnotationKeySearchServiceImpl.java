package org.tdar.search.service.query;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tdar.core.bean.resource.ResourceAnnotationKey;
import org.tdar.search.exception.SearchException;
import org.tdar.search.query.LuceneSearchResultHandler;
import org.tdar.search.query.QueryFieldNames;
import org.tdar.search.query.SearchResultHandler;
import org.tdar.search.query.builder.ResourceAnnotationKeyQueryBuilder;
import org.tdar.search.query.part.FieldQueryPart;
import org.tdar.search.query.part.PhraseFormatter;
import org.tdar.search.service.SearchUtils;

import com.opensymphony.xwork2.TextProvider;

@Service
public class ResourceAnnotationKeySearchServiceImpl extends AbstractSearchService implements ResourceAnnotationKeySearchService {

    @Autowired
    private SearchService<ResourceAnnotationKey> searchService;

    /*
     * (non-Javadoc)
     * 
     * @see org.tdar.search.service.query.ResourceAnnotationKeySearchService#buildAnnotationSearch(java.lang.String,
     * org.tdar.search.query.LuceneSearchResultHandler, int, com.opensymphony.xwork2.TextProvider)
     */
    @Override
    public SearchResultHandler<ResourceAnnotationKey> buildAnnotationSearch(String term, LuceneSearchResultHandler<ResourceAnnotationKey> result, int min,
            TextProvider provider) throws SearchException, IOException {
        ResourceAnnotationKeyQueryBuilder q = new ResourceAnnotationKeyQueryBuilder();

        // only return results if query length has enough characters
        if (SearchUtils.checkMinString(term, min)) {
            FieldQueryPart<String> fqp = new FieldQueryPart<>(QueryFieldNames.NAME_AUTOCOMPLETE, term);
            fqp.setPhraseFormatters(PhraseFormatter.ESCAPED, PhraseFormatter.WILDCARD);
            q.append(fqp);
        }

        searchService.handleSearch(q, result, provider);
        return result;

    }

}
