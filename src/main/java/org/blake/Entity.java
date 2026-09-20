package org.blake;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

public class Entity {
    int x, y, size;
    private EntityType type;

    public enum EntityType {
        NEUTRAL(Color.WHITE),
        FRIENDLY(Color.BLUE),
        ENEMY(Color.RED);

        final Color color;

        EntityType(Color color) {
            this.color = color;
        }
    }

    public Entity(int x, int y, int size) {
        this.x = x;
        this.y = y;
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
    public void cycleType() {
        EntityType[] values = EntityType.values();
        int nextIndex = (type.ordinal() + 1) % values.length;
        type = values[nextIndex];
    }
}