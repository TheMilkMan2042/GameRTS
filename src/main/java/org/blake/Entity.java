package org.blake;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

public class Entity {
    int x, y, size;
    int row, col;
    private boolean water;
    private boolean claimed;

    private static final Color LAND_COLOR = new Color(150, 200, 120);
    private static final Color WATER_COLOR = new Color(70, 130, 220);
    private static final Color CLAIMED_COLOR = new Color(240, 200, 60);

    public Entity(int row, int col, int size) {
        this.row = row;
        this.col = col;
        this.size = size;
    }

    public void draw(Graphics g) {
        Color fill = claimed ? CLAIMED_COLOR : (water ? WATER_COLOR : LAND_COLOR);
        g.setColor(fill);
        g.fillRect(x, y, size, size);

        g.setColor(Color.BLACK);
        g.drawRect(x, y, size, size);
    }

    public boolean contains(int px, int py) {
        return new Rectangle(x, y, size, size).contains(px, py);
    }

    public boolean isWater() { return water; }
    public void setWater(boolean water) { this.water = water; }

    public boolean isClaimed() { return claimed; }
    public void claim() { claimed = true; }
}