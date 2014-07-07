package org.tdar.core.dao.entity;

import java.math.BigInteger;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.tdar.core.bean.entity.Creator;
import org.tdar.core.bean.entity.Person;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.dao.Dao;
import org.tdar.core.dao.TdarNamedQueries;

/**
 * $Id$
 * 
 * Provides DAO access for Person entities, including a variety of methods for
 * looking up a Person in tDAR.
 * 
 * @author <a href='Allen.Lee@asu.edu'>Allen Lee</a>
 * @version $Revision$
 */
@Component
public class PersonDao extends Dao.HibernateBase<Person> {

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

    public TdarUser findUserByEmail(final String username) {
        return (TdarUser) getCriteria(TdarUser.class).add(Restrictions.eq("email", username.toLowerCase())).uniqueResult();
    }

    @SuppressWarnings("unchecked")
    public Set<Person> findByLastName(String lastName) {
        Criteria criteria = getCriteria().add(Restrictions.eq("lastName", lastName));
        return new HashSet<Person>(criteria.list());
    }

    // find people with the same firstName, lastName, or institution (if specified)
    @SuppressWarnings("unchecked")
    public Set<Person> findByPerson(Person person) {
        // if the email address is set then all other fields are moot
        if (StringUtils.isNotBlank(person.getEmail())) {
            Set<Person> hs = new HashSet<Person>();
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

    public Person findAuthorityFromDuplicate(Person dup) {
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
        return new HashSet<Person>(criteria.list());
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
        Set<Long> ids = new HashSet<Long>();
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
        logger.info("clearing creator occurrence values");
        session.createSQLQuery(String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_CLEAR_COUNT)).executeUpdate();
        logger.info("beginning updates - resource");
        session.createSQLQuery(String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_RESOURCE)).executeUpdate();
        logger.info("beginning updates - resource - inherited");
        session.createSQLQuery(String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_RESOURCE_INHERITED)).executeUpdate();
        logger.info("beginning updates - copyright");
        session.createSQLQuery(String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_RESOURCE_INFORMATION_RESOURCE_COPYRIGHT)).executeUpdate();
        logger.info("beginning updates - provider");
        session.createSQLQuery(String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_RESOURCE_INFORMATION_RESOURCE_PROVIDER)).executeUpdate();
        logger.info("beginning updates - publisher");
        session.createSQLQuery(String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_RESOURCE_INFORMATION_RESOURCE_PUBLISHER)).executeUpdate();
        logger.info("beginning updates - submitter");
        session.createSQLQuery(String.format(TdarNamedQueries.UPDATE_CREATOR_OCCURRENCE_RESOURCE_SUBMITTER)).executeUpdate();
        logger.info("completed updates");
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

}
