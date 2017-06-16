package mod.wurmonline.mods.deitymanager;

import com.ibm.icu.text.MessageFormat;
import com.wurmonline.server.Constants;
import com.wurmonline.server.spells.Spells;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

class DeityManagerWindow {

    private static Logger logger = Logger.getLogger(DeityManagerWindow.class.getName());
    private DeityPropertySheet deityPropertySheet;
    private final ChangeListener<DeityData> deitiesListListener = (observable, oldValue, newValue) -> deitiesListChanged();
    private final ChangeListener<String> serverSelectorListener = (observable, oldValue, newValue) -> serverSelectorChanged();
    private int lastSelectedDeity;
    private String lastSelectedServer;
    private boolean rebuilding = false;
    private DeityManager manager;

    private List<String> servers = new ArrayList<>();

    @FXML
    ListView<DeityData> deitiesList;
    @FXML
    ScrollPane deityProperties;
    @FXML
    ComboBox<String> serverSelector;
    @FXML
    Button refreshButton;
    @FXML
    Button saveButton;
    @FXML
    Button setPowerButton;

    private ResourceBundle messages = LocaleHelper.getBundle("DeityManager");

    void start(DeityManager manager) {
        this.manager = manager;
        try {
            FXMLLoader fx = new FXMLLoader(DeityManagerWindow.class.getResource("DeityManager.fxml"), messages);
            fx.setController(this);
            SplitPane pane = fx.load();
            Scene scene = new Scene(pane);
            Stage stage = new Stage();
            stage.setTitle(messages.getString("mod_name"));
            stage.setScene(scene);

            refreshButton.setOnAction(event -> initialize());
            saveButton.setOnAction(event -> saveDeity());
            setPowerButton.setOnAction(event -> setDeityPower());

            stage.show();

        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    @FXML
    private void populateDeitiesList() {
        rebuilding = true;

        deitiesList.getItems().clear();
        DeityDBInterface.loadAllData();

        for (DeityData deity : DeityDBInterface.getAllData()) {
            deitiesList.getItems().add(deity);
            if (deity.getNumber() == lastSelectedDeity) {
                deitiesList.getSelectionModel().select(deity);
            }
        }

        if (deitiesList.getSelectionModel().getSelectedItem() == null) {
            deitiesList.getSelectionModel().selectFirst();
        }

        rebuilding = false;
        deitiesListChanged();
    }

    @FXML
    private void deitiesListChanged() {
        if (!rebuilding) {
            DeityData selectedDeity = deitiesList.getSelectionModel().getSelectedItem();
            if (selectedDeity != null) {
                lastSelectedDeity = selectedDeity.getNumber();
                deityPropertySheet = new DeityPropertySheet(selectedDeity, Spells.getAllSpells(), false);
                deityProperties.setContent(deityPropertySheet);
                deityPropertySheet.requestFocus();
                setPowerButton.setDisable(false);
            }
            else {
                setPowerButton.setDisable(true);
            }
        }
    }

    private boolean saveCheck() {
        if (deityPropertySheet != null && deityPropertySheet.haveChanges()) {
            // TODO - Needs to be Yes/No/Cancel?
            Optional<ButtonType> result = askYesNo(messages.getString("changes_title"),
                    messages.getString("changes_header"),
                    messages.getString("changes_message"));

            if (result.isPresent() && result.get() == ButtonType.YES) {
                saveDeity();
            }
            return true;
        }
        return false;
    }

    @FXML
    private void saveDeity() {
        String error = deityPropertySheet.save();
        if (error != null && error.length() > 0 ) {
            try {
                DeityDBInterface.saveDeityData(deityPropertySheet.getCurrentData());

                showDialog(Alert.AlertType.INFORMATION,
                        messages.getString("saved_title"),
                        messages.getString("saved_header"),
                        messages.getString("saved_message"));

            } catch (SQLException ex) {
                ex.printStackTrace();
                error += ex.getMessage();
            }
        }
        if (error != null && error.length() > 0 && !error.equalsIgnoreCase("ok")) {
            showDialog(Alert.AlertType.ERROR,
                    messages.getString("save_error_title"),
                    messages.getString("save_error_header"),
                    MessageFormat.format(messages.getString("save_error_message"), error));
        }
        manager.applySettingsToServer();
        populateDeitiesList();
    }

    private void setDeityPower() {
        DeityData selectedDeity = deitiesList.getSelectionModel().getSelectedItem();
        if (selectedDeity == null) {
            // TODO - What to do here?  Should already be disabled?
            return;
        }

        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle(messages.getString("set_power_title"));
        dialog.setHeaderText(messages.getString("set_power_header"));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK,
                                                    ButtonType.CANCEL);
        GridPane grid = new GridPane();
        // TODO - Set current.
        Spinner<Integer> spinner = new Spinner<>();
        // TODO - What should be max power?  (He's the man...)
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100));
        spinner.getValueFactory().setValue(selectedDeity.getPower());
        grid.add(new Label(messages.getString("power")), 0, 0);
        grid.add(spinner, 0, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(dialogButton -> {
                if (dialogButton != ButtonType.CANCEL)
                    return spinner.getValue();
                else
                    return null;
        });

        Optional<Integer> result = dialog.showAndWait();
        // TODO - Not actually saving.
        result.ifPresent(selectedDeity::setPower);
        deityPropertySheet.updatePower();
    }

    @FXML
    private void initialize() {
        boolean cancel = saveCheck();
        if (cancel) {
            return;
        }
        EventHandler<InputEvent> checkEvent = (event) -> {
            boolean toCancel = saveCheck();
            if (toCancel) {
                event.consume();
            }
        };
        deitiesList.getSelectionModel().selectedItemProperty().removeListener(deitiesListListener);
        deitiesList.getSelectionModel().selectedItemProperty().addListener(deitiesListListener);
        deitiesList.setCellFactory(new Callback<ListView<DeityData>, ListCell<DeityData>>() {
            @Override
            public ListCell<DeityData> call(ListView<DeityData> param) {
                ListCell<DeityData> cell = new ListCell<DeityData>() {
                    @Override
                    public void updateItem (DeityData item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null) {
                            textProperty().bind(item.getNameProperty());
                        }
                    }
                };
                cell.addEventFilter(MouseEvent.MOUSE_PRESSED, checkEvent);
                cell.addEventFilter(KeyEvent.KEY_PRESSED, checkEvent);
                return cell;
            }
        });
        serverSelector.getSelectionModel().selectedItemProperty().removeListener(serverSelectorListener);
        serverSelector.getSelectionModel().selectedItemProperty().addListener(serverSelectorListener);
        serverSelector.setDisable(true);

        // TODO
        populateServerSelector();
    }

    private void showDialog(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        alert.showAndWait();
    }

    private Optional<ButtonType> askYesNo(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                content,
                ButtonType.YES,
                ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(header);

        return alert.showAndWait();
    }

    private void serverSelectorChanged() {
        // TODO - Rename duplicate?
        if (!rebuilding) {
            String selectedServer = serverSelector.getSelectionModel().getSelectedItem();
            if (selectedServer != null) {
                lastSelectedServer = selectedServer;
                // TODO - Select server here.

                deityProperties.setContent(null);
                populateDeitiesList();
            }
        }
    }

    private void populateServerSelector() {
        // Find gamedir
        logger.info("Populating server list.");
        if (!servers.isEmpty()) {
            servers = new ArrayList<>();
        }
        serverSelector.getItems().clear();

        File[] dirs = new File(".").listFiles((file1, s) -> new File(file1, s).isDirectory());

        if (dirs != null) {
            for (File dir : dirs) {
                String[] result = dir.list((file, name) -> name.equals("gamedir"));
                if (result != null && result.length > 0) {
                    servers.add(dir.getName());
                    serverSelector.getItems().add(dir.getName());
                }
            }
        }

        if (lastSelectedServer == null) {
            serverSelector.getSelectionModel().selectFirst();
        }
        else {
            serverSelector.getSelectionModel().select(lastSelectedServer);
        }
    }
}
