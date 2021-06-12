package com.tacfx.tacfx.gui;

import com.tacfx.tacfx.bus.RxBus;
import com.tacfx.tacfx.gui.dialog.DialogWindow;
import com.tacfx.tacfx.logic.NodeService;
import com.tacplatform.tacj.AssetDetails;
import com.tacplatform.tacj.Node;
import com.tacplatform.tacj.PrivateKeyAccount;
import io.reactivex.subjects.BehaviorSubject;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import java.util.ResourceBundle;

public abstract class MasterController {

    protected RxBus rxBus;
    protected AssetDetails mainToken;
    protected final BehaviorSubject<PrivateKeyAccount> privateKeyAccountSubject;
    protected final BehaviorSubject<Node> nodeSubject;
    protected final BehaviorSubject<ResourceBundle> messagesSubject;

    @FXML protected AnchorPane rootPane;

    public MasterController(RxBus rxBus) {
        this.rxBus = rxBus;
        privateKeyAccountSubject = rxBus.getPrivateKeyAccount();
        nodeSubject = rxBus.getNode();
        messagesSubject = rxBus.getResourceBundle();
        this.rxBus.getMainTokenDetails().subscribe(assetDetails -> this.mainToken = assetDetails);
    }

    protected Parent loadParent(FXMLView fxmlView, MasterController controller) {
        return CustomFXMLLoader.loadParent(fxmlView, controller, getMessages());
    }

    protected void switchRootScene(FXMLView fxmlView, MasterController controller) {
        final var parent = CustomFXMLLoader.loadParent(fxmlView, controller, getMessages());
        rootPane.getScene().setRoot(parent);
    }

    protected Stage getStage() {
        return (Stage) rootPane.getScene().getWindow();
    }

    protected Stage getStage(Control control) {
        return (Stage) control.getScene().getWindow();
    }

    protected PrivateKeyAccount getPrivateKeyAccount (){
        return privateKeyAccountSubject.getValue();
    }

    protected Node getNode(){
        return nodeSubject.getValue();
    }

    protected NodeService getNodeService() {
        return rxBus.getNodeService().getValue();
    }

    protected ResourceBundle getMessages() {
        return messagesSubject.getValue();
    }

    protected void createDialog(Parent parent){
        new DialogWindow(rxBus, getStage(), parent).show();
    }

}
