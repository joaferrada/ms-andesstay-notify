package com.andesstay.ms_andesstay_notify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Sin broker en el build: los listeners no arrancan durante el test.
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class MsAndesstayNotifyApplicationTests {

	@Test
	void contextLoads() {
	}

}
