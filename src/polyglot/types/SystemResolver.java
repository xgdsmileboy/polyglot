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

package polyglot.types;

import polyglot.util.*;
import polyglot.frontend.ExtensionInfo;
import polyglot.main.Report;
import polyglot.types.Package;
import java.util.*;

/**
 * The <code>SystemResolver</code> is the main resolver for
 * fully-qualified names.
 */
public class SystemResolver extends CachingResolver implements TopLevelResolver {
    protected Map packageCache;
    protected ExtensionInfo extInfo;
    protected SystemResolver previous;
    protected Collection justAdded;
    
    /**
     * Create a caching resolver.
     * @param inner The resolver whose results this resolver caches.
     */
    public SystemResolver(TopLevelResolver inner, ExtensionInfo extInfo) {
        super(inner);
        this.extInfo = extInfo;
        this.packageCache = new HashMap();
        this.previous = null;
        this.justAdded = new LinkedList();
    }

    public SystemResolver previous() {
        return previous;
    }
    
    public Object copy() {
        SystemResolver r = (SystemResolver) super.copy();
        r.packageCache = new HashMap(this.packageCache);
        r.previous = this;
        r.justAdded = new LinkedList();
        return r;
    }
    
    public void installInAll(String name, Named n) {
        this.install(name, n);
        if (previous != null) {
            previous.installInAll(name, n);
        }
    }

    public boolean installedInAll(String name, Named q) {
        if (check(name) != q) {
            return false;
        }
        if (previous != null) {
            return previous.installedInAll(name, q);
        }
        return true;
    }

    /** Check if a package exists in the resolver cache. */
    protected boolean packageExistsInCache(String name) {
        for (Iterator i = cachedObjects().iterator(); i.hasNext(); ) {
            Object o = i.next();
            if (o instanceof Importable) {
                Importable im = (Importable) o;
                if (im.package_() != null &&
                    im.package_().fullName() != null &&
                    (im.package_().fullName().equals(name) ||
                     im.package_().fullName().startsWith(name + "."))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if a package exists.
     */
    public boolean packageExists(String name) {
	Boolean b = (Boolean) packageCache.get(name);
	if (b != null) {
	    return b.booleanValue();
	}
	else {
            String prefix = StringUtil.getPackageComponent(name);

            if (packageCache.get(prefix) == Boolean.FALSE) {
                packageCache.put(name, Boolean.FALSE);
                return false;
            }

            boolean exists;
            exists = packageExistsInCache(name);
            if (! exists) {
                exists = ((TopLevelResolver) inner).packageExists(name);
            }

            if (exists) {
                packageCache.put(name, Boolean.TRUE);

                do {
                    packageCache.put(prefix, Boolean.TRUE);
                    prefix = StringUtil.getPackageComponent(prefix);
                } while (! prefix.equals(""));
            }
            else {
                packageCache.put(name, Boolean.FALSE);
            }

            return exists;
	}
    }

    protected void cachePackage(Package p) {
        if (p != null) {
            packageCache.put(p.fullName(), Boolean.TRUE);
            cachePackage(p.prefix());
        }
    }

    /**
     * Check if a type is in the cache, returning null if not.
     * @param name The name to search for.
     */
    public Type checkType(String name) {
        return (Type) check(name);
    }

    public Collection justAdded() {
        return new TransformingList(justAdded, new Transformation() {
            public Object transform(Object o) {
                Object[] p = (Object[]) o;
                return p[1];
            }
        });
    }

    public void clearAdded() {
        justAdded = new LinkedList();
    }

    public void putAll(SystemResolver r) throws SemanticException {
        for (Iterator i = r.justAdded.iterator(); i.hasNext(); ) {
            Object[] e = (Object[]) i.next();
            String name = (String) e[0];
            Named n = (Named) e[1];

            install(name, n);

            if (n instanceof Package) {
                Package p = (Package) n;
                cachePackage(p);
            }
        }
    }

    /**
     * Find a type (or package) by name. For most code, this should be called
     * with the Java source name (p.A.B), not the class file name (p.A$B). The
     * exceptions are for resolving names in deserialized types and in types
     * loaded from raw class files.
     */
    public Named find(String name) throws SemanticException {
        if (previous == null) {
            clearAdded();
        }

        Named n = super.find(name);

        if (previous == null) {
            if (Report.should_report(TOPICS, 2))
                Report.report(2, "Returning from root-level SR.find(" + name + "); added = " + justAdded);

          /*
            for (Iterator i = justAdded.iterator(); i.hasNext(); ) {
                Named n2 = (Named) i.next();
                if (n2 instanceof ParsedTypeObject) {
                    if (! ((ParsedTypeObject) n2).initializer().isTypeObjectInitialized()) {
                        throw new InternalCompilerError(n + " is in the root system resolver, but not initialized");
                    }
                }
            }
            */

            clearAdded();
        }
        else {
          if (Report.should_report(TOPICS, 2))
              Report.report(2, "Returning from non-root-level SR.find(" + name + "); added = " + justAdded);
        }

        return n;
    }

    public void install(String name, Named q) {
        if (Report.should_report(TOPICS, 2) && check(name) == null)
            Report.report(2, (previous == null ? "root" : "non-root") + " SR installing " + name + "->" + q);
        
        super.install(name, q);

        if (previous == null) {
            if (q instanceof ParsedTypeObject) {
                if (! ((ParsedTypeObject) q).initializer().isTypeObjectInitialized()) {
                    if (Report.should_report(TOPICS, 2))
                        Report.report(2, "SR initializing " + q);
                    ((ParsedTypeObject) q).initializer().initTypeObject();
                }
            }
        }
        else {
            justAdded.add(new Object[] { name, q });
        }
    }

    /**
     * Install a qualifier in the cache.
     * @param name The name of the qualifier to insert.
     * @param q The qualifier to insert.
     */
    public void addNamed(String name, Named q) throws SemanticException {
        super.addNamed(name, q);

        if (q instanceof ClassType) {
            ClassType ct = (ClassType) q;
            String containerName = StringUtil.getPackageComponent(name);
            if (ct.isTopLevel()) {
                Package p = ((ClassType) q).package_();
                cachePackage(p);
                if (p != null && containerName.equals(p.fullName())) {
                    addNamed(containerName, p);
                }
            }
            else if (ct.isMember()) {
                if (name.equals(ct.fullName())) {
                    // Check that the names match; we could be installing
                    // a member class under its class file name, not its Java
                    // source full name.
                    addNamed(containerName, ct.outer());
                }
            }
        }
        else if (q instanceof Package) {
            Package p = (Package) q;
            cachePackage(p);
            String containerName = StringUtil.getPackageComponent(name);
            if (p.prefix() != null && containerName.equals(p.prefix().fullName())) {
                addNamed(containerName, p.prefix());
            }
        }

        if (q instanceof Type && packageExists(name)) {
            throw new SemanticException("Type \"" + name +
                        "\" clashes with package of the same name.", q.position());
        }
    }

    private static final Collection TOPICS =
                    CollectionUtil.list(Report.types,
                                        Report.resolver,
                                        "sysresolver");
}
