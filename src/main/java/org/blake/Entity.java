package org.blake;

import java.awt.Color;
import java.awt.Graphics;

public class Entity {
    int x, y, size;

    public Entity(int x, int y, int size) {
        this.x = x;
        this.y = y;
        this.size = size;
    }

    public void draw(Graphics g) {
        g.setColor(Color.BLUE);
        g.fillRect(x, y, size, size);

        g.setColor(Color.BLACK);
        g.drawRect(x, y, size, size);
    }
}