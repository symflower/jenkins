package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class BallColorTest {

    @Test
    public void iconClassName() {
        assertEquals("icon-red", BallColor.RED.getIconClassName());
        assertEquals("icon-aborted-anime", BallColor.ABORTED_ANIME.getIconClassName());
    }
}
