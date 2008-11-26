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

package polyglot.frontend.goals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import polyglot.ast.NodeFactory;
import polyglot.frontend.Compiler;
import polyglot.frontend.EmptyPass;
import polyglot.frontend.ExtensionInfo;
import polyglot.frontend.Job;
import polyglot.frontend.Pass;
import polyglot.frontend.Scheduler;
import polyglot.frontend.VisitorPass;
import polyglot.main.Version;
import polyglot.types.TypeSystem;
import polyglot.util.ErrorQueue;
import polyglot.visit.ClassSerializer;
import polyglot.visit.InnerClassRemover;

/**
 * The <code>Serialized</code> goal is reached after typing information is serialized
 * into the compiled code. 
 */
public class Serialized extends SourceFileGoal {
    public static Goal create(Scheduler scheduler, Job job) {
        return scheduler.internGoal(new Serialized(job));
    }

    protected Serialized(Job job) { super(job); }
    
    public Pass createPass(ExtensionInfo extInfo) {
        Compiler compiler = extInfo.compiler();
        if (compiler.serializeClassInfo()) {
            TypeSystem ts = extInfo.typeSystem();
            NodeFactory nf = extInfo.nodeFactory();
            if (false)
            return new VisitorPass(this,
                     new InnerClassRemover(job, ts, nf));
            return new VisitorPass(this,
                                   createSerializer(ts,
                                                    nf,
                                                    job().source().lastModified(),
                                                    compiler.errorQueue(),
                                                    extInfo.version()));
        }
        else {
            return new EmptyPass(this);
        }
    }
    
    protected ClassSerializer createSerializer(TypeSystem ts, NodeFactory nf,
            Date lastModified, ErrorQueue eq, Version version) {
        return new ClassSerializer(ts, nf, lastModified, eq, version);
    }
    
    public Collection prerequisiteGoals(Scheduler scheduler) {
        List l = new ArrayList();
        l.add(scheduler.TypeChecked(job));
//        l.add(scheduler.ConstantsChecked(job));
        l.add(scheduler.ReachabilityChecked(job));
        l.add(scheduler.ExceptionsChecked(job));
        l.add(scheduler.ExitPathsChecked(job));
        l.add(scheduler.InitializationsChecked(job));
        l.add(scheduler.ConstructorCallsChecked(job));
        l.add(scheduler.ForwardReferencesChecked(job));
        l.addAll(super.prerequisiteGoals(scheduler));
        return l;
    }
}
