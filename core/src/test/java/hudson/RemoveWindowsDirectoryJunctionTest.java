/*
 *
 */

package hudson;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.os.WindowsUtil;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;

@For(Util.class)
// https://superuser.com/q/343074
public class RemoveWindowsDirectoryJunctionTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @BeforeEach
    public void windowsOnly() {
       assumeTrue(Functions.isWindows());
    }

    @Test
    @Issue("JENKINS-2995")
    public void testJunctionIsRemovedButNotContents() throws Exception {
        File subdir1 = tmp.newFolder("notJunction");
        File f1 = new File(subdir1, "testfile1.txt");
        assertTrue(f1.createNewFile(), "Unable to create temporary file in notJunction directory");
        File j1 = WindowsUtil.createJunction(new File(tmp.getRoot(), "test junction"), subdir1);
        Util.deleteRecursive(j1);
        assertFalse(j1.exists(), "Windows Junction should have been removed");
        assertTrue(f1.exists(), "Contents of Windows Junction should not be removed");
    }

}
