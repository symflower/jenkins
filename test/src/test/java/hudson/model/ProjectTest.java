/*
 * The MIT License
 *
 * Copyright 2013 Red Hat, Inc.
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

package hudson.model;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Launcher.RemoteLauncher;
import hudson.model.AbstractProject.BecauseOfDownstreamBuildInProgress;
import hudson.model.AbstractProject.BecauseOfUpstreamBuildInProgress;
import hudson.model.Cause.UserIdCause;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.SubTask;
import hudson.model.queue.SubTaskContributor;
import hudson.scm.NullSCM;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.slaves.Cloud;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProvisioner;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BatchFile;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Shell;
import hudson.triggers.SCMTrigger;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import jenkins.model.BlockedBecauseOfBuildInProgress;
import jenkins.model.Jenkins;
import jenkins.model.WorkspaceWriter;
import jenkins.scm.DefaultSCMCheckoutStrategyImpl;
import jenkins.scm.SCMCheckoutStrategy;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.javascript.host.event.Event;
import org.junit.jupiter.api.Disabled;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.test.FakeChangeLogSCM;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest2;

/**
 *
 * @author Lucie Votypkova
 */
public class ProjectTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Rule public InboundAgentRule inboundAgents = new InboundAgentRule();

    public static boolean createAction = false;
    public static boolean getFilePath = false;
    public static boolean createSubTask = false;

    @Test
    public void testSave() throws IOException, InterruptedException, ReactorException {
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.disabled = true;
        p.nextBuildNumber = 5;
        p.description = "description";
        p.save();
        j.jenkins.reload();
        assertEquals("description", p.description, "All persistent data should be saved.");
        assertEquals(5, p.nextBuildNumber, "All persistent data should be saved.");
        assertTrue(p.disabled, "All persistent data should be saved");
    }

    @Test
    public void testOnCreateFromScratch() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        j.buildAndAssertSuccess(p);
        p.removeRun(p.getLastBuild());
        createAction = true;
        p.onCreatedFromScratch();
        assertNotNull(p.getLastBuild(), "Project should have last build.");
        assertNotNull(p.getAction(TransientAction.class), "Project should have transient action TransientAction.");
        createAction = false;
    }

    @Test
    public void testOnLoad() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        j.buildAndAssertSuccess(p);
        p.removeRun(p.getLastBuild());
        createAction = true;
        p.onLoad(j.jenkins, "project");
        assertNotNull(p.getLastBuild(), "Project should have a build.");
        assertNotNull(p.getScm(), "Project should have a scm.");
        assertNotNull(p.getAction(TransientAction.class), "Project should have Transient Action TransientAction.");
        createAction = false;
    }

    @Test
    public void testGetEnvironment() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        Slave slave = j.createOnlineSlave();
        EnvironmentVariablesNodeProperty.Entry entry = new EnvironmentVariablesNodeProperty.Entry("jdk", "some_java");
        slave.getNodeProperties().add(new EnvironmentVariablesNodeProperty(entry));
        EnvVars var = p.getEnvironment(slave, TaskListener.NULL);
        assertEquals("some_java", var.get("jdk"), "Environment should have set jdk.");
    }

    @Test
    public void testPerformDelete() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.performDelete();
        assertFalse(p.getConfigFile().exists(), "Project should be deleted from disk.");
        assertTrue(p.isDisabled(), "Project should be disabled when deleting start.");
    }

    @Test
    public void testGetAssignedLabel() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.setAssignedLabel(j.jenkins.getSelfLabel());
        Slave slave = j.createOnlineSlave();
        assertEquals(j.jenkins.getSelfLabel(), p.getAssignedLabel(), "Project should have Jenkins's self label.");
        p.setAssignedLabel(null);
        assertNull(p.getAssignedLabel(), "Project should not have any label.");
        p.setAssignedLabel(slave.getSelfLabel());
        assertEquals(slave.getSelfLabel(), p.getAssignedLabel(), "Project should have self label of slave");
    }

    @Test
    public void testGetAssignedLabelString() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        Slave slave = j.createOnlineSlave();
        assertNull(p.getAssignedLabelString(), "Project should not have any label.");
        p.setAssignedLabel(j.jenkins.getSelfLabel());
        assertNull(p.getAssignedLabelString(), "Project should return null, because assigned label is Jenkins.");
        p.setAssignedLabel(slave.getSelfLabel());
        assertEquals(slave.getSelfLabel().name, p.getAssignedLabelString(), "Project should return name of slave.");
    }


    @Test
    public void testGetSomeWorkspace() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        assertNull(p.getSomeWorkspace(), "Project which has never run should not have any workspace.");
        getFilePath = true;
        assertNotNull(p.getSomeWorkspace(), "Project should have any workspace because WorkspaceBrowser find some.");
        getFilePath = false;
        String cmd = "echo ahoj > some.log";
        p.getBuildersList().add(Functions.isWindows() ? new BatchFile(cmd) : new Shell(cmd));
        j.buildAndAssertSuccess(p);
        assertNotNull(p.getSomeWorkspace(), "Project should has any workspace.");
    }

    @Test
    public void testGetSomeBuildWithWorkspace() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        String cmd = "echo ahoj > some.log";
        p.getBuildersList().add(Functions.isWindows() ? new BatchFile(cmd) : new Shell(cmd));
        assertNull(p.getSomeBuildWithWorkspace(), "Project which has never run should not have any build with workspace.");
        j.buildAndAssertSuccess(p);
        assertEquals(p.getLastBuild(), p.getSomeBuildWithWorkspace(), "Last build should have workspace.");
        p.getLastBuild().delete();
        assertNull(p.getSomeBuildWithWorkspace(), "Project should not have build with some workspace.");
    }

    @Issue("JENKINS-10450")
    @Test public void workspaceBrowsing() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        String cmd = "echo ahoj > some.log";
        p.getBuildersList().add(Functions.isWindows() ? new BatchFile(cmd) : new Shell(cmd));
        j.buildAndAssertSuccess(p);
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.goTo("job/project/ws/some.log", "text/plain");
        wc.assertFails("job/project/ws/other.log", 404);
        p.doDoWipeOutWorkspace();
        wc.assertFails("job/project/ws/some.log", 404);
    }

    @Test
    public void testGetQuietPeriod() throws IOException {
        FreeStyleProject p = j.createFreeStyleProject("project");
        assertEquals(j.jenkins.getQuietPeriod(), p.getQuietPeriod(), "Quiet period should be default.");
        j.jenkins.setQuietPeriod(0);
        assertEquals(0, p.getQuietPeriod(), "Quiet period is not set so it should be the same as global quiet period.");
        p.setQuietPeriod(10);
        assertEquals(10, p.getQuietPeriod(), "Quiet period was set.");
    }

    @Test
    public void testGetScmCheckoutStrategy() throws IOException {
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.setScmCheckoutStrategy(null);
        assertThat("Project should return default checkout strategy if scm checkout strategy is not set.", p.getScmCheckoutStrategy(), instanceOf(DefaultSCMCheckoutStrategyImpl.class));
        SCMCheckoutStrategy strategy = new SCMCheckoutStrategyImpl();
        p.setScmCheckoutStrategy(strategy);
        assertEquals(strategy, p.getScmCheckoutStrategy(), "Project should return its scm checkout strategy if this strategy is not null");
    }

    @Test
    public void testGetScmCheckoutRetryCount() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        assertEquals(j.jenkins.getScmCheckoutRetryCount(), p.getScmCheckoutRetryCount(), "Scm retry count should be default.");
        j.jenkins.setScmCheckoutRetryCount(6);
        assertEquals(6, p.getScmCheckoutRetryCount(), "Scm retry count should be the same as global scm retry count.");
        HtmlForm form = j.createWebClient().goTo(p.getUrl() + "/configure").getFormByName("config");
        ((HtmlElement) form.querySelectorAll(".advancedButton").get(0)).click();
        // required due to the new default behavior of click
        form.getInputByName("hasCustomScmCheckoutRetryCount").click(new Event(), false, false, false, true);
        form.getInputByName("scmCheckoutRetryCount").setValue("7");
        j.submit(form);
        assertEquals(7, p.getScmCheckoutRetryCount(), "Scm retry count was set.");
    }

    @Test
    public void isBuildable() throws IOException {
        FreeStyleProject p = j.createFreeStyleProject("project");
        assertTrue(p.isBuildable(), "Project should be buildable.");
        p.disable();
        assertFalse(p.isBuildable(), "Project should not be buildable if it is disabled.");
        p.enable();
        AbstractProject p2 = (AbstractProject) j.jenkins.copy(j.jenkins.getItem("project"), "project2");
        assertFalse(p2.isBuildable(), "Project should not be buildable until is saved.");
        p2.save();
        assertTrue(p2.isBuildable(), "Project should be buildable after save.");
    }

    @Test
    public void testMakeDisabled() throws IOException {
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.makeDisabled(false);
        assertFalse(p.isDisabled(), "Project should be enabled.");
        p.makeDisabled(true);
        assertTrue(p.isDisabled(), "Project should be disabled.");
        p.makeDisabled(false);
        p.setAssignedLabel(j.jenkins.getLabel("nonExist"));
        p.scheduleBuild2(0);
        p.makeDisabled(true);
        assertNull(Queue.getInstance().getItem(p), "Project should be canceled.");
    }

    @Test
    public void testAddProperty() throws IOException {
        FreeStyleProject p = j.createFreeStyleProject("project");
        JobProperty prop = new JobPropertyImp();
        createAction = true;
        p.addProperty(prop);
        assertNotNull(p.getProperty(prop.getClass()), "Project does not contain added property.");
        assertNotNull(p.getAction(TransientAction.class), "Project did not update transient actions.");
    }

    @Test
    public void testScheduleBuild2() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.setAssignedLabel(j.jenkins.getLabel("nonExist"));
        p.scheduleBuild(0, new UserIdCause());
        assertNotNull(Queue.getInstance().getItem(p), "Project should be in queue.");
        p.setAssignedLabel(null);
        int count = 0;
        while (count < 5 && p.getLastBuild() == null) {
            Thread.sleep(1000); //give some time to start build
            count++;
        }
        FreeStyleBuild b = p.getLastBuild();
        assertNotNull(b, "Build should be done or in progress.");
        j.assertBuildStatusSuccess(j.waitForCompletion(b));
    }


    @Test
    public void testSchedulePolling() throws IOException {
        FreeStyleProject p = j.createFreeStyleProject("project");
        assertFalse(p.schedulePolling(), "Project should not schedule polling because no scm trigger is set.");
        SCMTrigger trigger = new SCMTrigger("0 0 * * *");
        p.addTrigger(trigger);
        trigger.start(p, true);
        assertTrue(p.schedulePolling(), "Project should schedule polling.");
        p.disable();
        assertFalse(p.schedulePolling(), "Project should not schedule polling because project is disabled.");
    }

    @Test
    public void testSaveAfterSet() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.setScm(new NullSCM());
        p.setScmCheckoutStrategy(new SCMCheckoutStrategyImpl());
        p.setQuietPeriod(15);
        p.setBlockBuildWhenDownstreamBuilding(true);
        p.setBlockBuildWhenUpstreamBuilding(true);
        j.jenkins.getJDKs().add(new JDK("jdk", "path"));
        j.jenkins.save();
        p.setJDK(j.jenkins.getJDK("jdk"));
        p.setCustomWorkspace("/some/path");
        j.jenkins.reload();
        assertNotNull(p.getScm(), "Project did not save scm.");
        assertThat("Project did not save scm checkout strategy.", p.getScmCheckoutStrategy(), instanceOf(SCMCheckoutStrategyImpl.class));
        assertEquals(15, p.getQuietPeriod(), "Project did not save quiet period.");
        assertTrue(p.blockBuildWhenDownstreamBuilding(), "Project did not save block if downstream is building.");
        assertTrue(p.blockBuildWhenUpstreamBuilding(), "Project did not save block if upstream is building.");
        assertNotNull(p.getJDK(), "Project did not save jdk");
        assertEquals("/some/path", p.getCustomWorkspace(), "Project did not save custom workspace.");
    }

    @Test
    public void testGetActions() throws IOException {
        FreeStyleProject p = j.createFreeStyleProject("project");
        createAction = true;
        p.updateTransientActions();
        assertNotNull(p.getAction(TransientAction.class), "Action should contain transient actions too.");
        createAction = false;
    }

// for debugging
//    static {
//        Logger.getLogger("").getHandlers()[0].setFormatter(new MilliSecLogFormatter());
//    }

    @Test
    public void testGetCauseOfBlockage() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.getBuildersList().add(Functions.isWindows() ? new BatchFile("ping -n 10 127.0.0.1 >nul") : new Shell("sleep 10"));
        QueueTaskFuture<FreeStyleBuild> b1 = waitForStart(p);
        assertThat("Build can not start because previous build has not finished: " + p.getCauseOfBlockage(), p.getCauseOfBlockage(), instanceOf(BlockedBecauseOfBuildInProgress.class));
        p.getLastBuild().getExecutor().interrupt();
        b1.get();   // wait for it to finish

        FreeStyleProject downstream = j.createFreeStyleProject("project-downstream");
        downstream.getBuildersList().add(Functions.isWindows() ? new BatchFile("ping -n 10 127.0.0.1 >nul") : new Shell("sleep 10"));
        p.getPublishersList().add(new BuildTrigger(Set.of(downstream), Result.SUCCESS));
        Jenkins.get().rebuildDependencyGraph();
        p.setBlockBuildWhenDownstreamBuilding(true);
        QueueTaskFuture<FreeStyleBuild> b2 = waitForStart(downstream);
        assertThat("Build can not start because build of downstream project has not finished.", p.getCauseOfBlockage(), instanceOf(BecauseOfDownstreamBuildInProgress.class));
        downstream.getLastBuild().getExecutor().interrupt();
        b2.get();

        downstream.setBlockBuildWhenUpstreamBuilding(true);
        QueueTaskFuture<FreeStyleBuild> b3 = waitForStart(p);
        assertThat("Build can not start because build of upstream project has not finished.", downstream.getCauseOfBlockage(), instanceOf(BecauseOfUpstreamBuildInProgress.class));
        b3.get();
        assertTrue(j.jenkins.getQueue().cancel(downstream));
    }

    private static final Logger LOGGER = Logger.getLogger(ProjectTest.class.getName());

    private QueueTaskFuture<FreeStyleBuild> waitForStart(FreeStyleProject p) throws InterruptedException, ExecutionException {
        long start = System.nanoTime();
        LOGGER.info("Scheduling " + p);
        QueueTaskFuture<FreeStyleBuild> f = p.scheduleBuild2(0);
        f.waitForStart();
        LOGGER.info("Wait:" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return f;
    }

    @Test
    public void testGetSubTasks() throws IOException {
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.addProperty(new JobPropertyImp());
        createSubTask = true;
        List<SubTask> subtasks = p.getSubTasks();
        boolean containsSubTaskImpl = false;
        boolean containsSubTaskImpl2 = false;
        for (SubTask sub : subtasks) {
            if (sub instanceof SubTaskImpl)
                containsSubTaskImpl = true;
            if (sub instanceof SubTaskImpl2)
                containsSubTaskImpl2 = true;
        }
        createSubTask = false;
        assertTrue(containsSubTaskImpl2, "Project should return subtasks provided by SubTaskContributor.");
        assertTrue(containsSubTaskImpl, "Project should return subtasks provided by JobProperty.");

    }

    @Test
    public void testCreateExecutable() throws IOException {
        FreeStyleProject p = j.createFreeStyleProject("project");
        Build build = p.createExecutable();
        assertNotNull(build, "Project should create executable.");
        assertEquals(build, p.getLastBuild(), "CreatedExecutable should be the last build.");
        assertEquals(2, p.nextBuildNumber, "Next build number should be increased.");
        p.disable();
        build = p.createExecutable();
        assertNull(build, "Disabled project should not create executable.");
        assertEquals(2, p.nextBuildNumber, "Next build number should not be increased.");

    }

    @Test
    public void testCheckout() throws Exception {
        SCM scm = new NullSCM();
        FreeStyleProject p = j.createFreeStyleProject("project");
        Slave slave = j.createOnlineSlave();
        AbstractBuild build = p.createExecutable();
        FilePath ws = slave.getWorkspaceFor(p);
        assertNotNull(ws);
        FilePath path = slave.toComputer().getWorkspaceList().allocate(ws, build).path;
        build.setWorkspace(path);
        BuildListener listener = new StreamBuildListener(TaskListener.NULL.getLogger(), Charset.defaultCharset());
        assertTrue(p.checkout(build, new RemoteLauncher(listener, slave.getChannel(), true), listener, new File(build.getRootDir(), "changelog.xml")), "Project with null smc should perform checkout without problems.");
        p.setScm(scm);
        assertTrue(p.checkout(build, new RemoteLauncher(listener, slave.getChannel(), true), listener, new File(build.getRootDir(), "changelog.xml")), "Project should perform checkout without problems.");
    }

    @Disabled("randomly failed: Project should have polling result no change expected:<NONE> but was:<INCOMPARABLE>")
    @Test
    public void testPoll() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        SCM scm = new NullSCM();
        p.setScm(null);
        assertEquals(PollingResult.Change.NONE, p.poll(TaskListener.NULL).change, "Project with null scm should have have polling result no change.");
        p.setScm(scm);
        p.disable();
        assertEquals(PollingResult.Change.NONE, p.poll(TaskListener.NULL).change, "Project which is disabled should have have polling result no change.");
        p.enable();
        assertEquals(PollingResult.Change.INCOMPARABLE, p.poll(TaskListener.NULL).change, "Project which has no builds should have have polling result incomparable.");
        p.setAssignedLabel(j.jenkins.getLabel("nonExist"));
        p.scheduleBuild2(0);
        assertEquals(PollingResult.Change.NONE, p.poll(TaskListener.NULL).change, "Project which build is building should have polling result result no change.");
        p.setAssignedLabel(null);
        while (p.getLastBuild() == null)
            Thread.sleep(100); //wait until build start
        assertEquals(PollingResult.Change.NONE, p.poll(TaskListener.NULL).change, "Project should have polling result no change");
        SCM alwaysChange = new AlwaysChangedSCM();
        p.setScm(alwaysChange);
        j.buildAndAssertSuccess(p);
        assertEquals(PollingResult.Change.SIGNIFICANT, p.poll(TaskListener.NULL).change, "Project should have polling result significant");
    }

    @Test
    public void testHasParticipant() throws Exception {
        User user = User.get("John Smith", true, Collections.emptyMap());
        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleProject project2 = j.createFreeStyleProject("project2");
        FakeChangeLogSCM scm = new FakeChangeLogSCM();
        project2.setScm(scm);
        j.buildAndAssertSuccess(project2);
        assertFalse(project2.hasParticipant(user), "Project should not have any participant.");
        scm.addChange().withAuthor(user.getId());
        project.setScm(scm);
        j.buildAndAssertSuccess(project);
        assertTrue(project.hasParticipant(user), "Project should have participant.");
    }

    @Test
    public void testGetRelationship() throws Exception {
        final FreeStyleProject upstream = j.createFreeStyleProject("upstream");
        FreeStyleProject downstream = j.createFreeStyleProject("downstream");
        j.buildAndAssertSuccess(upstream);
        j.buildAndAssertSuccess(upstream);
        j.buildAndAssertSuccess(downstream);
        assertTrue(upstream.getRelationship(downstream).isEmpty(), "Project upstream should not have any relationship with downstream");

        upstream.getPublishersList().add(new Fingerprinter("change.log", true));
        upstream.getBuildersList().add(new WorkspaceWriter("change.log", "hello"));
        upstream.getPublishersList().add(new ArtifactArchiver("change.log"));
        downstream.getPublishersList().add(new Fingerprinter("change.log", false));
        downstream.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                for (Run<?, ?>.Artifact a : upstream.getLastBuild().getArtifacts()) {
                    try {
                        Files.copy(a.getFile().toPath(), new File(build.getWorkspace().child(a.getFileName()).getRemote()).toPath(), REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                return true;
            }
        });

        j.buildAndAssertSuccess(upstream);
        j.buildAndAssertSuccess(downstream);
        j.buildAndAssertSuccess(upstream);
        j.buildAndAssertSuccess(downstream);
        upstream.getBuildersList().add(new WorkspaceWriter("change.log", "helloWorld"));
        j.buildAndAssertSuccess(upstream);
        j.buildAndAssertSuccess(downstream);

        Map<Integer, Fingerprint.RangeSet> relationship = upstream.getRelationship(downstream);
        assertFalse(relationship.isEmpty(), "Project upstream should have relationship with downstream");
        assertTrue(relationship.containsKey(3), "Relationship should contain upstream #3");
        assertFalse(relationship.containsKey(4), "Relationship should not contain upstream #4 because previous fingerprinted file was not changed since #3");
        assertEquals(2, relationship.get(3).min(), "downstream #2 should be the first build which depends on upstream #3");
        assertEquals(3, relationship.get(3).max() - 1, "downstream #3 should be the last build which depends on upstream #3");
        assertEquals(4, relationship.get(5).min(), "downstream #4 should depend only on upstream #5");
        assertEquals(4, relationship.get(5).max() - 1, "downstream #4 should depend only on upstream #5");
    }

    @Test
    public void testDoCancelQueue() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("project");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith", "password");
        try (ACLContext as = ACL.as(user)) {
            assertThrows(AccessDeniedException3.class, () -> project.doCancelQueue(null, null), "User should not have permission to build project");
        }
    }

    @Test
    public void testDoDoDelete() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("project");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User user = User.getById("john", true);
        try (ACLContext as = ACL.as(user)) {
            assertThrows(AccessDeniedException3.class, () -> project.doDoDelete((StaplerRequest2) null, null), "User should not have permission to build project");
        }
        auth.add(Jenkins.READ, user.getId());
        auth.add(Item.READ, user.getId());
        auth.add(Item.DELETE, user.getId());

        // use Basic to speedup the test, normally it's pure UI testing
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials(user.getId());
        HtmlPage p = wc.goTo(project.getUrl() + "delete");

        List<HtmlForm> forms = p.getForms();
        for (HtmlForm form : forms) {
            if ("doDelete".equals(form.getAttribute("action"))) {
                j.submit(form);
            }
        }
        assertNull(j.jenkins.getItem(project.getDisplayName()), "Project should be deleted form memory.");
        assertFalse(project.getRootDir().exists(), "Project should be deleted form disk.");
    }

    @Test
    public void testDoDoWipeOutWorkspace() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("project");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith", "password");
        try (ACLContext as = ACL.as(user)) {
            assertThrows(AccessDeniedException3.class, project::doDoWipeOutWorkspace, "User should not have permission to build project");
        }
        auth.add(Item.READ, user.getId());
        auth.add(Item.BUILD, user.getId());
        auth.add(Item.WIPEOUT, user.getId());
        auth.add(Jenkins.READ, user.getId());
        Slave slave = j.createOnlineSlave();
        project.setAssignedLabel(slave.getSelfLabel());
        String cmd = "echo hello > change.log";
        project.getBuildersList().add(Functions.isWindows() ? new BatchFile(cmd) : new Shell(cmd));
        j.buildAndAssertSuccess(project);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials(user.getId(), "password");
        WebRequest request = new WebRequest(new URI(wc.getContextPath() + project.getUrl() + "doWipeOutWorkspace").toURL(), HttpMethod.POST);
        HtmlPage p = wc.getPage(request);
        assertEquals(200, p.getWebResponse().getStatusCode());

        Thread.sleep(500);
        assertFalse(project.getSomeWorkspace().exists(), "Workspace should not exist.");
    }

    @Test
    public void testDoDisable() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("project");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith", "password");
        try (ACLContext as = ACL.as(user)) {
            assertThrows(AccessDeniedException3.class, project::doDisable, "User should not have permission to build project");
        }
        auth.add(Item.READ, user.getId());
        auth.add(Item.CONFIGURE, user.getId());
        auth.add(Jenkins.READ, user.getId());

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials(user.getId(), "password");

        HtmlPage p = wc.getPage(project, "configure");
        HtmlForm form = p.getFormByName("config");
        form.getInputByName("enable").click();
        j.submit(form);

        assertTrue(project.isDisabled(), "Project should be disabled.");
    }

    @Test
    public void testDoEnable() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("project");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith", "password");
        try (ACLContext as = ACL.as(user)) {
            project.disable();
        }
        try (ACLContext as = ACL.as(user)) {
            assertThrows(AccessDeniedException3.class, project::doEnable, "User should not have permission to build project");
        }
        auth.add(Item.READ, user.getId());
        auth.add(Item.CONFIGURE, user.getId());
        auth.add(Jenkins.READ, user.getId());

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials(user.getId(), "password");
        HtmlPage p = wc.goTo(project.getUrl());

        List<HtmlForm> forms = p.getForms();
        for (HtmlForm form : forms) {
            if ("enable".equals(form.getAttribute("action"))) {
                j.submit(form);
            }
        }
       assertFalse(project.isDisabled(), "Project should be enabled.");
    }

    /**
     * Job is un-restricted (no nabel), this is submitted to queue, which spawns an on demand slave
     */
    @Test
    public void testJobSubmittedShouldSpawnCloud() throws Exception {
        /*
         * Setup a project with an SCM. Jenkins should have no executors in itself.
         */
        FreeStyleProject proj = j.createFreeStyleProject("JENKINS-21394-spawn");
        RequiresWorkspaceSCM requiresWorkspaceScm = new RequiresWorkspaceSCM(true);
        proj.setScm(requiresWorkspaceScm);
        j.jenkins.setNumExecutors(0);
        /*
         * We have a cloud
         */
        DummyCloudImpl2 c2 = new DummyCloudImpl2(j, 0);
        c2.label = new LabelAtom("test-cloud-label");
        j.jenkins.clouds.add(c2);

        SCMTrigger t = new SCMTrigger("@daily", true);
        t.start(proj, true);
        proj.addTrigger(t);
        t.new Runner().run();

        Thread.sleep(1000);
        //Assert that the job IS submitted to Queue.
        assertEquals(1, j.jenkins.getQueue().getItems().length);
        assertTrue(j.jenkins.getQueue().cancel(proj));
    }

    /**
     * Job is restricted, but label can not be provided by any cloud, only normal agents. Then job will not submit, because no slave is available.
     */
    @Test
    public void testUnrestrictedJobNoLabelByCloudNoQueue() throws Exception {
        assertTrue(j.jenkins.clouds.isEmpty());
        //Create slave. (Online)
        Slave s1 = j.createOnlineSlave();

        //Create a project, and bind the job to the created slave
        FreeStyleProject proj = j.createFreeStyleProject("JENKINS-21394-noqueue");
        proj.setAssignedLabel(s1.getSelfLabel());

        //Add an SCM to the project. We require a workspace for the poll
        RequiresWorkspaceSCM requiresWorkspaceScm = new RequiresWorkspaceSCM(true);
        proj.setScm(requiresWorkspaceScm);

        j.buildAndAssertSuccess(proj);

        //Now create another slave. And restrict the job to that slave. The slave is offline, leaving the job with no assignable nodes.
        //We tell our mock SCM to return that it has got changes. But since there are no agents, we get the desired result.
        Slave s2 = inboundAgents.createAgent(j, InboundAgentRule.Options.newBuilder().skipStart().build());
        proj.setAssignedLabel(s2.getSelfLabel());
        requiresWorkspaceScm.hasChange = true;

        //Poll (We now should have NO online agents, this should now return NO_CHANGES.
        PollingResult pr = proj.poll(j.createTaskListener());
        assertFalse(pr.hasChanges());

        SCMTrigger t = new SCMTrigger("@daily", true);
        t.start(proj, true);
        proj.addTrigger(t);

        t.new Runner().run();

        /*
         * Assert that the log contains the correct message.
         */
        HtmlPage log = j.createWebClient().getPage(proj, "scmPollLog");
        String logastext = log.asNormalizedText();
        assertThat(logastext, containsString("(" + AbstractProject.WorkspaceOfflineReason.all_suitable_nodes_are_offline.name() + ")"));

    }

    /**
     * Job is restricted. Label is on slave that can be started in cloud. Job is submitted to queue, which spawns an on demand slave.
     */
    @Test
    public void testRestrictedLabelOnSlaveYesQueue() throws Exception {
        FreeStyleProject proj = j.createFreeStyleProject("JENKINS-21394-yesqueue");
        RequiresWorkspaceSCM requiresWorkspaceScm = new RequiresWorkspaceSCM(true);
        proj.setScm(requiresWorkspaceScm);
        j.jenkins.setNumExecutors(0);

        /*
         * We have a cloud
         */
        DummyCloudImpl2 c2 = new DummyCloudImpl2(j, 0);
        c2.label = new LabelAtom("test-cloud-label");
        j.jenkins.clouds.add(c2);
        proj.setAssignedLabel(c2.label);

        SCMTrigger t = new SCMTrigger("@daily", true);
        t.start(proj, true);
        proj.addTrigger(t);
        t.new Runner().run();

        Thread.sleep(1000);
        //The job should be in queue
        assertEquals(1, j.jenkins.getQueue().getItems().length);
        assertTrue(j.jenkins.getQueue().cancel(proj));
    }

    @Issue("JENKINS-22750")
    @Test
    public void testMasterJobPutInQueue() throws Exception {
        FreeStyleProject proj = j.createFreeStyleProject("JENKINS-21394-yes-master-queue");
        RequiresWorkspaceSCM requiresWorkspaceScm = new RequiresWorkspaceSCM(true);
        proj.setAssignedLabel(null);
        proj.setScm(requiresWorkspaceScm);
        j.jenkins.setNumExecutors(1);
        proj.setScm(requiresWorkspaceScm);

        //First build is not important
        j.buildAndAssertSuccess(proj);

        SCMTrigger t = new SCMTrigger("@daily", true);
        t.start(proj, true);
        proj.addTrigger(t);
        t.new Runner().run();


        assertEquals(1, j.jenkins.getQueue().getItems().length);
        assertTrue(j.jenkins.getQueue().cancel(proj));
    }

    public static class TransientAction extends InvisibleAction{

    }

    @TestExtension
    public static class TransientActionFactoryImpl extends TransientProjectActionFactory {

        @Override
        public Collection<? extends Action> createFor(AbstractProject target) {
            List<Action> actions = new ArrayList<>();
            if (createAction)
                actions.add(new TransientAction());
            return actions;
        }

    }

    @TestExtension
    public static class RequiresWorkspaceSCM extends NullSCM {

        public boolean hasChange = false;

        public RequiresWorkspaceSCM() { }

        public RequiresWorkspaceSCM(boolean hasChange) {
            this.hasChange = hasChange;
        }

        @Override
        public boolean pollChanges(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace, TaskListener listener) {
            return hasChange;
        }

        @Override
        public boolean requiresWorkspaceForPolling() {
            return true;
        }

        @Override public SCMDescriptor<?> getDescriptor() {
            return new SCMDescriptor<>(null) {};
        }

        @Override
        protected PollingResult compareRemoteRevisionWith(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) {
            if (!hasChange) {
                return PollingResult.NO_CHANGES;
            }
            return PollingResult.SIGNIFICANT;
        }
    }

    @TestExtension
    public static class AlwaysChangedSCM extends NullSCM {

        @Override
        public boolean pollChanges(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace, TaskListener listener) {
            return true;
        }

        @Override
        public boolean requiresWorkspaceForPolling() {
            return false;
        }

        @Override
        protected PollingResult compareRemoteRevisionWith(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) {
            return PollingResult.SIGNIFICANT;
        }

    }

    @TestExtension
    public static class WorkspaceBrowserImpl extends WorkspaceBrowser {

        @Override
        public FilePath getWorkspace(Job job) {
            if (getFilePath)
                return new FilePath(new File("some_file_path"));
            return null;
        }

    }


    public static class SCMCheckoutStrategyImpl extends DefaultSCMCheckoutStrategyImpl implements Serializable {

        public SCMCheckoutStrategyImpl() {

        }

    }

    public static class JobPropertyImp extends JobProperty {

        @Override
        public Collection getSubTasks() {
            ArrayList<SubTask> list = new ArrayList<>();
            list.add(new SubTaskImpl());
            return list;
        }


    }

    @TestExtension
    public static class SubTaskContributorImpl extends SubTaskContributor {

        @Override
        public Collection<? extends SubTask> forProject(AbstractProject<?, ?> p) {
            ArrayList<SubTask> list = new ArrayList<>();
            if (createSubTask) {
                list.add(new SubTaskImpl2());
            }
            return list;
        }
    }

    public static class SubTaskImpl2 extends SubTaskImpl{

    }

    public static class SubTaskImpl implements SubTask {

        public String projectName;

        @Override
        public Executable createExecutable() {
            return null;
        }

        @Override
        public Task getOwnerTask() {
            return (Task) Jenkins.get().getItem(projectName);
        }

        @Override
        public String getDisplayName() {
            return "some task";
        }


    }

    public class ActionImpl extends InvisibleAction{

    }

    @TestExtension
    public static class DummyCloudImpl2 extends Cloud {
        private final transient JenkinsRule caller;

        /**
         * Configurable delay between the {@link Cloud#provision(Label,int)} and the actual launch of a slave,
         * to emulate a real cloud that takes some time for provisioning a new system.
         *
         * <p>
         * Number of milliseconds.
         */
        private final int delay;

        // stats counter to perform assertions later
        public int numProvisioned;

        /**
         * Only reacts to provisioning for this label.
         */
        public Label label;

        public DummyCloudImpl2() {
            super("test");
            this.delay = 0;
            this.caller = null;
        }

        public DummyCloudImpl2(JenkinsRule caller, int delay) {
            super("test");
            this.caller = caller;
            this.delay = delay;
        }

        @Override
        public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
            List<NodeProvisioner.PlannedNode> r = new ArrayList<>();

            //Always provision...even if there is no workload.
            while (excessWorkload >= 0) {
                System.out.println("Provisioning");
                numProvisioned++;
                Future<Node> f = Computer.threadPoolForRemoting.submit(new ProjectTest.DummyCloudImpl2.Launcher(delay));
                r.add(new NodeProvisioner.PlannedNode(name + " #" + numProvisioned, f, 1));
                excessWorkload -= 1;
            }
            return r;
        }

        @Override
        public boolean canProvision(Label label) {
            //This cloud can ALWAYS provision
           return true;
            /* return label==this.label; */
        }

        private final class Launcher implements Callable<Node> {
            private final long time;
            /**
             * This is so that we can find out the status of Callable from the debugger.
             */
            private volatile Computer computer;

            private Launcher(long time) {
                this.time = time;
            }

            @Override
            public Node call() throws Exception {
                // simulate the delay in provisioning a new slave,
                // since it's normally some async operation.
                Thread.sleep(time);

                System.out.println("launching slave");
                DumbSlave slave = caller.createSlave(label);
                computer = slave.toComputer();
                computer.connect(false).get();
                synchronized (ProjectTest.DummyCloudImpl2.this) {
                    System.out.println(computer.getName() + " launch" + (computer.isOnline() ? "ed successfully" : " failed"));
                    System.out.println(computer.getLog());
                }
                return slave;
            }
        }

        @Override
        public Descriptor<Cloud> getDescriptor() {
            throw new UnsupportedOperationException();
        }
    }
}
