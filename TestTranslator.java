import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TestTranslator {
    public static void main(String[] args) throws Exception {
        String text = "We have fixed the issue at MG Road, please check and verify.";
        String targetLanguage = "hi"; // Hindi
        
        System.out.println("Original text: " + text);
        System.out.println("Target language: " + targetLanguage);
        
        String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" 
            + targetLanguage + "&dt=t&q=" + java.net.URLEncoder.encode(text, "UTF-8");
            
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build();
            
        System.out.println("Calling translation API...");
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        System.out.println("Status Code: " + response.statusCode());
        System.out.println("Raw Response: " + response.body());
        
        // Simple extraction just for visual
        String body = response.body();
        if (body.startsWith("[[[\"")) {
            int endIdx = body.indexOf("\"", 4);
            System.out.println("Extracted Translation: " + body.substring(4, endIdx));
        }
    }
}
