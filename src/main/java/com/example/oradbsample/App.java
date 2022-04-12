package com.example.oradbsample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import static java.lang.Thread.*;

@SpringBootApplication
public class App implements CommandLineRunner {

	ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
	private static final Logger log = LoggerFactory.getLogger(App.class);

	// @Autowired
	// private JdbcTemplate jdbcTemplate;
	
	@Autowired
	private NamedParameterJdbcTemplate namedParameterjdbcTemplate;

	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		
		log.info("Querying for List of Regions");
		
		String query = "SELECT * FROM REGIONS";

		MapSqlParameterSource parameters = new MapSqlParameterSource();

		namedParameterjdbcTemplate
				.queryForList(
					query, parameters)
						.forEach(customer -> log.info(customer.toString()));
		
	}
	@PreDestroy
	public void destroy() {
		System.out.println("Triggered PreDestroy");

		//Verify if the threads have completed their tasks and then proceed with shutdown
		while (executor.getActiveCount() > 0) {
			try {
				sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Completed all active threads");
	}
}
