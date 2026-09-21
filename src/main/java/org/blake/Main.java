package org.blake;

import org.apache.logging.log4j.core.pattern.AbstractStyleNameConverter;

import javax.swing.JFrame;
import javax.swing.JLabel;
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

            List<Entity> entities = new ArrayList<>();

            int rows = 30;
            int cols = 60;
            int entitySize = 25;
            int spacing = 25;

            for (int i = 0; i < rows * cols; i++) {
                entities.add(new Entity(0, 0, entitySize));
            }

            GamePanel panel = new GamePanel(entities, rows, cols, spacing, entitySize);
            window.add(panel, BorderLayout.CENTER);

            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }
}