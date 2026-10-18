package org.blake;

import javax.swing.Timer;
import java.util.Set;

import static org.blake.GameSettings.*;

// Everything about crossing water: launching a boat, storm rolls, and picking where it sails from.
public class Boats {
    private final Game game;
    private final World world;

    Boats(Game game) {
        this.game = game;
        this.world = game.world;
    }

    // Spends 1 power, locks the target tile, and claims it when the boat arrives (unless it sinks).
    void launchBoat(Player p, Entity source, Entity target) {
        p.power -= 1.0;
        p.activeBoats++;
        target.setPending(true);

        double distance = Math.hypot(target.row - source.row, target.col - source.col);
        int delayMs = (int) (distance * MS_PER_DISTANCE);

        double stormChance = Math.min(STORM_MAX_CHANCE, distance / STORM_DISTANCE_DIVISOR);
        boolean sunk = game.random.nextDouble() < stormChance;

        Timer captureTimer = new Timer(delayMs, ev -> {
            target.setPending(false);
            p.activeBoats--;
            if (!sunk && target.getOwner() == null) {
                game.claimTile(target, p);
            }
            game.requestRepaint();
        });
        captureTimer.setRepeats(false);
        captureTimer.start();
        game.requestRepaint();
    }

    // Nearest tile owned by the player that touches the same water body as the target.
    Entity findBoatSource(Player p, Entity target) {
        Set<Integer> targetBodies = world.getAdjacentWaterBodyIds(target);
        if (targetBodies.isEmpty()) return null;

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity candidate : p.tiles) {
            Set<Integer> candidateBodies = world.getAdjacentWaterBodyIds(candidate);
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
}