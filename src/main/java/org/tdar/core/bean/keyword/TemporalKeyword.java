package org.tdar.core.bean.keyword;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.search.annotations.Indexed;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * $Id$
 * 
 * Temporal term coverage
 * 
 * @author <a href='mailto:Allen.Lee@asu.edu'>Allen Lee</a>
 * @version $Revision$
 */

@Entity
@Table(name = "temporal_keyword")
@XStreamAlias("temporalKeyword")
@Indexed(index = "Keyword")
public class TemporalKeyword extends UncontrolledKeyword.Base<TemporalKeyword> {

    private static final long serialVersionUID = -626136232824053935L;

    @OneToMany(orphanRemoval = true,cascade=CascadeType.ALL)
    @JoinColumn(name = "merge_keyword_id")
    private Set<TemporalKeyword> synonyms = new HashSet<TemporalKeyword>();

    public Set<TemporalKeyword> getSynonyms() {
        return synonyms;
    }

    public void setSynonyms(Set<TemporalKeyword> synonyms) {
        this.synonyms = synonyms;
    }

    public String getSynonymFormattedName() {
        return getLabel();
    }

}
