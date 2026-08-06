#include <arduino.h>
#include <esp_wifi.h>
#include <WiFi.h>
#include <WiFiUdp.h>

#include "USB.h"
#include "USBHIDGamepad.h"

#define PROTO_HEADER            "wjoy"
#define PROTO_PORT              5070
#define PROTO_CONTROL_TYPE      0x00
#define PROTO_DISCOVERY_TYPE    0x01
#define DISCOVERY_PACKET_PERIOD 5000

typedef struct __attribute__((packed)) {
    uint8_t hdr[4];
    uint8_t type;
    uint32_t buttons;
    int32_t x, y, z, rx, ry, rz, t, r;
    uint8_t hat;
} ControlPacket;

typedef struct __attribute__((packed)) {
    uint8_t hdr[4];
    uint8_t type;
} DiscoveryPacket;

WiFiUDP WiFiUdp;
USBHIDGamepad Gamepad;

SemaphoreHandle_t dataMutex;
ControlPacket latestControl;
volatile bool newControlData = false;

void __attribute__((noinline)) discardPacket(int32_t size) {
    uint8_t discard[2048];

    if (size < sizeof(discard)) {
        WiFiUdp.read(discard, size);
        return;
    }

    while (size > 0) {
        WiFiUdp.read(discard, size > sizeof(discard) ? sizeof(discard) : size);
        size -= sizeof(discard);
    }
}

void udpTask(void* param) {
    int32_t timerDiscovery = 0;
    int32_t previousMillis = (int) millis();

    while (true) {
        while (const int32_t incomingPacketSize = WiFiUdp.parsePacket()) {
            if (incomingPacketSize == sizeof(ControlPacket)) {
                ControlPacket incomingPacket;
                WiFiUdp.read((uint8_t*) &incomingPacket, sizeof(incomingPacket));

                if (*(uint32_t*)incomingPacket.hdr == *(uint32_t*)PROTO_HEADER && incomingPacket.type == PROTO_CONTROL_TYPE) {
                    xSemaphoreTake(dataMutex, portMAX_DELAY);
                    latestControl = incomingPacket;
                    newControlData = true;
                    xSemaphoreGive(dataMutex);
                }
            } else {
                discardPacket(incomingPacketSize);
            }
        }

        int32_t now = (int) millis();
        timerDiscovery -= now - previousMillis;
        previousMillis = now;
        if (timerDiscovery <= 0) {
            timerDiscovery = DISCOVERY_PACKET_PERIOD;

            DiscoveryPacket discoveryPacket;
            memcpy(discoveryPacket.hdr, PROTO_HEADER, sizeof(discoveryPacket.hdr));
            discoveryPacket.type = PROTO_DISCOVERY_TYPE;

            WiFiUdp.beginPacket("255.255.255.255", PROTO_PORT);
            WiFiUdp.write((uint8_t*) &discoveryPacket, sizeof(discoveryPacket));
            WiFiUdp.endPacket();
        }

        vTaskDelay(1);
    }
}

void setup() {
    setCpuFrequencyMhz(240);
    WiFi.persistent(false);
    WiFiClass::mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    while (WiFiClass::status() != WL_CONNECTED) delay(100);

    esp_wifi_set_ps(WIFI_PS_NONE);

    Gamepad.begin();
    USB.PID(0x0268);
    USB.VID(0x054C);
    USB.productName("PS3 Controller DualShock 3");
    USB.manufacturerName("Sony Corporation");
    USB.begin();

    yield();

    WiFiUdp.begin(PROTO_PORT);

    dataMutex = xSemaphoreCreateMutex();
    xTaskCreate(udpTask, "udpTask", 4096, NULL, 2, NULL);
}

void loop() {
    if (newControlData) {
        ControlPacket pkt;

        xSemaphoreTake(dataMutex, portMAX_DELAY);
        pkt = latestControl;
        newControlData = false;
        xSemaphoreGive(dataMutex);

        Gamepad.send(
            (int8_t) pkt.x, (int8_t) pkt.y,
            (int8_t) pkt.rx, (int8_t) pkt.ry,
            (int8_t) pkt.t, (int8_t) pkt.r,
            pkt.hat % 9, pkt.buttons
        );
    }

    vTaskDelay(1);
}
