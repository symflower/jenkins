package jenkins.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.Node.Mode;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class MasterBuildConfigurationTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-23966")
    public void retainMasterLabelWhenNoSlaveDefined() throws Exception {
        Jenkins jenkins = j.getInstance();

        assertEquals(1, jenkins.getComputers().length, "Test is for controller with no agent");

        // set our own label & mode
        final String myTestLabel = "TestLabelx0123";
        jenkins.setLabelString(myTestLabel);
        jenkins.setMode(Mode.EXCLUSIVE);

        // call global config page
        j.configRoundtrip();

        // make sure settings were not lost
        assertEquals(myTestLabel, jenkins.getLabelString(), "Built in node's label is lost");
        assertEquals(Mode.EXCLUSIVE, jenkins.getMode(), "Built in node's mode is lost");
    }
}
