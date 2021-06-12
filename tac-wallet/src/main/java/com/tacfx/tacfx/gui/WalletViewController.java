package com.tacfx.tacfx.gui;

import com.tacfx.tacfx.bus.RxBus;
import com.tacfx.tacfx.gui.addressBar.AddressBarController;
import com.tacfx.tacfx.gui.assets.AssetsController;
import com.tacfx.tacfx.gui.dashboard.DashboardController;
import com.tacfx.tacfx.gui.leasing.LeasingController;
import com.tacfx.tacfx.gui.login.LoginController;
import com.tacfx.tacfx.gui.settings.SettingsController;
import com.tacfx.tacfx.gui.sign.SignController;
import com.tacfx.tacfx.gui.style.StyleHandler;
import com.tacfx.tacfx.gui.tokenIssue.TokenIssueController;
import com.tacfx.tacfx.gui.transactions.TransactionsController;
import com.tacfx.tacfx.gui.transfer.TransferTabsController;
import com.tacfx.tacfx.logic.AssetDetailsService;
import com.tacfx.tacfx.logic.Net;
import com.tacfx.tacfx.utils.ApplicationSettings;
import com.tacplatform.tacj.AssetDetails;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.rxjavafx.observables.JavaFxObservable;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.tacfx.tacfx.utils.ApplicationSettings.MAIN_TOKEN;
import static com.tacfx.tacfx.utils.ApplicationSettings.REQUEST_DELAY;

public class WalletViewController extends MasterController {

    private static final Logger log = LogManager.getLogger();

    private final BehaviorSubject<Observable<Long>> afkCounterSubject;
    private final boolean offlineModeEnabled;
    private final Disposable emitter;
    private static final long AFK_TIMEOUT = 300;

    @FXML private BorderPane contentPane;
    @FXML private Button dashboardButton;
    @FXML private Button leasingButton;
    @FXML private Button logoutButton;
    @FXML private Button portfolioButton;
    @FXML private Button sendButton;
    @FXML private Button settingsButton;
    @FXML private Button signButton;
    @FXML private Button tokenIssueButton;
    @FXML private Button transactionsButton;
    @FXML private Label networkLabel;
    @FXML private Label versionLabel;
    @FXML private VBox bottomMenuVBox;
    @FXML private VBox topMenuVBox;

    public WalletViewController(final RxBus rxBus, final boolean offlineModeEnabled) {
        super(rxBus);
        emitter = ConnectableObservable
                .interval(REQUEST_DELAY, TimeUnit.SECONDS, Schedulers.io())
                .takeWhile(aLong -> !offlineModeEnabled)
                .subscribe(rxBus.getEmitter()::onNext);
        ConnectableObservable
                .interval(ApplicationSettings.TX_LIST_REQUEST_DELAY, TimeUnit.SECONDS, Schedulers.io())
                .subscribe(rxBus.getTxListEmitter()::onNext);
        afkCounterSubject = BehaviorSubject.create();
        final var hashMap = new HashMap<String, AssetDetails>();
        hashMap.put(mainToken.getAssetId(), mainToken);
        rxBus.getAssetDetailsHashMap().onNext(hashMap);
        final var assetDetailsService = new AssetDetailsService(hashMap, MAIN_TOKEN, getNode());
        rxBus.getAssetDetailsService().onNext(assetDetailsService);
        this.offlineModeEnabled = offlineModeEnabled;
    }

    @FXML
	public void initialize() {
        versionLabel.setText(ApplicationSettings.loadAppVersion());

        final var address = loadParent(FXMLView.ADDRESS, new AddressBarController(rxBus));
        final var settings = loadParent(FXMLView.SETTINGS, new SettingsController(rxBus));
        final var sign = loadParent(FXMLView.SIGN, new SignController(rxBus));
        contentPane.setTop(address);
        settingsButton.setOnAction(event -> switchPaneScene(event, settings));
        signButton.setOnAction(event -> switchPaneScene(event, sign));
        logoutButton.setOnAction(event -> logout());

        if (offlineModeEnabled){
            signButton.pseudoClassStateChanged(PseudoClass.getPseudoClass("active"), true);
            contentPane.setCenter(sign);
            Stream.of(dashboardButton, portfolioButton, sendButton, tokenIssueButton, transactionsButton, leasingButton)
                    .forEach(button-> button.setDisable(true));
            rxBus.getEmitter().onComplete();
        } else {
            dashboardButton.pseudoClassStateChanged(PseudoClass.getPseudoClass("active"), true);
            final var dashboard = loadParent(FXMLView.DASHBOARD, new DashboardController(rxBus));
            final var assets = loadParent(FXMLView.ASSETS, new AssetsController(rxBus));
            final var transfer = loadParent(FXMLView.TRANSFER, new TransferTabsController(rxBus));
            final var tokenIssue = loadParent(FXMLView.TOKEN_ISSUE, new TokenIssueController(rxBus));
            final var transactions = loadParent(FXMLView.TRANSACTIONS, new TransactionsController(rxBus));
            final var leasing = loadParent(FXMLView.LEASING, new LeasingController(rxBus));

            dashboardButton.setOnAction(event -> switchPaneScene(event, dashboard));
            portfolioButton.setOnAction(event -> switchPaneScene(event, assets));
            sendButton.setOnAction(event -> switchPaneScene(event, transfer));
            tokenIssueButton.setOnAction(event -> switchPaneScene(event, tokenIssue));
            transactionsButton.setOnAction(event -> switchPaneScene(event, transactions));
            leasingButton.setOnAction(event -> switchPaneScene(event, leasing));

            contentPane.setCenter(dashboard);
        }

        afkCounterSubject.onNext(createAfkCounter());
        afkCounterSubject.switchMap(longObservable -> longObservable)
                .take(1)
                .observeOn(JavaFxScheduler.platform())
                .subscribe(aLong -> logout(), Throwable::printStackTrace);

        networkLabel.setText(Net.getFullNetworkName((char) getPrivateKeyAccount().getChainId()).toString());
        rxBus.getEmitter()
                .observeOn(Schedulers.io())
                .map(aLong -> isConnectedToNode())
                .observeOn(JavaFxScheduler.platform())
                .subscribe(aBoolean -> StyleHandler.setConditionedStyle(aBoolean, networkLabel, "network"), Throwable::printStackTrace);

        JavaFxObservable.eventsOf(rootPane, InputEvent.ANY)
                .observeOn(Schedulers.computation())
                .throttleLast(1, TimeUnit.SECONDS)
                .subscribe(event -> afkCounterSubject.onNext(createAfkCounter()));
    }

    private void logout() {
        final var messages = getMessages();
        Optional.ofNullable(rxBus.getStageBehaviorSubject().getValue()).ifPresent(Stage::close);
        final var rxBus2 = new RxBus();
        rxBus2.getResourceBundle().onNext(messages);
        rxBus2.getConfigService().onNext(rxBus.getConfigService().getValue());
        emitter.dispose();
        rxBus.getTxListEmitter().onComplete();
        this.rxBus = rxBus2;
        switchRootScene(FXMLView.LOGIN, new LoginController(rxBus));
    }

    private void clearButtonStyles(Object source) {
        final var pseudoClass = PseudoClass.getPseudoClass("active");
        final var button = (Button) source;
        Stream.of(topMenuVBox, bottomMenuVBox)
                .flatMap(vBox -> vBox.getChildren().stream())
                .filter(node -> node.getPseudoClassStates().contains(pseudoClass))
                .forEach(node -> node.pseudoClassStateChanged(pseudoClass, false));

        button.pseudoClassStateChanged(pseudoClass, true);
    }

    private void switchPaneScene(ActionEvent actionEvent, Parent parent) {
        contentPane.setCenter(parent);
        clearButtonStyles(actionEvent.getSource());
    }

    private Observable<Long> createAfkCounter(){
        return Observable.interval(AFK_TIMEOUT, TimeUnit.SECONDS).observeOn(Schedulers.computation());
    }

    private boolean isConnectedToNode(){
        return getNodeService().fetchHeight().isPresent();
    }
}
