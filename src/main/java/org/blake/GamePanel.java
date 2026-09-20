package org.blake;

import javax.swing.JPanel;
import java.awt.Graphics;

public class GamePanel extends JPanel{
    private final Entity entity;

    public GamePanel(Entity entity) {
        this.entity = entity;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        entity.draw(g);
    }
}