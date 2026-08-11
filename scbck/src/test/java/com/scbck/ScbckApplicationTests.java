package com.scbck;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the whole bean graph wires up: security chain, controllers,
 * repositories and the privilege service.
 */
@SpringBootTest
@ActiveProfiles("test")
class ScbckApplicationTests {

	@Test
	void contextLoads() {
	}

}
