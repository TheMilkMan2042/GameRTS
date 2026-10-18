package org.blake;

import java.awt.Color;

// Every tuning knob in the game, in one place.
public final class GameSettings {
    private GameSettings() {}

    // --- Input ---
    // Minimum drag distance (in pixels) before a press-release counts as a directional arrow
    // instead of a simple click.
    public static final int DRAG_THRESHOLD = 15;

    // --- Land expansion ---
    public static final int MAX_FRONTIER_CANDIDATES = 200;
    public static final int MAX_CLAIMS_PER_CLICK = 40;
    public static final int MAX_EXPANSION_RANGE = 20;
    // Caps how many tiles deep an arrow expansion can reach, regardless of power budget.
    public static final int MAX_ARROW_LENGTH = 15;
    // How many tiles wide the arrow's line is, on EACH side of the center line.
    public static final int ARROW_WIDTH = 1;

    // --- Power ---
    public static final int POWER_TICK_MS = 1000;
    // Rate = BASE + MAX_BONUS * (1 - e^(-tiles / SCALE)). Each extra tile adds exponentially
    // less than the last, and the rate can never exceed BASE + MAX_BONUS.
    public static final double POWER_BASE_RATE = 0.5;
    // The most extra power/sec territory can ever add (the rate cap, minus the base).
    public static final double POWER_RATE_MAX_BONUS = 30.0;
    // Derived so the very first tiles still give +0.05/sec each.
    public static final double POWER_RATE_SCALE = POWER_RATE_MAX_BONUS / 0.05;
    // Max banked power = (rows * cols) / this.
    public static final double MAX_POWER_DIVISOR = 50.0;

    // --- Boats ---
    public static final int MS_PER_DISTANCE = 250;
    // Storm chance = min(STORM_MAX_CHANCE, distance / STORM_DISTANCE_DIVISOR)
    public static final double STORM_MAX_CHANCE = 0.6;
    public static final double STORM_DISTANCE_DIVISOR = 300.0;

    // --- Auto-claiming enclosed land ---
    // Power cost PER TILE to auto-claim a group of land enclosed by one player.
    // 0 = free. If you set a cost, a group the player can't afford at that moment is
    // NOT retried later, so free is the less tedious option.
    public static final double AUTO_FILL_COST = 0.0;
    // Largest enclosed group (in tiles) that will be auto-claimed. Bigger pockets are left alone.
    public static final int MAX_FILL_REGION = 8;
    // A group that touches water is claimed once this many of its sides border one player's
    // territory (counted across the whole group). Groups with no water need to be fully enclosed.
    public static final int MIN_OWNED_SIDES_WITH_WATER = 2;

    // --- Terrain generation ---
    public static final double INITIAL_WATER_CHANCE = 0.575;
    // One smoothing pass per entry; the value is the water-neighbor count that makes a tile water.
    public static final int[] SMOOTHING_THRESHOLDS = {5, 5, 5, 5, 6};
    // Land patches smaller than this become water; water patches smaller than this become land.
    public static final int MIN_LAND_REGION_SIZE = 15;
    public static final int MIN_WATER_REGION_SIZE = 10;

    // --- Starting territories ---
    // Starting circle area as a fraction of the whole map.
    public static final double START_AREA_FRACTION = 0.0005;
    // Players start at least max(START_MIN_SEPARATION, shorter side / divisor) tiles apart.
    public static final int START_MIN_SEPARATION = 10;
    public static final double START_SEPARATION_DIVISOR = 3.0;

    // --- Bots ---
    public static final int BOT_THINK_MS = 1000;          // how often each bot acts
    public static final double BOT_MIN_POWER = 6.0;       // bots save up to at least this before acting
    public static final double BOT_BOAT_CHANCE = 0.1;     // chance per turn to try a boat even if land remains
    public static final int BOT_BOAT_SAMPLES = 60;        // random tiles checked when looking for a boat target
    public static final int BOT_MAX_BOAT_DISTANCE = 30;   // bots won't sail farther than this

    // Player 0 is the human; extra colors are used by bots in order.
    public static final Color[] PLAYER_COLORS = {
            new Color(240, 200, 60),  // gold (you)
            new Color(210, 50, 50),   // red
            new Color(150, 80, 200),  // purple
            new Color(240, 130, 30),  // orange
            new Color(230, 100, 170), // pink
            new Color(130, 80, 40)    // brown
    };
}