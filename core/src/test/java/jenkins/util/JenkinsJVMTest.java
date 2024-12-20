package jenkins.util;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class JenkinsJVMTest {

    @Test
    public void checkNotJenkinsJVM_WhenNotInAJenkinsJVM() {
        JenkinsJVM.checkNotJenkinsJVM();
    }

    @Test
    public void checkJenkinsJVM_WhenNotInAJenkinsJVM() {
        assertThrows(IllegalStateException.class, JenkinsJVM::checkJenkinsJVM);
    }

    @Test
    public void checkNotJenkinsJVM_WhenInAJenkinsJVM() {
        JenkinsJVM.setJenkinsJVM(true);
        try {
            assertThrows(IllegalStateException.class, JenkinsJVM::checkNotJenkinsJVM);
        } finally {
            JenkinsJVM.setJenkinsJVM(false);
        }
    }

    @Test
    public void checkJenkinsJVM_WhenInAJenkinsJVM() {
        JenkinsJVM.setJenkinsJVM(true);
        try {
            JenkinsJVM.checkJenkinsJVM();
        } finally {
            JenkinsJVM.setJenkinsJVM(false);
        }
    }
}
