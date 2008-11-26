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

import java.util.*;

import polyglot.ast.NodeFactory;
import polyglot.frontend.*;
import polyglot.types.TypeSystem;
import polyglot.visit.Translator;

/**
 * Comment for <code>CompileGoal</code>
 *
 * @author nystrom
 */
public class CodeGenerated extends SourceFileGoal implements EndGoal {
    public static Goal create(Scheduler scheduler, Job job) {
        return scheduler.internGoal(new CodeGenerated(job));
    }

    /**
     * @param job The job to compile.
     */
    protected CodeGenerated(Job job) {
        super(job);
    }
    
    public Pass createPass(ExtensionInfo extInfo) {
        TypeSystem ts = extInfo.typeSystem();
        NodeFactory nf = extInfo.nodeFactory();
        return new OutputPass(this, new Translator(job(), ts, nf,
                                                   extInfo.targetFactory()));
    }
    
    public Collection prerequisiteGoals(Scheduler scheduler) {
        List l = new ArrayList();
        l.add(scheduler.Serialized(job));
        l.addAll(super.prerequisiteGoals(scheduler));
        return l;
    }
    
}
