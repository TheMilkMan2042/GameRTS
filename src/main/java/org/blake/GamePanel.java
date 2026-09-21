package org.blake;

import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class GamePanel extends JPanel{
    private final List<Entity> entities;
    private final int rows;
    private final int cols;
    private final int spacing;
    private final int entitySize;

    public GamePanel(List<Entity> entities, int rows, int cols, int spacing, int entitySize) {
        this.entities = entities;
        this.rows = rows;
        this.cols = cols;
        this.spacing = spacing;
        this.entitySize = entitySize;

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

    private void layoutEntities() {
        int gridWidth = cols * spacing;
        int gridHeight = rows * spacing;

        int startX = (getWidth() - gridWidth) / 2;
        int startY = (getHeight() - gridHeight) / 2;

        int index = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Entity entity = entities.get(index++);
                entity.x = startX + col * spacing;
                entity.y = startY + row * spacing;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        layoutEntities();
        for (Entity entity : entities) {
            entity.draw(g);
        }
    }
}