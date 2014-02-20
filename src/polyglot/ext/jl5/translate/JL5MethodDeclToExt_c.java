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
package polyglot.ext.jl5.translate;

import polyglot.ast.MethodDecl;
import polyglot.ast.Node;
import polyglot.ext.jl5.ast.JL5AnnotatedElementExt;
import polyglot.ext.jl5.ast.JL5NodeFactory;
import polyglot.ext.jl5.ast.JL5ProcedureDeclExt;
import polyglot.translate.ExtensionRewriter;
import polyglot.translate.ext.MethodDeclToExt_c;
import polyglot.types.SemanticException;
import polyglot.util.SerialVersionUID;

public class JL5MethodDeclToExt_c extends MethodDeclToExt_c {
    private static final long serialVersionUID = SerialVersionUID.generate();

    @Override
    public Node toExt(ExtensionRewriter rw) throws SemanticException {
        MethodDecl n = (MethodDecl) node();
        JL5NodeFactory to_nf = (JL5NodeFactory) rw.to_nf();
        return to_nf.MethodDecl(n.position(),
                                n.flags(),
                                JL5AnnotatedElementExt.annotationElems(n),
                                n.returnType(),
                                n.id(),
                                n.formals(),
                                n.throwTypes(),
                                n.body(),
                                JL5ProcedureDeclExt.typeParams(n));
    }

}
