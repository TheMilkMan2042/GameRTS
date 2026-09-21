package org.blake;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.Timer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.awt.Color;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame("RTS Game");

            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setSize(800, 600);

            JLabel powerLabel = new JLabel("Power: 1", SwingConstants.CENTER);
            window.add(powerLabel, BorderLayout.NORTH);

            int rows = 30;
            int cols = 60;
            int entitySize = 25;
            int spacing = 25;

            List<Entity> entities = new ArrayList<>();
            for (int i = 0; i < rows * cols; i++) {
                int row = i / cols;
                int col = i % cols;
                entities.add(new Entity(row, col, entitySize));
            }

            GamePanel panel = new GamePanel(entities, rows, cols, spacing);
            window.add(panel, BorderLayout.CENTER);

            Timer labelTimer = new Timer(200, e -> powerLabel.setText("Power: " + panel.getPlayerPower()));
            labelTimer.start();

            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }
}