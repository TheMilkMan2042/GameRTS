package org.blake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import static org.blake.GameSettings.*;
import static org.blake.World.ORTHO;

// Builds the map: random noise, smoothing, cleanup of tiny regions, then water body IDs.
public final class TerrainGenerator {
    private TerrainGenerator() {}

    public static void generate(World world, Random random) {
        generateCellular(world, random);
        pruneSmallRegions(world);
        assignWaterBodies(world);
    }

    private static void generateCellular(World world, Random random) {
        List<Entity> entities = world.entities;

        for (Entity entity : entities) {
            entity.setWater(random.nextDouble() < INITIAL_WATER_CHANCE);
        }

        for (int threshold : SMOOTHING_THRESHOLDS) {
            boolean[] nextState = new boolean[entities.size()];
            for (int i = 0; i < entities.size(); i++) {
                nextState[i] = countWaterNeighbors(world, entities.get(i)) >= threshold;
            }
            for (int i = 0; i < entities.size(); i++) {
                entities.get(i).setWater(nextState[i]);
            }
        }
    }

    private static int countWaterNeighbors(World world, Entity entity) {
        int count = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                Entity neighbor = world.getEntityAt(entity.row + dr, entity.col + dc);
                if (neighbor == null || neighbor.isWater()) count++;
            }
        }
        return count;
    }

    // Any connected land patch or water patch smaller than its minimum size is flipped to
    // the opposite terrain, leaving fewer, larger continents and bodies of water.
    private static void pruneSmallRegions(World world) {
        List<Entity> entities = world.entities;
        boolean[] visited = new boolean[entities.size()];

        for (int i = 0; i < entities.size(); i++) {
            if (visited[i]) continue;

            Entity start = entities.get(i);
            boolean isWaterRegion = start.isWater();
            List<Entity> region = floodFillRegion(world, start, visited, isWaterRegion);

            int minSize = isWaterRegion ? MIN_WATER_REGION_SIZE : MIN_LAND_REGION_SIZE;
            if (region.size() < minSize) {
                boolean flipTo = !isWaterRegion;
                for (Entity e : region) {
                    e.setWater(flipTo);
                }
            }
        }
    }

    private static List<Entity> floodFillRegion(World world, Entity start, boolean[] visited, boolean isWaterRegion) {
        List<Entity> region = new ArrayList<>();
        Deque<Entity> queue = new ArrayDeque<>();
        queue.add(start);
        visited[world.indexOf(start)] = true;

        while (!queue.isEmpty()) {
            Entity cur = queue.poll();
            region.add(cur);

            for (int[] o : ORTHO) {
                Entity n = world.getEntityAt(cur.row + o[0], cur.col + o[1]);
                if (n != null && n.isWater() == isWaterRegion && !visited[world.indexOf(n)]) {
                    visited[world.indexOf(n)] = true;
                    queue.add(n);
                }
            }
        }
        return region;
    }

    // Labels every connected patch of water with a shared ID so boats can tell
    // a lake and the ocean apart.
    private static void assignWaterBodies(World world) {
        List<Entity> entities = world.entities;
        boolean[] visited = new boolean[entities.size()];
        int nextId = 0;

        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (e.isWater() && !visited[i]) {
                floodFillWater(world, e, nextId, visited);
                nextId++;
            }
        }
    }

    private static void floodFillWater(World world, Entity start, int id, boolean[] visited) {
        Deque<Entity> queue = new ArrayDeque<>();
        queue.add(start);
        visited[world.indexOf(start)] = true;

        while (!queue.isEmpty()) {
            Entity cur = queue.poll();
            cur.setWaterBodyId(id);

            for (int[] o : ORTHO) {
                Entity n = world.getEntityAt(cur.row + o[0], cur.col + o[1]);
                if (n != null && n.isWater() && !visited[world.indexOf(n)]) {
                    visited[world.indexOf(n)] = true;
                    queue.add(n);
                }
            }
        }
    }
}