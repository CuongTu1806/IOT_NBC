package com.example.IoTwebNBC.config;


import com.example.IoTwebNBC.entity.DataSensorEntity;
import com.example.IoTwebNBC.entity.DeviceActionEntity;
import com.example.IoTwebNBC.entity.Telemetry;
import com.example.IoTwebNBC.repository.DataSensorEntityRepository;
import com.example.IoTwebNBC.repository.DeviceActionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.*;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

import java.nio.charset.StandardCharsets;
import java.time.*;

@Configuration
@RequiredArgsConstructor
public class MqttConfig {


    private final DataSensorEntityRepository dataSensorRepository;
    private final DeviceActionRepository deviceActionRepository;
    private final ObjectMapper om = new ObjectMapper(); // cần song song có thể đổi qua ExecutorChannel

    // Factory để kết nối broker
    @Bean
    public MqttPahoClientFactory clientFactory(@Value("${mqtt.url}") String url,
                                               @Value("${mqtt.username:}") String user,
                                               @Value("${mqtt.password:}") String pass) {
        var f = new DefaultMqttPahoClientFactory();
        var opts = new MqttConnectOptions();
        opts.setServerURIs(new String[]{url});
        if (!user.isEmpty()) {
            opts.setUserName(user);
            opts.setPassword(pass.toCharArray());
        }
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(false);  // Giữ session để tránh duplicate
        opts.setConnectionTimeout(30);
        opts.setKeepAliveInterval(60);
        f.setConnectionOptions(opts);
        return f;
    }

    // 2. Sau khi adapter đẩy message về đây, thì nó gọi DirectChannel() -> gọi hàm handle ngay trong cùng thread
    @Bean
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    // 1.Đăng ký topic cần lắng nghe
    @Bean
    public MessageProducer inbound(MqttPahoClientFactory cf,
                                   @Value("${mqtt.clientId}") String clientId,
                                   @Value("${mqtt.inTopics}") String inTopicsCsv) {
        String[] topics = org.springframework.util.StringUtils
                .commaDelimitedListToStringArray(inTopicsCsv);
        System.out.println("Subscribing MQTT topics: " + java.util.Arrays.toString(topics));
        var adapter = new MqttPahoMessageDrivenChannelAdapter(clientId + "-sub", cf, topics);
        adapter.setQos(1);
        adapter.setConverter(new DefaultPahoMessageConverter(StandardCharsets.UTF_8.name()));
        adapter.setOutputChannel(mqttInboundChannel());
        return adapter;
    }

    //3.Nhờ ServiceActivator mỗi message từ inboundChannel đến sẽ tự động gọi hàm này
    @Bean
    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public MessageHandler inboundHandler() {

        // Đặt message là msg
        return msg -> {

            String topic  = msg.getHeaders().get(org.springframework.integration.mqtt.support.MqttHeaders.RECEIVED_TOPIC, String.class);
            Object payloads = msg.getPayload();
            String payload = (payloads instanceof byte[]) ? new String((byte[]) payloads, StandardCharsets.UTF_8) : String.valueOf(payloads);

            // Chia 2 nhánh nhận topic: datasensor, status (JSON với device name)
            if(topic !=  null && topic.endsWith("/data_sensor")){
                try {
                    handleDataSensor(topic, payload);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            else if(topic !=  null && topic.endsWith("/status")){
                try {
                    handleDeviceStatusSingleTopic(topic, payload);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

        };
    }

    private void handleDataSensor(String topic, String payload) throws Exception {
                    // lấy topic trong msg do adapter set vào

            System.out.println("MQTT IN ["+topic+"] ID:" + " Payload: " + payload);

            // topic: devices/{roomId}/data_sensor
            //Tách topic thành các phần string tách bở dấu /
            String[] parts = topic.split("/");
            String room = parts.length >= 2 ? parts[1] : "unknown";

            try {
                JsonNode n = om.readTree(payload);
                Double temp = n.has("temp") && n.get("temp").isNumber() ? n.get("temp").asDouble() : null;
                Double hum  = n.has("hum")  && n.get("hum").isNumber()  ? n.get("hum").asDouble()  : null;
                Double light  = n.has("lux")  && n.get("lux").isNumber()  ? n.get("lux").asDouble()  : null;
                Integer rain = n.has("rain") && n.get("rain").isNumber() ? n.get("rain").asInt() : 0;
                Integer windy = n.has("windy") && n.get("windy").isNumber() ? n.get("windy").asInt() : 0;


                LocalDateTime tsLocal = null;
                if (n.has("ts") && n.get("ts").canConvertToLong()) {
                    long tsMillis = n.get("ts").asLong();
                    ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
                    tsLocal = Instant.ofEpochMilli(tsMillis).atZone(VN).toLocalDateTime();
                }


                if (tsLocal != null) {
                    // Tìm bản ghi gần nhất của device này
                    var existing = dataSensorRepository.findTop1ByRoomOrderByTimestampDesc(room);
                    if (!existing.isEmpty()) {
                        var lastRecord = existing.get(0);

                        // Kiểm tra xem có phải duplicate thực sự không
                        boolean isDuplicate = false;

                        if (lastRecord.getTimestamp() != null && temp != null && hum != null) {
                            long timeDiff = Math.abs(java.time.Duration.between(lastRecord.getTimestamp(), tsLocal).getSeconds());

                            // Nếu timestamp giống hệt và dữ liệu giống hệt → duplicate thực sự
                            if (timeDiff == 0 &&
                                Double.compare(lastRecord.getTemperature(), temp) == 0 &&
                                Double.compare(lastRecord.getHumidity(), hum) == 0) {
                                isDuplicate = true;
                                System.out.println("Skipping exact duplicate for room: " + room +
                                                " (same time, same data)");
                            }
                            // Nếu timestamp quá gần (< 1 giây) và dữ liệu giống hệt → duplicate
                            else if (timeDiff < 1 &&
                                     Double.compare(lastRecord.getTemperature(), temp) == 0 &&
                                     Double.compare(lastRecord.getHumidity(), hum) == 0) {
                                isDuplicate = true;
                                System.out.println("Skipping rapid duplicate for device: " + room +
                                                " (time diff: " + timeDiff + "s, same data)");
                            }
                            // Nếu timestamp cách nhau 1-3 giây → bình thường (theo yêu cầu thầy)
                            else if (timeDiff >= 1 && timeDiff <= 3) {
                                System.out.println("Normal interval detected for device: " + room +
                                                " (time diff: " + timeDiff + "s) - Accepting as normal");
                            }
                            // Nếu timestamp cách nhau > 3 giây → có thể bị mất kết nối
                            else if (timeDiff > 3) {
                                System.out.println("Long interval detected for device: " + room +
                                                " (time diff: " + timeDiff + "s) - Possible connection issue");
                            }
                        }

                        if (isDuplicate) {
                            return; // Bỏ qua duplicate thực sự
                        }
                    }
                }

                //Tạo đối tượng datasensor để nhận dữ liệu từ mqtt sau đó lưu vào csdl
                DataSensorEntity dse = DataSensorEntity.builder()
                        .temperature(temp)
                        .humidity(hum)
                        .timestamp(tsLocal)
                        .lightLevel(light)
                        .rain(rain)
                        .windy(windy)
                        .room(room)
                        .build();
                dataSensorRepository.save(dse);
                System.out.println("Saved datasensor: " + dse);
                
                // Kiểm tra ngưỡng cảnh báo và gửi lệnh LED
                checkAndSendAlert(room, rain, windy);
                
            } catch (Exception e) {
                System.err.println("Parse error: " + e.getMessage());
                e.printStackTrace();
            }
    }

    private void handleDeviceStatusSingleTopic(String topic, String payload) {
        // topic: devices/{room}/status
        // payload: {"device":"fan","state":"on"}
        System.out.println("DEVICE STATUS IN [" + topic + "] " + payload);
        
        String[] parts = topic.split("/");
        if (parts.length < 3) {
            System.err.println("Invalid status topic: " + topic);
            return;
        }
        
        String room = parts[1];
        
        try {
            JsonNode n = om.readTree(payload);
            
            if (!n.has("device") || !n.has("state")) {
                return;
            }
            
            String device = n.get("device").asText();  // fan, air, light
            String state = n.get("state").asText();    // on, off
            
            // Validate device name
            if (!device.matches("fan|air|light")) {
                System.err.println("Unknown device: " + device);
                return;
            }
            
            // Lưu vào DB
            DeviceActionEntity dae = DeviceActionEntity.builder()
                    .room(room)
                    .device(device)
                    .status(state.toUpperCase())  // ON/OFF
                    .timestamp(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                    .build();
            
            deviceActionRepository.save(dae);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Kiểm tra ngưỡng và gửi lệnh LED cảnh báo
    private void checkAndSendAlert(String room, Integer rain, Integer windy) {
        final int RAIN_THRESHOLD = 50;
        final int WIND_THRESHOLD = 25;
        
        boolean rainAlert = (rain != null && rain > RAIN_THRESHOLD);
        boolean windAlert = (windy != null && windy > WIND_THRESHOLD);
        
        String alertTopic = "devices/" + room + "/alert";

        
        try {
            // Gửi lệnh bật/tắt LED rain
            String rainCmd = rainAlert ? "led_rain:on" : "led_rain:off";
            mqttOutboundChannel().send(
                org.springframework.messaging.support.MessageBuilder
                    .withPayload(rainCmd)
                    .setHeader(MqttHeaders.TOPIC, alertTopic)
                    .build()
            );
            System.out.println("MQTT OUT [" + alertTopic + "] " + rainCmd);
            
            Thread.sleep(100);
            
            // Gửi lệnh bật/tắt LED wind
            String windCmd = windAlert ? "led_wind:on" : "led_wind:off";
            mqttOutboundChannel().send(
                org.springframework.messaging.support.MessageBuilder
                    .withPayload(windCmd)
                    .setHeader(MqttHeaders.TOPIC, alertTopic)
                    .build()
            );
            System.out.println("MQTT OUT [" + alertTopic + "] " + windCmd);
            
        } catch (Exception e) {
            System.err.println("Failed to send alert: " + e.getMessage());
        }
    }


    // -----outbound----- publish

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutboundHandler(MqttPahoClientFactory cf,
                                              @Value("${mqtt.clientId}") String clientId) {
        var h = new MqttPahoMessageHandler(clientId + "-pub", cf);
        h.setAsync(false);
        h.setDefaultQos(1);
        h.setDefaultRetained(false);
        return h;
    }


    @MessagingGateway(defaultRequestChannel = "mqttOutboundChannel")
    public interface MqttGateway {
        void send(@Header("mqtt_topic") String topic,
                  @Payload String payload);

        void send(@Header("mqtt_topic") String topic,
                 @Payload String payload,
                 @Header("mqtt_qos") int qos,
                 @Header("mqtt_retained") boolean retained);
    }

}

