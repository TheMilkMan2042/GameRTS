package org.blake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.blake.World.ORTHO;

// The cut-off rule: if enemy land splits one of your sections away from the rest of your
// territory, the enemy takes that section.
//
// Two pieces of your land count as connected if you can walk between them without crossing
// another player's land. Free land and water do NOT cut you off, so boat colonies and
// pieces separated only by unclaimed land are safe. Your largest connected piece always stays yours.
public class CutOffRule {
    private final Game game;
    private final World world;

    // Players whose land may have just been split (they lost a tile, or an enemy claimed next to them).
    private final Set<Player> dirty = new LinkedHashSet<>();
    private boolean resolving = false;

    CutOffRule(Game game) {
        this.game = game;
        this.world = game.world;
    }

    void markDirty(Player p) {
        if (p != null) dirty.add(p);
    }

    // Called once at the end of each action, so a big expansion or attack is checked as a batch.
    void resolveAll() {
        if (resolving || dirty.isEmpty()) return;
        resolving = true;
        try {
            int rounds = 0;
            while (!dirty.isEmpty() && rounds++ < 20 && !game.isGameOver()) {
                Player p = dirty.iterator().next();
                dirty.remove(p);
                resolvePlayer(p);
            }
            dirty.clear();
        } finally {
            resolving = false;
        }
    }

    private void resolvePlayer(Player p) {
        if (p.tiles.size() < 2) return;

        // Label connected groups of the player's land, walking over anything that isn't an enemy's land.
        int[] label = new int[world.entities.size()]; // 0 = not visited yet
        List<Group> groups = new ArrayList<>();

        for (Entity start : p.tiles) {
            if (label[world.indexOf(start)] != 0) continue;

            Group g = floodGroup(p, start, label, groups.size() + 1);
            if (g.tiles.size() == p.tiles.size()) return; // everything is connected: nothing cut off
            groups.add(g);
        }
        if (groups.size() < 2) return;

        // The biggest group stays; every other group goes to the enemy bordering it most.
        Group main = groups.get(0);
        for (Group g : groups) {
            if (g.tiles.size() > main.tiles.size()) main = g;
        }

        for (Group g : groups) {
            if (g == main) continue;

            Player receiver = strongestBorder(g);
            if (receiver == null) continue;

            for (Entity t : g.tiles) {
                game.assignOwner(t, receiver);

                // Anyone else touching this land may now be split too.
                for (int[] o : ORTHO) {
                    Entity nb = world.getEntityAt(t.row + o[0], t.col + o[1]);
                    if (nb == null) continue;
                    Player owner = nb.getOwner();
                    if (owner != null && owner != receiver && owner != p) markDirty(owner);
                }
            }
            game.checkWin(receiver);
        }
    }

    // Flood-fills outward from 'start' over every tile that is not another player's land
    // (the player's own land, free land, and water), collecting the player's tiles it reaches
    // and counting the enemy tiles that wall the area in. Stops early once it has reached
    // every tile the player owns.
    private Group floodGroup(Player p, Entity start, int[] label, int labelId) {
        Group g = new Group();
        Deque<Entity> queue = new ArrayDeque<>();
        queue.add(start);
        label[world.indexOf(start)] = labelId;

        while (!queue.isEmpty()) {
            Entity cur = queue.poll();
            if (cur.getOwner() == p) {
                g.tiles.add(cur);
                if (g.tiles.size() >= p.tiles.size()) return g;
            }

            for (int[] o : ORTHO) {
                Entity nb = world.getEntityAt(cur.row + o[0], cur.col + o[1]);
                if (nb == null) continue;

                Player owner = nb.getOwner();
                if (owner != null && owner != p) {
                    g.boundary.merge(owner, 1, Integer::sum);
                    continue;
                }

                int idx = world.indexOf(nb);
                if (label[idx] == 0) {
                    label[idx] = labelId;
                    queue.add(nb);
                }
            }
        }
        return g;
    }

    // The enemy with the most land bordering the group (ties go to the lower player id).
    private Player strongestBorder(Group g) {
        Player best = null;
        int bestCount = 0;
        for (Map.Entry<Player, Integer> entry : g.boundary.entrySet()) {
            Player candidate = entry.getKey();
            int count = entry.getValue();
            if (count > bestCount || (count == bestCount && best != null && candidate.id < best.id)) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    private static class Group {
        final List<Entity> tiles = new ArrayList<>();
        final Map<Player, Integer> boundary = new HashMap<>();
    }
}