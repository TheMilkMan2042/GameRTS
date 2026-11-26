package org.blake;

import javax.swing.Timer;
import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.blake.GameSettings.*;
import static org.blake.World.ORTHO;

// The game's rules and state, with no drawing code. GamePanel feeds it input and paints it.
public class Game {
    final World world;
    final Random random;
    final List<Player> players = new ArrayList<>();
    final Player human;

    final Expansion expansion;
    final Boats boats;
    final BotController bots;
    final CutOffRule cutOff;

    private final double maxPower;
    private final int landTileCount;
    private final Runnable onChange; // called whenever something changes and the screen needs a repaint

    private boolean gameOver = false;
    private Player winner = null; // null with gameOver = true means the human was eliminated
    private Timer powerTimer;
    private final List<Timer> botTimers = new ArrayList<>();

    public Game(World world, long seed, int botCount, Runnable onChange) {
        this.world = world;
        this.random = new Random(seed);
        this.onChange = onChange;
        this.cutOff = new CutOffRule(this); // must exist before any tiles are claimed
        this.maxPower = (world.rows * world.cols) / MAX_POWER_DIVISOR;

        TerrainGenerator.generate(world, random);

        int land = 0;
        for (Entity e : world.entities) {
            if (!e.isWater()) land++;
        }
        this.landTileCount = land;

        players.add(new Player(0, "You", PLAYER_COLORS[0], false));

        // Shuffle the personalities so each game has a different mix; they repeat if there are
        // more bots than personalities.
        List<Personality> mix = new ArrayList<>(Arrays.asList(Personality.values()));
        Collections.shuffle(mix, random);

        for (int i = 1; i <= botCount; i++) {
            Personality personality = mix.get((i - 1) % mix.size());
            Player bot = new Player(i, "Bot " + i + " (" + personality.label + ")",
                    PLAYER_COLORS[i % PLAYER_COLORS.length], true);
            bot.personality = personality;
            players.add(bot);
            System.out.println(bot.name); // so you can see who you're up against
        }
        this.human = players.get(0);

        placeStartingTerritories();

        this.expansion = new Expansion(this);
        this.boats = new Boats(this);
        this.bots = new BotController(this);
    }

    // Every action ends here. Cut-off checks run first, so the screen never shows a stale split.
    void requestRepaint() {
        cutOff.resolveAll();
        onChange.run();
    }

    // Starts the power timer and one think-timer per bot.
    public void start() {
        powerTimer = new Timer(POWER_TICK_MS, e -> {
            for (Player p : players) {
                p.power = Math.min(maxPower, p.power + getPowerRate(p));
            }
            requestRepaint();
        });
        powerTimer.start();

        // Staggered start times so bots don't all act at once.
        for (Player p : players) {
            if (!p.isBot) continue;
            Timer botTimer = new Timer(BOT_THINK_MS, e -> bots.botTurn(p));
            botTimer.setInitialDelay(500 + random.nextInt(BOT_THINK_MS));
            botTimer.start();
            botTimers.add(botTimer);
        }
    }

    // ---------- Input from the human player ----------

    public void handleClick(Point point) {
        if (gameOver) return;
        Entity entity = world.findEntityAtPoint(point);
        if (entity != null) tryClaim(human, entity);
    }

    public void handleDrag(Point start, double dx, double dy) {
        if (gameOver) return;
        expansion.expandInDirection(human, start, dx, dy);
    }

    // Click claiming: attack if the tile belongs to someone else, otherwise expand over land
    // if the tile is reachable, otherwise try a boat.
    void tryClaim(Player p, Entity entity) {
        if (entity.isWater() || entity.isPending()) return;

        Player owner = entity.getOwner();
        if (owner == p) return;
        if (owner != null) {
            // Another player's tile. Attack on foot if there's a land route to it...
            if (expansion.isLandReachableThroughEnemy(p, entity, MAX_EXPANSION_RANGE)) {
                if (expansion.attackToward(p, entity) > 0) return;
                // Borders them but can't afford a push yet: wait rather than sailing.
                if (!expansion.getEnemyFrontier(p, owner).isEmpty()) return;
            }

            // ...otherwise land a boat on it.
            if (p.power < ATTACK_COST_PER_TILE) return;
            if (p.activeBoats >= p.getMaxBoats()) return;

            Entity boatSource = boats.findBoatSource(p, entity);
            if (boatSource != null) {
                boats.launchBoat(p, boatSource, entity);
            }
            return;
        }

        if (p.power < 1.0) return;

        if (expansion.isLandReachable(p, entity, MAX_EXPANSION_RANGE)) {
            int claimedCount = expansion.expandToward(p, entity);
            if (claimedCount > 0) return;
        }

        if (p.activeBoats >= p.getMaxBoats()) return;

        Entity boatSource = boats.findBoatSource(p, entity);
        if (boatSource != null) {
            boats.launchBoat(p, boatSource, entity);
        }
    }

    // ---------- Power and score ----------

    // Power per second. Climbs quickly at first, then flattens toward BASE + MAX_BONUS,
    // so the 1000th tile is worth far less than the 10th.
    double getPowerRate(Player p) {
        double bonus = POWER_RATE_MAX_BONUS * (1.0 - Math.exp(-p.tiles.size() / POWER_RATE_SCALE));
        return POWER_BASE_RATE + bonus;
    }

    // Percent of all land tiles (water excluded) this player owns.
    double getOwnedPercent(Player p) {
        if (landTileCount == 0) return 0;
        return 100.0 * p.tiles.size() / landTileCount;
    }

    // ---------- End of game ----------

    boolean isGameOver() {
        return gameOver;
    }

    Player getWinner() {
        return winner;
    }

    void checkWin(Player p) {
        if (getOwnedPercent(p) >= WIN_PERCENT) endGame(p);
    }

    // winningPlayer == null means the human was eliminated.
    private void endGame(Player winningPlayer) {
        if (gameOver) return;
        gameOver = true;
        winner = winningPlayer;
        if (powerTimer != null) powerTimer.stop();
        for (Timer t : botTimers) t.stop();
        requestRepaint();
    }

    // ---------- Starting territories ----------

    private void placeStartingTerritories() {
        double minSeparation = Math.max(START_MIN_SEPARATION,
                Math.min(world.rows, world.cols) / START_SEPARATION_DIVISOR);
        List<Entity> centers = new ArrayList<>();

        for (Player p : players) {
            Entity center = null;
            double separation = minSeparation;

            // Try to keep players far apart; relax the distance if the map is too cramped.
            while (center == null) {
                for (int attempt = 0; attempt < 500 && center == null; attempt++) {
                    Entity candidate = world.entities.get(random.nextInt(world.entities.size()));
                    if (candidate.isWater()) continue;

                    boolean farEnough = true;
                    for (Entity other : centers) {
                        if (Math.hypot(candidate.row - other.row, candidate.col - other.col) < separation) {
                            farEnough = false;
                            break;
                        }
                    }
                    if (farEnough) center = candidate;
                }
                separation *= 0.5;
            }

            centers.add(center);
            claimCircle(p, center);
        }
    }

    private void claimCircle(Player p, Entity center) {
        int totalTiles = world.rows * world.cols;
        double targetArea = totalTiles * START_AREA_FRACTION;
        double radius = Math.sqrt(targetArea / Math.PI);

        for (Entity e : world.entities) {
            if (e.isWater() || e.getOwner() != null) continue;
            double dist = Math.hypot(e.row - center.row, e.col - center.col);
            if (dist <= radius) {
                claimTile(e, p);
            }
        }
    }

    // ---------- Ownership ----------

    // The ONE place a normal claim or capture happens, so each player's tile set never drifts
    // out of sync and the follow-up rules (enclosed land, win, elimination, cut-offs) always run.
    void claimTile(Entity e, Player p) {
        if (gameOver && p != null) return; // map is frozen after the game ends

        Player old = e.getOwner();
        assignOwner(e, p);

        if (p != null) {
            // Players this claim may have split: whoever lost the tile, and anyone whose land touches it.
            if (old != null && old != p) {
                cutOff.markDirty(old);
                old.lastAttackedBy.put(p, System.currentTimeMillis()); // bots remember who hit them
            }
            for (int[] o : ORTHO) {
                Entity nb = world.getEntityAt(e.row + o[0], e.col + o[1]);
                if (nb != null && nb.getOwner() != null && nb.getOwner() != p) {
                    cutOff.markDirty(nb.getOwner());
                }
            }

            fillEnclosedRegions(e, p);
            checkWin(p);
        }

        if (old == human && old != p && human.tiles.isEmpty()) {
            endGame(null); // the human lost their last tile
        }
    }

    // Raw ownership change with no side effects (used directly when filling a region or handing
    // over a cut-off section, so those don't re-trigger the rules tile by tile).
    void assignOwner(Entity e, Player p) {
        Player old = e.getOwner();
        if (old != null) old.tiles.remove(e);
        e.setOwner(p);
        if (p != null) p.tiles.add(e);
    }

    // After a claim, check each unowned land tile touching it. If that tile belongs to a
    // group of connected unowned land enclosed by this one player, the whole group is claimed.
    // A claim can only complete an enclosure that touches the newly claimed tile, so only its
    // 4 neighbors need checking.
    private void fillEnclosedRegions(Entity claimed, Player p) {
        Set<Entity> checked = new HashSet<>();

        for (int[] o : ORTHO) {
            Entity start = world.getEntityAt(claimed.row + o[0], claimed.col + o[1]);
            if (start == null || start.getOwner() != null || start.isWater()) continue;
            if (checked.contains(start)) continue;

            List<Entity> region = findEnclosedRegion(start, p, checked);
            if (region == null) continue;

            double cost = AUTO_FILL_COST * region.size();
            if (p.power < cost) continue;
            p.power -= cost;

            for (Entity t : region) {
                assignOwner(t, p);
            }
        }
    }

    // Flood-fills the unowned land connected to 'start'. Every tile bordering the group must
    // be either the player's territory or water. Returns the group if:
    //   - it never touches water (fully enclosed by the player), or
    //   - it touches water AND at least MIN_OWNED_SIDES_WITH_WATER of its sides border the player.
    // Returns null if the group touches a map edge, another player's land, a tile with a
    // boat on the way, or is bigger than MAX_FILL_REGION (which also keeps open continents
    // from being scanned in full).
    private List<Entity> findEnclosedRegion(Entity start, Player p, Set<Entity> checked) {
        List<Entity> region = new ArrayList<>();
        Deque<Entity> queue = new ArrayDeque<>();
        queue.add(start);
        checked.add(start);

        boolean touchesWater = false;
        int ownedSides = 0;

        while (!queue.isEmpty()) {
            Entity cur = queue.poll();
            if (cur.isPending()) return null;

            region.add(cur);
            if (region.size() > MAX_FILL_REGION) return null;

            for (int[] o : ORTHO) {
                Entity n = world.getEntityAt(cur.row + o[0], cur.col + o[1]);
                if (n == null) return null;

                if (n.isWater()) {
                    touchesWater = true;
                    continue;
                }

                Player owner = n.getOwner();
                if (owner == null) {
                    if (checked.add(n)) queue.add(n);
                } else if (owner != p) {
                    return null;
                } else {
                    ownedSides++;
                }
            }
        }

        if (touchesWater && ownedSides < MIN_OWNED_SIDES_WITH_WATER) return null;
        return region;
    }
}