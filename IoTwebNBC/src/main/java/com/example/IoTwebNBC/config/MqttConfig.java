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

            // Chia 2 nhánh nhận topic. 1 topic datasensor, 2 topic status
            if(topic !=  null && topic.endsWith("/data_sensor")){
                try {
                    handleDataSensor(topic, payload);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            else if(topic !=  null && topic.endsWith("/status")){
                try {
                    handleStatus(topic, payload);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

        };
    }

    private void handleDataSensor(String topic, String payload) throws Exception {
                    // lấy topic trong msg do adapter set vào

            System.out.println("MQTT IN ["+topic+"] ID:" + " Payload: " + payload);

            // topic: {roomId}/datasensor
            //Tách topic thành các phần string tách bở dấu /
            String[] parts = topic.split("/");
            String room = parts.length >= 2 ? parts[0] : "unknown";

            try {
                JsonNode n = om.readTree(payload);
                Double temp = n.has("temp") && n.get("temp").isNumber() ? n.get("temp").asDouble() : null;
                Double hum  = n.has("hum")  && n.get("hum").isNumber()  ? n.get("hum").asDouble()  : null;
                Double light  = n.has("lux")  && n.get("lux").isNumber()  ? n.get("lux").asDouble()  : null;

                // ts từ ESP là epoch millis UTC → đổi sang giờ VN để lưu dạng LocalDateTime
                LocalDateTime tsLocal = null;
                if (n.has("ts") && n.get("ts").canConvertToLong()) {
                    long tsMillis = n.get("ts").asLong();
                    ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
                    tsLocal = Instant.ofEpochMilli(tsMillis).atZone(VN).toLocalDateTime();
                }

                // Kiểm tra duplicate dựa trên deviceId + timestamp + dữ liệu
                if (tsLocal != null) {
                    // Tìm bản ghi gần nhất của device này
                    var existing = dataSensorRepository.findTop1ByRoomOrderByTimestampDesc(room);
                    if (!existing.isEmpty()) {
                        var lastRecord = existing.get(0);

                        // Kiểm tra xem có phải duplicate thực sự không
                        boolean isDuplicate = false;

                        if (lastRecord.getTimestamp() != null) {
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
                        .room(room)
                        .build();
                dataSensorRepository.save(dse);
                System.out.println("Saved datasensor: " + dse);
            } catch (Exception e) {
                System.err.println("Parse error: " + e.getMessage());
                e.printStackTrace();
            }
    }

    private void handleStatus(String topic, String payload) throws Exception {

        // lưu vào db
        String[] parts = topic.split("/");
        String room = parts.length >= 2 ? parts[0] : "unknown";

        try {
            JsonNode n = om.readTree(payload);
            String action = n.get("action").isTextual() ? n.get("action").asText() : null;
            String device = n.get("device").isTextual() ? n.get("device").asText() : null;
            // ts từ ESP là epoch millis UTC → đổi sang giờ VN để lưu dạng LocalDateTime
            LocalDateTime action_time = null;
            if (n.has("ts") && n.get("ts").canConvertToLong()) {
                long tsMillis = n.get("ts").asLong();
                ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
                action_time = Instant.ofEpochMilli(tsMillis).atZone(VN).toLocalDateTime();
            }

            DeviceActionEntity dae = DeviceActionEntity.builder()
                    .room(room)
                    .status(action)
                    .timestamp(action_time)
                    .device(device)
                    .build();
            deviceActionRepository.save(dae);
            System.out.println("Saved device action: " + dae);
        }catch (Exception e) {
            System.err.println("Parse error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("STATUS [" + topic + "] " + payload);
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

