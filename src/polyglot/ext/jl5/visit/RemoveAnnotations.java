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
package polyglot.ext.jl5.visit;

import java.util.Collections;

import polyglot.ast.MethodDecl;
import polyglot.ast.Node;
import polyglot.ast.NodeFactory;
import polyglot.ext.jl5.ast.AnnotatedElement;
import polyglot.ext.jl5.ast.AnnotationElem;
import polyglot.ext.jl5.ast.AnnotationElemDecl;
import polyglot.ext.jl5.ast.JL5ClassDecl;
import polyglot.ext.jl5.types.JL5Flags;
import polyglot.frontend.Job;
import polyglot.types.Flags;
import polyglot.types.SemanticException;
import polyglot.types.TypeSystem;
import polyglot.visit.ContextVisitor;
import polyglot.visit.NodeVisitor;

/**
 * Remove annotations
 */
public class RemoveAnnotations extends ContextVisitor {
    public RemoveAnnotations(Job job, TypeSystem ts, NodeFactory nf) {
        super(job, ts, nf);
    }

    @Override
    protected Node leaveCall(Node parent, Node old, Node n, NodeVisitor v)
            throws SemanticException {
        if (n instanceof JL5ClassDecl) {
            JL5ClassDecl cd = (JL5ClassDecl) n;
            cd = (JL5ClassDecl) cd.flags(JL5Flags.clearAnnotation(cd.flags()));
            cd.type().flags(JL5Flags.clearAnnotation(cd.type().flags()));
            cd = (JL5ClassDecl) cd.annotationElems(Collections.<AnnotationElem> emptyList());
            return cd;
        }
        if (n instanceof AnnotationElemDecl) {
            return translateAnnotationElemDecl((AnnotationElemDecl) n);
        }
        if (n instanceof AnnotatedElement) {
            // remove the annotations
            return (Node) ((AnnotatedElement) n).annotationElems(Collections.<AnnotationElem> emptyList());
        }
        return n;
    }

    private MethodDecl translateAnnotationElemDecl(AnnotationElemDecl n) {
        Flags f = JL5Flags.clearAnnotation(n.flags());
        MethodDecl md =
                nodeFactory().MethodDecl(n.position(),
                                         f,
                                         n.returnType(),
                                         n.id(),
                                         n.formals(),
                                         n.throwTypes(),
                                         n.body());
        n.methodInstance().flags(JL5Flags.clearAnnotation(n.methodInstance()
                                                           .flags()));
        md = md.methodInstance(n.methodInstance());
        return md;
    }
}