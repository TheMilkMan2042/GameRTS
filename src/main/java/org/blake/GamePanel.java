package org.blake;

import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class GamePanel extends JPanel{
    private final Entity entity;

    public GamePanel(Entity entity) {
        this.entity = entity;

            addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                System.out.println("(" + e.getX() + " ," + e.getY() + ")");
                if (entity.contains(e.getX(), e.getY())) {
                    System.out.println("Hit!");
                    entity.cycleType();
                    repaint();
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        entity.draw(g);
    }
}