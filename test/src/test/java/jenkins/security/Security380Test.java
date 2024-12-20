package jenkins.security;

import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import org.htmlunit.Page;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import org.junit.jupiter.api.Assertions;

public class Security380Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Issue("SECURITY-380")
    @Test
    public void testGetItemsWithoutAnonRead() throws Exception {
        FullControlOnceLoggedInAuthorizationStrategy strategy = new FullControlOnceLoggedInAuthorizationStrategy();
        strategy.setAllowAnonymousRead(false);
        Jenkins.get().setAuthorizationStrategy(strategy);

        Jenkins.get().setSecurityRealm(j.createDummySecurityRealm());

        j.createFreeStyleProject();
        ACL.impersonate2(Jenkins.ANONYMOUS2, () -> Assertions.assertEquals(0, Jenkins.get().getItems().size(), "no items"));
    }

    @Issue("SECURITY-380")
    @Test
    public void testGetItems() throws Exception {
        FullControlOnceLoggedInAuthorizationStrategy strategy = new FullControlOnceLoggedInAuthorizationStrategy();
        strategy.setAllowAnonymousRead(true);
        Jenkins.get().setAuthorizationStrategy(strategy);

        Jenkins.get().setSecurityRealm(j.createDummySecurityRealm());

        j.createFreeStyleProject();
        ACL.impersonate2(Jenkins.ANONYMOUS2, () -> Assertions.assertEquals(1, Jenkins.get().getItems().size(), "one item"));
    }

    @Issue("SECURITY-380")
    @Test
    public void testWithUnprotectedRootAction() throws Exception {
        FullControlOnceLoggedInAuthorizationStrategy strategy = new FullControlOnceLoggedInAuthorizationStrategy();
        strategy.setAllowAnonymousRead(false);
        Jenkins.get().setAuthorizationStrategy(strategy);

        Jenkins.get().setSecurityRealm(j.createDummySecurityRealm());
        j.createFreeStyleProject();

        JenkinsRule.WebClient wc = j.createWebClient();
        Page page = wc.goTo("listJobs", "text/plain");
        // return "0\r\n"
        Assertions.assertEquals("0", page.getWebResponse().getContentAsString().trim(), "expect 0 items");
    }

    @TestExtension
    public static class JobListingUnprotectedRootAction implements UnprotectedRootAction {

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "listJobs";
        }

        public HttpResponse doIndex() throws Exception {
            return HttpResponses.text(Integer.toString(Jenkins.get().getItems().size()));
        }
    }
}
