package org.blake;

import java.awt.Color;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// Everything that belongs to one player, human or bot: power, boats, territory, and
// (for bots) a personality and a memory of who has attacked them.
public class Player {
    final int id;
    final String name;
    final Color color;
    final boolean isBot;

    double power = 1.0;
    int activeBoats = 0;
    long spearReadyAt = 0; // timestamp (ms) when the spear can be used again

    // Bots only: how they pick fights, and when each player last captured one of their tiles.
    Personality personality = Personality.AGGRESSOR;
    final Map<Player, Long> lastAttackedBy = new HashMap<>();

    // Every tile this player owns. Insertion-ordered so seeded runs stay deterministic.
    final Set<Entity> tiles = new LinkedHashSet<>();

    public Player(int id, String name, Color color, boolean isBot) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.isBot = isBot;
    }

    public int getMaxBoats() {
        return (int) (0.05 * power) + 1;
    }

    public long getSpearCooldownRemainingMs() {
        return Math.max(0, spearReadyAt - System.currentTimeMillis());
    }
}