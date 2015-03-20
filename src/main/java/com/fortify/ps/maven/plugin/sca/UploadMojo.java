package com.fortify.ps.maven.plugin.sca;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.DefaultConsumer;
import org.codehaus.plexus.util.cli.CommandLineUtils.StringStreamConsumer;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Upload result to Software Security Center.
 * 
 * @goal upload
 * @requiresProject
 */
public class UploadMojo extends AbstractSCAMojo {

	/**
	 * Location and name of the generated FPR file to upload
	 * 
	 * @parameter expression="${fortify.sca.resultsFile}" default-value=
	 *            "${project.build.directory}/${project.artifactId}-${project.version}.fpr"
	 */
	private String resultsFile;

	/**
	 * location of the fortifyclient executable. Defaults to fortifyclient which
	 * will run the version on the path, uploads will fail if non exists.
	 * 
	 * @parameter expression="${fortify.sca.managerclient.executable}"
	 *            default-value="fortifyclient"
	 * @required
	 */
	private String fortifyclient;

	/**
	 * If set to false the plugin will try to continue past individual scan
	 * upload failures.
	 * 
	 * @parameter expression="${fortify.sca.upload.failOnError}"
	 *            default-value="false"
	 */
	private boolean failOnUploadError;

	/**
	 * Project Name of the project in Software Security Center used if upload is
	 * set to true. Note that this must be supplied in conjunction with
	 * projectVersion.
	 * 
	 * @parameter expression="${fortify.f360.projectName}"
	 */
	protected String projectName;

	/**
	 * Project Version of the project in Software Security Center used if upload
	 * is set to true. Note that this must be supplied in conjunction with
	 * projectName.
	 * 
	 * @parameter expression="${fortify.f360.projectVersion}"
	 */
	protected String projectVersion;

	/**
	 * Project ID of the project in Software Security Center used if upload is
	 * set to true. Note that projectId will override projectName,
	 * projectVersion combinations if supplied.
	 * 
	 * @parameter expression="${fortify.f360.projectId}"
	 * @deprecated See projectName and projectVersion
	 */
	private String projectId;

	/**
	 * AnalysisUploadToken to use when attempting to upload fpr files to
	 * Software Security Center
	 * 
	 * @parameter expression="${fortify.f360.authToken}"
	 */
	protected String f360AuthToken;

	/**
	 * SSC url to interact with during uplaod actions
	 * 
	 * @parameter expression="${fortify.f360.url}"
	 */
	protected String f360Url;

	public void execute() throws MojoExecutionException, MojoFailureException {
		init();
		
		upload();
	}

	protected void upload() throws MojoExecutionException, MojoFailureException {
		if (projectId == null && (projectName == null && projectVersion == null)) {
			projectId = getProjectId(project.getArtifactId(), project.getVersion());
		}

		getLog().info("***** Attempting to upload to SSC with project (Name: \""
						+ (projectName == null ? "N/A" : projectName)
						+ "\", Version: \""
						+ (projectVersion == null ? "N/A" : projectVersion)
						+ "\") + file: " + resultsFile + " *****");

		upload(projectId, projectName, projectVersion, resultsFile, failOnUploadError);
	}

	private boolean upload(String projectId, String projectName, String projectVersion, String resultsFile, boolean failOnUploadError)
			throws MojoExecutionException, MojoFailureException {
		StreamConsumer output = new DefaultConsumer();
		StreamConsumer error = new DefaultConsumer();
		Commandline cli = new Commandline();

		// validate parameters ... note that this will throw an Exception if the
		// correct parameters have not bee supplied
		validateSSCUploadArgs(projectId, projectName, projectVersion, f360Url, f360AuthToken);

		try {
			if (fortifyclient == null)
				throw new IllegalArgumentException("No fortifyclient executable has been defined.  "
								+ "Please rectify this, if problems persist contact Fortify Professional Services for assistance.");
			cli.setExecutable(fortifyclient);
			cli.addSystemEnvironment();

			// Sample upload call:
			// fortifyclient uploadFPR -file banking.fpr -project BankingProject
			// -version 7.1 -authtoken 2aeabd06-703f-4ebc-a66e-3d66f19f0630 -url
			// http://10.94.175.31:8180/ssc
			cli.createArgument().setValue("uploadFPR");
			cli.createArgument().setValue("-f");
			cli.createArgument().setValue(resultsFile);

			if (projectId != null && projectId.length() != 0) {
				cli.createArgument().setValue("-projectID");
				cli.createArgument().setValue(projectId);
			} else {
				cli.createArgument().setValue("-project");
				cli.createArgument().setValue(projectName);
				cli.createArgument().setValue("-version");
				cli.createArgument().setValue(projectVersion);
			}

			cli.createArgument().setValue("-authtoken");
			cli.createArgument().setValue(f360AuthToken);

			cli.createArgument().setValue("-url");
			cli.createArgument().setValue(f360Url);

			getLog().info("cmd: \"" + cli.toString() + "\"");

			// execute the command
			getLog().debug("Executing system command: " + cli.toString());
			int result = CommandLineUtils.executeCommandLine(cli, output, error);
			getLog().debug("Exit code " + result);
			if (result != 0) {
				getLog().error("The fortifyclient invocation failed. ResultCode: "
								+ result
								+ "\nPlease check your project settings, and sourceanalyzer installation / invokation."
								+ "\nContact Fortify Professional Services if this error persists.");
			}

			return (result == 0);
		} catch (CommandLineException e) {
			getLog().error("While executing command " + e, e);
			getLog().error("Please confirm that the fortifyclient executable is in the path or specified correctly using the fortifyclient configuration"
							+ " option.  Currently it is set to \""
							+ sourceanalyzer + "\"");
			throw new MojoExecutionException(e.getLocalizedMessage());
		} catch (Exception e) {
			getLog().error("While setting system environment to cli - " + e.getLocalizedMessage(), e);
			throw new MojoExecutionException(e.getLocalizedMessage());
		}
	}

	private String getProjectId(String projectName, String projectVersion)
			throws MojoExecutionException, MojoFailureException {
		StringStreamConsumer output = new StringStreamConsumer();
		StringStreamConsumer error = new StringStreamConsumer();
		Commandline cli = new Commandline();
		String projectId = null;
		try {
			if (fortifyclient == null)
				throw new IllegalArgumentException("No fortifyclient executable has been defined.  "
								+ "Please rectify this, if problems persist contact Fortify Professional Services for assistance.");
			cli.setExecutable(fortifyclient);
			cli.addSystemEnvironment();

			cli.createArgument().setValue("listprojects");
			cli.createArgument().setValue("-url");
			cli.createArgument().setValue(f360Url);
			cli.createArgument().setValue("-authtoken");
			cli.createArgument().setValue(f360AuthToken);

			getLog().info("cmd: \"" + cli.toString() + "\"");

			// execute the command
			getLog().debug("Executing system command: " + cli.toString());
			int result = CommandLineUtils.executeCommandLine(cli, output, error);
			getLog().debug("Exit code " + result);
			if (result != 0) {
				getLog().error("The fortifyclient invocation failed. ResultCode: "
								+ result
								+ "\nPlease check your project settings, and sourceanalyzer installation / invokation."
								+ "\nContact Fortify Professional Services if this error persists.");
				if (failOnUploadError) {
					throw new MojoExecutionException("The fortifyclient invocation failed. ResultCode: "
									+ result
									+ "\nPlease check your project settings, , and sourceanalyzer installation / invokation."
									+ "\nContact Fortify Professional Services if this error persists.");
				}
			} else {
				// try to parse result and look for our specific project name
				String strOutput = output.getOutput();
				String regexp = ".*(\\d+)\\s+" + escapeRegexpChars(projectName) + "\\s+" + escapeRegexpChars(projectVersion) + ".*";
				getLog().debug("\n*******************\n"
								+ "******* fortifyclient OUPUT looking for Project: \""
								+ projectName + "\" Version: \""
								+ projectVersion + "\" ******* \n"
								+ "******* Regexp: \"" + regexp + "\"\n"
								+ "*******************\n" + strOutput
								+ "***** END OUTPUT *****\n");
				Pattern p = Pattern.compile(regexp, Pattern.MULTILINE | Pattern.DOTALL);
				Matcher m = p.matcher(strOutput);
				if (m.matches()) {
					projectId = m.group(1);
					getLog().info("** Found FM Project Id: " + projectId + " **");
				} else {
					getLog().info("** Project: " + projectName + " does not exist in SSC.  Skipping upload");
				}
			}

			return projectId;
		} catch (CommandLineException e) {
			getLog().error("While executing command " + e, e);
			getLog().error("Please confirm that the fortifyclient executable is in the path or specified correctly using the fortifyclient configuration"
							+ " option.  Currently it is set to \""
							+ sourceanalyzer + "\"");
			throw new MojoExecutionException(e.getLocalizedMessage());
		} catch (Exception e) {
			getLog().error("While setting system environment to cli - " + e.getLocalizedMessage(), e);
			throw new MojoExecutionException(e.getLocalizedMessage());
		}
	}

	/**
	 * Escapes regexp characters appearing in parameter to prevent accidental
	 * corruption of regexp (incomplete)
	 * 
	 * @param name
	 * @return name with regexp chars escaped
	 */
	private String escapeRegexpChars(String name) {

		return name;
		/*
		 * if ( name == null || name.length() == 0) return name ;
		 * 
		 * // otherwise escape . \ * + $ and ^ String sanitized =
		 * name.replaceAll("\\", "\\\\") ;
		 * 
		 * // replace . sanitized.replaceAll("\\.","\\.") ;
		 * 
		 * // replace * sanitized.replaceAll("\\*","\\\\*") ;
		 * 
		 * // replace ^ sanitized.replaceAll("\\^","\\\\^") ;
		 * 
		 * // replace $ sanitized.replaceAll("\\$","\\\\$") ;
		 * 
		 * //TODO change to debug getLog().info("Sanitizing string \"" + name +
		 * "\" with \"" + sanitized+ "\"") ;
		 * 
		 * return sanitized ;
		 */
	}

	/**
	 * Performs a sanity check on the supplied parameters.
	 * 
	 * @throws MojoExecutionException
	 *             if correct combination of parameters have not been supplied.
	 */
	protected void validateSSCUploadArgs(String projectId, String projectName, String projectVersion, String f360Url, String f360AuthToken)
			throws MojoExecutionException {

		StringBuffer errMsg = new StringBuffer();

		// if upload then url, authtoken are mandatory
		if (!StringHelper.isDefinedNonTrivially(f360Url)) {
			errMsg.append("Please supply the Software Security Center url.\n");
		}

		if (!StringHelper.isDefinedNonTrivially(f360AuthToken)) {
			errMsg.append("Please supply the Software Security Center authentication token.\n");
		}

		// if projectId supplied then warn that it overrides projectName/Version
		// if supplied
		if (!StringHelper.isDefinedNonTrivially(projectId)) {
			if (projectName != null || projectVersion != null) {
				getLog().warn("Supplied upload projectId: " 
								+ projectId
								+ " overrides projectName/projectVersion combination: \""
								+ projectName + "\"/\"" + projectVersion + "\"");
			}
		} else {
			// no projectID then name/version required
			if (!StringHelper.isDefinedNonTrivially(projectName)) {
				errMsg.append("Please supply a valid projectName value.\n");
			}

			if (!StringHelper.isDefinedNonTrivially(projectVersion)) {
				errMsg.append("Please supply a valid projectVersion value.\n");
			}
		}

		if (errMsg.length()>0){
			throw new MojoExecutionException(errMsg.toString());
		}
	}
}
