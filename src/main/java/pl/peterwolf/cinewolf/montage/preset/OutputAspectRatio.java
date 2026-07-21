package pl.peterwolf.cinewolf.montage.preset;

public enum OutputAspectRatio {
    LANDSCAPE_16_9(16, 9),
    VERTICAL_9_16(9, 16);

    private final int widthUnits;
    private final int heightUnits;

    OutputAspectRatio(int widthUnits, int heightUnits) {
        this.widthUnits = widthUnits;
        this.heightUnits = heightUnits;
    }

    public int widthUnits() {
        return widthUnits;
    }

    public int heightUnits() {
        return heightUnits;
    }

    public double ratio() {
        return widthUnits / (double) heightUnits;
    }

    public boolean vertical() {
        return heightUnits > widthUnits;
    }
}
