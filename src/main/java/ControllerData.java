public class ControllerData {
    public int buttons;
    public int lx, ly, unk1, rx, ry, unk2, unk3, unk4, hat;

    @SuppressWarnings("unused")
    public void setButton(int buttonIndex, boolean isPressed) {
        if (buttonIndex < 0 || buttonIndex > 31) return;
        if (isPressed) {
            buttons |= (1 << buttonIndex);
        } else {
            buttons &= ~(1 << buttonIndex);
        }
    }

    private boolean isSmallDiff(int a, int b) {
        return Math.abs(a - b) < 10;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj instanceof ControllerData) {
            final ControllerData obj2 = (ControllerData) obj;
            return obj2.hat == this.hat && obj2.unk3 == this.unk3 &&
                    obj2.unk4 == this.unk4 &&
                    isSmallDiff(obj2.ly, this.ly) && isSmallDiff(obj2.lx, this.lx) &&
                    isSmallDiff(obj2.rx, this.rx) && isSmallDiff(obj2.ry, this.ry) &&
                    obj2.unk1 == this.unk1 && obj2.unk2 == this.unk2 && obj2.buttons == this.buttons;
        }
        return false;
    }
}