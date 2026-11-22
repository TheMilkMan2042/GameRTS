package org.blake;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

import static org.blake.GameSettings.*;

// Bot decision-making. Bots use the same rules as the human; this only picks what they do.
public class BotController {
    private final Game game;
    private final World world;

    BotController(Game game) {
        this.game = game;
        this.world = game.world;
    }

    void botTurn(Player bot) {
        if (bot.tiles.isEmpty()) return;
        if (bot.power < BOT_MIN_POWER) return; // save up for a bigger push

        List<Entity> frontier = game.expansion.getFrontier(bot);

        // Attack a neighbor: always when out of free land, sometimes otherwise.
        boolean wantAttack = frontier.isEmpty() || game.random.nextDouble() < BOT_ATTACK_CHANCE;
        if (wantAttack && tryBotAttack(bot)) return;

        boolean wantBoat = frontier.isEmpty() || game.random.nextDouble() < BOT_BOAT_CHANCE;
        if (wantBoat && tryBotBoat(bot)) return;

        if (!frontier.isEmpty()) {
            Entity target = frontier.get(game.random.nextInt(frontier.size()));
            game.expansion.expandToward(bot, target);
        }
    }

    // Picks the weakest bordering player (fewest tiles) and pushes into a random border tile of theirs.
    private boolean tryBotAttack(Player bot) {
        List<Entity> enemyFrontier = game.expansion.getEnemyFrontier(bot, null);
        if (enemyFrontier.isEmpty()) return false;

        Player weakest = null;
        for (Entity e : enemyFrontier) {
            Player owner = e.getOwner();
            if (weakest == null || owner.tiles.size() < weakest.tiles.size()) weakest = owner;
        }

        List<Entity> targets = new ArrayList<>();
        for (Entity e : enemyFrontier) {
            if (e.getOwner() == weakest) targets.add(e);
        }

        Entity target = targets.get(game.random.nextInt(targets.size()));
        return game.expansion.attackToward(bot, target) > 0;
    }

    private boolean tryBotBoat(Player bot) {
        if (bot.activeBoats >= bot.getMaxBoats()) return false;
        if (bot.power < 1.0) return false;

        Set<Integer> myBodies = new HashSet<>();
        for (Entity t : bot.tiles) {
            myBodies.addAll(world.getAdjacentWaterBodyIds(t));
        }
        if (myBodies.isEmpty()) return false;

        Entity bestTarget = null;
        Entity bestSource = null;
        double bestDist = Double.MAX_VALUE;
        int found = 0;

        // Sample random tiles, keep the closest reachable-by-boat one.
        for (int attempt = 0; attempt < BOT_BOAT_SAMPLES && found < 6; attempt++) {
            Entity cand = world.entities.get(game.random.nextInt(world.entities.size()));
            if (cand.getOwner() != null || cand.isWater() || cand.isPending()) continue;

            Set<Integer> bodies = world.getAdjacentWaterBodyIds(cand);
            bodies.retainAll(myBodies);
            if (bodies.isEmpty()) continue;

            Entity source = game.boats.findBoatSource(bot, cand);
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

        game.boats.launchBoat(bot, bestSource, bestTarget);
        return true;
    }
}