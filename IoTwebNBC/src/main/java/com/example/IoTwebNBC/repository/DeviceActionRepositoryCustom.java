package com.example.IoTwebNBC.repository;


import com.example.IoTwebNBC.entity.DeviceActionEntity;
import com.example.IoTwebNBC.request.DeviceActionFilterRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;


public interface DeviceActionRepositoryCustom {
    Page<DeviceActionEntity> findByFilter(DeviceActionFilterRequest filter, Pageable pageable);
    java.util.Map<String,String> findLatestStatesByRoom(String room);
}
