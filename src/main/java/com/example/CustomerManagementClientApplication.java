package com.example;


import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
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
@EnableBinding(Source.class)
@EnableDiscoveryClient
@EnableCircuitBreaker
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

    @RestController
    @RequestMapping("/customers")
    class CustomerApiGatewayRestController {

        private final RestTemplate restTemplate;

        @Autowired
        public CustomerApiGatewayRestController(RestTemplate restTemplate) {
            this.restTemplate = restTemplate;
        }

        public Collection<String> fallback() {
            return new ArrayList<>();
        }

        private Source outputChannelSource;

        @Autowired
        @RequestMapping(method = RequestMethod.POST)
        public void write(@RequestBody Customer cn) {
            MessageChannel channel = this.outputChannelSource.output();
            channel.send(
                    MessageBuilder.withPayload(cn.getCustomerName()).build()
            );
        }

        @HystrixCommand(fallbackMethod = "fallback")
        @RequestMapping(method = RequestMethod.GET, value = "/names")
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
}

    class Customer {
        private String customerName;

        public String getCustomerName() {
            return customerName;
        }

        public Customer(String customerName) {
            this.customerName = customerName;
        }
    }