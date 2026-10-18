package org.blake;

import java.awt.Point;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// The tile grid and the small lookups everything else shares.
public class World {
    public static final int[][] ORTHO = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    final List<Entity> entities;
    final int rows;
    final int cols;

    public World(List<Entity> entities, int rows, int cols) {
        this.entities = entities;
        this.rows = rows;
        this.cols = cols;
    }

    public Entity getEntityAt(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) return null;
        return entities.get(row * cols + col);
    }

    public int indexOf(Entity e) {
        return e.row * cols + e.col;
    }

    // The tile under a screen pixel (positions are set each time the panel paints).
    public Entity findEntityAtPoint(Point pt) {
        for (Entity entity : entities) {
            if (entity.contains(pt.x, pt.y)) return entity;
        }
        return null;
    }

    // IDs of every water body touching this tile's 4 sides.
    public Set<Integer> getAdjacentWaterBodyIds(Entity entity) {
        Set<Integer> ids = new HashSet<>();
        for (int[] o : ORTHO) {
            Entity n = getEntityAt(entity.row + o[0], entity.col + o[1]);
            if (n != null && n.isWater()) ids.add(n.getWaterBodyId());
        }
        return ids;
    }
}