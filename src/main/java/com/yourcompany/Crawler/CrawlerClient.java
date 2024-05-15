package com.yourcompany.Crawler;

import java.io.PrintWriter;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class CrawlerClient {

    public void PrintIt(Object p) {
        System.out.println(p);
    }

    // Just hit run and it will start executing
    public static void main(String[] args) {
        CrawlerClient crawlClient = new CrawlerClient();
        String BASE_URL = "http://www.cochranelibrary.com/home/topic-and-review-group-list.html?page=topic";
        ArrayList<String> topicUrls = new ArrayList<String>();
        Scanner input = new Scanner(System.in);

        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpGet getPage = new HttpGet(BASE_URL);
            getPage.addHeader("User-agent", "random");

            try (CloseableHttpResponse response = client.execute(getPage)) {
                HttpEntity entity = response.getEntity();
                Document doc = Jsoup.parse(EntityUtils.toString(entity));

                Elements topics = doc.getElementsByClass("browse-by-list-item");

                int i = 0;

                for (Element topic : topics) {
                    crawlClient.PrintIt("#" + (i++) + "." + topic.getElementsByTag("a").text());
                    topicUrls.add(topic.getElementsByTag("a").attr("href"));
                }

                String chosenTopic = "";
                crawlClient.PrintIt("Enter the topic number:");
                String myInput = input.nextLine();
                crawlClient.PrintIt("Fetching the data of the chosen topic.");

                if (!myInput.isEmpty() && myInput.matches("\\d+")) {

                    int tempIndex = Integer.parseInt(myInput);

                    if (tempIndex >= 0 && tempIndex < topicUrls.size()) {
                        chosenTopic = topicUrls.get(tempIndex);
                    }

                } else {

                    crawlClient.PrintIt("Wrong Input, Try again");

                }

                HttpGet browseCurrentTopic = new HttpGet(chosenTopic);

                browseCurrentTopic.addHeader("User-agent", "random");

                ArrayList<String> page_urls = new ArrayList<String>();

                try (CloseableHttpResponse response2 = client.execute(browseCurrentTopic)) {
                    HttpEntity entity2 = response2.getEntity();
                    doc = Jsoup.parse(EntityUtils.toString(entity2));

                    Elements pageHtml = doc.getElementsByClass("pagination-page-list-item");

                    for (Element page : pageHtml) {
                        String page_links = page.getElementsByTag("a").attr("href");
                        page_urls.add(page_links);
                    }

                } catch (Exception e) {
                    // TODO: handle exception
                    crawlClient.PrintIt(e);
                    crawlClient.PrintIt("Wrong Input, Try again");
                }

                for (String page_link : page_urls) {
                    HttpGet fetchPage = new HttpGet(page_link);
                    fetchPage.addHeader("User-Agent", "random");

                    try (CloseableHttpResponse response3 = client.execute(fetchPage)) {
                        HttpEntity entity3 = response3.getEntity();

                        CrawlerClient.processContent(entity3);

                    } catch (Exception e) {
                        crawlClient.PrintIt("Error: " + e.getMessage());
                    }
                }

            } catch (Exception e) {
                crawlClient.PrintIt("Error: " + e.getMessage());
            }

        } catch (Exception e) {
            crawlClient.PrintIt("Error: " + e.getMessage());
        }

    }

    public static void processContent(HttpEntity entity) throws Exception {
        if (entity == null) {
            System.out.println("No content to show.");
            return;
        } else {
            Document doc = Jsoup.parse(EntityUtils.toString(entity));
            Elements searchResult = doc.getElementsByClass("search-results-item-body");
            String topic = doc.getElementsByClass("facet-pill secondary").text();
            String libUrl = doc.getElementsByClass("aux-footer-nav-link").attr("href");
            libUrl = libUrl.substring(0, libUrl.length() - 1);

            try (PrintWriter writer = new PrintWriter(
                    Files.newBufferedWriter(Paths.get("cochrane_Suvineet_reviews.txt"), StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND))) {
                for (Element item_body : searchResult) {

                    String hrefl = item_body.select("a").attr("href");
                    hrefl = hrefl.substring(5, hrefl.length());
                    String title = item_body.select(".result-title").text();
                    String authors = item_body.select(".search-result-authors").text();
                    String date = item_body.select(".search-result-date").text();
                    String final_date = convertDateFormat(date);

                    String[] authors_list = authors.split(", ");
                    String final_authors = Stream.of(authors_list).limit(3)
                            .collect(Collectors.joining(", "));

                    writer.println(prepareLine(libUrl + hrefl, topic, title, final_authors, final_date));

                }
            } catch (Exception e) {
                System.out.println("Error writing to file: " + e.getMessage());
            }
        }

    }

    public static String prepareLine(String url, String topic, String title, String authors, String date) {
        StringBuilder output = new StringBuilder();
        int currentLength = 0;

        // List of fields to process
        List<String> fields = Arrays.asList(url, topic, title, authors, date);
        for (int i = 0; i < fields.size(); i++) {
            String field = fields.get(i);
            String[] words = field.split("\\s+");

            if (i > 0) {
                if (currentLength + 3 > 80) {
                    output.append("\n");
                    currentLength = 0;
                    output.append("| ");
                } else {
                    output.append(" | ");
                    currentLength += 3;

                }

            }

            for (String word : words) {

                if (currentLength + word.length() + (currentLength > 0 ? 1 : 0) > 80) {
                    if (currentLength > 0) {
                        output.append("\n");
                        currentLength = 0;
                    }
                }

                if (currentLength != 0) {
                    output.append(" ");
                    currentLength += 1;
                }

                output.append(word);
                currentLength += word.length();
            }
        }

        return output.toString();

    }

    public static String convertDateFormat(String oldDateString) {
        DateTimeFormatter oldFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy");
        DateTimeFormatter newFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try {
            LocalDate date = LocalDate.parse(oldDateString, oldFormatter);
            return date.format(newFormatter);
        } catch (DateTimeParseException e) {
            System.err.println("Error parsing the date: " + e.getMessage());
            return null;
        }
    }
}
