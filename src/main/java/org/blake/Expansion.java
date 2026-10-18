package org.blake;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.blake.GameSettings.*;
import static org.blake.World.ORTHO;

// Everything about growing a player's territory over land.
public class Expansion {
    private final Game game;
    private final World world;

    Expansion(Game game) {
        this.game = game;
        this.world = game.world;
    }

    // Unowned, non-water, non-pending tiles touching any of the player's tiles.
    List<Entity> getFrontier(Player p) {
        Set<Entity> frontier = new LinkedHashSet<>();
        for (Entity t : p.tiles) {
            for (int[] o : ORTHO) {
                Entity n = world.getEntityAt(t.row + o[0], t.col + o[1]);
                if (n != null && n.getOwner() == null && !n.isWater() && !n.isPending()) {
                    frontier.add(n);
                }
            }
        }
        return new ArrayList<>(frontier);
    }

    // True if the target can be reached by walking over land (unowned or the player's own)
    // from the player's territory within maxRange steps.
    boolean isLandReachable(Player p, Entity target, int maxRange) {
        boolean[] visited = new boolean[world.entities.size()];
        Deque<int[]> queue = new ArrayDeque<>(); // {entityIndex, depth}

        for (Entity e : p.tiles) {
            int idx = world.indexOf(e);
            visited[idx] = true;
            queue.add(new int[]{idx, 0});
        }

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            Entity curEntity = world.entities.get(cur[0]);
            int depth = cur[1];

            if (curEntity == target) return true;
            if (depth >= maxRange) continue;

            for (int[] o : ORTHO) {
                Entity n = world.getEntityAt(curEntity.row + o[0], curEntity.col + o[1]);
                if (n != null && !n.isWater() && (n.getOwner() == null || n.getOwner() == p)) {
                    int nIdx = world.indexOf(n);
                    if (!visited[nIdx]) {
                        visited[nIdx] = true;
                        queue.add(new int[]{nIdx, depth + 1});
                    }
                }
            }
        }
        return false;
    }

    // Click expansion: border tiles closest to the target claim first, so territory
    // bulges toward wherever was clicked. Spends up to half the player's power.
    int expandToward(Player p, Entity target) {
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

            game.claimTile(e, p);
            p.power -= 1.0;
            claimed++;
        }

        game.requestRepaint();
        return claimed;
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
    void expandInDirection(Player p, Point startPoint, double dx, double dy) {
        if (p.tiles.isEmpty() || p.power < 1.0) return;

        double length = Math.hypot(dx, dy);
        if (length == 0) return;
        double baseAngle = Math.atan2(dy, dx);

        Entity startTile = world.findEntityAtPoint(startPoint);
        Entity anchor;
        if (startTile != null && startTile.getOwner() == p) {
            anchor = startTile;
        } else {
            double refRow = (startTile != null) ? startTile.row : world.rows / 2.0;
            double refCol = (startTile != null) ? startTile.col : world.cols / 2.0;
            anchor = findNearestOwnedTile(p, refRow, refCol);
        }
        if (anchor == null) return;

        expandAlongAngle(p, anchor, baseAngle);
    }

    // Core arrow logic (bots can call this directly with an anchor tile and an angle).
    void expandAlongAngle(Player p, Entity anchor, double baseAngle) {
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
            int maxSteps = world.rows + world.cols;

            for (int i = 0; i < maxSteps; i++) {
                stepRow += testDirRow;
                stepCol += testDirCol;
                Entity current = world.getEntityAt((int) Math.round(stepRow), (int) Math.round(stepCol));
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
            Entity centerTile = world.getEntityAt(roundedRow, roundedCol);
            if (centerTile == null || centerTile.isWater()
                    || (centerTile.getOwner() != null && centerTile.getOwner() != p)) break;

            for (int w = -ARROW_WIDTH; w <= ARROW_WIDTH; w++) {
                if (claimed >= budget || p.power < 1.0) break;

                int tileRow = (int) Math.round(row + perpRow * w);
                int tileCol = (int) Math.round(col + perpCol * w);
                Entity current = world.getEntityAt(tileRow, tileCol);
                if (current == null || current.isWater() || current.getOwner() != null || current.isPending()) continue;

                game.claimTile(current, p);
                p.power -= 1.0;
                claimed++;
            }

            lastRow = roundedRow;
            lastCol = roundedCol;
            row += dirRow;
            col += dirCol;
            steps++;
        }

        game.requestRepaint();
    }
}