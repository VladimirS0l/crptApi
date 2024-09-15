package ru.solarev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final HttpClient client;
    private final Semaphore semaphore;
    private final long timeIntervalInMillis;
    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.client = HttpClient.newHttpClient();
        this.semaphore = new Semaphore(requestLimit, true);
        this.timeIntervalInMillis = timeUnit.toMillis(1);
        this.objectMapper = new ObjectMapper();
    }

    public String createDocumentForGoodsIntroduction(
            Document document,
            String signature
    ) throws IOException, InterruptedException {
        // Блокируем запрос, если лимит исчерпан
        if (!semaphore.tryAcquire(1, timeIntervalInMillis, TimeUnit.MILLISECONDS)) {
            semaphore.acquire(); // Блокировка до освобождения семафора
        }

        try {
            // Формирование тела запроса в JSON формате
            String jsonBody = objectMapper.writeValueAsString(document);
            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("signature", signature);
            rootNode.set("document", objectMapper.readTree(jsonBody));
            String requestBody = rootNode.toString();

            // Формирование HTTP запроса
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // Выполнение HTTP запроса
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Обработка ответа
            if (response.statusCode() == 200) {
                return response.body(); // Возвращаем тело ответа
            } else {
                throw new RuntimeException("Ошибка запроса: " + response.statusCode() + " " + response.body());
            }
        } finally {
            // Разблокируем семафор
            semaphore.release();
        }
    }

    // Внутренний класс для описания документа
    public static class Document {
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }
}
