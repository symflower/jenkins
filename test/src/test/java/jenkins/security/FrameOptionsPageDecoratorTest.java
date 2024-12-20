package jenkins.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

public class FrameOptionsPageDecoratorTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void defaultHeaderPresent() throws IOException, SAXException {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("");
        assertEquals("sameorigin", getFrameOptionsFromResponse(page.getWebResponse()), "Expected different X-Frame-Options value");
    }

    @Test
    public void testDisabledFrameOptions() throws IOException, SAXException {
        FrameOptionsPageDecorator.enabled = false;
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("");
        assertNull(getFrameOptionsFromResponse(page.getWebResponse()), "Expected X-Frame-Options unset");
    }

    private static String getFrameOptionsFromResponse(WebResponse response) {
        for (NameValuePair pair : response.getResponseHeaders()) {
            if (pair.getName().equals("X-Frame-Options")) {
                return pair.getValue();
            }
        }
        return null;
    }
}
