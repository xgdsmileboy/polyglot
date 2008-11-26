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

package polyglot.frontend;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.*;

import polyglot.main.Options;
import polyglot.types.reflect.ClassFileLoader;
import polyglot.util.*;

/**
 * This is the main entry point for the compiler. It contains a work list that
 * contains entries for all classes that must be compiled (or otherwise worked
 * on).
 */
public class Compiler
{
    /** The extension info */
    private ExtensionInfo extensionInfo;

    /** A list of all extension infos active in this compiler. */
    private List allExtensions;

    /** The error queue handles outputting error messages. */
    private ErrorQueue eq;

    /**
     * Class file loader.  There should be only one of these so we can cache
     * across type systems.
     */
    private ClassFileLoader loader;

    /**
     * The output files generated by the compiler.  This is used to to call the
     * post-compiler (e.g., javac).
     */
    private Collection outputFiles = new HashSet();

    /**
     * Initialize the compiler.
     *
     * @param extensionInfo the <code>ExtensionInfo</code> this compiler is for.
     */
    public Compiler(ExtensionInfo extensionInfo) {    
        this(extensionInfo, new StdErrorQueue(System.err, 
                                              extensionInfo.getOptions().error_count,
                                              extensionInfo.compilerName()));
    }
        
    /**
     * Initialize the compiler.
     *
     * @param extensionInfo the <code>ExtensionInfo</code> this compiler is for.
     */
    public Compiler(ExtensionInfo extensionInfo, ErrorQueue eq) {
        this.extensionInfo = extensionInfo;
        this.eq = eq;
        this.allExtensions = new ArrayList(2);

        loader = new ClassFileLoader(extensionInfo);

        // This must be done last.
        extensionInfo.initCompiler(this);
    }

    /** Return a set of output filenames resulting from a compilation. */
    public Collection outputFiles() {
        return outputFiles;
    }

    /**
     * Compile all the files listed in the set of strings <code>source</code>.
     * Return true on success. The method <code>outputFiles</code> can be
     * used to obtain the output of the compilation.  This is the main entry
     * point for the compiler, called from main().
     */
    public boolean compileFiles(Collection filenames) {
        List sources = new ArrayList(filenames.size());

        // Construct a list of sources from the list of file names.
        try {
            try {
                SourceLoader source_loader = sourceExtension().sourceLoader();

                for (Iterator i = filenames.iterator(); i.hasNext(); ) {
                    String sourceName = (String) i.next();
                    // mark this source as being explicitly specified
                    // by the user.
                    FileSource source = source_loader.fileSource(sourceName, true);

                    sources.add(source);
                }
            }
            catch (FileNotFoundException e) {
                eq.enqueue(ErrorInfo.IO_ERROR,
                    "Cannot find source file \"" + e.getMessage() + "\".");
                eq.flush();
                return false;
	    }
	    catch (IOException e) {
		eq.enqueue(ErrorInfo.IO_ERROR, e.getMessage());
                eq.flush();
                return false;
	    }
	    catch (InternalCompilerError e) {
                // Report it like other errors, but rethrow to get the stack
                // trace.
		try {
                    eq.enqueue(ErrorInfo.INTERNAL_ERROR, e.message(),
                               e.position());
		}
		catch (ErrorLimitError e2) {
		}

		eq.flush();
		throw e;
	    }
	    catch (RuntimeException e) {
		// Flush the error queue, then rethrow to get the stack trace.
		eq.flush();
		throw e;
	    }
        }
	catch (ErrorLimitError e) {
            eq.flush();
            return false;
	}

        return compile(sources);
    }

    /**
     * Compile all the files listed in the set of Sources <code>source</code>.
     * Return true on success. The method <code>outputFiles</code> can be
     * used to obtain the output of the compilation.  This is the main entry
     * point for the compiler, called from main().
     */
    public boolean compile(Collection sources) {
	boolean okay = false;
    
	try {
	    try {
                Scheduler scheduler = sourceExtension().scheduler();
                List jobs = new ArrayList();

                // First, create a goal to compile every source file.
                for (Iterator i = sources.iterator(); i.hasNext(); ) {
                    Source source = (Source) i.next();
                    
                    // Add a new SourceJob for the given source. If a Job for the source
                    // already exists, then we will be given the existing job.
                    Job job = scheduler.addJob(source);
                    jobs.add(job);
                    
                    // Now, add a goal for completing the job.
                    scheduler.addGoal(sourceExtension().getCompileGoal(job));
                }

                scheduler.setCommandLineJobs(jobs);
                
                // Then, compile the files to completion.
                okay = scheduler.runToCompletion();
	    }
	    catch (InternalCompilerError e) {
		// Report it like other errors, but rethrow to get the stack trace.
		try {
		    eq.enqueue(ErrorInfo.INTERNAL_ERROR, e.message(), e.position());
		}
		catch (ErrorLimitError e2) {
		}
		eq.flush();
		throw e;
	    }
	    catch (RuntimeException e) {
		// Flush the error queue, then rethrow to get the stack trace.
		eq.flush();
		throw e;
	    }
	}
	catch (ErrorLimitError e) {
	}

	eq.flush();

        for (Iterator i = allExtensions.iterator(); i.hasNext(); ) {
            ExtensionInfo ext = (ExtensionInfo) i.next();
            ext.getStats().report();
        }

	return okay;
    }

    /** Get the compiler's class file loader. */
    public ClassFileLoader loader() {
        return this.loader;
    }

    /** Should fully qualified class names be used in the output? */
    public boolean useFullyQualifiedNames() {
        return extensionInfo.getOptions().fully_qualified_names;
    }

    /** Return a list of all languages extensions active in the compiler. */
    public void addExtension(ExtensionInfo ext) {
        allExtensions.add(ext);
    }

    /** Return a list of all languages extensions active in the compiler. */
    public List allExtensions() {
        return allExtensions;
    }

    /** Get information about the language extension being compiled. */
    public ExtensionInfo sourceExtension() {
	return extensionInfo;
    }

    /** Maximum number of characters on each line of output */
    public int outputWidth() {
        return extensionInfo.getOptions().output_width;
    }

    /** Should class info be serialized into the output? */
    public boolean serializeClassInfo() {
	return extensionInfo.getOptions().serialize_type_info;
    }

    /** Get the compiler's error queue. */
    public ErrorQueue errorQueue() {
	return eq;
    }

    static {
      // FIXME: if we get an io error (due to too many files open, for example)
      // it will throw an exception. but, we won't be able to do anything with
      // it since the exception handlers will want to load
      // polyglot.util.CodeWriter and polyglot.util.ErrorInfo to print and
      // enqueue the error; but the classes must be in memory since the io
      // can't open any files; thus, we force the classloader to load the class
      // file.
      try {
	ClassLoader loader = Compiler.class.getClassLoader();
	// loader.loadClass("polyglot.util.CodeWriter");
	// loader.loadClass("polyglot.util.ErrorInfo");
	loader.loadClass("polyglot.util.StdErrorQueue");
      }
      catch (ClassNotFoundException e) {
	throw new InternalCompilerError(e.getMessage());
      }
    }
    
    public static CodeWriter createCodeWriter(OutputStream w) {
        return createCodeWriter(w, Options.global.output_width);
    }
    public static CodeWriter createCodeWriter(OutputStream w, int width) {
        if (Options.global.use_simple_code_writer)
            return new SimpleCodeWriter(w, width);
        else
	    return new OptimalCodeWriter(w, width);
    }
    public static CodeWriter createCodeWriter(Writer w) {
        return createCodeWriter(w, Options.global.output_width);
    }
    public static CodeWriter createCodeWriter(Writer w, int width) {
        if (Options.global.use_simple_code_writer)
            return new SimpleCodeWriter(w, width);
        else
            return new OptimalCodeWriter(w, width);
    }
}
