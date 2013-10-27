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

import polyglot.ast.LocalDecl;
import polyglot.ast.Node;
import polyglot.ext.jl5.types.TypeVariable;
import polyglot.ext.jl5.types.TypeVariable.TVarDecl;
import polyglot.types.Flags;
import polyglot.types.SemanticException;
import polyglot.util.CodeWriter;
import polyglot.util.SerialVersionUID;
import polyglot.visit.PrettyPrinter;
import polyglot.visit.TypeChecker;

public class JL5LocalDeclDel extends JL5AnnotatedElementDel {
    private static final long serialVersionUID = SerialVersionUID.generate();

    @Override
    public Node typeCheck(TypeChecker tc) throws SemanticException {
        LocalDecl n = (LocalDecl) this.node();
        if (!n.flags().clear(Flags.FINAL).equals(Flags.NONE)) {
            throw new SemanticException("Modifier: " + n.flags().clearFinal()
                    + " not allowed here.", n.position());
        }
        if (n.type().type() instanceof TypeVariable
                && tc.context().inStaticContext()) {
            if (((TypeVariable) n.type().type()).declaredIn()
                                                .equals(TVarDecl.CLASS_TYPE_VARIABLE))
                throw new SemanticException("Cannot access non-static type: "
                        + ((TypeVariable) n.type().type()).name()
                        + " in a static context.", n.position());
        }
        return super.typeCheck(tc);
    }

    @Override
    public void prettyPrint(CodeWriter w, PrettyPrinter tr) {
        JL5AnnotatedElementExt ext =
                (JL5AnnotatedElementExt) JL5Ext.ext(this.node());
        for (AnnotationElem ae : ext.annotationElems()) {
            ae.del().prettyPrint(w, tr);
            w.newline();
        }

        super.prettyPrint(w, tr);
    }

}
