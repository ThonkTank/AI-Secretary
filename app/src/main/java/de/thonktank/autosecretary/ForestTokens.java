package de.thonktank.autosecretary;

public final class ForestTokens {
    public final int tree;
    public final float farAlpha;
    public final float middleAlpha;
    public final float frontAlpha;
    public final float sunX;
    public final float sunWidth;
    public final int sunColor;

    public ForestTokens(int tree, float farAlpha, float middleAlpha, float frontAlpha,
                        float sunX, float sunWidth, int sunColor) {
        this.tree = tree;
        this.farAlpha = farAlpha;
        this.middleAlpha = middleAlpha;
        this.frontAlpha = frontAlpha;
        this.sunX = sunX;
        this.sunWidth = sunWidth;
        this.sunColor = sunColor;
    }
}
