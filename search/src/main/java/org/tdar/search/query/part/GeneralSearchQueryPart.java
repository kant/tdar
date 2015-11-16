package org.tdar.search.query.part;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParser.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tdar.search.query.QueryFieldNames;
import org.tdar.search.service.SearchFieldType;

import com.opensymphony.xwork2.TextProvider;

public class GeneralSearchQueryPart extends FieldQueryPart<String> {
    protected static final float TITLE_BOOST = 6f;
    protected static final float CREATOR_BOOST = 5f;
    protected static final float DESCRIPTION_BOOST = 4f;
    protected static final float PHRASE_BOOST = 3.2f;
    protected static final float ANY_FIELD_BOOST = 2f;

    private boolean useProximity = true;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public GeneralSearchQueryPart() {
    }

    public GeneralSearchQueryPart(String... values) {
        add(values);
    }

    public GeneralSearchQueryPart(Collection<String> values) {
        add(values.toArray(new String[0]));
    }

    protected QueryPartGroup getQueryPart(String value) {

        String cleanedQueryString = getCleanedQueryString(value);

        QueryPartGroup primary = new QueryPartGroup();

        logger.trace(cleanedQueryString);

        FieldQueryPart<String> titlePart = new FieldQueryPart<String>(QueryFieldNames.NAME, cleanedQueryString);
        FieldQueryPart<String> descriptionPart = new FieldQueryPart<String>(QueryFieldNames.DESCRIPTION, cleanedQueryString);
        FieldQueryPart<String> allFields = new FieldQueryPart<String>(QueryFieldNames.ALL, cleanedQueryString).setBoost(ANY_FIELD_BOOST);

        List<String> fields = new ArrayList<String>();
        for (String txt : StringUtils.split(cleanedQueryString)) {
            if (!ArrayUtils.contains(QueryPart.LUCENE_RESERVED_WORDS, txt)) {
                fields.add(txt);
            }
        }

        FieldQueryPart<String> allFieldsAsPart = new FieldQueryPart<String>(QueryFieldNames.ALL, fields).setBoost(ANY_FIELD_BOOST);

        allFieldsAsPart.setOperator(Operator.AND);
        allFieldsAsPart.setPhraseFormatters(PhraseFormatter.ESCAPED);

        if (cleanedQueryString.contains(" ")) {
            // APPLIES WEIGHTING BASED ON THE "PHRASE" NOT THE TERM
            titlePart = new FieldQueryPart<String>(QueryFieldNames.TITLE_PHRASE, cleanedQueryString);
            descriptionPart = new FieldQueryPart<String>(QueryFieldNames.DESCRIPTION_PHRASE, cleanedQueryString);
            FieldQueryPart<String> phrase = new FieldQueryPart<String>(QueryFieldNames.ALL_PHRASE, cleanedQueryString);
            // FIXME: magic words
            if (useProximity) {
                phrase.setProximity(4);
            }
            phrase.setBoost(PHRASE_BOOST);
            primary.append(phrase);
            if (useProximity) {
                titlePart.setProximity(3);
                descriptionPart.setProximity(4);
            }
        }

        primary.append(titlePart.setBoost(TITLE_BOOST));
        primary.append(descriptionPart.setBoost(DESCRIPTION_BOOST));
        primary.append(allFields);
        primary.append(allFieldsAsPart);

        primary.setOperator(Operator.OR);
        return primary;
    }

    @Override
    public String generateQueryString() {
        QueryPartGroup group = new QueryPartGroup(getOperator());
        for (String value : getFieldValues()) {
            group.append(this.getQueryPart(value));
        }
        return group.generateQueryString();
    }

    @Override
    public String getDescription(TextProvider provider) {
        String fields = StringUtils.join(getFieldValues(), ", ");
        if (StringUtils.isBlank(fields)) {
            return "";
        }
        return provider.getText(SearchFieldType.ALL_FIELDS.getLocaleKey()) + ": " + fields + " ";
    }

    @Override
    public String getDescriptionHtml(TextProvider provider) {
        return StringEscapeUtils.escapeHtml4(getDescription(provider));
    }

    public boolean isUseProximity() {
        return useProximity;
    }

    public void setUseProximity(boolean useProximity) {
        this.useProximity = useProximity;
    }

}
