module ru.whiteroomlz {
    requires javafx.controls;
    requires com.opencsv;
    requires javafx.fxml;
    requires org.jetbrains.annotations;
    requires org.apache.poi.ooxml;

    opens ru.whiteroomlz to javafx.fxml;
    exports ru.whiteroomlz;
    exports ru.whiteroomlz.model;
    exports ru.whiteroomlz.controller;
    opens ru.whiteroomlz.controller to javafx.fxml;
}
