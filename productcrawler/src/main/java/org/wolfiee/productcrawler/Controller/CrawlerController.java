package org.wolfiee.productcrawler.Controller;

import java.util.*;

import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wolfiee.productcrawler.Service.crawlerservice;

@RestController
@RequestMapping("/crawler")

public class CrawlerController {

    @Autowired
    private crawlerservice crawlerService;

    @PostMapping("/crawl")
    public ResponseEntity<Map<String, Set<String>>> crawlProductLinks(@RequestBody List<String> domains) {
        if (domains == null || domains.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", Set.of("No domains provided")));
        }

        Map<String, Set<String>> results = crawlerService.crawlDomains(domains);

        return ResponseEntity.ok(results);
    }
}
