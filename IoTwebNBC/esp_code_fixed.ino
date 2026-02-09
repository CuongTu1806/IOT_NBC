#include <ESP8266WiFi.h>
#include <WiFiUdp.h>
#include <NTPClient.h>
#include <PubSubClient.h>
#include <DHT.h>
#include <Wire.h>
#include <BH1750.h>

// ===== WiFi =====
#define WIFI_SSID   "Redmi K50G"
#define WIFI_PASS   "18042004ccc"

// ===== MQTT Broker =====
#define BROKER_IP   "192.168.116.123"
#define BROKER_PORT 1883
#define DEVICE_ID   "sensor1"
#define ROOM "room1"
#define MQTT_USER   "cuong123"
#define MQTT_PASS   "cuong123"
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
// Không dùng D8 (GPIO15) vì có vấn đề boot
#define PIN_FAN          D6
#define PIN_AIRCOND      D7
#define PIN_LIGHT        D0

// ===== LED cảnh báo =====
// D3: LED ngoài (Active-HIGH), D4: LED onboard (Active-LOW)
#define PIN_LED_RAIN     D3  // LED cảnh báo mưa - LED ngoài
#define PIN_LED_WIND     D4  // LED cảnh báo gió - LED onboard

// Ngưỡng cảnh báo
#define RAIN_THRESHOLD   50
#define WIND_THRESHOLD   25

// Nếu dùng relay module phổ biến (IN kéo xuống sẽ bật) → active-low
// Đổi sang 0 nếu dùng LED thường (HIGH = sáng)
// Đổi sang 1 nếu LED mắc ngược (LOW = tắt, HIGH = sáng)
#define RELAY_ACTIVE_LOW 1

// Hỗ trợ cấu hình polarity theo từng chân (nếu một chân mắc ngược so với các chân khác)
// 1 = active-low (LOW = ON), 0 = active-high (HIGH = ON)
#define RELAY_FAN_ACTIVE_LOW   1
#define RELAY_AIR_ACTIVE_LOW   0  // <-- FIX: chân D7 (AIR) mắc ngược, đảo logic tại đây
#define RELAY_LIGHT_ACTIVE_LOW 1

inline bool isPinActiveLow(int pin){
  if (pin == PIN_FAN) return RELAY_FAN_ACTIVE_LOW;
  if (pin == PIN_AIRCOND) return RELAY_AIR_ACTIVE_LOW;
  if (pin == PIN_LIGHT) return RELAY_LIGHT_ACTIVE_LOW;
  return RELAY_ACTIVE_LOW; // fallback
}

// Helpers cho điều khiển thiết bị có thể đảo logic theo từng pin
inline void setDeviceState(int pin, bool turnOn){
  bool activeLow = isPinActiveLow(pin);
  if (activeLow) {
    digitalWrite(pin, turnOn ? LOW : HIGH);
  } else {
    digitalWrite(pin, turnOn ? HIGH : LOW);
  }
  delay(10);  // Đảm bảo pin được set
}

inline bool readDeviceIsOn(int pin){
  int level = digitalRead(pin);
  bool activeLow = isPinActiveLow(pin);
  if (activeLow) {
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
const char* TOPIC_DATASENSOR = "devices/room1/data_sensor";
const char* TOPIC_STATUS = "devices/room1/status";
const char* TOPIC_COMMANDS = "devices/room1/commands";
const char* TOPIC_ALERT = "devices/room1/alert";
const char* TOPIC_CMD_FAN = "devices/room1/commands/fan";
const char* TOPIC_CMD_AIR = "devices/room1/commands/air";
const char* TOPIC_CMD_LIGHT = "devices/room1/commands/light";

unsigned long lastPub = 0;
const unsigned long PUBLISH_INTERVAL_MS = 2000;

// ===== LED cảnh báo =====
bool ledRainOn = false;
bool ledWindOn = false;

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

  auto publishDeviceStatus = [&](const char* deviceName, int pin){
    const char* state = readDeviceIsOn(pin) ? "on" : "off";
    // Tạo JSON payload: {"device":"fan","state":"on"}
    String payload = String("{\"device\":\"") + deviceName + 
                     "\",\"state\":\"" + state + "\"}";
    mqtt.publish(TOPIC_STATUS, payload.c_str(), true);
    Serial.printf("Published status: %s\n", payload.c_str());
  };

  auto applyAction = [&](int pin, const char* deviceName){
    if (msg == "on") {
      setDeviceState(pin, true);
      Serial.printf("Set pin %d = ON (level=%d)\n", pin, digitalRead(pin));
    } else if (msg == "off") {
      setDeviceState(pin, false);
      Serial.printf("Set pin %d = OFF (level=%d)\n", pin, digitalRead(pin));
    } else if (msg == "toggle") {
      setDeviceState(pin, !readDeviceIsOn(pin));
      Serial.printf("Toggle pin %d (level=%d)\n", pin, digitalRead(pin));
    } else {
      return; // không rõ lệnh
    }
    publishDeviceStatus(deviceName, pin);
  };

  if (t == String(TOPIC_CMD_FAN)) {
    applyAction(PIN_FAN, "fan");
    return;
  }
  if (t == String(TOPIC_CMD_AIR)) {
    applyAction(PIN_AIRCOND, "air");
    return;
  }
  if (t == String(TOPIC_CMD_LIGHT)) {
    applyAction(PIN_LIGHT, "light");
    return;
  }

  
  if (t == String(TOPIC_COMMANDS)) {
    int sep = msg.indexOf(':');
    if (sep > 0) {
      String dev = msg.substring(0, sep);
      msg = msg.substring(sep + 1);
      if (dev == "fan") {
        applyAction(PIN_FAN, "fan");
      } else if (dev == "air" || dev == "airconditioner" || dev == "ac") {
        applyAction(PIN_AIRCOND, "air");
      } else if (dev == "light" || dev == "lamp") {
        applyAction(PIN_LIGHT, "light");
      }
    }
  }

  // Xử lý lệnh LED cảnh báo từ backend
  if (t == String(TOPIC_ALERT)) {
    Serial.printf("Received ALERT command: %s\n", msg.c_str());
    
    int sep = msg.indexOf(':');
    if (sep > 0) {
      String led = msg.substring(0, sep);
      String state = msg.substring(sep + 1);
      bool turnOn = (state == "on" || state == "blink");
      
      Serial.printf("Parsed LED: %s, State: %s, TurnOn: %d\n", led.c_str(), state.c_str(), turnOn);
      
      if (led == "led_rain" || led == "rain") {
        ledRainOn = turnOn;
        digitalWrite(PIN_LED_RAIN, ledRainOn ? HIGH : LOW); 
        Serial.printf("LED Rain: %s (Pin D3=%d)\n", ledRainOn ? "ON" : "OFF", digitalRead(PIN_LED_RAIN));
      } else if (led == "led_wind" || led == "wind" || led == "windy") {
        ledWindOn = turnOn;
        digitalWrite(PIN_LED_WIND, ledWindOn ? LOW : HIGH); 
        Serial.printf("LED Wind: %s (Pin D4=%d)\n", ledWindOn ? "ON" : "OFF", digitalRead(PIN_LED_WIND));
      } else {
        Serial.printf("Unknown LED command: %s\n", led.c_str());
      }
    }
    return;  // Đã xử lý xong, thoát
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
      mqtt.subscribe(TOPIC_ALERT, 1);
      Serial.printf("Subscribed to ALERT: %s\n", TOPIC_ALERT);
      mqtt.subscribe(TOPIC_CMD_FAN, 1);
      Serial.printf("Subscribed to FAN: %s\n", TOPIC_CMD_FAN);
      mqtt.subscribe(TOPIC_CMD_AIR, 1);
      Serial.printf("Subscribed to AIR: %s\n", TOPIC_CMD_AIR);
      mqtt.subscribe(TOPIC_CMD_LIGHT, 1);
      Serial.printf("Subscribed to LIGHT: %s\n", TOPIC_CMD_LIGHT);
    } else {
      Serial.printf("fail rc=%d; retry in 2s\n", mqtt.state());
      delay(2000);
    }
  }
}

void setup() {
  Serial.begin(115200);
  delay(200);

  Serial.println("=== MQTT Topics ===");
  Serial.printf("FAN CMD: %s\n", TOPIC_CMD_FAN);
  Serial.printf("AIR CMD: %s\n", TOPIC_CMD_AIR);
  Serial.printf("LIGHT CMD: %s\n", TOPIC_CMD_LIGHT);

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
  
  // In log trạng thái khởi động
  Serial.printf("Device init: FAN(D6)=%d, AIR(D7)=%d, LIGHT(D0)=%d\n", 
                digitalRead(PIN_FAN), digitalRead(PIN_AIRCOND), digitalRead(PIN_LIGHT));

  // Cấu hình LED cảnh báo
  // D3: LED ngoài (Active-HIGH: LOW=OFF, HIGH=ON)
  // D4: LED onboard (Active-LOW: HIGH=OFF, LOW=ON)
  pinMode(PIN_LED_RAIN, OUTPUT);
  pinMode(PIN_LED_WIND, OUTPUT);
  digitalWrite(PIN_LED_RAIN, LOW);   // OFF
  digitalWrite(PIN_LED_WIND, HIGH);  // OFF

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
    
    // Random rain (0-100) và windy (0-50)
    int rain = random(0, 101);  // 0 đến 100
    int windy = random(0, 51);  // 0 đến 50
    
    String payload = String("{\"ts\":") + tsMsStr +
                     ",\"temp\":" + String(t,2) +
                     ",\"hum\":"  + String(h,2) +
                     ",\"lux\":"  + luxStr +
                     ",\"rain\":" + String(rain) +
                     ",\"windy\":" + String(windy) + "}";

    bool ok = mqtt.publish(TOPIC_DATASENSOR, payload.c_str(), false);
    Serial.printf("PUB [%s] %s -> %s\n",
                  TOPIC_DATASENSOR, payload.c_str(), ok ? "OK" : "FAIL");

    lastPub = now; // giữ nhịp 2 giây
  }

  yield();
}
