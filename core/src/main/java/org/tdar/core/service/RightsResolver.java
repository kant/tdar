package org.tdar.core.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tdar.core.bean.entity.AuthorizedUser;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.entity.permissions.Permissions;
import org.tdar.core.bean.resource.HasAuthorizedUsers;
import org.tdar.core.exception.TdarAuthorizationException;

/**
 * Helper class to take a list of AuthorizedUsers and get the Maximum permissions allotted based on the rights available to the user
 */
public class RightsResolver {

    static final Date NEVER = new Date(0);
    static final Date INFINITE = new Date(Long.MAX_VALUE);
    private boolean admin = false;

    private List<AuthorizedUser> authorizedUsers;
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // track the PermissionSeen and the date assigned
    private Comparator<Permissions> comparator = new Comparator<Permissions>() {
        @Override
        public int compare(Permissions o1, Permissions o2) {
            return ObjectUtils.compare(o1.getEffectivePermissions(), o2.getEffectivePermissions());
        }
    };
    private TreeMap<Permissions, Date> lookup = new TreeMap<>(comparator);

    public RightsResolver(List<AuthorizedUser> authorizedUsers) {
        this.setAuthorizedUsers(authorizedUsers);
        // for each authorized user, grab the permissions
        for (AuthorizedUser au : authorizedUsers) {
            Date expires = au.getDateExpires();

            Permissions generalPermission = au.getGeneralPermission();
            Date existingDate = lookup.getOrDefault(generalPermission, NEVER);
            Date newDate = au.getDateExpires();

            // if the value is null, this is equal to never expiring
            if (expires == null) {
                newDate = INFINITE;
            } else if (expires.after(existingDate)) { // incoming is later
                newDate = expires;
            }
            lookup.put(generalPermission, newDate);
        }
    }

    public RightsResolver() {
        // TODO Auto-generated constructor stub
    }

    public static RightsResolver evaluate(List<AuthorizedUser> checkSelfEscalation) {
        return new RightsResolver(checkSelfEscalation);
    }

    public List<AuthorizedUser> getAuthorizedUsers() {
        return authorizedUsers;
    }

    public void setAuthorizedUsers(List<AuthorizedUser> authorizedUsers) {
        this.authorizedUsers = authorizedUsers;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    /**
     * For the authorized user, do we have GREATER or EQUAL permissions AND
     * Greater or Equal date of expiration for those permissions
     * 
     * @param userToAdd
     * @return
     */
    public boolean hasPermissionsEscalation(AuthorizedUser userToAdd) {
        if (admin) {
            return false;
        }

        // if we have no permissions, issue
        if (lookup.isEmpty()) {
            return false;
        }

        // keep the set with the least permissions to the greatest
        TreeSet<Permissions> toEval = new TreeSet<>(comparator);
        for (Permissions perm : lookup.keySet()) {
            if (perm.ordinal() >= userToAdd.getGeneralPermission().ordinal()) {
                toEval.add(perm);
            }
        }

        // if we have nothing found, there's an issue
        if (toEval.isEmpty()) {
            return true;
        }

        Date expiry = userToAdd.getDateExpires();
        if (expiry == null) {
            expiry = INFINITE;
        }

        // in increasing level or permission... check that we have the rights to do XYZ
        for (Permissions perm : toEval) {
            Date date = lookup.get(perm);
            // if the date we have rights for is INFINITE, then we're fine
            if (date == INFINITE) {
                return false;
            }
            if (logger.isTraceEnabled()) {
                logger.trace("{} <--> {} ({})", date, expiry, date.compareTo(expiry));
            }
            // if the AU's date is <= date then we're ok too
            if (date.compareTo(expiry) >= 0) {
                return false;
            }
        }

        return true;
    }

    public void logDebug(TdarUser actor, AuthorizedUser userToAdd) {
        logger.debug("~~ EscalationIssue ~~");
        logger.debug("actor:{}", actor);
        logger.debug("  map: {}", lookup);
        logger.debug("  all:{}", getAuthorizedUsers());
        logger.debug(" user:{}", userToAdd);
    }

    public void checkEscalation(TdarUser actor, AuthorizedUser userToAdd) {
        // check escalation of permissions
        if (hasPermissionsEscalation(userToAdd)) {
            logDebug(actor, userToAdd);
            throw new TdarAuthorizationException("resourceCollectionService.could_not_add_user", Arrays.asList(userToAdd,
                    userToAdd.getGeneralPermission()));
        }

    }

    public boolean canModifyUsersOn(HasAuthorizedUsers account) {
        Permissions minPerm = Permissions.getEditPermissionFor(account);

        if (isAdmin()) {
            return true;
        }

        if (lookup.isEmpty()) {
            return false;
        }

        for (Permissions perm : lookup.keySet()) {
            if (perm.ordinal() >= minPerm.ordinal()) {
                return true;
            }
        }
        return false;
    }

}
