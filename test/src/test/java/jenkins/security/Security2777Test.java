package jenkins.security;

import hudson.model.UnprotectedRootAction;
import java.io.IOException;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.junit.jupiter.api.Assertions;

public class Security2777Test {
    public static final String ACTION_URL = "security2777";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testView() throws IOException {
        final JenkinsRule.WebClient wc = j.createWebClient();

        // no exception on action index page
        wc.getPage(wc.getContextPath() + ACTION_URL);

        final FailingHttpStatusCodeException ex2 = Assertions.assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(wc.getContextPath() + ACTION_URL + "/fragmentWithoutIcon"), "no icon, no response");
        Assertions.assertEquals(404, ex2.getStatusCode(), "it's 404");

        final FailingHttpStatusCodeException ex3 = Assertions.assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(wc.getContextPath() + ACTION_URL + "/fragmentWithIcon"), "icon, still no response");
        Assertions.assertEquals(404, ex3.getStatusCode(), "it's 404");
    }

    @TestExtension
    public static class ViewHolder implements UnprotectedRootAction {

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
            return ACTION_URL;
        }
    }
}
