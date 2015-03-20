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
 * Perform the scan step on a particular build ID. Optionally, upload results to
 * Software Security Center.
 * 
 * @goal scan
 * @requiresProject
 */
public class ScanMojo extends AbstractSCAMojo {

	/**
	 * For aggregate builds it is necessary to use the -Dfortify.sca.toplevel.artifactId
	 * commandline configuration to ensure that all code for modules
	 * (sub-projects) built are added under a single buildId and hence analyzed
	 * together. Otherwise each sub-module will be assigned its own default
	 * buildId
	 * 
	 * @parameter expression="${fortify.sca.toplevel.artifactId}"
	 */
	private String toplevelArtifactId;

	/**
	 * arguments file for running sourceanalyzer
	 * 
	 * @parameter default-value="${project.build.directory}/sca-scan-args.txt"
	 * @required
	 * @readonly
	 */
	private String scaArgsFileName;

	/**
	 * Build Label inserted in to generated FPR
	 * 
	 * @parameter expression="${fortify.sca.buildLabel}"
	 *            default-value="${project.artifactId}-${project.version}"
	 */
	private String buildLabel;

	/**
	 * Build Project Name inserted in to generated FPR
	 * 
	 * @parameter expression="${fortify.sca.buildProject}"
	 *            default-value="${project.artifactId}"
	 */
	private String buildProject;

	/**
	 * Build Version inserted in to generated FPR
	 * 
	 * @parameter expression="${fortify.sca.project}"
	 *            default-value="${project.version}"
	 */
	private String buildVersion;

	/**
	 * Location and name of the generated FPR file
	 * 
	 * @parameter expression="${fortify.sca.resultsFile}" default-value=
	 *            "${project.build.directory}/${project.artifactId}-${project.version}.fpr"
	 */
	private String resultsFile;

	/**
	 * If true include FindBugs analysis results in the final report
	 * 
	 * @parameter expression="${fortify.sca.findbugs}" default-value="false"
	 */
	private boolean findbugs;

	/**
	 * If true an html report summary of results is produced
	 * 
	 * @parameter expression="${fortify.sca.htmlReport}" default-value="false"
	 */
	private boolean htmlReport;

	/**
	 * Source files are not included in the FPR file if set to false.
	 * 
	 * @parameter expression="${fortify.sca.renderSources}" default-value="true"
	 */
	private boolean renderSources;

	/**
	 * Scans the project in Quick Scan Mode, using the
	 * fortifysca-quickscan.properties file. By default, this scan searches for
	 * high-confidence, high-severity issues
	 * 
	 * @parameter expression="${fortify.sca.quickScan}" default-value="false"
	 */
	private boolean quickScan;

	/**
	 * Rules files to use when performing a scan. Use an outer &lt;rules&gt; tag
	 * followed by nested &lt;rule&gt; tags to specify one or more rules files
	 * to use when performing the scan.
	 * 
	 * @parameter
	 */
	private String[] rules;

	/**
	 * Filter file to use when performing a scan
	 * 
	 * @parameter expression="${fortify.sca.filter}"
	 */
	private String filter;

	/**
	 * Specifies the log file that is produced by Fortify SCA.
	 * 
	 * @parameter expression="${fortify.sca.logfile}"
	 *            default-value="${project.build.directory}/sca-scan.log"
	 */
	private String logfile;

	/**
	 * If set to false the plugin will try to continue past individual SCA scan
	 * invocation failures. This is useful to get an initial scan or when you
	 * are doing a component by component scan of a project tree. Set this to
	 * true when performing an aggregate scan.
	 * 
	 * @parameter expression="${fortify.sca.scan.failOnError}"
	 *            default-value="false"
	 */
	private boolean failOnSCAError;

	/**
	 * if true, the plugin will attempt to upload the result of the scan to
	 * Software Security Center
	 * 
	 * @parameter expression="${fortify.sca.upload}" default-value="false"
	 */
	protected boolean upload;

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
		if (!scanEnabled) {
			getLog().info("Scan disabled for this project. Skipping.");
			return;
		}

		init();

		if (!packaging.equals("pom") && !packaging.equals("jar") && !packaging.equals("war") && !packaging.equals("ear") && !packaging.equals("ejb") && !packaging.equals("maven-plugin") && !packaging.equals("bundle")) {
			getLog().info("******* Skipping scan for " + project.getArtifactId() + ". The SCA maven-plugin does not support the packaging value " + packaging + " ********");
			return;
		}

		getLog().info("                   Packaging -> " + packaging);
		getLog().info("       Top-Level Artifact ID -> " + toplevelArtifactId);
		getLog().info("                 Build Label -> " + buildLabel);
		getLog().info("               Build Version -> " + buildVersion);
		getLog().info("          Build Project Name -> " + buildProject);
		getLog().info("                    Build ID -> " + buildId);
		getLog().info("                Results File -> " + resultsFile);
		getLog().info("  Location of SCA Executable -> " + sourceanalyzer);
		getLog().info("                    Scan Log -> " + logfile);
		getLog().info("            FindBugs Results -> " + findbugs);
		getLog().info("               Fail on Error -> " + failOnSCAError);
		getLog().info("               Upload to SSC -> " + upload);
		if(upload){
			getLog().info("            SSC Project Name -> " + projectName);
			getLog().info("         SSC Project Version -> " + projectVersion);	
		}else{
			getLog().info("Issues will not be tracked and trended without uploading to SSC.");
		}

		// if aggregate scan then only process toplevel project
		if (buildId != null && toplevelArtifactId != null) {
			if (toplevelArtifactId.equals(project.getArtifactId())) {
				getLog().info("*** !! Scanning aggregate project - " + project.getArtifactId() + " !! ***");
			} else {
				getLog().info("*** Skipping scan of sub-project - " + project.getArtifactId() + " ***");
				return;
			}
		} else if (packaging.equals("pom")) {
			getLog().info("*** Nothing to scan for pom project - " + project.getArtifactId() + " ***");
			return;
		} else {
			getLog().info("*** !! Scanning individual sub-project - " + project.getArtifactId() + " !! ***");
		}

		// construct basic params
		constructCommandLineArgs();

		// print command
		writeArgFile(scaArgsFileName);
		boolean success = invoke(scaArgsFileName, failOnSCAError);
		if (success && upload) {
			upload();
		}
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
				if (failOnSCAError) {
					throw new MojoExecutionException("The fortifyclient invocation failed. ResultCode: "
									+ result
									+ "\nPlease check your project settings, , and sourceanalyzer installation / invokation."
									+ "\nContact Fortify Professional Services if this error persists.");
				}
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

		// nothing to check if no upload
		if (!upload){
			return;
		}

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

	protected void constructCommandLineArgs() throws MojoExecutionException {
		super.constructCommandLineArgs();
		
		addArg("-scan");

		if (logfile != null && logfile.length() > 0) {
			addArg("-logfile");
			addArg(logfile);
		}

		if (findbugs) {
			addArg("-findbugs");
		}

		if (buildLabel != null && buildLabel.length() > 0) {
			addArg("-build-label");
			addArg(buildLabel);
		}

		if (rules != null) {
			for (int i = 0; i < rules.length; i++) {
				addArg("-rules");
				addArg(rules[i]);
			}
		}

		if (filter != null && filter.length() > 0) {
			addArg("-filter");
			addArg(filter);
		}

		if (buildProject != null && buildProject.length() > 0) {
			addArg("-build-project");
			addArg(buildProject);
		}

		if (buildVersion != null && buildVersion.length() > 0) {
			addArg("-build-version");
			addArg(buildVersion);
		}

		if (htmlReport) {
			addArg("-html-report");
		}

		if (!renderSources) {
			addArg("-disable-sourcerendering");
		}

		if (quickScan) {
			addArg("-quick");
		}

		addArg("-format");
		addArg("fpr");

		addArg("-f");
		addArg(resultsFile);
	}

}
