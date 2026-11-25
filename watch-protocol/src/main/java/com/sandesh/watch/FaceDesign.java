package com.sandesh.watch;

/** Validated local clock design; independent of SMS and wearable transport. */
public final class FaceDesign {
    public static final String[] STYLES = {"RETRO", "DIGITAL", "ANALOG", "MINIMAL", "RETRO_DUAL"};
    public static final String[] COLORS = {"GREEN", "BLUE", "DARK", "PURPLE", "ROSE", "AMBER"};
    public final String id, name, style, color;
    public final boolean date, battery, seconds, twentyFour;
    public FaceDesign(String id, String name, String style, String color, boolean date, boolean battery, boolean seconds, boolean twentyFour) {
        if (id == null || !id.matches("[a-zA-Z0-9-]{1,64}")) throw new IllegalArgumentException("Invalid design ID");
        if (name == null || name.trim().isEmpty() || name.trim().length() > 24) throw new IllegalArgumentException("Use a face name of 1–24 characters");
        if (!contains(STYLES,style) || !contains(COLORS,color)) throw new IllegalArgumentException("Unknown face style or color");
        this.id=id; this.name=name.trim(); this.style=style; this.color=color;
        this.date=date; this.battery=battery; this.seconds=seconds; this.twentyFour=twentyFour;
    }
    public static FaceDesign retro() { return new FaceDesign("retro-default","Sandesh Retro","RETRO","GREEN",true,true,true,true); }
    private static boolean contains(String[] values,String value) { for (String item:values) if(item.equals(value)) return true; return false; }
    public static int segmentMask(char digit) {
        // Clockwise a,b,c,d,e,f, then middle g.
        switch(digit) { case '0':return 0x3f; case '1':return 0x06; case '2':return 0x5b; case '3':return 0x4f; case '4':return 0x66;
            case '5':return 0x6d; case '6':return 0x7d; case '7':return 0x07; case '8':return 0x7f; case '9':return 0x6f; default:return 0; }
    }
    public static int displayedHour(int hour,boolean twentyFour) { return twentyFour ? hour : (hour%12==0 ? 12 : hour%12); }
}

