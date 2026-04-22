import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WJoyClient {
    private static final int PROTO_PORT = 5070;
    private static final byte[] PROTO_HEADER = "wjoy".getBytes();
    private static final byte PROTO_CONTROL_TYPE = 0x00;
    private static final byte PROTO_DISCOVERY_TYPE = 0x01;

    private static final int CONTROL_PACKET_SIZE = 42;

    private DatagramSocket socket;
    private volatile boolean running;

    private volatile InetAddress controllerAddress;

    /**
     * Starts the UDP server and listens for the ESP32 broadcast packets.
     */
    public void start() throws SocketException {
        running = true;
        controllerAddress = null;

        if (socket != null && !socket.isClosed()) return;
        socket = new DatagramSocket(PROTO_PORT);

        Thread listenerThread = new Thread(() -> {
            byte[] buffer = new byte[256];

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    if (packet.getLength() == 5) {
                        byte[] data = packet.getData();

                        if (data[0] == PROTO_HEADER[0] &&
                                data[1] == PROTO_HEADER[1] &&
                                data[2] == PROTO_HEADER[2] &&
                                data[3] == PROTO_HEADER[3] &&
                                data[4] == PROTO_DISCOVERY_TYPE) {

                            InetAddress newAddress = packet.getAddress();
                            if (controllerAddress == null) {
                                controllerAddress = newAddress;
                            }
                        }
                    }
                } catch (Exception e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
            }
        });

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Stops the listener thread and closes the socket.
     */
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
    }

    /**
     * Returns true if a discovery broadcast from the ESP32 has been received.
     */
    public boolean foundController() {
        return controllerAddress != null;
    }

    /**
     * Sends the control state to the discovered ESP32.
     * * @param data The ControllerData object containing the joystick state
     */
    public void sendControls(ControllerData data) {
        if (!foundController()) return;

        try {
            ByteBuffer buffer = ByteBuffer.allocate(CONTROL_PACKET_SIZE);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            buffer.put(PROTO_HEADER);
            buffer.put(PROTO_CONTROL_TYPE);
            buffer.putInt(data.buttons);
            buffer.putInt(data.lx);
            buffer.putInt(data.ly);
            buffer.putInt(data.unk1);
            buffer.putInt(data.rx);
            buffer.putInt(data.ry);
            buffer.putInt(data.unk2);
            buffer.putInt(data.unk3);
            buffer.putInt(data.unk4);
            buffer.put((byte) data.hat);

            byte[] payload = buffer.array();
            DatagramPacket packet = new DatagramPacket(payload, payload.length, controllerAddress, PROTO_PORT);
            socket.send(packet);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}