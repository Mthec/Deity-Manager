package mod.wurmonline.mods.deitymanager;

import com.ibm.icu.text.MessageFormat;
import com.wurmonline.server.spells.Spells;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
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

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeityManagerWindow extends Application {

    private static Logger logger = Logger.getLogger(DeityManagerWindow.class.getName());
    private DeityPropertySheet deityPropertySheet;
    private final ChangeListener<DeityData> listener = (observable, oldValue, newValue) -> deitiesListChanged();
    private int lastSelectedDeity;
    private boolean rebuilding = false;

    @FXML
    ListView<DeityData> deitiesList;
    @FXML
    ScrollPane deityProperties;

    private ResourceBundle messages = LocaleHelper.getBundle("DeityManager");

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader fx = new FXMLLoader(DeityManager.class.getResource("DeityManager.fxml"), messages);
            SplitPane pane = fx.load();
            //??? fx.getController();

            Scene scene = new Scene(pane);
            primaryStage.setScene(scene);

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
            return false;
        }
        return true;
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
        deitiesList.getSelectionModel().selectedItemProperty().removeListener(listener);
        deitiesList.getSelectionModel().selectedItemProperty().addListener(listener);
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
        populateDeitiesList();
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
}
