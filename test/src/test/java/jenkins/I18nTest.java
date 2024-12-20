/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;

import java.io.IOException;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;
import org.junit.jupiter.api.Assertions;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class I18nTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void test_baseName_unspecified() throws IOException, SAXException {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle").getJSONObject();
        Assertions.assertEquals("error", response.getString("status"));
        Assertions.assertEquals("Mandatory parameter 'baseName' not specified.", response.getString("message"));
    }

    @Test
    public void test_baseName_unknown() throws IOException, SAXException {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle?baseName=com.acme.XyzWhatever").getJSONObject();
        Assertions.assertEquals("error", response.getString("status"));
        assertThat(response.getString("message"), startsWith("Can't find bundle for base name com.acme.XyzWhatever"));
    }

    @Issue("JENKINS-35270")
    @Test
    public void test_baseName_plugin() throws Exception {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle?baseName=org.jenkinsci.plugins.matrixauth.Messages").getJSONObject();
        Assertions.assertEquals("ok", response.getString("status"), response.toString());
        JSONObject data = response.getJSONObject("data");
        Assertions.assertEquals("Matrix-based security", data.getString("GlobalMatrixAuthorizationStrategy.DisplayName"));
    }

    @Test
    public void test_valid() throws IOException, SAXException {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle?baseName=hudson.logging.Messages&language=de").getJSONObject();
        Assertions.assertEquals("ok", response.getString("status"));
        JSONObject data = response.getJSONObject("data");
        Assertions.assertEquals("Initialisiere Log-Rekorder", data.getString("LogRecorderManager.init"));
    }

    @Issue("JENKINS-39034")
    @Test // variant testing
    public void test_valid_region_variant() throws IOException, SAXException {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle?baseName=jenkins.i18n.Messages&language=en_AU_variant").getJSONObject();
        Assertions.assertEquals("ok", response.getString("status"));
        JSONObject data = response.getJSONObject("data");
        Assertions.assertEquals("value_au_variant", data.getString("Key"));
    }

    @Issue("JENKINS-39034")
    @Test //country testing with delimiter '-' instead of '_'
    public void test_valid_region() throws IOException, SAXException {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle?baseName=jenkins.i18n.Messages&language=en-AU").getJSONObject();
        Assertions.assertEquals("ok", response.getString("status"));
        JSONObject data = response.getJSONObject("data");
        Assertions.assertEquals("value_au", data.getString("Key"));
    }

    @Issue("JENKINS-39034")
    @Test //fallthrough to default language if variant does not exit
    public void test_valid_fallback() throws IOException, SAXException {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle?baseName=jenkins.i18n.Messages&language=en_NZ_variant").getJSONObject();
        Assertions.assertEquals("ok", response.getString("status"));
        JSONObject data = response.getJSONObject("data");
        Assertions.assertEquals("value", data.getString("Key"));
    }

    @Test // testing with unknown language falls through to default language
    public void test_unsupported_language() throws IOException, SAXException {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle?baseName=jenkins.i18n.Messages&language=xyz").getJSONObject();
        Assertions.assertEquals("ok", response.getString("status"));
        JSONObject data = response.getJSONObject("data");
        Assertions.assertEquals("value", data.getString("Key"));
    }

}
