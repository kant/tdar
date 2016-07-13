package org.tdar.core.bean.collection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.tdar.core.bean.SortOption;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.resource.Document;
import org.tdar.utils.jaxb.converters.JaxbPersistableConverter;

@DiscriminatorValue(value = "SHARED")
@Entity
@XmlRootElement(name = "sharedCollection")
public class SharedCollection extends RightsBasedResourceCollection implements Comparable<SharedCollection>, HierarchicalCollection<SharedCollection>, HasDisplayProperties {
    private static final long serialVersionUID = 7900346272773477950L;

    public SharedCollection(String title, String description, SortOption sortBy, boolean visible, TdarUser creator) {
        setName(title);
        setDescription(description);
        setSortBy(sortBy);
        setHidden(visible);
        setOwner(creator);
        this.setType(CollectionType.SHARED);
    }

    public SharedCollection(Long id, String title, String description, SortOption sortBy, boolean visible) {
        setId(id);
        setName(title);
        setDescription(description);
        setSortBy(sortBy);
        setHidden(visible);
        this.setType(CollectionType.SHARED);
    }

    public SharedCollection(Document document, TdarUser tdarUser) {
        markUpdated(tdarUser);
        getResources().add(document);
        this.setType(CollectionType.SHARED);
    }

    public SharedCollection() {
        this.setType(CollectionType.SHARED);
    }
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private SharedCollection parent;

    @XmlAttribute(name = "parentIdRef")
    @XmlJavaTypeAdapter(JaxbPersistableConverter.class)
    public SharedCollection getParent() {
        return parent;
    }
    

    @ElementCollection()
    @CollectionTable(name = "collection_parents", joinColumns = @JoinColumn(name = "collection_id") )
    @Column(name = "parent_id")
    private Set<Long> parentIds = new HashSet<>();

    

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private CollectionDisplayProperties properties;

    public CollectionDisplayProperties getProperties() {
        return properties;
    }

    public void setProperties(CollectionDisplayProperties properties) {
        this.properties = properties;
    }

//    public boolean isWhiteLabelCollection() {
//        return properties != null && properties.isWhitelabel();
//    }

//    public boolean isSearchEnabled() {
//        if (properties == null) {
//            return false;
//        }
//        return properties.isSearchEnabled();
//    }

    /**
     * Get ordered list of parents (ids) of this resources ... great grandfather, grandfather, father.
     * 
     * Note: in earlier implementations this contained the currentId as well, I've removed this, but am unsure
     * whether it should be there
     */
    @Transient
    @ElementCollection
    public Set<Long> getParentIds() {
        return parentIds;
    }

    public void setParentIds(Set<Long> parentIds) {
        this.parentIds = parentIds;
    }



    public void setParent(SharedCollection parent) {
        this.parent = parent;
    }

    private transient Set<SharedCollection> transientChildren = new LinkedHashSet<>();

    @XmlTransient
    @Transient
    @Override
    public Set<SharedCollection> getTransientChildren() {
        return transientChildren;
    }

    @Override
    public void setTransientChildren(Set<SharedCollection> transientChildren) {
        this.transientChildren = transientChildren;
    }


    /*
     * Get all of the resource collections via a tree (actually list of lists)
     */
    @Transient
    @XmlTransient
    // infinite loop because parentTree[0]==self
    @Override
    public List<SharedCollection> getHierarchicalResourceCollections() {
        return getHierarchicalResourceCollections(SharedCollection.class, this);
    }

    /*
     * Default to sorting by name, but grouping by parentId, used for sorting int he tree
     */
    @Override
    public int compareTo(SharedCollection o) {
        return compareTo(this, o);
    }

    @Transient
    @XmlTransient
    @Override
    public List<SharedCollection> getVisibleParents() {
        return getVisibleParents(SharedCollection.class);
    }

    @Override
    public boolean isValid() {
        if (isValidForController()) {
            if ((getType() == CollectionType.SHARED) && (getSortBy() == null)) {
                return false;
            }
            return super.isValid();
        }
        return false;
    }
}
