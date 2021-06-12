package com.tacfx.tacfx.logic;

import com.tacplatform.tacj.Base58;

import java.util.Arrays;

import static com.tacplatform.tacj.Hash.secureHash;

public class AddressValidator {
    private final static byte ADDRESS_VERSION = 15;
    private final static byte CHECKSUM_LENGTH = 4;
    private final static byte HASH_LENGTH = 20;
    private final static byte ADDRESS_LENGTH = (byte) (1 + 1 + HASH_LENGTH + CHECKSUM_LENGTH);

    public static boolean validateAddress(final String address, byte networkId) {
        try {
            return fromBytes(Base58.decode(address), networkId);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean fromBytes(byte[] address, byte networkId) {
        if (address == null || address.length != ADDRESS_LENGTH)
            return false;
        if (address[0] != ADDRESS_VERSION || address[1] != networkId)
            return false;

        final var checkSum = Arrays.copyOfRange(address, ADDRESS_LENGTH - CHECKSUM_LENGTH, ADDRESS_LENGTH);
        final var checkSumGenerated = calcCheckSum(address);
        return Arrays.equals(checkSum, checkSumGenerated);
    }

    private static byte[] calcCheckSum(final byte[] withoutChecksum) {
        final var checkSumGenerated = secureHash(withoutChecksum, 0, ADDRESS_LENGTH - CHECKSUM_LENGTH);
        return Arrays.copyOf(checkSumGenerated, CHECKSUM_LENGTH);
    }

}
