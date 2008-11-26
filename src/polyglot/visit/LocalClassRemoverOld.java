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

package polyglot.visit;

import polyglot.ast.*;
import polyglot.frontend.Job;
import polyglot.types.*;
import polyglot.util.*;

import java.util.*;

/**
 * @author nystrom
 *
 * This class translates local classes and anonymous classes to member classes.
 * It adds fields to the classes for each local variable in the enclosing method
 * that is used in the class body.
 */
public class LocalClassRemoverOld extends ContextVisitor
{
    List unclaimedDecls;
    Map envMap;
    int[] count;
    
    public LocalClassRemoverOld(Job job, TypeSystem ts, NodeFactory nf) {
        super(job, ts, nf);
        envMap = new HashMap();
        unclaimedDecls = new ArrayList();
        count = new int[1];
    }

    protected String newFieldName(String name) {
        return namePrefix() + name;
    }

    /*
     * Generate the new name for a field that comes from a final local variable.
     */
    protected String namePrefix() {
        return "jl$";
    }

    static class EnvCollector extends ContextVisitor {
        List env;
        Context outerContext;
        Context innerContext;

        EnvCollector(Job job, TypeSystem ts, NodeFactory nf, Context context, Context innerContext) {
            super(job, ts, nf);
            this.env = new ArrayList();
            this.outerContext = context;
            this.innerContext = innerContext;
        }

        List env() {
            return env;
        }

        public NodeVisitor begin() {
            ContextVisitor v = (ContextVisitor) super.begin();
            v.context = innerContext;
            return v;
        }
        
        /*
        public Node override(Node parent, Node n) {
            if (n instanceof LocalClassDecl) {
                return n;
            }
            if (parent instanceof New && n instanceof ClassBody) {
                return n;
            }
            return super.override(n);
        }
        */

        public Node leaveCall(Node old, Node n, NodeVisitor v) throws SemanticException {
            if (n instanceof Local) {
                Local local = (Local) n;
//                System.out.println("checking " + n + "\nin " + context + "\nin " + outerContext);
                
                // The variable should be in the environment if it's not in the local
                // scope here, but is in the local scope of outerContext.
                if (! context.isLocal(local.name())) {
                    try {
                        LocalInstance li = outerContext.findLocal(local.name());
                        // found!
//                        System.out.println("  found " + li);
                        if (outerContext.isLocal(local.name())) {
                            // and not local to the outer context too
//                        System.out.println("  defined in enclosing method: " + n);
                            env.add(local.localInstance().orig());
                        }
                    }
                    catch (SemanticException e) {
//                        System.out.println("  not in scope of enclosing method: " + n);
                        // The local was defined somewhere within the class body.
                    }
                }
                else {
//                    System.out.println("  is local to the inner scope " + n);
                }
            }

            return super.leaveCall(old, n, v);
        }
    }
    
    List computeClosure(ClassBody body, Context context, Context innerContext) {
        EnvCollector v = new EnvCollector(job, ts, nf, context, innerContext);
        v = (EnvCollector) v.begin();
        body.visit(v);
        v.finish();
        
//        System.out.println("env of:");
//        body.del().prettyPrint(System.out);
//        System.out.println(" = " + v.env());

        return v.env();
    }
    
    String generateName() {
        return generateName("Anon");
    }

    String generateName(String base) {
        return base + "$jl" + count[0]++;
    }
    
    FieldInstance localToField(ParsedClassType ct, LocalInstance li) {
        FieldInstance fi = ts.fieldInstance(li.position(), ct, li.flags().Protected(), li.type(), namePrefix() + li.name());
        return fi;
    }
    
    FieldDecl createFieldDecl(FieldInstance fi) {
        FieldDecl fd = nf.FieldDecl(fi.position(), fi.flags(), nf.CanonicalTypeNode(fi.position(), fi.type()), fi.name());
        fd = fd.fieldInstance(fi);
        return fd;
    }
    
    class ClassBodyTranslator extends ContextVisitor { 
        ParsedClassType ct;
        Map fieldMap;
        Context outerContext;
        
        ClassBodyTranslator(Job job, TypeSystem ts, NodeFactory nf, Context context, ParsedClassType ct, Map fieldMap) {
            super(job, ts, nf);
            this.ct = ct;
            this.fieldMap = fieldMap;
            this.outerContext = context;
        }
       
        public NodeVisitor begin() {
            ContextVisitor v = (ContextVisitor) super.begin();
            v.context = outerContext;
            return v;
        } 
   
        public Node leaveCall(Node old, Node n, NodeVisitor v) throws SemanticException {
            if (n instanceof Local) {
                Local l = (Local) n;
                FieldInstance fi = (FieldInstance) fieldMap.get(new IdentityKey(l.localInstance().orig()));
                if (fi != null) {
                    Special this_;
                    if (ct.equals(context.currentClass())) {
                        this_ = nf.Special(l.position(), Special.THIS);
                    }
                    else {
                        this_ = nf.Special(l.position(), Special.THIS, nf.CanonicalTypeNode(l.position(), ct));
                    }
                    this_ = (Special) this_.type(ct);
                    Field f = nf.Field(l.position(), this_, fi.name());
                    f = f.fieldInstance(fi);
                    f = (Field) f.type(fi.type());
                    n = f;
                }
            }
            if (n instanceof ConstructorDecl) {
                ConstructorDecl ctd = (ConstructorDecl) n;
                ClassType ct2 = (ClassType)
                    ctd.constructorInstance().container();
                if (ct2.equals(ct)) {
                    ctd = translateConstructorDecl(ct, ctd, fieldMap);
                }
                n = ctd;
            }
            return super.leaveCall(old, n, v);
        }
    }
    
    ConstructorInstance createEmptyCI(ParsedClassType ct) {
        ConstructorInstance ci = ts.constructorInstance(ct.position(), ct,
                                                        Flags.PRIVATE,
                                                        Collections.EMPTY_LIST,
                                                        Collections.EMPTY_LIST);
        ct.addConstructor(ci);
        return ci;
    }

    ConstructorDecl createEmptyConstructorDecl(ParsedClassType ct, ConstructorInstance ci) {
        List stmts = new ArrayList();
        
        try {
            ConstructorInstance superCI = ct.typeSystem().findConstructor((ClassType) ct.superType(),
                                                                          Collections.EMPTY_LIST,
                                                                          ct);
            ConstructorCall superCall;

            if (!ct.flags().isStatic()) {
                Special this_ = nf.Special(ci.position(), Special.THIS, nf.CanonicalTypeNode(ci.position(), ct.container()));
                this_ = (Special) this_.type(ct.container());
                superCall = nf.SuperCall(ci.position(), this_,
                                         Collections.EMPTY_LIST);
            }
            else {
                superCall = nf.SuperCall(ci.position(), Collections.EMPTY_LIST);
            }

            superCall = superCall.constructorInstance(superCI);
            stmts.add(superCall);
        }
        catch (SemanticException e) {
        }

        Block b = nf.Block(ci.position(), stmts);
        
        ConstructorDecl cd = nf.ConstructorDecl(ci.position(), ci.flags(), ct.name(), Collections.EMPTY_LIST, Collections.EMPTY_LIST, b);
        cd = cd.constructorInstance(ci);
        
        return cd;
    }
    
    void addEnvToCI(ConstructorInstance ci, List env) {
        List formals = new ArrayList(ci.formalTypes());
        formals.addAll(envAsFormalTypes(env));
        ci.setFormalTypes(formals);
    }

    ConstructorDecl translateConstructorDecl(ParsedClassType ct, ConstructorDecl cd, Map m) {
        List env = env(ct);

        addEnvToCI(cd.constructorInstance(), env);

        cd = cd.name(ct.name());
        
        List envAsFormals = envAsFormals(env);

        // Add the new formals.
        List newFormals = new ArrayList();
        newFormals.addAll(cd.formals());
        newFormals.addAll(envAsFormals);
        cd = cd.formals(newFormals);

        if (cd.body() == null) {
            // Must be a native constructor; just let the programmer
            // deal with it.
            return cd;
        }

        List oldStmts = cd.body().statements();
        List newStmts = new ArrayList();

        // Check if this constructor invokes another with a this call.
        // If so, don't initialize the fields, but do pass the environment
        // to the other constructor.
        ConstructorCall cc = null;

        if (oldStmts.size() >= 1) {
            Stmt s = (Stmt) oldStmts.get(0);
            if (s instanceof ConstructorCall) {
                cc = (ConstructorCall) s;
            }
        }

        if (cc != null && cc.kind() == ConstructorCall.THIS) {
            List newArgs = new ArrayList();
            newArgs.addAll(cc.arguments());
            newArgs.addAll(envAsLocalActuals(envAsFormals));

            ConstructorCall newCC = (ConstructorCall) cc.arguments(newArgs);
            newStmts.add(newCC);
        }
        else if (cc != null) {
            // adjust the super call arguments
            List newArgs = new ArrayList();
            newArgs.addAll(cc.arguments());
            
            List superEnvAsFormals = new ArrayList();
            List superEnv = env((ClassType) ct.superType());
            for (Iterator i = superEnv.iterator(); i.hasNext(); ) {
                LocalInstance li = (LocalInstance) i.next();
                Iterator j = envAsFormals.iterator();
                Iterator k = env.iterator();
                while (j.hasNext()) {
                    Formal f = (Formal) j.next();
                    LocalInstance li2 = (LocalInstance) k.next();
                    // f.localInstance() is a copy of li2.
                    if (li.equals(li2)) {
                        superEnvAsFormals.add(f);
                    }
                }
            }
            newArgs.addAll(envAsLocalActuals(superEnvAsFormals));

            ConstructorCall newCC = (ConstructorCall) cc.arguments(newArgs);
            newStmts.add(newCC);
        }

        // Initialize the new fields.
        if (cc == null || cc.kind() == ConstructorCall.SUPER) {
            for (Iterator i = env.iterator(); i.hasNext(); ) {
                LocalInstance li = (LocalInstance) i.next();
                FieldInstance fi = (FieldInstance) m.get(new IdentityKey(li));
                
                if (fi == null) continue;
                if (! fi.container().equals(ct)) continue;
                
                Special this_ = nf.Special(cd.position(), Special.THIS);
                this_ = (Special) this_.type(ct);
                
                Field target = nf.Field(cd.position(), this_, fi.name());
                target = target.fieldInstance(fi);
                target = (Field) target.type(fi.type());
                
                Local source = nf.Local(cd.position(), li.name());
                source = source.localInstance(li);
                source = (Local) source.type(li.type());
                
                FieldAssign assign = nf.FieldAssign(cd.position(), target, Assign.ASSIGN, source);
                assign = (FieldAssign) assign.type(target.type());
                
                newStmts.add(nf.Eval(cd.position(), assign));
            }
        }

        if (cc != null) {
            for (int i = 1; i < oldStmts.size(); i++) {
                newStmts.add(oldStmts.get(i));
            }
        }
        else {
            newStmts.addAll(oldStmts);
        }

        Block b = cd.body().statements(newStmts);
        cd = (ConstructorDecl) cd.body(b);
        return cd;

        /*
        void m() {
            final T x;
            class C {
                C(y) { super(y); ... x ... }
                C() { this(0); ... x ... }
            }
            new C(e);
        }

        ->

        class C {
            T x;
            C(y) { super(y); this.x = x; ... this.x ... }
            C() { this(0); ... this.x ... }
        }
        void m() {
            final T x;
            new C(e, x);
        }
        */
    }
    
    ClassDecl createMemberClass(ParsedClassType ct, ClassBody body) {
        TypeNode superClass = nf.CanonicalTypeNode(ct.position(), ct.superType());
        List interfaces = new TransformingList(ct.interfaces(),
                                               new Transformation() {
            public Object transform(Object o) {
                Type t = (Type) o;
                return nf.CanonicalTypeNode(t.position(), t);
            }
        });

        ClassDecl cd = nf.ClassDecl(ct.position(), ct.flags(), ct.name(), superClass, interfaces, body);
        cd.type(ct);
        return cd;
    }
    
    List env(ClassType ct) {
        if (ct != null) {
            List superEnv = env((ClassType) ct.superType());
            List env = (List) envMap.get(ct);
            if (env == null || env.isEmpty()) {
                return superEnv;
            }
            if (superEnv.isEmpty()) {
                return env;
            }
            List l = new ArrayList();
            l.addAll(superEnv);
            l.removeAll(env);
            l.addAll(env);
            return l;
        }
        return Collections.EMPTY_LIST;
    }
    
    List envAsFormalTypes(List env) {
        List formals = new ArrayList();
        for (Iterator i = env.iterator(); i.hasNext(); ) {
            LocalInstance li = (LocalInstance) i.next();
            formals.add(li.type());
        }
        return formals;
    }
    
    List envAsFormals(List env) {
        List formals = new ArrayList();
        for (Iterator i = env.iterator(); i.hasNext(); ) {
            LocalInstance li = (LocalInstance) i.next();
            Formal f = nf.Formal(Position.compilerGenerated(), li.flags(),
                                 nf.CanonicalTypeNode(li.position(), li.type()),
                                 li.name());
            f = f.localInstance((LocalInstance) li.copy());
            formals.add(f);
        }
        return formals;
    }
    
    List envAsLocalActuals(List envAsFormals) {
        List actuals = new ArrayList();
        for (Iterator i = envAsFormals.iterator(); i.hasNext(); ) {
            Formal f = (Formal) i.next();
            LocalInstance li = f.localInstance();
            Local local = nf.Local(li.position(), li.name());
            local = local.localInstance(li);
            local = (Local) local.type(li.type());
            actuals.add(local);
        }
        return actuals;
    }

    List envAsActuals(List env) {
        List actuals = new ArrayList();
        for (Iterator i = env.iterator(); i.hasNext(); ) {
            LocalInstance li = (LocalInstance) i.next();
            Local local = nf.Local(li.position(), li.name());
            local = local.localInstance(li);
            local = (Local) local.type(li.type());
            actuals.add(local);
        }
        return actuals;
    }

    protected boolean isLocal(ClassType ct) {
        for (ClassType sup = ct; sup != null; sup = (ClassType) sup.superType()) {
            if (sup.isLocal()) {
                return true;
            }
        }
        return false;
    }
    
    protected Node leaveCall(Node old, Node n, NodeVisitor v) throws SemanticException {
          Context innerContext = ((LocalClassRemoverOld) v).context();

          // If this class extends a local class, we need to change its constructor
          // to pass in the environment.  Need to split into two passes.
          if (n instanceof ConstructorDecl) {
              ParsedClassType ct = context.currentClassScope();
              if (isLocal(ct) && ! ct.isLocal()) {
                  n = translateConstructorDecl(ct, (ConstructorDecl) n, Collections.EMPTY_MAP);
              }
          }
          if (n instanceof New) {
              New newExp = (New) n;
              ClassType ct = (ClassType) newExp.objectType().type();
              
              if (newExp.body() != null) {
                  ParsedClassType pct = (ParsedClassType) newExp.anonType();
                  pct.kind(ClassType.MEMBER);
                  pct.name(generateName());
                  
                  ParsedClassType container = context.currentClassScope();
                  container.addMemberClass(pct);
                  pct.setContainer(container);
                  pct.outer(container);

                  if (pct.inStaticContext()) {
                      pct.setFlags(Flags.PRIVATE.Static());
                  }
                  else {
                      pct.setFlags(Flags.PRIVATE);
                  }

                  if (context.inStaticContext()) {
                      pct.setFlags(pct.flags().Static());
                  }

                  Context c = newExp.del().enterChildScope(newExp.body(), context);
                  ClassBody body = newExp.body();
                  translateAnonClassBody(pct, newExp.arguments(), body, c);

                  newExp = newExp.body(null);
                  newExp = newExp.anonType(null);
                  newExp = newExp.objectType(nf.CanonicalTypeNode(newExp.position(), pct));

                  ct = pct;
              }
              
              // If instantiating a local class, pass in the environment at
              // the class declaration.  env(ct) will be empty of the class
              // was not local.
              List newArgs = new ArrayList(newExp.arguments());
              newArgs.addAll(envAsActuals(env(ct)));
              newExp = (New) newExp.arguments(newArgs);
              
              n = newExp;
          }
          if (n instanceof LocalClassDecl) {
              LocalClassDecl lcd = (LocalClassDecl) n;
              ClassDecl cd = lcd.decl();
              ParsedClassType pct = cd.type();
              if (pct.isLocal()) {
                  pct.kind(ClassType.MEMBER);
                  pct.name(generateName(pct.name()));

                  ParsedClassType container = context.currentClassScope();
                  container.addMemberClass(pct);
                  pct.setContainer(container);
                  pct.outer(container);

                  if (pct.inStaticContext()) {
                      pct.setFlags(pct.flags().Private().Static());
                  }
                  else {
                      pct.setFlags(pct.flags().Private());
                  }

                  if (context.inStaticContext()) {
                      pct.setFlags(pct.flags().Static());
                  }

                  ClassBody body = cd.body();
                  Context c = cd.del().enterChildScope(body, context);
                  translateLocalClassBody(pct, body, c);
              }
              return nf.Empty(lcd.position());
          }
          if (n instanceof ClassBody) {
              ClassBody cb = (ClassBody) n;
              List members = new ArrayList(cb.members());
              for (Iterator i = unclaimedDecls.iterator(); i.hasNext(); ) {
                  ClassDecl cd = (ClassDecl) i.next();
                  ClassType container = cd.type().outer();
                  if (container.equals(innerContext.currentClass())) {
                      members.add(cd);
                      i.remove();
                  }
              }
              cb = cb.members(members);
              n = cb;
          }

          n = super.leaveCall(old, n, v);
          return n;
    }

    protected NodeVisitor enterCall(Node n) throws SemanticException {
        if (n instanceof LocalClassDecl) {
            LocalClassDecl lcd = (LocalClassDecl) n;
            ClassDecl cd = lcd.decl();
            ParsedClassType pct = cd.type();
            ClassBody body = cd.body();
            Context c = cd.del().enterChildScope(body, context);
            List env = computeClosure(body, context, c);
            envMap.put(pct, env);
        }
        return super.enterCall(n);
    }
    
    protected void translateAnonClassBody(ParsedClassType ct, List arguments,
      ClassBody body, Context context) {
      // Create a constructor for the class based on the argument list.
      List stmts = new ArrayList(1);
      
      // First, find the ConstructorInstance for the super call.
      ClassType superCT = (ClassType) ct.superType();
      List argTypes = new ArrayList(arguments.size());
      for (Iterator it = arguments.iterator(); it.hasNext();)
        argTypes.add(((Expr) it.next()).type());
      ConstructorInstance superCI = null;
      try {
        superCI = ts.findConstructor(superCT, argTypes, ct);
      } catch (SemanticException e) {
        // Shouldn't happen.
      }
      
      // From the super's ConstructorInstance, create a list of formals and
      // actuals.
      List formals = new ArrayList(argTypes.size());
      List actuals = new ArrayList(argTypes.size());
      for (Iterator it = superCI.formalTypes().iterator(); it.hasNext();) {
        Type type = (Type) it.next();
        String name = "jl$arg" + formals.size();
        Id id = nf.Id(Position.compilerGenerated(), name);
        TypeNode tn = nf.CanonicalTypeNode(Position.compilerGenerated(), type);
        Formal formal =
          nf.Formal(Position.compilerGenerated(), Flags.NONE, tn, id);
        LocalInstance li =
          ts.localInstance(Position.compilerGenerated(), formal.flags(), type,
              name);
        formal = formal.localInstance(li);
        formals.add(formal);
        
        Local actual = nf.Local(Position.compilerGenerated(), id);
        actual = (Local) actual.type(type);
        actual = actual.localInstance(li);
        actuals.add(actual);
      }
      
      // Create the super call.
      Special this_ = nf.Special(Position.compilerGenerated(), Special.THIS,
          nf.CanonicalTypeNode(Position.compilerGenerated(), ct.container()));
      this_ = (Special) this_.type(ct.container());
      ConstructorCall superCall =
        nf.SuperCall(Position.compilerGenerated(), this_, actuals);
      superCall = superCall.constructorInstance(superCI);
      stmts.add(superCall);
      
      // Create the constructor declaration.
      ConstructorInstance ci =
        ts.constructorInstance(ct.position(), ct, Flags.PRIVATE, superCI
            .formalTypes(), superCI.throwTypes());
      ConstructorDecl decl =
        nf.ConstructorDecl(Position.compilerGenerated(), ci.flags(), nf.Id(
            Position.compilerGenerated(), ct.name()), formals, ci.throwTypes(),
            nf.Block(Position.compilerGenerated(), stmts));
      decl = decl.constructorInstance(ci);
      
      // Add the constructor to the body and pass to translateLocalClassBody.
      List members = new ArrayList(body.members());
      members.add(decl);
      body = body.members(members);
      translateLocalClassBody(ct, body, context);
    }
    
    protected void translateLocalClassBody(ParsedClassType ct, ClassBody body, Context context) {
        List members = new ArrayList();

        List env = env(ct);

        Map fieldMap = new HashMap();

        for (Iterator i = env.iterator(); i.hasNext(); ) {
            LocalInstance li = (LocalInstance) i.next();
            FieldInstance fi = localToField(ct, li);
            fieldMap.put(new IdentityKey(li), fi);

            ct.addField(fi);
            members.add(createFieldDecl(fi));
        }

        // Now add existing members, making sure constructors appear
        // first.  The constructors may have field
        // initializers which must be run before other initializers.
        List ctors = new ArrayList();
        List others = new ArrayList();
        for (Iterator i = body.members().iterator(); i.hasNext(); ) {
            ClassMember cm = (ClassMember) i.next();
            if (cm instanceof ConstructorDecl) {
                ctors.add(cm);
            }
            else {
                others.add(cm);
            }
        }
        
        members.addAll(ctors);
        members.addAll(others);

        body = body.members(members);

        // Rewrite the class body.
        ClassBodyTranslator v = new ClassBodyTranslator(job, ts, nf, context, ct, fieldMap);
        v = (ClassBodyTranslator) v.begin();
        body = (ClassBody) body.visit(v);
        v.finish();

//        System.out.println("----------------------------------");
//        System.out.println("new class body:");
//        body.del().prettyPrint(System.out);
//        System.out.println("----------------------------------");

        ClassDecl cd = createMemberClass(ct, body);
        cd = cd.type(ct);
        unclaimedDecls.add(cd);
    }
}
