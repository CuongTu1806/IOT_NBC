package com.example.IoTwebNBC.service.impl;

import com.example.IoTwebNBC.dto.DeviceStateDTO;
import com.example.IoTwebNBC.entity.DeviceActionEntity;
import com.example.IoTwebNBC.repository.DeviceActionRepository;
import com.example.IoTwebNBC.request.DeviceActionFilterRequest;
import com.example.IoTwebNBC.service.DeviceActionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class DeviceActionServiceImpl implements DeviceActionService {
    private final DeviceActionRepository deviceActionRepository;

    public DeviceActionServiceImpl(DeviceActionRepository deviceActionRepository) {
        this.deviceActionRepository = deviceActionRepository;
    }

    @Override
    public Page<DeviceActionEntity> findByFilter(DeviceActionFilterRequest filter, Pageable pageable) {
        return deviceActionRepository.findByFilter(filter, pageable);
    }

    @Override
    public DeviceStateDTO findState() {
        DeviceActionEntity airEntity = deviceActionRepository.findFirstByDeviceOrderByIdDesc("air");
        DeviceActionEntity fanEntity = deviceActionRepository.findFirstByDeviceOrderByIdDesc("fan");
        DeviceActionEntity lightEntity = deviceActionRepository.findFirstByDeviceOrderByIdDesc("light");
        
        String air = airEntity != null ? airEntity.getStatus() : "OFF";
        String fan = fanEntity != null ? fanEntity.getStatus() : "OFF";
        String light = lightEntity != null ? lightEntity.getStatus() : "OFF";
        
        System.out.println("findState() - air: " + air + ", fan: " + fan + ", light: " + light);

        DeviceStateDTO state = new DeviceStateDTO();
        state.setAir(air);
        state.setFan(fan);
        state.setLight(light);

        return state;
    }
}
