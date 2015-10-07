package org.tdar.search.index;

import org.apache.commons.lang3.StringUtils;
import org.tdar.core.bean.HasLabel;
import org.tdar.core.bean.Indexable;
import org.tdar.core.bean.Localizable;
import org.tdar.core.bean.collection.ResourceCollection;
import org.tdar.core.bean.entity.Institution;
import org.tdar.core.bean.entity.Person;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.keyword.CultureKeyword;
import org.tdar.core.bean.keyword.GeographicKeyword;
import org.tdar.core.bean.keyword.InvestigationType;
import org.tdar.core.bean.keyword.MaterialKeyword;
import org.tdar.core.bean.keyword.OtherKeyword;
import org.tdar.core.bean.keyword.SiteNameKeyword;
import org.tdar.core.bean.keyword.SiteTypeKeyword;
import org.tdar.core.bean.keyword.TemporalKeyword;
import org.tdar.core.bean.resource.Archive;
import org.tdar.core.bean.resource.Audio;
import org.tdar.core.bean.resource.CodingSheet;
import org.tdar.core.bean.resource.Dataset;
import org.tdar.core.bean.resource.Document;
import org.tdar.core.bean.resource.Geospatial;
import org.tdar.core.bean.resource.Ontology;
import org.tdar.core.bean.resource.Project;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.bean.resource.ResourceAnnotationKey;
import org.tdar.core.bean.resource.SensoryData;
import org.tdar.core.bean.resource.Video;
import org.tdar.utils.MessageHelper;

@SuppressWarnings("unchecked")
public enum LookupSource implements HasLabel, Localizable {
    PERSON("people", Person.class, TdarUser.class),
    INSTITUTION("institutions", Institution.class),
    KEYWORD("items", CultureKeyword.class, GeographicKeyword.class, InvestigationType.class, MaterialKeyword.class, OtherKeyword.class, TemporalKeyword.class,
            SiteNameKeyword.class, SiteTypeKeyword.class),
    RESOURCE("resources", Resource.class, Document.class, Dataset.class, Ontology.class, CodingSheet.class, Project.class,
            SensoryData.class, Video.class, Geospatial.class, Archive.class, Audio.class),
    COLLECTION("collections", ResourceCollection.class),
    RESOURCE_ANNOTATION_KEY("annotationKeys", ResourceAnnotationKey.class);

    private String collectionName;
    private Class<? extends Indexable>[] classes;

    private LookupSource(String name, Class<? extends Indexable>... classes) {
        this.collectionName = name;
        this.classes = classes;
    }

    @Override
    public String getLabel() {
        return collectionName;
    }

    @Override
    public String getLocaleKey() {
        return MessageHelper.formatLocalizableKey(this);
    }

    public String getCollectionName() {
        return collectionName;
    }

    public String getProper() {
        return StringUtils.capitalize(name().toLowerCase());
    }

    public Class<? extends Indexable>[] getClasses() {
        return classes;
    }

}