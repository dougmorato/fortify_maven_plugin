package com.fortify.ps.maven.plugin.sca;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.DefaultConsumer;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class Util {

	////////////////////
	// Static Methods //
	////////////////////

	public static Pattern launcherArgument = Pattern.compile("(-flaunch_.*)|(-autoheap)|(-64)|(-X.*)|(-VM:.*)|(-scan)");

	public static boolean isLauncherArgument(String argument) {
		return launcherArgument.matcher(argument).matches();
	}

	/**
	 * Explode an EAR. In general, earFile should be an archive with the "ear" extension. However, it is legitimate to
	 * explode directories (that may or may not have the EAR extension) if they contain un-exploded WARs inside.
	 *
	 * @param earFile
	 * @return
	 */
	public boolean explodeEarFile(File earFile) {
		if (earFile.exists()) {
			if (!earFile.getName().endsWith("ear") && !earFile.isDirectory()) {
				log.warn("Attempting to explode a non-directory file that does not have the 'ear' extension.");
			}
			try {
				FileHelper.explodeWebApp(earFile);

				if (!earFile.isDirectory()) {
					log.error("Could not explode EAR file for unknown reasons. Please explode it manually and invoke this goal again.");
				} else if (!FileHelper.containsGeneratedSources(earFile)) {
					log.warn("Exploded EAR does not contain generated Java sources.");
				}
				return true;
			} catch (IOException e) {
				log.error("Error while exploding EAR file located at " + earFile.getAbsolutePath(), e);
			}
		}
		return false;
	}

	public static final String XML_PATH_SEP = "/";
	/**
	 * Simple way to access a certain node in an XML document. Not meant to replace XPath at all.
	 * 
	 * @param rootNode  Must be non-null. Starting point.
	 * @param path      Must be non-null. A path delimited by /.
	 * @return          At worst, an empty array. Otherwise, the node at the path specified xor the children if children
	 *                  exist.
	 */

	public static Xpp3Dom[] getElem(Xpp3Dom rootNode, String path) {
		assert rootNode != null;
		assert path != null;

		Xpp3Dom[] emptyArray = new Xpp3Dom[] { };

		if (rootNode == null || !StringHelper.isDefinedNonTrivially(path)) {
			return emptyArray;
		}

		String[] pathElems = path.split(XML_PATH_SEP);
		Xpp3Dom currNode = rootNode;
		for (int i = 0; i < pathElems.length; i++) {
			if (!StringHelper.isDefinedNonTrivially(pathElems[i])) { // Don't attempt to get a child without a name.
				continue;
			}
			currNode = currNode.getChild(pathElems[i]);
			if (currNode == null) {
				return emptyArray; // We cannot continue. This path does not exist.
			}
		}

		// Scenario 1: This node *can* and *does* have children. Return its children in an array.
		if (currNode.getChildCount() > 0) {
			return currNode.getChildren();
		}

		// Scenario 2: This node isn't the kind that has children. Rather, it takes a value. Just return the node.
		// If desired, the user may query for the value him/herself.

		if (StringHelper.isDefinedNonTrivially(currNode.getValue())) {
			return new Xpp3Dom[] { currNode };
		}

		// Scenario 3: Since this node doesn't have a value, it is one that *can* have children but doesn't. Indicate
		// this to the user of this method by returning an empty array.
		return emptyArray;
	}

	/**
	 * Given a path to a node (relative to a specified node), return its value.
	 * 
	 * @param rootNode
	 * @param path
	 * @returns The value of the specified node. Null if the specified node does not exist or has siblings.
	 */
	public static String getElemValue(Xpp3Dom rootNode, String path) {
		Xpp3Dom[] elem = getElem(rootNode, path);
		String retVal = null;
		if (elem.length == 1) {
			retVal = elem[0].getValue();
		}
		return retVal;
	}

	//////////////////////
	// Instance Methods //
	//////////////////////

	private MavenProject project = null;
	private Log log;

	public Util(MavenProject project, Log log) {
		this.project = project;
		this.log = log;
	}

	public File getClassPathFile() {
		return new File(project.getBuild().getDirectory(), "sca-classpath.log");
	}

	/**
	 * Returns the classpath string. The includeTests variable if true will
	 * result in Test class path dependencies to be included.
	 *
	 * @return An OS specific path string for passing to the -cp flag of SCA.
	 *         Returns null if none is found.
	 */
	public String resolveClassPath(List<Artifact> cpathArtifacts) {
		StringBuffer cpathBuff = new StringBuffer();
		Iterator<Artifact> artifactIt = cpathArtifacts.iterator();
		int size = cpathArtifacts.size();
		int count = 1;

		log.debug("** Artifacts ** ");

		while (artifactIt.hasNext()) {
			Artifact artifact = artifactIt.next();
			log.debug("#" + count + " scope: " + artifact.getScope() + " type:" + artifact.getType() + ", @" + artifact.getFile().getAbsolutePath());
			cpathBuff.append(artifact.getFile().getAbsolutePath());

			count++;

			// add separator if we're not the last
			if (count <= size) {
				cpathBuff.append(File.pathSeparator);
			}
		}

		// return constructed string or null if empty
		if (cpathBuff.length() != 0) {
			return cpathBuff.toString();
		}else {
			return null;
		}
	}

	/**
	 * Find the plug-in with the specified group ID and artifact ID. Currently, version is used.
	 * @param groupId
	 * @param artifactId
	 * @return
	 */
	public Plugin getPlugin(String groupId, String artifactId) {
		List plugins = project.getBuildPlugins();

		if (plugins != null) {
			Iterator iter = plugins.iterator();
			while (iter.hasNext()) {
				Plugin currPlugin = (Plugin)iter.next();

				String currGroupId = currPlugin.getGroupId();
				if (!currGroupId.equals(groupId)) {
					continue;
				}

				String currArtifactId = currPlugin.getArtifactId();
				if (!currArtifactId.equals(artifactId)) {
					continue;
				}

				return currPlugin;
			}
		}
		return null;
	}

	/**
	 * @return  Returns the WAR plug-in if it is found. If not found, return null.
	 */
	public Plugin getWarPlugin() {
		return getPlugin("org.apache.maven.plugins", "maven-war-plugin");
	}

	/**
	 * @return  Returns the EAR plug-in if it is found. If not found, returns null.
	 */
	public Plugin getEarPlugin() {
		return getPlugin("org.apache.maven.plugins", "maven-ear-plugin");
	}

	/**
	 * This class is safe because calling its methods should not throw any NullPointerExceptions. Even if the
	 * configuration of the plug-in is found to be null, this should not happen.
	 */
	public static class SafeConfig {
		private Xpp3Dom config  = null;

		public SafeConfig (Plugin plugin) {
			if (plugin != null) {
				this.config = (Xpp3Dom)plugin.getConfiguration();
			}
		}

		/**
		 * @param path  Path relative to the root node of the plug-in's configuration.
		 * @return      An array containing itself xor its children, depending on what is available. At worst,
		 *              an empty array.
		 */
		public Xpp3Dom[] getElem(String path) {
		    return Util.getElem(config, path);
		}

		/**
		 * Use this if you know that you are accessing an XML element with a value rather than one with children.
		 * 
		 * @param path
		 * @param defaultValue
		 * @return  At worst, the default value.
		 */
		public String getElemValue(String path, String defaultValue) {
			Xpp3Dom[] node = Util.getElem(config, path);
			if (node != null && node.length > 0 && StringHelper.isDefinedNonTrivially(node[0].getValue())) {
				return node[0].getValue();
			}

			return defaultValue;
		}
		
		/**
		 * Use this if you know that you are accessing an XML element with a value rather than one with children.
		 * 
		 * @param path
		 * @return  null if not found.
		 */
		public String getElemValue(String path) {
			Xpp3Dom[] node = Util.getElem(config, path);
			if (node != null && node.length > 0 && StringHelper.isDefinedNonTrivially(node[0].getValue())) {
				return node[0].getValue();
			}

			return null;
		}

	}

	/**
	 * This is an inner class of Util so that is is able to access the logging mechanism of a Mojo. Using an instance
	 * of this class, it is possible to get the output of a command invocation.
	 */
	public class Executable {
		private List outputLines = new ArrayList();
		private List errorLines = new ArrayList();
		private StreamConsumer output = new StreamConsumer() {
			public void consumeLine(String line) {
				outputLines.add(line);
			}
		};

		private StreamConsumer error = new StreamConsumer() {
			public void consumeLine(String line) {
				errorLines.add(line);
			}
		};

		private Commandline cli = new Commandline();

		public Executable(String executableName) {
			cli.setExecutable(executableName);
			try {
				cli.addSystemEnvironment();
			} catch (Exception e) {
				log.error("Error setting environment variables: ", e);
			}
		}

		/**
		 * Convenience constructor. If not executable is specified, assume that it is sourceanalyzer.
		 */
		public Executable() {
			this("sourceanalyzer");
		}

		private void addOption(String arg) {
			try {
				cli.createArgument().setLine("\"" + arg + "\"");
			} catch (Exception e) {
				log.error("Could not add argument " + arg + ".");
			}
		}

		public void addOptions(String... optionArr) {
			for (String opt : optionArr) {
				addOption(opt);
			}
		}

		public boolean invoke() {
			log.info("Executing command: " + cli.toString());
			int exitCode = 0;
			try {
				exitCode = CommandLineUtils.executeCommandLine(cli, output, error);
				if (exitCode != 0) {
					log.error("Command execution failed. Exit code: " + exitCode + ". Make sure " + "that the executable is available.");
				}
			} catch (CommandLineException e) {
			    log.error("Error executing command: ", e);
			}

			return exitCode == 0;
		}

		/**
		 * Retrieves non-warning output from an invocation of the executable.
		 * @return
		 */
		public String getOutput() {
			Iterator it = outputLines.iterator();
			while (it.hasNext()) {
				String currElem = (String)it.next();
				if (currElem.startsWith("[warning]")) {
					it.remove();
				}
			}
			return StringHelper.listToString(outputLines, File.pathSeparator);
		}

		public void setArgFile() {
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * Stores a class-path string for later use. Behind the scenes, the following is called.
	 * <p>
	 * sourceanalyzer -b buildId -cp "entry1;entry2;entry3" -build-class-path
	 * 
	 * @param buildId
	 * @param cp
	 * @return True if the invocation succeeded. False if not.
	 */
	public boolean buildClassPath(String buildId, String cp) {
		Executable exec = new Executable();
		exec.addOptions("-b", buildId, "-cp", cp, "-build-class-path");
		return exec.invoke();
	}

	/**
	 * Given a build ID, show the class path it is storing. Behind the scenes:
	 * <p>
	 * sourceanalyzer -b bid -show-class-path
	 * 
	 * @param buildId
	 * @return At worst, an empty string. At best, the class path stored in the specified build ID.
	 */
	public String showClassPath(String buildId) {
		String retVal = "";
		Executable exec = new Executable();
		exec.addOptions("-b", buildId, "-show-class-path");
		if (exec.invoke()) {
			retVal = exec.getOutput();
		} else {
			log.error("Could not retrieve saved class path.");
		}
		return retVal;
	}

	/**
	* Store the generated sources directory for a particular module.
	* <p>
	* sourceanalyzer -b bid -group-id gid -artifact-id aid -generated-sources gen-src-dir -add-module
	* 
	* @param buildId
	* @param groupId
	* @param artifactId
	* @param generatedSourcesDir
	* @return
	*/
	public boolean addModule(String buildId, String groupId, String artifactId, String generatedSourcesDir) {
		Executable exec = new Executable();
		exec.addOptions("-b", buildId,
						"-group-id", groupId,
						"-artifact-id", artifactId,
						"-generated-sources", generatedSourcesDir,
						"-add-module");
		return exec.invoke();
	}

	/**
	 * Given a build ID and module, show the generated sources directory, i.e. where generated JSP servlets can be
	 * found.
	 * <p>
	 * sourceanalyzer -b bid -group-id gid -artifact-id aid -show-module
	 * 
	 * @param buildId
	 * @param groupId
	 * @param artifactId
	 * @return
	 */
	public String showModule(String buildId, String groupId, String artifactId) {
		Executable exec = new Executable();
		exec.addOptions("-b", buildId,
						"-group-id", groupId,
						"-artifact-id", artifactId,
						"-show-module");
		if (exec.invoke()) {
			return exec.getOutput();
		}
		return "";
	}

	/**
	 * Given a folder containing JSP servlets (generatedSourcesDir) and a folder containing JSPs (documentRoot),
	 * translate them into the analysis model using the specified class path (classPath).
	 * <p>
	 * sourceanalyzer -b bid -cp "item1;item2;item3"
	 *                -document-root /path/to/jsps -generated-sources /path/to/generated/jsp/servlets
	 * 
	 * @param buildId
	 * @param classPath
	 * @param documentRoot
	 * @param generatedSourcesDir
	 * @return
	 */
	public boolean translateGeneratedSources(String buildId, String classPath, String documentRoot, String generatedSourcesDir) {
		Executable exec = new Executable();
		exec.addOptions("-b", buildId,
						"-cp", classPath,
						"-document-root", documentRoot,
						"-generated-sources", generatedSourcesDir);
		return exec.invoke();
	}

    /**
     * Invoke sourceanalyzer using an argument file.
     * 
     * @param argFile for SCA invocation.
     * @param failOnSCAError If true, throw an exception if the command fails.
     * @return True if the invocation succeeded. Otherwise, false.
     * @throws org.apache.maven.plugin.MojoExecutionException
     * @throws org.apache.maven.plugin.MojoFailureException
     */
    public boolean invoke(String sourceanalyzer, String args, String argFile, boolean failOnSCAError, String maxHeap)
		throws MojoExecutionException, MojoFailureException {
		
		StreamConsumer output = new DefaultConsumer();
		StreamConsumer error = new DefaultConsumer();
		Commandline cli = new Commandline();

		try {
			if (sourceanalyzer == null) {
			    throw new IllegalArgumentException("No executable defined.");
			}
			cli.setExecutable(sourceanalyzer);
			cli.addSystemEnvironment();

			// The JVM heap space must be specified/allocated prior to starting up the JVM. Hence, it cannot go inside
			// the argument file.
			if (maxHeap != null && maxHeap.length() > 0) {
			    cli.createArg().setLine(args);
			}

			cli.createArg().setValue("@" + argFile);

			log.info("Executing command: \"" + cli.toString() + "\"") ;
			int result = CommandLineUtils.executeCommandLine(cli, output, error);
			if (result != 0) {
				String errorMsg = "Error invoking sourceanalyzer. Exit code: " + result +
				        ".\nVerify your project settings and your SCA installation.";
				log.error(errorMsg);
				if (failOnSCAError) {
				    throw new MojoExecutionException(errorMsg);
				}
			}
			return result == 0;
		} catch (CommandLineException e) {
			log.error("Exception while executing command: ", e);
			log.error("Please confirm that the sourceanalyzer executable is in the path or specified correctly using the sourceanalyzer configuration" +
			    " option. Currently it is set to \"" + sourceanalyzer + "\"") ;
		    throw new MojoExecutionException(e.getLocalizedMessage());
		} catch (Exception e) {
			log.error( "While setting system environment to cli - " + e.getLocalizedMessage(), e);
			throw new MojoExecutionException(e.getLocalizedMessage());
		}
	}
}
