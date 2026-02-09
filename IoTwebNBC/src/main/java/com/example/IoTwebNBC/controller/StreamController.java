package com.example.IoTwebNBC.controller;


import com.example.IoTwebNBC.repository.DataSensorEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
// unused

@RestController
@RequestMapping("/api/data_sensor")
@RequiredArgsConstructor
public class StreamController {

    private final DataSensorEntityRepository repo;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @GetMapping(value = "/stream/room1", produces = "text/event-stream")
    public SseEmitter stream() {
    // room fixed to room1 in mapping
        SseEmitter emitter = new SseEmitter(0L); // không timeout
        emitter.onError(e -> {
            System.out.println("SSE error: " + e);
        });
        emitter.onCompletion(() -> {
            System.out.println("SSE complete");
        });
        emitter.onTimeout(() -> {
            System.out.println("SSE timeout");
        });

        // Gửi snapshot ngay khi connect (nếu có)
        repo.findLatestByRoomNative("room1").ifPresent(e -> {
            try {
                System.out.println("SSE initial send - ID: " + e.getId() + ", Rain: " + e.getRain() + ", Windy: " + e.getWindy());
                emitter.send(SseEmitter.event().name("sensor").data(e));
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        });

        final ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                var latest = repo.findLatestByRoomNative("room1");
                if (latest.isPresent()) {
                    var e = latest.get();
                    System.out.println("SSE polling - ID: " + e.getId() + 
                                     ", Timestamp: " + e.getTimestamp() + 
                                     ", Rain: " + e.getRain() + 
                                     ", Windy: " + e.getWindy());
                    try {
                        emitter.send(SseEmitter.event().name("sensor").data(e));
                    } catch (IOException ex) {
                        // if a send fails, complete emitter and cancel task
                        try { emitter.completeWithError(ex); } catch (Exception ignore) {}
                    }
                } else {
                    System.out.println("SSE polling - No data found for room1");
                }
            } catch (Exception ex) {
                System.err.println("SSE polling error: " + ex.getMessage());
                ex.printStackTrace();
                try { emitter.completeWithError(ex); } catch (Exception ignore) {}
            }
        }, 1, 2, TimeUnit.SECONDS);

        emitter.onCompletion(() -> {
            try { task.cancel(true); } catch (Exception ignored) {}
            System.out.println("SSE task cancelled on completion");
        });
        emitter.onTimeout(() -> {
            try { task.cancel(true); } catch (Exception ignored) {}
            try { emitter.complete(); } catch (Exception ignored) {}
            System.out.println("SSE task cancelled on timeout");
        });
        emitter.onError((Throwable ex) -> {
            try { task.cancel(true); } catch (Exception ignored) {}
            try { emitter.completeWithError(ex); } catch (Exception ignored) {}
            System.out.println("SSE task cancelled on error: " + ex);
        });

        return emitter;
    }
}


