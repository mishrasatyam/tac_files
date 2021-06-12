package com.tacfx.tacfx.logic;

import com.tacplatform.tacj.AssetDetails;
import com.tacplatform.tacj.Transaction;
import com.tacplatform.tacj.Transfer;
import com.tacplatform.tacj.transactions.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.ResourceBundle;

import static com.tacfx.tacfx.logic.AssetNumeralFormatter.*;
import static com.tacplatform.tacj.transactions.AliasTransaction.ALIAS;
import static com.tacplatform.tacj.transactions.BurnTransaction.BURN;
import static com.tacplatform.tacj.transactions.DataTransaction.DATA;
import static com.tacplatform.tacj.transactions.ExchangeTransaction.EXCHANGE;
import static com.tacplatform.tacj.transactions.InvokeScriptTransaction.CONTRACT_INVOKE;
import static com.tacplatform.tacj.transactions.IssueTransaction.ISSUE;
import static com.tacplatform.tacj.transactions.LeaseCancelTransaction.LEASE_CANCEL;
import static com.tacplatform.tacj.transactions.LeaseTransaction.LEASE;
import static com.tacplatform.tacj.transactions.MassTransferTransaction.MASS_TRANSFER;
import static com.tacplatform.tacj.transactions.ReissueTransaction.REISSUE;
import static com.tacplatform.tacj.transactions.SetAssetScriptTransaction.SET_ASSET_SCRIPT;
import static com.tacplatform.tacj.transactions.SponsorTransaction.SPONSOR;
import static com.tacplatform.tacj.transactions.TransferTransaction.TRANSFER;
import static java.text.MessageFormat.format;

public class TransactionDetails {
    private static final Logger log = LogManager.getLogger();

    static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Transaction transaction;
    private final AssetDetailsService assetDetailsService;
    private final ResourceBundle messages;
    private final String address;
    private final long dateTime;
    private final String transactionId;
    private final Byte transactionType;
    private final TransactionSummary transactionSummary;

    public TransactionDetails(AssetDetailsService assetDetailsService, Transaction transaction, String address,
                              ResourceBundle messages) {
        this.assetDetailsService = assetDetailsService;
        this.messages = messages;
        this.address = address;
        this.transaction = transaction;
        this.dateTime = transaction.getTimestamp();
        this.transactionId = transaction.getId().getBase58String();
        this.transactionSummary = setTransactionSummary(transaction);
        this.transactionType = transaction.getType();
    }

    private String formatDateTime(long timestamp) {
        final var dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return FORMATTER.format(dateTime);
    }

    public String getDateTime() {
        return formatDateTime(dateTime);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public String getAddress() {
        return address;
    }

    public AssetDetailsService getAssetDetailsService() {
        return assetDetailsService;
    }

    public TransactionSummary getTransactionSummary() {
        return transactionSummary;
    }

    public boolean isOfTypeFilter(TxFilter txFilter) {
        final var txType = transaction.getType();
        return switch (txFilter) {
            case Exchanged -> txType == EXCHANGE;
            case Received -> !transaction.getSenderPublicKey().getAddress().equals(address) && (txType == TRANSFER || txType == MASS_TRANSFER);
            case Leased -> txType == LEASE || txType == LEASE_CANCEL;
            case Issued -> txType == ISSUE || txType == BURN;
            case Sent -> transaction.getSenderPublicKey().getAddress().equals(address) && (txType == TRANSFER || txType == MASS_TRANSFER);
            case All -> true;
        };
    }

    public boolean isTransferTransaction() {
        return transaction instanceof TransferTransaction;
    }

    public long getEpochDateTime() {
        return dateTime;
    }

    private AssetDetails fetchAssetDetails(String assetId) {
        return assetDetailsService.fetchAssetDetails(assetId);
    }

    private TransactionSummary setTransactionSummary(final Transaction transaction) {
        return switch (transaction.getType()) {
            case TRANSFER -> generateTransferTransactionInfo((TransferTransaction) transaction);
            case DATA -> generateDataTransferInfo();
            case EXCHANGE -> generateExchangeTransactionInfo((ExchangeTransaction) transaction);
            case LEASE_CANCEL -> generateLeaseCancelTransactionInfo();
            case LEASE -> generateLeaseTransactionInfo((LeaseTransaction) transaction);
            case MASS_TRANSFER -> generateMassTransferTransactionInfo((MassTransferTransaction) transaction);
            case ISSUE -> generateIssueTransactionInfo((IssueTransaction) transaction);
            case REISSUE -> generateReissueTransactionInfo((ReissueTransaction) transaction);
            case BURN -> generateBurnTransactionInfo((BurnTransaction) transaction);
            case SPONSOR -> generateSponsorTransactionInfo((SponsorTransaction) transaction);
            case ALIAS -> generateAliasTransactionInfo((AliasTransaction) transaction);
            case CONTRACT_INVOKE -> generateInvokeScriptTransactionInfo((InvokeScriptTransaction) transaction);
            case SET_ASSET_SCRIPT -> generateSetScriptTransactionInfo((SetScriptTransaction) transaction);
            default -> messageToTransactionSummary(format("unknown_transaction", transaction.getId()));
        };
    }

    private TransactionSummary generateTransferTransactionInfo(final TransferTransaction transferTransaction) {
        final var assetDetails = fetchAssetDetails(transferTransaction.getAssetId());
        final var assetName = assetDetails.getName();
        final var amount = toReadable(transferTransaction.getAmount(), assetDetails.getDecimals());
        final var senderAddress = transferTransaction.getSenderPublicKey().getAddress();
        final var recipientAddress = transferTransaction.getRecipient();

        if (senderAddress.equals(address)) {
            return messageToTransactionSummary(format(messages.getString("sent"), amount, assetName, recipientAddress));
        } else {
            return messageToTransactionSummary(format(messages.getString("received"), amount, assetName, senderAddress));
        }
    }

    private TransactionSummary generateMassTransferTransactionInfo(final MassTransferTransaction massTransferTransaction) {
        final var assetDetails = fetchAssetDetails(massTransferTransaction.getAssetId());
        final var assetName = assetDetails.getName();
        final var senderAddress = massTransferTransaction.getSenderPublicKey().getAddress();
        final var transfer = massTransferTransaction.getTransfers().stream();

        if (senderAddress.equals(address)) {
            final var totalAmount = transfer.mapToLong(Transfer::getAmount).sum();
            final var amount = toReadable(totalAmount, assetDetails.getDecimals());
            return messageToTransactionSummary(format(messages.getString("sent_masstransfer"), amount, assetName));
        } else {
            final var receviedAmount = transfer
                    .filter(transfer1 -> transfer1.getRecipient().equals(address))
                    .mapToLong(Transfer::getAmount)
                    .findFirst();
            final var amount = toReadable(receviedAmount.orElse(0), assetDetails.getDecimals());
            return messageToTransactionSummary(format(messages.getString("received_masstransfer"), amount, assetName));
        }
    }

    private TransactionSummary generateDataTransferInfo() {
        return messageToTransactionSummary(messages.getString("data_transaction"));
    }

    private TransactionSummary generateExchangeTransactionInfo(final ExchangeTransaction exchangeTransaction) {
        final var buyOrder = exchangeTransaction.getOrder1();
        final var sellOrder = exchangeTransaction.getOrder2();
        final var buyer = buyOrder.getSenderPublicKey().getAddress();
        final var seller = sellOrder.getSenderPublicKey().getAddress();
        final var assetPair = buyOrder.getAssetPair();
        final var amountAssetDetails = fetchAssetDetails(assetPair.getAmountAsset());
        final var priceAssetDetails = fetchAssetDetails(assetPair.getPriceAsset());
        final BigDecimal price = toBigDecimal(exchangeTransaction.getPrice(), 8 + priceAssetDetails.getDecimals() - amountAssetDetails.getDecimals());
        final BigDecimal soldAmount = toBigDecimal(exchangeTransaction.getAmount(), amountAssetDetails.getDecimals()).multiply(price);
        final var boughtAmount = toReadable(exchangeTransaction.getAmount(), amountAssetDetails.getDecimals());
        final var readableSoldAmount = fromBigDecimalToReadable(soldAmount, priceAssetDetails.getDecimals());

        if (buyer.equals(address)) {
            return messageToTransactionSummary(format(messages.getString("bought"), boughtAmount, amountAssetDetails.getName(),
                    readableSoldAmount, priceAssetDetails.getName()));
        } else {
            return messageToTransactionSummary(format(messages.getString("sold"), boughtAmount, amountAssetDetails.getName()
                    , readableSoldAmount, priceAssetDetails.getName()));
        }
    }

    private TransactionSummary generateLeaseTransactionInfo(final LeaseTransaction leaseTransaction) {
        final var mainToken = assetDetailsService.getMainToken();
        final var amount = toReadable(leaseTransaction.getAmount(), mainToken.getDecimals());
        final var address = leaseTransaction.getRecipient();
        final var recipient = address.startsWith("alias:W:") ? address.replace("alias:W:", "")
                : address;
        final var sender = leaseTransaction.getSenderPublicKey().getAddress();
        if (!recipient.equals(this.address)) {
            return messageToTransactionSummary(format(messages.getString("started_leasing"), amount, mainToken.getName(), recipient));
        } else {
            return messageToTransactionSummary(format(messages.getString("received_lease"), amount, mainToken.getName(), sender));
        }
    }

    private TransactionSummary generateLeaseCancelTransactionInfo() {
        return messageToTransactionSummary(messages.getString("canceled_leasing"));
    }

    private TransactionSummary generateIssueTransactionInfo(IssueTransaction issueTransaction) {
        final var amount = toReadable(issueTransaction.getQuantity(), issueTransaction.getDecimals());
        return messageToTransactionSummary(format(messages.getString("issued_token"), issueTransaction.getName(), amount));
    }

    private TransactionSummary generateReissueTransactionInfo(ReissueTransaction reissueTransaction) {
        final var assetInfo = fetchAssetDetails(reissueTransaction.getAssetId());
        final var amount = toReadable(reissueTransaction.getQuantity(), assetInfo.getDecimals());
        return messageToTransactionSummary(format(messages.getString("reissued_token"), assetInfo.getName(), amount));
    }

    private TransactionSummary generateBurnTransactionInfo(BurnTransaction burnTransaction) {
        final var assetInfo = fetchAssetDetails(burnTransaction.getAssetId());
        final var amount = toReadable(burnTransaction.getAmount(), assetInfo.getDecimals());
        return messageToTransactionSummary(format(messages.getString("burned_token"), amount, assetInfo.getName()));
    }

    private TransactionSummary generateSponsorTransactionInfo(SponsorTransaction sponsorTransaction) {
        final var name = fetchAssetDetails(sponsorTransaction.getAssetId()).getName();
        return messageToTransactionSummary(format(messages.getString("sponsored_transaction"), name));
    }

    private TransactionSummary generateAliasTransactionInfo(AliasTransaction aliasTransaction) {
        return messageToTransactionSummary(format(messages.getString("alias_transaction"), aliasTransaction.getAlias()));
    }

    private TransactionSummary generateInvokeScriptTransactionInfo(InvokeScriptTransaction invokeScriptTransaction) {
        return messageToTransactionSummary(format(messages.getString("invoked_script"), invokeScriptTransaction.getdApp()));
    }

    private TransactionSummary generateSetScriptTransactionInfo(SetScriptTransaction setScriptTransaction) {
        return messageToTransactionSummary(messages.getString("set_script"));
    }

    private TransactionSummary messageToTransactionSummary(final String message) {
        final var regex = "\r";
        if (message.contains(regex)) {
            final var messages = message.split(regex);
            return new TransactionSummary(messages[0], messages[1]);
        }

        return new TransactionSummary(message, "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionDetails)) return false;
        TransactionDetails that = (TransactionDetails) o;
        return getDateTime().equals(that.getDateTime()) &&
                Objects.equals(getTransaction(), that.getTransaction()) &&
                Objects.equals(getAssetDetailsService(), that.getAssetDetailsService()) &&
                Objects.equals(messages, that.messages) &&
                Objects.equals(getAddress(), that.getAddress()) &&
                Objects.equals(getTransactionId(), that.getTransactionId()) &&
                Objects.equals(transactionType, that.transactionType) &&
                Objects.equals(getTransactionSummary(), that.getTransactionSummary());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTransaction(), getAssetDetailsService(), messages, getAddress(), getDateTime(), getTransactionId(), transactionType, getTransactionSummary());
    }
}
