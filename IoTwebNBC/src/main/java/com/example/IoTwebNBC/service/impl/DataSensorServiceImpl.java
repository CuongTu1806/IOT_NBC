package com.example.IoTwebNBC.service.impl;

import com.example.IoTwebNBC.entity.DataSensorEntity;
import com.example.IoTwebNBC.enums.CompareOpEnum;
import com.example.IoTwebNBC.repository.DataSensorEntityRepository;
import com.example.IoTwebNBC.repository.DataSensorEntityRepositoryImpl;
import com.example.IoTwebNBC.request.DataSensorFilterRequest;
import com.example.IoTwebNBC.service.DataSensorService;
import com.example.IoTwebNBC.utils.DataTypeUltil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class DataSensorServiceImpl implements DataSensorService {

    @Autowired
    private DataSensorEntityRepository repo;



    @Override
    public Page<DataSensorEntity> findByFilter(String sensor, String inputSearch, Pageable pageable) {
        return repo.findByFilter(sensor, inputSearch, pageable);
    }
}
