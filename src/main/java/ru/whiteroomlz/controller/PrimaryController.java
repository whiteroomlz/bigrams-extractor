package ru.whiteroomlz.controller;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import javafx.beans.property.BooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jetbrains.annotations.Nullable;
import ru.whiteroomlz.App;
import ru.whiteroomlz.model.ConceptsPair;
import ru.whiteroomlz.model.MainTableDummy;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PrimaryController implements Controller, Initializable {
    private static final String SELF_SUBORDINATION_MARK = "==========";
    private static final String INITIAL_EXPORT_FILE_NAME = "out.txt";

    private static final double MIN_CONNECTION_WEIGHT = 0d;
    private static final double MAX_CONNECTION_WEIGHT = 100d;

    private Stage stage;

    @Nullable
    private FilteredList<MainTableDummy> filteredMainTableItems;
    @Nullable
    private List<ConceptsPair> conceptsPairs;
    @FXML
    private TableView<MainTableDummy> mainTable;
    @FXML
    private TableColumn<MainTableDummy, Boolean> significanceColumn;
    @FXML
    private TableColumn<MainTableDummy, String> parentColumn;
    @FXML
    private TableColumn<MainTableDummy, Integer> frequencyColumn;
    @FXML
    private TableColumn<MainTableDummy, Double> conceptWeightColumn;

    @FXML
    private TextField lowerBoundField;
    @FXML
    private TextField upperBoundField;
    @FXML
    private TextField regexpPatternField;

    @FXML
    private Label selectedConceptsCount;
    ObservableSet<String> selectedConcepts = FXCollections.observableSet(new HashSet<>());

    @FXML
    private Label selectedPairsCount;
    ObservableSet<ConceptsPair> selectedPairs = FXCollections.observableSet(new HashSet<>());

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initializeToolbar();
        initializeMainTable();
        initializeInfoPanel();
    }

    private void initializeToolbar() {
        final String numericValuePattern = "-?(([1-9][0-9]*|0)(\\.[0-9]*)?)?";
        final UnaryOperator<TextFormatter.Change> numericFilter = change ->
                change.getControlNewText().matches(numericValuePattern) ? change : null;

        lowerBoundField.setTextFormatter(new TextFormatter<>(numericFilter));
        lowerBoundField.textProperty().addListener((observable, oldValue, newValue) -> onFilterChanged());

        upperBoundField.setTextFormatter(new TextFormatter<>(numericFilter));
        upperBoundField.textProperty().addListener((observable, oldValue, newValue) -> onFilterChanged());

        regexpPatternField.textProperty().addListener((observable, oldValue, newValue) -> onFilterChanged());
    }

    private void initializeMainTable() {
        mainTable.setFocusModel(null);

        significanceColumn.setCellFactory(CheckBoxTableCell.forTableColumn(significanceColumn));
        significanceColumn.prefWidthProperty().bind(mainTable.widthProperty().multiply(0.2));
        significanceColumn.setStyle("-fx-alignment: CENTER;");
        significanceColumn.setCellValueFactory(cell -> {
            final MainTableDummy dummy = cell.getValue();
            final BooleanProperty property = dummy.isSignificantProperty();

            property.addListener((p, oldValue, newValue) -> {
                if (newValue != oldValue) {
                    dummy.setSignificant(newValue);

                    if (newValue) {
                        selectedConcepts.add(dummy.getConcept());
                        assert conceptsPairs != null;
                        selectedPairs.addAll(conceptsPairs.stream()
                                .filter(pair -> pair.parent().equals(dummy.getConcept()) ||
                                        pair.subordinate().equals(dummy.getConcept()))
                                .filter(pair -> selectedConcepts.contains(pair.parent()) &&
                                        selectedConcepts.contains(pair.subordinate()))
                                .collect(Collectors.toSet()));
                    } else {
                        selectedConcepts.remove(dummy.getConcept());
                        selectedPairs.removeAll(selectedPairs.stream()
                                .filter(pair -> pair.parent().equals(dummy.getConcept()) ||
                                        pair.subordinate().equals(dummy.getConcept()))
                                .collect(Collectors.toSet()));
                    }
                }
            });

            return property;
        });

        parentColumn.setCellValueFactory(cellData -> cellData.getValue().conceptProperty());
        parentColumn.prefWidthProperty().bind(mainTable.widthProperty().multiply(0.4));

        frequencyColumn.setCellValueFactory(cellData -> cellData.getValue().frequencyProperty().asObject());
        frequencyColumn.prefWidthProperty().bind(mainTable.widthProperty().multiply(0.2));
        frequencyColumn.setStyle("-fx-alignment: CENTER;");

        conceptWeightColumn.setCellValueFactory(cellData -> cellData.getValue().weightProperty().asObject());
        conceptWeightColumn.prefWidthProperty().bind(mainTable.widthProperty().multiply(0.2));
        conceptWeightColumn.setStyle("-fx-alignment: CENTER;");
    }

    private void initializeInfoPanel() {
        selectedConcepts.addListener((SetChangeListener<? super String>) change ->
                selectedConceptsCount.setText(String.valueOf(selectedConcepts.size())));
        selectedPairs.addListener((SetChangeListener<? super ConceptsPair>) change ->
                selectedPairsCount.setText(String.valueOf(selectedPairs.size())));
    }

    private void onFilterChanged() {
        if (filteredMainTableItems != null) {
            filteredMainTableItems.setPredicate(dummy -> {
                double lowerBound;
                try {
                    lowerBound = Double.parseDouble(lowerBoundField.getText());
                } catch (NumberFormatException exception) {
                    lowerBound = Double.NEGATIVE_INFINITY;
                }

                double upperBound;
                try {
                    upperBound = Double.parseDouble(upperBoundField.getText());
                } catch (NumberFormatException exception) {
                    upperBound = Double.POSITIVE_INFINITY;
                }

                Pattern searchPattern;
                try {
                    searchPattern = Pattern.compile(regexpPatternField.getText().isEmpty() ?
                            ".*" : regexpPatternField.getText());
                } catch (PatternSyntaxException exception) {
                    searchPattern = Pattern.compile("");
                }

                return lowerBound <= dummy.getWeight() && dummy.getWeight() <= upperBound &&
                        dummy.getConcept().matches(searchPattern.pattern());
            });
        }
    }

    @FXML
    private void onOpenFileAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(App.getResourceBundle(App.BundleName.STRINGS).getString("file_chooser_open_title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("TextAnalyst CSV files", "*.csv"));

        File csvFile = fileChooser.showOpenDialog(stage);
        if (csvFile != null && csvFile.canRead()) {
            conceptsPairs = loadCSV(csvFile.getPath());
            prepareMainTableItems();
        }
    }

    private List<ConceptsPair> loadCSV(String path) {
        ResourceBundle bundle = App.getResourceBundle(App.BundleName.STRINGS);

        List<ConceptsPair> parsed = new ArrayList<>();

        String charsetName = bundle.getString("text_analyst_encoding_charset_name");
        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(path), charsetName))) {
            reader.readNext();

            String[] serializedParts;
            int incorrectLinesCount = 0;
            while ((serializedParts = reader.readNext()) != null) {
                try {
                    String parent = serializedParts[0];
                    int frequency = Integer.parseInt(serializedParts[1]);
                    double weight = Double.parseDouble(serializedParts[2]);
                    String subordinate = serializedParts[3];

                    parsed.add(new ConceptsPair(parent, frequency, weight, subordinate));
                } catch (NumberFormatException exception) {
                    incorrectLinesCount++;
                }
            }

            if (incorrectLinesCount > 0) {
                String alertHeader = bundle.getString("load_csv_parse_troubles_header");
                String alertMessage = bundle.getString("load_csv_parse_troubles_message") + " " + incorrectLinesCount;
                App.showAlert(alertMessage, alertHeader, Alert.AlertType.WARNING, ButtonType.OK);
            }
        } catch (CsvValidationException exception) {
            String alertHeader = bundle.getString("load_csv_csv_validation_exception_header");
            String alertMessage =
                    bundle.getString("load_csv_csv_validation_exception_message") + " " + exception.getMessage();
            App.showAlert(alertMessage, alertHeader, Alert.AlertType.ERROR, ButtonType.OK);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return parsed;
    }

    private void prepareMainTableItems() {
        assert conceptsPairs != null;
        ObservableList<MainTableDummy> selfSubordinatedDummies = FXCollections.observableList(conceptsPairs.stream()
                .filter(pair -> SELF_SUBORDINATION_MARK.equals(pair.subordinate()))
                .map(MainTableDummy::new)
                .toList());
        filteredMainTableItems = new FilteredList<>(selfSubordinatedDummies);
        SortedList<MainTableDummy> mainTableItems = new SortedList<>(filteredMainTableItems);
        mainTableItems.comparatorProperty().bind(mainTable.comparatorProperty());

        clearToolbar();
        onUnselectAll();
        mainTable.setItems(mainTableItems);
    }

    private void clearToolbar() {
        lowerBoundField.clear();
        upperBoundField.clear();
        regexpPatternField.clear();
    }

    @FXML
    private void onExportAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(App.getResourceBundle(App.BundleName.STRINGS).getString("file_chooser_export_title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("TXT file", "*.txt"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel file", "*.xlsx"));
        fileChooser.setInitialFileName(INITIAL_EXPORT_FILE_NAME);

        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            String path = file.getPath();
            String extension = path.substring(path.lastIndexOf('.')).toLowerCase(Locale.ROOT);

            switch (extension) {
                case ".txt" -> writeTxt(path);
                case ".csv" -> writeCsv(path);
                case ".xlsx" -> writeXlsx(path);
            }
        }
    }

    private double[][] getWeightsMatrix(Map<String, Integer> indexMap) {
        double[][] weightsMatrix = new double[selectedConcepts.size()][selectedConcepts.size()];
        for (int row = 0; row < selectedConcepts.size(); row++) {
            for (int column = 0; column < selectedConcepts.size(); column++) {
                weightsMatrix[row][column] = 0d;
            }
        }

        for (ConceptsPair conceptsPair : selectedPairs) {
            int row = indexMap.get(conceptsPair.parent());
            int column = indexMap.get(conceptsPair.subordinate());

            double weight = conceptsPair.weight();
            weight = (weight - MIN_CONNECTION_WEIGHT) / (MAX_CONNECTION_WEIGHT - MIN_CONNECTION_WEIGHT);
            weight = 2 * weight - 1;

            weightsMatrix[row][column] = weight;
        }

        return weightsMatrix;
    }

    private void writeTxt(String path) {
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(path),
                StandardCharsets.UTF_8))) {
            var sortedConcepts = this.selectedConcepts.stream().sorted().toList();

            writer.println(sortedConcepts.size());
            sortedConcepts.forEach(writer::println);

            Map<String, Integer> indexMap = new HashMap<>();
            for (int rowColumnIndex = 0; rowColumnIndex < sortedConcepts.size(); rowColumnIndex++) {
                indexMap.put(sortedConcepts.get(rowColumnIndex), rowColumnIndex);
            }
            double[][] weightsMatrix = getWeightsMatrix(indexMap);

            for (int row = 0; row < selectedConcepts.size(); row++) {
                for (int column = 0; column < selectedConcepts.size(); column++) {
                    writer.printf("%f ", weightsMatrix[row][column]);
                }
                writer.println();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void writeCsv(String path) {
        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(new FileOutputStream(path),
                StandardCharsets.UTF_8))) {
            var sortedConcepts = this.selectedConcepts.stream().sorted().toList();

            List<String[]> data = new ArrayList<>();

            data.add(Stream.concat(Stream.of(""), sortedConcepts.stream()).toArray(String[]::new));

            Map<String, Integer> indexMap = new HashMap<>();
            for (int rowColumnIndex = 0; rowColumnIndex < sortedConcepts.size(); rowColumnIndex++) {
                indexMap.put(sortedConcepts.get(rowColumnIndex), rowColumnIndex);
            }
            double[][] weightsMatrix = getWeightsMatrix(indexMap);

            for (int row = 0; row < selectedConcepts.size(); row++) {
                List<String> rowData = new ArrayList<>(Collections.singleton(sortedConcepts.get(row)));
                rowData.addAll(Arrays.stream(weightsMatrix[row]).mapToObj(String::valueOf).toList());
                data.add(rowData.toArray(String[]::new));
            }

            writer.writeAll(data);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void writeXlsx(String path) {
        try (FileOutputStream stream = new FileOutputStream(path); XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sortedConcepts = this.selectedConcepts.stream().sorted().toList();

            XSSFSheet sheet = workbook.createSheet();

            Row headerRow = sheet.createRow(0);
            for (int index = 0; index < sortedConcepts.size(); index++) {
                Cell cell = headerRow.createCell(index + 1);
                cell.setCellValue(sortedConcepts.get(index));
            }

            Map<String, Integer> indexMap = new HashMap<>();
            for (int rowColumnIndex = 0; rowColumnIndex < sortedConcepts.size(); rowColumnIndex++) {
                indexMap.put(sortedConcepts.get(rowColumnIndex), rowColumnIndex);
            }
            double[][] weightsMatrix = getWeightsMatrix(indexMap);

            for (int rowIndex = 1; rowIndex <= selectedConcepts.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                Cell zeroCell = row.createCell(0);
                zeroCell.setCellValue(sortedConcepts.get(rowIndex - 1));

                for (int columnIndex = 1; columnIndex <= selectedConcepts.size(); columnIndex++) {
                    Cell cell = row.createCell(columnIndex);
                    cell.setCellValue(weightsMatrix[rowIndex - 1][columnIndex - 1]);
                }
            }

            workbook.write(stream);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @FXML
    private void onAboutProgramAction() {
        ResourceBundle bundle = App.getResourceBundle(App.BundleName.STRINGS);
        String header = bundle.getString("help_menu_about_header");
        String message = bundle.getString("help_menu_about_message");
        App.showAlert(message, header, Alert.AlertType.INFORMATION, ButtonType.OK);
    }

    @FXML
    private void onSelectAll() {
        if (conceptsPairs != null) {
            mainTable.getItems().forEach(dummy -> dummy.setSignificant(true));
            selectedConcepts.addAll(mainTable.getItems().stream()
                    .map(MainTableDummy::getConcept)
                    .collect(Collectors.toSet()));
            selectedPairs.addAll(conceptsPairs.stream()
                    .filter(pair -> selectedConcepts.contains(pair.parent()) &&
                            selectedConcepts.contains(pair.subordinate()))
                    .collect(Collectors.toSet()));
            mainTable.refresh();
        }
    }

    @FXML
    private void onUnselectAll() {
        if (filteredMainTableItems != null) {
            mainTable.getItems().forEach(dummy -> dummy.setSignificant(false));
            selectedConcepts.removeAll(mainTable.getItems().stream()
                    .map(MainTableDummy::getConcept)
                    .collect(Collectors.toSet()));
            selectedPairs.removeAll(selectedPairs.stream()
                    .filter(pair -> !selectedConcepts.contains(pair.parent()) ||
                            !selectedConcepts.contains(pair.subordinate()))
                    .collect(Collectors.toSet()));
            mainTable.refresh();
        }
    }
}
