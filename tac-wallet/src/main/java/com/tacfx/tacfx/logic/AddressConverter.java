package com.tacfx.tacfx.logic;

import com.tacplatform.tacj.Alias;

public class AddressConverter {
    public static String toRawString (String recipient, byte chainId){
        return recipient.length()<=30 ? Alias.fromRawString(recipient, chainId).toRawString() : recipient;
    }
}
