package com.fortify.ps.maven.plugin.sca;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * This goal clears out a build ID, potentially for re-use in a subsequent
 * analysis.
 * 
 * @goal clean
 * @phase clean
 * @requiresProject
 */

public class CleanMojo extends AbstractSCAMojo {

	/**
	 * The log file to be produced by sourceanalyzer.
	 * 
	 * @parameter expression="${fortify.sca.logfile}"
	 *            default-value="${project.build.directory}/sca-clean.log"
	 */
	protected String logfile;

	/**
	 * Argument file to use when invoking sourceanalyzer.
	 * 
	 * @parameter default-value="${project.build.directory}/sca-clean-args.txt"
	 * @required
	 * @readonly
	 */
	protected String scaArgsFileName;

	private static String cleanedBuild = null;

	public void execute() throws MojoExecutionException, MojoFailureException {

		if (!scanEnabled) {
			getLog().info("Scan disabled for this project. Will not clean.");
			return;
		}

		init();
		getLog().debug("         packaging: " + packaging);
		getLog().debug("           buildId: " + buildId);
		getLog().debug("    sourceanalyzer: " + sourceanalyzer);

		// Delete the class path log.
		File cpFile = util.getClassPathFile();
		if (cpFile.delete()) {
			getLog().info("Deleted " + cpFile.getAbsolutePath());
		}

		// print command
		if (cleanedBuild != null && cleanedBuild.equals(buildId)) {
			getLog().info(buildId + " clean - nothing to do.");
		} else {
			getLog().info(" *** Clean intermediate files for buildId: " + buildId + " ***");
			// construct basic params
			constructCommandLineArgs();
			// print command
			writeArgFile(scaArgsFileName);
			invoke(scaArgsFileName, true);
			cleanedBuild = buildId;
		}
	}

	protected void constructCommandLineArgs() throws MojoExecutionException {
		super.constructCommandLineArgs();
		if (StringHelper.isDefinedNonTrivially(logfile)) {
			addArg("-logfile");
			addArg(logfile);
		}
		addArg("-clean");
	}
}
