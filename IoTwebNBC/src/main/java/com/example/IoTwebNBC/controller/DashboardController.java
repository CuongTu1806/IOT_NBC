package com.example.IoTwebNBC.controller;

import com.example.IoTwebNBC.entity.DataSensorEntity;
import com.example.IoTwebNBC.repository.DataSensorEntityRepository;
import com.example.IoTwebNBC.dto.DeviceStateDTO;
import com.example.IoTwebNBC.service.DeviceActionService;
import com.example.IoTwebNBC.service.impl.CommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/dashboard")
public class DashboardController {
    private final DataSensorEntityRepository repo;
    private final DeviceActionService deviceActionService;
    private final CommandService commandService;

    @GetMapping("")
    public ModelAndView dashboard() {
        // Lấy bản ghi mới nhất trong room
        String room = "room1";
        DataSensorEntity latest = repo.findFirstByRoomOrderByTimestampDesc(room)
                .orElse(null);
        Map<String,Object> model = new HashMap<>();
//        model.put("room", room);
        model.put("sensor", latest);
        // convenience attributes for template and JSON
//        model.put("temperature", latest != null ? latest.getTemperature() : null);
//        model.put("humidity", latest != null ? latest.getHumidity() : null);
//        model.put("light", latest != null ? latest.getLightLevel() : null);

        // Preload latest 10 records for charts (oldest -> newest)
        var latestList = repo.findLatest(room, org.springframework.data.domain.PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("timestamp").descending()));
        java.util.List<java.util.Map<String, Object>> preloaded = new java.util.ArrayList<>();
        for (DataSensorEntity e : latestList) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", e.getId());
            m.put("temperature", e.getTemperature());
            m.put("humidity", e.getHumidity());
            m.put("lightLevel", e.getLightLevel());
            m.put("timestamp", e.getTimestamp());
            preloaded.add(m);
        }
        // ensure ascending order for charting (oldest first)
        preloaded.sort((a,b) -> {
            java.time.temporal.Temporal t1 = (java.time.temporal.Temporal) a.get("timestamp");
            java.time.temporal.Temporal t2 = (java.time.temporal.Temporal) b.get("timestamp");
            // fallback to string compare if not temporal
            try{
                java.time.Instant i1 = java.time.Instant.from((java.time.temporal.TemporalAccessor) t1);
                java.time.Instant i2 = java.time.Instant.from((java.time.temporal.TemporalAccessor) t2);
                return i1.compareTo(i2);
            }catch(Exception ex){
                return String.valueOf(a.get("timestamp")).compareTo(String.valueOf(b.get("timestamp")));
            }
        });
        model.put("latestTenForDashboard", preloaded);

        // attach device state snapshot for initial render (air/fan/light)
        DeviceStateDTO state = deviceActionService.findState();
        model.put("state", state);

        return new ModelAndView("/user/dashboard", model);
    }


    @PostMapping("/command")
    public ResponseEntity<?> send(@RequestParam String room,
                                  @RequestParam String device,
                                  @RequestParam String action) {
        commandService.sendCommand(room, device, action);
        return ResponseEntity.ok(Map.of(device, action));
    }

    @GetMapping("/state")
    public ResponseEntity<DeviceStateDTO> state() {
        DeviceStateDTO state = deviceActionService.findState();
        return ResponseEntity.ok(state);
    }
}
