package jenkins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ExtensionComponent;
import hudson.console.ConsoleAnnotatorFactory;
import hudson.model.PageDecorator;
import jenkins.install.SetupWizard;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExtensionFilterTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void filter() {
        assertThat(PageDecorator.all(), hasSize(1));
        assertTrue(ConsoleAnnotatorFactory.all().isEmpty());
    }

    @TestExtension("filter")
    public static class Impl extends ExtensionFilter {
        @Override
        public <T> boolean allows(Class<T> type, ExtensionComponent<T> component) {
            if (type == ConsoleAnnotatorFactory.class) {
                return false;
            }
            // SetupWizard is required during startup
            if (component.isDescriptorOf(PageDecorator.class) && !component.isDescriptorOf(SetupWizard.class)) {
                return false;
            }
            return true;
        }
    }
}
