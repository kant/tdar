package org.tdar.core.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tdar.core.bean.entity.AuthorizedUser;
import org.tdar.core.bean.entity.permissions.GeneralPermissions;
import org.tdar.utils.PersistableUtils;

/**
 * This is a utility class to try and simplify collection rights comparisons
 * 
 * @author abrin
 *
 */
public class CollectionRightsComparator {

    private static final Logger logger = LoggerFactory.getLogger(CollectionRightsComparator.class);
    private Set<AuthorizedUser> currentUsers;
    private List<AuthorizedUser> incomingUsers;

    private List<AuthorizedUser> additions = new ArrayList<>();
    private List<AuthorizedUser> deletions = new ArrayList<>();
    private List<AuthorizedUser> changes = new ArrayList<>();

    public CollectionRightsComparator(Set<AuthorizedUser> currentUsers, List<AuthorizedUser> incomingUsers) {
        this.currentUsers = currentUsers;
        this.incomingUsers = incomingUsers;
    }

    public boolean rightsDifferent() {
        Map<Long, GeneralPermissions> map = new HashMap<>();
        // iterate through current users, add them to the map
        for (AuthorizedUser user : currentUsers) {
            if (user == null) {
                continue;
            }
            if (PersistableUtils.isTransient(user.getUser())) {
                logger.debug(">> {}", user);
                return true;
            }
            addRemoveMap(map, user, true);
        }

        //iterate through the incoming list
        for (AuthorizedUser user : incomingUsers) {
            if (user == null) {
                continue;
            }
            if (PersistableUtils.isTransient(user.getUser())) {
                logger.debug(">>>> {}", user);
                return true;
            }
            addRemoveMap(map, user, false);
        }

        // if there are no changes and no additions, and the map is empty, then, we're done
        if (MapUtils.isEmpty(map) && CollectionUtils.isEmpty(additions) && CollectionUtils.isEmpty(changes)) {
            logger.debug("skipping rights section b/c no-changes");
            return false;
        }

        // otherwise, find deletions
        for (AuthorizedUser user : currentUsers) {
            if (user == null || user.getUser() == null) {
                continue;
            }
            for (Long id : map.keySet()) {
                if (Objects.equals(user.getUser().getId(),id)) {
                    deletions.add(user);
                }
            }
        }
        logger.debug("add: {} ; change: {} ; delete: {}", additions, changes, deletions);
        return true;
    }

    /**
     * Add or remove an entry from the map. If the map is empty at the end, then we have 0 changes.
     * 
     * @param map
     * @param user
     * @param add
     * @return
     */
    private void addRemoveMap(Map<Long, GeneralPermissions> map, AuthorizedUser user, boolean add) {
        if (user == null) {
            return;
        }
        Long id = user.getUser().getId();
        if (add) {
            //if we're adding, insert into the map
            map.put(id, user.getGeneralPermission());
        } else {
            // try and get the permissions from the map
            GeneralPermissions perm = map.get(id);
            if (perm != null) {
                // if we're there, then eitehr a no-op if exact match or a change
                if (Objects.equals(perm, user.getGeneralPermission())) {
                    map.remove(id);
                } else {
                    changes.add(user);
                }
            } else {
                // if we're not in the map, we're an addition
                additions.add(user);
            }
        }
    }

}
