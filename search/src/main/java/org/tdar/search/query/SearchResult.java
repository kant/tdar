package org.tdar.search.query;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.tdar.core.bean.DisplayOrientation;
import org.tdar.core.bean.Indexable;
import org.tdar.core.bean.SortOption;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.search.query.facet.FacetWrapper;
import org.tdar.search.query.facet.FacetedResultHandler;

public class SearchResult<I extends Indexable> implements FacetedResultHandler<I>, Serializable {

    private static final long serialVersionUID = 8370261049894410532L;
    private SortOption sortField;
    private SortOption secondarySortField;
    private int totalRecords = 0;
    private int startRecord = 0;
    private int recordsPerPage = getDefaultRecordsPerPage();
    private boolean debug;
    private List<I> results = new ArrayList<>();
    private String mode;
    private boolean reindexing;
    private TdarUser authenticatedUser;
    private String searchTitle;
    private String searchDescription;
    private FacetWrapper facetWrapper;
    private ProjectionModel projectionModel = ProjectionModel.HIBERNATE_DEFAULT;    

    public SearchResult() {}
    
    public SearchResult(int i) {
    	this.recordsPerPage = i;
	}

	@Override
    public SortOption getSortField() {
        return sortField;
    }

    @Override
    public void setSortField(SortOption sortField) {
        this.sortField = sortField;
    }

    @Override
    public SortOption getSecondarySortField() {
        return secondarySortField;
    }

    public void setSecondarySortField(SortOption secondarySortField) {
        this.secondarySortField = secondarySortField;
    }

    @Override
    public int getTotalRecords() {
        return totalRecords;
    }

    @Override
    public void setTotalRecords(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    @Override
    public int getStartRecord() {
        return startRecord;
    }

    @Override
    public void setStartRecord(int startRecord) {
        this.startRecord = startRecord;
    }

    @Override
    public int getRecordsPerPage() {
        return recordsPerPage;
    }

    @Override
    public void setRecordsPerPage(int recordsPerPage) {
        this.recordsPerPage = recordsPerPage;
    }

    @Override
    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public List<I> getResults() {
        return results;
    }

    @Override
    public void setResults(List<I> results) {
        this.results = results;
    }

    @Override
    public String getMode() {
        return mode;
    }

    @Override
    public void setMode(String mode) {
        this.mode = mode;
    }

    @Override
    public boolean isReindexing() {
        return reindexing;
    }

    public void setReindexing(boolean reindexing) {
        this.reindexing = reindexing;
    }

    @Override
    public TdarUser getAuthenticatedUser() {
        return authenticatedUser;
    }

    public void setAuthenticatedUser(TdarUser authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
    }

    @Override
    public String getSearchTitle() {
        return searchTitle;
    }

    public void setSearchTitle(String searchTitle) {
        this.searchTitle = searchTitle;
    }

    @Override
    public String getSearchDescription() {
        return searchDescription;
    }

    public void setSearchDescription(String searchDescription) {
        this.searchDescription = searchDescription;
    }

    @Override
    public int getNextPageStartRecord() {
        return startRecord + recordsPerPage;
    }

    @Override
    public int getPrevPageStartRecord() {
        return startRecord - recordsPerPage;
    }

    @Override
    public ProjectionModel getProjectionModel() {
        return projectionModel;
    }

    public void setProjectionModel(ProjectionModel projectionModel) {
        this.projectionModel = projectionModel;
    }

    @Override
    public int getDefaultRecordsPerPage() {
        return DEFAULT_RESULT_SIZE;
    }

    public boolean hasMore() {
        return (getTotalRecords() > getStartRecord() + getRecordsPerPage());
    }

    @Override
    public FacetWrapper getFacetWrapper() {
        return facetWrapper;
    }

    public void setFacetWrapper(FacetWrapper facetWrapper) {
        this.facetWrapper = facetWrapper;
    }

	@Override
	public DisplayOrientation getOrientation() {
		return null;
	}

}