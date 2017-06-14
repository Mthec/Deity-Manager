package mod.wurmonline.mods.deitymanager;

import com.ibm.icu.text.MessageFormat;
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
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeityManagerWindow {

    private static Logger logger = Logger.getLogger(DeityManagerWindow.class.getName());
    private DeityPropertySheet deityPropertySheet;
    private final ChangeListener<DeityData> deitiesListListener = (observable, oldValue, newValue) -> deitiesListChanged();
    private final ChangeListener<String> serverSelectorListener = (observable, oldValue, newValue) -> serverSelectorChanged();
    private int lastSelectedDeity;
    private String lastSelectedServer;
    private boolean rebuilding = false;
    public Stage stage;

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

    private ResourceBundle messages = LocaleHelper.getBundle("DeityManager");
    //private ResourceBundle messages = ResourceBundle.getBundle("locales/DeityManager", Locale.getDefault());

    public void start() {
        messages.getString("button.refresh");
        try {
            FXMLLoader fx = new FXMLLoader(DeityManagerWindow.class.getResource("DeityManager.fxml"), messages);
            fx.setController(this);
            SplitPane pane = fx.load();

            this.initialize();

            Scene scene = new Scene(pane);
            stage = new Stage();
            stage.setScene(scene);

            refreshButton.setOnAction(event -> initialize());
            saveButton.setOnAction(event -> saveDeity());

            stage.show();

        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    @FXML
    private void populateDeitiesList() {
        rebuilding = true;
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
                //String name = selectedDeity.getName();
                //logger.info(MessageFormat.format(messages.getString("selecting"), name));
                deityPropertySheet = new DeityPropertySheet(selectedDeity, Spells.getAllSpells(), false);
                deityProperties.setContent(deityPropertySheet);
                deityPropertySheet.requestFocus();
            }
        }
    }

    private boolean saveCheck() {
        if (deityPropertySheet != null && deityPropertySheet.haveChanges()) {
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
        populateDeitiesList();
    }

    @FXML
    public void initialize () {
        System.out.println("Initialising");
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

        populateServerSelector();
    }

    private Optional<ButtonType> showDialog(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        return alert.showAndWait();
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
                //DeityDBInterface.selectServer(selectedServer);

                deityProperties.setContent(null);
                populateDeitiesList();
            }
        }
    }

    private void populateServerSelector() {
        // Find gamedir
        logger.info("Populating server list.");
        System.out.println("Populating");
        if (!servers.isEmpty()) {
            servers = new ArrayList<>();
        }
        serverSelector.getItems().clear();

        List<File> folders = new ArrayList<>();

        File folder = new File(".");
        File[] dirs = folder.listFiles((file1, s) -> new File(file1, s).isDirectory());

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
