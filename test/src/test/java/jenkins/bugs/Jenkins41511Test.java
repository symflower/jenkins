package jenkins.bugs;

import hudson.security.HudsonPrivateSecurityRealm;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeAll;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class Jenkins41511Test {

    @BeforeAll
    public static void setUpClass() {
        System.setProperty(Jenkins.class.getName() + ".slaveAgentPort", "10000");
        System.setProperty(Jenkins.class.getName() + ".slaveAgentPortEnforce", "true");
    }

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void configRoundTrip() throws Exception {
        Jenkins.get().setSecurityRealm(new HudsonPrivateSecurityRealm(true, false, null));
        j.submit(j.createWebClient().goTo("configureSecurity").getFormByName("config"));
    }
}
