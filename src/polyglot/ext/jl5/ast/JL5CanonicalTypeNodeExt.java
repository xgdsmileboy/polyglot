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

import java.util.LinkedHashSet;
import java.util.Set;

import polyglot.ast.CanonicalTypeNode;
import polyglot.ast.Node;
import polyglot.ext.jl5.types.IntersectionType;
import polyglot.ext.jl5.types.JL5Context;
import polyglot.ext.jl5.types.JL5ParsedClassType;
import polyglot.ext.jl5.types.JL5SubstClassType;
import polyglot.ext.jl5.types.JL5TypeSystem;
import polyglot.ext.jl5.types.RawClass;
import polyglot.ext.jl5.types.TypeVariable;
import polyglot.ext.jl5.types.WildCardType;
import polyglot.frontend.MissingDependencyException;
import polyglot.frontend.Scheduler;
import polyglot.frontend.goals.Goal;
import polyglot.types.ArrayType;
import polyglot.types.ClassType;
import polyglot.types.ReferenceType;
import polyglot.types.SemanticException;
import polyglot.types.Type;
import polyglot.util.Position;
import polyglot.util.SerialVersionUID;
import polyglot.visit.TypeChecker;

public class JL5CanonicalTypeNodeExt extends JL5Ext {
    private static final long serialVersionUID = SerialVersionUID.generate();

    /**
     * This method takes a type, and, if that type 
     * is a class with type parameters, then
     * make it a raw class. Also, if the type is a 
     * class with no type variables, but is an inner
     * class of a raw class, then the class should 
     * be a raw class (i.e., the erasure of the type).
     */
    public static Type makeRawIfNeeded(Type type, Position pos) {
        if (type.isClass()) {
            JL5TypeSystem ts = (JL5TypeSystem) type.typeSystem();
            if (type instanceof JL5ParsedClassType
                    && !((JL5ParsedClassType) type).typeVariables().isEmpty()) {
                // needs to be a raw type
                return ts.rawClass((JL5ParsedClassType) type, pos);
            }
            if (type.toClass().isInnerClass()) {
                ClassType t = type.toClass();
                ClassType outer = type.toClass().outer();
                while (t.isInnerClass() && outer != null) {
                    if (outer instanceof RawClass) {
                        // an inner class of a raw class should be a raw class.
                        return ts.erasureType(type);
                    }
                    t = outer;
                    outer = outer.outer();
                }
            }
        }
        return type;
    }

    @Override
    public Node typeCheck(TypeChecker tc) throws SemanticException {
        CanonicalTypeNode n = (CanonicalTypeNode) this.node();
        Type t = n.type();
        if (t instanceof JL5SubstClassType) {
            JL5SubstClassType st = (JL5SubstClassType) t;
            JL5TypeSystem ts = (JL5TypeSystem) tc.typeSystem();

            // Check for rare types: e.g., Outer<String>.Inner, where Inner has uninstantiated type variables
            // See JLS 3rd ed. 4.8
            if (st.isInnerClass() && !st.base().typeVariables().isEmpty()) {
                // st is an inner class, with type variables. Make sure that
                // these type variables are acutally instantiated
                for (TypeVariable tv : st.base().typeVariables()) {
                    if (!st.subst().substitutions().keySet().contains(tv)) {
                        throw new SemanticException("\"Rare\" types are not allowed: cannot "
                                                            + "use raw class "
                                                            + st.name()
                                                            + " when the outer class "
                                                            + st.outer()
                                                            + " has instantiated type variables.",
                                                    n.position());
                    }
                }

            }
            if (!st.base().typeVariables().isEmpty()) {
                // check that arguments obey their bounds.
                // first we must perform capture conversion. see beginning of JLS 4.5            
                JL5SubstClassType capCT =
                        (JL5SubstClassType) ts.applyCaptureConversion(st,
                                                                      n.position());

                for (int i = 0; i < capCT.actuals().size(); i++) {
                    TypeVariable ai = capCT.base().typeVariables().get(i);
                    Type xi = capCT.actuals().get(i);
                    if (!ai.upperBound().isCanonical()) {
                        // need to disambiguate
                        Scheduler scheduler =
                                tc.job().extensionInfo().scheduler();
                        Goal g = scheduler.SupertypesResolved(st.base());
                        throw new MissingDependencyException(g);
                    }
                    //require that arguments obey their bounds
                    if (!ts.isSubtype(xi,
                                      capCT.subst().substType(ai.upperBound()))) {
                        throw new SemanticException("Type argument "
                                + st.actuals().get(i)
                                + " is not a subtype of its declared bound "
                                + ai.upperBound(), n.position());
                    }
                }
            }
        }

        // check for uses of type variables in static contexts
        if (tc.context().inStaticContext()
                && !((JL5Context) tc.context()).inCTORCall()) {
            for (TypeVariable tv : findInstanceTypeVariables(t)) {
                throw new SemanticException("Type variable " + tv
                        + " cannot be used in a static context", n.position());

            }
        }
        ClassType currentClass = tc.context().currentClass();
        JL5Context jc = (JL5Context) tc.context();
        if (jc.inExtendsClause()) {
            currentClass = jc.extendsClauseDeclaringClass();
        }
        if (currentClass != null) {
            if (currentClass.isNested() && !currentClass.isInnerClass()) {
                // the current class is static.
                for (TypeVariable tv : findInstanceTypeVariables(t)) {
                    if (!tv.declaringClass().equals(currentClass)) {
                        throw new SemanticException("Type variable "
                                                            + tv
                                                            + " of class "
                                                            + tv.declaringClass()
                                                            + " cannot be used in a nested class",
                                                    n.position());
                    }
                }
            }

        }

        return this.superDel().typeCheck(this.node(), tc);
    }

    private Set<TypeVariable> findInstanceTypeVariables(Type t) {
        Set<TypeVariable> s = new LinkedHashSet<TypeVariable>();
        findInstanceTypeVariables(t, s);
        return s;
    }

    private void findInstanceTypeVariables(Type t, Set<TypeVariable> tvs) {
        if (t instanceof TypeVariable) {
            TypeVariable tv = (TypeVariable) t;
            if (tv.declaredIn() == TypeVariable.TVarDecl.CLASS_TYPE_VARIABLE) {
                tvs.add(tv);
            }
        }
        if (t instanceof ArrayType) {
            ArrayType at = (ArrayType) t;
            findInstanceTypeVariables(at.base(), tvs);
        }
        if (t instanceof WildCardType) {
            WildCardType at = (WildCardType) t;
            findInstanceTypeVariables(at.upperBound(), tvs);
        }
        if (t instanceof JL5SubstClassType) {
            JL5SubstClassType ct = (JL5SubstClassType) t;
            for (ReferenceType at : ct.actuals()) {
                findInstanceTypeVariables(at, tvs);
            }
        }
        if (t instanceof IntersectionType) {
            IntersectionType it = (IntersectionType) t;
            for (Type at : it.bounds()) {
                findInstanceTypeVariables(at, tvs);
            }
        }
        if (t.isClass() && t.toClass().isNested()) {
            findInstanceTypeVariables(t.toClass().outer(), tvs);
        }
    }
}
