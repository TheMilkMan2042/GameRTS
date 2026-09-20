package org.blake;

import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class GamePanel extends JPanel{
    private final List<Entity> entities;

    public GamePanel(List<Entity> entities) {
        this.entities = entities;

            addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                for (Entity entity : entities) {
                    if (entity.contains(e.getX(), e.getY())) {
                        System.out.println("(" + e.getX() + " ," + e.getY() + ")");
                        System.out.println("Hit!");
                        entity.cycleType();
                        repaint();
                        break;
                    }
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        for (Entity entity : entities) {
            entity.draw(g);
        }
    }
}