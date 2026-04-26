import com.raylib.Colors;
import com.raylib.Raylib;

import java.net.SocketException;

public class App {
    private final WJoyClient client = new WJoyClient();
    private final Raylib.Color background;

    private App() throws SocketException {
        client.start();

        Raylib.SetConfigFlags(Raylib.FLAG_WINDOW_RESIZABLE | Raylib.FLAG_WINDOW_ALWAYS_RUN);
        Raylib.InitWindow(300, 175, "WiFiPad");
        Raylib.SetWindowMinSize(300, 175);

        background = new Raylib.Color();
        background.a((byte) 0xFF).r((byte) 0x12).g((byte) 0).b((byte) 0x13);
    }

    private App loop() {
        try (Raylib.Rectangle loader = new Raylib.Rectangle()) {
            int currentController = 0;
            float[] loaderProgress = new float[]{ 0 };
            final String loadingText = "Searching client...";
            final int loadingTextW = Raylib.MeasureText(loadingText, 20);
            ControllerData lastData = new ControllerData();
            boolean firstClientConnection = true;
            float refreshTimer = 0.0f;
            while (isRunning()) {
                final boolean hasClient = client.foundController();
                loader.x(Raylib.GetScreenWidth() / 4.f).y(Raylib.GetScreenHeight() / 2.f - 4);
                loader.width(Raylib.GetScreenWidth() / 2.f).height(8);
                loaderProgress[0] += Raylib.GetFrameTime() * 10;
                if (loaderProgress[0] > 100) loaderProgress[0] = 0;

                Raylib.BeginDrawing();
                Raylib.ClearBackground(background);

                if (!hasClient) {
                    Raylib.DrawText(loadingText, Raylib.GetScreenWidth() / 2 - loadingTextW / 2, Raylib.GetScreenHeight() / 2 - 20 - 4, 20, Colors.WHITE);
                    Raylib.GuiProgressBar(loader, "", "", loaderProgress, 0, 100);
                } else {
                    String frequencyText = "Please connect a gamepad!";
                    if (Raylib.IsGamepadAvailable(0)) frequencyText = String.format("Available frequency: %d", Raylib.GetFPS());
                    int frequencyTextW = Raylib.MeasureText(frequencyText, 20);
                    Raylib.DrawText(frequencyText, Raylib.GetScreenWidth() / 2 - frequencyTextW / 2, Raylib.GetScreenHeight() / 2 - 10, 20, Colors.WHITE);
                }
                Raylib.EndDrawing();

                if (hasClient) {
                    if (firstClientConnection) {
                        firstClientConnection = false;
                        client.sendControls(lastData);
                    } else {
                        refreshTimer -= Raylib.GetFrameTime();
                    }

                    ControllerData data = new ControllerData();
                    if (Raylib.IsGamepadAvailable(currentController)) {
                        data.setButton(0, Raylib.IsGamepadButtonDown(currentController, Raylib.GAMEPAD_BUTTON_RIGHT_FACE_LEFT));
                        data.setButton(1, Raylib.IsGamepadButtonDown(currentController, Raylib.GAMEPAD_BUTTON_RIGHT_FACE_DOWN));
                        data.setButton(2, Raylib.IsGamepadButtonDown(currentController, Raylib.GAMEPAD_BUTTON_RIGHT_FACE_RIGHT));
                        data.setButton(3, Raylib.IsGamepadButtonDown(currentController, Raylib.GAMEPAD_BUTTON_RIGHT_FACE_UP));

                        data.setButton(4, Raylib.IsGamepadButtonDown(currentController, Raylib.GAMEPAD_BUTTON_LEFT_TRIGGER_1));
                        data.setButton(5, Raylib.IsGamepadButtonDown(currentController, Raylib.GAMEPAD_BUTTON_RIGHT_TRIGGER_1));
                        data.setButton(6, !(Raylib.GetGamepadAxisMovement(currentController, Raylib.GAMEPAD_AXIS_LEFT_TRIGGER) <= 0.75));
                        data.setButton(7, !(Raylib.GetGamepadAxisMovement(currentController, Raylib.GAMEPAD_AXIS_RIGHT_TRIGGER) <= 0.75));

                        data.setButton(8, Raylib.IsGamepadButtonDown(currentController, Raylib.GAMEPAD_BUTTON_MIDDLE_LEFT));
                        data.setButton(9, Raylib.IsGamepadButtonDown(currentController, Raylib.GAMEPAD_BUTTON_MIDDLE_RIGHT));

                        data.setButton(10, Raylib.IsGamepadButtonDown(currentController, Raylib.GAMEPAD_BUTTON_LEFT_THUMB));
                        data.setButton(11, Raylib.IsGamepadButtonDown(currentController, Raylib.GAMEPAD_BUTTON_RIGHT_THUMB));

                        data.lx = (int) (Raylib.GetGamepadAxisMovement(currentController, 0) * 127);
                        data.ly = (int) (Raylib.GetGamepadAxisMovement(currentController, 1) * 127);
                        data.rx = (int) (Raylib.GetGamepadAxisMovement(currentController, 2) * 127);
                        data.ry = (int) (Raylib.GetGamepadAxisMovement(currentController, 3) * 127);
                        if (data.lx > 127) data.lx = 127; else if (data.lx < -127) data.lx = -127;
                        if (data.ly > 127) data.ly = 127; else if (data.ly < -127) data.ly = -127;
                        if (data.rx > 127) data.rx = 127; else if (data.rx < -127) data.rx = -127;
                        if (data.ry > 127) data.ry = 127; else if (data.ry < -127) data.ry = -127;

                        final boolean dpadUp = Raylib.IsGamepadButtonDown(currentController, Raylib.GAMEPAD_BUTTON_LEFT_FACE_UP);
                        final boolean dpadRight = Raylib.IsGamepadButtonDown(currentController, Raylib.GAMEPAD_BUTTON_LEFT_FACE_RIGHT);
                        final boolean dpadDown = Raylib.IsGamepadButtonDown(currentController, Raylib.GAMEPAD_BUTTON_LEFT_FACE_DOWN);
                        final boolean dpadLeft = Raylib.IsGamepadButtonDown(currentController, Raylib.GAMEPAD_BUTTON_LEFT_FACE_LEFT);
                        if (dpadUp && dpadRight) data.hat = 2;
                        else if (dpadRight && dpadDown) data.hat = 4;
                        else if (dpadDown && dpadLeft) data.hat = 6;
                        else if (dpadLeft && dpadUp) data.hat = 8;
                        else if (dpadUp) data.hat = 1;
                        else if (dpadRight) data.hat = 3;
                        else if (dpadDown) data.hat = 5;
                        else if (dpadLeft) data.hat = 7;
                    }

                    if (!data.equals(lastData)) {
                        client.sendControls(data);
                        lastData = data;
                    }

                    if (refreshTimer <= 0) {
                        lastData = data;
                        client.sendControls(lastData);
                        refreshTimer = 0.5f;
                    }
                }
            }
        }

        return this;
    }

    private void close() {
        client.stop();
        Raylib.CloseWindow();
    }

    private boolean isRunning() {
        return !Raylib.WindowShouldClose();
    }

    public static void main(String[] args) throws SocketException {
        new App().loop().close();
    }
}
