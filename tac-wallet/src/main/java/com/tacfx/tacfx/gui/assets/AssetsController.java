package com.tacfx.tacfx.gui.assets;

import com.tacfx.tacfx.bus.RxBus;
import com.tacfx.tacfx.gui.MasterController;
import com.tacfx.tacfx.logic.Asset;
import com.tacfx.tacfx.logic.Transferable;
import com.tacfx.tacfx.logic.Tac;
import com.tacplatform.tacj.AssetBalance;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.rxjavafx.observables.JavaFxObservable;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AssetsController extends MasterController {

    private static final Logger log = LogManager.getLogger();

    private List<Transferable> assetBalanceList;
    private ObservableList<Transferable> assetList;
    private SortedList<Transferable> sortedList;
    private final BehaviorSubject<Long> emitterSubject;

    @FXML private TableView<Transferable> assetsTableView;
    @FXML private TableColumn<Asset, Asset> assetNameTableColumn;
    @FXML private TableColumn<Asset, Asset> assetBalanceTableColumn;
    @FXML private TableColumn<Asset, Asset> assetIdTableColumn;
    @FXML private TextField filterTextField;

    public AssetsController(final RxBus rxBus) {
        super(rxBus);
        emitterSubject = rxBus.getEmitter();
    }

    @FXML
	public void initialize() {
        initializeTableView();

        JavaFxObservable.valuesOf(filterTextField.textProperty())
                .subscribe(this::filterList);

        ConnectableObservable.merge(privateKeyAccountSubject, emitterSubject)
                .observeOn(Schedulers.computation())
                .map(emission -> getNodeService().fetchAssetBalance(getPrivateKeyAccount().getAddress()))
                .filter(Optional::isPresent).map(Optional::get)
                .subscribe(this::loadAndUpdatePortfolio, Throwable::printStackTrace);

        rxBus.getAssetList().subscribeOn(JavaFxScheduler.platform())
                .subscribe(this::populateTable);
    }

    private void filterList(final String filter) {
        final var filterLowered = filter.toLowerCase();
        if (filterLowered.isEmpty()) {
            assetsTableView.setItems(assetList);
        } else {
            final var sorted = assetList.filtered(asset -> asset.getName().toLowerCase().contains(filterLowered) ||
                    asset.getAssetId().toLowerCase().contains(filterLowered));
            final var sortedList = new SortedList<Transferable>(sorted);
            sortedList.comparatorProperty().bind(assetsTableView.comparatorProperty());
            assetsTableView.setItems(sortedList);
        }
    }

    private void initializeTableView() {
        assetList = FXCollections.observableArrayList();
        sortedList = new SortedList<Transferable>(assetList);
        sortedList.comparatorProperty().bind(assetsTableView.comparatorProperty());
        assetsTableView.setItems(sortedList);
        assetsTableView.setRowFactory(p -> new AssetTableRow(rxBus, getStage()));
    }

    private void loadAndUpdatePortfolio(List<AssetBalance> assetBalanceList) {
        final var assets = assetBalanceList.stream()
                .map(assetBalance1 -> (Transferable) new Asset(assetBalance1))
                .sorted(Comparator.comparing(Transferable::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toUnmodifiableList());

        final var tacBalance = (Transferable) tacBalance();
        final var portfolio = Stream.of(List.of(tacBalance), assets)
                .flatMap(Collection::stream)
                .collect(Collectors.toUnmodifiableList());

        if (rxBus.getAssetList().getValue() != null){
            if (!rxBus.getAssetList().getValue().equals(portfolio))
                rxBus.getAssetList().onNext(portfolio);
        } else {
            rxBus.getAssetList().onNext(portfolio);
        }
    }

    private Tac tacBalance() {
        final var node = getNode();
        final var privateKeyAccount = getPrivateKeyAccount();
        try {
            return new Tac(node.getBalance(privateKeyAccount.getAddress()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void populateTable(List<Transferable> assets) {
        final var focusedItem = assetsTableView.selectionModelProperty().get().getFocusedIndex();
        assetList.clear();
        assetList.setAll(assets);
        assetsTableView.sort();
        assetsTableView.selectionModelProperty().get().select(focusedItem);
    }
}
