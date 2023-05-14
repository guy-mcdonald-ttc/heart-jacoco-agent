plugins {
	com.teamscale.`system-test-convention`
}

tasks.test {
	/** This ports must match what is configured in the SystemTest class. */
	val agentPort = 63600
	teamscaleAgent(
		mapOf(
			"http-server-port" to "$agentPort",
			"tia-mode" to "http",
			"mode" to "testwise",
			"includes" to "*systemundertest.*"
		)
	)
}

dependencies {
	implementation(project(":tia-client"))
}