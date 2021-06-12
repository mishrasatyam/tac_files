package com.tacplatform.tacj.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TacJMapper extends ObjectMapper {

    public TacJMapper() {
        registerModule(new TacJModule());
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
