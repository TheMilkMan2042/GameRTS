package org.blake;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;

public class GamePanel extends JPanel {
    private final List<Entity> entities;
    private final int rows;
    private final int cols;
    private final int spacing;
    private final long seed;
    private final Random random;

    private double playerPower = 1.0;
    private int activeBoats = 0;
    private static final int MS_PER_DISTANCE = 250;

    private static final int MAX_FRONTIER_CANDIDATES = 200;
    private static final int MAX_CLAIMS_PER_CLICK = 40;
    private static final int MAX_EXPANSION_RANGE = 20;

    // Minimum drag distance (in pixels) before a press-release is treated as a
    // directional arrow instead of a simple click.
    private static final int DRAG_THRESHOLD = 15;

    // Caps how many tiles deep an arrow expansion can reach, regardless of power budget.
    private static final int MAX_ARROW_LENGTH = 15;
    // How many tiles wide the arrow's line is, on EACH side of the center line.
    // 0 = single-tile-wide line, 1 = 3 tiles wide, 2 = 5 tiles wide, etc.
    private static final int ARROW_WIDTH = 1;

    // --- Terrain generation cleanup — tune these for bigger/smaller landmasses ---
    // Any connected patch of land smaller than this many tiles gets converted to water
    // (removes stray islands and thin tendrils, leaving larger continents standing).
    private static final int MIN_LAND_REGION_SIZE = 15;
    // Any connected patch of water smaller than this many tiles gets converted to land
    // (removes stray single-tile ponds/puddles, smoothing out coastlines).
    private static final int MIN_WATER_REGION_SIZE = 10;

    private Point dragStart;

    public GamePanel(List<Entity> entities, int rows, int cols, int spacing, long seed) {
        this.entities = entities;
        this.rows = rows;
        this.cols = cols;
        this.spacing = spacing;
        this.seed = seed;
        this.random = new Random(seed);

        generateTerrainCellular();
        assignWaterBodies();

        claimStartingCircle();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragStart == null) return;

                Point dragEnd = e.getPoint();
                double dx = dragEnd.x - dragStart.x;
                double dy = dragEnd.y - dragStart.y;
                double dragDistance = Math.hypot(dx, dy);

                if (dragDistance < DRAG_THRESHOLD) {
                    // Short movement — treat as a normal click toward a specific tile
                    for (Entity entity : entities) {
                        if (entity.contains(dragEnd.x, dragEnd.y)) {
                            tryClaim(entity);
                            break;
                        }
                    }
                } else {
                    // Long movement — treat as a directional arrow expansion
                    expandInDirection(dragStart, dx, dy);
                }

                dragStart = null;
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
            entity.setWater(random.nextDouble() < 0.575);
        }

        // Early passes use a strict majority threshold, aggressively cutting stray
        // isolated points. Later passes use a slightly looser threshold, which fills
        // in gaps and rounds out coastlines rather than leaving thin winding strands.
        int[] thresholds = {5, 5, 5, 5, 6};
        for (int threshold : thresholds) {
            boolean[] nextState = new boolean[entities.size()];
            for (int i = 0; i < entities.size(); i++) {
                nextState[i] = countWaterNeighbors(entities.get(i)) >= threshold;
            }
            for (int i = 0; i < entities.size(); i++) {
                entities.get(i).setWater(nextState[i]);
            }
        }

        pruneSmallRegions();
    }

    // Finds every connected land patch and every connected water patch; any patch
    // smaller than the configured minimum size gets flipped to the opposite terrain.
    // This is what removes leftover thin tendrils and stray single-tile ponds,
    // leaving a smaller number of larger, cleaner landmasses and bodies of water.
    private void pruneSmallRegions() {
        boolean[] visited = new boolean[entities.size()];

        for (int i = 0; i < entities.size(); i++) {
            if (visited[i]) continue;

            Entity start = entities.get(i);
            boolean isWaterRegion = start.isWater();
            List<Entity> region = floodFillRegion(start, visited, isWaterRegion);

            int minSize = isWaterRegion ? MIN_WATER_REGION_SIZE : MIN_LAND_REGION_SIZE;
            if (region.size() < minSize) {
                boolean flipTo = !isWaterRegion;
                for (Entity e : region) {
                    e.setWater(flipTo);
                }
            }
        }
    }

    private List<Entity> floodFillRegion(Entity start, boolean[] visited, boolean isWaterRegion) {
        List<Entity> region = new ArrayList<>();
        Deque<Entity> queue = new ArrayDeque<>();
        queue.add(start);
        visited[indexOf(start)] = true;

        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!queue.isEmpty()) {
            Entity cur = queue.poll();
            region.add(cur);

            for (int[] o : offsets) {
                Entity n = getEntityAt(cur.row + o[0], cur.col + o[1]);
                if (n != null && n.isWater() == isWaterRegion && !visited[indexOf(n)]) {
                    visited[indexOf(n)] = true;
                    queue.add(n);
                }
            }
        }
        return region;
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

    private Entity findEntityAtPoint(Point p) {
        for (Entity entity : entities) {
            if (entity.contains(p.x, p.y)) return entity;
        }
        return null;
    }

    private Entity findNearestClaimedTileToPosition(double refRow, double refCol) {
        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Entity e : entities) {
            if (!e.isClaimed()) continue;
            double dist = Math.hypot(e.row - refRow, e.col - refCol);
            if (dist < closestDist) {
                closestDist = dist;
                closest = e;
            }
        }
        return closest;
    }

    // Directional "arrow" expansion: takes a raw drag vector (dx, dy in screen pixels),
    // finds where that direction exits your territory from its center, then lays down
    // a straight line of claims continuing in that same direction from there.
    private void expandInDirection(Point startPoint, double dx, double dy) {
        if (countOwnedTiles() == 0) return;
        if (playerPower < 1.0) return;

        double length = Math.hypot(dx, dy);
        if (length == 0) return;
        double baseAngle = Math.atan2(dy, dx);

        // Anchor the search on wherever the drag actually started, not the territory's
        // overall centroid — a centroid can sit outside an irregularly-shaped territory
        // (horseshoes, thin coastlines, separate blobs), which was causing dead-end failures.
        Entity startTile = findEntityAtPoint(startPoint);
        Entity anchor;
        if (startTile != null && startTile.isClaimed()) {
            anchor = startTile;
        } else {
            double refRow = (startTile != null) ? startTile.row : rows / 2.0;
            double refCol = (startTile != null) ? startTile.col : cols / 2.0;
            anchor = findNearestClaimedTileToPosition(refRow, refCol);
        }
        if (anchor == null) return; // no claimed territory found at all

        double centerRow = anchor.row;
        double centerCol = anchor.col;

        // Try the exact drag direction first, then widen the search to nearby angles
        // if the exact direction happens to exit territory onto water or off the map.
        double[] angleOffsetsDeg = {0, 10, -10, 20, -20, 30, -30, 45, -45};
        Entity borderTile = null;
        double dirRow = 0, dirCol = 0;

        for (double offsetDeg : angleOffsetsDeg) {
            double angle = baseAngle + Math.toRadians(offsetDeg);
            double testDirRow = Math.sin(angle);
            double testDirCol = Math.cos(angle);

            double stepRow = centerRow;
            double stepCol = centerCol;
            int maxSteps = rows + cols;

            for (int i = 0; i < maxSteps; i++) {
                stepRow += testDirRow;
                stepCol += testDirCol;
                Entity current = getEntityAt((int) Math.round(stepRow), (int) Math.round(stepCol));
                if (current == null) break; // walked off the map on this angle
                if (!current.isClaimed()) {
                    if (!current.isWater()) {
                        borderTile = current;
                        dirRow = testDirRow;
                        dirCol = testDirCol;
                    }
                    break; // this angle is resolved (land found, or blocked by water) — stop scanning it
                }
            }

            if (borderTile != null) break; // found a usable land border, stop widening the cone
        }

        if (borderTile == null) return; // every nearby angle hit water or the map edge

        int budget = (int) (playerPower / 2.0);
        if (budget < 1) return;
        budget = Math.min(budget, MAX_CLAIMS_PER_CLICK);

        // Perpendicular direction, for claiming tiles to either side of the center line.
        double perpRow = -dirCol;
        double perpCol = dirRow;

        double row = borderTile.row;
        double col = borderTile.col;
        int claimed = 0;
        int lastRow = Integer.MIN_VALUE;
        int lastCol = Integer.MIN_VALUE;
        int steps = 0;

        while (claimed < budget && playerPower >= 1.0 && steps < MAX_ARROW_LENGTH) {
            int roundedRow = (int) Math.round(row);
            int roundedCol = (int) Math.round(col);

            // Shallow diagonal angles can round to the same tile two steps in a row —
            // skip forward instead of re-checking (and falsely failing on) that tile.
            if (roundedRow == lastRow && roundedCol == lastCol) {
                row += dirRow;
                col += dirCol;
                steps++;
                continue;
            }

            // The line itself must not cross water or the map edge — only the width
            // offsets around a valid center tile may be skipped individually.
            Entity centerTile = getEntityAt(roundedRow, roundedCol);
            if (centerTile == null || centerTile.isWater()) break;

            // Claim the center tile, then tiles offset to each side (perpendicular to travel).
            for (int w = -ARROW_WIDTH; w <= ARROW_WIDTH; w++) {
                if (claimed >= budget || playerPower < 1.0) break;

                int tileRow = (int) Math.round(row + perpRow * w);
                int tileCol = (int) Math.round(col + perpCol * w);
                Entity current = getEntityAt(tileRow, tileCol);
                if (current == null || current.isWater() || current.isClaimed() || current.isPending()) continue;

                current.claim();
                playerPower -= 1.0;
                claimed++;
            }

            lastRow = roundedRow;
            lastCol = roundedCol;
            row += dirRow;
            col += dirCol;
            steps++;
        }

        repaint();
    }

    private void tryClaim(Entity entity) {
        if (entity.isClaimed() || entity.isWater() || entity.isPending()) return;
        if (playerPower < 1.0) return;

        if (isLandReachable(entity, MAX_EXPANSION_RANGE)) {
            int claimedCount = expandToward(entity);
            if (claimedCount > 0) return;
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

    public long getSeed() {
        return seed;
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