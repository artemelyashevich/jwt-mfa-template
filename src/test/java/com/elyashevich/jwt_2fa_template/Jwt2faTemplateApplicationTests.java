package com.elyashevich.jwt_2fa_template;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
	"app.auth.access-secret=test-access-secret-test-access-secret-0123456789",
	"app.auth.refresh-secret=test-refresh-secret-test-refresh-secret-0123456789",
	"ADMIN_PASSWORD_HASH=test-admin-password-hash"
})
class Jwt2faTemplateApplicationTests {

	@Test
	void contextLoads() {
	}

}
