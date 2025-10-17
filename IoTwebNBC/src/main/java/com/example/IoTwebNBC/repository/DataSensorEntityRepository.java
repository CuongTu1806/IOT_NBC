package com.example.IoTwebNBC.repository;

import com.example.IoTwebNBC.entity.DataSensorEntity;
import com.example.IoTwebNBC.request.DataSensorFilterRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
 

import java.util.List;
import java.util.Optional;


public interface DataSensorEntityRepository extends JpaRepository<DataSensorEntity, Long>, DataSensorRepositoryCustom{
    List<DataSensorEntity> findTop1ByRoomOrderByTimestampDesc(String deviceId);


    //cái query này đặt trước hàm nào thì hàm đó sẽ chạy query này
    @Query("""
    select dse from DataSensorEntity dse
    where (:roomId is null or dse.room = :roomId)
    order by dse.timestamp desc
  """)
    List<DataSensorEntity> findLatest(@Param("roomId") String room, Pageable pageable);

    Optional<DataSensorEntity> findFirstByRoomOrderByTimestampDesc(String room);
    Optional<DataSensorEntity> findFirstByRoomOrderByIdDesc(String room);
    List<DataSensorEntity> findByRoomOrderByIdDesc(String room);
}
