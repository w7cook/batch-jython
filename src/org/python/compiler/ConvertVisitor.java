// Copyright (c) Corporation for National Research Initiatives
package org.python.compiler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ListIterator;
import java.util.Map;
import java.util.Stack;
import java.util.Vector;

import org.python.antlr.ParseException;
import org.python.antlr.PythonTree;
import org.python.antlr.Visitor;
import org.python.antlr.ast.Assert;
import org.python.antlr.ast.Assign;
import org.python.antlr.ast.Attribute;
import org.python.antlr.ast.AugAssign;
import org.python.antlr.ast.BinOp;
import org.python.antlr.ast.BoolOp;
import org.python.antlr.ast.Break;
import org.python.antlr.ast.Call;
import org.python.antlr.ast.ClassDef;
import org.python.antlr.ast.Compare;
import org.python.antlr.ast.Continue;
import org.python.antlr.ast.Delete;
import org.python.antlr.ast.Dict;
import org.python.antlr.ast.Ellipsis;
import org.python.antlr.ast.ExceptHandler;
import org.python.antlr.ast.Exec;
import org.python.antlr.ast.Expr;
import org.python.antlr.ast.Expression;
import org.python.antlr.ast.ExtSlice;
import org.python.antlr.ast.For;
import org.python.antlr.ast.FunctionDef;
import org.python.antlr.ast.GeneratorExp;
import org.python.antlr.ast.Global;
import org.python.antlr.ast.If;
import org.python.antlr.ast.IfExp;
import org.python.antlr.ast.Import;
import org.python.antlr.ast.ImportFrom;
import org.python.antlr.ast.Index;
import org.python.antlr.ast.Interactive;
import org.python.antlr.ast.Lambda;
import org.python.antlr.ast.List;
import org.python.antlr.ast.ListComp;
import org.python.antlr.ast.Name;
import org.python.antlr.ast.Num;
import org.python.antlr.ast.Pass;
import org.python.antlr.ast.Print;
import org.python.antlr.ast.Raise;
import org.python.antlr.ast.Repr;
import org.python.antlr.ast.Return;
import org.python.antlr.ast.Slice;
import org.python.antlr.ast.Str;
import org.python.antlr.ast.Subscript;
import org.python.antlr.ast.Suite;
import org.python.antlr.ast.TryExcept;
import org.python.antlr.ast.TryFinally;
import org.python.antlr.ast.Tuple;
import org.python.antlr.ast.UnaryOp;
import org.python.antlr.ast.While;
import org.python.antlr.ast.With;
import org.python.antlr.ast.Yield;
import org.python.antlr.ast.alias;
import org.python.antlr.ast.cmpopType;
import org.python.antlr.ast.comprehension;
import org.python.antlr.ast.expr_contextType;
import org.python.antlr.ast.keyword;
import org.python.antlr.ast.operatorType;
import org.python.antlr.base.expr;
import org.python.antlr.base.mod;
import org.python.antlr.base.stmt;
import org.python.core.CompilerFlags;
import org.python.core.ContextGuard;
import org.python.core.ContextManager;
import org.python.core.imp;
import org.python.core.Py;
import org.python.core.PyCode;
import org.python.core.PyComplex;
import org.python.core.PyDictionary;
import org.python.core.PyException;
import org.python.core.PyFloat;
import org.python.core.PyFrame;
import org.python.core.PyFunction;
import org.python.core.PyInteger;
import org.python.core.PyList;
import org.python.core.PyLong;
import org.python.core.PyObject;
import org.python.core.PySet;
import org.python.core.PySlice;
import org.python.core.PyString;
import org.python.core.PyTuple;
import org.python.core.PyUnicode;
import org.python.core.ThreadState;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import static org.python.util.CodegenUtils.*;

import batch.partition.*;
import batch.Op;

public class ConvertVisitor extends Visitor {
    
    private static final java.util.Map<operatorType, Op> operators = new java.util.HashMap<operatorType, Op>();
    static {
        operators.put(operatorType.UNDEFINED, null);
        operators.put(operatorType.Add, Op.ADD);
        operators.put(operatorType.Sub, Op.SUB);
        operators.put(operatorType.Mult, Op.MUL);
        operators.put(operatorType.Div, Op.DIV);
        operators.put(operatorType.Mod, Op.MOD);
        //operators.put(operatorType.Pow, Op.);
        //operators.put(operatorType.LShift, "<<");
        //operators.put(operatorType.RShift, ">>");
        //operators.put(operatorType.BitOr, "|");
        //operators.put(operatorType.BitXor, "^");
        //operators.put(operatorType.BitAnd, "&");
        //operators.put(operatorType.FloorDiv, "//");
    }
    
    private static final java.util.Map<cmpopType, Op> compare = new java.util.HashMap<cmpopType, Op>();
    static {
        compare.put(cmpopType.UNDEFINED, null);
        compare.put(cmpopType.Eq, Op.EQ);
        compare.put(cmpopType.NotEq, Op.NE);
        compare.put(cmpopType.Lt, Op.LT);
        compare.put(cmpopType.LtE, Op.LE);
        compare.put(cmpopType.Gt, Op.GT);
        compare.put(cmpopType.GtE, Op.GE);
        //compare.put(cmpopType.Is, "is");
        //compare.put(cmpopType.IsNot, "is not");
        //compare.put(cmpopType.In, "in");
        //compare.put(cmpopType.NotIn, "not in");
    }
    
    private static CodeModel f = CodeModel.factory;
    static {
        CodeModel.factory.allowAllTransers = true;
    }
    
    public Object visitAll(java.util.List<stmt> body) throws Exception {
        // Check if there's nothing left
        if (body.isEmpty()) {
            System.out.println("It's empty body?");
            return null;
        }
        if (body.size() == 1) {
            return visit(body.get(0));
        }
        
        // Slowly visit each stmt and add to a list for SEQ
        ArrayList<PExpr> seqlist = new ArrayList<PExpr>();
        try {
            while (!body.isEmpty()) {
                stmt cur = body.remove(0);
                PExpr result = (PExpr)visit(cur);
                if (result != null) {
                    seqlist.add(result);
                }
            }
            return ConvertVisitor.f.Prim(Op.SEQ, seqlist);
        } catch (LetAssignException e) { // Let is special case, an exception
            java.util.List<String> vars = e.getVars();
            PExpr value = e.getValue();
            PExpr let = ConvertVisitor.f.Let(vars.remove(0), value, (PExpr)visitAll(body));
            while (!vars.isEmpty()) {
                let = ConvertVisitor.f.Let(vars.remove(0), value, let);
            }
            // Only add to seqlist if there was something there before
            if (seqlist.isEmpty()) {
                return let;
            }
            else {
                seqlist.add(let);
                return ConvertVisitor.f.Prim(Op.SEQ, seqlist);
            }
        }
    }
    
    @Override
    public Object visitExpr(Expr node) throws Exception {
        expr value = node.getInternalValue();
        return visit(value);
    }
    
    @Override
    public Object visitAssign(Assign node) throws Exception {
        java.util.List<expr> targets = node.getInternalTargets();
        expr value = node.getInternalValue();
        ArrayList<String> vars = new ArrayList<String>();
        for (expr e : targets) {
            vars.add(((PExpr)(visit(e))).toString());
        }
        throw new LetAssignException(vars, (PExpr)visit(value));  // Throw an exception...
    }
    
    @Override
    public Object visitPrint(Print node) throws Exception {
        // Use the Other object...
        java.util.List<PExpr> subs = new java.util.ArrayList<PExpr>();
        if (node.getInternalDest() == null) {
            subs.add(ConvertVisitor.f.Data(null));
        }
        else {
            subs.add((PExpr)visit(node.getInternalDest()));
        }
        java.util.List<PExpr> values = new java.util.ArrayList<PExpr>();
        for (expr e : node.getInternalValues()) {
            values.add((PExpr)visit(e));
        }
        subs.add(ConvertVisitor.f.Data(values));
        if (node.getInternalNl().booleanValue()) {
            subs.add(ConvertVisitor.f.Data(new Integer(1)));
        }
        else {
            subs.add(ConvertVisitor.f.Data(new Integer(0)));
        }
        return ConvertVisitor.f.Other(node, subs);
    }
    
    @Override
    public Object visitDelete(Delete node) throws Exception {
        java.util.List<PExpr> subs = new java.util.ArrayList<PExpr>();
        java.util.List<PExpr> targets = new java.util.ArrayList<PExpr>();
        for (expr e : node.getInternalTargets()) {
            targets.add((PExpr)visit(e));
        }
        subs.add(ConvertVisitor.f.Data(targets));
        return ConvertVisitor.f.Other(node, subs);
    }
    
    @Override
    public Object visitPass(Pass node) throws Exception {
        return ConvertVisitor.f.Other(node, new java.util.ArrayList<PExpr>());
    }
    
    @Override
    public Object visitYield(Yield node) throws Exception {
        java.util.List<PExpr> subs = new java.util.ArrayList<PExpr>();
        subs.add((PExpr)visit(node.getInternalValue()));
        return ConvertVisitor.f.Other(node, subs);
    }
    
    @Override
    public Object visitIf(If node) throws Exception {
        expr test = node.getInternalTest();
        java.util.List<stmt> body = node.getInternalBody();
        java.util.List<stmt> orelse = node.getInternalOrelse();
        return ConvertVisitor.f.If((PExpr)(visit(test)), (PExpr)visitAll(body), (PExpr)visitAll(orelse));
    }
    
    @Override
    public Object visitFor(For node) throws Exception {
        expr target = node.getInternalTarget();
        expr iter = node.getInternalIter();
        java.util.List<stmt> body = node.getInternalBody();
        // First assume target is always just a variable...
        if (target instanceof Name) {
            return ConvertVisitor.f.Loop(((Name)target).getInternalId(), (PExpr)visit(iter), (PExpr)visitAll(body));
        }
        return null;    // Hopefully does not reach here...
    }
    
    public Object visitCompare(Compare node) throws Exception {
        expr left = node.getInternalLeft();
        java.util.List<cmpopType> ops = node.getInternalOps();
        java.util.List<expr> comparators = node.getInternalComparators();
        
        expr cur = left;
        ArrayList<PExpr> ands = new ArrayList<PExpr>();
        for (int i = 0; i < ops.size(); i++) {
            ArrayList<PExpr> args = new ArrayList<PExpr>();
            args.add((PExpr)(visit(cur)));
            args.add((PExpr)(visit(comparators.get(i))));
            PExpr temp = ConvertVisitor.f.Prim(ConvertVisitor.compare.get(ops.get(i)), args);
            ands.add(temp);
            cur = comparators.get(i);
        }
        if (ands.size() > 1) {
            return ConvertVisitor.f.Prim(Op.AND, ands);
        }
        else {
            return ands.get(0);
        }
    }
    
    @Override
    public Object visitBinOp(BinOp node) throws Exception {
        expr left = node.getInternalLeft();
        operatorType op = node.getInternalOp();
        expr right = node.getInternalRight();
        
        PExpr leftExpr = (PExpr)(visit(left));
        PExpr rightExpr = (PExpr)(visit(right));
        ArrayList<PExpr> args = new ArrayList<PExpr>();
        args.add(leftExpr);
        args.add(rightExpr);
        return ConvertVisitor.f.Prim(ConvertVisitor.operators.get(op), args);
    }
    
    @Override
    public Object visitAugAssign(AugAssign node) throws Exception {
        expr target = node.getInternalTarget();
        operatorType op = node.getInternalOp();
        expr value = node.getInternalValue();
        return ConvertVisitor.f.Assign((PExpr)visit(target), (PExpr)visit(value));
    }
    
    @Override
    public Object visitCall(Call node) throws Exception {
        expr func = node.getInternalFunc();
        java.util.List<expr> args = node.getInternalArgs();
        
        if (func instanceof Attribute) {
            Attribute a = (Attribute)func;
            PExpr target = (PExpr)visit(a.getInternalValue());
            String method = a.getInternalAttr();
            ArrayList<PExpr> expr_args = new ArrayList<PExpr>();
            for (expr arg : args) {
                expr_args.add((PExpr)visit(arg));
            }
            
            return ConvertVisitor.f.Call(target, method, expr_args);
        }
        return null;    // Place holder for now...
    }
    
    @Override
    public Object visitAttribute(Attribute node) throws Exception {
        PExpr base = (PExpr)visit(node.getInternalValue());
        String field = node.getInternalAttr();
        return ConvertVisitor.f.Prop(base, field);
    }
    
    @Override
    public Object visitList(List node) throws Exception {
        java.util.List<expr> elts = node.getInternalElts();
        java.util.List converted_elts = new java.util.ArrayList();
        for (expr elt : elts) {
            converted_elts.add(visit(elt));
        }
        return ConvertVisitor.f.Data(converted_elts);   // List should be Data
    }
    
    @Override
    public Object visitNum(Num node) throws Exception {
        Integer n;
        if (node.getInternalN() instanceof PyInteger) {
            n = new Integer(((PyInteger)node.getInternalN()).getValue());
        }
        else {
            n = (Integer)node.getInternalN();   // Probably not an Integer though...
        }
        return ConvertVisitor.f.Data(n);
    }
    
    @Override
    public Object visitName(Name node) throws Exception {
        return ConvertVisitor.f.Var(node.getInternalId());
    }
    
    @Override
    public Object visitStr(Str node) throws Exception {
        return ConvertVisitor.f.Data(((PyString)node.getInternalS()).getString());
    }
}

