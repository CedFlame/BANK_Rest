package com.example.bankcards.testutil;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class Jsons {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private Jsons() {}
    public static String toJson(Object o) {
        try { return MAPPER.writeValueAsString(o); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
