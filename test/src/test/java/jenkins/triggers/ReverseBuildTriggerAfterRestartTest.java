package jenkins.triggers;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsSessionRule;

public class ReverseBuildTriggerAfterRestartTest {

    @Rule
    public JenkinsSessionRule rule = new JenkinsSessionRule();

    @Issue("JENKINS-67237")
    @Test
    public void testExecutionOfReverseBuildTriggersAfterRestart() throws Throwable {
        String nameOfUpstreamProject = "upstreamProject";
        String nameOfDownstreamProject = "downstreamProject";

        rule.then(j -> {
            j.createFreeStyleProject(nameOfUpstreamProject);
            FreeStyleProject downstreamProject = j.createFreeStyleProject(nameOfDownstreamProject);
            downstreamProject.addTrigger(new ReverseBuildTrigger(nameOfUpstreamProject));
            downstreamProject.save();
        });

        rule.then(j -> {
            FreeStyleProject upstreamProject = j.jenkins.getItem(nameOfUpstreamProject, j.jenkins, FreeStyleProject.class);
            j.buildAndAssertSuccess(upstreamProject);
            j.waitUntilNoActivity();

            FreeStyleProject downstreamProject = j.jenkins.getItem(nameOfDownstreamProject, j.jenkins, FreeStyleProject.class);
            assertNotNull(downstreamProject.getLastBuild());
        });
    }
}
