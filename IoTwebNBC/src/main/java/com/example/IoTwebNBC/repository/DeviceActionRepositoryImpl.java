package com.example.IoTwebNBC.repository;


import com.example.IoTwebNBC.entity.DeviceActionEntity;
import com.example.IoTwebNBC.request.DeviceActionFilterRequest;
import com.example.IoTwebNBC.utils.DataTypeUltil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DeviceActionRepositoryImpl implements DeviceActionRepositoryCustom {
    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<DeviceActionEntity> findByFilter(DeviceActionFilterRequest filter, Pageable pageable) {
        String sql = "SELECT  d.id, d.room, d.device, d.status, d.timestamp FROM iot.deviceaction d WHERE 1=1 ";
        String where = "";
        if(filter.getDevice() != null && !filter.getDevice().equals("")) {
            where += " AND device = '" + filter.getDevice() + "' ";
        }

        if(filter.getStatus() != null && !filter.getStatus().equals("")) {
            where += " AND status = '" + filter.getStatus() + "' ";
        }

        if(filter.getInputSearch() != null && !filter.getInputSearch().equals("")) {
                where += " AND timestamp like '%" + filter.getInputSearch() + "%' " ;
        }

        // Determine ORDER BY from pageable's sort if present; default to id DESC
        String orderBy = "d.id DESC";
        try{
            if(pageable != null && pageable.getSort() != null && pageable.getSort().isSorted()){
                org.springframework.data.domain.Sort.Order o = pageable.getSort().iterator().next();
                String prop = o.getProperty();
                String dir = o.getDirection().isAscending() ? "ASC" : "DESC";
                String column;
                switch(prop){
                    case "id": column = "d.id"; break;
                    default: column = "d.id"; break;
                }
                orderBy = column + " " + dir;
            }
        }catch(Exception e){  }

        sql += where + " ORDER BY " + orderBy + " ";

    String countSql = "SELECT COUNT(id) FROM iot.deviceaction d WHERE 1=1 " + where;
    Query query = em.createNativeQuery(sql, DeviceActionEntity.class);
    Pageable pg = (pageable == null) ? org.springframework.data.domain.PageRequest.of(0, 10) : pageable;
    query.setFirstResult((int) pg.getOffset());
    query.setMaxResults(pg.getPageSize());

    @SuppressWarnings("unchecked")
    List<DeviceActionEntity> resultList = query.getResultList();

        // Truy vấn tổng số bản ghi (theo filter)
        Query countQuery = em.createNativeQuery(countSql);
        Object countResult = countQuery.getSingleResult();
        long total = 0L;
        if(countResult instanceof Number){
            total = ((Number) countResult).longValue();
        } else {
            try{
                total = Long.parseLong(String.valueOf(countResult));
            }catch(Exception e){
                total = 0L;
            }
        }

    return new PageImpl<DeviceActionEntity>(resultList, pg, total);
    }

    @Override
    public java.util.Map<String,String> findLatestStatesByRoom(String room) {
        String sql = "SELECT device, status FROM iot.deviceaction WHERE room = :room ORDER BY timestamp DESC LIMIT 1000";
        @SuppressWarnings("unchecked")
        java.util.List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("room", room)
                .getResultList();
        java.util.Map<String,String> map = new java.util.LinkedHashMap<>();
        for(Object[] r : rows){
            if(r == null || r.length < 2) continue;
            String device = r[0] == null ? null : String.valueOf(r[0]);
            String status = r[1] == null ? null : String.valueOf(r[1]);
            if(device == null || status == null) continue;
            if(!map.containsKey(device)) map.put(device, status);
            if(map.size() >= 10) break;
        }
        return map;
    }
}
