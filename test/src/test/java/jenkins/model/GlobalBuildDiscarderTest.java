package jenkins.model;

import hudson.ExtensionList;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.LogRotator;
import hudson.util.DescribableList;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.junit.jupiter.api.Assertions;

public class GlobalBuildDiscarderTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @LocalData
    @Issue("JENKINS-61688")
    public void testLoading() throws Exception {
        Assertions.assertEquals(0, GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().size());
    }

    @Test
    @LocalData
    @Issue("JENKINS-61688")
    public void testLoadingWithDiscarders() throws Exception {
        final DescribableList<GlobalBuildDiscarderStrategy, GlobalBuildDiscarderStrategyDescriptor> configuredBuildDiscarders = GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders();
        Assertions.assertEquals(2, configuredBuildDiscarders.size());
        Assertions.assertNotNull(configuredBuildDiscarders.get(JobGlobalBuildDiscarderStrategy.class));
        Assertions.assertEquals(5, ((LogRotator) configuredBuildDiscarders.get(SimpleGlobalBuildDiscarderStrategy.class).getDiscarder()).getNumToKeep());
    }

    @Test
    public void testJobBuildDiscarder() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        { // no discarder
            j.buildAndAssertSuccess(p);
            j.buildAndAssertSuccess(p);
            j.buildAndAssertSuccess(p);
            j.buildAndAssertSuccess(p);
            j.buildAndAssertSuccess(p);
            Assertions.assertArrayEquals(p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), new int[]{5, 4, 3, 2, 1}, "all 5 builds exist");
        }

        { // job build discarder
            GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().add(new JobGlobalBuildDiscarderStrategy());
            p.setBuildDiscarder(new LogRotator(null, "3", null, null));
            Assertions.assertArrayEquals(p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), new int[]{5, 4, 3, 2, 1}, "all 5 builds exist");

            ExtensionList.lookupSingleton(BackgroundGlobalBuildDiscarder.class).execute(TaskListener.NULL);
            Assertions.assertArrayEquals(p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), new int[]{5, 4, 3}, "only 3 builds left");

            j.buildAndAssertSuccess(p);
            Assertions.assertArrayEquals(p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), new int[]{6, 5, 4}, "still only 3 builds");

            p.setBuildDiscarder(null);
            j.buildAndAssertSuccess(p);
            j.buildAndAssertSuccess(p);
            Assertions.assertArrayEquals(p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), new int[]{8, 7, 6, 5, 4}, "5 builds again");

            ExtensionList.lookupSingleton(BackgroundGlobalBuildDiscarder.class).execute(TaskListener.NULL);
            Assertions.assertArrayEquals(p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), new int[]{8, 7, 6, 5, 4}, "still 5 builds");
        }

        { // global build discarder
            GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().add(new SimpleGlobalBuildDiscarderStrategy(new LogRotator(null, "2", null, null)));
            ExtensionList.lookupSingleton(BackgroundGlobalBuildDiscarder.class).execute(TaskListener.NULL);
            Assertions.assertArrayEquals(p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), new int[]{8, 7}, "newest 2 builds");
            j.buildAndAssertSuccess(p);
            j.buildAndAssertSuccess(p);

            // run global discarders once a build finishes
            Assertions.assertArrayEquals(p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), new int[]{10, 9}, "2 builds because of BackgroundBuildDiscarderListener");

            GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().clear();
            GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().add(new SimpleGlobalBuildDiscarderStrategy(new LogRotator(null, "1", null, null)));

            // apply global config changes periodically
            ExtensionList.lookupSingleton(BackgroundGlobalBuildDiscarder.class).execute(TaskListener.NULL);
            Assertions.assertArrayEquals(p.getBuilds().stream().mapToInt(Run::getNumber).toArray(), new int[]{10}, "2 builds again");
        }

        // reset global config
        GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().clear();

        { // job and global build discarder
            FreeStyleProject p1 = j.createFreeStyleProject();
            j.buildAndAssertSuccess(p1);
            j.buildAndAssertSuccess(p1);
            j.buildAndAssertSuccess(p1);
            j.buildAndAssertSuccess(p1);
            j.buildAndAssertSuccess(p1);
            j.buildAndAssertSuccess(p1);
            j.buildAndAssertSuccess(p1);
            Assertions.assertArrayEquals(p1.getBuilds().stream().mapToInt(Run::getNumber).toArray(), new int[]{7, 6, 5, 4, 3, 2, 1}, "job with 5 builds");
            p1.setBuildDiscarder(new LogRotator(null, "5", null, null));

            FreeStyleProject p2 = j.createFreeStyleProject();
            j.buildAndAssertSuccess(p2);
            j.buildAndAssertSuccess(p2);
            j.buildAndAssertSuccess(p2);
            j.buildAndAssertSuccess(p2);
            j.buildAndAssertSuccess(p2);
            j.buildAndAssertSuccess(p2);
            Assertions.assertArrayEquals(p2.getBuilds().stream().mapToInt(Run::getNumber).toArray(), new int[]{6, 5, 4, 3, 2, 1}, "job with 3 builds");
            p2.setBuildDiscarder(new LogRotator(null, "3", null, null));

            GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().add(new SimpleGlobalBuildDiscarderStrategy(new LogRotator(null, "4", null, null)));
            GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().add(new JobGlobalBuildDiscarderStrategy());

            { // job 1 with builds more aggressively deleted by global strategy
                j.buildAndAssertSuccess(p1);
                j.buildAndAssertSuccess(p1);
                Assertions.assertArrayEquals(p1.getBuilds().stream().mapToInt(Run::getNumber).toArray(), new int[]{9, 8, 7, 6}, "job 1 discards down to 5, but global override is for 4");
                ExtensionList.lookupSingleton(BackgroundGlobalBuildDiscarder.class).execute(TaskListener.NULL);
                Assertions.assertArrayEquals(p1.getBuilds().stream().mapToInt(Run::getNumber).toArray(), new int[]{9, 8, 7, 6}, "job 1 discards down to 5, but global override is for 4");
            }

            { // job 2 with more aggressive local build discarder
                j.buildAndAssertSuccess(p2);
                Assertions.assertArrayEquals(p2.getBuilds().stream().mapToInt(Run::getNumber).toArray(), new int[]{7, 6, 5}, "job 1 discards down to 3");
            }
        }
    }
}
