package com.example.IoTwebNBC.service;

import com.example.IoTwebNBC.dto.DeviceStateDTO;
import com.example.IoTwebNBC.entity.DeviceActionEntity;
import com.example.IoTwebNBC.repository.DeviceActionRepositoryCustom;
import com.example.IoTwebNBC.request.DeviceActionFilterRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

@Service
public interface DeviceActionService {
    Page<DeviceActionEntity> findByFilter(DeviceActionFilterRequest filter, Pageable pageable);
     DeviceStateDTO findState();
}
