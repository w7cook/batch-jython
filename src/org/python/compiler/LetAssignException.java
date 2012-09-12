package org.python.compiler;

import batch.partition.*;

public class LetAssignException extends Exception {
    
    private java.util.List<String> vars;
    private PExpr value;
    
    public LetAssignException(java.util.List<String> vars, PExpr value) {
        this.vars = vars;
        this.value = value;
    }
    
    public java.util.List<String> getVars() {
        return vars;
    }
    
    public PExpr getValue() {
        return value;
    }
}
