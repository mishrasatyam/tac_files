package com.tacfx.tacfx.bus;

import com.tacfx.tacfx.config.ConfigService;
import com.tacfx.tacfx.gui.FXMLView;
import com.tacfx.tacfx.gui.accountCreator.AccountCreator;
import com.tacfx.tacfx.logic.*;
import com.tacplatform.tacj.*;
import com.tacplatform.tacj.transactions.BurnTransaction;
import com.tacplatform.tacj.transactions.MassTransferTransaction;
import com.tacplatform.tacj.transactions.TransferTransaction;
import io.reactivex.subjects.BehaviorSubject;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;

import java.util.*;

public class RxBus {
    private final Collection<BehaviorSubject<?>> behaviorSubjects = new ArrayList<>();
    private final BehaviorSubject<AccountCreator> accountCreatorBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<AssetDetails> assetDetailsBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<AssetDetails> mainTokenDetailsBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<AssetDetailsService> assetDetailsServiceBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<BalanceDetails> balanceDetailsBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<ConfigService> configServiceBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<HashMap<FXMLView, FXMLLoader>> fxmlLoaderHashMap = createBehaviorSubject();
    private final BehaviorSubject<HashMap<String, AssetDetails>> assetDetailsHashMapBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<List<TransactionDetails>> transactionDetailsListBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<List<Transferable>> assetListBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<List<TransferTransaction>> transactionsBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<List<BurnTransaction>> burnTransactionsBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<List<MassTransferTransaction>> massTransferTransactionsBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<Long> emitterBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<Long> txListEmitterBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<Node> nodeBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<NodeService> nodeService2BehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<PrivateKeyAccount> privateKeyAccountBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<Profile> profileBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<ResourceBundle> resourceBundleBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<Stage> stageBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<Transaction> transactionBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<TransactionDetails> transactionDetailsBehaviorSubject = createBehaviorSubject();
    private final BehaviorSubject<Transferable> transferableBehaviorSubject = createBehaviorSubject();

    public RxBus() {
    }

    private <T> BehaviorSubject<T> createBehaviorSubject() {
        final BehaviorSubject<T> behaviorSubject = BehaviorSubject.create();
        behaviorSubjects.add(behaviorSubject);
        return behaviorSubject;
    }

    public BehaviorSubject<ResourceBundle> getResourceBundle() {
        return resourceBundleBehaviorSubject;
    }

    public BehaviorSubject<ConfigService> getConfigService() {
        return configServiceBehaviorSubject;
    }

    public BehaviorSubject<Profile> getProfile() {
        return profileBehaviorSubject;
    }

    public BehaviorSubject<PrivateKeyAccount> getPrivateKeyAccount() {
        return privateKeyAccountBehaviorSubject;
    }

    public BehaviorSubject<List<Transferable>> getAssetList() {
        return assetListBehaviorSubject;
    }

    public BehaviorSubject<Node> getNode() {
        return nodeBehaviorSubject;
    }

    public BehaviorSubject<List<TransactionDetails>> getTransactionDetailsList() {
        return transactionDetailsListBehaviorSubject;
    }

    public BehaviorSubject<AssetDetails> getAssetDetails() {
        return assetDetailsBehaviorSubject;
    }

    public BehaviorSubject<AssetDetails> getMainTokenDetails() {
        return mainTokenDetailsBehaviorSubject;
    }

    public BehaviorSubject<Transaction> getTransaction() {
        return transactionBehaviorSubject;
    }

    public BehaviorSubject<Transferable> getTransferable() {
        return transferableBehaviorSubject;
    }

    public BehaviorSubject<AccountCreator> getAccountCreator() {
        return accountCreatorBehaviorSubject;
    }

    public BehaviorSubject<Long> getEmitter() {
        return emitterBehaviorSubject;
    }

    public BehaviorSubject<Long> getTxListEmitter() {
        return txListEmitterBehaviorSubject;
    }

    public BehaviorSubject<BalanceDetails> getBalanceDetails() {
        return balanceDetailsBehaviorSubject;
    }

    public BehaviorSubject<List<TransferTransaction>> getTransactions() {
        return transactionsBehaviorSubject;
    }

    public BehaviorSubject<List<BurnTransaction>> getBurnTransactions() {
        return burnTransactionsBehaviorSubject;
    }

    public BehaviorSubject<List<MassTransferTransaction>> getMassTransferTransactions() {
        return massTransferTransactionsBehaviorSubject;
    }

    public BehaviorSubject<HashMap<String, AssetDetails>> getAssetDetailsHashMap() {
        return assetDetailsHashMapBehaviorSubject;
    }

    public BehaviorSubject<AssetDetailsService> getAssetDetailsService() {
        return assetDetailsServiceBehaviorSubject;
    }

    public BehaviorSubject<TransactionDetails> getTransactionDetails() {
        return transactionDetailsBehaviorSubject;
    }

    public BehaviorSubject<Stage> getStageBehaviorSubject() {
        return stageBehaviorSubject;
    }

    public BehaviorSubject<NodeService> getNodeService() {
        return nodeService2BehaviorSubject;
    }
}
