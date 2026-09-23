package org.blake;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;

public class GamePanel extends JPanel {
    private final List<Entity> entities;
    private final int rows;
    private final int cols;
    private final int spacing;
    private final Random random = new Random();

    private double playerPower = 1.0;
    private int activeBoats = 0;
    private static final int MS_PER_DISTANCE = 250;

    public GamePanel(List<Entity> entities, int rows, int cols, int spacing) {
        this.entities = entities;
        this.rows = rows;
        this.cols = cols;
        this.spacing = spacing;

        generateTerrainCellular();
        assignWaterBodies();

        Entity start;
        do {
            start = entities.get(random.nextInt(entities.size()));
        } while (start.isWater());
        start.claim();

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

        Timer powerTimer = new Timer(1000, e -> {
            int ownedTiles = countOwnedTiles();
            double rate = 0.5 + (0.05 * ownedTiles);
            playerPower += rate;
            repaint();
        });
        powerTimer.start();
    }

    private int countOwnedTiles() {
        int count = 0;
        for (Entity entity : entities) {
            if (entity.isClaimed()) count++;
        }
        return count;
    }

    private void generateTerrainCellular() {
        for (Entity entity : entities) {
            entity.setWater(random.nextDouble() < 0.55);
        }

        int smoothingPasses = 3;
        for (int pass = 0; pass < smoothingPasses; pass++) {
            boolean[] nextState = new boolean[entities.size()];
            for (int i = 0; i < entities.size(); i++) {
                nextState[i] = countWaterNeighbors(entities.get(i)) >= 5;
            }
            for (int i = 0; i < entities.size(); i++) {
                entities.get(i).setWater(nextState[i]);
            }
        }
    }

    private int countWaterNeighbors(Entity entity) {
        int count = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                Entity neighbor = getEntityAt(entity.row + dr, entity.col + dc);
                if (neighbor == null || neighbor.isWater()) count++;
            }
        }
        return count;
    }

    private void assignWaterBodies() {
        boolean[] visited = new boolean[entities.size()];
        int nextId = 0;

        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (e.isWater() && !visited[i]) {
                floodFillWater(e, nextId, visited);
                nextId++;
            }
        }
    }

    private void floodFillWater(Entity start, int id, boolean[] visited) {
        Deque<Entity> queue = new ArrayDeque<>();
        queue.add(start);
        visited[indexOf(start)] = true;

        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!queue.isEmpty()) {
            Entity cur = queue.poll();
            cur.setWaterBodyId(id);

            for (int[] o : offsets) {
                Entity n = getEntityAt(cur.row + o[0], cur.col + o[1]);
                if (n != null && n.isWater() && !visited[indexOf(n)]) {
                    visited[indexOf(n)] = true;
                    queue.add(n);
                }
            }
        }
    }

    private int indexOf(Entity e) {
        return e.row * cols + e.col;
    }

    private Set<Integer> getAdjacentWaterBodyIds(Entity entity) {
        Set<Integer> ids = new HashSet<>();
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] o : offsets) {
            Entity n = getEntityAt(entity.row + o[0], entity.col + o[1]);
            if (n != null && n.isWater()) ids.add(n.getWaterBodyId());
        }
        return ids;
    }

    // Finds the specific claimed neighbor touching this tile (used to determine chain direction)
    private Entity getAdjacentClaimedTile(Entity entity) {
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] o : offsets) {
            Entity n = getEntityAt(entity.row + o[0], entity.col + o[1]);
            if (n != null && n.isClaimed()) return n;
        }
        return null;
    }

    private int getTerritorySideLength() {
        int owned = countOwnedTiles();
        int side = (int) Math.sqrt(owned) / 2;
        return Math.max(0, Math.min(2, side));
    }

    // Claims tiles in a straight line starting at 'target', extending away from 'source',
    // until roughly half of current power is spent or an invalid tile is hit.
    private void claimChain(Entity source, Entity target) {
        int dRow = Integer.signum(target.row - source.row);
        int dCol = Integer.signum(target.col - source.col);

        int perpRow = -dCol;
        int perpCol = dRow;

        int totalBudget = (int) (playerPower / 2.0);
        if (totalBudget < 1) return; // not enough power to expand at all — do nothing

        int numSideLines = getTerritorySideLength();
        int totalLines = (numSideLines * 2) + 1;
        int perLineBudget = Math.max(1, totalBudget / totalLines);
        int remainder = totalBudget - (perLineBudget * totalLines);

        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 1; i <= numSideLines; i++) {
            offsets.add(i);
            offsets.add(-i);
        }

        boolean first = true;
        for (int offset : offsets) {
            if (playerPower < 1.0) break;

            int startRow = target.row + (perpRow * offset);
            int startCol = target.col + (perpCol * offset);

            int lineBudget = perLineBudget;
            if (first) {
                lineBudget += remainder;
                first = false;
            }

            int row = startRow;
            int col = startCol;

            while (lineBudget > 0 && playerPower >= 1.0) {
                Entity current = getEntityAt(row, col);
                if (current == null || current.isWater() || current.isClaimed() || current.isPending()) break;

                current.claim();
                playerPower -= 1.0;
                lineBudget--;

                row += dRow;
                col += dCol;
            }
        }

        repaint();
    }

    private void tryClaim(Entity entity) {
        if (entity.isClaimed() || entity.isWater() || entity.isPending()) return;
        if (playerPower < 1.0) return;

        Entity landSource = getAdjacentClaimedTile(entity);
        if (landSource != null) {
            claimChain(landSource, entity);
            return;
        }

        if (activeBoats >= getMaxBoats()) return;

        Entity boatSource = findBoatSource(entity);
        if (boatSource != null) {
            playerPower -= 1.0;
            activeBoats++;
            entity.setPending(true);

            double distance = Math.hypot(entity.row - boatSource.row, entity.col - boatSource.col);
            int delayMs = (int) (distance * MS_PER_DISTANCE);

            double stormChance = Math.min(0.6, distance * 0.05);
            boolean sunk = random.nextDouble() < stormChance;

            Timer captureTimer = new Timer(delayMs, e -> {
                entity.setPending(false);
                activeBoats--;
                if (!sunk) {
                    entity.claim();
                }
                repaint();
            });
            captureTimer.setRepeats(false);
            captureTimer.start();
            repaint();
        }
    }

    private int getMaxBoats() {
        return (int) (0.05 * playerPower) + 1;
    }

    private Entity findBoatSource(Entity target) {
        Set<Integer> targetBodies = getAdjacentWaterBodyIds(target);
        if (targetBodies.isEmpty()) return null;

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity candidate : entities) {
            if (!candidate.isClaimed()) continue;

            Set<Integer> candidateBodies = getAdjacentWaterBodyIds(candidate);
            candidateBodies.retainAll(targetBodies);
            if (candidateBodies.isEmpty()) continue;

            double dist = Math.hypot(target.row - candidate.row, target.col - candidate.col);
            if (dist < closestDist) {
                closestDist = dist;
                closest = candidate;
            }
        }
        return closest;
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

    public double getPlayerPower() {
        return playerPower;
    }

    public int getActiveBoats() {
        return activeBoats;
    }

    public int getMaxBoatsPublic() {
        return getMaxBoats();
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