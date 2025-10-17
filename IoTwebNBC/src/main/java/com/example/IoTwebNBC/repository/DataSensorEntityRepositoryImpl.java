
package com.example.IoTwebNBC.repository;

import com.example.IoTwebNBC.entity.DataSensorEntity;
import com.example.IoTwebNBC.request.DataSensorFilterRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Repository
public class DataSensorEntityRepositoryImpl implements DataSensorRepositoryCustom {

    @PersistenceContext
    private EntityManager em;


    @Override
    public Page<DataSensorEntity> findByFilter(String sensor, String inputSearch, Pageable pageable) {
        String jpql = " SELECT d.id, d.room, d.temperature, d.humidity, d.light_level, d.timestamp FROM datasensor d ";
        String where = " Where 1 = 1";
        if(sensor != null && !sensor.isEmpty() ) {
            if(inputSearch != null && !inputSearch.isEmpty() ) {
                if(sensor.equals("timestamp")) {
                    where += " AND d.timestamp like " + "'%" + inputSearch + "%' ";
                }
                else where += " AND  d." + sensor + " = " + inputSearch + " ";
            }
        }

        else {
            if(inputSearch != null && !inputSearch.isEmpty()) {
                if(inputSearch.contains("-")) {
                    where += " AND d.timestamp like " + "'%" + inputSearch + "%' ";
                }
                else {
                    where +=" AND ( d.id = " + inputSearch + " " +
                            "OR d.temperature = " + inputSearch + " " +
                            "OR d.humidity = " + inputSearch + " " +
                            "OR d.light_level = " + inputSearch + " )";
                }
            }
        }
        jpql += where;

        // Determine ORDER BY from pageable's sort if present; default to timestamp desc
        String orderBy = "d.timestamp DESC";
        try{
            if(pageable != null && pageable.getSort() != null && pageable.getSort().isSorted()){
                // take only the first sort order (single-column sorting requirement)
                org.springframework.data.domain.Sort.Order o = pageable.getSort().iterator().next();
                String prop = o.getProperty();
                String dir = o.getDirection().isAscending() ? "ASC" : "DESC";
                // Map Java property names to DB column names (whitelist)
                String column;
                switch(prop) {
                    case "id": column = "d.id"; break;
                    case "temperature": column = "d.temperature"; break;
                    case "humidity": column = "d.humidity"; break;
                    case "lightLevel": column = "d.light_level"; break;
                    case "timestamp": column = "d.timestamp"; break;
                    default: column = "d.timestamp"; break; // fallback
                }
                orderBy = column + " " + dir;
            }
        }catch(Exception e){}

        jpql += " ORDER BY " + orderBy;

        String countJpql = "SELECT COUNT(id) FROM datasensor d " + where;

    // Truy vấn dữ liệu
    Query query = em.createNativeQuery(jpql, DataSensorEntity.class);
    Pageable pg = (pageable == null) ? org.springframework.data.domain.PageRequest.of(0, 10) : pageable;
    query.setFirstResult((int) pg.getOffset());
    query.setMaxResults(pg.getPageSize());
    @SuppressWarnings("unchecked")
    List<DataSensorEntity> resultList = query.getResultList();

        // Truy vấn tổng số bản ghi
        Query countQuery = em.createNativeQuery(countJpql);
        Object countResult = countQuery.getSingleResult();
        long total = 0L;
        if (countResult instanceof Number) {
            total = ((Number) countResult).longValue();
        } else {
            try {
                total = Long.parseLong(String.valueOf(countResult));
            } catch (Exception e) {
                total = 0L;
            }
        }

    return new PageImpl<DataSensorEntity>(resultList, pg, total);
    }

}
