package org.blake;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.blake.GameSettings.DRAG_THRESHOLD;

// Only the Swing side: draws the grid, turns mouse input into calls on Game, and shows the
// end-of-game banner with its Close / Play Again buttons.
public class GamePanel extends JPanel {
    private World world;
    private Game game;
    private final int spacing;
    private final int botCount;
    private long seed;

    private Point dragStart;

    private JButton[] endButtons;       // created once the game ends
    private boolean endButtonsRequested;

    public GamePanel(List<Entity> entities, int rows, int cols, int spacing, long seed, int botCount) {
        this.spacing = spacing;
        this.botCount = botCount;
        this.seed = seed;

        setLayout(null); // the end-of-game buttons are placed by hand over the board

        this.world = new World(entities, rows, cols);
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

    // ---------- Restarting ----------

    // Builds a brand-new map and game (new random seed, same size and bot count).
    // The finished game already stopped its own timers when it ended.
    private void restart() {
        int rows = world.rows;
        int cols = world.cols;
        int tileSize = world.entities.get(0).size;

        List<Entity> fresh = new ArrayList<>();
        for (int i = 0; i < rows * cols; i++) {
            fresh.add(new Entity(i / cols, i % cols, tileSize));
        }

        seed = new Random().nextLong();
        world = new World(fresh, rows, cols);
        game = new Game(world, seed, botCount, () -> repaint());
        game.start();

        if (endButtons != null) {
            for (JButton b : endButtons) remove(b);
            endButtons = null;
        }
        endButtonsRequested = false;

        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof JFrame) {
            ((JFrame) window).setTitle("RTS Game — Seed: " + seed);
        }

        revalidate();
        repaint();
    }

    // ---------- End-of-game buttons ----------

    private void showEndButtons() {
        if (endButtons != null) return;

        JButton close = new JButton("Close");
        JButton playAgain = new JButton("Play Again");
        close.addActionListener(e -> System.exit(0));
        playAgain.addActionListener(e -> restart());

        endButtons = new JButton[]{close, playAgain};
        for (JButton b : endButtons) add(b);

        positionEndButtons();
        revalidate();
        repaint();
    }

    // Centered just under the banner text.
    private void positionEndButtons() {
        if (endButtons == null) return;

        int buttonWidth = 150;
        int buttonHeight = 44;
        int gap = 20;
        int totalWidth = endButtons.length * buttonWidth + (endButtons.length - 1) * gap;

        int x = (getWidth() - totalWidth) / 2;
        int y = getHeight() / 2 + 30;
        for (JButton b : endButtons) {
            b.setBounds(x, y, buttonWidth, buttonHeight);
            x += buttonWidth + gap;
        }
    }

    @Override
    public void doLayout() {
        super.doLayout();
        positionEndButtons(); // keeps the buttons centered if the window is resized
    }

    // ---------- Drawing ----------

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

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        layoutEntities();
        for (Entity entity : world.entities) {
            entity.draw(g);
        }

        if (game.isGameOver()) {
            drawBanner(g);

            if (!endButtonsRequested) {
                endButtonsRequested = true;
                SwingUtilities.invokeLater(this::showEndButtons);
            }
        }
    }

    private void drawBanner(Graphics g) {
        Player winner = game.getWinner();
        String text;
        Color textColor;
        if (winner == null) {
            text = "YOU WERE ELIMINATED";
            textColor = Color.WHITE;
        } else if (winner == game.human) {
            text = "YOU WIN!";
            textColor = winner.color;
        } else {
            text = winner.name + " WINS";
            textColor = winner.color;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(0, 0, getWidth(), getHeight());

        // Shrink the font until long names (like "Bot 2 (Tit-for-tat) WINS") fit the window.
        int fontSize = 56;
        Font font;
        FontMetrics fm;
        do {
            font = new Font("SansSerif", Font.BOLD, fontSize);
            fm = g2.getFontMetrics(font);
            fontSize -= 4;
        } while (fm.stringWidth(text) > getWidth() - 40 && fontSize > 16);

        g2.setFont(font);
        g2.setColor(textColor);
        g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, getHeight() / 2);
        g2.dispose();
    }

    // ---------- Getters for the on-screen label ----------

    public double getPlayerPower() {
        return game.human.power;
    }

    // Your current power per second (handy for showing in the label while tuning).
    public double getPlayerPowerRate() {
        return game.getPowerRate(game.human);
    }

    public double getSpearCooldownSeconds() {
        return game.human.getSpearCooldownRemainingMs() / 1000.0;
    }

    public double getOwnedPercent() {
        return game.getOwnedPercent(game.human);
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
}