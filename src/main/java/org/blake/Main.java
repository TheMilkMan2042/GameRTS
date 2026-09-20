package org.blake;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame("RTS Game");
            JLabel message = new JLabel("Its Peak", SwingConstants.CENTER);

            window.add(message, BorderLayout.NORTH);
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            window.setSize(800, 600);

            Entity entity = new Entity(375, 225, 50);
            GamePanel panel = new GamePanel(entity);
            window.add(panel, BorderLayout.CENTER);

            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }
}