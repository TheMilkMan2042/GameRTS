package org.blake;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Random;

public class GamePanel extends JPanel {
    private final List<Entity> entities;
    private final int rows;
    private final int cols;
    private final int spacing;

    private int playerPower = 1; // enough to make your first claim

    public GamePanel(List<Entity> entities, int rows, int cols, int spacing) {
        this.entities = entities;
        this.rows = rows;
        this.cols = cols;
        this.spacing = spacing;

        // Claim one random starting tile
        Random random = new Random();
        entities.get(random.nextInt(entities.size())).claim();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                for (Entity entity : entities) {
                    if (entity.contains(e.getX(), e.getY())) {
                        tryClaim(entity);
                        break;
                    }
                }
            }
        });

        // Power regenerates over time — adjust as needed
        Timer powerTimer = new Timer(3000, e -> {
            playerPower++;
            repaint();
        });
        powerTimer.start();
    }

    private void tryClaim(Entity entity) {
        if (entity.isClaimed()) return;
        if (playerPower < 1) return;
        if (!isAdjacentToClaimed(entity)) return;

        entity.claim();
        playerPower--;
        repaint();
    }

    private boolean isAdjacentToClaimed(Entity entity) {
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] offset : offsets) {
            Entity neighbor = getEntityAt(entity.row + offset[0], entity.col + offset[1]);
            if (neighbor != null && neighbor.isClaimed()) return true;
        }
        return false;
    }

    private Entity getEntityAt(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) return null;
        return entities.get(row * cols + col);
    }

    private void layoutEntities() {
        int gridWidth = cols * spacing;
        int gridHeight = rows * spacing;
        int startX = (getWidth() - gridWidth) / 2;
        int startY = (getHeight() - gridHeight) / 2;

        for (Entity entity : entities) {
            entity.x = startX + entity.col * spacing;
            entity.y = startY + entity.row * spacing;
        }
    }

    public int getPlayerPower() {
        return playerPower;
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