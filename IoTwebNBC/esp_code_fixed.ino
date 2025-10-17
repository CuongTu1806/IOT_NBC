#include <ESP8266WiFi.h>
#include <WiFiUdp.h>
#include <NTPClient.h>
#include <PubSubClient.h>
#include <DHT.h>
#include <Wire.h>
#include <BH1750.h>

// ===== WiFi =====
#define WIFI_SSID   ""
#define WIFI_PASS   ""

// ===== MQTT Broker =====
#define BROKER_IP   "192.168.1.11"
#define BROKER_PORT 1883
#define DEVICE_ID   "sensor1"
#define ROOM "room1"
#define MQTT_USER   ""
#define MQTT_PASS   ""

// ===== DHT22 =====
#define DHTPIN   D5
#define DHTTYPE  DHT22
DHT dht(DHTPIN, DHTTYPE);

// ===== BH1750 =====
#define SDA_PIN  D2
#define SCL_PIN  D1
// ADDR thả nổi/GND -> 0x23; nếu ADDR nối 3.3V -> 0x5C
BH1750 lightMeter(0x23);
bool bhReady = false;

// ===== Device pins (KHÔNG dùng D1/D2 vì dành cho I2C) =====
// Chọn các chân rảnh: D6 (GPIO12), D7 (GPIO13), D0 (GPIO16)
#define PIN_FAN          D6
#define PIN_AIRCOND      D7
#define PIN_LIGHT        D0

// Nếu dùng relay module phổ biến (IN kéo xuống sẽ bật) → active-low
#define RELAY_ACTIVE_LOW 1

// Helpers cho điều khiển thiết bị có thể đảo logic
inline void setDeviceState(int pin, bool turnOn){
  if (RELAY_ACTIVE_LOW) {
    digitalWrite(pin, turnOn ? LOW : HIGH);
  } else {
    digitalWrite(pin, turnOn ? HIGH : LOW);
  }
}

inline bool readDeviceIsOn(int pin){
  int level = digitalRead(pin);
  if (RELAY_ACTIVE_LOW) {
    return level == LOW;
  } else {
    return level == HIGH;
  }
}

// ===== MQTT client =====
WiFiClient espClient;
PubSubClient mqtt(espClient);

// ===== NTP =====
// NTP: GMT+7 = 7*3600
WiFiUDP ntpUDP;
// update mỗi 60s, offset +7h
NTPClient timeClient(ntpUDP, "time.nist.gov", 0, 60 * 1000); // dự phòng pool.ntp.org , 1.pool.ntp.org

// ===== Topics =====
char TOPIC_DATASENSOR[64];
char TOPIC_STATUS[64];
char TOPIC_COMMANDS[64];
char TOPIC_STATUS_FAN[80];
char TOPIC_STATUS_AIR[80];
char TOPIC_STATUS_LIGHT[80];
char TOPIC_CMD_FAN[80];
char TOPIC_CMD_AIR[80];
char TOPIC_CMD_LIGHT[80];

unsigned long lastPub = 0;
const unsigned long PUBLISH_INTERVAL_MS = 2000;

// ---------- Helpers ----------
void wifiConnect() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.printf("WiFi: connecting to \"%s\"", WIFI_SSID);
  int guard = 0;
  while (WiFi.status() != WL_CONNECTED && guard < 120) {
    delay(300); Serial.print(".");
    guard++;
  }
  Serial.println();
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("WiFi OK  IP: %s  RSSI: %d dBm\n",
                  WiFi.localIP().toString().c_str(), WiFi.RSSI());
  } else {
    Serial.println("WiFi FAILED. Restarting in 5s...");
    delay(5000);
    ESP.restart();
  }
}

void ntpInit() {
  timeClient.begin();
  Serial.print("Sync NTP");
  for (int i = 0; i < 20 && !timeClient.update(); i++) {
    timeClient.forceUpdate();
    Serial.print(".");
    delay(500);
  }
  Serial.println();
  if (!timeClient.isTimeSet()) {
    Serial.println("NTP not set yet, will keep trying in loop().");
  } else {
    Serial.printf("NTP OK: %s\n", timeClient.getFormattedTime().c_str());
  }
}

void onMqttMessage(char* topic, byte* payload, unsigned int len) {
  Serial.printf("IN  [%s] ", topic);
  for (unsigned int i = 0; i < len; i++) Serial.print((char)payload[i]);
  Serial.println();
  // Xử lý điều khiển thiết bị: on/off/toggle
  String t = String(topic);
  String msg;
  msg.reserve(len + 1);
  for (unsigned int i = 0; i < len; i++) msg += (char)tolower(payload[i]);

  auto publishDeviceStatus = [&](const char* statusTopic, int pin){
    const char* state = readDeviceIsOn(pin) ? "on" : "off";
    mqtt.publish(statusTopic, state, true);
  };

  auto applyAction = [&](int pin, const char* statusTopic){
    if (msg == "on") {
      setDeviceState(pin, true);
    } else if (msg == "off") {
      setDeviceState(pin, false);
    } else if (msg == "toggle") {
      setDeviceState(pin, !readDeviceIsOn(pin));
    } else {
      return; // không rõ lệnh
    }
    publishDeviceStatus(statusTopic, pin);
  };

  if (t == String(TOPIC_CMD_FAN)) {
    applyAction(PIN_FAN, TOPIC_STATUS_FAN);
    return;
  }
  if (t == String(TOPIC_CMD_AIR)) {
    applyAction(PIN_AIRCOND, TOPIC_STATUS_AIR);
    return;
  }
  if (t == String(TOPIC_CMD_LIGHT)) {
    applyAction(PIN_LIGHT, TOPIC_STATUS_LIGHT);
    return;
  }

  // Hỗ trợ lệnh tổng quát: "fan:on", "light:toggle" qua TOPIC_COMMANDS
  if (t == String(TOPIC_COMMANDS)) {
    int sep = msg.indexOf(':');
    if (sep > 0) {
      String dev = msg.substring(0, sep);
      msg = msg.substring(sep + 1);
      if (dev == "fan") {
        applyAction(PIN_FAN, TOPIC_STATUS_FAN);
      } else if (dev == "air" || dev == "airconditioner" || dev == "ac") {
        applyAction(PIN_AIRCOND, TOPIC_STATUS_AIR);
      } else if (dev == "light" || dev == "lamp") {
        applyAction(PIN_LIGHT, TOPIC_STATUS_LIGHT);
      }
    }
  }
}

void mqttReconnect() {
  while (!mqtt.connected()) {
    Serial.print("MQTT connecting... ");
    bool ok = false;
    if (strlen(MQTT_USER) > 0) {
      ok = mqtt.connect(ROOM, MQTT_USER, MQTT_PASS, TOPIC_STATUS, 1, true, "offline");
    } else {
      ok = mqtt.connect(ROOM, TOPIC_STATUS, 1, true, "offline");
    }
    if (ok) {
      Serial.println("connected!");
      mqtt.publish(TOPIC_STATUS, "online", true);
      mqtt.subscribe(TOPIC_COMMANDS, 1);
      mqtt.subscribe(TOPIC_CMD_FAN, 1);
      mqtt.subscribe(TOPIC_CMD_AIR, 1);
      mqtt.subscribe(TOPIC_CMD_LIGHT, 1);
    } else {
      Serial.printf("fail rc=%d; retry in 2s\n", mqtt.state());
      delay(2000);
    }
  }
}

void setup() {
  Serial.begin(115200);
  delay(200);

  snprintf(TOPIC_DATASENSOR, sizeof(TOPIC_DATASENSOR), "devices/%s/data_sensor", ROOM);
  snprintf(TOPIC_STATUS,    sizeof(TOPIC_STATUS),    "devices/%s/status",    ROOM);
  snprintf(TOPIC_COMMANDS,  sizeof(TOPIC_COMMANDS),  "devices/%s/commands",  ROOM);
  // Topic trạng thái và lệnh cho từng thiết bị
  snprintf(TOPIC_STATUS_FAN,   sizeof(TOPIC_STATUS_FAN),   "devices/%s/status/fan",   ROOM);
  snprintf(TOPIC_STATUS_AIR,   sizeof(TOPIC_STATUS_AIR),   "devices/%s/status/air",   ROOM);
  snprintf(TOPIC_STATUS_LIGHT, sizeof(TOPIC_STATUS_LIGHT), "devices/%s/status/light", ROOM);
  snprintf(TOPIC_CMD_FAN,   sizeof(TOPIC_CMD_FAN),   "devices/%s/commands/fan",   ROOM);
  snprintf(TOPIC_CMD_AIR,   sizeof(TOPIC_CMD_AIR),   "devices/%s/commands/air",   ROOM);
  snprintf(TOPIC_CMD_LIGHT, sizeof(TOPIC_CMD_LIGHT), "devices/%s/commands/light", ROOM);

  dht.begin();

  // I2C + BH1750
  Wire.begin(SDA_PIN, SCL_PIN);    // SDA=D2, SCL=D1
  Wire.setClock(100000);           // 100kHz cho chắc
  bhReady = lightMeter.begin(BH1750::CONTINUOUS_HIGH_RES_MODE);
  if (bhReady) {
    Serial.println("BH1750 init OK");
    delay(180); // chờ phép đo đầu tiên
  } else {
    Serial.println("BH1750 init FAILED (check wiring/address)");
  }

  wifiConnect();
  ntpInit();

  mqtt.setServer(BROKER_IP, BROKER_PORT);
  mqtt.setCallback(onMqttMessage);
  mqtt.setKeepAlive(30);
  mqtt.setBufferSize(512);

  // Cấu hình chân điều khiển thiết bị, mặc định OFF
  pinMode(PIN_FAN, OUTPUT);
  pinMode(PIN_AIRCOND, OUTPUT);
  pinMode(PIN_LIGHT, OUTPUT);
  // Đảm bảo trạng thái OFF khi khởi động (đặc biệt với relay active-low)
  setDeviceState(PIN_FAN, false);
  setDeviceState(PIN_AIRCOND, false);
  setDeviceState(PIN_LIGHT, false);

  lastPub = millis();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) wifiConnect();
  if (!mqtt.connected()) mqttReconnect();
  mqtt.loop();

  timeClient.update();

  unsigned long now = millis();
  if (now - lastPub >= PUBLISH_INTERVAL_MS) {
    float t = dht.readTemperature();
    float h = dht.readHumidity();
    if (isnan(t) || isnan(h)) {
      Serial.println("❌ DHT read failed (NaN).");
      // vẫn cập nhật lastPub để giữ nhịp 2s, tránh spam lỗi
      lastPub = now;
      return;
    }

    // Đọc lux (nếu BH1750 sẵn sàng)
    float lux = NAN;
    if (bhReady) {
      lux = lightMeter.readLightLevel();  // lux; một số lib trả -1 khi lỗi
    }

    // timestamp ms
    unsigned long epochSec = timeClient.getEpochTime();
    String tsMsStr = String((unsigned long long)epochSec * 1000ULL);

    // build JSON (nếu lux lỗi -> dùng null)
    String luxStr = (!isnan(lux) && lux >= 0.0f && lux < 200000.0f) ? String(lux,1) : "null";
    String payload = String("{\"ts\":") + tsMsStr +
                     ",\"temp\":" + String(t,2) +
                     ",\"hum\":"  + String(h,2) +
                     ",\"lux\":"  + luxStr + "}";

    bool ok = mqtt.publish(TOPIC_DATASENSOR, payload.c_str(), false);
    Serial.printf("PUB [%s] %s -> %s\n",
                  TOPIC_DATASENSOR, payload.c_str(), ok ? "OK" : "FAIL");

    lastPub = now; // giữ nhịp 2 giây
  }

  yield();
}
