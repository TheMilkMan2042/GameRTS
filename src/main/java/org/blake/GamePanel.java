package org.blake;

import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

import static org.blake.GameSettings.DRAG_THRESHOLD;

// Only the Swing side: draws the grid and turns mouse input into calls on Game.
public class GamePanel extends JPanel {
    private final World world;
    private final Game game;
    private final int spacing;
    private final long seed;

    private Point dragStart;

    public GamePanel(List<Entity> entities, int rows, int cols, int spacing, long seed, int botCount) {
        this.world = new World(entities, rows, cols);
        this.spacing = spacing;
        this.seed = seed;
        this.game = new Game(world, seed, botCount, () -> repaint());
        game.start();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragStart == null) return;

                Point dragEnd = e.getPoint();
                double dx = dragEnd.x - dragStart.x;
                double dy = dragEnd.y - dragStart.y;
                double dragDistance = Math.hypot(dx, dy);

                if (dragDistance < DRAG_THRESHOLD) {
                    game.handleClick(dragEnd);
                } else {
                    game.handleDrag(dragStart, dx, dy);
                }

                dragStart = null;
            }
        });
    }

    private void layoutEntities() {
        int gridWidth = world.cols * spacing;
        int gridHeight = world.rows * spacing;
        int startX = (getWidth() - gridWidth) / 2;
        int startY = (getHeight() - gridHeight) / 2;

        for (Entity entity : world.entities) {
            entity.x = startX + entity.col * spacing;
            entity.y = startY + entity.row * spacing;
        }
    }

    public double getPlayerPower() {
        return game.human.power;
    }

    // Your current power per second (handy for showing in the label while tuning).
    public double getPlayerPowerRate() {
        return game.getPowerRate(game.human);
    }

    public int getActiveBoats() {
        return game.human.activeBoats;
    }

    public int getMaxBoatsPublic() {
        return game.human.getMaxBoats();
    }

    public long getSeed() {
        return seed;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(game.players);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        layoutEntities();
        for (Entity entity : world.entities) {
            entity.draw(g);
        }
    }
}