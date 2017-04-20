package com.example;


import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

@EnableZuulProxy
@EnableFeignClients
@SpringBootApplication
public class CustomerManagementClientApplication {

    @Bean
    @LoadBalanced
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public static void main(String[] args) {
        SpringApplication.run(CustomerManagementClientApplication.class, args);
    }
}

@EnableDiscoveryClient
@EnableBinding(Source.class)
@RestController
@RequestMapping("/customers")
@EnableCircuitBreaker
class CustomerApiGatewayRestController {

    @Autowired
    private Source outputChannelSource;

    private final RestTemplate restTemplate;

    @RequestMapping(method = RequestMethod.POST)
    public void write(@RequestBody Customer customer) {
        MessageChannel channel = this.outputChannelSource.output();
        channel.send(
                MessageBuilder.withPayload(customer).build()
        );
    }

    public Collection<String> fallback() {
        return new ArrayList<>();
    }

    @Autowired
    public CustomerApiGatewayRestController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }


    @HystrixCommand(fallbackMethod = "fallback")
    @RequestMapping(method = RequestMethod.GET)
    public Collection<String> names() {
        return this.restTemplate.exchange("http://customer-management-service/customers/",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Resources<Customer>>() {
                })
                .getBody()
                .getContent()
                .stream()
                .map(Customer::getCustomerName)
                .collect(Collectors.toList());
    }
}


class Customer {
    private String customerName;

    public Customer() {
    }

    public Customer(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }
}
