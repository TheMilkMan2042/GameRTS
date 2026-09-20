package org.blake;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame("RTS Game");

            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setSize(800, 600);

            List<Entity> entities = new ArrayList<>();

            for (int row = 0; row < 15; row++) {
                for (int col = 0; col < 30; col++){
                    int x = 13 + col * 25;
                    int y = 25 + row * 25;
                    entities.add(new Entity(x, y, 25));
                }
            }

            Entity entity = new Entity(3750, 225, 50);
            GamePanel panel = new GamePanel(entities);
            window.add(panel, BorderLayout.CENTER);

            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }
}