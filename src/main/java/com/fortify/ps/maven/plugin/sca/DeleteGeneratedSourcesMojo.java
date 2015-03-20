package com.fortify.ps.maven.plugin.sca;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Delete all generated Java sources from each project that is marked
 * scanEnabled.
 * 
 * @goal cleanUpGeneratedSources
 */
public class DeleteGeneratedSourcesMojo extends AbstractSCAMojo {

	@Override
	protected void init() {
		super.init();
		if (buildId == null) {
			throw new IllegalArgumentException("Build ID not set.");
		}
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		init();
		// Determine the generated sources directory. If successful and the
		// directory is available, delete all
		// Java files that are underneath it.
		String generatedSourcesDir = util.showModule(buildId,
				project.getGroupId(), project.getArtifactId());

		if (FileHelper.isDirectory(generatedSourcesDir)) {
			getLog().info("Deleting generated Java sources in the directory tree rooted at: " + generatedSourcesDir);
			FileHelper.deleteJavaSources(new File(generatedSourcesDir));
		}
	}
}
