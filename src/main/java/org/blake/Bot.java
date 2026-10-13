package org.blake;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;

// Everything that used to live once on GamePanel (power, boats, territory) now
// lives here, so every player, human or bot, has their own.
public class Bot {
    final int id;
    final String name;
    final Color color;
    final boolean isBot;

    double power = 1.0;
    int activeBoats = 0;

    // Every tile this player owns. Insertion-ordered so seeded runs stay deterministic.
    final Set<Entity> tiles = new LinkedHashSet<>();

    public Bot(int id, String name, Color color, boolean isBot) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.isBot = isBot;
    }

    public int getMaxBoats() {
        return (int) (0.05 * power) + 1;
    }
}