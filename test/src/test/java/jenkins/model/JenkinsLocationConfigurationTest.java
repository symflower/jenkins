package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.Label;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlElementUtil;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * @author Kohsuke Kawaguchi
 */
public class JenkinsLocationConfigurationTest {

    private String lastRootUrlReturned;
    private boolean lastRootUrlSet;

    @Rule
    public JenkinsRule j = new JenkinsRule() {
        @Override
        public URL getURL() throws IOException {
            // first call for the "Running on xxx" log message, Jenkins not being set at that point
            // and the second call is to set the rootUrl of the JLC inside the JenkinsRule#init
            if (Jenkins.getInstanceOrNull() != null) {
                // only useful for doNotAcceptNonHttpBasedRootURL_fromConfigXml
                lastRootUrlReturned = JenkinsLocationConfiguration.getOrDie().getUrl();
                lastRootUrlSet = true;
            }
            return super.getURL();
        }
    };

    /**
     * Makes sure the use of "localhost" in the Hudson URL reports a warning.
     */
    @Test
    public void localhostWarning() throws Exception {
        HtmlPage p = j.createWebClient().goTo("configure");
        HtmlInput url = p.getFormByName("config").getInputByName("_.url");
        url.setValue("http://localhost:1234/");
        assertThat(p.getDocumentElement().getTextContent(), containsString("instead of localhost"));
    }

    @Test
    @Issue("SECURITY-1471")
    public void doNotAcceptNonHttpBasedRootURL_fromUI() throws Exception {
        // in JenkinsRule, the URL is set to the current URL
        JenkinsLocationConfiguration.getOrDie().setUrl(null);

        JenkinsRule.WebClient wc = j.createWebClient();

        assertNull(JenkinsLocationConfiguration.getOrDie().getUrl());

        settingRootURL("javascript:alert(123);//");

        // no impact on the url in memory
        assertNull(JenkinsLocationConfiguration.getOrDie().getUrl());

        Path configFile = j.jenkins.getRootDir().toPath().resolve("jenkins.model.JenkinsLocationConfiguration.xml");
        String configFileContent = Files.readString(configFile, StandardCharsets.UTF_8);
        assertThat(configFileContent, containsString("JenkinsLocationConfiguration"));
        assertThat(configFileContent, not(containsString("javascript:alert(123);//")));
    }

    @Test
    @Issue("SECURITY-1471")
    public void escapeHatch_acceptNonHttpBasedRootURL_fromUI() throws Exception {
        boolean previousValue = JenkinsLocationConfiguration.DISABLE_URL_VALIDATION;
        JenkinsLocationConfiguration.DISABLE_URL_VALIDATION = true;

        try {
            // in JenkinsRule, the URL is set to the current URL
            JenkinsLocationConfiguration.getOrDie().setUrl(null);

            JenkinsRule.WebClient wc = j.createWebClient();

            assertNull(JenkinsLocationConfiguration.getOrDie().getUrl());

            String expectedUrl = "weirdSchema:somethingAlsoWeird";
            settingRootURL(expectedUrl);

            // the method ensures there is an trailing slash
            assertEquals(expectedUrl + "/", JenkinsLocationConfiguration.getOrDie().getUrl());

            Path configFile = j.jenkins.getRootDir().toPath().resolve("jenkins.model.JenkinsLocationConfiguration.xml");
            String configFileContent = Files.readString(configFile, StandardCharsets.UTF_8);
            assertThat(configFileContent, containsString("JenkinsLocationConfiguration"));
            assertThat(configFileContent, containsString(expectedUrl));
        }
        finally {
            JenkinsLocationConfiguration.DISABLE_URL_VALIDATION = previousValue;
        }
    }

    @Test
    @Issue("SECURITY-1471")
    @LocalData("xssThroughConfigXml")
    public void doNotAcceptNonHttpBasedRootURL_fromConfigXml() {
        // in JenkinsRule, the URL is set to the current URL, even if coming from LocalData
        // so we need to catch the last value before the getUrl from the JenkinsRule that will be used to set the rootUrl
        assertNull(lastRootUrlReturned);
        assertTrue(lastRootUrlSet);

        assertThat(JenkinsLocationConfiguration.getOrDie().getUrl(), not(containsString("javascript")));
    }

    @Test
    @Issue("SECURITY-1471")
    public void cannotInjectJavaScriptUsingRootUrl_inNewViewLink() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        j.createFreeStyleProject();

        settingRootURL("javascript:alert(123);//");

        // setup the victim
        AtomicReference<Boolean> alertAppeared = new AtomicReference<>(false);
        wc.setAlertHandler((page, s) -> alertAppeared.set(true));
        HtmlPage page = wc.goTo("");

        HtmlAnchor newViewLink = page.getDocumentElement().getElementsByTagName("a").stream()
                .filter(HtmlAnchor.class::isInstance).map(HtmlAnchor.class::cast)
                .filter(a -> a.getHrefAttribute().endsWith("newView"))
                .findFirst().orElseThrow(AssertionError::new);

        // last verification
        assertFalse(alertAppeared.get());

        HtmlElementUtil.click(newViewLink);

        assertFalse(alertAppeared.get());
    }

    @Test
    @Issue("SECURITY-1471")
    public void cannotInjectJavaScriptUsingRootUrl_inLabelAbsoluteLink() throws Exception {
        String builtInLabel = "builtin-node";
        j.jenkins.setLabelString(builtInLabel);

        JenkinsRule.WebClient wc = j.createWebClient();

        settingRootURL("javascript:alert(123);//");

        // setup the victim
        AtomicReference<Boolean> alertAppeared = new AtomicReference<>(false);
        wc.setAlertHandler((page, s) -> alertAppeared.set(true));

        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedLabel(Label.get(builtInLabel));

        HtmlPage projectConfigurePage = wc.getPage(p, "/configure");

        HtmlAnchor labelAnchor = projectConfigurePage.getDocumentElement().getElementsByTagName("a").stream()
                .filter(HtmlAnchor.class::isInstance).map(HtmlAnchor.class::cast)
                .filter(a -> a.getHrefAttribute().contains("/label/"))
                .findFirst().orElseThrow(AssertionError::new);

        assertFalse(alertAppeared.get());
        HtmlElementUtil.click(labelAnchor);
        assertFalse(alertAppeared.get());

        String labelHref = labelAnchor.getHrefAttribute();
        assertThat(labelHref, not(containsString("javascript:alert(123)")));

        String responseContent = projectConfigurePage.getWebResponse().getContentAsString();
        assertThat(responseContent, not(containsString("javascript:alert(123)")));
    }

    private void settingRootURL(String desiredRootUrl) throws Exception {
        HtmlPage configurePage = j.createWebClient().goTo("configure");
        HtmlForm configForm = configurePage.getFormByName("config");
        HtmlInput url = configForm.getInputByName("_.url");
        url.setValue(desiredRootUrl);
        HtmlFormUtil.submit(configForm);
    }
}
