package org.tdar.search.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.tdar.core.bean.Indexable;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.resource.Facetable;
import org.tdar.struts.data.FacetGroup;

/* further abstracting some of the functions of the search result handler 
 * so it can be pushed into the service layer. HibernateSearch handles the request by pulling field info
 * from the SearchResultHandler, and then sets results back on when done along with additional info if needed.
 */
/**
 * The SearchResultHandler interface is used by the SearchService to return search results, sorted and organised into pages.
 * An instance of this interface is passed to the SearchService, along with a query, and in response the SearchService
 * queries this interface for sorting, paging, faceting, etc. options, and then returns a page of results.
 * 
 * @see org.tdar.core.service.SearchService#handleSearch(org.tdar.search.query.QueryBuilder, SearchResultHandler)
 * 
 */
public interface SearchResultHandler<I extends Indexable> {

    final int DEFAULT_START = 0;
    final int DEFAULT_RESULT_SIZE = 25;

    SortOption getSortField();

    void setSortField(SortOption sortField);

    SortOption getSecondarySortField();

    public enum ProjectionModel {
        HIBERNATE_DEFAULT,
        LUCENE,
        RESOURCE_PROXY;

        private List<String> projections = new ArrayList<>();

        public List<String> getProjections() {
            if (this == RESOURCE_PROXY) {
                return Arrays.asList("id");
            }
            return projections;
        }

        public void setProjections(List<String> projections) {
            this.projections = projections;
        }

    }

    ProjectionModel getProjectionModel();

    /**
     * Sets the total number of records found by the SearchService.
     * When resultSize is less than startRecord + recordsPerPage, then there are more pages of results available.
     * 
     * @param resultSize
     *            the total number of records matching the search
     */
    void setTotalRecords(int resultSize);

    int getTotalRecords();

    /**
     * Gets the index of the first record which the SearchService should return in this page of results.
     * 
     * @return the index of the first record which the SearchService should return
     */
    int getStartRecord();

    void setStartRecord(int startRecord);

    /**
     * Retrieve the number of records which the SearchService should return in this page of results.
     * 
     * @return the number of records to return.
     */
    int getRecordsPerPage();

    void setRecordsPerPage(int recordsPerPage);

    boolean isDebug();

    boolean isShowAll();

    /**
     * Return a page of results from the SearchService.
     * 
     * @param toReturn
     */
    void setResults(List<I> toReturn);

    List<I> getResults();

    void setMode(String mode);

    boolean isReindexing();

    /*
     * Used for debug statements to print the mode
     */
    String getMode();

    TdarUser getAuthenticatedUser();

    String getSearchTitle();

    String getSearchDescription();

    int getNextPageStartRecord();

    int getPrevPageStartRecord();

    @SuppressWarnings("rawtypes")
    List<FacetGroup<? extends Facetable>> getFacetFields();

}
