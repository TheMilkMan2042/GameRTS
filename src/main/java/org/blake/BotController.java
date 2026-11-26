package org.blake;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.blake.GameSettings.*;
import static org.blake.World.ORTHO;

// Bot decision-making. Bots use the same rules as the human; this only picks what they do.
// Each bot's Personality decides who it wants to fight (if anyone).
public class BotController {
    private static final double OPPORTUNIST_SIZE_RATIO = 0.8; // preys on players under 80% of its size

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
        boolean noFreeLand = frontier.isEmpty();

        // Who does this bot want to fight right now? (null = nobody)
        Player enemy = pickEnemy(bot);

        if (enemy != null && (noFreeLand || game.random.nextDouble() < bot.personality.fightChance)) {
            List<Entity> border = game.expansion.getEnemyFrontier(bot, enemy);
            if (!border.isEmpty()) {
                // Borders them: push into their land on foot.
                Entity target = border.get(game.random.nextInt(border.size()));
                if (game.expansion.attackToward(bot, target) > 0) return;
                // Couldn't afford a push yet: fall through to peaceful options.
            } else if (tryBotBoat(bot, enemy)) {
                return; // sailed to land on their territory
            }
        }

        // Peaceful options: a boat to free land, or ordinary expansion.
        boolean wantBoat = noFreeLand || game.random.nextDouble() < BOT_BOAT_CHANCE;
        if (wantBoat && tryBotBoat(bot, null)) return;

        if (!frontier.isEmpty()) {
            Entity target = frontier.get(game.random.nextInt(frontier.size()));
            game.expansion.expandToward(bot, target);
        }
    }

    // ---------- Choosing who to fight ----------

    private Player pickEnemy(Player bot) {
        switch (bot.personality) {
            case PACIFIST:
                return null;

            case TIT_FOR_TAT:
            case GRUDGE:
                // Same memory, different lengths: Tit-for-tat forgets after a while, Grudge never does.
                return mostRecentAttacker(bot, bot.personality.memoryMs);

            case OPPORTUNIST: {
                // Tag team: pile onto anyone another player is attacking right now.
                Player underAttack = mostRecentlyAttackedByOthers(bot, bot.personality.memoryMs);
                if (underAttack != null) return underAttack;

                // Otherwise prey on someone clearly smaller than itself.
                Player weakest = weakestAlive(bot);
                if (weakest != null && weakest.tiles.size() < OPPORTUNIST_SIZE_RATIO * bot.tiles.size()) {
                    return weakest;
                }
                return null;
            }

            case AGGRESSOR:
            default: {
                Player bordering = weakestBordering(bot);
                return (bordering != null) ? bordering : weakestAlive(bot);
            }
        }
    }

    // The player who most recently captured one of this bot's tiles, if it was within memoryMs
    // and they're still in the game.
    private Player mostRecentAttacker(Player bot, long memoryMs) {
        long now = System.currentTimeMillis();
        Player best = null;
        long bestTime = Long.MIN_VALUE;

        for (Map.Entry<Player, Long> entry : bot.lastAttackedBy.entrySet()) {
            Player attacker = entry.getKey();
            long when = entry.getValue();
            if (attacker == bot || attacker.tiles.isEmpty()) continue;
            if (now - when > memoryMs) continue;
            if (when > bestTime) {
                bestTime = when;
                best = attacker;
            }
        }
        return best;
    }

    // The player who most recently lost land to someone OTHER than this bot, within windowMs,
    // as long as that attacker is still in the game. This is who the bot can tag team.
    private Player mostRecentlyAttackedByOthers(Player bot, long windowMs) {
        long now = System.currentTimeMillis();
        Player best = null;
        long bestTime = Long.MIN_VALUE;

        for (Player victim : game.players) {
            if (victim == bot || victim.tiles.isEmpty()) continue;

            for (Map.Entry<Player, Long> entry : victim.lastAttackedBy.entrySet()) {
                Player attacker = entry.getKey();
                long when = entry.getValue();
                if (attacker == bot || attacker == victim || attacker.tiles.isEmpty()) continue;
                if (now - when > windowMs) continue;
                if (when > bestTime) {
                    bestTime = when;
                    best = victim;
                }
            }
        }
        return best;
    }

    // The bordering player with the fewest tiles.
    private Player weakestBordering(Player bot) {
        Player weakest = null;
        for (Entity e : game.expansion.getEnemyFrontier(bot, null)) {
            Player owner = e.getOwner();
            if (weakest == null || owner.tiles.size() < weakest.tiles.size()) weakest = owner;
        }
        return weakest;
    }

    // The player (anywhere on the map) with the fewest tiles who is still in the game.
    private Player weakestAlive(Player bot) {
        Player weakest = null;
        for (Player p : game.players) {
            if (p == bot || p.tiles.isEmpty()) continue;
            if (weakest == null || p.tiles.size() < weakest.tiles.size()) weakest = p;
        }
        return weakest;
    }

    // ---------- Boats ----------

    // With victim == null: sails to nearby FREE land across the same water.
    // With a victim: sails to land on one of that player's tiles (costs attack power).
    private boolean tryBotBoat(Player bot, Player victim) {
        if (bot.activeBoats >= bot.getMaxBoats()) return false;

        double cost = (victim != null) ? ATTACK_COST_PER_TILE : 1.0;
        if (bot.power < cost) return false;

        Set<Integer> myBodies = new HashSet<>();
        for (Entity t : bot.tiles) {
            myBodies.addAll(world.getAdjacentWaterBodyIds(t));
        }
        if (myBodies.isEmpty()) return false;

        // For invasions, sample the victim's own tiles (a random map tile would rarely be theirs).
        List<Entity> victimTiles = null;
        if (victim != null) {
            victimTiles = new ArrayList<>(victim.tiles);
            if (victimTiles.isEmpty()) return false;
        }

        Entity bestTarget = null;
        Entity bestSource = null;
        double bestDist = Double.MAX_VALUE;
        int found = 0;

        for (int attempt = 0; attempt < BOT_BOAT_SAMPLES && found < 8; attempt++) {
            Entity cand = (victim != null)
                    ? victimTiles.get(game.random.nextInt(victimTiles.size()))
                    : world.entities.get(game.random.nextInt(world.entities.size()));

            if (cand.isWater() || cand.isPending() || cand.getOwner() == bot) continue;
            if (victim == null && cand.getOwner() != null) continue; // peaceful trips: free land only
            // A tile touching our own land is better taken on foot, not by boat.
            if (victim != null && touchesOwnLand(bot, cand)) continue;

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

    private boolean touchesOwnLand(Player bot, Entity e) {
        for (int[] o : ORTHO) {
            Entity nb = world.getEntityAt(e.row + o[0], e.col + o[1]);
            if (nb != null && nb.getOwner() == bot) return true;
        }
        return false;
    }
}