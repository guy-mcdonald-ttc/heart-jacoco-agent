package com.teamscale.jacoco.agent.git_properties;

import com.teamscale.client.CommitDescriptor;
import com.teamscale.client.StringUtils;
import com.teamscale.jacoco.agent.util.LoggingUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class GitPropertiesLocator {

	private static final Logger LOGGER = LoggingUtils.getLogger(GitPropertiesLocator.class);

	/** Name of the git.properties file. */
	private static final String GIT_PROPERTIES_FILE_NAME = "git.properties";

	/** The standard date format used by git.properties. */
	private static final DateTimeFormatter GIT_PROPERTIES_DATE_FORMAT = DateTimeFormatter
			.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

	private static final Executor BACKGROUND_EXECUTOR = Executors.newSingleThreadExecutor();
	private static boolean FOUND_COMMIT_DESCRIPTOR = false;

	public static void searchJarFileForGitPropertiesAsync(File jarFile) {
		if (!FOUND_COMMIT_DESCRIPTOR) {
			BACKGROUND_EXECUTOR.execute(() -> searchJarFile(jarFile));
		}
	}

	private static void searchJarFile(File jarFile) {
		try {
			CommitDescriptor commitDescriptor = getCommitFromGitProperties(jarFile);
			if (commitDescriptor == null) {
				LOGGER.debug("No git.properties file found in {}", jarFile.toString());
			} else {
				LOGGER.debug("Found git.properties file in {} and found commit descriptor {}", jarFile.toString(),
						commitDescriptor.toString());
				FOUND_COMMIT_DESCRIPTOR = true;
				// TODO (FS)
			}
		} catch (IOException | InvalidGitPropertiesException e) {
			LOGGER.error("Error during asynchronous search for git.properties in {}", jarFile.toString(), e);
		}
	}

	/**
	 * Reads branch and timestamp entries from the given jar file's git.properties and builds a commit descriptor out of
	 * it. If no git.properties file can be found, returns null.
	 *
	 * @throws IOException                   If reading the jar file fails.
	 * @throws InvalidGitPropertiesException If a git.properties file is found but it is malformed.
	 */
	public static CommitDescriptor getCommitFromGitProperties(
			File jarFile) throws IOException, InvalidGitPropertiesException {
		try (JarInputStream jarStream = new JarInputStream(new FileInputStream(jarFile))) {
			return getCommitFromGitProperties(jarStream, jarFile);
		} catch (IOException e) {
			throw new IOException("Reading jar " + jarFile.getAbsolutePath() + " for obtaining commit " +
					"descriptor from git.properties failed", e);
		}
	}

	/** Visible for testing. */
	/*package*/
	static CommitDescriptor getCommitFromGitProperties(
			JarInputStream jarStream, File jarFile) throws IOException, InvalidGitPropertiesException {
		JarEntry entry = jarStream.getNextJarEntry();
		while (entry != null) {
			if (Paths.get(entry.getName()).getFileName().toString().toLowerCase().equals(GIT_PROPERTIES_FILE_NAME)) {
				Properties gitProperties = new Properties();
				gitProperties.load(jarStream);
				return parseGitPropertiesJarEntry(entry.getName(), gitProperties, jarFile);
			}
			entry = jarStream.getNextJarEntry();
		}

		return null;
	}

	/** Visible for testing. */
	/*package*/
	static CommitDescriptor parseGitPropertiesJarEntry(
			String entryName, Properties gitProperties, File jarFile) throws InvalidGitPropertiesException {
		String branch = gitProperties.getProperty("git.branch");
		String timestamp = gitProperties.getProperty("git.commit.time");

		if (StringUtils.isEmpty(branch)) {
			throw new InvalidGitPropertiesException(
					"No entry or empty value for 'git.branch' in " + entryName + " in " + jarFile + "." +
							"\nContents of " + GIT_PROPERTIES_FILE_NAME + ": " + gitProperties.toString()
			);
		} else if (StringUtils.isEmpty(timestamp)) {
			throw new InvalidGitPropertiesException(
					"No entry or empty value for 'git.commit.time' in " + entryName + " in " + jarFile +
							"\nContents of " + GIT_PROPERTIES_FILE_NAME + ": " + gitProperties.toString());
		}

		try {
			long parsedTimestamp = ZonedDateTime.parse(timestamp, GIT_PROPERTIES_DATE_FORMAT).toInstant()
					.toEpochMilli();
			return new CommitDescriptor(branch, parsedTimestamp);
		} catch (DateTimeParseException e) {
			throw new InvalidGitPropertiesException(
					"git.commit.time value '" + timestamp + "' in " + entryName + " in " + jarFile +
							" is malformed: it cannot be parsed with the pattern " + GIT_PROPERTIES_DATE_FORMAT, e);
		}
	}
}
