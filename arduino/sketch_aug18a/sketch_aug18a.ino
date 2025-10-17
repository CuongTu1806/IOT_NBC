#include <ESP8266WiFi.h>
#include <WiFiUdp.h>
#include <NTPClient.h>
#include <PubSubClient.h>
#include <DHT.h>
#include <Wire.h>
#include <BH1750.h>

// ===== WiFi =====
// #define WIFI_SSID   "Redmi K50G"
// #define WIFI_PASS   "18042004ccc"

#define WIFI_SSID   "Hoangsiucap"
#define WIFI_PASS   "44448888"

// #define WIFI_SSID   "NGUYEN BA NGOC"
// #define WIFI_PASS   "baocuong1814"

// ===== MQTT Broker =====
#define BROKER_IP   "192.168.0.104"
#define BROKER_PORT 1883
#define ROOM        "room1"   
#define MQTT_USER   "cuong123"
#define MQTT_PASS   "cuong123"

// ===== DHT22 =====
#define DHTPIN   D5
#define DHTTYPE  DHT22
DHT dht(DHTPIN, DHTTYPE);

// ===== BH1750 (I2C on D2/D1) =====
#define SDA_PIN  D2
#define SCL_PIN  D1

BH1750 lightMeter(0x23);
bool bhReady = false;

// ===== GPIO điều khiển thiết bị =====
// D6 (GPIO12), D7 (GPIO13), D0 (GPIO16)
#define PIN_FAN          D6
#define PIN_AIRCOND      D7
#define PIN_LIGHT        D0


#define RELAY_ACTIVE_LOW 1

inline void setDeviceState(int pin, bool turnOn){
  if (RELAY_ACTIVE_LOW) digitalWrite(pin, turnOn ? LOW : HIGH);
  else                  digitalWrite(pin, turnOn ? HIGH : LOW);
}
inline bool readDeviceIsOn(int pin){
  int level = digitalRead(pin);
  return RELAY_ACTIVE_LOW ? (level == LOW) : (level == HIGH);
}

// ===== MQTT client =====
WiFiClient espClient;
PubSubClient mqtt(espClient);

// ===== NTP =====
WiFiUDP ntpUDP;
// offset 0s, cập nhật mỗi 60s
NTPClient timeClient(ntpUDP, "time.nist.gov", 0, 60 * 1000);

// ===== Topics =====
char TOPIC_DATASENSOR[64];  
char TOPIC_STATUS[64];        
char TOPIC_COMMANDS[64];    

unsigned long lastPub = 0;
const unsigned long PUBLISH_INTERVAL_MS = 2000;

// ---------- WiFi/NTP ----------
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
    Serial.printf("WiFi OK  IP: %s  RSSI: %d dBm\n",WiFi.localIP().toString().c_str(), WiFi.RSSI());
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

// lấy value từ command json ({"key":"value"})
static String extractJsonStringValue(const String& srcLower, int keyPos) {
  if (keyPos < 0) return "";
  int colon = srcLower.indexOf(':', keyPos);
  if (colon < 0) return "";
  int q1 = srcLower.indexOf('"', colon + 1);
  if (q1 < 0) return "";
  int q2 = srcLower.indexOf('"', q1 + 1);
  if (q2 < 0) return "";
  return srcLower.substring(q1 + 1, q2);
}

// pub Status khi bật tắt thành công
void publishStatusEvent(const char* deviceName, bool isOn) {
  unsigned long epochSec = timeClient.getEpochTime();
  unsigned long long tsMs = (unsigned long long)epochSec * 1000ULL;

  String payload = String("{\"device\":\"") + deviceName + "\",\"action\":\"" + (isOn ? "on" : "off") + "\",\"ts\":" + String(tsMs) + "}";

  bool ok = mqtt.publish(TOPIC_STATUS, payload.c_str(), true );
  Serial.printf("PUB [%s] %s -> %s\n", TOPIC_STATUS, payload.c_str(), ok ? "OK" : "FAIL");

}

// ===== MQTT callback =====
void onMqttMessage(char* topic, byte* payload, unsigned int len) {
  Serial.printf("IN  [%s] ", topic);
  for (unsigned int i = 0; i < len; i++) Serial.print((char)payload[i]);
  Serial.println();

  if (String(topic) != String(TOPIC_COMMANDS)) return;

  // Build & normalize message (lowercase)
  String msg; 
  msg.reserve(len + 1);
  for (unsigned int i = 0; i < len; i++) msg += (char)payload[i];
  String low = msg; low.toLowerCase();

  int dKey = low.indexOf("\"device\"");
  int aKey = low.indexOf("\"action\"");
  if (dKey < 0 || aKey < 0) {
    Serial.println("⚠️ JSON thiếu device/action (yêu cầu {\"device\":\"fan\",\"action\":\"on\"})");
    return;
  }

  String dev = extractJsonStringValue(low, dKey);
  String act = extractJsonStringValue(low, aKey);

  auto applyAction = [&](int pin, const char* devName){
    if      (act == "on")  setDeviceState(pin, true);
    else if (act == "off") setDeviceState(pin, false);
    else { Serial.println("⚠️ Action chỉ hỗ trợ on/off"); return; }
    publishStatusEvent(devName, readDeviceIsOn(pin));
  };

  if(dev == "fan") applyAction(PIN_FAN, "fan");
  else if(dev == "air" || dev == "airconditioner" || dev == "ac") applyAction(PIN_AIRCOND, "air");
  else if (dev == "light" || dev == "lamp") applyAction(PIN_LIGHT, "light");
  else Serial.println("⚠️ Unknown device (fan|air|light)");
}

// ===== MQTT connect/reconnect =====
void mqttReconnect() {
  while (!mqtt.connected()) {
    Serial.print("MQTT connecting... ");
    bool ok = false;
    // Last Will: nếu ESP rớt, broker phát retained {"online":false}
    const char* lastWill = "{\"online\":false}";
    if (strlen(MQTT_USER) > 0) {
      ok = mqtt.connect(ROOM, MQTT_USER, MQTT_PASS, TOPIC_STATUS, 1, true, lastWill);
    } else {
      ok = mqtt.connect(ROOM, TOPIC_STATUS, 1, true, lastWill);
    }
    if (ok) {
      Serial.println("connected!");
      mqtt.subscribe(TOPIC_COMMANDS, 1); 
      // phát một event online (tuỳ chọn)
      unsigned long epochSec = timeClient.getEpochTime();
      unsigned long long tsMs = (unsigned long long)epochSec * 1000ULL;
      String onlinePayload = String("{\"online\":true,\"ts\":") + String(tsMs) + "}";
      mqtt.publish(TOPIC_STATUS, onlinePayload.c_str(), true);
    } else {
      Serial.printf("fail rc=%d; retry in 2s\n", mqtt.state());
      delay(2000);
    }
  }
}

// ===== Setup/Loop =====
void setup() {
  Serial.begin(115200);
  delay(200);

  // Topics
  snprintf(TOPIC_DATASENSOR, sizeof(TOPIC_DATASENSOR), "%s/data_sensor", ROOM);
  snprintf(TOPIC_STATUS,     sizeof(TOPIC_STATUS),     "%s/status",      ROOM);    // duy nhất
  snprintf(TOPIC_COMMANDS,   sizeof(TOPIC_COMMANDS),   "%s/commands",    ROOM);

  dht.begin();

  // I2C + BH1750
  Wire.begin(SDA_PIN, SCL_PIN);    // SDA=D2, SCL=D1
  Wire.setClock(100000);
  bhReady = lightMeter.begin(BH1750::CONTINUOUS_HIGH_RES_MODE);
  if (bhReady) { Serial.println("BH1750 init OK"); delay(180); }
  else { Serial.println("BH1750 init FAILED (check wiring/address)"); }

  wifiConnect();
  ntpInit();

  mqtt.setServer(BROKER_IP, BROKER_PORT);
  mqtt.setCallback(onMqttMessage);
  mqtt.setKeepAlive(30);
  mqtt.setBufferSize(512);

  // cấu hình chân điều khiển thiết bị, mặc định OFF
  pinMode(PIN_FAN, OUTPUT);
  pinMode(PIN_AIRCOND, OUTPUT);
  pinMode(PIN_LIGHT, OUTPUT);
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
      lastPub = now; // giữ nhịp 2s, tránh spam
      return;
    }

    float lux = NAN;
    if (bhReady) lux = lightMeter.readLightLevel(); // lux

    unsigned long epochSec = timeClient.getEpochTime();
    unsigned long long ms = (unsigned long long)epochSec * 1000ULL;

    String luxStr = (!isnan(lux) && lux >= 0.0f && lux < 200000.0f) ? String(lux, 0) : "null";
    String payload = String("{\"ts\":") + String(ms) +
                     ",\"temp\":" + String(t, 1) +
                     ",\"hum\":"  + String(h, 0) +
                     ",\"lux\":"  + luxStr + "}";

    bool ok = mqtt.publish(TOPIC_DATASENSOR, payload.c_str(), false);
    Serial.printf("PUB [%s] %s -> %s\n",
                  TOPIC_DATASENSOR, payload.c_str(), ok ? "OK" : "FAIL");

    lastPub = now;
  }

  yield();
}
