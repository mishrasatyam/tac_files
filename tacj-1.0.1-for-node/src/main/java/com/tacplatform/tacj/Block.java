package com.tacplatform.tacj;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import im.mak.tac.transactions.account.Address;
import im.mak.tac.transactions.common.Base58String;

import java.util.List;
import java.util.Objects;

/**
 * This class represents a block.
 */
@SuppressWarnings("unused")
public class Block extends BlockHeaders {

    private final long fee;
    private final List<TransactionInfo> transactions;

    @JsonCreator
    public Block(
            @JsonProperty("version") int version,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("reference") Base58String reference,
            @JsonProperty("transactionsRoot") Base58String transactionsRoot,
            @JsonProperty("id") Base58String id,
            @JsonProperty("features") List<Integer> features,
            @JsonProperty("desiredReward") long desiredReward,
            @JsonProperty("generator") Address generator,
            @JsonProperty("signature") Base58String signature,
            @JsonProperty("blocksize") int size,
            @JsonProperty("transactionCount") int transactionsCount,
            @JsonProperty("height") int height,
            @JsonProperty("totalFee") long totalFee,
            @JsonProperty("reward") long reward,
            @JsonProperty("VRF") Base58String vrf,
            @JsonProperty("fee") long fee,
            @JsonProperty("transactions") List<TransactionInfo> transactions) {
        super(version, timestamp, reference, transactionsRoot, id, features, desiredReward, generator,
                signature, size, transactionsCount, height, totalFee, reward, vrf);
        this.fee = fee;
        this.transactions = Common.notNull(transactions, "Transactions");

        //transactions in block don't have height field in json
        for (int i = 0; i < this.transactions.size(); i++) {
            TransactionInfo info = this.transactions.get(i);
            if (info.height() == 0)
                this.transactions.set(i, new TransactionInfo(info.tx(), info.applicationStatus(), this.height()));
        }
    }

    public long fee() {
        return fee;
    }

    public List<TransactionInfo> transactions() {
        return transactions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Block block = (Block) o;
        return fee == block.fee &&
                Objects.equals(transactions, block.transactions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fee, transactions);
    }

    @Override
    public String toString() {
        return "Block{" +
                "version=" + version() +
                ", timestamp=" + timestamp() +
                ", reference=" + reference() +
                ", baseTarget=" + baseTarget() +
                ", generationSignature='" + generationSignature() + '\'' +
                ", transactionsRoot='" + transactionsRoot() + '\'' +
                ", id=" + id() +
                ", features=" + features() +
                ", desiredReward=" + desiredReward() +
                ", generator=" + generator() +
                ", signature='" + signature() + '\'' +
                ", size=" + size() +
                ", transactionsCount=" + transactionsCount() +
                ", height=" + height() +
                ", totalFee=" + totalFee() +
                ", reward=" + reward() +
                ", vrf=" + vrf() +
                ", fee=" + fee +
                ", transactions=" + transactions +
                '}';
    }
}
