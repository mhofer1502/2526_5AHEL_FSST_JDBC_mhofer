package com.example.jdbcdatenvisualisierung;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HelloController {
    @FXML
    private ComboBox<String> countryComboBox;

    @FXML
    private BarChart<String, Number> districtBarChart;

    @FXML
    private CategoryAxis xAxis;

    @FXML
    private NumberAxis yAxis;

    // Datenbank-Verbindungsdaten
    private static final String DB_URL = "jdbc:postgresql://xserv:5432/world2";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    @FXML
    public void initialize() {
        setupChart();
        loadCountries();
    }

    private void setupChart() {
        xAxis.setLabel("District");
        yAxis.setLabel("Anzahl Städte");
        districtBarChart.setTitle("Städte pro District");
    }

    private void loadCountries() {
        new Thread(() -> {
            List<String> countries = new ArrayList<>();

            try {
                // JDBC-Treiber laden (für PostgreSQL)
                Class.forName("org.postgresql.Driver");

                System.out.println("Versuche Verbindung zur Datenbank herzustellen...");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    System.out.println("Verbindung erfolgreich!");

                    // Teste verschiedene mögliche Spaltennamen
                    String query = "SELECT DISTINCT name FROM country ORDER BY name";

                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(query)) {

                        int count = 0;
                        while (rs.next()) {
                            countries.add(rs.getString("name"));
                            count++;
                        }
                        System.out.println("Anzahl geladener Länder: " + count);

                    } catch (SQLException e) {
                        System.err.println("Fehler bei SQL-Query: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

            } catch (ClassNotFoundException e) {
                System.err.println("PostgreSQL-Treiber nicht gefunden!");
                e.printStackTrace();
                showError("Datenbanktreiber fehlt", "PostgreSQL JDBC-Treiber wurde nicht gefunden.");
                return;
            } catch (SQLException e) {
                System.err.println("Datenbankverbindungsfehler: " + e.getMessage());
                e.printStackTrace();
                showError("Datenbankfehler", "Verbindung zur Datenbank fehlgeschlagen:\n" + e.getMessage());
                return;
            }

            // UI-Update im JavaFX-Thread
            Platform.runLater(() -> {
                if (!countries.isEmpty()) {
                    ObservableList<String> countryList = FXCollections.observableArrayList(countries);
                    countryComboBox.setItems(countryList);
                    System.out.println("ComboBox wurde mit " + countries.size() + " Ländern befüllt");
                } else {
                    System.err.println("Keine Länder gefunden!");
                    showError("Keine Daten", "Es wurden keine Länder in der Datenbank gefunden.");
                }
            });
        }).start();
    }

    @FXML
    private void onCountrySelected() {
        String selectedCountry = countryComboBox.getValue();
        System.out.println("Land ausgewählt: " + selectedCountry);
        if (selectedCountry != null) {
            loadDistrictData(selectedCountry);
        }
    }

    private void loadDistrictData(String country) {
        new Thread(() -> {
            Map<String, Integer> districtCounts = new HashMap<>();

            String query = "SELECT c.district, COUNT(*) as city_count " +
                    "FROM city c " +
                    "JOIN country co ON c.countrycode = co.code " +
                    "WHERE co.name = ? " +
                    "GROUP BY c.district " +
                    "ORDER BY city_count DESC";

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement pstmt = conn.prepareStatement(query)) {

                pstmt.setString(1, country);
                ResultSet rs = pstmt.executeQuery();

                int count = 0;
                while (rs.next()) {
                    String district = rs.getString("district");
                    int cityCount = rs.getInt("city_count");
                    districtCounts.put(district, cityCount);
                    count++;
                }

                System.out.println("Anzahl Districts für " + country + ": " + count);

                // UI-Update im JavaFX-Thread
                Platform.runLater(() -> updateChart(districtCounts));

            } catch (SQLException e) {
                System.err.println("Fehler beim Laden der District-Daten: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> showError("Datenfehler",
                        "Fehler beim Laden der District-Daten:\n" + e.getMessage()));
            }
        }).start();
    }

    private void updateChart(Map<String, Integer> districtCounts) {
        districtBarChart.getData().clear();

        if (districtCounts.isEmpty()) {
            System.out.println("Keine Districts gefunden!");
            return;
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Anzahl Städte");

        for (Map.Entry<String, Integer> entry : districtCounts.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }

        districtBarChart.getData().add(series);
        System.out.println("Chart aktualisiert mit " + districtCounts.size() + " Districts");
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
