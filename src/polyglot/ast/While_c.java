/*******************************************************************************
 * This file is part of the Polyglot extensible compiler framework.
 *
 * Copyright (c) 2000-2008 Polyglot project group, Cornell University
 * Copyright (c) 2006-2008 IBM Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * This program and the accompanying materials are made available under
 * the terms of the Lesser GNU Public License v2.0 which accompanies this
 * distribution.
 * 
 * The development of the Polyglot project has been supported by a
 * number of funding sources, including DARPA Contract F30602-99-1-0533,
 * monitored by USAF Rome Laboratory, ONR Grant N00014-01-1-0968, NSF
 * Grants CNS-0208642, CNS-0430161, and CCF-0133302, an Alfred P. Sloan
 * Research Fellowship, and an Intel Research Ph.D. Fellowship.
 *
 * See README for contributors.
 ******************************************************************************/

package polyglot.ast;

import polyglot.ast.*;
import polyglot.types.*;
import polyglot.visit.*;
import polyglot.util.*;
import java.util.*;

/**
 * An immutable representation of a Java language <code>while</code>
 * statement.  It contains a statement to be executed and an expression
 * to be tested indicating whether to reexecute the statement.
 */ 
public class While_c extends Loop_c implements While
{
    protected Expr cond;
    protected Stmt body;

    public While_c(Position pos, Expr cond, Stmt body) {
	super(pos);
	assert(cond != null && body != null);
	this.cond = cond;
	this.body = body;
    }

    /** Get the conditional of the statement. */
    public Expr cond() {
	return this.cond;
    }

    /** Set the conditional of the statement. */
    public While cond(Expr cond) {
	While_c n = (While_c) copy();
	n.cond = cond;
	return n;
    }

    /** Get the body of the statement. */
    public Stmt body() {
	return this.body;
    }

    /** Set the body of the statement. */
    public While body(Stmt body) {
	While_c n = (While_c) copy();
	n.body = body;
	return n;
    }

    /** Reconstruct the statement. */
    protected While_c reconstruct(Expr cond, Stmt body) {
	if (cond != this.cond || body != this.body) {
	    While_c n = (While_c) copy();
	    n.cond = cond;
	    n.body = body;
	    return n;
	}

	return this;
    }

    /** Visit the children of the statement. */
    public Node visitChildren(NodeVisitor v) {
	Expr cond = (Expr) visitChild(this.cond, v);
	Node body = visitChild(this.body, v);
        if (body instanceof NodeList) body = ((NodeList) body).toBlock();
	return reconstruct(cond, (Stmt) body);
    }

    /** Type check the statement. */
    public Node typeCheck(TypeChecker tc) throws SemanticException {
	TypeSystem ts = tc.typeSystem();
	
	if (! ts.typeEquals(cond.type(), ts.Boolean())) {
	    throw new SemanticException(
		"Condition of while statement must have boolean type.",
		cond.position());
	}
	
	return this;
    }

    public Type childExpectedType(Expr child, AscriptionVisitor av) {
        TypeSystem ts = av.typeSystem();

        if (child == cond) {
            return ts.Boolean();
        }

        return child.type();
    }

    public String toString() {
	return "while (" + cond + ") ...";
    }

    /** Write the statement to an output file. */
    public void prettyPrint(CodeWriter w, PrettyPrinter tr) {
	w.write("while (");
	printBlock(cond, w, tr);
	w.write(")");
	printSubStmt(body, w, tr);
    }

    public Term firstChild() {
        return cond;
    }

    public List acceptCFG(CFGBuilder v, List succs) {
        if (condIsConstantTrue()) {
            v.visitCFG(cond, body, ENTRY);
        }
        else {
            v.visitCFG(cond, FlowGraph.EDGE_KEY_TRUE, body, ENTRY, 
                             FlowGraph.EDGE_KEY_FALSE, this, EXIT);
        }

        v.push(this).visitCFG(body, cond, ENTRY);

        return succs;
    }

    public Term continueTarget() {
        return cond;
    }
    
    public Node copy(NodeFactory nf) {
        return nf.While(this.position, this.cond, this.body);
    }

}
