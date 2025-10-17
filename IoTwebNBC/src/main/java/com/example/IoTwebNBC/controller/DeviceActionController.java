package com.example.IoTwebNBC.controller;


import com.example.IoTwebNBC.entity.DataSensorEntity;
import com.example.IoTwebNBC.entity.DeviceActionEntity;
import com.example.IoTwebNBC.repository.DataSensorEntityRepository;
import com.example.IoTwebNBC.request.DeviceActionFilterRequest;
import com.example.IoTwebNBC.service.DataSensorService;
import com.example.IoTwebNBC.service.DeviceActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;



@Controller
@RequiredArgsConstructor
@RequestMapping("/device_action")
@Slf4j
public class DeviceActionController {
    private final DeviceActionService deviceActionService;

    @GetMapping("")
    public org.springframework.web.servlet.ModelAndView show(@ModelAttribute("filter") DeviceActionFilterRequest filter
        ,@RequestParam(defaultValue = "0") int page
        ,@RequestParam(defaultValue = "10") int size
        ,@RequestParam(name = "sortField", required = false) String sortField
        ,@RequestParam(name = "sortDir", required = false) String sortDir
        ) {

        // Build pageable: allow single-column sorting if provided; otherwise default to timestamp desc
        Pageable pageable;
        if(sortField != null && !sortField.isBlank()){
            String prop;
            switch(sortField){
                case "id": prop = "id"; break;
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

        Page<DeviceActionEntity> da = deviceActionService.findByFilter(filter, pageable);
    java.util.Map<String,Object> model = new java.util.HashMap<>();
    model.put("filter", filter);
        // Ensure requested page is within bounds - if out of bounds, clamp and re-query so the
        // returned content matches the page we display.
        int requestedPage = page;
        int totalPages = da.getTotalPages();
        if(totalPages == 0) totalPages = 1; // avoid zero pages in UI
        if(requestedPage < 0) requestedPage = 0;
        if(requestedPage >= totalPages) requestedPage = totalPages - 1;

        if(requestedPage != page){
            // re-create pageable with the clamped page and fetch again
            pageable = PageRequest.of(requestedPage, size, Sort.by("timestamp").descending());
            da = deviceActionService.findByFilter(filter, pageable);
        }

        model.put("deviceActions", da.getContent());
        model.put("page", requestedPage);
        model.put("size", size);
        model.put("totalPages", totalPages);
        model.put("totalElements", da.getTotalElements());

        return new org.springframework.web.servlet.ModelAndView("/user/device_action", model);
    }


}
