package org.tdar.core.bean.resource;

import org.tdar.core.bean.PluralLocalizable;
import org.tdar.locale.HasLabel;
import org.tdar.locale.Localizable;
import org.tdar.utils.MessageHelper;

/**
 * $Id$
 * 
 * Controlled vocabulary for document types.
 * 
 * @author <a href='mailto:Allen.Lee@asu.edu'>Allen Lee</a>
 * @version $Revision$
 */
public enum DocumentType implements HasLabel, Localizable, PluralLocalizable {

    BOOK("book", "book", "Book"),
    BOOK_SECTION("chapter", "bookitem", "Book"),
    JOURNAL_ARTICLE("article", "article", "Article"),
    THESIS("thesis", "thesis",
            "Institution", "Department", "Book"),
    CONFERENCE_PRESENTATION("conference", "conference", "Conference", "Conference Location",
            "Book"),
    REPORT("report", "unknown", "Book"),
    OTHER("other", "unknown", "Book");

    private final String label;
    private final String openUrlGenre;
    private String publisherName;
    private String publisherLocationName;
    private String schema;

    public boolean isPartOfLargerDocument() {
        switch (this) {
            case BOOK:
            case THESIS:
            case CONFERENCE_PRESENTATION:
                return false;
            default:
                return true;
        }
    }

    private DocumentType(String label, String genre, String schema) {
        this.label = label;
        this.openUrlGenre = genre;
        this.schema = schema;
    }

    private DocumentType(String label, String genre, String pubName, String pubLoc, String schema) {
        this.label = label;
        this.openUrlGenre = genre;
        this.publisherName = pubName;
        this.publisherLocationName = pubLoc;
        this.schema = schema;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getLocaleKey() {
        return MessageHelper.formatLocalizableKey(this);
    }

    @Override
    public String getPluralLocaleKey() {
        return MessageHelper.formatPluralLocalizableKey(this);
    }

    public boolean isBookTitleDisplayed() {
        switch (this) {
            case BOOK_SECTION:
                return true;
            default:
                return false;
        }

    }

    /**
     * Returns the ResourceType corresponding to the String given or null if none exists. Used in place of valueOf since
     * valueOf throws RuntimeExceptions.
     */
    public static DocumentType fromString(String string) {
        if ((string == null) || "".equals(string)) {
            return null;
        }
        // try to convert incoming resource type String query parameter to ResourceType enum.. unfortunately valueOf only throws RuntimeExceptions.
        try {
            return DocumentType.valueOf(string);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * Returns the name of the URL fragment and template basename minus the file extension (e.g., instead of book.ftl it returns book).
     * 
     * The convention used is to lowercase the enum name and replace all underscores with dashes, e.g., BOOK_SECTION to book-section and JOURNAL_ARTICLE to
     * journal-article.
     * 
     * @return
     */
    public String toUrlFragment() {
        return name().toLowerCase().replace('_', '-');
    }

    public String getOpenUrlGenre() {
        return openUrlGenre;
    }

    public String getPlural() {
        return MessageHelper.getMessage(getLocaleKey() + "_PLURAL");
    }

    public String getPublisherLocationName() {
        if (publisherLocationName == null) {
            return MessageHelper.getMessage("DocumentType.publisher_location");
        }
        return publisherLocationName;
    }

    private void setPublisherLocationName(String publisherLocationName) {
        this.publisherLocationName = publisherLocationName;
    }

    public String getPublisherName() {
        if (publisherName == null) {
            return MessageHelper.getMessage("DocumentType.publisher");
        }
        return publisherName;
    }

    private void setPublisherName(String publisherName) {
        this.publisherName = publisherName;
    }

    public String getSchema() {
        return schema;
    }

    private void setSchema(String schema) {
        this.schema = schema;
    }
}
