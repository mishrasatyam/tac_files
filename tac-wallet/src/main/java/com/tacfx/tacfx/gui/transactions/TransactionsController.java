package com.tacfx.tacfx.gui.transactions;

import com.tacfx.tacfx.bus.RxBus;
import com.tacfx.tacfx.gui.MasterController;
import com.tacfx.tacfx.logic.TransactionDetails;
import com.tacfx.tacfx.logic.TransactionSummary;
import com.tacfx.tacfx.logic.TxFilter;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.rxjavafx.observables.JavaFxObservable;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TransactionsController extends MasterController {

    private static final Logger log = LogManager.getLogger();

    private ObservableList<TransactionDetails> transactionList;
    private BehaviorSubject<Long> txListEmitter;
    private SortedList<TransactionDetails> sortedList;
    private Clipboard clipboard;

    @FXML private TableView<TransactionDetails> transactionsTableView;
    @FXML private TableColumn<TransactionDetails, String> transactionDateTableColumn;
    @FXML private TableColumn<TransactionDetails, TransactionSummary> transactionTypeTableColumn;
    @FXML private ComboBox<Integer> txAmountComboBox;
    @FXML private ComboBox<TxFilter> txFilterComboBox;

    public TransactionsController(final RxBus rxBus) {
        super(rxBus);
        txListEmitter = rxBus.getTxListEmitter();
    }

    @FXML
	public void initialize() {
        clipboard = Clipboard.getSystemClipboard();
        initializeTxAmountComboBox();
        initializeTxFilter();
        initializeTableView();

        privateKeyAccountSubject.observeOn(JavaFxScheduler.platform())
                .subscribe(pk -> clearTableViewForReload());

        final var txAmountObservable = JavaFxObservable.valuesOf(txAmountComboBox.getSelectionModel().selectedItemProperty())
                .doOnNext(integer -> clearTableViewForReload());

        ConnectableObservable.merge(privateKeyAccountSubject, txListEmitter, txAmountObservable)
                .observeOn(Schedulers.io())
                .switchMap(o -> loadTransactionHistoryObservable())
                .observeOn(JavaFxScheduler.platform())
                .subscribe(this::populateList, Throwable::printStackTrace);

        JavaFxObservable.valuesOf(txFilterComboBox.valueProperty())
                .observeOn(Schedulers.io())
                .subscribe(this::filterList);

    }

    private Observable<List<TransactionDetails>> loadTransactionHistoryObservable() {
        return Single.just(loadTransactionHistory())
                .delay(1, TimeUnit.SECONDS)
                .toObservable();
    }

    private void initializeTxFilter() {
        txFilterComboBox.getItems().setAll(TxFilter.class.getEnumConstants());
        txFilterComboBox.getSelectionModel().select(0);
    }

    private void initializeTxAmountComboBox() {
        txAmountComboBox.getItems().addAll(50, 100, 250, 500, 1000);
        txAmountComboBox.getSelectionModel().select(0);
    }

    private void initializeTableView() {
        transactionList = FXCollections.observableArrayList();
        sortedList = new SortedList<TransactionDetails>(transactionList);
        sortedList.comparatorProperty().bind(transactionsTableView.comparatorProperty());
        transactionsTableView.setItems(sortedList);

        transactionsTableView.setRowFactory(p -> new TransactionTableRow(getMessages(), clipboard));
        transactionTypeTableColumn.setCellFactory(p -> new TransactionsTableCell());
    }

    private List<TransactionDetails> loadTransactionHistory() {
        final var address = getPrivateKeyAccount().getAddress();
        final var assetDetailsService = rxBus.getAssetDetailsService().getValue();
        final var txAmount = txAmountComboBox.getValue();
        final var txs = getNodeService().fetchAddressTransactions(address, txAmount).orElse(new ArrayList<>());

        return txs.stream()
                .map(tx -> new TransactionDetails(assetDetailsService, tx, address, getMessages()))
                .sorted(Comparator.comparingLong(TransactionDetails::getEpochDateTime).reversed())
                .collect(Collectors.toUnmodifiableList());

    }

    private void filterList(final TxFilter txFilter) {
        if (txFilter == TxFilter.All) {
            transactionsTableView.setItems(sortedList);
        } else {
            final var sorted = transactionList.filtered(transactionDetails -> transactionDetails.isOfTypeFilter(txFilter));
            final var sortedList = new SortedList<>(sorted);
            sortedList.comparatorProperty().bind(transactionsTableView.comparatorProperty());
            transactionsTableView.getItems().removeAll();
            transactionsTableView.setItems(sortedList);
        }
    }

    private void clearTableViewForReload() {
        final var loadingLabel = new Label(getMessages().getString("loading"));
        transactionList.clear();
        transactionsTableView.setPlaceholder(loadingLabel);
    }

    private void populateList(List<TransactionDetails> list) {
        if (!list.equals(transactionList)) {
            final var focusedItem = transactionsTableView.selectionModelProperty().get().getFocusedIndex();
            transactionList.clear();
            transactionList.setAll(list);
            transactionsTableView.setPlaceholder(new Label());
            transactionsTableView.selectionModelProperty().get().focus(focusedItem);
        }
    }
}
