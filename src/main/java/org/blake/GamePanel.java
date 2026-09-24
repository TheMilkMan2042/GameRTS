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

    private static final int MAX_FRONTIER_CANDIDATES = 200;
    private static final int MAX_CLAIMS_PER_CLICK = 40;
    private static final int MAX_EXPANSION_RANGE = 20;

    public GamePanel(List<Entity> entities, int rows, int cols, int spacing) {
        this.entities = entities;
        this.rows = rows;
        this.cols = cols;
        this.spacing = spacing;

        generateTerrainCellular();
        assignWaterBodies();

        claimStartingCircle();

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

    private void claimStartingCircle() {
        int totalTiles = rows * cols;
        double targetArea = totalTiles * 0.0005; // 0.05% of the map
        double radius = Math.sqrt(targetArea / Math.PI);

        Entity center;
        do {
            center = entities.get(random.nextInt(entities.size()));
        } while (center.isWater());

        for (Entity e : entities) {
            if (e.isWater()) continue;
            double dist = Math.hypot(e.row - center.row, e.col - center.col);
            if (dist <= radius) {
                e.claim();
            }
        }
    }

    private void generateTerrainCellular() {
        for (Entity entity : entities) {
            entity.setWater(random.nextDouble() < 0.55);
        }

        int smoothingPasses = 6;
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

    private Entity getAdjacentClaimedTile(Entity entity) {
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] o : offsets) {
            Entity n = getEntityAt(entity.row + o[0], entity.col + o[1]);
            if (n != null && n.isClaimed()) return n;
        }
        return null;
    }

    private boolean isLandReachable(Entity target, int maxRange) {
        boolean[] visited = new boolean[entities.size()];
        Deque<int[]> queue = new ArrayDeque<>(); // {entityIndex, depth}

        for (Entity e : entities) {
            if (e.isClaimed()) {
                int idx = indexOf(e);
                visited[idx] = true;
                queue.add(new int[]{idx, 0});
            }
        }

        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            Entity curEntity = entities.get(cur[0]);
            int depth = cur[1];

            if (curEntity == target) return true;
            if (depth >= maxRange) continue;

            for (int[] o : offsets) {
                Entity n = getEntityAt(curEntity.row + o[0], curEntity.col + o[1]);
                if (n != null && !n.isWater()) {
                    int nIdx = indexOf(n);
                    if (!visited[nIdx]) {
                        visited[nIdx] = true;
                        queue.add(new int[]{nIdx, depth + 1});
                    }
                }
            }
        }
        return false;
    }

    private int expandToward(Entity target) {
        int budget = (int) (playerPower / 2.0);
        if (budget < 1) return 0;

        budget = Math.min(budget, MAX_CLAIMS_PER_CLICK);

        List<Entity> frontier = new ArrayList<>();
        for (Entity e : entities) {
            if (e.isClaimed() || e.isWater() || e.isPending()) continue;
            if (getAdjacentClaimedTile(e) != null) {
                frontier.add(e);
            }
        }

        frontier.sort(Comparator.comparingDouble(e ->
                Math.hypot(e.row - target.row, e.col - target.col)
        ));

        if (frontier.size() > MAX_FRONTIER_CANDIDATES) {
            frontier = frontier.subList(0, MAX_FRONTIER_CANDIDATES);
        }

        int claimed = 0;
        for (Entity e : frontier) {
            if (claimed >= budget || playerPower < 1.0) break;
            if (getAdjacentClaimedTile(e) == null) continue;

            e.claim();
            playerPower -= 1.0;
            claimed++;
        }

        repaint();
        return claimed;
    }

    private void tryClaim(Entity entity) {
        if (entity.isClaimed() || entity.isWater() || entity.isPending()) return;
        if (playerPower < 1.0) return;

        if (isLandReachable(entity, MAX_EXPANSION_RANGE)) {
            int claimedCount = expandToward(entity);
            if (claimedCount > 0) return; // land push actually reached it — done
            // otherwise fall through and try a boat instead
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