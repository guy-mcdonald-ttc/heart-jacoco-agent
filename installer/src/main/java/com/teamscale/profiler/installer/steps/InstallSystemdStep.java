package com.teamscale.profiler.installer.steps;

import com.teamscale.profiler.installer.EnvironmentMap;
import com.teamscale.profiler.installer.FatalInstallerError;
import com.teamscale.profiler.installer.PermissionError;
import com.teamscale.profiler.installer.TeamscaleCredentials;
import org.conqat.lib.commons.system.SystemUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * On Linux with systemd, registers the agent globally for systemd services. This is necessary in addition to
 * {@link InstallEtcEnvironmentStep}, since systemd doesn't always inject /etc/environment into started services.
 */
public class InstallSystemdStep implements IStep {

	private final Path etcDirectory;
	private final EnvironmentMap environmentVariables;

	public InstallSystemdStep(Path etcDirectory, EnvironmentMap environmentMap) {
		this.etcDirectory = etcDirectory;
		this.environmentVariables = environmentMap;
	}

	@Override
	public void install(TeamscaleCredentials credentials) throws FatalInstallerError {
		if (!SystemUtils.isLinux()) {
			return;
		}

		if (!Files.exists(getSystemdEtcDirectory())) {
			System.out.println("systemd could not be detected. Not installing profiler for systemd services.");
			// system has no systemd installed
			return;
		}

		Path systemdConfigFile = getSystemdConfigFile();
		if (Files.exists(systemdConfigFile)) {
			throw new PermissionError("Cannot create systemd configuration file " + systemdConfigFile);
		}

		String content = "[Manager]\nDefaultEnvironment=" + environmentVariables.getSystemdString() + "\n";
		try {
			Files.write(systemdConfigFile, content.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new PermissionError("Could not create " + systemdConfigFile, e);
		}
	}

	private Path getSystemdEtcDirectory() {
		return etcDirectory.resolve("systemd");
	}

	private Path getSystemdConfigFile() {
		return getSystemdEtcDirectory().resolve("teamscale-java-profiler.conf");
	}

	@Override
	public void uninstall(IUninstallErrorReporter errorReporter) {
		if (!SystemUtils.isLinux()) {
			return;
		}

		Path systemdConfigFile = getSystemdConfigFile();
		try {
			Files.delete(systemdConfigFile);
		} catch (IOException e) {
			errorReporter.report(
					new PermissionError("Failed to remove systemd config file " + systemdConfigFile + "." +
							" Manually remove this file or systemd Java services may fail to start.", e));
		}
	}
}
