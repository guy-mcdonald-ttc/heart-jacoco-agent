package com.teamscale.tia.maven;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Composes a new argLine based on the current one and input about the desired agent configuration.
 */
public class ArgLine {

	/**
	 * Name of the property used in the maven-osgi-test-plugin.
	 */
	private static final String TYCHO_ARG_LINE = "tycho.testArgLine";

	/**
	 * Name of the property used in the maven-surefire-plugin.
	 */
	private static final String SUREFIRE_ARG_LINE = "argLine";

	/**
	 * Name of the property used in the spring-boot-maven-plugin start goal.
	 */
	private static final String SPRING_BOOT_ARG_LINE = "spring-boot.run.jvmArguments";

	private final String[] additionalAgentOptions;
	private final String agentLogLevel;
	private final Path agentJarFile;
	private final Path agentConfigFile;
	private final Path logFilePath;

	public ArgLine(String[] additionalAgentOptions, String agentLogLevel, Path agentJarFile,
				   Path agentConfigFile, Path logFilePath) {
		this.additionalAgentOptions = additionalAgentOptions;
		this.agentLogLevel = agentLogLevel;
		this.agentJarFile = agentJarFile;
		this.agentConfigFile = agentConfigFile;
		this.logFilePath = logFilePath;
	}

	public static void applyToMavenProject(ArgLine argLine, MavenSession session, Log log,
										   String userDefinedPropertyName, boolean isIntegrationTest) {
		MavenProject mavenProject = session.getCurrentProject();
		String effectivePropertyName = ArgLine.getEffectivePropertyName(userDefinedPropertyName, mavenProject,
				isIntegrationTest);

		Properties projectProperties = mavenProject.getProperties();
		cleanOldArgLines(projectProperties, log);

		if (effectivePropertyName.equals(SPRING_BOOT_ARG_LINE)) {
			projectProperties = session.getUserProperties();
		}
		String oldArgLine = projectProperties.getProperty(effectivePropertyName);
		String newArgLine = argLine.prependTo(oldArgLine);

		projectProperties.setProperty(effectivePropertyName, newArgLine);
		log.info(effectivePropertyName + " set to " + newArgLine);
	}

	private static void cleanOldArgLines(Properties projectProperties, Log log) {
		for (String propertyName : Arrays.asList(SPRING_BOOT_ARG_LINE, TYCHO_ARG_LINE, SUREFIRE_ARG_LINE)) {
			String oldArgLine = projectProperties.getProperty(propertyName);
			if (StringUtils.isBlank(oldArgLine)) {
				continue;
			}

			String newArgLine = removePreviousTiaAgent(oldArgLine);
			if (!oldArgLine.equals(newArgLine)) {
				log.info("Removed agent from property " + propertyName);
				projectProperties.setProperty(propertyName, newArgLine);
			}
		}
	}

	/**
	 * Takes the given old argLine, removes any previous invocation of our agent and prepends a new one based on the
	 * constructor parameters of this class. Preserves all other options in the old argLine.
	 */
	/*package*/ String prependTo(String oldArgLine) {
		String suffix = removePreviousTiaAgent(oldArgLine);
		if (StringUtils.isNotBlank(suffix)) {
			suffix = " " + suffix;
		}
		return createJvmOptions() + suffix;
	}

	private String createJvmOptions() {
		List<String> jvmOptions = new ArrayList<>();
		jvmOptions.add("-Dteamscale.markstart");
		jvmOptions.add(createJavaagentArgument());
		jvmOptions.add("-DTEAMSCALE_AGENT_LOG_FILE=" + logFilePath);
		jvmOptions.add("-DTEAMSCALE_AGENT_LOG_LEVEL=" + agentLogLevel);
		jvmOptions.add("-Dteamscale.markend");
		return jvmOptions.stream().map(ArgLine::quoteCommandLineOptionIfNecessary)
				.collect(Collectors.joining(" "));
	}

	private static String quoteCommandLineOptionIfNecessary(String option) {
		if (StringUtils.containsWhitespace(option)) {
			return "'" + option + "'";
		} else {
			return option;
		}
	}

	private String createJavaagentArgument() {
		List<String> agentOptions = new ArrayList<>();
		agentOptions.add("config-file=" + agentConfigFile.toAbsolutePath());
		agentOptions.addAll(Arrays.asList(ArrayUtils.nullToEmpty(additionalAgentOptions)));
		return "-javaagent:" + agentJarFile.toAbsolutePath() + "=" + String.join(",", agentOptions);
	}

	/**
	 * Determines the property in which to set the argLine. By default, this is the property used by the testing
	 * framework of the current project's packaging. The user may override this by providing their own property name.
	 */
	private static String getEffectivePropertyName(String userDefinedPropertyName, MavenProject mavenProject,
												  boolean isIntegrationTest) {
		if (StringUtils.isNotBlank(userDefinedPropertyName)) {
			return userDefinedPropertyName;
		}

		System.err.println("-----> isIT=" + isIntegrationTest + " hasSB=" + hasSpringBootPluginEnabled(mavenProject));
		if (isIntegrationTest && hasSpringBootPluginEnabled(mavenProject)) {
			return SPRING_BOOT_ARG_LINE;
		}

		if ("eclipse-test-plugin".equals(mavenProject.getPackaging())) {
			return TYCHO_ARG_LINE;
		}
		return SUREFIRE_ARG_LINE;
	}

	private static boolean hasSpringBootPluginEnabled(MavenProject mavenProject) {
		return mavenProject.getBuildPlugins().stream()
				.anyMatch(plugin -> plugin.getArtifactId().equals("spring-boot-maven-plugin"));
	}

	/**
	 * Removes any previous invocation of our agent from the given argLine. This is necessary in case we want to
	 * instrument unit and integration tests but with different arguments.
	 */
	private static String removePreviousTiaAgent(String argLine) {
		if (argLine == null) {
			return "";
		}
		return argLine.replaceAll("-Dteamscale.markstart.*teamscale.markend", "");
	}

}
