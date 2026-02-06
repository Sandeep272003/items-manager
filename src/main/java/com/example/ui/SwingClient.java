package com.example.itemmanager.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class SwingClient {

    // Default API base; can be overridden by env var API_BASE or system property api.base
    private final String apiBase;

    private final HttpClient http = HttpClient.newHttpClient();
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private JFrame frame;
    private JTextField nameField;
    private JTextField priceField;
    private JTextArea descArea;

    public SwingClient() {
        String env = System.getenv("API_BASE");
        String prop = System.getProperty("api.base");
        if (prop != null && !prop.isBlank()) apiBase = prop;
        else if (env != null && !env.isBlank()) apiBase = env;
        else apiBase = "http://localhost:8080/api/items";
    }

    public void show() {
        SwingUtilities.invokeLater(this::createAndShowGUI);
    }

    private void createAndShowGUI() {
        frame = new JFrame("Item Manager (Desktop)");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout(10, 10));

        // Top: form
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; nameField = new JTextField(); form.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel("Price:"), gbc);
        gbc.gridx = 1; priceField = new JTextField(); form.add(priceField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; form.add(new JLabel("Description:"), gbc);
        gbc.gridx = 1; descArea = new JTextArea(3, 40); form.add(new JScrollPane(descArea), gbc);

        JButton addBtn = new JButton("Add Item");
        addBtn.addActionListener(e -> addItem());
        gbc.gridx = 1; gbc.gridy = 3; form.add(addBtn, gbc);

        frame.add(form, BorderLayout.NORTH);

        // Center: list
        JList<String> list = new JList<>(listModel);
        frame.add(new JScrollPane(list), BorderLayout.CENTER);

        // Bottom: controls
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadItems());
        bottom.add(refresh);

        JButton seed = new JButton("Seed 3 Items");
        seed.addActionListener(e -> seedItems());
        bottom.add(seed);

        frame.add(bottom, BorderLayout.SOUTH);

        frame.setVisible(true);

        loadItems();
    }

    private void addItem() {
        String name = nameField.getText().trim();
        String desc = descArea.getText().trim();
        String priceText = priceField.getText().trim();
        if (name.isEmpty() || desc.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Name and description required");
            return;
        }
        double price = 0;
        try { price = Double.parseDouble(priceText.isEmpty() ? "0" : priceText); } catch (Exception ex) { JOptionPane.showMessageDialog(frame, "Invalid price"); return; }

        String json = String.format("{\"name\":\"%s\",\"description\":\"%s\",\"price\":%s}", escape(name), escape(desc), price);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiBase))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json))
                .build();

        CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<String> res = http.send(req, BodyHandlers.ofString());
                if (res.statusCode() >= 200 && res.statusCode() < 300) {
                    SwingUtilities.invokeLater(() -> {
                        nameField.setText("");
                        descArea.setText("");
                        priceField.setText("");
                        loadItems();
                    });
                } else {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Failed: " + res.body()));
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage()));
            }
        });
    }

    private void loadItems() {
        listModel.clear();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "?page=0&size=50"))
                .GET()
                .build();
        CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<String> res = http.send(req, BodyHandlers.ofString());
                if (res.statusCode() >= 200 && res.statusCode() < 300) {
                    String body = res.body();
                    // crude display: show raw JSON; for production parse JSON
                    SwingUtilities.invokeLater(() -> {
                        listModel.addElement("Updated: " + Instant.now().toString());
                        listModel.addElement(body);
                    });
                } else {
                    SwingUtilities.invokeLater(() -> listModel.addElement("Failed to load: " + res.statusCode()));
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> listModel.addElement("Error: " + ex.getMessage()));
            }
        });
    }

    private void seedItems() {
        String[] samples = new String[] {
                "{\"name\":\"Aurora Wireless Headphones\",\"description\":\"Over-ear Bluetooth headphones with ANC\",\"price\":7999.00}",
                "{\"name\":\"Heritage Leather Wallet\",\"description\":\"Hand-stitched genuine leather wallet\",\"price\":1499.50}",
                "{\"name\":\"Zen Garden Organic Green Tea\",\"description\":\"Premium loose-leaf green tea\",\"price\":499.00}"
        };
        CompletableFuture.runAsync(() -> {
            for (String s : samples) {
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(apiBase))
                            .header("Content-Type", "application/json")
                            .POST(BodyPublishers.ofString(s))
                            .build();
                    http.send(req, BodyHandlers.ofString());
                } catch (Exception ignored) {}
            }
            SwingUtilities.invokeLater(this::loadItems);
        });
    }

    private String escape(String s) {
        return s.replace("\"", "\\\"");
    }
}
