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

WiFiUDP WiFiUdp;
USBHIDGamepad Gamepad;

int32_t timerDiscovery = 0;
int32_t previousMillis = 0;

void setup() {
    WiFi.persistent(false);
    WiFiClass::mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    yield();

    Gamepad.begin();
    USB.PID(0x0268);
    USB.VID(0x054C);
    USB.productName("PS3 Controller DualShock 3");
    USB.manufacturerName("Sony Corporation");
    USB.begin();

    yield();

    WiFiUdp.begin(PROTO_PORT);
    previousMillis = (int) millis();
}

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

void loop() {
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

    yield();

    const int32_t incomingPacketSize = WiFiUdp.parsePacket();
    if (incomingPacketSize == sizeof(ControlPacket)) {
        ControlPacket incomingPacket;
        WiFiUdp.read((uint8_t*) &incomingPacket, sizeof(incomingPacket));
        if (memcmp(incomingPacket.hdr, PROTO_HEADER, sizeof(incomingPacket.hdr)) == 0 && incomingPacket.type == PROTO_CONTROL_TYPE) {
            Gamepad.send((int8_t) incomingPacket.x, (int8_t) incomingPacket.y, (int8_t) incomingPacket.rx, (int8_t) incomingPacket.ry, (int8_t) incomingPacket.t, (int8_t) incomingPacket.r, incomingPacket.hat % 9, incomingPacket.buttons);
        }
    } else discardPacket(incomingPacketSize);

    timerDiscovery -= (int) millis() - previousMillis;
    previousMillis = (int) millis();
    if (timerDiscovery <= 0) {
        timerDiscovery = DISCOVERY_PACKET_PERIOD;

        DiscoveryPacket discoveryPacket;
        memcpy(discoveryPacket.hdr, PROTO_HEADER, sizeof(discoveryPacket.hdr));
        discoveryPacket.type = PROTO_DISCOVERY_TYPE;

        WiFiUdp.beginPacket("255.255.255.255", PROTO_PORT);
        WiFiUdp.write((uint8_t*) &discoveryPacket, sizeof(discoveryPacket));
        WiFiUdp.endPacket();
    }
}
