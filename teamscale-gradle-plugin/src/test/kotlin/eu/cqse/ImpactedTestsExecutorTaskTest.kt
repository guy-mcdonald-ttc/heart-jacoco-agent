package eu.cqse

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ImpactedTestsExecutorTaskTest {
    @Test
    fun normalize() {
        assertThat(ImpactedTestsExecutorTask.normalize("**/com/teamscale/test/ui/**")).isEqualTo("**com.teamscale.test.ui**")
        assertThat(ImpactedTestsExecutorTask.normalize("**/TeamscaleUITest.class")).isEqualTo("**TeamscaleUITest")
    }

}