/*******************************************************************************
 * This file is part of the Polyglot extensible compiler framework.
 *
 * Copyright (c) 2000-2012 Polyglot project group, Cornell University
 * Copyright (c) 2006-2012 IBM Corporation
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
 * monitored by USAF Rome Laboratory, ONR Grants N00014-01-1-0968 and
 * N00014-09-1-0652, NSF Grants CNS-0208642, CNS-0430161, CCF-0133302,
 * and CCF-1054172, AFRL Contract FA8650-10-C-7022, an Alfred P. Sloan 
 * Research Fellowship, and an Intel Research Ph.D. Fellowship.
 *
 * See README for contributors.
 ******************************************************************************/
package polyglot.ext.jl5.ast;

import polyglot.ast.Ambiguous;
import polyglot.ast.Node;
import polyglot.ast.TypeNode;
import polyglot.ast.TypeNode_c;
import polyglot.ext.jl5.types.JL5TypeSystem;
import polyglot.types.ReferenceType;
import polyglot.types.SemanticException;
import polyglot.types.Type;
import polyglot.util.CodeWriter;
import polyglot.util.Position;
import polyglot.util.SerialVersionUID;
import polyglot.visit.AmbiguityRemover;
import polyglot.visit.NodeVisitor;
import polyglot.visit.PrettyPrinter;

public class AmbWildCard extends TypeNode_c implements Ambiguous {
    private static final long serialVersionUID = SerialVersionUID.generate();

    private TypeNode constraint;
    private boolean isExtendsConstraint;

    public AmbWildCard(Position pos) {
        this(pos, null, true);
    }

    public AmbWildCard(Position pos, TypeNode constraint,
            boolean isExtendsConstraint) {
        super(pos);
        this.constraint = constraint;
        this.isExtendsConstraint = isExtendsConstraint;
    }

    @Override
    public Node disambiguate(AmbiguityRemover sc) throws SemanticException {
        if (this.constraint != null && !this.constraint.isDisambiguated()) {
            return this;
        }
        JL5TypeSystem ts = (JL5TypeSystem) sc.typeSystem();
        Type t;
        if (this.constraint == null) {
            t = ts.wildCardType(this.position());
        }
        else {
            ReferenceType upperBound = null;
            ReferenceType lowerBound = null;
            if (this.isExtendsConstraint) {
                upperBound = (ReferenceType) constraint.type();
            }
            else {
                lowerBound = (ReferenceType) constraint.type();
            }
            t = ts.wildCardType(this.position(), upperBound, lowerBound);
        }
        return sc.nodeFactory().CanonicalTypeNode(this.position, t);
    }

    @Override
    public void prettyPrint(CodeWriter w, PrettyPrinter tr) {
        w.write("?");
        if (constraint != null) {
            w.write(" ");
            w.write(this.isExtendsConstraint ? "extends" : "super");
            w.write(" ");
            tr.lang().prettyPrint(constraint, w, tr);
        }
    }

    protected AmbWildCard reconstruct(TypeNode constraint) {
        if (this.constraint != constraint) {
            AmbWildCard n = (AmbWildCard) this.copy();
            n.constraint = constraint;
            return n;
        }
        return this;
    }

    @Override
    public Node visitChildren(NodeVisitor v) {
        TypeNode c = visitChild(this.constraint, v);
        return reconstruct(c);
    }
}
