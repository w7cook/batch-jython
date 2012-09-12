package org.python.compiler;

import org.python.antlr.PythonTree;
import org.python.antlr.adapter.AstAdapters;
import org.python.antlr.ast.Assign;
import org.python.antlr.ast.Attribute;
import org.python.antlr.ast.AugAssign;
import org.python.antlr.ast.BinOp;
import org.python.antlr.ast.Call;
import org.python.antlr.ast.Expr;
import org.python.antlr.ast.If;
import org.python.antlr.ast.Name;
import org.python.antlr.ast.Num;
import org.python.antlr.ast.Suite;
import org.python.antlr.ast.expr_contextType;
import org.python.antlr.ast.operatorType;
import org.python.antlr.base.expr;
import org.python.antlr.base.stmt;
import org.python.core.AstList;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyString;

import batch.Op;
import batch.partition.PartitionFactoryHelper;

// Change the batch objects into lines of python code, will then parse again to get AST
public class ConvertFactory extends PartitionFactoryHelper<PythonTree> {
    
    private static final java.util.Map<Op, operatorType> operators = new java.util.HashMap<Op, operatorType>();
    static {
        operators.put(null, operatorType.UNDEFINED);
        operators.put(Op.ADD, operatorType.Add);
        operators.put(Op.SUB, operatorType.Sub);
        operators.put(Op.MUL, operatorType.Mult);
        operators.put(Op.DIV, operatorType.Div);
        operators.put(Op.MOD, operatorType.Mod);
        //operators.put(operatorType.Pow, Op.);
        //operators.put(operatorType.LShift, "<<");
        //operators.put(operatorType.RShift, ">>");
        //operators.put(operatorType.BitOr, "|");
        //operators.put(operatorType.BitXor, "^");
        //operators.put(operatorType.BitAnd, "&");
        //operators.put(operatorType.FloorDiv, "//");
    }
    
    public PythonTree Var(String name) {
        // Set the variable to be load context at first, have others change later ??
        // Wrap everything into Expr statement ype, to be consistent
        return new PythonTree(new Expr(new Name(new PyString(name), AstAdapters.expr_context2py(expr_contextType.Load))));
    }
    
    public PythonTree Data(Object value) {
        // Numbers for now...
        // Wrap expr into Expr
        return new PythonTree(new Expr(new Num((PyObject)value)));
    }
    
    public PythonTree Fun(String var, PythonTree body) {return null;}
    
    public PythonTree Prim(Op op, java.util.List<PythonTree> args) {
        PythonTree ret;
        if (op == Op.SEQ) {
            // For a sequence of arguments, return a suite
            java.util.List<stmt> body = new java.util.ArrayList<stmt>();
            for (PythonTree t : args) {
                // Just in case adding a Suite, break it apart and combine
                if (t instanceof Suite) {
                    body.addAll(((Suite)t).getInternalBody());
                }
                else {
                    body.add((stmt)t);
                }
            }
            ret = new Suite(new AstList(body, AstAdapters.stmtAdapter));
        }
        else  {
            // Everyting else is just binary operations ?
            expr left, right;
            // Must unwrap expr from Expr
            left = ((Expr)(args.get(0))).getInternalValue();
            for (int i = 1; i < args.size(); i++) {
                right = ((Expr)args.get(i)).getInternalValue();
                left = new BinOp(left, AstAdapters.operator2py(operators.get(op)), right);
            }
            // Wrap into Expr
            ret = new Expr(left);
        }
        
        return new PythonTree(ret);
    }
    
    public PythonTree Prop(PythonTree base, String field) {
        // Wrap into Expr type
        return new PythonTree(new Expr(new Attribute(base, new PyString(field), AstAdapters.expr_context2py(expr_contextType.Load)))); // Set context as load for now
    }
    
    public PythonTree Assign(Op op, PythonTree target, PythonTree source) {
        return new PythonTree(new AugAssign(target, AstAdapters.operator2py(operators.get(op)), source));
    }
    
    public PythonTree Let(String var, PythonTree expression, PythonTree body) {
        java.util.List<expr> targets = new java.util.ArrayList<expr>();
        targets.add(new Name(new PyString(var), AstAdapters.expr_context2py(expr_contextType.Store)));  // Assign puts variable in store context
        // All target possiblities will be wrapped ino Expr
        expr value = ((Expr)expression).getInternalValue();
        Assign a = new Assign(new AstList(targets, AstAdapters.exprAdapter), value);
        java.util.List<stmt> statements = new java.util.ArrayList<stmt>();
        statements.add(a);
        if (body instanceof Suite) {
            statements.addAll(((Suite)(body)).getInternalBody()); // Assume body is Suite
        }
        else {
            statements.add((stmt)(body));
        }
        return new PythonTree(new Suite(new AstList(statements, AstAdapters.stmtAdapter)));
    }
    
    public PythonTree If(PythonTree condition, PythonTree thenExp, PythonTree elseExp) {
        PyObject thenBody, elseBody;
        // If the bodys are not Suites, must do some finetuning
        if (thenExp instanceof Suite) {
            thenBody = new AstList(((Suite)thenExp).getInternalBody(), AstAdapters.stmtAdapter);
        }
        else {
            java.util.List<stmt> list = new java.util.ArrayList<stmt>();
            list.add((stmt)thenExp);
            thenBody = new AstList(list, AstAdapters.stmtAdapter);
        }
        if (elseExp instanceof Suite) {
            elseBody = new AstList(((Suite)elseExp).getInternalBody(), AstAdapters.stmtAdapter);
        }
        else {
            java.util.List<stmt> list = new java.util.ArrayList<stmt>();
            list.add((stmt)elseExp);
            elseBody = new AstList(list, AstAdapters.stmtAdapter);
        }
        return new PythonTree(new If(((Expr)condition).getValue(), thenBody, elseBody));
    }
    
    public PythonTree Loop(String var, PythonTree collection, PythonTree body) {return null;}
    
    public PythonTree Call(PythonTree target, String method, java.util.List<PythonTree> args) {
        Attribute func = new Attribute(target, new PyString(method), AstAdapters.expr_context2py(expr_contextType.Load));
        java.util.List<expr> expr_args = new java.util.ArrayList<expr>();
        for (PythonTree t : args) {
            // The PythonTree only holds Expr type
            expr_args.add(((Expr)t).getInternalValue());
        }
        return new PythonTree(new Expr(new Call(func, new AstList(expr_args, AstAdapters.exprAdapter), Py.None, Py.None, Py.None))); // No such thing as keywords, starargs, or kwargs in batch language
    }
    
    public PythonTree In(String location) {
        return new PythonTree(new Expr(new Name(new PyString(location), AstAdapters.expr_context2py(expr_contextType.Load))));
    }
    
    public PythonTree Out(String location, PythonTree expression) {return null;}
    
    public String RootName() {return null;}
    
    public PythonTree Root() {return null;}
    
    public PythonTree Assign(PythonTree target, PythonTree source) {
        return Assign(null, target, source);
    }
    
    public PythonTree Prim(Op op, PythonTree... args) {return null;}
    
    public PythonTree Call(PythonTree target, String method, PythonTree... args) {return null;}
    
    public PythonTree Skip() {return null;}
    
	public PythonTree Other(Object external, PythonTree... subs) {return null;}
    
	public PythonTree Other (Object external, java.util.List<PythonTree> subs) {return null;}
    
	public PythonTree DynamicCall(PythonTree target, String method, java.util.List<PythonTree> args) {return null;}
    
	public PythonTree Mobile(String type, Object obj, PythonTree exp) {return null;}

  @Override
  public PythonTree Mobile(String type, PythonTree exp) {
    throw new Error("NOT DEFINED");
  }

  @Override
  public PythonTree setExtra(PythonTree exp, Object extra) {
    System.out.println("MISSING CASE??");
    return exp;
  }
}

