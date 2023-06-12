package com.carparketl.config;

import com.carparketl.commons.SqlBuilder;
import com.carparketl.dtos.CarParkDto;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.util.Scanner;

@Configuration
@Slf4j
public class CarParkJobBatchConfig {

    @Value("classpath:hdb-carpark-information.csv")
    Resource carParkResource;

    @Autowired
    JobBuilderFactory jobBuilderFactory;

    @Autowired
    JobLauncher jobLauncher;

    @Autowired
    StepBuilderFactory stepBuilderFactory;

    @Autowired
    DataSource dataSource;

    @Autowired
    SqlBuilder sqlBuilder;


    @Bean
    public Job jobImportCsvCarParkInfo(){
        return jobBuilderFactory.get("importCsvCarParkInfo").start(step()).build();
    }


    @Bean
    public Step step(){
        return stepBuilderFactory.get("stepProcessCarParkInfo")
                .<CarParkDto, CarParkDto>chunk(100)
                .writer(writer())
                .reader(reader())
                .build();
    }

    @Scheduled(fixedRate = 5000)
    public void run() throws Exception {
        JobExecution execution = jobLauncher.run(
                jobImportCsvCarParkInfo(),
                new JobParametersBuilder().addLong("uniqueness", System.nanoTime()).toJobParameters()
        );
        log.info("Exit status: {}", execution.getStatus());
    }

    @Bean
    public ItemReader<CarParkDto> reader(){
        FlatFileItemReader<CarParkDto> flatFileItemReader = new FlatFileItemReader<>();
        flatFileItemReader.setResource(carParkResource);
        flatFileItemReader.setLinesToSkip(1);
        flatFileItemReader.setLineMapper(getCarParkMapper());
        return flatFileItemReader;
    }

    @Bean
    public ItemWriter<CarParkDto> writer() {
        JdbcBatchItemWriter<CarParkDto> writer = new JdbcBatchItemWriter<>();
        writer.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>());
        writer.setSql(sqlBuilder.getCarParkInsertQuery());
        writer.setDataSource(dataSource);
        return writer;
    }

    public DefaultLineMapper<CarParkDto> getCarParkMapper(){
        String header = getHeaderLine();
        return new DefaultLineMapper<>() {{
            setLineTokenizer(new DelimitedLineTokenizer() {{
                setNames(header.split(","));
            }});
            setFieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
                setTargetType(CarParkDto.class);
            }});
        }};
    }

    @SneakyThrows
    public String getHeaderLine(){
        Scanner scanner = new Scanner(carParkResource.getInputStream());
        String line = scanner.nextLine();
        scanner.close();
        return line;
    }

}
