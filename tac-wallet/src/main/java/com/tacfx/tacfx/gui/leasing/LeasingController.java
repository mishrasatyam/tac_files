package com.tacfx.tacfx.gui.leasing;

import com.tacfx.tacfx.bus.RxBus;
import com.tacfx.tacfx.gui.FXMLView;
import com.tacfx.tacfx.gui.MasterController;
import com.tacfx.tacfx.gui.dialog.ConfirmTransferController;
import com.tacfx.tacfx.gui.style.StyleHandler;
import com.tacfx.tacfx.gui.transactions.TransactionsTableCell;
import com.tacfx.tacfx.logic.*;
import com.tacplatform.tacj.BalanceDetails;
import com.tacplatform.tacj.Transactions;
import com.tacplatform.tacj.transactions.LeaseTransaction;
import io.reactivex.Observable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.rxjavafx.observables.JavaFxObservable;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.tacfx.tacfx.logic.AssetNumeralFormatter.toLong;
import static com.tacfx.tacfx.logic.AssetNumeralFormatter.toReadable;
import static com.tacfx.tacfx.logic.FormValidator.AMOUNT_PATTERN;
import static com.tacfx.tacfx.logic.FormValidator.inputCorrectorObservable;
import static com.tacfx.tacfx.utils.ApplicationSettings.INPUT_REQUEST_DELAY;

public class LeasingController extends MasterController {

    private static final Logger log = LogManager.getLogger();

    private ObservableList<TransactionDetails> transactionList;
    private SortedList<TransactionDetails> sortedList;
    private final Observable<Long> emitter;

    @FXML private TableView<TransactionDetails> transactionTableView;
    @FXML private TableColumn<TransactionDetails, String> transactionDateTableColumn;
    @FXML private TableColumn<TransactionDetails, TransactionSummary> transactionTypeTableColumn;
    @FXML private Label regularBalanceLabel;
    @FXML private Label effectiveBalanceLabel;
    @FXML private Label availableBalanceLabel;
    @FXML private Label generatingBalanceLabel;
    @FXML private Label feeLabel;
    @FXML private Button startLeasingButton;
    @FXML private TextField recipientTextField;
    @FXML private TextField amountTextField;

    public LeasingController(final RxBus rxBus) {
        super(rxBus);
        emitter = rxBus.getEmitter();
    }

    @FXML
	public void initialize() {
        initializeTableView();

        privateKeyAccountSubject
                .subscribe(pk -> transactionList.setAll());

        inputCorrectorObservable(amountTextField, AMOUNT_PATTERN);

        Observable.merge(rxBus.getBalanceDetails(), privateKeyAccountSubject)
                .observeOn(Schedulers.io())
                .switchMap(aLong -> rxBus.getBalanceDetails())
                .observeOn(JavaFxScheduler.platform())
                .subscribe(this::setBalanceDetails);

        ConnectableObservable.merge(privateKeyAccountSubject, emitter)
                .observeOn(Schedulers.io())
                .map(o -> loadActiveLeases())
                .observeOn(JavaFxScheduler.platform())
                .subscribe(this::populateList);

        final var amountObservable = JavaFxObservable.valuesOf(amountTextField.textProperty());

        BehaviorSubject<Boolean> observableRecipientIsValid = BehaviorSubject.create();

        Observable.combineLatest(JavaFxObservable.valuesOf(recipientTextField.textProperty()),
                privateKeyAccountSubject, (fxObservable, pKeyAccount) -> fxObservable)
                .doOnNext(s -> observableRecipientIsValid.onNext(false))
                .throttleLast(INPUT_REQUEST_DELAY, TimeUnit.MILLISECONDS)
                .observeOn(Schedulers.computation())
                .map(this::isValidLeaseAddress)
                .retry()
                .subscribe(observableRecipientIsValid::onNext, Throwable::printStackTrace);

        final var amountIsValidObservable = amountObservable
                .observeOn(Schedulers.computation())
                .map(this::isValidAmount)
                .cache();

        StyleHandler.setBorderDisposable(observableRecipientIsValid, recipientTextField);
        StyleHandler.setBorderDisposable(amountIsValidObservable, amountTextField);

        final var validFormObservable = ConnectableObservable
                .combineLatest(amountIsValidObservable, observableRecipientIsValid, this::isInvalidForm);

        validFormObservable.observeOn(Schedulers.io())
                .filter(aBoolean -> !aBoolean)
                .map(aBoolean -> signTransaction())
                .map(tx -> getNodeService().calculateFee(tx))
                .retry()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(fee -> toReadable(fee, mainToken.getDecimals()))
                .observeOn(JavaFxScheduler.platform())
                .subscribe(feeLabel::setText);

        validFormObservable
                .observeOn(JavaFxScheduler.platform())
                .subscribe(startLeasingButton::setDisable);

        JavaFxObservable.actionEventsOf(startLeasingButton)
                .subscribe(actionEvent -> sendTransaction());
    }

    private void initializeTableView() {
        transactionList = FXCollections.observableArrayList();
        sortedList = new SortedList<TransactionDetails>(transactionList);
        sortedList.comparatorProperty().bind(transactionTableView.comparatorProperty());
        transactionTableView.setItems(sortedList);

        transactionTableView.setRowFactory(p -> new LeasingTableRow(rxBus, getStage()));
        transactionTypeTableColumn.setCellFactory(p -> new TransactionsTableCell());

    }

    private LeaseTransaction signTransaction() {
        final var recipient = recipientTextField.getText();
        final var privateKeyAccount = getPrivateKeyAccount();
        final var recipientAddress = AddressConverter.toRawString(recipient, privateKeyAccount.getChainId());
        return Transactions.makeLeaseTx(privateKeyAccount, recipientAddress, toLong(amountTextField.getText(), mainToken.getDecimals()), Tac.FEE);

    }

    private void sendTransaction() {
        rxBus.getTransaction().onNext(signTransaction());
        final var parent = loadParent(FXMLView.CONFIRM_TRANSACTION, new ConfirmTransferController(rxBus));
        createDialog(parent);
    }

    private List<TransactionDetails> loadActiveLeases() {
        final var address = getPrivateKeyAccount().getAddress();
        final var assetDetailsService = rxBus.getAssetDetailsService().getValue();
        final var activeLeases = getNodeService().fetchActiveLeases(address).orElse(new ArrayList<>());
        return activeLeases.stream()
                .map(tx -> new TransactionDetails(assetDetailsService, tx, address, getMessages()))
                .sorted(Comparator.comparingLong(TransactionDetails::getEpochDateTime).reversed())
                .collect(Collectors.toUnmodifiableList());
    }

    private void populateList(List<TransactionDetails> list) {
        transactionList.setAll(list);
    }


    private void setBalanceDetails(BalanceDetails balanceDetails) {
        regularBalanceLabel.setText(new Tac(balanceDetails.getRegular()).getBalance());
        effectiveBalanceLabel.setText(new Tac(balanceDetails.getEffective()).getBalance());
        generatingBalanceLabel.setText(new Tac(balanceDetails.getRegular()).getBalance());
        availableBalanceLabel.setText(new Tac(balanceDetails.getAvailable()).getBalance());
    }

    private boolean isValidAmount(final String amount) {
        if (amount.isEmpty())
            return false;
        final var available = new Tac(availableBalanceLabel.getText());
        final var input = new Tac(amount);
        return input.balanceAsLong() <= (available.balanceAsLong() + Tac.FEE) && input.balanceAsLong() >= Tac.FEE;
    }

    private boolean isInvalidForm(Boolean... booleans) {
        return !FormValidator.areValid(booleans);
    }

    private boolean isValidLeaseAddress(final String address) {
        final var privateKeyAccountAddress = privateKeyAccountSubject.getValue().getAddress();
        if (address.isEmpty() || address.equals(privateKeyAccountAddress)){
            return false;
        }
        return NodeAddressValidator.isValidAddress(address, nodeSubject.getValue());
    }
}
