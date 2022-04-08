package com.example.oradbsample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SpringBootApplication
public class App implements CommandLineRunner {

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

}
