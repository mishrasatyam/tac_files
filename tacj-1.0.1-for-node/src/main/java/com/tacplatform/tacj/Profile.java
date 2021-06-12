package com.tacplatform.tacj;

import java.net.URI;
import java.net.URISyntaxException;

public enum Profile {
    MAINNET("https://nodes.tactoken.io"),
    TESTNET("https://testnodes.tactoken.io"),
    STAGENET("https://stagenodes.tactoken.io"),
    LOCAL("http://127.0.0.1:6869");

    private final URI uri;

    Profile(String url) {
        try {
            this.uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public URI uri() {
        return uri;
    }
}
