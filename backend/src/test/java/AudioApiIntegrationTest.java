import com.fasterxml.jackson.databind.ObjectMapper; // Thêm import này
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

public class AudioApiIntegrationTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDeezerApi_ShouldReturnData() throws Exception {
        String url = "https://api.deezer.com/search?q=lofi";

        // Create header
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Call API Deezer
        ResponseEntity<Map> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        Map<String, Object> response = responseEntity.getBody();

        // Check response
        assertNotNull(response);
        assertTrue(response.containsKey("data"), "Deezer response must have field 'data'");

        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);

        System.out.println("======= DATA FROM DEEZER =======");
        System.out.println(prettyJson);
        System.out.println("=================================");

        System.out.println("Deezer OK! Found: " + ((List) response.get("data")).size() + " audio.");
    }
}