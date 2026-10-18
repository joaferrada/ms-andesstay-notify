package com.andesstay.ms_andesstay_notify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MsAndesstayNotifyApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsAndesstayNotifyApplication.class, args);
	}

}
