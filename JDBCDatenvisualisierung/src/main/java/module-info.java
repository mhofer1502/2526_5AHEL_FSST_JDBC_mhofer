module com.example.jdbcdatenvisualisierung {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;


    opens com.example.jdbcdatenvisualisierung to javafx.fxml;
    exports com.example.jdbcdatenvisualisierung;
}