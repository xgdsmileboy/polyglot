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

import java.util.List;

import polyglot.ast.MethodDecl;
import polyglot.ast.MethodDecl_c;
import polyglot.ast.Node;
import polyglot.ext.jl5.types.JL5Flags;
import polyglot.ext.jl5.types.JL5MethodInstance;
import polyglot.ext.jl5.types.JL5TypeSystem;
import polyglot.ext.jl5.types.TypeVariable;
import polyglot.types.Declaration;
import polyglot.types.Flags;
import polyglot.types.MethodInstance;
import polyglot.types.ParsedClassType;
import polyglot.types.SemanticException;
import polyglot.types.Type;
import polyglot.util.CodeWriter;
import polyglot.util.SerialVersionUID;
import polyglot.visit.PrettyPrinter;
import polyglot.visit.Translator;
import polyglot.visit.TypeChecker;

public class JL5MethodDeclExt extends JL5ProcedureDeclExt {
    private static final long serialVersionUID = SerialVersionUID.generate();

    protected boolean compilerGenerated;

    public boolean isGeneric() {
        if (!typeParams.isEmpty()) return true;
        return false;
    }

    public boolean isCompilerGenerated() {
        return compilerGenerated;
    }

    public MethodDecl setCompilerGenerated(boolean val) {
        MethodDecl n = (MethodDecl) node().copy();
        JL5MethodDeclExt ext = (JL5MethodDeclExt) JL5Ext.ext(n);
        ext.compilerGenerated = val;
        return n;
    }

    @Override
    protected Declaration declaration() {
        MethodDecl n = (MethodDecl) this.node();
        return n.methodInstance();
    }

    @Override
    protected Node buildTypesFinish(JL5TypeSystem ts, ParsedClassType ct,
            Flags flags, List<? extends Type> formalTypes,
            List<? extends Type> throwTypes, List<TypeVariable> typeParams) {
        MethodDecl md = (MethodDecl) this.node();
        if (ct.flags().isInterface()) {
            flags = flags.Public().Abstract();
        }

        MethodInstance mi =
                ts.methodInstance(md.position(),
                                  ct,
                                  flags,
                                  ts.unknownType(md.position()),
                                  md.name(),
                                  formalTypes,
                                  throwTypes,
                                  typeParams);
        ct.addMethod(mi);
        return md.methodInstance(mi);
    }

    @Override
    public Node typeCheck(TypeChecker tc) throws SemanticException {
        MethodDecl md = (MethodDecl) super.typeCheck(tc);
        JL5TypeSystem ts = (JL5TypeSystem) tc.typeSystem();

        JL5MethodInstance mi = (JL5MethodInstance) md.methodInstance();
        Flags flags = mi.flags();

        // repeat super class type checking so it can be specialized
        // to handle inner enum classes which indeed do have
        // static methods
        if (tc.context().currentClass().flags().isInterface()) {
            if (flags.isProtected() || flags.isPrivate()) {
                throw new SemanticException("Interface methods must be public.",
                                            md.position());
            }
        }

        try {
            ts.checkMethodFlags(flags);
        }
        catch (SemanticException e) {
            throw new SemanticException(e.getMessage(), md.position());
        }

        if (md.body() == null && !(flags.isAbstract() || flags.isNative())) {
            throw new SemanticException("Missing method body.", md.position());
        }

        if (md.body() != null && flags.isAbstract()) {
            throw new SemanticException("An abstract method cannot have a body.",
                                        md.position());
        }

        if (md.body() != null && flags.isNative()) {
            throw new SemanticException("A native method cannot have a body.",
                                        md.position());
        }

        ((MethodDecl_c) md).throwsCheck(tc);

        // check that inner classes do not declare static methods
        // unless class is enum
        if (flags.isStatic()
                && !JL5Flags.isEnum(md.methodInstance()
                                      .container()
                                      .toClass()
                                      .flags())
                && md.methodInstance().container().toClass().isInnerClass()) {
            // it's a static method in an inner class.
            throw new SemanticException("Inner classes cannot declare "
                    + "static methods.", md.position());
        }

        ((MethodDecl_c) md).overrideMethodCheck(tc);

        return md;
    }

    @Override
    public void translate(CodeWriter w, Translator tr) {
        MethodDecl md = (MethodDecl) this.node();
        JL5MethodDeclExt ext = (JL5MethodDeclExt) JL5Ext.ext(md);

        if (ext.isCompilerGenerated()) return;

        superLang().translate(this.node(), w, tr);
    }

    @Override
    protected void prettyPrintName(CodeWriter w, PrettyPrinter pp) {
        MethodDecl n = (MethodDecl) this.node();
        pp.print(n, n.returnType(), w);
        w.write(" " + n.name());
    }
}
