package ch.so.agi.ili2gpkgws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

@SpringBootApplication
public class Ili2gpkgWebServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(Ili2gpkgWebServiceApplication.class, args);
	}
	
	@Configuration
	public class AppConfig {
	    @Bean
	    public ForwardedHeaderFilter forwardedHeaderFilter() {
	        return new ForwardedHeaderFilter();
	    }
	}
}
