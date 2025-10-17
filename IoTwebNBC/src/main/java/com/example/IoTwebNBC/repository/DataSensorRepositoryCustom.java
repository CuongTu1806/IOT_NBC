package com.example.IoTwebNBC.repository;

import com.example.IoTwebNBC.entity.DataSensorEntity;
import com.example.IoTwebNBC.entity.Telemetry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DataSensorRepositoryCustom  {
   Page<DataSensorEntity> findByFilter(String sensor, String inputSearch, Pageable pageable);
}
