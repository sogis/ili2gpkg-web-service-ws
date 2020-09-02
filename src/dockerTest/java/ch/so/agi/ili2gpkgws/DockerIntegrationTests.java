package ch.so.agi.ili2gpkgws;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class DockerIntegrationTests extends IntegrationTests {

    @BeforeAll
    public void setup() {
        port = "8080";
        servletContextPath = "/";
    }
}
