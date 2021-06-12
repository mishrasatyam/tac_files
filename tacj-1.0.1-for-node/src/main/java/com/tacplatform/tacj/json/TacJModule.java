package com.tacplatform.tacj.json;

import com.tacplatform.tacj.TransactionDebugInfo;
import com.tacplatform.tacj.TransactionInfo;
import com.tacplatform.tacj.json.deser.*;
import im.mak.tac.transactions.serializers.json.TacTransactionsModule;

public class TacJModule extends TacTransactionsModule {
    public TacJModule() {
        super();
        addDeserializer(TransactionDebugInfo.class, new TransactionDebugInfoDeser());
        addDeserializer(TransactionInfo.class, new TransactionInfoDeser());
    }
}
