package jenkins.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.junit.jupiter.api.Assertions;

/**
 * Tests about the behavior expected setting different values in the escape-by-default directive and the
 * CustomJellyContext.ESCAPE_BY_DEFAULT field.
 */
public class Security857Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Test that a jelly is escaped right thanks to the CustomJellyContext.ESCAPE_BY_DEFAULT field. Its default value is true.
     */
    @Issue("SECURITY-857")
    @Test
    public void testJellyEscapingTrue() throws Exception {
        testJelly(true);
    }

    /**
     * Test that a jelly is not escaped when the escape-by-default='false' directive is set in it.
     */
    @Issue("SECURITY-857")
    @Test
    public void testJellyEscapingFalse() throws Exception {
        testJelly(false);
    }


    /**
     * Test that a jelly is escaped when the escape-by-default='true' directive is set in it.
     */
    @Issue("SECURITY-857")
    @Test
    public void testJellyEscapingDefault() throws Exception {
        testJelly(null);
    }

    private void testJelly(Boolean escape) throws Exception {
        String jelly = getJellyContent(escape);

        String response = parseJelly(jelly);

        checkResponse(response, escape);
    }

    /**
     * Get a jelly test string with the escape-by-default set or not depending on the escape parameter.
     * @param escape null to not set anything, true of false to set or not the escape-by-default directive respectively
     * @return The jelly test with the escape-by-default directive
     * @throws IOException if there are some exception reading the jelly test file.
     */
    private String getJellyContent(Boolean escape) throws IOException {
        String jelly = IOUtils.toString(this.getClass().getResourceAsStream("escape.jelly"), StandardCharsets.UTF_8);
        if (escape != null) {
            jelly = String.format("<?jelly escape-by-default='%s'?>%n%s", escape, jelly);
        }

        return jelly;
    }

    /**
     * Parse a jelly using the eval url.
     * @param jelly The jelly to parse
     * @return The content of the response web page.
     * @throws Exception if there are some exception during the requests.
     */
    private String parseJelly(String jelly) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        wc.login("admin");

        WebRequest req = new WebRequest(wc.createCrumbedUrl("eval"), HttpMethod.POST);
        req.setEncodingType(null);

        req.setRequestBody(jelly);
        WebResponse response = wc.getPage(req).getWebResponse();

        Assertions.assertEquals(200, response.getStatusCode());
        return response.getContentAsString();
    }

    /**
     * Check the response of the parse of the jelly. Depending on the escape parameter, the response page should have
     * escaped characters or unescaped characters.
     * @param response The response of the parse of the jelly.
     * @param escape How the escape-by-default directive was set. null: not set, true: set to true, false: set to false
     */
    private void checkResponse(String response, Boolean escape) {
        String evidence = "<script> alert";
        if (escape == null) {
            Assertions.assertFalse(response.contains(evidence), "There is no escape-by-default tag in the jelly (true is assumed) but there are unescaped characters in the response.");
        } else if (escape) {
            Assertions.assertFalse(response.contains(evidence), "Set explicitly the <?jelly escape-by-default='true' in the jelly but there are unescaped characters in the response. Jenkins is not escaping the characters and it should to.");
        } else {
            Assertions.assertTrue(response.contains(evidence), "Set explicitly the <?jelly escape-by-default='false' in the jelly but there are escaped characters in the response. Jenkins is escaping the characters and it shouldn't to.");
        }
    }
}
