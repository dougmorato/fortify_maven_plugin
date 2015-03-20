package com.fortify.ps.maven.plugin.sca;


import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Resource;
//import org.apache.maven.plugin.PluginManager;		// For Maven 2.0 and 2.2
import org.apache.maven.plugin.BuildPluginManager;	// For Maven 3.0
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.execution.MavenSession;
import org.twdata.maven.mojoexecutor.MojoExecutor;
import org.twdata.maven.mojoexecutor.MojoExecutor.ExecutionEnvironment;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Translate your application to a particular build ID.
 *
 * @goal translate
 * @phase install
 * @requiresProject
 * @requiresDependencyResolution test
 */
public class TranslateMojo extends AbstractSCAMojo {

	/**
	 * Location of the exploded war directory. Only necessary for packaging of
	 * type war and then only if the default location of the exploded war
	 * directory has been overridden by the webappDirectory war plugin
	 * configuration parameter.
	 *
	 * @parameter expression="${project.war.output.directory}" default-value="${project.build.directory}/${project.build.finalName}"
	 */
    private File warOutputDirectory;

	/**
	 * The list of directories which contain source code for the project. List of
	 * source roots containing non-test code.
	 *
	 * @parameter
	 * @readonly
	 */
    private List<String> compileSourceRoots;

	/**
	 * The list of directories that contain test source code for the project.
	 *
	 * @parameter default-value="${project.testCompileSourceRoots}"
	 * @readonly
	 */
    private List<String> testCompileSourceRoots;

	/**
	 * @parameter default-value="${project.compileArtifacts}"
	 * @readonly
	 */
    private List<Artifact> compileArtifacts;

	/**
	 * @parameter default-value="${project.testArtifacts}"
	 * @readonly
	 */
    private List<Artifact> testArtifacts;

	/**
	 * @parameter default-value="${project.resources}"
	 * @readonly
	 */
    private List<Resource> resources;

	/**
	 * @parameter default-value="${project.testResources}"
	 * @readonly
	 */
    private List<Resource> testResources;

	/**
	 * The directory containing generated classes.
	 *
	 * @parameter expression="${project.build.outputDirectory}"
	 * @required
	 * @readonly
	 */
    private File classesDirectory;

	/**
	 * The directory containing generated classes.
	 *
	 * @parameter expression="${project.build.testOutputDirectory}"
	 * @required
	 * @readonly
	 */
    private File testClassesDirectory;

	/**
	 * If set to true tests are also included in the analysis.  It defaults to
	 * the maven.test.skip value or can be set independently for sca.
	 *
	 * @parameter expression="${fortify.sca.tests.exclude}" default-value="true"
	 */
    private boolean skipTests;

	/**
	 * Specifies the application server for processing JSP files: weblogic or
	 * websphere.
	 *
	 * @parameter expression="${fortify.sca.appserver}"
	 */
    private String appserver;

	/**
	 * Specifies the application server's home. For Weblogic, this is the path to
	 * the directory containing the server/lib directory. For WebSphere, this is
	 * the path to the directory containing the bin/JspBatchCompiler script.
	 *
	 * @parameter expression="${fortify.sca.appserverHome}"
	 */
    private String appserverHome;

	/**
	 * Specifies the version of the application server. For Weblogic, valid
	 * values are 7, 8, 9, and 10. For WebSphere, the valid value is 6.
	 *
	 * @parameter expression="${fortify.sca.appserverVersion}"
	 */
    private String appserverVersion;

	/**
	 * Specifies a string to be added to the end of -cp sca command-line option.
	 * This is in addition to dependencies worked out from the pom.  This
	 * should be a correctly formed and separated list of paths for the
	 * underlying system.  No processing is done on this string other than
	 * concatenation.  Potentially useful for JSP compilation.
	 *
	 * @parameter expression="${fortify.sca.cp}"
	 */
    private String extraClassPathString;

	/**
	 * Specifies a string to be passed to sca via the -Dcom.fortify.sca.DefaultJarsDirs
	 * command-line property definition.  This string is expected to be a properly formatted
	 * OS dependent list of paths relative to the ScA install Core directory.  No processing
	 * is done on this string.  Potentially useful for JSP compilation.
	 *
	 * @parameter expression="${fortify.sca.defaultJars}"
	 */
	private String defaultJarsPathString;

	/**
	 * Specifies the source file encoding type. This option is the same as the
	 * javac encoding option.
	 *
	 * @parameter expression="${fortify.sca.encoding}"
	 */
	private String encoding;

	/**
	 * Indicates which version of the JDK the Java code is written for.
	 * Valid values for version are 1.3, 1.4, 1.5, 1.6. and 1.7
	 *
	 * @parameter expression="${fortify.sca.source.version}" default-value="1.6"
	 */
	private String source;


	/**
	 * prefix of arguments file for running sourceanalyzer
	 *
	 * @parameter default-value="${project.build.directory}/sca-translate-"
	 * @required
	 * @readonly
	 */
	private String scaArgsFileNamePrefix;


	/**
	 * Specifies the log file that is produced by Fortify SCA.
	 *
	 * @parameter expression="${fortify.sca.logfile}" default-value="${project.build.directory}/sca-translate.log"
	 */
	protected String logfile;

	/**
	 * If set to false the plugin will try to continue past individual SCA translate invocation failures.  Translate invocations
	 * may fail legitimately.  For example if there are no files in src/main/java for a jar project.  Set this to true during your
	 * verification step but false during your normal runs.
	 *
	 * @parameter expression="${fortify.sca.translate.failOnError}" default-value="false"
	 */
	protected boolean failOnSCAError ;

	/**
	 *  Directory containing source files to be used for name resolution. These files are not to be analyzed.
	 *  @parameter expression=${fortify.sca.translate.sourcePath}
	 */
	protected File sourcePath;

    /**
     * Specifies the file path to exclude from translation..
     *
     * @parameter expression="${fortify.sca.exclude}"
     */
    protected String exclude;

    /**
     * Project artifacts.
     *
     * @parameter default-value="${project.artifact}"
     * @required
     * @readonly
     * @todo this is an export variable, really
     */
    private Artifact projectArtifact;
	
    /**
     * The current build session instance. This is used for
     * toolchain manager API calls.
     *
     * @parameter default-value="${session}"
     * @required
     * @readonly
     */
    private MavenSession session;

	/**
	 * The Maven BuildPluginManager component.
	 *
	 * @component
	 * @required
	 */
//	private PluginManager pluginManager;		// For Maven 2.0 and 2.2
    private BuildPluginManager pluginManager;	// For Maven 3.0

	/**
	 * @parameter expression="${project.build.plugins}"
	 */
	private List<Plugin> plugins;

	/**
	 * Performs the translation step of an SCA analysis.
	 *
	 * @throws MojoExecutionException
	 * @throws MojoFailureException
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (compileSourceRoots == null) {
			compileSourceRoots = project.getCompileSourceRoots();
		}
		if (!scanEnabled) {
			getLog().info("Scan disabled for this project. Skipping.");
			return;
		}

		init();
        compileJavaSource();
		identifyJavaSource();

		if (packaging.equals("pom")) {
		    getLog().info("Packaging is POM. Skipping since its modules will be translated.");
		} else if (packaging.equals("jar") || packaging.equals("war") || packaging.equals("ear") || packaging.equals("ejb") || packaging.equals("maven-plugin") || packaging.equals("bundle")){
			logBasicInfo();
			translateJavaSources(); // Always translate the Java sources in a project.
			if (packaging.equals("war")) {
				translateWar();
			} else if (packaging.equals("ear"))  {
				translateEar();
			}
		} else {
			getLog().info("******* The SCA plug-in does not support the packaging value " + packaging + " ********");
		}
		projectArtifact.setFile( classesDirectory );
	}
	
	private void compileJavaSource() throws MojoExecutionException {
		
		if (!classesDirectory.exists() || !FileHelper.containsClassFiles(classesDirectory)) {
			Plugin compilerPlugin = null;
			
			for( Plugin plug : plugins) {
				if (plug.getGroupId().equals("org.apache.maven.plugins") && plug.getArtifactId().equals("maven-compiler-plugin")) {
					compilerPlugin = plug;
					break;
				}
			}
			if (compilerPlugin == null) {
				compilerPlugin = MojoExecutor.plugin(MojoExecutor.groupId("org.apache.maven.plugins"), MojoExecutor.artifactId("maven-compiler-plugin"), MojoExecutor.version("3.1"));
				plugins.add(compilerPlugin);
			}
			
			Xpp3Dom config = (Xpp3Dom)compilerPlugin.getConfiguration();
			
			if (config == null) {
				config = MojoExecutor.configuration(
					MojoExecutor.element(MojoExecutor.name("source"), source),
					MojoExecutor.element(MojoExecutor.name("target"), source)
				);
			}
			MojoExecutor.ExecutionEnvironment environment = MojoExecutor.executionEnvironment(project, session, pluginManager);
			MojoExecutor.executeMojo(compilerPlugin, MojoExecutor.goal("compile"), config, environment);
		}
	}

	protected void translateJavaSources() throws MojoExecutionException, MojoFailureException {
		constructCommandLineArgs();
		addClassPath();
		addJavaBuildDir();
		boolean addedSourceDirs = addSourceDirs();
		boolean addedResources = addResources();
		if (addedSourceDirs || addedResources) {
			writeArgFile(scaArgsFileNamePrefix + "java.txt");
			invoke(scaArgsFileNamePrefix + "java.txt", failOnSCAError);
		} else {
			getLog().info("The above source directories do not exist.");
		}
	}

	protected void translateWar() throws MojoExecutionException, MojoFailureException {
		generateArgsForWar();
		writeArgFile(scaArgsFileNamePrefix + "war.txt") ;
		invoke(scaArgsFileNamePrefix + "war.txt", failOnSCAError) ;
	}

	private String getWarSourceRoot() {
		Plugin warPlugIn = util.getWarPlugin();
		Util.SafeConfig config = new Util.SafeConfig(warPlugIn);
		String defaultLoc = baseDir + File.separator + "src" + File.separator + "main" + File.separator + "webapp";
		String retVal = config.getElemValue("warSourceDirectory", defaultLoc);
		if (new File(retVal).isAbsolute()) {
			return retVal;
		}
		return new File(baseDir, retVal).getAbsolutePath();
	}

	/**
	 * Generate SCA arguments to translate the WAR represented by this project. The class path automatically includes
	 * WEB-INF/classes and the JARs inside WEB-INF/lib. If class path information was stored by a prior ear goal
	 * invocation, then this too is added. Additionally, the user may supply additional class path entries using the
	 * fortify.sca.cp command line property or extraClassPathString POM property. 
	 * 
	 * @throws MojoExecutionException
	 */
	protected void generateArgsForWar() throws MojoExecutionException {
		constructCommandLineArgs();

		boolean useSourceDirectory = false;
		String warTargetRoot = warOutputDirectory.getAbsolutePath();
		String warSourceRoot = getWarSourceRoot();

		if (buildId != null) {
			// If generated Java sources are available, use them.

			// SYNTAX
			// sourceanalyzer -b bid -document-root main/webapp -generated-sources /path/to/generated/sources
			//                -cp ...

			String generatedSourcesDir = util.showModule(buildId, project.getGroupId(), project.getArtifactId());
			if (StringHelper.isDefinedNonTrivially(generatedSourcesDir)) {
				getLog().info("Using available generated Java sources at " + generatedSourcesDir);
				addArg("-generated-sources");
				addArg(generatedSourcesDir);
				addArg("-document-root");
				addArg(warSourceRoot);
				useSourceDirectory = true;
			}
		}

		if (!useSourceDirectory) {
			if (warOutputDirectory.exists()) {
				getLog().info("Generated sources not found. Using exploded WAR location at " + warOutputDirectory);
				addArg(warTargetRoot);
			} else {
				getLog().info("Target folder not found. Using source files at " + warSourceRoot);
				addArg(warSourceRoot);
			}
		}

		// When building the class path, use the Web app root in the target directory rather than the one in the source
		// directory itself. Sometimes, needed class path entries do not appear until after compilation, the output of
		// which appears in the target directory.

		// -cp <saved class path>;WEB-INF/classes;<extra class path>
		String warCP = warTargetRoot + File.separator + "WEB-INF" + File.separator + "classes";
		String combinedCP = StringHelper.concatenateClassPaths(warCP, extraClassPathString); // user-provided
		combinedCP = StringHelper.concatenateClassPaths(util.showClassPath(buildId), combinedCP); // stored by ear goal
		addOptionValuePair("-cp", combinedCP);

		// -extdirs WEB-INF/lib
		addArg("-extdirs") ;
		addArg(warTargetRoot + File.separator + "WEB-INF" + File.separator + "lib");
	}

	/**
	 * Translate the resources of the EAR module.
	 * 
	 * @throws MojoExecutionException
	 * @throws MojoFailureException
	 */
	protected void translateEar() throws MojoExecutionException, MojoFailureException {
		Plugin earPlugIn = util.getPlugin("org.apache.maven.plugins", "maven-ear-plugin");
		Util.SafeConfig config = new Util.SafeConfig(earPlugIn);

		// This directory contains resources that may be of interest to SCA.
		String defaultLoc = new File(baseDir + File.separator + "src" + File.separator + "main" + File.separator + "application").getAbsolutePath();
		String earSourceDirectory = config.getElemValue("earSourceDirectory", defaultLoc);
		File earSourceDirectoryFile = new File(earSourceDirectory);

		if (!earSourceDirectoryFile.exists()) {
			getLog().info("This EAR module does not contain any resources.");
			return;
		}

		constructCommandLineArgs();
		if (earSourceDirectoryFile.isAbsolute()) {
			addArg(earSourceDirectory);
		} else {
			addArg(new File(baseDir, earSourceDirectory).getAbsolutePath());
		}

		writeArgFile(scaArgsFileNamePrefix + "ear.txt") ;
		invoke(scaArgsFileNamePrefix + "ear.txt", failOnSCAError) ;
	}

	protected void logBasicInfo() {
		// In the future, queue up the properties that need to be logged. And format the output automatically.

		getLog().info("                    Build ID -> " + buildId);
		getLog().info("  Location of SCA Executable -> " + sourceanalyzer);
		getLog().info("             Translation Log -> " + logfile);
		getLog().info("               Fail on Error -> " + failOnSCAError );
		getLog().info("                   Packaging -> " + packaging);
	}

	protected void constructCommandLineArgs() throws MojoExecutionException {
		super.constructCommandLineArgs() ;

		addOptionValuePair("-logfile", logfile);
		addOptionValuePair("-encoding", encoding);
		addOptionValuePair("-source", source);
        addOptionValuePair("-exclude", exclude);

		// Should these be added for both Java and JSP translation?
		addOptionValuePair("-appserver", appserver);
		addOptionValuePair("-appserver-home", appserverHome);
		addOptionValuePair("-appserver-version", appserverVersion);
		addOptionValuePair("-sourcepath", sourcePath);

		if ( defaultJarsPathString != null && !defaultJarsPathString.trim().equals("")) {
			addArg( "-Dcom.fortify.sca.DefaultJarsDirs=" + defaultJarsPathString.trim() );
		}
	}

	/**
	 * Add the inferred class-path and any extra class-path the user may have specified.
	 */
	protected void addClassPath() {
		String classpath = util.resolveClassPath(skipTests ? compileArtifacts : testArtifacts ) ;
		String combinedCP = StringHelper.concatenateClassPaths(classpath, extraClassPathString) ;
		getLog().info ("                  Class Path -> " + (classpath == null? "" : classpath));
		getLog().info ("   User-Specified Class Path -> " + (extraClassPathString == null? "" : extraClassPathString ) ) ;
		if ( combinedCP !=  null ) {
			addArg("-cp") ;
			addArg(combinedCP);
		}
	}

	/**
	 * This is needed when using FindBugs.
	 */
	protected void addJavaBuildDir() {
		getLog().info ("           Classes Directory -> " + classesDirectory);
		getLog().info ("      Test Classes Directory -> " + testClassesDirectory);
		addArg("-java-build-dir") ;
		addArg (classesDirectory.getAbsolutePath());
		if (! skipTests) {
			addArg("-java-build-dir") ;
			addArg (testClassesDirectory.getAbsolutePath());
		}
	}

	protected boolean addSourceDirs() {
		getLog().info ("              Source Version -> " + (source == null ? "SCA default" : source) );
		getLog().info ("           Source File Paths -> " + StringHelper.listToString(compileSourceRoots, " "));
		getLog().info ("      Test Source File Paths -> " + StringHelper.listToString(testCompileSourceRoots, " "));
        getLog().info ("         Excluded File Paths -> " + exclude);

		boolean retVal = false;
		Iterator<String> it = compileSourceRoots.iterator();
		while (it.hasNext()) {
			String currSourceRoot = it.next();
			if (new File(currSourceRoot).isDirectory()) {
				addArg(currSourceRoot);
				retVal = true;
			}
		}
		if (!skipTests) {
			it = testCompileSourceRoots.iterator();
			while (it.hasNext()) {
				String currRoot = it.next();
				if (new File(currRoot).isDirectory()) {
					addArg(it.next());
					retVal = true;
				}
			}
		}
		return retVal;
	}

	/**
	 * Add the resources to the SCA build. Two important resources that need to be added are ejb-jar.xml and
	 * hibernate.properties. Otherwise, it is usually worth adding resources since they may contain issues such as
	 * Hardcoded Passwords.
	 */
	protected boolean addResources() {
		List<String> resourceStrList = getResourcesAsStrings(resources);
		getLog().info ("                   Resources -> " + StringHelper.listToString(resourceStrList, " "));
		Iterator<String> resIter = resourceStrList.iterator();
		while (resIter.hasNext()) {
			String currRes = resIter.next();
			if (new File(currRes).exists()) {
				addArg(currRes);
			}
		}
		List<String> testResourceStrList = null;
		if (!skipTests) {
			testResourceStrList = getResourcesAsStrings(testResources);
			getLog().info ("              Test Resources -> " + StringHelper.listToString(testResourceStrList, " "));
			resIter = testResourceStrList.iterator();
			while (resIter.hasNext()) {
				String currRes = resIter.next();
				if (new File(currRes).exists()) {
					addArg(currRes);
				}
			}
		}
		return resourceStrList.size() > 0 || (testResourceStrList != null ? testResourceStrList.size() > 0 : false);
	}
	
	/**
	 * Turns a List&lt;Resource&gt; to a List&lt;String&gt;. The return value is useful for feeding to listToString().
	 * 
	 * @param resources List to turn into a list of strings.
	 * @return A list of String objects.
	 * @see <a href="http://maven.apache.org/pom.html#Resources">Maven Resources</a>
	 */
	private List<String> getResourcesAsStrings(List<Resource> resources) {
		/*
			- resources:  is a list of resource elements that each describe what and where to include files associated with this project.
			- targetPath: Specifies the directory structure to place the set of resources from a build. Target path defaults to the base directory.
			              A commonly specified target path for resources that will be packaged in a JAR is META-INF.
			- filtering:  is true or false, denoting if filtering is to be enabled for this resource.
			- directory:  This element's value defines where the resources are to be found. The default directory for a build is ${basedir}/src/main/resources.
			- includes:   A set of files patterns which specify the files to include as resources under that specified directory, using * as a wildcard.
			- excludes:   The same structure as includes, but specifies which files to ignore. In conflicts between include and exclude, exclude wins.
		*/
		List<String> retVal = new ArrayList<String>();
		
		for( Resource rcs : resources) {
			if (rcs.isFiltering() == false ) {
				String dir = rcs.getDirectory();
				
				if( FileHelper.isDirectory(dir) ) {
					List<String> includes = rcs.getIncludes();
					List<String> excludes = rcs.getExcludes();
					if ( includes.size() == 0 && excludes.size() == 0 ) {
						retVal.add(dir);
					} else {
						List<String> filePaths = FileHelper.getAllFilesInDirectory(dir);
						if (includes.size() == 0 ) {
							retVal.addAll(filePaths);
						} else {
							for(String filePath : filePaths) {
								for( String include : includes) {
									File file = new File(filePath);
									File includePattern = new File(dir + File.separator + include);
									if ( matches(file, includePattern) ) {
										retVal.add(filePath);
									}
								}
							}
						}
						
						for(String filePath : filePaths) {
							for( String exclude : excludes) {
								File file = new File(filePath);
								File excludePattern = new File(dir + File.separator + exclude);
								if ( matches(file, excludePattern) ) {
									retVal.remove(filePath);
								}
							}
						}
					}
				}
			}
		}
		return retVal;
	}
	
	private boolean matches(File file, File pattern)
	{
		String filePath = StringHelper.convertFileSeparator(file.getAbsolutePath());
		String filePattern = StringHelper.convertFileSeparator(pattern.getAbsolutePath());
		filePattern = StringHelper.createRegexFromFilePattern(filePattern);
		
		Pattern p = Pattern.compile(filePattern);
		Matcher m = p.matcher(filePath);
		
		return m.matches();
	}

	private void identifyJavaSource(){
		Plugin plugin = util.getPlugin("org.apache.maven.plugins", "maven-compiler-plugin");
		Util.SafeConfig config = new Util.SafeConfig(plugin);
		String javacSource = config.getElemValue("source");
		
		if( source == null ) {
			if( javacSource != null ) {
				getLog().info("Translating java source as " + javacSource + ".");
				source = javacSource;
			}
			else {
				getLog().info("Translating java source as SCA default.");
			}
		} else {
			getLog().info("Translating java source as " + source + ".");
			if( javacSource != null && !source.equals(javacSource)) {
				getLog().warn("Specified source options are inconsistent.");
				getLog().warn("source for sourceanalyzer is " + source + "," + " but source for javac is " + javacSource + ".");
			}
		}
	}
}
