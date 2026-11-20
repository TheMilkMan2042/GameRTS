package org.blake;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.Timer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            long seed = new Random().nextLong();

            JFrame window = new JFrame("RTS Game — Seed: " + seed);

            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setSize(800, 600);

            JLabel powerLabel = new JLabel("Power: 1.00", SwingConstants.CENTER);
            window.add(powerLabel, BorderLayout.NORTH);

            int rows = 154;
            int cols = 306;
            int entitySize = 5;
            int spacing = 5;
            int botCount = 3;

            List<Entity> entities = new ArrayList<>();
            for (int i = 0; i < rows * cols; i++) {
                int row = i / cols;
                int col = i % cols;
                entities.add(new Entity(row, col, entitySize));
            }

            GamePanel panel = new GamePanel(entities, rows, cols, spacing, seed, botCount);
            window.add(panel, BorderLayout.CENTER);

            Timer labelTimer = new Timer(200, e -> {
                double cooldown = panel.getSpearCooldownSeconds();
                String spearText = cooldown > 0 ? String.format("Spear: %.1fs", cooldown) : "Spear: ready";
                powerLabel.setText(String.format("Power: %.2f  |  %s  |  Owned: %.1f%%",
                        panel.getPlayerPower(), spearText, panel.getOwnedPercent()));
            });
            labelTimer.start();

            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }
}