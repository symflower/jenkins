package hudson.util;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.junit.jupiter.api.Assertions;

public class SecretUtilTest {

    @Issue("JENKINS-47500")
    @Test
    public void decrypt() {
        String data = "{}";
        Secret secret = Secret.decrypt(data);
        Assertions.assertNull(secret); // expected to not throw ArrayIndexOutOfBoundsException
    }


    @Issue("JENKINS-47500")
    @Test
    public void decryptJustSpace() {
        String data = " ";
        Secret secret = Secret.decrypt(data);
        Assertions.assertNull(secret); // expected to not throw ArrayIndexOutOfBoundsException
    }

    @Issue("JENKINS-47500")
    @Test
    public void decryptWithSpace() {
        String data = "{ }";
        Secret secret = Secret.decrypt(data);
        Assertions.assertNull(secret); // expected to not throw ArrayIndexOutOfBoundsException
    }

    @Issue("JENKINS-47500")
    @Test
    public void decryptWithSpaces() {
        String data = "{     }";
        Secret secret = Secret.decrypt(data);
        Assertions.assertNull(secret); // expected to not throw ArrayIndexOutOfBoundsException
    }
}
