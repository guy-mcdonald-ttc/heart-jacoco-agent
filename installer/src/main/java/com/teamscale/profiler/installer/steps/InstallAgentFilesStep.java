package com.teamscale.profiler.installer.steps;

import com.teamscale.profiler.installer.FatalInstallerError;
import com.teamscale.profiler.installer.InstallFileUtils;
import com.teamscale.profiler.installer.TeamscaleCredentials;
import org.conqat.lib.commons.filesystem.FileSystemUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Properties;
import java.util.stream.Stream;

/** Copies the agent into the installation directory and sets the necessary file permissions. */
public class InstallAgentFilesStep implements IStep {

	private final Path sourceDirectory;
	private final Path installDirectory;

	public InstallAgentFilesStep(Path sourceDirectory, Path installDirectory) {
		this.sourceDirectory = sourceDirectory;
		this.installDirectory = installDirectory;
	}

	private Path getCoverageDirectory() {
		return installDirectory.resolve("coverage");
	}

	private Path getLogDirectory() {
		return installDirectory.resolve("logs");
	}

	private Path getTeamscalePropertiesPath() {
		return installDirectory.resolve("teamscale.properties");
	}

	@Override
	public void install(TeamscaleCredentials credentials) throws FatalInstallerError {
		createAgentDirectory();
		copyAgentFiles();
		writeTeamscaleProperties(credentials);
		makeCoverageAndLogDirectoriesWorldWritable();
		makeAllProfilerFilesWorldReadable();
	}

	@Override
	public void uninstall(IUninstallErrorReporter errorReporter) {
		if (!Files.exists(installDirectory)) {
			return;
		}

		FileSystemUtils.deleteRecursively(installDirectory.toFile());

		if (Files.exists(installDirectory)) {
			errorReporter.report(new FatalInstallerError("Failed to fully remove " + installDirectory));
		}
	}

	private void makeAllProfilerFilesWorldReadable() throws FatalInstallerError {
		try (Stream<Path> fileStream = Files.walk(installDirectory)) {
			for (Iterator<Path> it = fileStream.iterator(); it.hasNext(); ) {
				Path path = it.next();
				InstallFileUtils.makeReadable(path);
			}
		} catch (IOException e) {
			throw new FatalInstallerError("Failed to list all files in " + installDirectory + ".", e);
		}
	}

	private void makeCoverageAndLogDirectoriesWorldWritable() throws FatalInstallerError {
		InstallFileUtils.createDirectory(getCoverageDirectory());
		InstallFileUtils.makeWritable(getCoverageDirectory());

		InstallFileUtils.createDirectory(getLogDirectory());
		InstallFileUtils.makeWritable(getLogDirectory());

	}

	private void writeTeamscaleProperties(TeamscaleCredentials credentials) throws FatalInstallerError {
		Properties properties = new Properties();
		properties.setProperty("url", credentials.url.toString());
		properties.setProperty("username", credentials.username);
		properties.setProperty("accesskey", credentials.accessKey);

		try (OutputStream out = Files.newOutputStream(getTeamscalePropertiesPath(), StandardOpenOption.WRITE,
				StandardOpenOption.CREATE)) {
			properties.store(out, null);
		} catch (IOException e) {
			throw new FatalInstallerError("Failed to write " + getTeamscalePropertiesPath() + ".", e);
		}

		InstallFileUtils.makeReadable(getTeamscalePropertiesPath());
	}

	private void copyAgentFiles() throws FatalInstallerError {
		try {
			FileSystemUtils.copyFiles(sourceDirectory.toFile(), installDirectory.toFile(), null);
		} catch (IOException e) {
			throw new FatalInstallerError("Failed to copy some files to " + installDirectory + "."
					+ " Please manually clean up " + installDirectory, e);
		}
	}

	private void createAgentDirectory() throws FatalInstallerError {
		if (Files.exists(installDirectory)) {
			throw new FatalInstallerError("Cannot install to " + installDirectory + ": Path already exists");
		}

		InstallFileUtils.createDirectory(installDirectory);
	}

}
