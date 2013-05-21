package org.tdar.core.bean.billing;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tdar.core.bean.resource.InformationResource;
import org.tdar.core.bean.resource.InformationResourceFile;
import org.tdar.core.bean.resource.InformationResourceFileVersion;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.bean.resource.ResourceType;
import org.tdar.core.bean.resource.Status;
import org.tdar.core.exception.TdarValidationException;

/*
 * This class is designed to help figure out what resources (files, resources, space) that a tDAR Resource is taking up.
 * Some resources, like Ontologies, CodingSheets, etc. you get for free.
 * 
 * A Resource Evaluator is initialized with a BillingModel which tells it some of how to evaluate things ... as we decide, wa may need to port more of the decisions into that boolean logic
 * 
 * 
 * This class is not designed to be reused
 */
public class ResourceEvaluator implements Serializable {

    private static final long serialVersionUID = 3621509880429873050L;
    private boolean includeDeletedFilesInCounts = false;
    private boolean includeAllVersionsInCounts = false;
    private List<ResourceType> uncountedResourceTypes = Arrays.asList(ResourceType.CODING_SHEET, ResourceType.ONTOLOGY, ResourceType.PROJECT);
    private List<Status> uncountedResourceStatuses = Arrays.asList();
    private long resourcesUsed = 0;
    private long filesUsed = 0;
    private long spaceUsedInBytes = 0;
    protected final transient Logger logger = LoggerFactory.getLogger(getClass());
    private Set<Long> resourceIds = new HashSet<Long>();
    private BillingActivityModel model;

    public ResourceEvaluator(BillingActivityModel model) {
        this.model = model;
    }

    public ResourceEvaluator(BillingActivityModel model, Resource... resources) {
        this.model = model;
        evaluateResources(resources);
    }

    /*
     * IOC putting all of the logic in one place
     */
    public boolean accountHasMinimumForNewResource(Account account, ResourceType resourceType) {
        logger.info("f: {} s: {} r: {}", new Object[] { account.getAvailableNumberOfFiles(), account.getAvailableSpaceInMb(), account.getAvailableResources() });
        if (evaluatesNumberOfResources()) {
            if (!getUncountedResourceTypes().contains(resourceType) && account.getAvailableResources() <= 0) {
                return false;
            }
        }
        if (evaluatesNumberOfFiles() && account.getAvailableNumberOfFiles() <= 0)
            return false;
        if (evaluatesSpace() && account.getAvailableSpaceInMb() <= 0)
            return false;
        return true;
    }

    public void evaluateResources(Collection<Resource> resources) {
        evaluateResources(resources.toArray(new Resource[0]));
    }

    /*
     * Evaluate whether a resource can be added and how it counts when added to an account
     */
    public void evaluateResources(Resource... resources) {

        for (Resource resource : resources) {
            if (resource == null)
                continue;
            Status status = Status.ACTIVE;
            if (resource.isTransient()) {
                logger.warn("Resource {} is transient, it may not be updated properly", resource);
            }
            getResourceIds().add(resource.getId());
            if (resource.getStatus() != null) {
                status = resource.getStatus();
            }
            if (uncountedResourceTypes.contains(resource.getResourceType()) || uncountedResourceStatuses.contains(status)) {
                logger.trace("skipping because of status {} or type: {}", status, resource.getResourceType());
                resource.setCountedInBillingEvaluation(false);
                continue;
            }

            resourcesUsed++;
            long filesUsed_ = 0;
            long spaceUsed_ = 0;
            if (resource instanceof InformationResource) {
                InformationResource informationResource = (InformationResource) resource;
                for (InformationResourceFile file : informationResource.getInformationResourceFiles()) {
                    if (file.isDeleted() && !includeDeletedFilesInCounts) {
                        continue;
                    }
                    if (informationResource.getResourceType().isCompositeFilesEnabled()) {
                        filesUsed = 1;
                    } else {
                        filesUsed_++;
                    }
                    
                    for (InformationResourceFileVersion version : file.getInformationResourceFileVersions()) {
                        // we use version 1 because it's the original uploaded version
                        if (!includeAllVersionsInCounts && !version.getVersion().equals(1) || !version.isUploaded()) {
                            continue;
                        }
                        if (version.getFileLength() != null) {
                            spaceUsed_ += version.getFileLength();
                        }
                    }
                }
            }
            resource.setSpaceInBytesUsed(spaceUsed_);
            resource.setFilesUsed(filesUsed_);
            spaceUsedInBytes += spaceUsed_;
            filesUsed += filesUsed_;
        }
    }

    @Override
    public String toString() {
        return String.format("%s resources %s files %s mb", getResourcesUsed(), getFilesUsed(), getSpaceUsedInMb());
    }

    public boolean isIncludeDeletedFilesInCounts() {
        return includeDeletedFilesInCounts;
    }

    public void setIncludeDeletedFilesInCounts(boolean includeDeletedFilesInCounts) {
        this.includeDeletedFilesInCounts = includeDeletedFilesInCounts;
    }

    public boolean isIncludeOlderVersionsInCounts() {
        return includeAllVersionsInCounts;
    }

    public void setIncludeOlderVersionsInCounts(boolean includeOlderVersionsInCounts) {
        this.includeAllVersionsInCounts = includeOlderVersionsInCounts;
    }

    public List<ResourceType> getUncountedResourceTypes() {
        return uncountedResourceTypes;
    }

    public void setUncountedResourceTypes(List<ResourceType> uncountedResourceTypes) {
        this.uncountedResourceTypes = uncountedResourceTypes;
    }

    public long getResourcesUsed() {
        return resourcesUsed;
    }

    public void setResourcesUsed(long resourcesUsed) {
        this.resourcesUsed = resourcesUsed;
    }

    public long getFilesUsed() {
        return filesUsed;
    }

    public void setFilesUsed(long filesUsed) {
        this.filesUsed = filesUsed;
    }

    public long getSpaceUsedInBytes() {
        return spaceUsedInBytes;
    }

    public long getSpaceUsedInMb() {
        return (long) Math.ceil((double) spaceUsedInBytes / (double) Invoice.ONE_MB);
    }

    public void setSpaceUsed(long spaceUsed) {
        this.spaceUsedInBytes = spaceUsed;
    }

    public List<Status> getUncountedResourceStatuses() {
        return uncountedResourceStatuses;
    }

    public void setUncountedResourceStatuses(List<Status> uncountedResourceStatuses) {
        this.uncountedResourceStatuses = uncountedResourceStatuses;
    }

    public BillingActivityModel getModel() {
        return model;
    }

    public void subtract(ResourceEvaluator initialEvaluation) {
        if (!initialEvaluation.getModel().equals(getModel())) {
            throw new TdarValidationException("using two different models ");
        }
        setSpaceUsed(getSpaceUsedInBytes() - initialEvaluation.getSpaceUsedInBytes());
        setFilesUsed(getFilesUsed() - initialEvaluation.getFilesUsed());
        setResourcesUsed(getResourcesUsed() - initialEvaluation.getResourcesUsed());
    }

    public boolean evaluatesSpace() {
        return model.getCountingSpace();
    }

    public boolean evaluatesNumberOfResources() {
        return model.getCountingResources();
    }

    public boolean evaluatesNumberOfFiles() {
        return model.getCountingFiles();
    }

    public Set<Long> getResourceIds() {
        return resourceIds;
    }

}
