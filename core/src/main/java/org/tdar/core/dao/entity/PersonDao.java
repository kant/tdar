package org.tdar.core.dao.entity;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.tdar.core.bean.entity.AgreementTypes;
import org.tdar.core.bean.entity.Creator;
import org.tdar.core.bean.entity.Creator.CreatorType;
import org.tdar.core.bean.entity.Person;
import org.tdar.core.bean.entity.ResourceCreatorRole;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.entity.UserAffiliation;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.configuration.TdarConfiguration;
import org.tdar.core.dao.Dao;
import org.tdar.core.dao.TdarNamedQueries;

/**
 * $Id$
 * 
 * Provides DAO access for Person entities.
 * 
 * FIXME: replace with TdarUserDao?
 * 
 * @author <a href='Allen.Lee@asu.edu'>Allen Lee</a>
 * @version $Revision$
 */
@Component
public class PersonDao extends Dao.HibernateBase<Person> {

    private static final String RESOURCE_IDS = "resourceIds";
    private static final Long TDAR_USER_PRIOR_TO_ASKING_AFFILIATION = 5215L;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public PersonDao() {
        super(Person.class);
    }

    @SuppressWarnings("unchecked")
    public List<TdarUser> findAllRegisteredUsers(Integer num) {
        Query query = getCurrentSession().getNamedQuery(QUERY_RECENT_USERS_ADDED);
        if ((num != null) && (num > 0)) {
            query.setMaxResults(num);
        }
        return query.list();
    }

    public List<Person> findSimilarPeople(TdarUser user) {
        List<Person> people = new ArrayList<>();
        String initial = user.getFirstName().substring(0, 1).toUpperCase();
        Query query = getCurrentSession().getNamedQuery(QUERY_SIMILAR_PEOPLE);
        query.setParameter("firstName", user.getFirstName());
        query.setParameter("lastName", user.getLastName());
        query.setParameter("initial", initial);
        query.setParameter("initial2", initial + ".");
        people.addAll(query.list());
        return people;
    }

    /**
     * Searches for a Person with the given email. Lowercases the email.
     * 
     * @param email
     * @return
     */
    public Person findByEmail(final String email) {
        return (Person) getCriteria().add(Restrictions.eq("email", email.toLowerCase())).uniqueResult();
    }

    public TdarUser findByUsername(final String username) {
        return (TdarUser) getCriteria(TdarUser.class).add(Restrictions.eq("username", username.toLowerCase())).uniqueResult();
    }

    public TdarUser findUserByEmail(final String email) {
        return (TdarUser) getCriteria(TdarUser.class).add(Restrictions.eq("email", email.toLowerCase())).uniqueResult();
    }

    @SuppressWarnings("unchecked")
    public Set<Person> findByLastName(String lastName) {
        Criteria criteria = getCriteria().add(Restrictions.eq("lastName", lastName));
        return new HashSet<>(criteria.list());
    }

    // find people with the same firstName, lastName, or institution (if specified)
    @SuppressWarnings("unchecked")
    public Set<Person> findByPerson(Person person) {
        // if the email address is set then all other fields are moot
        if (StringUtils.isNotBlank(person.getEmail())) {
            Set<Person> hs = new HashSet<>();
            hs.add(findByEmail(person.getEmail()));
            return hs;
        }
        Criteria criteria = getCriteria();
        criteria.add(Restrictions.eq("firstName", person.getFirstName()));
        criteria.add(Restrictions.eq("lastName", person.getLastName()));

        if (StringUtils.isNotBlank(person.getInstitutionName())) {
            criteria.createCriteria("institution").add(Restrictions.eq("name", person.getInstitutionName()));
        }
        return new HashSet<Person>(criteria.list());
    }

    public Person findAuthorityFromDuplicate(Creator dup) {
        Query query = getCurrentSession().createSQLQuery(String.format(QUERY_CREATOR_MERGE_ID, dup.getClass().getSimpleName(), dup.getId()));
        @SuppressWarnings("unchecked")
        List<BigInteger> result = query.list();
        if (CollectionUtils.isNotEmpty(result)) {
            try {
                return find(result.get(0).longValue());
            } catch (Exception e) {
                logger.error("could not find master for {} {}", dup, result);
            }
        }
        return null;
    }

    /**
     * Returns all people with the given full name.
     */
    @SuppressWarnings("unchecked")
    public Set<Person> findByFullName(String fullName) {
        String[] names = Person.split(fullName);
        if (names.length == 0) {
            return null;
        }
        final String lastName = names[0].trim();
        final String firstName = names[1].trim(); // hpcao added the trim, otherwise it can not work
        // System.out.println("finding by last name, firstName: |" + lastName + "|, |" + firstName+"|");
        // FIXME: figure out some way to reliably get a person if first name /
        // last name isn't unique enough... perhaps this method should return a
        // List<Person> instead

        // DetachedCriteria criteria = getDetachedCriteria();
        // criteria.add(Restrictions.eq("lastName", lastName));
        // criteria.add(Restrictions.eq("firstName", firstName));
        // List<Person> persons = (List<Person>) getHibernateTemplate().findByCriteria(criteria);

        Criteria criteria = getCriteria();
        criteria.add(Restrictions.eq("lastName", lastName));
        criteria.add(Restrictions.eq("firstName", firstName));
        return new HashSet<>(criteria.list());
    }

    @Override
    protected String getDefaultOrderingProperty() {
        return "lastName";
    }

    @SuppressWarnings("unchecked")
    public List<TdarUser> findRecentLogins() {
        Criteria criteria = getCriteria(TdarUser.class);
        criteria.add(Restrictions.isNotNull("lastLogin"));
        criteria.addOrder(Property.forName("lastLogin").desc());
        criteria.setMaxResults(25);
        return criteria.list();
    }

    public Long findNumberOfActualContributors() {
        Criteria criteria = getCriteria(Resource.class);
        criteria.setProjection(Projections.projectionList().add(Projections.countDistinct("uploader.id")));
        return (Long) ((criteria.list()).get(0));
    }

    @SuppressWarnings("unchecked")
    public Set<Long> findAllContributorIds() {
        Set<Long> ids = new HashSet<>();
        for (Number obj_ : (List<Number>) getCurrentSession().createSQLQuery(TdarNamedQueries.DISTINCT_SUBMITTERS).list()) {
            ids.add(obj_.longValue());
        }
        return ids;
    }

    public void registerLogin(TdarUser authenticatedUser) {
        authenticatedUser.setLastLogin(new Date());
        authenticatedUser.incrementLoginCount();
        logger.trace("login {} {}", authenticatedUser.getLastLogin(), authenticatedUser.getTotalLogins());
        saveOrUpdate(authenticatedUser);
    }

    public void updateOccuranceValues() {
        Session session = getCurrentSession();
        String roles = getFormattedRoles(null);
        logger.info("clearing creator occurrence values");
        session.createSQLQuery(String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_CLEAR_COUNT)).executeUpdate();
        logger.info("beginning updates - resource");
        session.createSQLQuery(String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_RESOURCE)).executeUpdate();
        logger.info("beginning updates - resource - inherited");
        session.createSQLQuery(String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_RESOURCE_INHERITED, roles)).executeUpdate();
        logger.info("beginning updates - copyright");
        session.createSQLQuery(String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_RESOURCE_INFORMATION_RESOURCE_COPYRIGHT)).executeUpdate();
        logger.info("beginning updates - provider");
        session.createSQLQuery(
                String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_RESOURCE_INFORMATION_RESOURCE_PROVIDER, Creator.OCCURRENCE, Creator.OCCURRENCE))
                .executeUpdate();
        logger.info("beginning updates - publisher");
        session.createSQLQuery(
                String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_RESOURCE_INFORMATION_RESOURCE_PUBLISHER, Creator.OCCURRENCE, Creator.OCCURRENCE))
                .executeUpdate();
        logger.info("beginning updates - submitter");
        session.createSQLQuery(String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_RESOURCE_SUBMITTER)).executeUpdate();
        logger.info("beginning updates - institution");
        session.createSQLQuery(String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_INSTITUTION)).executeUpdate();
        logger.info("completed updates");

        // create a temp table for these users and drop them in (much faster than a single query); the 1st temp table is 1:1 with resources, the second is 1:1
        // with creators. This is much faster.
        session.createSQLQuery(BROWSE_CREATOR_CREATE_TEMP).executeUpdate();
        // populate the resource table
        session.createSQLQuery(BROWSE_CREATOR_ACTIVE_USERS_1).executeUpdate();
        roles = getFormattedRoles(CreatorType.PERSON);
        session.createSQLQuery(String.format(BROWSE_CREATOR_ROLES_2, roles, 1000)).executeUpdate();
        session.createSQLQuery(String.format(BROWSE_CREATOR_IR_ROLES_3, roles, 1000)).executeUpdate();
        // these roles matter less, so they get a negative priority. If someone is "just" the submitter, they are 0, if they have secondary roles or submitter,
        // they will have a negative value, if they have an authorship equivalent role we get a positive result
        roles = getFormattedRoles(CreatorType.INSTITUTION);
        session.createSQLQuery(String.format(BROWSE_CREATOR_ROLES_2, roles, -10)).executeUpdate();
        session.createSQLQuery(String.format(BROWSE_CREATOR_IR_ROLES_3, roles, -10)).executeUpdate();
        session.createSQLQuery(BROWSE_CREATOR_IR_FIELDS_4).executeUpdate();
        // populate the temp_creator table with its values
        session.createSQLQuery(BROWSE_CREATOR_CREATOR_TEMP_5).executeUpdate();
        // migrate the data from the temp_creator table to the real one
        session.createSQLQuery(BROWSE_CREATOR_UPDATE_CREATOR_6).executeUpdate();
        logger.info("completed updates");

    }

    private String getFormattedRoles(CreatorType type) {
        Set<ResourceCreatorRole> roleSet = ResourceCreatorRole.getResourceCreatorRolesForProfilePage(type);
        String roles = String.format("'%s'", StringUtils.join(roleSet, "','"));
        return roles;
    }

    public Long getCreatorViewCount(Creator creator) {
        Query query = getCurrentSession().getNamedQuery(TdarNamedQueries.CREATOR_VIEW);
        query.setParameter("id", creator.getId());
        Number result = (Number) query.uniqueResult();
        return result.longValue();
    }

    public TdarUser findConvertPersonToUser(Person person, String username) {
        Long id = person.getId();
        getCurrentSession().createSQLQuery(String.format(TdarNamedQueries.CONVERT_PERSON_TO_USER, id, username)).executeUpdate();
        detachFromSession(person);
        TdarUser toReturn = find(TdarUser.class, id);
        logger.debug("toReturn: {}", toReturn);
        return toReturn;
    }

    public Map<AgreementTypes, Long> getAgreementCounts() {
        HashMap<AgreementTypes, Long> toReturn = new HashMap<>();
        Query query = getCurrentSession().getNamedQuery(TdarNamedQueries.AGREEMENT_COUNTS);
        Object[] result = (Object[]) query.uniqueResult();
        toReturn.put(AgreementTypes.USER_AGREEMENT, ((Number) result[0]).longValue());
        toReturn.put(AgreementTypes.CONTRIBUTOR_AGREEMENT, ((Number) result[1]).longValue());
        return toReturn;
    }

    public Map<UserAffiliation, Long> getAffiliationCounts() {
        HashMap<UserAffiliation, Long> toReturn = new HashMap<>();
        Query query = getCurrentSession().getNamedQuery(TdarNamedQueries.AFFILIATION_COUNTS);
        for (Object row_ : query.list()) {
            Object[] row = (Object[]) row_;
            toReturn.put((UserAffiliation) row[0], ((Number) row[1]).longValue());
        }
        Long noAnswer = toReturn.remove(null);
        if (TdarConfiguration.getInstance().isProductionEnvironment() && TdarConfiguration.getInstance().getHostName().equalsIgnoreCase("core.tdar.org")) {
            noAnswer = noAnswer - TDAR_USER_PRIOR_TO_ASKING_AFFILIATION;
            toReturn.put(UserAffiliation.PRIOR_TO_ASKING, TDAR_USER_PRIOR_TO_ASKING_AFFILIATION);
        }
        toReturn.put(UserAffiliation.NO_RESPONSE, noAnswer);
        return toReturn;
    }

    /**
     * Creates a temporary table with creator IDs for all resources Ids in list. This is used by the creator analysis process for related creators. It was
     * initially designed to run in loops but it took too much memory, so using temp tables in the database to generate the logic
     * 
     * @param resourceIds
     * @return
     */
    public Map<Creator, Integer> getRelatedCreatorCounts(Set<Long> resourceIds) {
        String drop = TdarNamedQueries.CREATOR_DROP_TEMP;
        getCurrentSession().createSQLQuery(drop).executeUpdate();

        String sql = TdarNamedQueries.CREATOR_ANALYSIS_CREATE_TEMP;
        getCurrentSession().createSQLQuery(sql).executeUpdate();
        String sql1 = TdarNamedQueries.CREATOR_ANALYSIS_RESOURCE_CREATOR_INSERT;
        getCurrentSession().createSQLQuery(sql1).setParameterList(RESOURCE_IDS, resourceIds).executeUpdate();
        String sql11 = TdarNamedQueries.CREATOR_ANALYSIS_SUBMITTER_INSERT;
        getCurrentSession().createSQLQuery(sql11).setParameterList(RESOURCE_IDS, resourceIds).executeUpdate();
        String sql12 = TdarNamedQueries.CREATOR_ANALYSIS_PUBLISHER_INSERT;
        getCurrentSession().createSQLQuery(sql12).setParameterList(RESOURCE_IDS, resourceIds).executeUpdate();
        String sql2 = TdarNamedQueries.CREATOR_ANALYSIS_INHERITED_CREATORS_INSERT;
        getCurrentSession().createSQLQuery(sql2).setParameterList(RESOURCE_IDS, resourceIds).executeUpdate();
        Map<Creator, Integer> results = new HashMap<Creator, Integer>();
        String sql3 = TdarNamedQueries.CREATOR_ANALYSIS__SLECT_COUNTS;
        for (Object row_ : getCurrentSession().createSQLQuery(sql3).list()) {
            Object[] row = (Object[]) row_;
            Integer count = ((BigInteger) row[0]).intValue();
            Long id = ((BigInteger) row[1]).longValue();
            Creator creator = find(Creator.class, id);

            if (creator.isDuplicate()) {
                creator = findAuthorityFromDuplicate(creator);
            }

            if (results.containsKey(creator)) {
                count += results.get(creator);
            }
            results.put(creator, count);
        }
        return results;
    }
}