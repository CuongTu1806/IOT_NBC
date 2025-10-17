package com.example.IoTwebNBC.service;

import com.example.IoTwebNBC.entity.DataSensorEntity;
import com.example.IoTwebNBC.request.DataSensorFilterRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public interface DataSensorService {
    Page<DataSensorEntity> findByFilter(String sensor, String inputSearch, Pageable pageable);
}
