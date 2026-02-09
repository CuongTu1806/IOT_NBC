package com.example.IoTwebNBC.controller;

import com.example.IoTwebNBC.entity.DataSensorEntity;
import com.example.IoTwebNBC.repository.DataSensorEntityRepository;
//import com.example.IoTwebNBC.repository.DataSensorEntityRepositoryImpl;
import com.example.IoTwebNBC.request.DataSensorFilterRequest;
import com.example.IoTwebNBC.service.DataSensorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
// Model is not needed because we return ModelAndView
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/data_sensor")
@Slf4j
public class DataSensorController {
    private final DataSensorEntityRepository dataSensorEntityRepository;

    private final DataSensorService dataSensorService;

    @GetMapping("")
    public org.springframework.web.servlet.ModelAndView show(@RequestParam(name = "sensor", required = false) String sensor,
                        @RequestParam(name = "inputSearch", required = false) String inputSearch,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(name = "sortField", required = false) String sortField,
                        @RequestParam(name = "sortDir", required = false) String sortDir) {

            Pageable pageable;
            if(sortField != null && !sortField.isBlank()){
                String prop;
                switch(sortField){
                    case "id": prop = "id"; break;
                    case "temperature": prop = "temperature"; break;
                    case "humidity": prop = "humidity"; break;
                    case "lightLevel": prop = "lightLevel"; break;
                    case "rain": prop = "rain"; break;
                    case "windy": prop = "windy"; break;
                    case "timestamp": prop = "timestamp"; break;
                    default: prop = null; break;
                }
                if(prop == null){
                    pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
                } else {
                    Sort.Direction dir = (sortDir != null && sortDir.equalsIgnoreCase("asc")) ? Sort.Direction.ASC : Sort.Direction.DESC;
                    pageable = PageRequest.of(page, size, Sort.by(dir, prop));
                }
            } else {
                pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
            }

            Page<DataSensorEntity> ds = dataSensorService.findByFilter(sensor, inputSearch, pageable);

            // Ensure requested page is within bounds - mirror device_action behaviour
            int requestedPage = page;
            int totalPages = ds.getTotalPages();
            if (totalPages == 0) totalPages = 1; // keep UI showing a single page when no results
            if (requestedPage < 0) requestedPage = 0;
            if (requestedPage >= totalPages) requestedPage = totalPages - 1;

            if (requestedPage != page) {
                pageable = PageRequest.of(requestedPage, size, Sort.by("timestamp").descending());
                ds = dataSensorService.findByFilter(sensor, inputSearch, pageable);
            }

            // build model similar to dashboard so ContentNegotiation can produce JSON
            java.util.Map<String,Object> model = new java.util.HashMap<>();

            model.put("dataSensorEntities", ds.getContent());
            model.put("sensor", sensor);
            model.put("inputSearch", inputSearch);
            model.put("page", requestedPage);
            model.put("size", size);
            model.put("totalPages", totalPages);
            model.put("totalElements", ds.getTotalElements());

            return new org.springframework.web.servlet.ModelAndView("/user/data_sensor", model);
    }
}
