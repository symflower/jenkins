/*
 * The MIT License
 *
 * Copyright (c) 2021, Snap, Inc., Casey Duquette
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

package hudson.slaves;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.Slave;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.DOMReader;
import org.htmlunit.xml.XmlPage;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

/**
 * Tests of {@link JNLPLauncher} using a custom inbound agent url.
 */
public class AgentInboundUrlTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public InboundAgentRule inboundAgents = new InboundAgentRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(Slave.class, Level.FINE);

    // Override the inbound agent url
    private static final String customInboundUrl = "http://localhost:8080/jenkins";

    @Rule
    public final FlagRule<String> customInboundUrlRule = FlagRule.systemProperty(JNLPLauncher.CUSTOM_INBOUND_URL_PROPERTY, customInboundUrl);

    @Issue("JENKINS-63222")
    @Test
    public void testInboundAgentUrlOverride() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy authorizationStrategy = new MockAuthorizationStrategy();
        authorizationStrategy.grant(Jenkins.ADMINISTER).everywhere().toEveryone();
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);

        // Create an agent
        inboundAgents.createAgent(j, InboundAgentRule.Options.newBuilder().name("test").skipStart().build());

        // parse the JNLP page into DOM to inspect the jnlp url argument.
        JenkinsRule.WebClient agent = j.createWebClient();
        XmlPage jnlp = (XmlPage) agent.goTo("computer/test/jenkins-agent.jnlp", "application/x-java-jnlp-file");
        Document dom = new DOMReader().read(jnlp.getXmlDocument());
        Object arg = dom.selectSingleNode("//application-desc/argument[7]/following-sibling::argument[1]");
        String val = ((Element) arg).getText();
        assertEquals(customInboundUrl, val);
    }
}
