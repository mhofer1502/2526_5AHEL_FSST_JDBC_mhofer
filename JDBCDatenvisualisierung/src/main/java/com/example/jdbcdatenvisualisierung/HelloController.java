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
import java.util.LinkedHashMap;
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

    // Datenbank-Verbindungsdaten - BITTE ANPASSEN!
    private static final String DB_URL = "jdbc:postgresql://xserv:5432/world2";
    private static final String DB_USER = "reader";  // Oft "postgres" statt "root"
    private static final String DB_PASSWORD = "reader";      // Ihr Passwort hier eintragen

    @FXML
    public void initialize() {
        System.out.println("Controller wird initialisiert...");
        setupChart();
        loadCountries();
    }

    private void setupChart() {
        xAxis.setLabel("District");
        yAxis.setLabel("Anzahl Städte");
        districtBarChart.setTitle("Städte pro District");
        districtBarChart.setLegendVisible(false);
    }

    private void loadCountries() {
        new Thread(() -> {
            List<String> countries = new ArrayList<>();

            try {
                // PostgreSQL JDBC-Treiber laden
                Class.forName("org.postgresql.Driver");
                System.out.println("PostgreSQL-Treiber geladen");

                System.out.println("Versuche Verbindung herzustellen zu: " + DB_URL);

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    System.out.println("✓ Datenbankverbindung erfolgreich!");

                    String query = "SELECT name FROM country ORDER BY name";

                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(query)) {

                        while (rs.next()) {
                            String countryName = rs.getString("name");
                            countries.add(countryName);
                        }

                        System.out.println("✓ " + countries.size() + " Länder geladen");

                    }
                }

            } catch (ClassNotFoundException e) {
                System.err.println("✗ PostgreSQL-Treiber nicht gefunden!");
                System.err.println("Fügen Sie diese Dependency in pom.xml hinzu:");
                System.err.println("<dependency>");
                System.err.println("    <groupId>org.postgresql</groupId>");
                System.err.println("    <artifactId>postgresql</artifactId>");
                System.err.println("    <version>42.7.0</version>");
                System.err.println("</dependency>");
                e.printStackTrace();
                Platform.runLater(() -> showError("Treiber fehlt",
                        "PostgreSQL JDBC-Treiber wurde nicht gefunden.\nBitte pom.xml prüfen."));
                return;

            } catch (SQLException e) {
                System.err.println("✗ Datenbankfehler: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> showError("Datenbankfehler",
                        "Verbindung fehlgeschlagen:\n" + e.getMessage() +
                                "\n\nBitte prüfen Sie:\n- Server erreichbar?\n- Benutzername korrekt?\n- Passwort korrekt?"));
                return;
            }

            // ComboBox im JavaFX-Thread aktualisieren
            Platform.runLater(() -> {
                if (!countries.isEmpty()) {
                    ObservableList<String> countryList = FXCollections.observableArrayList(countries);
                    countryComboBox.setItems(countryList);
                    System.out.println("✓ ComboBox mit " + countries.size() + " Ländern befüllt");
                } else {
                    System.err.println("✗ Keine Länder gefunden!");
                    showError("Keine Daten", "Es wurden keine Länder gefunden.");
                }
            });

        }).start();
    }

    @FXML
    private void onCountrySelected() {
        String selectedCountry = countryComboBox.getValue();
        if (selectedCountry != null && !selectedCountry.isEmpty()) {
            System.out.println("Land ausgewählt: " + selectedCountry);
            loadDistrictData(selectedCountry);
        }
    }

    private void loadDistrictData(String country) {
        new Thread(() -> {
            Map<String, Integer> districtCounts = new LinkedHashMap<>();

            String query = "SELECT c.district, COUNT(*) as city_count " +
                    "FROM city c " +
                    "JOIN country co ON c.countrycode = co.code " +
                    "WHERE co.name = ? " +
                    "GROUP BY c.district " +
                    "ORDER BY city_count DESC";

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement pstmt = conn.prepareStatement(query)) {

                pstmt.setString(1, country);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String district = rs.getString("district");
                        int cityCount = rs.getInt("city_count");

                        // Leere Districts überspringen
                        if (district != null && !district.trim().isEmpty()) {
                            districtCounts.put(district, cityCount);
                        }
                    }
                }

                System.out.println("✓ " + districtCounts.size() + " Districts für '" + country + "' geladen");

                // Chart im JavaFX-Thread aktualisieren
                final Map<String, Integer> finalCounts = districtCounts;
                Platform.runLater(() -> updateChart(finalCounts, country));

            } catch (SQLException e) {
                System.err.println("✗ Fehler beim Laden der Districts: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> showError("Datenfehler",
                        "Fehler beim Laden der District-Daten:\n" + e.getMessage()));
            }

        }).start();
    }

    private void updateChart(Map<String, Integer> districtCounts, String country) {
        districtBarChart.getData().clear();

        if (districtCounts.isEmpty()) {
            System.out.println("⚠ Keine Districts für " + country + " gefunden");
            districtBarChart.setTitle("Keine Daten für " + country + " vorhanden");
            return;
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Anzahl Städte");

        for (Map.Entry<String, Integer> entry : districtCounts.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }

        districtBarChart.getData().add(series);
        districtBarChart.setTitle("Städte pro District in " + country);

        System.out.println("✓ Chart aktualisiert mit " + districtCounts.size() + " Districts");
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
