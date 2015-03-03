package org.tdar.search.query.part;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.Query;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tdar.core.service.search.Operator;
import org.tdar.search.query.QueryFieldNames;

import com.opensymphony.xwork2.TextProvider;

/**
 * Provides Lucene query string for autocomplete lookups.
 * 
 * @author Adam Brin
 */
public class AutocompleteTitleQueryPart implements QueryPart<String> {
    private static final float TITLE_SORT_BOOST = 4f;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final float TITLE_BOOST = 6f;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");

    private final String title;

    public AutocompleteTitleQueryPart(String title) {
        this.title = title;
    }

    @Override
    public boolean isEmpty() {
        return StringUtils.isBlank(title);
    }

    @Override
    public Query generateQuery(QueryBuilder builder) {
        return getQueryPart().generateQuery(builder);
    }
    
    protected QueryPart<?> getQueryPart() {
        QueryPartGroup titleGroup = new QueryPartGroup(Operator.OR);
        // look up quoted leading match in autocomplete index
        FieldQueryPart<String> autoPart = new FieldQueryPart<String>(QueryFieldNames.TITLE_AUTO, title).setPhraseFormatters(PhraseFormatter.ESCAPE_QUOTED);
        // FIXME: while allowed, I'm not sure it's helpful to include non-analyzed fields in a search, especially considering the fact that it will use a
        // default analyzer at search-time. arguments otherwise?
        FieldQueryPart<String> titleSortPart = new FieldQueryPart<String>(QueryFieldNames.TITLE_SORT, title).setPhraseFormatters(PhraseFormatter.ESCAPED,
                PhraseFormatter.WILDCARD);
        // keyword match on title
        FieldQueryPart<String> keywordTitlePart = new FieldQueryPart<String>(QueryFieldNames.TITLE, title).setPhraseFormatters(PhraseFormatter.ESCAPED);
        if (title.length() > 2) {
            // FIXME: if title is over 2 characters, use escaped wildcard formatter?
            keywordTitlePart.setPhraseFormatters(PhraseFormatter.ESCAPED, PhraseFormatter.WILDCARD);
        }
        if (WHITESPACE_PATTERN.matcher(title).find()) {
            // FIXME: if the value contains a space, should we change from ESCAPED -> WILDCARD to WILDCARD -> QUOTED?
            titleSortPart.setPhraseFormatters(PhraseFormatter.WILDCARD, PhraseFormatter.QUOTED);
            if (title.length() > 2) {
                // FIXME: if value contains a space, should we change from ESCAPED -> WILDCARD to WILDCARD -> QUOTED?
                keywordTitlePart.setPhraseFormatters(PhraseFormatter.WILDCARD, PhraseFormatter.QUOTED);
            }
        }
        autoPart.setBoost(TITLE_BOOST);
        titleGroup.append(autoPart);
        titleSortPart.setBoost(TITLE_SORT_BOOST);
        titleGroup.append(titleSortPart);
        titleGroup.append(keywordTitlePart);
        logger.info("{}", titleGroup);
        return titleGroup;

    }

    @Override
    public String generateQueryString() {
        return isEmpty() ? "" : getQueryPart().generateQueryString();
    }

    @Override
    public String getDescription(TextProvider provider) {
        return "Title: " + title;
    }

    @Override
    public String getDescriptionHtml(TextProvider provider) {
        return StringEscapeUtils.escapeHtml4(getDescription(provider));
    }

    @Override
    public boolean isDescriptionVisible() {
        return false;
    }

    @Override
    public void setDescriptionVisible(boolean visible) {
    }

    @Override
    public Operator getOperator() {
        return Operator.AND;
    }
}
