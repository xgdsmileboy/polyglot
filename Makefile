#
# Makefile to build the jltools source to source compiler
# includes a makefile in each package to handle building of respective 
# packages
#

SOURCE = .
SUBDIRS = jltools

include Rules.mk


all: classes
	$(subdirs)

clean:
	rm -rf classes
	$(subdirs)

classes:
	mkdir classes

bin:
	mkdir bin

clobber:
	-rm -rf $(JAVADOC_OUTPUT)
	-rm -f $(JAR_FILE)
	$(subdirs)

norecurse: classes jif polyj split op jmatch
	$(JC) $(JC_FLAGS) jltools/main/Main.java

jif:
	$(JC) $(JC_FLAGS) jltools/ext/jif/ExtensionInfo.java
polyj:
	$(JC) $(JC_FLAGS) jltools/ext/polyj/ExtensionInfo.java
op:
	$(JC) $(JC_FLAGS) jltools/ext/op/ExtensionInfo.java
split:
	$(JC) $(JC_FLAGS) jltools/ext/split/ExtensionInfo.java
jmatch:
	$(JC) $(JC_FLAGS) jltools/ext/jmatch/ExtensionInfo.java


PACKAGES = \
	jltools.ast \
	jltools.frontend \
	jltools.lex \
	jltools.main \
	jltools.types \
	jltools.util \
	jltools.visit \
	jltools.ext.jl.ast \
	jltools.ext.jl.parse \
	jltools.ext.jl.types \
	jltools.ext.op \
	jltools.ext.op.runtime \
	jltools.ext.polyj \
	jltools.ext.polyj.ast \
	jltools.ext.polyj.extension \
	jltools.ext.polyj.types \
	jltools.ext.polyj.visit \
	jltools.ext.jif \
	jltools.ext.jif.ast \
	jltools.ext.jif.extension \
	jltools.ext.jif.types \
	jltools.ext.jif.visit \
	jltools.ext.jmatch \
	jltools.ext.jmatch.ast \
	jltools.ext.jmatch.extension \
	jltools.ext.jmatch.ir \
	jltools.ext.jmatch.types \
	jltools.ext.jmatch.visit \
	jltools.ext.split \
	jltools.ext.split.ast \
	jltools.ext.split.dataflow \
	jltools.ext.split.extension \
	jltools.ext.split.types \
	jltools.ext.split.visit

javadoc: FORCE
	$(javadoc)

jar: all
	cd classes ; \
	$(JAR) $(JAR_FLAGS) ../$(JAR_FILE) `find jltools -name \*.class`; \
	$(JAR) $(JAR_FLAGS) ../jif.jar `find jif -name \*.class`

REL_SOURCES = \
	Rules.mk \
	configure \
	README \
	java_cup.jar \
	jlex.jar \
	iDoclet.jar \

REL_LIBS = \
	jltools.jar \
	java_cup.jar \
	jif.jar \

release_clean: FORCE
	mkdir -p $(RELPATH)
	rm -rf $(RELPATH)/*

release_doc: FORCE
	cp LICENSE README README-JIF.txt $(RELPATH)
	mkdir -p $(REL_DOC)
	mkdir -p $(REL_SRC)
	cp -f BUILD.txt $(REL_SRC)/README
	$(MAKE) -C doc release

release_demo: FORCE
	mkdir -p $(REL_DEMO)

release: jar release_clean release_doc release_src
	cp -f configure.rel $(RELPATH)/configure
	$(subdirs)
	mkdir -p $(REL_LIB)
	cp $(REL_LIBS) $(REL_LIB)
	rm jltools.jar jif.jar
	
#cd $(RELPATH); zip -r jltools .

FORCE:
