package com.tacfx.tacfx.logic;

import com.tacplatform.tacj.AssetDetails;

import java.util.Objects;

import static com.tacfx.tacfx.logic.AssetNumeralFormatter.toLong;
import static com.tacfx.tacfx.logic.AssetNumeralFormatter.toReadable;

public class Tac implements Transferable {

    public static final long FEE = 1;
    public static final String NAME = "Tac";
    public static final String ASSET_ID = "TAC";
    public static final String MIN_FEE = "0.00000001";
    public static final String SPONSOR_BALANCE = "0.001";
    public static final int DECIMALS = 8;
    private final String balance;


    public Tac(Long balance) {
        this.balance = toReadable(balance, DECIMALS);
    }

    public Tac(String balance) {
        this.balance = balance;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getBalance() {
        return balance;
    }

    @Override
    public String getAssetId() {
        return ASSET_ID;
    }

    @Override
    public int getDecimals() {
        return DECIMALS;
    }

    @Override
    public String getMinFee() {
        return MIN_FEE;
    }

    @Override
    public String getSponsorBalance() {
        return SPONSOR_BALANCE;
    }

    @Override
    public long balanceAsLong() {
        return toLong(balance, DECIMALS);
    }

    @Override
    public long minFeeAsLong() {
        return toLong(MIN_FEE, DECIMALS);
    }

    @Override
    public long sponsorBalanceAsLong() {
        return toLong(SPONSOR_BALANCE, DECIMALS);
    }

    public static AssetDetails getAssetDetails() {
        return new AssetDetails(ASSET_ID, 0L, 0L, "", NAME, "", 8, false, 10000000000000000L, false, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tac)) return false;
        Tac tac = (Tac) o;
        return Objects.equals(getBalance(), tac.getBalance());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getBalance());
    }
}
