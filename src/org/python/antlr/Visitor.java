package org.python.antlr;

import org.python.antlr.ast.Batch;
import org.python.antlr.ast.VisitorBase;

public class Visitor extends VisitorBase {

    /**
     * Visit each of the children one by one.
     * @args node The node whose children will be visited.
     */
    public void traverse(PythonTree node) throws Exception {
        node.traverse(this);
    }


    public void visit(PythonTree[] nodes) throws Exception {
        for (int i = 0; i < nodes.length; i++) {
            visit(nodes[i]);
        }
    }

    /**
     * Visit the node by calling a visitXXX method.
     */
    public Object visit(PythonTree node) throws Exception {
        Object ret = node.accept(this);
        return ret;
    }

    protected Object unhandled_node(PythonTree node) throws Exception {
        return this;
    }


    @Override
    public Object visitBatch(Batch batch) throws Exception {
      throw new Error("SHOULD NOT HAPPEN??");
    }

}
