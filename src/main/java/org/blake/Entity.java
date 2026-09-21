package org.blake;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

public class Entity {
    int x, y, size;
    int row, col;
    private EntityType type;

    public enum EntityType {
        NEUTRAL(Color.WHITE),
        PLAYER(Color.BLUE);

        final Color color;

        EntityType(Color color) {
            this.color = color;
        }
    }

    public Entity(int row, int col, int size) {
        this.row = row;
        this.col = col;
        this.size = size;
        this.type = EntityType.NEUTRAL;
    }

    public void draw(Graphics g) {
        g.setColor(type.color);
        g.fillRect(x, y, size, size);

        g.setColor(Color.BLACK);
        g.drawRect(x, y, size, size);
    }

    public boolean contains(int px, int py) {
        return new Rectangle(x, y, size, size).contains(px, py);
    }

    public boolean isClaimed() {
        return type == EntityType.PLAYER;
    }

    public void claim() {
        type = EntityType.PLAYER;
    }
}