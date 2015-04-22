package org.tdar.dataone.dao;

import java.util.Date;
import java.util.List;

import org.hibernate.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tdar.core.dao.GenericDao;
import org.tdar.dataone.bean.ListObjectEntry;
import org.tdar.dataone.bean.ObjectList;

@Component
public class DataOneDao {

    @Autowired
    private GenericDao genericDao;

    @SuppressWarnings("unchecked")
    public List<ListObjectEntry> findUpdatedResourcesWithDOIs(Date start, Date end, ObjectList list) {
        Query query = genericDao.getNamedQuery("query.dataone_list_objects");
        query.setDate("start", start);
        query.setDate("end", end);
        
        //FIXME: find better way to handle pagination
        list.setTotal(query.list().size());
        
        query = genericDao.getNamedQuery("query.dataone_list_objects");
        query.setDate("start", start);
        query.setDate("end", end);

        query.setMaxResults(list.getCount());
        query.setFirstResult(list.getStart());
        return query.list();
    }

}
