package com.tacfx.tacfx.logic;

import com.tacfx.tacfx.encryption.Encrypter;
import com.tacplatform.tacj.PrivateKeyAccount;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class Profile {

    private static final Logger log = LogManager.getLogger();

    private final String name;
    private final String seed;
    private final String node;
    private final int nonce;
    private final int lastNonce;
    private final char networkId;
    private final boolean isPrivateKeyAccount;

    public Profile(String name, String seed, final String node, Boolean isPrivateKeyAccount, int nonce, int lastNonce,
                   char networkId) {
        this.name = name;
        this.seed = seed;
        this.node = node;
        this.nonce = nonce;
        this.lastNonce = lastNonce;
        this.networkId = networkId;
        this.isPrivateKeyAccount = isPrivateKeyAccount;
    }

    public Profile(){
        this.name = "";
        this.seed = "";
        this.node = "";
        this.lastNonce = 0;
        this.nonce = 0;
        this.networkId = 0;
        this.isPrivateKeyAccount = true;
    }

    public String getName() {
        return name;
    }

    public Profile addAddress() {
        if (!isPrivateKeyAccount) {
            return new Profile(name, seed, node, isPrivateKeyAccount, nonce + 1, lastNonce, networkId);
        } else {
            return this;
        }
    }

    public Profile updateLastNonce(int nonce){
        return new Profile(name, seed, node, isPrivateKeyAccount, this.nonce, nonce, networkId);
    }

    public Profile changeNode(final String url) {
        return new Profile(name, seed, url, isPrivateKeyAccount, nonce, lastNonce, networkId);
    }

    public String getSeed() {
        return seed;
    }

    public int getNonce() {
        return nonce;
    }

    public int getLastNonce() {
        return lastNonce;
    }

    public char getNetworkId() {
        return networkId;
    }

    @Override
    public String toString() {
        return name;
    }

    public List<PrivateKeyAccount> loadPrivateKeyAccounts() {
        if (!isPrivateKeyAccount) {
            return IntStream.rangeClosed(0, nonce)
                    .mapToObj(i -> PrivateKeyAccount.fromSeed(seed, i, (byte) networkId))
                    .collect(Collectors.toUnmodifiableList());
        } else {
            return List.of(PrivateKeyAccount.fromPrivateKey(seed, (byte) networkId));
        }
    }

    public String getNode() {
        return node;
    }

    public boolean isPrivateKeyAccount() {
        return isPrivateKeyAccount;
    }

    public Profile decrypt(String password) throws Exception {
        return new Profile(name, Encrypter.decrypt(seed, password), node, isPrivateKeyAccount, nonce, lastNonce, networkId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Profile)) return false;
        Profile profile = (Profile) o;
        return getNonce() == profile.getNonce() &&
                getLastNonce() == profile.getLastNonce() &&
                getNetworkId() == profile.getNetworkId() &&
                isPrivateKeyAccount() == profile.isPrivateKeyAccount() &&
                Objects.equals(getName(), profile.getName()) &&
                Objects.equals(getSeed(), profile.getSeed()) &&
                Objects.equals(getNode(), profile.getNode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getSeed(), getNode(), getNonce(), getLastNonce(), getNetworkId(), isPrivateKeyAccount());
    }
}
