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

import polyglot.types.*;
import polyglot.util.*;
import polyglot.visit.*;

/**
 * A <code>TypeNode</code> represents the syntactic representation of a
 * <code>Type</code> within the abstract syntax tree.
 */
public class ArrayTypeNode_c extends TypeNode_c implements ArrayTypeNode
{
    protected TypeNode base;

    public ArrayTypeNode_c(Position pos, TypeNode base) {
	super(pos);
	assert(base != null);
	this.base = base;
    }

    public TypeNode base() {
        return base;
    }

    public ArrayTypeNode base(TypeNode base) {
        ArrayTypeNode_c n = (ArrayTypeNode_c) copy();
	n.base = base;
	return n;
    }

    protected ArrayTypeNode_c reconstruct(TypeNode base) {
        if (base != this.base) {
	    ArrayTypeNode_c n = (ArrayTypeNode_c) copy();
	    n.base = base;
	    return n;
	}

	return this;
    }
    
    public boolean isDisambiguated() {
        return false;
    }
    
    public Node visitChildren(NodeVisitor v) {
        TypeNode base = (TypeNode) visitChild(this.base, v);
	return reconstruct(base);
    }

    public Node buildTypes(TypeBuilder tb) throws SemanticException {
	TypeSystem ts = tb.typeSystem();
        return type(ts.arrayOf(position(), base.type()));
    }

    public Node disambiguate(AmbiguityRemover ar) throws SemanticException {
	TypeSystem ts = ar.typeSystem();
	NodeFactory nf = ar.nodeFactory();

        if (! base.isDisambiguated()) {
            return this;
        }

        Type baseType = base.type();

        if (! baseType.isCanonical()) {
            return this;
	}

        return nf.CanonicalTypeNode(position(),
		                    ts.arrayOf(position(), baseType));
    }

    public Node typeCheck(TypeChecker tc) throws SemanticException {
	throw new InternalCompilerError(position(),
	    "Cannot type check ambiguous node " + this + ".");
    }

    public Node exceptionCheck(ExceptionChecker ec) throws SemanticException {
	throw new InternalCompilerError(position(),
	    "Cannot exception check ambiguous node " + this + ".");
    }

    public void prettyPrint(CodeWriter w, PrettyPrinter tr) {
        print(base, w, tr);
        w.write("[]");
    }

    public String toString() {
        return base.toString() + "[]";
    }
    public Node copy(NodeFactory nf) {
        return nf.ArrayTypeNode(this.position, this.base);
    }
}
