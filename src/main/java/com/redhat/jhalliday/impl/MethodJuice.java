package com.redhat.jhalliday.impl;

import java.util.HashMap;
import java.util.Map;

public class MethodJuice<T> {

    private T method;
    private Map<String, String> _naming;
    private String _body;
    private final Map<LineNumber, String> CFG;

    public MethodJuice(T method, Map<String, String> naming, String body) {
        this.method = method;
        this._naming = naming;
        this._body = body;
        this.CFG = new HashMap<>();
    }

    public MethodJuice(T method, Map<String, String> naming, Map<LineNumber, String> CFG) {
        this.method = method;
        this._naming = naming;
        this._body = "";
        this.CFG = CFG;
    }

    public String getBody() {
        return _body;
    }

    public T getMethod() { return method; }

    public boolean addBlock(Map.Entry<LineNumber, String> entry) {
        return !this.CFG.putIfAbsent(entry.getKey(), entry.getValue()).equals(entry.getValue());
    }
}
