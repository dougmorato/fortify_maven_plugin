package com.fortify.ps.maven.plugin.sca;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.DefaultConsumer;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Super class of SCA Mojos. Captures common sca parameters and a method to
 * construct these options in to a command-line.
 * 
 * @author kaveh
 */
public abstract class AbstractSCAMojo extends AbstractMojo {

	/**
	 * MavenProject object
	 * 
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	protected MavenProject project;

	/**
	 * Collect commands to be run, but do not actually run them.
	 * 
	 * @parameter expression="${com.fortify.dev.maven.doNotRunSCA}"
	 */
	protected boolean dontRunSCA;

	/**
	 * Packaging - currenty only supporting jar or war.
	 * 
	 * @parameter expression="${project.packaging}"
	 * @readonly
	 * @required
	 */
	protected String packaging;

	/**
	 * @parameter expression="${project.build.directory}"
	 */
	protected String projectBuildDir;

	/**
	 * The set of dependencies required by the project
	 * 
	 * @parameter default-value="${project.dependencies}"
	 * @required
	 * @readonly protected List dependencies;
	 */
	protected List dependencies;

	/**
	 * location of the sourceanalyzer executable. Defaults to sourceanalyzer
	 * which will run the version on the path or fail if non exists.
	 * 
	 * @parameter expression="${fortify.sca.sourceanalyzer.executable}"
	 *            default-value="sourceanalyzer"
	 * @required
	 */
	protected String sourceanalyzer;
	
	/**
	 * Specifies the maximum heap size of JVM which runs Fortify SCA.
	 * The default value is 600 MB (-Xmx600M), which can be insufficient for
	 * large code bases. When specifying this option, ensure that you do not
	 * allocate more memory than is physically available, because this degrades
	 * performance. As a guideline, assuming no other memory intensive processes
	 * are running, do not allocate more than 2/3 of the available memory.
	 * 
	 * @parameter expression="${fortify.sca.Xmx}"
	 */
	protected String maxHeap;
	
	/**
	 * Specifies the initial and minimum heap size of JVM which runs Fortify SCA.
	 * The default value is 300MB (-Xms300M), which can be sufficient in most of cases.
	 * 
	 * @parameter expression="${fortify.sca.Xms}"
	 */
	protected String minHeap;

	/**
	 * Specifies the maximum permanent space size of JVM which runs Fortify SCA.
	 * The permanent space is the third part of Java memory, where are stored classes, methods etc.
	 * The default maximum value for the permanent generation is 64 MB (-XX:MaxPermSize=64M). 
	 * The permanent space is allocated as a separate memory region from the java heap, so increasing
	 * the permanent space will increase the overall memory requirements for the process. 
	 * 
	 * @parameter expression="${fortify.sca.PermGen}"
	 */
	protected String maxPermGen;

	/**
	 * Specifies the thread stack size of JVM runs Fortify SCA.
	 * Thread stacks are memory areas allocated for each Java thread for their internal use.
	 * The defaul value is 1MB (-Xss1M), which can be insufficient for large code bases. 
	 * When encountering java.lang.StackOverflowError, increase the value gradually.
	 * For example, -Xss4M, -Xss8M.
	 * 
	 * @parameter expression="${fortify.sca.Xss}"
	 */
	protected String stackSize;
	
	/**
	 * If true sourceanalyzer will run in debug mode which is useful during
	 * troubleshooting
	 * 
	 * @parameter expression="${fortify.sca.debug}" default-value="false"
	 */
	protected boolean debug = false;

	/**
	 * If true sourceanalyzer will sends verbose status messages to the console.
	 * 
	 * @parameter expression="${fortify.sca.verbose}" default-value="false"
	 */
	protected boolean verbose;

	/**
	 * If true sourceanalyzer will sends verbose status messages to the console.
	 * 
	 * @parameter expression="${fortify.sca.machineOutput}"
	 *            default-value="false"
	 */
	protected boolean machineOutput;

	/**
	 * If true sourceanalyzer will write minimal messages to the console
	 * 
	 * @parameter expression="${fortify.sca.quiet}" default-value="true"
	 */
	protected boolean quiet;

	/**
	 * If true sourceanalyzer will print its version
	 * 
	 * @parameter expression="${fortify.sca.version}" default-value="true"
	 */
	protected boolean version;

	/**
	 * @parameter expression="${basedir}"
	 */
	protected String baseDir;

	/**
	 * build Id - will default to project name and version. If you are running
	 * an aggregate build use the -Dfortify.sca.buildId commandline
	 * configuration option instead to set the buildId for all modules.
	 * 
	 * @parameter expression="${fortify.sca.buildId}"
	 *            default-value="${project.artifactId}-${project.version}"
	 */
	protected String buildId;

	/**
	 * Runs Fortify SCA inside the 64-bit JRE. If no 64-bit JRE is available,
	 * Fortify SCA fails.
	 * 
	 * @parameter expression="${fortify.sca.64bit}" default-value="false"
	 */
	protected boolean jre64;

	/**
	 * The location of a .properties file, the contents of which are
	 * property-value pairs that are to be passed to sourceanalyzer in the form
	 * of -Dproperty=value, e.g.
	 * -Dcom.fortify.sca.DefaultJarsDirs=default_jar:../mylibs and
	 * -Dcom.fortify.sca.limiters.MaxChainDepth=10. Said file should be a
	 * standard Java properties file; refer to the Java documentation for more
	 * info on what this entails. Note that properties set in this manner will
	 * often take preference over Maven plug-in configuration properties such as
	 * defaultJarsPathString.
	 * 
	 * @parameter expression="${fortify.sca.properties.file}"
	 */
	private File scaPropertiesFile;

	/**
	 * Set scanEnabled to false for any project that should not be scanned.
	 * 
	 * @parameter default-value="true"
	 */
	protected boolean scanEnabled;

	/**
	 * SCA options.
	 */
	private StringBuilder argFileStrBuf = null;

	private StringBuilder commandStrBuf = null;
	
	protected Util util;

	/**
	 * Adds an individual argument to the command line.
	 * 
	 * @param arg
	 *            Argument to add to the command line.
	 */
	protected void addArg(String arg) {
		StringBuilder buf = Util.isLauncherArgument(arg) ? commandStrBuf : argFileStrBuf;
		//The -scan argument should always go first, to accomodate CloudScan
		if("-scan".equals(arg)){
			buf.insert(0, arg + " ");
		}else{
			buf.append(" " + "\"" + arg + "\"");
		}
	}

	/**
	 * Add the option to the command if the condition is true. It is intended
	 * that the condition be related to the plug-in's configuration.
	 * 
	 * @param shouldAdd
	 * @param option
	 */
	protected void toggleSwitch(boolean shouldAdd, String option) {
		if (shouldAdd) {
			addArg(option);
		}
	}

	// Add an option-value pair to the command.
	protected void addOptionValuePair(String option, String value) {
		if (StringHelper.isDefinedNonTrivially(option) && StringHelper.isDefinedNonTrivially(value)) {
			addArg(option);
			addArg(value);
		}
	}

	protected void addOptionValuePair(String option, File value) {
		if (value == null || !value.exists()) {
			return; // If a File-value doesn't exist, Maven injects null into the field corresponding to the parameter.
		}
		addOptionValuePair(option, value.getAbsolutePath());
	}

	// Add each property to the command line as -Dproperty=value.
	protected void addProperties(Properties props) {
		Enumeration keys = props.keys();
		while (keys.hasMoreElements()) {
			String currKey = (String) keys.nextElement();
			String option = "-D" + currKey + "=" + props.getProperty(currKey);
			addArg(option);
		}
	}

	// Read in a .properties file and add each constituent property-value pair
	// to the command line in the form of
	// -Dproperty=value.
	protected void processPropertiesFile() throws MojoExecutionException {
		if (scaPropertiesFile == null) {
			return;
		}

		if (!scaPropertiesFile.exists()) {
			throw new MojoExecutionException("Properties file " + scaPropertiesFile.getAbsolutePath() + " does not exist");
		}else if (scaPropertiesFile.isDirectory()) {
			throw new MojoExecutionException("Properties file " + scaPropertiesFile.getAbsolutePath() + " is a directory.");
		}

		FileInputStream fis = null;

		try {
			Properties scaRunTimeProps = new Properties();
			fis = new FileInputStream(scaPropertiesFile);

			scaRunTimeProps.load(fis);
			addProperties(scaRunTimeProps);
		}catch (IOException x) {
			getLog().error("Failed to process " + scaPropertiesFile.getAbsolutePath());

			throw new MojoExecutionException("Failed to process " + scaPropertiesFile.getAbsolutePath(), x.getCause());
		}finally {
			FileHelper.closeQuietly(fis);
		}
	}

	protected void constructCommandLineArgs() throws MojoExecutionException {
		argFileStrBuf = new StringBuilder();
		commandStrBuf = new StringBuilder();
		toggleSwitch(jre64, "-64");
		toggleSwitch(version, "-version");
		toggleSwitch(debug, "-debug");
		toggleSwitch(verbose, "-verbose");
		toggleSwitch(quiet, "-quiet");
		toggleSwitch(machineOutput, "-machine-output");
		addOptionValuePair("-b", buildId);
		processPropertiesFile();
	}

	protected void writeArgFile(String fileName) throws MojoExecutionException {
		FileWriter fw = null;
		try {
			File argfile = new File(fileName);
			File parentDir = argfile.getParentFile();
			if (!parentDir.exists() && !parentDir.mkdirs()) {
				getLog().info("Failed to create dir " + parentDir.getAbsolutePath());

				throw new MojoExecutionException("Unable to find / create specified output directory " + parentDir.getAbsolutePath());
			} else {
				getLog().info("Created output dir " + parentDir.getAbsolutePath());
			}

			fw = new FileWriter(fileName);
			fw.write(argFileStrBuf.toString());
			fw.flush();
		} catch (IOException x) {
			getLog().error("Failed while attempting to write to " + fileName + " - " + x.getMessage());
			throw new MojoExecutionException(x.getMessage());
		} finally {
			FileHelper.closeQuietly(fw);
		}
	}

	/**
	 * @param argFile
	 *            for SCA invocation.
	 * @param failOnSCAError
	 *            when true, throws an exception if command-fails.
	 * @return true if scan was successful, false otherwise.
	 * @throws MojoExecutionException
	 * @throws MojoFailureException
	 */
	protected boolean invoke(String argFile, boolean failOnSCAError)
			throws MojoExecutionException, MojoFailureException {
		if (dontRunSCA) {
			getLog().info("dontRunSCA is set by the user. Collect the argument file from the target directory.");
			return true;
		}

		StreamConsumer output = new DefaultConsumer();
		StreamConsumer error = new DefaultConsumer();
		Commandline cli = new Commandline();
		try {
			if (sourceanalyzer == null)
				throw new IllegalArgumentException("No sourceanalyzer executable has been defined.  " 
					+ "Please rectify this, if problems persist contact Fortify Professional Services for assistance.");
			cli.setExecutable(sourceanalyzer);
			cli.addSystemEnvironment();

			cli.createArgument().setLine(commandStrBuf.toString());
			// -Xmx is a JVM argument. Hence, it must go outside the argument
			// file: By the time SCA starts up and parses
			// the argument file, it is too late -- the JVM has already started.
			if (maxHeap != null && maxHeap.length() > 0) {
				cli.createArgument().setLine("-Xmx" + maxHeap);
			}
			
			if (minHeap != null && minHeap.length() > 0 ) {
				cli.createArgument().setLine("-Xms" + minHeap);
			}
			
			if (maxPermGen != null && maxPermGen.length() > 0 ) {
				cli.createArgument().setLine("-XX:MaxPermSize=" + maxPermGen);
			}
			
			if (stackSize != null && stackSize.length() > 0 ) {
				cli.createArgument().setLine("-Xss" + stackSize);
			}
			cli.createArgument().setValue("@" + argFile);
			getLog().info("cmd: \"" + cli.toString() + "\"");

			// execute the command
			getLog().debug("Executing system command: " + cli.toString());
			int result = CommandLineUtils
					.executeCommandLine(cli, output, error);
			getLog().debug("Exit code " + result);
			if (result != 0) {
				String errorMsg = "Error invoking sourceanalyzer. Exit code: "
								+ result 
								+ ".\nVerify your project settings and your SCA installation.";

				getLog().error(errorMsg);

				if (failOnSCAError) {
					throw new MojoExecutionException(errorMsg);
				}
			}
			return (result == 0);
		} catch (CommandLineException e) {
			getLog().error("While executing command " + e, e);
			getLog().error("Please confirm that the sourceanalyzer executable is in the path or specified correctly using the sourceanalyzer configuration"
							+ " option.  Currently it is set to \""
							+ sourceanalyzer + "\"");
			throw new MojoExecutionException(e.getLocalizedMessage());
		} catch (Exception e) {
			getLog().error("While setting system environment to cli - " + e.getLocalizedMessage(), e);
			throw new MojoExecutionException(e.getLocalizedMessage());
		}
	}

	// Make sure that the project is available. Initialize util.
	protected void init() {
		if (project == null) {
			throw new IllegalArgumentException("Project not found. Make sure that your POM file is valid.");
		}

		util = new Util(project, getLog());
	}
}
