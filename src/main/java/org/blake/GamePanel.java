package org.blake;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
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
    private final double maxPower;

    private final List<Player> players = new ArrayList<>();
    private final Player human;

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
    private static final int ARROW_WIDTH = 1;

    // --- Terrain generation cleanup — tune these for bigger/smaller landmasses ---
    private static final int MIN_LAND_REGION_SIZE = 15;
    private static final int MIN_WATER_REGION_SIZE = 10;

    // --- Bot tuning ---
    private static final int BOT_THINK_MS = 1000;        // how often each bot acts
    private static final double BOT_MIN_POWER = 6.0;     // bots save up to at least this before acting
    private static final double BOT_BOAT_CHANCE = 0.1;   // chance per turn to try a boat even if land remains
    private static final int BOT_BOAT_SAMPLES = 60;      // random tiles checked when looking for a boat target
    private static final int BOT_MAX_BOAT_DISTANCE = 30; // bots won't sail farther than this

    private static final int[][] ORTHO = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    // Player 0 is the human; extra colors are used by bots in order.
    private static final Color[] PLAYER_COLORS = {
            new Color(240, 200, 60),  // gold (you)
            new Color(210, 50, 50),   // red
            new Color(150, 80, 200),  // purple
            new Color(240, 130, 30),  // orange
            new Color(230, 100, 170), // pink
            new Color(130, 80, 40)    // brown
    };

    private Point dragStart;

    public GamePanel(List<Entity> entities, int rows, int cols, int spacing, long seed, int botCount) {
        this.entities = entities;
        this.rows = rows;
        this.cols = cols;
        this.spacing = spacing;
        this.maxPower = (rows * cols) / 10.0;
        this.seed = seed;
        this.random = new Random(seed);

        generateTerrainCellular();
        assignWaterBodies();

        players.add(new Player(0, "You", PLAYER_COLORS[0], false));
        for (int i = 1; i <= botCount; i++) {
            players.add(new Player(i, "Bot " + i, PLAYER_COLORS[i % PLAYER_COLORS.length], true));
        }
        this.human = players.get(0);

        placeStartingTerritories();

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
                    for (Entity entity : entities) {
                        if (entity.contains(dragEnd.x, dragEnd.y)) {
                            tryClaim(human, entity);
                            break;
                        }
                    }
                } else {
                    expandInDirection(human, dragStart, dx, dy);
                }

                dragStart = null;
            }
        });

        // Every player's power grows with how much land they own.
        Timer powerTimer = new Timer(1000, e -> {
            for (Player p : players) {
                p.power = Math.min(maxPower, p.power + 0.5 + (0.05 * p.tiles.size()));
            }
            repaint();
        });
        powerTimer.start();

        // One think-timer per bot, with staggered start times so they don't all act at once.
        for (Player p : players) {
            if (!p.isBot) continue;
            Timer botTimer = new Timer(BOT_THINK_MS, e -> botTurn(p));
            botTimer.setInitialDelay(500 + random.nextInt(BOT_THINK_MS));
            botTimer.start();
        }
    }

    // ---------- Ownership ----------

    // The ONE place tile ownership changes, so each player's tile set never drifts out of sync.
    // (Attacks and the cut-off-territory rule can reuse this later to transfer tiles.)
    private void claimTile(Entity e, Player p) {
        Player old = e.getOwner();
        if (old != null) old.tiles.remove(e);
        e.setOwner(p);
        if (p != null) p.tiles.add(e);
    }

    private void placeStartingTerritories() {
        double minSeparation = Math.max(10, Math.min(rows, cols) / 3.0);
        List<Entity> centers = new ArrayList<>();

        for (Player p : players) {
            Entity center = null;
            double separation = minSeparation;

            // Try to keep players far apart; relax the distance if the map is too cramped.
            while (center == null) {
                for (int attempt = 0; attempt < 500 && center == null; attempt++) {
                    Entity candidate = entities.get(random.nextInt(entities.size()));
                    if (candidate.isWater()) continue;

                    boolean farEnough = true;
                    for (Entity other : centers) {
                        if (Math.hypot(candidate.row - other.row, candidate.col - other.col) < separation) {
                            farEnough = false;
                            break;
                        }
                    }
                    if (farEnough) center = candidate;
                }
                separation *= 0.5;
            }

            centers.add(center);
            claimCircle(p, center);
        }
    }

    private void claimCircle(Player p, Entity center) {
        int totalTiles = rows * cols;
        double targetArea = totalTiles * 0.0005;
        double radius = Math.sqrt(targetArea / Math.PI);

        for (Entity e : entities) {
            if (e.isWater() || e.getOwner() != null) continue;
            double dist = Math.hypot(e.row - center.row, e.col - center.col);
            if (dist <= radius) {
                claimTile(e, p);
            }
        }
    }

    // ---------- Terrain generation ----------

    private void generateTerrainCellular() {
        for (Entity entity : entities) {
            entity.setWater(random.nextDouble() < 0.575);
        }

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

        while (!queue.isEmpty()) {
            Entity cur = queue.poll();
            region.add(cur);

            for (int[] o : ORTHO) {
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

        while (!queue.isEmpty()) {
            Entity cur = queue.poll();
            cur.setWaterBodyId(id);

            for (int[] o : ORTHO) {
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
        for (int[] o : ORTHO) {
            Entity n = getEntityAt(entity.row + o[0], entity.col + o[1]);
            if (n != null && n.isWater()) ids.add(n.getWaterBodyId());
        }
        return ids;
    }

    // ---------- Expansion (all take the acting player) ----------

    // Unowned, non-water, non-pending tiles touching any of the player's tiles.
    private List<Entity> getFrontier(Player p) {
        Set<Entity> frontier = new LinkedHashSet<>();
        for (Entity t : p.tiles) {
            for (int[] o : ORTHO) {
                Entity n = getEntityAt(t.row + o[0], t.col + o[1]);
                if (n != null && n.getOwner() == null && !n.isWater() && !n.isPending()) {
                    frontier.add(n);
                }
            }
        }
        return new ArrayList<>(frontier);
    }

    // True if the target can be reached by walking over land (unowned or the player's own)
    // from the player's territory within maxRange steps.
    private boolean isLandReachable(Player p, Entity target, int maxRange) {
        boolean[] visited = new boolean[entities.size()];
        Deque<int[]> queue = new ArrayDeque<>(); // {entityIndex, depth}

        for (Entity e : p.tiles) {
            int idx = indexOf(e);
            visited[idx] = true;
            queue.add(new int[]{idx, 0});
        }

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            Entity curEntity = entities.get(cur[0]);
            int depth = cur[1];

            if (curEntity == target) return true;
            if (depth >= maxRange) continue;

            for (int[] o : ORTHO) {
                Entity n = getEntityAt(curEntity.row + o[0], curEntity.col + o[1]);
                if (n != null && !n.isWater() && (n.getOwner() == null || n.getOwner() == p)) {
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

    private int expandToward(Player p, Entity target) {
        int budget = (int) (p.power / 2.0);
        if (budget < 1) return 0;

        budget = Math.min(budget, MAX_CLAIMS_PER_CLICK);

        List<Entity> frontier = getFrontier(p);

        frontier.sort(Comparator.comparingDouble(e ->
                Math.hypot(e.row - target.row, e.col - target.col)
        ));

        if (frontier.size() > MAX_FRONTIER_CANDIDATES) {
            frontier = frontier.subList(0, MAX_FRONTIER_CANDIDATES);
        }

        int claimed = 0;
        for (Entity e : frontier) {
            if (claimed >= budget || p.power < 1.0) break;
            if (e.getOwner() != null) continue;

            claimTile(e, p);
            p.power -= 1.0;
            claimed++;
        }

        repaint();
        return claimed;
    }

    private Entity findEntityAtPoint(Point pt) {
        for (Entity entity : entities) {
            if (entity.contains(pt.x, pt.y)) return entity;
        }
        return null;
    }

    private Entity findNearestOwnedTile(Player p, double refRow, double refCol) {
        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Entity e : p.tiles) {
            double dist = Math.hypot(e.row - refRow, e.col - refCol);
            if (dist < closestDist) {
                closestDist = dist;
                closest = e;
            }
        }
        return closest;
    }

    // Human arrow input: resolve where the drag started, then expand from there.
    private void expandInDirection(Player p, Point startPoint, double dx, double dy) {
        if (p.tiles.isEmpty() || p.power < 1.0) return;

        double length = Math.hypot(dx, dy);
        if (length == 0) return;
        double baseAngle = Math.atan2(dy, dx);

        Entity startTile = findEntityAtPoint(startPoint);
        Entity anchor;
        if (startTile != null && startTile.getOwner() == p) {
            anchor = startTile;
        } else {
            double refRow = (startTile != null) ? startTile.row : rows / 2.0;
            double refCol = (startTile != null) ? startTile.col : cols / 2.0;
            anchor = findNearestOwnedTile(p, refRow, refCol);
        }
        if (anchor == null) return;

        expandAlongAngle(p, anchor, baseAngle);
    }

    // Core arrow logic (bots can call this directly with an anchor tile and an angle).
    private void expandAlongAngle(Player p, Entity anchor, double baseAngle) {
        double centerRow = anchor.row;
        double centerCol = anchor.col;

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
                if (current == null) break;
                if (current.getOwner() != p) {
                    // Land nobody owns is a valid start; water or an enemy's tile blocks this angle.
                    if (!current.isWater() && current.getOwner() == null) {
                        borderTile = current;
                        dirRow = testDirRow;
                        dirCol = testDirCol;
                    }
                    break;
                }
            }

            if (borderTile != null) break;
        }

        if (borderTile == null) return;

        int budget = (int) (p.power / 2.0);
        if (budget < 1) return;
        budget = Math.min(budget, MAX_CLAIMS_PER_CLICK);

        double perpRow = -dirCol;
        double perpCol = dirRow;

        double row = borderTile.row;
        double col = borderTile.col;
        int claimed = 0;
        int lastRow = Integer.MIN_VALUE;
        int lastCol = Integer.MIN_VALUE;
        int steps = 0;

        while (claimed < budget && p.power >= 1.0 && steps < MAX_ARROW_LENGTH) {
            int roundedRow = (int) Math.round(row);
            int roundedCol = (int) Math.round(col);

            if (roundedRow == lastRow && roundedCol == lastCol) {
                row += dirRow;
                col += dirCol;
                steps++;
                continue;
            }

            // The line must not cross water, the map edge, or another player's land.
            Entity centerTile = getEntityAt(roundedRow, roundedCol);
            if (centerTile == null || centerTile.isWater()
                    || (centerTile.getOwner() != null && centerTile.getOwner() != p)) break;

            for (int w = -ARROW_WIDTH; w <= ARROW_WIDTH; w++) {
                if (claimed >= budget || p.power < 1.0) break;

                int tileRow = (int) Math.round(row + perpRow * w);
                int tileCol = (int) Math.round(col + perpCol * w);
                Entity current = getEntityAt(tileRow, tileCol);
                if (current == null || current.isWater() || current.getOwner() != null || current.isPending()) continue;

                claimTile(current, p);
                p.power -= 1.0;
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

    // ---------- Click claiming and boats ----------

    private void tryClaim(Player p, Entity entity) {
        if (entity.getOwner() != null || entity.isWater() || entity.isPending()) return;
        if (p.power < 1.0) return;

        if (isLandReachable(p, entity, MAX_EXPANSION_RANGE)) {
            int claimedCount = expandToward(p, entity);
            if (claimedCount > 0) return;
        }

        if (p.activeBoats >= p.getMaxBoats()) return;

        Entity boatSource = findBoatSource(p, entity);
        if (boatSource != null) {
            launchBoat(p, boatSource, entity);
        }
    }

    private void launchBoat(Player p, Entity source, Entity target) {
        p.power -= 1.0;
        p.activeBoats++;
        target.setPending(true);

        double distance = Math.hypot(target.row - source.row, target.col - source.col);
        int delayMs = (int) (distance * MS_PER_DISTANCE);

        double stormChance = Math.min(0.6, (distance / (rows * cols)));
        boolean sunk = random.nextDouble() < stormChance;

        Timer captureTimer = new Timer(delayMs, ev -> {
            target.setPending(false);
            p.activeBoats--;
            if (!sunk && target.getOwner() == null) {
                claimTile(target, p);
            }
            repaint();
        });
        captureTimer.setRepeats(false);
        captureTimer.start();
        repaint();
    }

    // Nearest tile owned by the player that touches the same water body as the target.
    private Entity findBoatSource(Player p, Entity target) {
        Set<Integer> targetBodies = getAdjacentWaterBodyIds(target);
        if (targetBodies.isEmpty()) return null;

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity candidate : p.tiles) {
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

    // ---------- Bots ----------

    private void botTurn(Player bot) {
        if (bot.tiles.isEmpty()) return;
        if (bot.power < BOT_MIN_POWER) return; // save up for a bigger push

        List<Entity> frontier = getFrontier(bot);

        // Out of land to grab (or randomly feeling adventurous): look for a boat target.
        boolean wantBoat = frontier.isEmpty() || random.nextDouble() < BOT_BOAT_CHANCE;
        if (wantBoat && tryBotBoat(bot)) return;

        if (!frontier.isEmpty()) {
            Entity target = frontier.get(random.nextInt(frontier.size()));
            expandToward(bot, target);
        }
    }

    private boolean tryBotBoat(Player bot) {
        if (bot.activeBoats >= bot.getMaxBoats()) return false;
        if (bot.power < 1.0) return false;

        Set<Integer> myBodies = new HashSet<>();
        for (Entity t : bot.tiles) {
            myBodies.addAll(getAdjacentWaterBodyIds(t));
        }
        if (myBodies.isEmpty()) return false;

        Entity bestTarget = null;
        Entity bestSource = null;
        double bestDist = Double.MAX_VALUE;
        int found = 0;

        // Sample random tiles, keep the closest reachable-by-boat one.
        for (int attempt = 0; attempt < BOT_BOAT_SAMPLES && found < 6; attempt++) {
            Entity cand = entities.get(random.nextInt(entities.size()));
            if (cand.getOwner() != null || cand.isWater() || cand.isPending()) continue;

            Set<Integer> bodies = getAdjacentWaterBodyIds(cand);
            bodies.retainAll(myBodies);
            if (bodies.isEmpty()) continue;

            Entity source = findBoatSource(bot, cand);
            if (source == null) continue;

            found++;
            double dist = Math.hypot(cand.row - source.row, cand.col - source.col);
            if (dist < bestDist) {
                bestDist = dist;
                bestTarget = cand;
                bestSource = source;
            }
        }

        if (bestTarget == null || bestDist > BOT_MAX_BOAT_DISTANCE) return false;

        launchBoat(bot, bestSource, bestTarget);
        return true;
    }

    // ---------- Layout / painting / getters ----------

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
        return human.power;
    }

    public int getActiveBoats() {
        return human.activeBoats;
    }

    public int getMaxBoatsPublic() {
        return human.getMaxBoats();
    }

    public long getSeed() {
        return seed;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
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