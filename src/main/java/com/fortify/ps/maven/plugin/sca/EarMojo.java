package com.fortify.ps.maven.plugin.sca;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.model.Plugin;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.Artifact;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import java.io.File;
import java.util.*;

/**
 * Requires SCA 360 v2.5+. Only use with WebLogic.
 * @goal ear
 * @phase install
 * @requiresDependencyResolution
 */
public class EarMojo extends AbstractSCAMojo {
	/**
	 * @parameter expression="${com.fortify.dev.maven.generatedSourcesDirectory}" default-value="${project.build.directory}"
	 */
	protected String generatedSourcesDirectory;
	
	/**
	 * @parameter expression="${com.fortify.dev.maven.earFileName}" default-value="${project.build.finalName}.ear"
	 */
	protected String earFileName;
	
	/**
	 * @parameter expression="${com.fortify.dev.maven.DoNotUseGeneratedSources}" default-value="false"
	 */
	protected boolean doNotUseGeneratedSources;
	
	/**
	 * @parameter expression="${com.fortify.dev.maven.RemoveNonExistentClassPathEntries}" default-value="true"
	 */
	protected boolean removeNonExistentClassPathEntries;
	
	private String warSourceDirectory = null;
	
	/**
	 * Where the user puts files to include in the WAR. The default directory is ${basedir}/src/main/webapp.
	 * 
	 * @return  A file path containing files to include in the WAR.
	 */
	public String getWarSourceDirectory() {
		if (warSourceDirectory != null) {
			return warSourceDirectory;
		}
		Plugin warPlugIn = util.getWarPlugin();
		Util.SafeConfig config = new Util.SafeConfig(warPlugIn);
		String defaultSrcDir = baseDir + File.separator + "src" + File.separator + "main" + File.separator + "webapp";
		warSourceDirectory = config.getElemValue("warSourceDirectory", defaultSrcDir);
		return warSourceDirectory;
	}
	
	protected String getClassPath() throws DependencyResolutionRequiredException {
		List entries = project.getCompileClasspathElements();
		if (entries != null & entries.size() > 0) {
			if (removeNonExistentClassPathEntries) {
				Iterator iter = entries.iterator();
				while (iter.hasNext()) {
					String classPathEntry = (String)iter.next();
					File f = new File(classPathEntry);
					if (!f.exists()) {
						iter.remove();
					}
				}
			}
			return StringHelper.listToString(entries);
		}
		return "";
	}

	@Override
	protected void init() {
		super.init();
		if (project.getBuild() == null || project.getBuildPlugins() == null || generatedSourcesDirectory == null) {
			// A better error message is in order here.
			getLog().error("The POM file does not appear to be valid.");
			return;
		}
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		// Even if scanEnabled is false for a project, invoke this goal on it anyway. It may be an EAR module that
		// contains information needed for translating other projects.
		
		init();
		
		try {
			File cpFile = util.getClassPathFile();
			String cp = getClassPath();
			if (StringHelper.isDefinedNonTrivially(cp)) {
				FileHelper.appendToFile(cpFile, getClassPath());
				util.buildClassPath(buildId, getClassPath());
				getLog().info("Class path log: " + cpFile);
			} else {
				getLog().info("This project contains no class path info.");
			}
		} catch (DependencyResolutionRequiredException e) {
			getLog().error(e);
		}

		// maven-ear-plugin's required parameters
		String earSourceDirectory;
		String finalName;
		String outputDirectory;

		// I don't remember why I put these here. Re-examine this later. Probably related to Maven using lazy evaluation.
		project.getArtifacts();
		project.getArtifactMap();

		Plugin earPlugIn = util.getEarPlugin();
		
		if (earPlugIn != null) {
			getLog().info("This is an EAR module.");
			Util.SafeConfig config = new Util.SafeConfig(earPlugIn);
			earSourceDirectory = config.getElemValue("earSourceDirectory", new File("src/main/application").getAbsolutePath());
			finalName = config.getElemValue("finalName", project.getBuild().getFinalName());
			outputDirectory = config.getElemValue("outputDirectory", project.getBuild().getDirectory());

			getLog().info("  Source Directory -> " + earSourceDirectory);
			getLog().info("        Final Name -> " + finalName);
			getLog().info("  Output Directory -> " + outputDirectory);

			Xpp3Dom rawConfig = (Xpp3Dom)earPlugIn.getConfiguration();
			Xpp3Dom[] mods = Util.getElem(rawConfig, "modules");
			for (int i = 0; i < mods.length; i++) {
				// sourceanalyzer -b buildId -group-id com.fortify -artifact-id myapp -add-module
				//      -generated-sources path/to/ear/bundleDir/bundleFileName

				if (!mods[i].getName().equals("webModule")) {
					continue;
				}

				String groupId = Util.getElemValue(mods[i], "groupId");
				String artifactId = Util.getElemValue(mods[i], "artifactId");

				if (!StringHelper.isDefinedNonTrivially(groupId) || !StringHelper.isDefinedNonTrivially(artifactId)) {
					getLog().warn("Module missing group ID or artifact ID.");
					continue;
				}

				getLog().info("          Group ID -> " + groupId);
				getLog().info("       Artifact ID -> " + artifactId);


				// This is not correct in the general case. The plugin should really parse the configuration of
				// maven-ear-plugin for the most accurate answer. For now, the user can set both
				// generatedSourcesDirectory and earFileName him/herself.
				String output = generatedSourcesDirectory + File.separator + earFileName;
				String bundleFileName = Util.getElemValue(mods[i], "bundleFileName");
				String bundleDir = Util.getElemValue(mods[i], "bundleDir");

				if (StringHelper.isDefinedNonTrivially(bundleDir)) {
					// The directory within the EAR that the Web module is put in. If this is not set, then the Web
					// module is simply put into the root of the EAR.
					output += File.separator + bundleDir;
				}

				if (StringHelper.isDefinedNonTrivially(bundleFileName)) {
					// This is the name of the Web app as its appears inside the EAR. It may be an archive or it may
					// simply be a directory. If this is not defined, then the name of the artifact in the repository
					// is used.
					output += File.separator + bundleFileName;
				} else {
					String key = groupId + ":" + artifactId;
					Artifact artifact = (Artifact)project.getArtifactMap().get(key);
					if (artifact != null && artifact.getFile() != null && StringHelper.isDefinedNonTrivially(artifact.getFile().getName())) {
						output += File.separator + artifact.getFile().getName();
					} else {
						getLog().warn("Could not determine the generated-sources path for Web module, " + key + ".");
						continue;
					}
				}

				File earFile = new File(generatedSourcesDirectory, earFileName);
				if (!util.explodeEarFile(earFile)) {
					getLog().error("EAR file could not be exploded.");    
				} else {
					getLog().info("Was able to explode EAR file: " + earFile.getAbsolutePath());
				}

				// The generated Java sources are put into WEB-INF/classes of the Web module.
				output += File.separator + "WEB-INF" + File.separator + "classes";

				if (!doNotUseGeneratedSources) {
					util.addModule(buildId, groupId, artifactId, new File(output).getAbsolutePath());
				}
			}
		}
	}
}
