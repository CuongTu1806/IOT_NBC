package com.example.IoTwebNBC.repository;

import com.example.IoTwebNBC.entity.DeviceActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface DeviceActionRepository extends JpaRepository<DeviceActionEntity, Long>, DeviceActionRepositoryCustom {
    DeviceActionEntity findFirstByDeviceOrderByIdDesc(String device);

}
 
