package org.wolfiee.productcrawler.Service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeoutException;

@Service
public class crawlerservice {

    private static final List<String> PRODUCT_PATTERNS = List.of("/product", "/item", "/p/", "/products/");
    private static final int MAX_DEPTH = 4;
    private static final int MAX_PAGES = 500;

    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public Map<String, Set<String>> crawlDomains(List<String> domains) {

        Map<String, Set<String>> resultMap = new ConcurrentHashMap<>();
        Map<String, Future<?>> futures = new HashMap<>();

        for (String domain : domains) {
            resultMap.put(domain, ConcurrentHashMap.newKeySet());
            String base = domain;
            futures.put(base, executor.submit(() -> crawlWithSelenium(base, resultMap.get(base))));
        }

        // Wait for each domain with timeout
        for (Map.Entry<String, Future<?>> entry : futures.entrySet()) {
            String domain = entry.getKey();
            Future<?> future = entry.getValue();

            try {
                future.get(10, TimeUnit.MINUTES);  // Wait max 10 min
            } catch (TimeoutException e) {
                future.cancel(true);  // Stop crawling
                resultMap.get(domain).add("TIMEOUT: Partial results returned");
            } catch (Exception e) {
                resultMap.get(domain).add("ERROR: " + e.getMessage());
            }
        }

        return resultMap;
    }

    private void crawlWithSelenium(String baseUrl, Set<String> productUrls) {
        Set<String> visited = ConcurrentHashMap.newKeySet();
        Queue<String> toVisit = new ConcurrentLinkedQueue<>();
        Map<String, Integer> depthMap = new ConcurrentHashMap<>();

        toVisit.add(baseUrl);
        depthMap.put(baseUrl, 0);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new","--no-sandbox","--disable-dev-shm-usage","--disable-gpu","--disable-software-rasterizer","--remote-debugging-port=9222");
        WebDriver driver = new ChromeDriver(options);

        try {
            long startTime = System.currentTimeMillis();
            long maxRunTimeMillis = 10 * 60 * 1000; // 10 minutes

            while (!toVisit.isEmpty() && (System.currentTimeMillis() - startTime < maxRunTimeMillis)) {
                String url = toVisit.poll();
                if (url == null) continue;

                String normalizedUrl = normalizeUrl(url);
                if (visited.contains(normalizedUrl)) continue;
                if (visited.size() >= MAX_PAGES) break;

                int currentDepth = depthMap.getOrDefault(normalizedUrl, 0);
                if (currentDepth > MAX_DEPTH) continue;

                visited.add(normalizedUrl);

                try {
                    driver.get(normalizedUrl);
                    Thread.sleep(2000); // Allow JS content to load

                    List<WebElement> anchorTags = driver.findElements(By.tagName("a"));
                    for (WebElement anchor : anchorTags) {
                        String href = anchor.getAttribute("href");
                        if (href == null || href.isEmpty()) continue;

                        String absUrl = normalizeUrl(href);
                        if (!absUrl.startsWith(baseUrl)) continue;

                        if (isProductUrl(absUrl)) {
                            productUrls.add(absUrl);
                            System.out.println("Product: " + absUrl);
                        } else if (!visited.contains(absUrl) && !toVisit.contains(absUrl)) {
                            toVisit.add(absUrl);
                            depthMap.put(absUrl, currentDepth + 1);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to crawl: " + normalizedUrl + " - " + e.getMessage());
                }
            }
        } finally {
            driver.quit();
        }
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        return url.split("#")[0].replaceAll("/$", "").trim();
    }

    private boolean isProductUrl(String url) {
        return PRODUCT_PATTERNS.stream().anyMatch(url::contains);
    }
}
